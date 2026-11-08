package app.cruiser.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.ParcelUuid
import android.util.Log
import androidx.core.content.ContextCompat
import app.cruiser.proto.PodProtocol
import java.util.UUID
import java.util.concurrent.ConcurrentLinkedQueue
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class LinkState { SCANNING, CONNECTING, CONNECTED, DISCONNECTED, BT_OFF, NO_PERMISSIONS }

/**
 * BLE transport for the Tripper Pod, mirroring the RE app's behavior:
 * connect as GATT client (subscribe to the Pod's char too) + host a mirror GATT
 * server so the Pod can reply over the established link; frames go out serialized,
 * WRITE_TYPE_NO_RESPONSE, next frame offered from onCharacteristicWrite.
 */
@SuppressLint("MissingPermission") // PodService only starts this holding BLUETOOTH_SCAN/CONNECT
class PodLink(private val context: Context) {

    private val mgr get() = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val adapter: BluetoothAdapter get() = mgr.adapter
    private val serviceUuid = ParcelUuid.fromString(PodProtocol.SERVICE_UUID)
    private val charUuid: UUID = UUID.fromString(PodProtocol.CHAR_UUID)
    private val cccd = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    /** Frames waiting for a serialized write. */
    val queue = ConcurrentLinkedQueue<ByteArray>()

    private val _state = MutableStateFlow(LinkState.DISCONNECTED)
    val state: StateFlow<LinkState> = _state

    /** MAC of the Pod we last saw / are bound to (resume without a scan). */
    val deviceAddress = MutableStateFlow<String?>(null)

    /** Raw command received from the Pod (server write or client notification). */
    var onCommand: ((ByteArray) -> Unit)? = null

    private var client: BluetoothGatt? = null
    private var server: BluetoothGattServer? = null
    private var char: BluetoothGattCharacteristic? = null
    private var scanCallback: ScanCallback? = null

    private val scanner get() = adapter.bluetoothLeScanner

    private val clientCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothGatt.STATE_CONNECTED -> {
                    _state.value = LinkState.CONNECTING
                    g.discoverServices()
                }
                BluetoothGatt.STATE_DISCONNECTED -> {
                    _state.value = LinkState.DISCONNECTED
                    char = null
                    g.close()
                    client = null
                }
            }
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            val svc = g.services.firstOrNull { it.uuid == serviceUuid.uuid } ?: return
            val c = svc.characteristics.firstOrNull { it.uuid == charUuid } ?: return
            // Try notifications (RE does) — if the Pod never notifies, the mirror
            // server path still delivers its replies.
            g.setCharacteristicNotification(c, true)
            c.descriptors.firstOrNull()?.let { d ->
                g.writeDescriptor(d, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
            }
            char = c
            _state.value = LinkState.CONNECTED
            drain()
        }

        override fun onCharacteristicWrite(g: BluetoothGatt, c: BluetoothGattCharacteristic, status: Int) {
            drain()
        }

        // Legacy signature covers API 31/32 when running on newer compile SDK.
        @Deprecated("deprecated in Java")
        override fun onCharacteristicChanged(g: BluetoothGatt, c: BluetoothGattCharacteristic) {
            val v = c.value ?: return
            onCommand?.invoke(v)
        }
    }

    private val serverCallback = object : BluetoothGattServerCallback() {
        override fun onCharacteristicWriteRequest(
            device: BluetoothDevice, requestId: Int,
            characteristic: BluetoothGattCharacteristic,
            preparedWrite: Boolean, responseNeeded: Boolean, offset: Int, value: ByteArray,
        ) {
            onCommand?.invoke(value)
            if (responseNeeded) server?.sendResponse(device, requestId, 0, offset, value)
        }
    }

    /**
     * Remembered Pod: connect directly; otherwise scan for the Pod's service briefly.
     * Bluetooth off / permissions missing surface as states, never throw.
     */
    fun scanOrConnect(blePerms: Boolean) {
        if (!adapter.isEnabled) {
            _state.value = LinkState.BT_OFF
            return
        }
        if (!blePerms) {
            _state.value = LinkState.NO_PERMISSIONS
            return
        }
        val remembered = deviceAddress.value
        if (remembered != null) connect(blePerms, remembered) else startScan()
    }

    private fun startScan() {
        if (scanCallback != null) return
        _state.value = LinkState.SCANNING
        scanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                stopScan()
                deviceAddress.value = result.device.address
                connect(true, result.device.address)
            }
        }
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .setNumOfMatches(1)
            .build()
        val filters = listOf(ScanFilter.Builder().setServiceUuid(serviceUuid).build())
        scanner?.startScan(filters, settings, scanCallback)
    }

    fun stopScan() {
        scanCallback?.let { scanner?.stopScan(it) }
        scanCallback = null
    }

    fun connect(blePerms: Boolean, address: String) {
        if (!adapter.isEnabled || !blePerms) {
            _state.value = if (!adapter.isEnabled) LinkState.BT_OFF else LinkState.NO_PERMISSIONS
            return
        }
        stopScan()
        _state.value = LinkState.CONNECTING
        closeAndReset()
        deviceAddress.value = address
        client = adapter.getRemoteDevice(address)
            .connectGatt(context, false, clientCallback, BluetoothDevice.TRANSPORT_LE)
    }

    fun openServer() {
        if (server != null) return
        server = mgr.openGattServer(context, serverCallback)?.apply {
            val svc = BluetoothGattService(serviceUuid.uuid, BluetoothGattService.SERVICE_TYPE_PRIMARY)
            svc.addCharacteristic(BluetoothGattCharacteristic(charUuid, 5, 16)) // READ|WRITE, perm WRITE
            addService(svc)
        }
    }

    fun closeServer() {
        server?.clearServices()
        server?.close()
        server = null
    }

    /**
     * GrapheneOS-style auto-off cycles kill the GATT server (and the client
     * link) without our knowing — watch the adapter, mark the state, and reopen
     * the mirror server automatically so the Pod's reply channel comes back.
     */
    private var wantsServer = false
    private val stateWatch = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != BluetoothAdapter.ACTION_STATE_CHANGED) return
            when (intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, -1)) {
                BluetoothAdapter.STATE_OFF -> {
                    wantsServer = server != null
                    server = null // system closed it
                    closeAndReset()
                    _state.value = LinkState.BT_OFF
                    if (wantsServer) Log.i("CruiserNav", "BT off — link dropped, will reopen server")
                }
                BluetoothAdapter.STATE_ON -> {
                    if (wantsServer) openServer()
                    Log.i("CruiserNav", "BT on — server reopened=${server != null}")
                }
            }
        }
    }

    fun watchAdapterState() {
        ContextCompat.registerReceiver(
            context,
            stateWatch,
            IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }

    fun unwatchAdapterState() = context.unregisterReceiver(stateWatch)

    /** Offer a frame; written when the transport is idle. */
    fun send(bytes: ByteArray) {
        queue.offer(bytes)
        drain()
    }

    private fun drain() {
        val c = char ?: return
        val b = queue.poll() ?: return
        c.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE)
        @Suppress("DEPRECATION")
        c.value = b
        @Suppress("DEPRECATION")
        val ok = client?.writeCharacteristic(c)
        if (ok == false) Log.i("CruiserNav", "BLE write rejected")
    }

    fun disconnect() {
        client?.disconnect()
        client?.close()
        closeAndReset()
        _state.value = LinkState.DISCONNECTED
    }

    private fun closeAndReset() {
        client = null
        char = null
        queue.clear()
    }
}
