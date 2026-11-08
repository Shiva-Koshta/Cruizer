package app.cruiser

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
import android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
import android.location.LocationListener
import android.location.LocationManager
import android.os.IBinder
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.app.NotificationCompat
import app.cruiser.ble.LinkState
import app.cruiser.ble.PodLink
import app.cruiser.notify.ComapsSource
import app.cruiser.proto.Icons
import app.cruiser.proto.PodProtocol
import app.cruiser.proto.TurnUpdate
import app.cruiser.ui.MainActivity
import app.cruiser.trip.TripRecorder
import app.cruiser.trip.TripStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Foreground service owning the BLE link. A 1 Hz loop renders [Session] state into
 * Pod frames: nav screen re-sent every second (this doubles as the Pod's watchdog
 * feed), call overlay while a call is active, PING_FW when idle for 2 s.
 */
class PodService : Service() {

    companion object {
        const val ACTION_START = "app.cruiser.START"
        const val ACTION_STOP = "app.cruiser.STOP"
        const val ACTION_FORGET = "app.cruiser.FORGET"
        private const val CHANNEL_ID = "pod"
        private const val NOTIF_ID = 42
        private const val KEY_MAC = "pod_mac"
        private const val KEY_PAIRED = "pod_paired"
        private const val CONNECT_PAUSE_MS = 1000L
        private const val RESUME_PREAMBLE_MS = 200L

        /** Don't keep the foreground process alive forever with no Pod around. */
        private const val STOP_AFTER_DISCONNECTED_MS = 5 * 60 * 1000L
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var link: PodLink
    private var tickJob: Job? = null
    private var telephonyCallback: TelephonyCallback? = null
    private lateinit var recorder: TripRecorder

    private var lastFrameSentAt = 0L
    private var lastTurn: TurnUpdate? = null
    private var lastManeuver: Int? = null
    private var sentSplash = false

    /** No binding: the UI talks to the service through [Session] only. */
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) Log.i("CruiserNav", "service started")
        startAsForeground("Cruiser is starting…")
        Session.init(this)
        link = PodLink(this)
        link.watchAdapterState() // BT off/on cycles (GrapheneOS) kill the GATT server — track it
        // The GATT server only needs CONNECT (SCAN is for discovering new Pods).
        if (hasPerm(Manifest.permission.BLUETOOTH_CONNECT)) link.openServer()
        link.onCommand = ::onPodCommand
        observe()
        restorePairedAddress()
        recorder = TripRecorder(this)
        // GPS listening is trip-scoped: holding a 1 Hz session the whole time the
        // service runs starves nav apps (notably CoMaps' faster fixer) of it.
        refreshLocation()
        registerPhoneState()
        ComapsSource.start(this) // structured turn data from CoMaps, if installed+granted
        app.cruiser.notify.TurnSource.rebind(this) // ask OS to bind the listener if enabled
        // UI PIN submissions -> Pod (PIN typed on the connection dialog).
        scope.launch { Session.pinInput.collect { link.send(PodProtocol.pinFrame(it)) } }
        // Trips tab start/stop -> recorder (service consumes and clears).
        scope.launch {
            Session.tripCommand.collect { cmd ->
                when (cmd) {
                    null -> {}
                    true -> {
                        recorder.startListening()
                        recorder.startTrip(System.currentTimeMillis())
                        // Trip active: include the location FGS type so GPS keeps
                        // flowing with the screen off.
                        updateForeground(link.state.value)
                    }
                    false -> {
                        recorder.stopTrip()
                        recorder.stopListening()
                        updateForeground(link.state.value)
                    }
                }
                Session.tripCommand.value = null
            }
        }
        tickJob = scope.launch {
            // The Pod only advertises for ~30 s after ignition-on, so reconnect
            // attempts keep pacing in the background.
            var reconnectCountdown = 0
            var failedDirect = 0
            var disconnectedMs = 0L
            while (isActive) {
                if (link.state.value != LinkState.CONNECTED) {
                    disconnectedMs += CONNECT_PAUSE_MS
                    // A ride being recorded needs the service even without a Pod.
                    if (Session.trip.value == null && disconnectedMs > STOP_AFTER_DISCONNECTED_MS) {
                        // Pod never showed up (ignition off / forgotten) — don't
                        // burn battery scanning all day. "Connect" revives this.
                        if (BuildConfig.DEBUG) Log.i("CruiserNav", "stopping: no Pod for 5 min")
                        stopSelf()
                        break
                    }
                    if (--reconnectCountdown <= 0) {
                        // A remembered MAC that never completes (Pod re-paired,
                        // asleep, out of range) would loop forever: after a few
                        // dead attempts, give up the address and re-scan.
                        if (++failedDirect > 3) {
                            link.deviceAddress.value = null
                            failedDirect = 0
                            Log.i("CruiserNav", "direct connect kept failing — re-scan")
                        }
                        link.scanOrConnect(bleReady())
                        reconnectCountdown = 7
                    }
                } else {
                    reconnectCountdown = 0
                    failedDirect = 0
                    disconnectedMs = 0
                }
                val now = System.currentTimeMillis()
                if (link.state.value == LinkState.CONNECTED) tick()
                else if (BuildConfig.DEBUG) Log.i("CruiserNav", "tick skipped: ${link.state.value}")
                delay(CONNECT_PAUSE_MS)
            }
        }
    }

    /** Preconditions for any BLE call — checked, never assumed. */
    private fun bleReady(): Boolean =
        hasPerm(Manifest.permission.BLUETOOTH_CONNECT) &&
            hasPerm(Manifest.permission.BLUETOOTH_SCAN)

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> stopSelf()
            ACTION_FORGET -> forgetPod()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        if (BuildConfig.DEBUG) Log.i("CruiserNav", "service stopped")
        tickJob?.cancel()
        unregisterPhoneState()
        recorder.stopTrip()
        recorder.stopListening()
        link.disconnect()
        link.closeServer()
        link.unwatchAdapterState()
        ComapsSource.stop(this)
        // The state collector is cancelled below; mirror the final state directly
        // so the UI never keeps a dead "Connected" screen.
        Session.linkState.value = LinkState.DISCONNECTED
        Session.awaitingPin.value = false
        scope.cancel()
        super.onDestroy()
    }

    // ------------------------------------------------------------- connection

    private fun observe() {
        link.state.onEach { st ->
            Session.linkState.value = st
            if (BuildConfig.DEBUG) Log.i("CruiserNav", "link state → $st")
            updateForeground(st)
            if (st == LinkState.CONNECTED) handshake()
            if (st == LinkState.DISCONNECTED) {
                // Leaving the link (voluntary or watchdog) resets the Pod's screen side.
                lastFrameSentAt = 0
                sentSplash = false
                lastTurn = Session.currentTurn.value
                awaitPin(false)
            }
        }.launchIn(scope)

        Session.currentTurn.onEach { t ->
            if (t == null) lastManeuver = null
            if (lastTurn == null && t != null) sentSplash = false
            lastTurn = t
            if (t != null && link.state.value == LinkState.DISCONNECTED) link.scanOrConnect(bleReady())
            // Fire the route splash the instant nav data lands instead of waiting
            // for the next 1 Hz tick (up to a second of dead Pod screen).
            if (t != null && !sentSplash && link.state.value == LinkState.CONNECTED) {
                link.send(PodProtocol.routeStartedFrame(currentNight()))
                sentSplash = true
                lastFrameSentAt = System.currentTimeMillis()
                if (BuildConfig.DEBUG) Log.i("CruiserNav", "routeStarted frame sent (on turn)")
            }
        }.launchIn(scope)
    }

    private fun handshake() {
        val paired = isPaired()
        link.send(
            if (paired) PodProtocol.HANDSHAKE_RESUME else PodProtocol.HANDSHAKE_PAIR_NEW
        ).also {
            awaitPin(!paired)
        }
        // Pod wants ~200 ms of preamble after the resume handshake before SET_TIME (RE timing).
        if (paired) scope.launch { delay(RESUME_PREAMBLE_MS); sendSetTime() }
    }

    /** Commands from the Pod; only the PIN handshake reply matters to Cruiser. */
    private fun onPodCommand(cmd: ByteArray) {
        if (cmd.size < 2) return
        if (cmd[0] == 0x20.toByte()) {
            if (cmd[1] == 1.toByte()) {
                markPaired()
                awaitPin(false)
                sendSetTime()
            } // 0x20 00 = rejected PIN: keep awaitingPin, user retypes
        }
        // 0x03 = Pod firmware/version, 0x30 = serial — not needed for driving frames.
    }

    private fun sendSetTime() {
        val cal = java.util.Calendar.getInstance()
        link.send(
            PodProtocol.setTimeFrame(
                hour24 = cal.get(java.util.Calendar.HOUR_OF_DAY),
                minute = cal.get(java.util.Calendar.MINUTE),
                is12hFormat = !android.text.format.DateFormat.is24HourFormat(this),
            )
        )
    }

    fun isPaired(): Boolean {
        val p = getSharedPreferences("pod", Context.MODE_PRIVATE)
        return p.getBoolean(KEY_PAIRED, false) && p.getString(KEY_MAC, null) == link.deviceAddress.value
    }

    private fun markPaired() {
        val mac = link.deviceAddress.value ?: return
        getSharedPreferences("pod", Context.MODE_PRIVATE).edit()
            .putString(KEY_MAC, mac)
            .putBoolean(KEY_PAIRED, true)
            .apply()
        if (Session.savedPods.value.none { it.mac == mac }) {
            Session.savePod(mac, "Tripper Pod")
        }
    }

    fun forgetPod() {
        getSharedPreferences("pod", Context.MODE_PRIVATE).edit().clear().apply()
        link.deviceAddress.value = null
        if (bleReady()) link.disconnect()
        link.scanOrConnect(bleReady())
    }

    private fun awaitPin(on: Boolean) {
        // Compare the flow, not a second copy: the dialog's Cancel clears it
        // directly, and a later handshake must be able to show it again.
        if (Session.awaitingPin.value == on) return
        Session.awaitingPin.value = on
        updateForeground(link.state.value)
    }

    // ------------------------------------------------------------------ 1 Hz

    private fun tick() {
        val night = currentNight()
        val now = System.currentTimeMillis()
        if (Session.phoneCall.value != Session.PhoneCall.NONE) {
            // Call overlay is also the watchdog feed while the call lasts (RE behavior).
            link.send(PodProtocol.callFrame())
            lastFrameSentAt = now
            return
        }
        val turn = Session.currentTurn.value
        if (turn == null) {
            if (now - lastFrameSentAt > 2000) {
                if (Session.navActive.value) {
                    // Route is live but has no instruction yet: keep the Pod on
                    // the route-started icon instead of its clock screen.
                    link.send(PodProtocol.routeStartedFrame(night))
                } else {
                    link.send(PodProtocol.PING_FW)
                }
                lastFrameSentAt = now
            }
            return
        }
        if (turn.ended) {
            Session.postTurn(null)
            return
        }
        if (!sentSplash) {
            link.send(PodProtocol.routeStartedFrame(night))
            sentSplash = true
            lastFrameSentAt = now
            if (BuildConfig.DEBUG) Log.i("CruiserNav", "routeStarted frame sent")
            return
        }
        renderNavFrame(turn, night, now)
    }

    private fun renderNavFrame(turn: TurnUpdate, night: Boolean, now: Long) {
        val s = Session.settings.value
        // Roundabout: per-exit icons are hardware-observed (Likely). RE canonically
        // sends the base roundabout icon; the playground verifies which the Pod draws.
        val maneuver = when {
            turn.kind == app.cruiser.proto.TurnKind.ROUNDABOUT && turn.exit != null -> Icons.exitCode(turn.exit)
            turn.kind != app.cruiser.proto.TurnKind.UNKNOWN -> turn.kind.code()
            else -> lastManeuver ?: Icons.STRAIGHT.code
        }
        if (maneuver < 0) return // unmappable instruction: re-send keeps the last icon (RE behavior)
        lastManeuver = maneuver
        val bottom = when (s.bottomMode) {
            Session.BottomMode.ARRIVAL -> turn.arrivalSecondsFromNow
                ?.let { PodProtocol.Bottom.Arrival(it, s.twelveHourClock) }
            Session.BottomMode.REMAINING -> turn.remainingSeconds
                ?.let { PodProtocol.Bottom.Remaining(it) }
            Session.BottomMode.TOTAL -> turn.routeRemainingM
                ?.let { PodProtocol.Bottom.Total(it, s.imperial) }
            Session.BottomMode.CYCLE -> {
                val available = listOfNotNull(
                    turn.arrivalSecondsFromNow?.let { PodProtocol.Bottom.Arrival(it, s.twelveHourClock) },
                    turn.remainingSeconds?.let { PodProtocol.Bottom.Remaining(it) },
                    turn.routeRemainingM?.let { PodProtocol.Bottom.Total(it, s.imperial) },
                )
                if (available.isNotEmpty()) {
                    val interval = s.cycleIntervalSeconds.coerceAtLeast(1)
                    val idx = ((now / 1000) / interval % available.size).toInt()
                    available[idx]
                } else null
            }
        } ?: PodProtocol.Bottom.None
        val f = PodProtocol.navFrame(
            maneuver = maneuver,
            distanceM = turn.distanceM ?: 0,
            imperial = s.imperial,
            next = turn.next?.nextCode()?.takeIf { it >= 0 },
            nextDistanceM = turn.nextDistanceM,
            bottom = bottom,
            night = night,
            exit = if (turn.exit != null) turn.exit else 0,
        )
        link.send(f)
        if (BuildConfig.DEBUG) Log.i("CruiserNav", "nav frame: " + f.joinToString(" ") { "%02X".format(it) })
        lastFrameSentAt = now
    }

    // ------------------------------------------------------------- night ---

    private fun currentNight(): Boolean = when (Session.settings.value.nightMode) {
        Session.NightMode.DAY -> false
        Session.NightMode.NIGHT -> true
        Session.NightMode.AUTO -> {
            val lat = Session.lastLat
            val lon = Session.lastLon
            if (lat == null || lon == null) false else SunPhase.isNight(lat, lon, System.currentTimeMillis())
        }
    }

    @SuppressLint("MissingPermission")
    private fun refreshLocation() {
        if (!hasPerm(Manifest.permission.ACCESS_COARSE_LOCATION)) return
        val lm = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        lm.allProviders.mapNotNull { lm.getLastKnownLocation(it) }
            .firstOrNull()?.let {
                Session.lastLat = it.latitude
                Session.lastLon = it.longitude
            }
        for (provider in lm.allProviders) {
            try {
                lm.requestSingleUpdate(provider, object : LocationListener {
                    override fun onLocationChanged(location: android.location.Location) {
                        Session.lastLat = location.latitude
                        Session.lastLon = location.longitude
                    }
                }, null)
            } catch (_: Exception) {
            }
        }
    }

    private fun registerPhoneState() {
        if (!hasPerm(Manifest.permission.READ_PHONE_STATE)) return
        val tm = getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        val cb = object : TelephonyCallback(), TelephonyCallback.CallStateListener {
            override fun onCallStateChanged(state: Int) {
                Session.postCall(
                    when (state) {
                        TelephonyManager.CALL_STATE_RINGING -> Session.PhoneCall.INCOMING
                        TelephonyManager.CALL_STATE_OFFHOOK -> Session.PhoneCall.ACTIVE
                        else -> Session.PhoneCall.NONE
                    }
                )
            }
        }
        telephonyCallback = cb
        try {
            tm.registerTelephonyCallback(mainExecutor, cb)
        } catch (_: Exception) {
        }
    }

    private fun unregisterPhoneState() {
        val cb = telephonyCallback ?: return
        val tm = getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        try {
            tm.unregisterTelephonyCallback(cb)
        } catch (_: Exception) {
        }
    }

    private fun hasPerm(p: String): Boolean =
        checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED

    // ------------------------------------------------------------ pairing memory

    /** Mirror the remembered Pod MAC into the link so resume skips the scan. */
    private fun restorePairedAddress() {
        val mac = applicationContext.getSharedPreferences("pod", Context.MODE_PRIVATE)
            .getString(KEY_MAC, null)
        if (mac != null) link.deviceAddress.tryEmit(mac)
    }

    // ------------------------------------------------------------- notification

    private fun channel() {
        getSystemService(NotificationManager::class.java)
            .createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Tripper Pod", NotificationManager.IMPORTANCE_LOW)
            )
    }

    private fun startAsForeground(text: String) {
        channel()
        val n = NotificationCompat.Builder(this, CHANNEL_ID)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setContentText(text)
            .setContentIntent(
                PendingIntent.getActivity(
                    this, 0, Intent(this, MainActivity::class.java),
                    PendingIntent.FLAG_IMMUTABLE,
                )
            )
            .build()
        // A trip needs the location type, but only while the permission is held:
        // startForeground throws without it on Android 14+.
        val withLocation = Session.trip.value != null &&
            hasPerm(Manifest.permission.ACCESS_FINE_LOCATION)
        val type = FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE or
            (if (withLocation) FOREGROUND_SERVICE_TYPE_LOCATION else 0)
        startForeground(NOTIF_ID, n, type)
    }

    private fun updateForeground(state: LinkState) {
        startAsForeground(
            when (state) {
                LinkState.CONNECTED -> "Connected to the Tripper Pod"
                LinkState.CONNECTING -> "Connecting to Tripper Pod…"
                LinkState.SCANNING -> "Scanning for the Tripper Pod…"
                LinkState.BT_OFF -> "Bluetooth is off — turn it on"
                LinkState.NO_PERMISSIONS -> "Bluetooth permission required"
                LinkState.DISCONNECTED ->
                    if (Session.awaitingPin.value) "Enter the PIN shown on the Tripper Pod"
                    else "Tripper Pod disconnected — waiting for ignition"
            }
        )
    }
}
