package site.casiopea.cruizer.ble

import android.Manifest
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
import android.content.pm.PackageManager
import android.os.ParcelUuid
import android.util.Log
import androidx.core.content.ContextCompat
import site.casiopea.cruizer.proto.PodProtocol
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

    @Volatile private var client: BluetoothGatt? = null
    private var server: BluetoothGattServer? = null
    @Volatile private var char: BluetoothGattCharacteristic? = null
    private var scanCallback: ScanCallback? = null
    private var connectStartedAt = 0L

    private val scanner get() = adapter.bluetoothLeScanner

    private val clientCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            // Stale callbacks from a client we already dropped must not touch state.
            if (client !== g) {
                g.close()
                return
            }
            when (newState) {
                BluetoothGatt.STATE_CONNECTED -> {
                    _state.value = LinkState.CONNECTING
                    g.discoverServices()
                }
                BluetoothGatt.STATE_DISCONNECTED -> {
                    _state.value = LinkState.DISCONNECTED
                    client = null
                    char = null
                    g.close()
                }
            }
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            if (client !== g) return
            val svc = g.services.firstOrNull { it.uuid == serviceUuid.uuid }
            val c = svc?.characteristics?.firstOrNull { it.uuid == charUuid }
            if (c == null) {
                disconnect() // wrong device or a partial discovery; retry cleanly
                return
            }
            char = c
            _state.value = LinkState.CONNECTED
            val d = c.getDescriptor(cccd)
            if (d != null) {
                g.setCharacteristicNotification(c, true)
                @Suppress("DEPRECATION")
                d.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                @Suppress("DEPRECATION")
                g.writeDescriptor(d)
            } else {
                drain()
            }
        }

        override fun onDescriptorWrite(g: BluetoothGatt, d: BluetoothGattDescriptor, status: Int) {
            if (client !== g) return
            drain()
        }

        override fun onCharacteristicWrite(g: BluetoothGatt, c: BluetoothGattCharacteristic, status: Int) {
            if (client !== g) return
            // status is the only delivery signal there is: a rejected write is
            // otherwise indistinguishable from a delivered one. Re-queue it.
            if (status != BluetoothGatt.GATT_SUCCESS) c.value?.let { queue.offer(it) }
            drain()
        }

        // Legacy signature covers API 31/32 when running on newer compile SDK.
        @Deprecated("deprecated in Java")
        override fun onCharacteristicChanged(g: BluetoothGatt, c: BluetoothGattCharacteristic) {
            if (client !== g) return
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
        val cb = object : ScanCallback() {
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
        val s = scanner ?: return
        scanCallback = cb
        _state.value = LinkState.SCANNING
        try {
            s.startScan(filters, settings, cb)
        } catch (_: SecurityException) {
            scanCallback = null
            _state.value = LinkState.NO_PERMISSIONS
        }
    }

    fun stopScan() {
        scanCallback?.let { cb -> runCatching { scanner?.stopScan(cb) } }
        scanCallback = null
    }

    fun connect(blePerms: Boolean, address: String) {
        if (!adapter.isEnabled || !blePerms) {
            _state.value = if (!adapter.isEnabled) LinkState.BT_OFF else LinkState.NO_PERMISSIONS
            return
        }
        // Ignore retries while a fresh attempt is in flight; one that never
        // called back (>10 s) is retried instead of leaving a stuck spinner.
        val now = System.currentTimeMillis()
        if (_state.value == LinkState.CONNECTING && now - connectStartedAt < 10_000) return
        connectStartedAt = now
        _state.value = LinkState.CONNECTING
        reset()
        deviceAddress.value = address
        client = adapter.getRemoteDevice(address)
            .connectGatt(context, false, clientCallback, BluetoothDevice.TRANSPORT_LE)
    }

    fun openServer() {
        if (server != null) return
        if (context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) !=
            PackageManager.PERMISSION_GRANTED
        ) return
        try {
            server = mgr.openGattServer(context, serverCallback)?.apply {
                val svc =
                    BluetoothGattService(serviceUuid.uuid, BluetoothGattService.SERVICE_TYPE_PRIMARY)
                // READ|WRITE, permission WRITE (the Pod writes its replies here).
                svc.addCharacteristic(BluetoothGattCharacteristic(charUuid, 5, 16))
                addService(svc)
            }
        } catch (_: SecurityException) {
            server = null
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
    private val stateWatch = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != BluetoothAdapter.ACTION_STATE_CHANGED) return
            when (intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, -1)) {
                BluetoothAdapter.STATE_OFF -> {
                    server = null // system closed it
                    reset()
                    _state.value = LinkState.BT_OFF
                    Log.i("CruizerNav", "BT off — link dropped")
                }
                BluetoothAdapter.STATE_ON -> {
                    // Idempotent, and needed even when the service started with BT off.
                    openServer()
                    Log.i("CruizerNav", "BT on — server reopened=${server != null}")
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

    @Synchronized
    private fun drain() {
        val c = char ?: return
        // Peek so a BT-down window can't consume the frame from the queue.
        val b = queue.peek() ?: return
        c.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE)
        @Suppress("DEPRECATION")
        c.value = b
        @Suppress("DEPRECATION")
        val ok = client?.writeCharacteristic(c)
        // true = handed to the stack, not delivered; a failure re-queues the frame
        // from onCharacteristicWrite.
        if (ok == true) queue.poll() else if (ok == false) Log.i("CruizerNav", "BLE write rejected")
    }

    fun disconnect() {
        reset()
        _state.value = LinkState.DISCONNECTED
    }

    /** One cleanup for every exit: stop the scan, drop the client, clear pending frames. */
    @Synchronized
    private fun reset() {
        stopScan()
        client?.let { runCatching { it.disconnect(); it.close() } }
        client = null
        char = null
        queue.clear()
    }
}
