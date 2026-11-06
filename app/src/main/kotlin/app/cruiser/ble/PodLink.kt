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
import android.content.Context
import android.os.ParcelUuid
import java.util.UUID
import java.util.concurrent.ConcurrentLinkedQueue
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class LinkState { SCANNING, CONNECTING, CONNECTED, DISCONNECTED }

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
            g.getDescriptor(c)?.let { d ->
                d.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                g.writeDescriptor(d)
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
            c.value?.let(onCommand)
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

    /** Remembered Pod: connect directly; otherwise scan for the Pod's service briefly. */
    fun scanOrConnect() {
        val remembered = deviceAddress.value
        if (remembered != null) connect(remembered) else startScan()
    }

    private fun startScan() {
        if (scanCallback != null) return
        _state.value = LinkState.SCANNING
        scanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                stopScan()
                deviceAddress.value = result.device.address
                connect(result.device.address)
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

    fun connect(address: String) {
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
        client?.writeCharacteristic(c)
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
