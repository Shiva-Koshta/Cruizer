package app.cruiser

import android.content.Context
import android.location.Location
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.cruiser.ble.LinkState
import app.cruiser.proto.TurnUpdate
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
 * Plain flows, no DI framework.
 */
object Session {

    private const val KEY_NIGHT = "night_mode"
    private const val KEY_BOTTOM = "bottom_mode"
    private const val KEY_THEME = "theme_mode"
    private const val KEY_CYCLE_INTERVAL = "cycle_interval"
    private const val KEY_SAVED_PODS = "saved_pods"
    private const val KEY_LIQUID_GLASS = "liquid_glass_mode"

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
                    val theme = NightMode.entries
                    val themeChoice = ThemeMode.entries
                        .getOrElse(p[intPreferencesKey(KEY_THEME)] ?: 0) { ThemeMode.SYSTEM }
                    val glassChoice = p[booleanPreferencesKey("liquid_glass")] ?: true
                    themeMode.value = themeChoice
                    isLiquidGlass.value = glassChoice
                    _settings.value = _settings.value.copy(
                        nightMode = NightMode.entries
                            .getOrElse(p[intPreferencesKey(KEY_NIGHT)] ?: 2) { NightMode.AUTO },
                        bottomMode = BottomMode.entries
                            .getOrElse(p[intPreferencesKey(KEY_BOTTOM)] ?: 2) { BottomMode.ARRIVAL },
                        themeMode = themeChoice,
                        cycleIntervalSeconds = p[intPreferencesKey(KEY_CYCLE_INTERVAL)] ?: 5,
                        twelveHourClock = p[booleanPreferencesKey("clock_12h")] ?: false,
                        // Indian standard units (metric: km, km/h, km/L, Liters) by default unless toggled
                        imperial = p[booleanPreferencesKey("imperial")] ?: false,
                        liquidGlass = glassChoice,
                    )
                    var pods = parseSavedPods(p[stringPreferencesKey(KEY_SAVED_PODS)])
                    val rememberedMac = context.getSharedPreferences("pod", Context.MODE_PRIVATE)
                        .getString("pod_mac", null)
                    if (rememberedMac != null && pods.none { it.mac == rememberedMac }) {
                        pods = pods + SavedPod(rememberedMac, "My Bike", BikeModel.METEOR_350)
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

    /** Official Royal Enfield catalogue models with displacement and verified average fuel economy. */
    enum class BikeModel(val label: String, val cc: Int, val kmpl: Double) {
        HUNTER_350("Hunter 350", 349, 36.2),
        CLASSIC_350("Classic 350", 349, 37.7),
        METEOR_350("Meteor 350", 349, 36.5),
        BULLET_350("Bullet 350", 349, 37.0),
        HIMALAYAN_450("Himalayan 450", 452, 30.0),
        GUERRILLA_450("Guerrilla 450", 452, 29.5),
        HIMALAYAN_411("Himalayan 411 / Scram", 411, 32.0),
        INTERCEPTOR_650("Interceptor 650", 648, 24.0),
        CONTINENTAL_GT_650("Continental GT 650", 648, 25.0),
        SUPER_METEOR_650("Super Meteor 650", 648, 23.5),
        SHOTGUN_650("Shotgun 650", 648, 23.5),
        CLASSIC_650("Classic 650 / Bear", 648, 24.0),
        CUSTOM("Custom Motorcycle", 0, 35.0);
    }

    /** A paired bike with its custom nickname and engine profile/mileage. */
    data class SavedPod(
        val mac: String,
        val nickname: String,
        val model: BikeModel = BikeModel.METEOR_350,
        val customCc: Int = 350,
        val customKmpl: Double = 36.5,
    ) {
        val effectiveKmpl: Double get() = if (model == BikeModel.CUSTOM) customKmpl else model.kmpl
        val effectiveCc: Int get() = if (model == BikeModel.CUSTOM) customCc else model.cc
    }

    data class Settings(
        val nightMode: NightMode = NightMode.AUTO,
        val bottomMode: BottomMode = BottomMode.ARRIVAL,
        val themeMode: ThemeMode = ThemeMode.SYSTEM,
        val cycleIntervalSeconds: Int = 5,
        val twelveHourClock: Boolean = false,
        /** Indian standard units (km, km/h, km/L, Liters) by default; imperial feet/miles when true */
        val imperial: Boolean = false,
        val liquidGlass: Boolean = true,
    )

    private val _settings = MutableStateFlow(Settings())
    val settings: StateFlow<Settings> = _settings
    val isLiquidGlass = MutableStateFlow(true)
    val themeMode = MutableStateFlow(ThemeMode.SYSTEM)

    private val _savedPods = MutableStateFlow<List<SavedPod>>(emptyList())
    val savedPods: StateFlow<List<SavedPod>> = _savedPods

    data class LiveRide(
        val isActive: Boolean = false,
        val startedAt: Long? = null,
        val elapsedSeconds: Long = 0L,
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
        val isConnected = linkState.value == LinkState.CONNECTED
        val speedKmh = Math.round(location.speed * 3.6f).coerceAtLeast(0)
        val current = _liveRide.value
        val isMoving = speedKmh > 1

        // Only start or tick the timer when connected, navigating, or actually moving
        if (!isConnected && !isMoving && !navActive.value) {
            lastLiveLoc = location
            return
        }

        val started = current.startedAt ?: System.currentTimeMillis()
        val elapsed = ((System.currentTimeMillis() - started) / 1000).coerceAtLeast(0)
        val prev = lastLiveLoc
        lastLiveLoc = location
        val delta = prev?.distanceTo(location) ?: 0f
        val add = if (prev != null && delta in 0.4f..150f) Math.round(delta) else 0
        val newDist = current.distanceM + add
        val elapsedHours = elapsed / 3600.0
        val avgSpeed = if (elapsedHours > 0.0005) Math.round((newDist / 1000.0) / elapsedHours).toInt() else speedKmh

        _liveRide.value = current.copy(
            isActive = true,
            startedAt = started,
            elapsedSeconds = elapsed,
            distanceM = newDist,
            currentSpeedKmh = speedKmh,
            avgSpeedKmh = avgSpeed,
            maxSpeedKmh = maxOf(current.maxSpeedKmh, speedKmh),
        )
    }

    fun resetLiveRide() {
        lastLiveLoc = null
        _liveRide.value = LiveRide()
    }

    fun updateSettings(f: (Settings) -> Settings) {
        val next = f(_settings.value)
        _settings.value = next
        if (themeMode.value != next.themeMode) themeMode.value = next.themeMode
        if (isLiquidGlass.value != next.liquidGlass) isLiquidGlass.value = next.liquidGlass
        scope.launch {
            store?.edit {
                it[intPreferencesKey(KEY_NIGHT)] = next.nightMode.ordinal
                it[intPreferencesKey(KEY_BOTTOM)] = next.bottomMode.ordinal
                it[intPreferencesKey(KEY_THEME)] = next.themeMode.ordinal
                it[intPreferencesKey(KEY_CYCLE_INTERVAL)] = next.cycleIntervalSeconds
                it[booleanPreferencesKey("clock_12h")] = next.twelveHourClock
                it[booleanPreferencesKey("imperial")] = next.imperial
                it[booleanPreferencesKey("liquid_glass")] = next.liquidGlass
            }
        }
    }

    fun savePod(
        mac: String,
        nickname: String,
        model: BikeModel = BikeModel.METEOR_350,
        customCc: Int = 350,
        customKmpl: Double = 36.5,
    ) {
        val trimmed = nickname.trim().ifEmpty { "Tripper Pod" }
        val current = _savedPods.value.filter { it.mac != mac }
        val updated = current + SavedPod(mac, trimmed, model, customCc, customKmpl)
        _savedPods.value = updated
        persistSavedPods(updated)
    }

    fun removeSavedPod(mac: String) {
        val updated = _savedPods.value.filter { it.mac != mac }
        _savedPods.value = updated
        persistSavedPods(updated)
    }

    fun activeBike(currentMac: String? = null): SavedPod? {
        val pods = _savedPods.value
        if (pods.isEmpty()) return null
        return if (currentMac != null) pods.firstOrNull { it.mac == currentMac } ?: pods.first()
        else pods.first()
    }

    private fun persistSavedPods(pods: List<SavedPod>) {
        scope.launch {
            store?.edit {
                it[stringPreferencesKey(KEY_SAVED_PODS)] = pods.joinToString("\n") { p ->
                    "${p.mac}|${p.nickname}|${p.model.ordinal}|${p.customCc}|${p.customKmpl}"
                }
            }
        }
    }

    private fun parseSavedPods(raw: String?): List<SavedPod> {
        if (raw.isNullOrBlank()) return emptyList()
        return raw.lineSequence()
            .mapNotNull { line ->
                val parts = line.split('|')
                if (parts.size >= 2 && parts[0].isNotBlank()) {
                    val mac = parts[0].trim()
                    val nickname = parts[1].trim()
                    val model = parts.getOrNull(2)?.toIntOrNull()?.let { ord ->
                        BikeModel.entries.getOrNull(ord)
                    } ?: BikeModel.METEOR_350
                    val customCc = parts.getOrNull(3)?.toIntOrNull() ?: model.cc
                    val customKmpl = parts.getOrNull(4)?.toDoubleOrNull() ?: model.kmpl
                    SavedPod(mac, nickname, model, customCc, customKmpl)
                } else null
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
}
