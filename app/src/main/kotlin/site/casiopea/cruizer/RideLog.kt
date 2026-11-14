package site.casiopea.cruizer

import android.location.Location
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Live ride clock and distance. The clock follows the route, not the Pod or GPS:
 * GPS only enriches it when a fix arrives.
 */
object RideLog {

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

    /** A location fix actually arrived; distance/speed are real, not just zero. */
    val hasFix = MutableStateFlow(false)
    private var lastLoc: Location? = null

    fun update(location: Location, navActive: Boolean) {
        hasFix.value = true
        // Trip timer runs while a route is live — with or without the Pod.
        // A BLE drop mid-ride pauses nothing: the Pod merely loses its screen feed.
        if (!navActive) {
            if (_liveRide.value.isActive) reset()
            lastLoc = null
            return
        }
        val speedKmh = Math.round(location.speed * 3.6f).coerceAtLeast(0)
        val current = ensureStarted()
        val started = current.startedAt ?: System.currentTimeMillis()
        val elapsed = ((System.currentTimeMillis() - started) / 1000).coerceAtLeast(0)
        val prev = lastLoc
        lastLoc = location
        val delta = prev?.distanceTo(location) ?: 0f
        val dt = if (prev != null && location.time > prev.time) ((location.time - prev.time) / 1000f).coerceIn(1f, 30f) else 1f
        val maxRealisticDelta = 50f * dt // Up to 180 km/h (50 m/s) allowed over elapsed interval
        val add = if (prev != null && delta in 0.4f..maxRealisticDelta) Math.round(delta) else 0
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

    fun reset() {
        lastLoc = null
        hasFix.value = false // next ride must not show the previous ride's fixes as real
        _liveRide.value = LiveRide()
    }

    fun ensureStarted(): LiveRide {
        val current = _liveRide.value
        if (current.isActive && current.startedAt != null) return current
        val next = current.copy(isActive = true, startedAt = current.startedAt ?: System.currentTimeMillis())
        _liveRide.value = next
        return next
    }
}
