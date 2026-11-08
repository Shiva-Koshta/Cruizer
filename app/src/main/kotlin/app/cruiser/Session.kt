package app.cruiser

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import android.location.Location
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.cruiser.ble.LinkState
import app.cruiser.proto.TurnUpdate
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Process-wide DataStore shared by the UI and the Pod service. */
internal val Context.cruiserStore by preferencesDataStore("cruiser")

/**
 * Process-wide shared state between the turn source, the BLE service and the UI.
 * Plain flows, no DI framework. Call [init] once when the process starts (either
 * entry point is fine); after that every setting change persists immediately —
 * not only while the Pod service happens to be running.
 */
object Session {

    private const val KEY_NIGHT = "night_mode"
    private const val KEY_BOTTOM = "bottom_mode"
    private const val KEY_THEME = "theme_mode"
    private const val KEY_CYCLE_INTERVAL = "cycle_interval"
    private const val KEY_SAVED_PODS = "saved_pods"
    private const val KEY_BIKE_MODEL = "bike_model"
    private const val KEY_CUSTOM_KMPL = "custom_kmpl"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile private var store: DataStore<Preferences>? = null
    @Volatile private var initialized = false

    /** Load stored settings once and keep persisting every change from now on. */
    fun init(context: Context) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            store = context.applicationContext.cruiserStore
            initialized = true
            scope.launch {
                store?.data?.first()?.let { p ->
                    _settings.value = _settings.value.copy(
                        nightMode = NightMode.entries
                            .getOrElse(p[intPreferencesKey(KEY_NIGHT)] ?: 2) { NightMode.AUTO },
                        bottomMode = BottomMode.entries
                            .getOrElse(p[intPreferencesKey(KEY_BOTTOM)] ?: 2) { BottomMode.ARRIVAL },
                        themeMode = ThemeMode.entries
                            .getOrElse(p[intPreferencesKey(KEY_THEME)] ?: 0) { ThemeMode.SYSTEM },
                        cycleIntervalSeconds = p[intPreferencesKey(KEY_CYCLE_INTERVAL)] ?: 5,
                        twelveHourClock = p[booleanPreferencesKey("clock_12h")] ?: false,
                        imperial = p[booleanPreferencesKey("imperial")]
                            ?: Locale.getDefault().country in setOf("US", "GB", "LR", "MM"),
                        bikeModel = BikeModel.entries
                            .getOrElse(p[intPreferencesKey(KEY_BIKE_MODEL)] ?: 0) { BikeModel.J_SERIES_350 },
                        customKmpl = p[doublePreferencesKey(KEY_CUSTOM_KMPL)] ?: 35.0,
                    )
                    var pods = parseSavedPods(p[stringPreferencesKey(KEY_SAVED_PODS)])
                    val rememberedMac = context.getSharedPreferences("pod", Context.MODE_PRIVATE)
                        .getString("pod_mac", null)
                    if (rememberedMac != null && pods.none { it.mac == rememberedMac }) {
                        pods = pods + SavedPod(rememberedMac, "My Bike")
                        persistSavedPods(pods)
                    }
                    _savedPods.value = pods
                }
            }
        }
    }

    enum class NightMode { DAY, NIGHT, AUTO }

    /** What the Pod's bottom line shows while navigating. */
    enum class BottomMode { TOTAL, REMAINING, ARRIVAL, CYCLE }

    /** The app's own theme; independent of the night-mode setting. */
    enum class ThemeMode { SYSTEM, LIGHT, DARK }

    enum class PhoneCall { NONE, INCOMING, ACTIVE }

    enum class BikeModel(val label: String, val kmpl: Double) {
        J_SERIES_350("Royal Enfield 350cc (Meteor/Classic/Hunter)", 36.0),
        TWINS_650("Royal Enfield 650cc (Interceptor/GT/Super Meteor)", 25.0),
        SHERPA_450("Royal Enfield 450cc (Himalayan/Guerrilla)", 30.0),
        HIMALAYAN_411("Royal Enfield 411cc (Himalayan/Scram)", 32.0),
        CUSTOM("Custom Mileage", 35.0);
    }

    data class SavedPod(
        val mac: String,
        val nickname: String,
    )

    data class Settings(
        val nightMode: NightMode = NightMode.AUTO,
        val bottomMode: BottomMode = BottomMode.ARRIVAL,
        val themeMode: ThemeMode = ThemeMode.SYSTEM,
        val cycleIntervalSeconds: Int = 5,
        val twelveHourClock: Boolean = false,
        /** imperial feet/miles; default metric (Indian standard units) */
        val imperial: Boolean = false,
        val bikeModel: BikeModel = BikeModel.J_SERIES_350,
        val customKmpl: Double = 35.0,
    )

    private val _settings = MutableStateFlow(Settings())
    val settings: StateFlow<Settings> = _settings

    private val _savedPods = MutableStateFlow<List<SavedPod>>(emptyList())
    val savedPods: StateFlow<List<SavedPod>> = _savedPods

    data class LiveRide(
        val startedAt: Long = System.currentTimeMillis(),
        val distanceM: Int = 0,
        val currentSpeedKmh: Int = 0,
        val avgSpeedKmh: Int = 0,
        val maxSpeedKmh: Int = 0,
    )

    private val _liveRide = MutableStateFlow(LiveRide())
    val liveRide: StateFlow<LiveRide> = _liveRide
    private var lastLiveLoc: Location? = null

    fun updateLiveLocation(location: Location) {
        lastLat = location.latitude
        lastLon = location.longitude
        val prev = lastLiveLoc
        lastLiveLoc = location
        val delta = prev?.distanceTo(location) ?: 0f
        val add = if (prev != null && delta in 0.4f..150f) Math.round(delta) else 0
        val current = _liveRide.value
        val newDist = current.distanceM + add
        val speedKmh = Math.round(location.speed * 3.6f).coerceAtLeast(0)
        val elapsedHours = (System.currentTimeMillis() - current.startedAt) / 3600000.0
        val avgSpeed = if (elapsedHours > 0.0005) Math.round((newDist / 1000.0) / elapsedHours).toInt() else speedKmh
        _liveRide.value = current.copy(
            distanceM = newDist,
            currentSpeedKmh = speedKmh,
            avgSpeedKmh = avgSpeed,
            maxSpeedKmh = maxOf(current.maxSpeedKmh, speedKmh),
        )
    }

    fun resetLiveRide() {
        lastLiveLoc = null
        _liveRide.value = LiveRide(startedAt = System.currentTimeMillis())
    }

    fun updateSettings(f: (Settings) -> Settings) {
        val next = f(_settings.value)
        _settings.value = next
        scope.launch {
            store?.edit {
                it[intPreferencesKey(KEY_NIGHT)] = next.nightMode.ordinal
                it[intPreferencesKey(KEY_BOTTOM)] = next.bottomMode.ordinal
                it[intPreferencesKey(KEY_THEME)] = next.themeMode.ordinal
                it[intPreferencesKey(KEY_CYCLE_INTERVAL)] = next.cycleIntervalSeconds
                it[booleanPreferencesKey("clock_12h")] = next.twelveHourClock
                it[booleanPreferencesKey("imperial")] = next.imperial
                it[intPreferencesKey(KEY_BIKE_MODEL)] = next.bikeModel.ordinal
                it[doublePreferencesKey(KEY_CUSTOM_KMPL)] = next.customKmpl
            }
        }
    }

    fun savePod(mac: String, nickname: String) {
        val trimmed = nickname.trim().ifEmpty { "Tripper Pod" }
        val current = _savedPods.value.filter { it.mac != mac }
        val updated = current + SavedPod(mac, trimmed)
        _savedPods.value = updated
        persistSavedPods(updated)
    }

    fun removeSavedPod(mac: String) {
        val updated = _savedPods.value.filter { it.mac != mac }
        _savedPods.value = updated
        persistSavedPods(updated)
    }

    private fun persistSavedPods(pods: List<SavedPod>) {
        scope.launch {
            store?.edit {
                it[stringPreferencesKey(KEY_SAVED_PODS)] = pods.joinToString("\n") { p -> "${p.mac}|${p.nickname}" }
            }
        }
    }

    private fun parseSavedPods(raw: String?): List<SavedPod> {
        if (raw.isNullOrBlank()) return emptyList()
        return raw.lineSequence()
            .mapNotNull { line ->
                val parts = line.split('|')
                if (parts.size >= 2 && parts[0].isNotBlank()) SavedPod(parts[0].trim(), parts[1].trim())
                else null
            }
            .toList()
    }

    /** Latest parsed turn from any nav app; null when no active route. */
    private val _currentTurn = MutableStateFlow<TurnUpdate?>(null)
    val currentTurn: StateFlow<TurnUpdate?> = _currentTurn

    /** A nav app has an ongoing navigation notification (even before any turn text). */
    val navActive = MutableStateFlow(false)

    fun postTurn(t: TurnUpdate?) {
        if (BuildConfig.DEBUG) {
            Log.i(
                "CruiserNav",
                "turn posted: ${t?.let { "${it.kind} dist=${it.distanceM ?: "-"} arr=${it.arrivalSecondsFromNow ?: "-"}" } ?: "null"}",
            )
        }
        _currentTurn.value = t
    }

    /** BLE link state, mirrored in by the Pod service for the UI. */
    val linkState = MutableStateFlow(LinkState.DISCONNECTED)

    /** Pod shows a PIN to confirm — the UI opens the PIN dialog when true. */
    val awaitingPin = MutableStateFlow(false)

    /** UI -> service channel for the 6-digit PIN the user typed (manual DI). */
    val pinInput = MutableSharedFlow<String>(extraBufferCapacity = 4)

    /**
     * UI (Trips tab) -> service: start/stop the ride. StateFlow, not SharedFlow,
     * so a command survives the cold start of the service; the service consumes
     * it and clears back to null.
     */
    val tripCommand = MutableStateFlow<Boolean?>(null)

    private val _phoneCall = MutableStateFlow(PhoneCall.NONE)
    val phoneCall: StateFlow<PhoneCall> = _phoneCall

    fun postCall(c: PhoneCall) {
        _phoneCall.value = c
    }

    /** Debug raw-notification capture (Settings tab on debug builds), newest first. */
    val recentNotifs = MutableStateFlow<List<String>>(emptyList())
    fun captureNotif(line: String) {
        if (!app.cruiser.BuildConfig.DEBUG) return
        // Also into logcat so `adb logcat -d -s CruiserNav` pulls it without the UI.
        Log.i("CruiserNav", line)
        recentNotifs.value = (listOf(line) + recentNotifs.value).take(15)
    }

    /** Coarse location cache for the AUTO night mode (sunrise/sunset). */
    @Volatile var lastLat: Double? = null
    @Volatile var lastLon: Double? = null

    /** Live ride; null when no ride is active. */
    data class TripState(val startedAt: Long, val distanceM: Int, val lastLoc: android.location.Location?)

    private val _trip = MutableStateFlow<TripState?>(null)
    val trip: StateFlow<TripState?> = _trip

    fun startTrip(startedAt: Long) {
        _trip.value = TripState(startedAt, 0, null)
    }

    fun updateTrip(distanceM: Int, lastLoc: android.location.Location?) {
        _trip.value = _trip.value?.copy(distanceM = distanceM, lastLoc = lastLoc ?: _trip.value?.lastLoc)
    }

    fun endTrip() {
        _trip.value = null
    }

}
