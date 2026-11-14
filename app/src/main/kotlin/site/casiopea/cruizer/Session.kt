package site.casiopea.cruizer

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
import site.casiopea.cruizer.ble.LinkState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Process-wide DataStore shared by the UI and the Pod service. */
internal val Context.cruiserStore by preferencesDataStore("cruizer")

/**
 * Process-wide shared state between the turn source, the BLE service and the UI.
 * Plain flows, no DI framework.
 */
object Session {

    private const val KEY_NIGHT = "night_mode"

    // Renamed when "time left" was dropped: the Pod's bottom line has no
    // time-remaining format, and old ordinals would land on the wrong mode.
    private const val KEY_BOTTOM = "bottom_mode_v2"
    private const val KEY_THEME = "theme_mode"
    private const val KEY_CYCLE_INTERVAL = "cycle_interval"
    private const val KEY_SAVED_PODS = "saved_pods"
    private const val KEY_LIQUID_GLASS = "liquid_glass_mode"
    private const val KEY_ACTIVE_BIKE_MAC = "active_bike_mac"
    private const val KEY_ONBOARDING_COMPLETED = "onboarding_completed"
    private const val KEY_DISCONNECT_TIMEOUT = "disconnect_timeout"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile private var store: DataStore<Preferences>? = null
    @Volatile private var initialized = false

    /** A settings change landed before the stored values finished loading. */
    @Volatile private var settingsTouched = false

    /** A pod/onboarding change landed before the stored values finished loading. */
    @Volatile private var stateTouched = false

    @Volatile private var appContext: Context? = null

