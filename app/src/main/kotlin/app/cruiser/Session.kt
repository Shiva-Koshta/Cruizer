package app.cruiser

import app.cruiser.ble.LinkState
import app.cruiser.proto.TurnUpdate
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Process-wide shared state between the turn source, the BLE service and the UI.
 * Plain flows, no DI framework.
 */
object Session {

    enum class NightMode { DAY, NIGHT, AUTO }

    /** What the Pod's bottom line shows while navigating. */
    enum class BottomMode { TOTAL, REMAINING, ARRIVAL }

    enum class PhoneCall { NONE, INCOMING, ACTIVE }

    data class Settings(
        val nightMode: NightMode = NightMode.AUTO,
        val bottomMode: BottomMode = BottomMode.ARRIVAL,
        val radarEnabled: Boolean = true,
        val twelveHourClock: Boolean = false,
        /** imperial feet/miles; default metric (Indian standard units) */
        val imperial: Boolean = false,
    )

    private val _settings = MutableStateFlow(Settings())
    val settings: StateFlow<Settings> = _settings

    fun updateSettings(f: (Settings) -> Settings) {
        _settings.value = f(_settings.value)
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

    /** Coarse location cache for the AUTO night mode (sunrise/sunset). */
    @Volatile var lastLat: Double? = null
    @Volatile var lastLon: Double? = null
}
