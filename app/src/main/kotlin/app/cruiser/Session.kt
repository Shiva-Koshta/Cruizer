package app.cruiser

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
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
    private const val KEY_RADAR = "radar_enabled"

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
                        radarEnabled = p[booleanPreferencesKey(KEY_RADAR)] ?: true,
                        twelveHourClock = p[booleanPreferencesKey("clock_12h")] ?: false,
                        imperial = p[booleanPreferencesKey("imperial")]
                            ?: Locale.getDefault().country in setOf("US", "GB", "LR", "MM"),
                    )
                }
            }
        }
    }

    enum class NightMode { DAY, NIGHT, AUTO }

    /** What the Pod's bottom line shows while navigating. */
    enum class BottomMode { TOTAL, REMAINING, ARRIVAL }

    /** The app's own theme; independent of the night-mode setting. */
    enum class ThemeMode { SYSTEM, LIGHT, DARK }

    enum class PhoneCall { NONE, INCOMING, ACTIVE }

    data class Settings(
        val nightMode: NightMode = NightMode.AUTO,
        val bottomMode: BottomMode = BottomMode.ARRIVAL,
        val themeMode: ThemeMode = ThemeMode.SYSTEM,
        val radarEnabled: Boolean = true,
        val twelveHourClock: Boolean = false,
        /** imperial feet/miles; default metric (Indian standard units) */
        val imperial: Boolean = false,
    )

    private val _settings = MutableStateFlow(Settings())
    val settings: StateFlow<Settings> = _settings

    fun updateSettings(f: (Settings) -> Settings) {
        val next = f(_settings.value)
        _settings.value = next
        scope.launch {
            store?.edit {
                it[intPreferencesKey(KEY_NIGHT)] = next.nightMode.ordinal
                it[intPreferencesKey(KEY_BOTTOM)] = next.bottomMode.ordinal
                it[intPreferencesKey(KEY_THEME)] = next.themeMode.ordinal
                it[booleanPreferencesKey(KEY_RADAR)] = next.radarEnabled
                it[booleanPreferencesKey("clock_12h")] = next.twelveHourClock
                it[booleanPreferencesKey("imperial")] = next.imperial
            }
        }
    }

    /** Latest parsed turn from any nav app; null when no active route. */
    private val _currentTurn = MutableStateFlow<TurnUpdate?>(null)
    val currentTurn: StateFlow<TurnUpdate?> = _currentTurn

    fun postTurn(t: TurnUpdate?) {
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

    /** Best-effort speed-camera event in meters (null when none active). */
    private val _radar = MutableStateFlow<Int?>(null)
    val radar: StateFlow<Int?> = _radar

    fun postRadar(meters: Int?) {
        _radar.value = meters
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