    /** Load stored settings once and keep persisting every change from now on. */
    fun init(context: Context) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            appContext = context.applicationContext
            store = context.applicationContext.cruiserStore
            initialized = true
            scope.launch {
                // A corrupt/unreadable store must not leave the app stuck on
                // defaults forever: fall through and let onboarding run once.
                val p = runCatching { store?.data?.first() }.getOrNull() ?: return@launch
                val themeChoice = ThemeMode.entries
                    .getOrElse(p[intPreferencesKey(KEY_THEME)] ?: 0) { ThemeMode.SYSTEM }
                val glassChoice = p[booleanPreferencesKey("liquid_glass")] ?: true
                // Don't clobber a setting the rider changed while we were reading.
                if (!settingsTouched) {
                    themeMode.value = themeChoice
                    isLiquidGlass.value = glassChoice
                    _settings.value = _settings.value.copy(
                        nightMode = NightMode.entries
                            .getOrElse(p[intPreferencesKey(KEY_NIGHT)] ?: 2) { NightMode.AUTO },
                        bottomMode = BottomMode.entries
                            .getOrElse(p[intPreferencesKey(KEY_BOTTOM)] ?: 1) { BottomMode.ARRIVAL },
                        themeMode = themeChoice,
                        cycleIntervalSeconds = p[intPreferencesKey(KEY_CYCLE_INTERVAL)] ?: 5,
                        twentyFourHourClock = p[booleanPreferencesKey("clock_24h")] ?: false,
                        // Indian standard units (metric: km, km/h, km/L, Liters) by default unless toggled
                        imperial = p[booleanPreferencesKey("imperial")] ?: false,
                        liquidGlass = glassChoice,
                        disconnectTimeoutMinutes = p[intPreferencesKey(KEY_DISCONNECT_TIMEOUT)] ?: 60,
                    )
                }
                if (!stateTouched) {
                    var pods = parseSavedPods(p[stringPreferencesKey(KEY_SAVED_PODS)])
                    val rememberedMac = context.getSharedPreferences("pod", Context.MODE_PRIVATE)
                        .getString("pod_mac", null)
                    if (rememberedMac != null && pods.none { it.mac == rememberedMac }) {
                        pods = pods + SavedPod(rememberedMac, "My Bike", BikeModel.METEOR_350)
                        persistSavedPods(pods)
                    }
                    _savedPods.value = pods
                    val storedActive = p[stringPreferencesKey(KEY_ACTIVE_BIKE_MAC)]
                    activeBikeMac.value = storedActive ?: rememberedMac ?: pods.firstOrNull()?.mac
                    onboardingCompleted.value =
                        p[booleanPreferencesKey(KEY_ONBOARDING_COMPLETED)] ?: false
                }
            }
        }
    }

    enum class NightMode { DAY, NIGHT, AUTO }

    /** What the Pod's bottom line shows while navigating. */
    enum class BottomMode { TOTAL, ARRIVAL, CYCLE }

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
        val twentyFourHourClock: Boolean = false,
        /** Indian standard units (km, km/h, km/L, Liters) by default; imperial feet/miles when true */
        val imperial: Boolean = false,
        val liquidGlass: Boolean = true,
        val disconnectTimeoutMinutes: Int = 60,
    ) {
        val is12Hour: Boolean get() = !twentyFourHourClock
    }

    private val _settings = MutableStateFlow(Settings())
    val settings: StateFlow<Settings> = _settings

    // Narrow mirrors of two settings fields, on purpose: CruiserGlassCard and the
    // pill bar are on the 120 fps path, so they must not recompose on every
    // settings change (e.g. each keystroke in the timeout field).
    val isLiquidGlass = MutableStateFlow(true)
    val themeMode = MutableStateFlow(ThemeMode.SYSTEM)
    val onboardingCompleted = MutableStateFlow(false)

    private val _savedPods = MutableStateFlow<List<SavedPod>>(emptyList())
    val savedPods: StateFlow<List<SavedPod>> = _savedPods
    val activeBikeMac = MutableStateFlow<String?>(null)
    val pinRejected = MutableStateFlow(false)

    /** Night-mode seed and the ride log both need the fix; the ride math lives in [RideLog]. */
    fun updateLiveLocation(location: Location) {
        lastLat = location.latitude
        lastLon = location.longitude
        RideLog.update(location, NavRoute.navActive.value)
    }

    fun updateSettings(f: (Settings) -> Settings) {
        settingsTouched = true
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
                it[booleanPreferencesKey("clock_24h")] = next.twentyFourHourClock
                it[booleanPreferencesKey("imperial")] = next.imperial
                it[booleanPreferencesKey("liquid_glass")] = next.liquidGlass
                it[intPreferencesKey(KEY_DISCONNECT_TIMEOUT)] = next.disconnectTimeoutMinutes
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
        stateTouched = true
        val sanitized = nickname.replace('|', '/').replace('\n', ' ').trim().ifEmpty { "Tripper Pod" }
        val current = _savedPods.value.filter { it.mac != mac }
        val updated = current + SavedPod(mac, sanitized, model, customCc, customKmpl)
        _savedPods.value = updated
        if (activeBikeMac.value == null || activeBikeMac.value == mac) {
            selectBike(mac)
        }
        persistSavedPods(updated)
    }

    fun completeOnboarding() {
        stateTouched = true
        onboardingCompleted.value = true
        scope.launch {
            store?.edit {
                it[booleanPreferencesKey(KEY_ONBOARDING_COMPLETED)] = true
            }
        }
    }

    /** Re-show the permission/onboarding screen; persisting "done" stays untouched. */
    fun showOnboarding() {
        stateTouched = true
        onboardingCompleted.value = false
    }

    fun selectBike(mac: String) {
        if (mac.isBlank()) return
        stateTouched = true
        activeBikeMac.value = mac
        scope.launch {
            store?.edit {
                it[stringPreferencesKey(KEY_ACTIVE_BIKE_MAC)] = mac
            }
        }
    }

    fun removeSavedPod(mac: String) {
        stateTouched = true
        val updated = _savedPods.value.filter { it.mac != mac }
        _savedPods.value = updated
        persistSavedPods(updated)
        // Drop the pairing memory too, or init() re-adds this bike on the next
        // launch ("My Bike", Meteor 350) and the delete looks undone.
        forgetRememberedMac(mac)
        if (activeBikeMac.value == mac) {
            val fallback = updated.firstOrNull()?.mac.orEmpty()
            if (fallback.isNotEmpty()) selectBike(fallback)
            else activeBikeMac.value = null
        }
    }

    /** Clear SharedPreferences("pod") when it remembers the Pod being removed. */
    private fun forgetRememberedMac(mac: String) {
        val ctx = appContext ?: return
        val prefs = ctx.getSharedPreferences("pod", Context.MODE_PRIVATE)
        if (prefs.getString("pod_mac", null) == mac) {
            prefs.edit().remove("pod_mac").remove("pod_paired").apply()
        }
    }

    fun activeBike(currentMac: String? = null): SavedPod? {
        val pods = _savedPods.value
        if (pods.isEmpty()) return null
        val target = currentMac ?: activeBikeMac.value
        return if (target != null) pods.firstOrNull { it.mac == target } ?: pods.first()
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

    /** Google Maps screen-read (accessibility) service currently connected: banner hides when true. */
    val mapsA11yConnected = MutableStateFlow(false)

    /** Exactly what the Pod is showing right now (null = clock/PING). Set by the service. */
    val podScreen = MutableStateFlow<site.casiopea.cruizer.proto.PodProtocol.PodScreen?>(null)

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
        if (!site.casiopea.cruizer.BuildConfig.DEBUG) return
        // Also into logcat so `adb logcat -d -s CruiserNav` pulls it without the UI.
        Log.i("CruizerNav", line)
        recentNotifs.value = (listOf(line) + recentNotifs.value).take(15)
    }

    /** Coarse location cache for the AUTO night mode (sunrise/sunset). */
    @Volatile var lastLat: Double? = null
    @Volatile var lastLon: Double? = null
}
