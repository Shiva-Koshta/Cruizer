package site.casiopea.cruizer

import android.util.Log
import site.casiopea.cruizer.proto.TurnUpdate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Route ownership and the latest parsed turn. Only the source that started a
 * ride may end it; the ride clock follows the route, not the Pod service.
 */
object NavRoute {

    /** Latest parsed turn from any nav app; null when no active route. */
    private val _currentTurn = MutableStateFlow<TurnUpdate?>(null)
    val currentTurn: StateFlow<TurnUpdate?> = _currentTurn

    /** A nav app has an ongoing navigation notification (even before any turn text). */
    val navActive = MutableStateFlow(false)

    /** Package of the nav app that produced the latest turn/nav activity (home-screen banner logic). */
    val lastNavSource = MutableStateFlow<String?>(null)

    fun postTurn(t: TurnUpdate?) {
        if (BuildConfig.DEBUG) {
            Log.i(
                "CruizerNav",
                "turn posted: ${t?.let { "${it.kind} dist=${it.distanceM ?: "-"} arr=${it.arrivalSecondsFromNow ?: "-"}" } ?: "null"}",
            )
        }
        _currentTurn.value = t
    }

    /**
     * A source has live route data: it takes ownership of the route (last writer
     * wins) and, unless the update is the arrival, marks navigation active.
     */
    fun beginRoute(pkg: String, ended: Boolean = false) {
        lastNavSource.value = pkg
        navActive.value = !ended
        // The ride clock follows the route, not the Pod: it must start even when
        // the service (and therefore its collector) is not running.
        if (ended) RideLog.reset() else RideLog.ensureStarted()
    }

    /**
     * Only the source that currently owns the route may end it. Without this a
     * browse screen in one app tore down a ride another app was still guiding.
     */
    fun endRoute(pkg: String) {
        if (lastNavSource.value != pkg) return
        lastNavSource.value = null
        navActive.value = false
        postTurn(null)
        RideLog.reset()
    }
}
