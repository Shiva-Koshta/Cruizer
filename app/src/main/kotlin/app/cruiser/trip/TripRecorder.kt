package app.cruiser.trip

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import app.cruiser.Session
import java.util.concurrent.Executors
import kotlin.math.roundToInt

/**
 * Records the current ride: GPS points stream into a GPX file, aggregates
 * publish to [Session]'s live trip state. Owned by the Pod service; a trip is
 * started/stopped from the Trips tab via [Session.trip].
 */
class TripRecorder(private val context: Context) : LocationListener {

    private var gpx: TripGpx? = null

    override fun onLocationChanged(location: Location) {
        Session.lastLat = location.latitude
        Session.lastLon = location.longitude
        val trip = Session.trip.value ?: return
        gpx?.point(location.latitude, location.longitude, location.time)
        val prev = trip.lastLoc
        val delta = prev?.distanceTo(location) ?: 0f
        // Discard absurd jumps (GPS glitches) and in-place jitter — but always
        // re-anchor below, or one glitch freezes the distance for the whole ride.
        val add = if (prev != null && delta in 0.3f..150f) delta.roundToInt() else 0
        Session.updateTrip(distanceM = trip.distanceM + add, lastLoc = location)
    }

    @Deprecated("kept for platform consistency")
    override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) {}

    @SuppressLint("MissingPermission") // permission checked above
    fun startListening() {
        if (!hasPerm(Manifest.permission.ACCESS_FINE_LOCATION)) return
        val lm = lm()
        lm.allProviders.forEach { p ->
            try {
                lm.requestLocationUpdates(p, 1000, 0f, this, context.mainLooper)
            } catch (_: Exception) {
            }
        }
    }

    fun stopListening() {
        lm().removeUpdates(this)
    }

    fun startTrip(now: Long) {
        val g = TripGpx(context, now)
        Session.lastLat?.let { lat -> Session.lastLon?.let { lon -> g.point(lat, lon, now) } }
        gpx = g
        Session.startTrip(now)
    }

    fun stopTrip() {
        val trip = Session.trip.value ?: return
        val file = gpx
        gpx = null
        // Slow-disk writes (GPX footer + index) off the main thread.
        persist.execute {
            try {
                file?.close()
                val bike = Session.activeBike()
                TripStore.save(
                    context,
                    Trip(
                        id = trip.startedAt,
                        endedAt = System.currentTimeMillis(),
                        distanceM = trip.distanceM,
                        bikeName = bike?.nickname,
                    ),
                )
            } finally {
                // Even a disk error must not leave the UI stuck on "Recording…".
                Session.endTrip()
            }
        }
    }

    private fun lm(): LocationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    private val persist = Executors.newSingleThreadExecutor { r -> Thread(r, "trip-gpx").apply { isDaemon = true } }

    private fun hasPerm(p: String) =
        context.checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED
}
