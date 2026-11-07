package app.cruiser.trip

import android.content.ContentValues
import android.content.Context
import android.provider.MediaStore
import java.io.File
import java.io.FileWriter
import org.json.JSONArray
import org.json.JSONObject

/**
 * One ride: aggregates in the index, track points in a GPX file written live.
 * Keeping tracks as GPX from the start means "export" is a file copy.
 */
data class Trip(
    val id: Long, // startedAt millis of the ride — the one key a ride reliably has
    val endedAt: Long,
    val distanceM: Int,
) {
    val seconds: Int get() = ((endedAt - id) / 1000).toInt()
    val gpxName: String get() = "cruiser-trip-$id.gpx"
}

/**
 * Trip history backed by a JSON index file + one GPX track file per trip in
 * filesDir/trips/. Deliberately no database for one list.
 */
object TripStore {

    private fun dir(context: Context) = File(context.filesDir, "trips").apply { mkdirs() }
    private fun index(context: Context) = File(dir(context), "index.json")

    fun save(context: Context, trip: Trip) {
        val arr = if (index(context).exists()) JSONArray(index(context).readText()) else JSONArray()
        // one entry per ride id
        val kept = (0 until arr.length()).filterNot { arr.getJSONObject(it).optLong("id") == trip.id }
        val next = JSONArray()
        kept.forEach { next.put(arr.getJSONObject(it)) }
        next.put(
            JSONObject()
                .put("id", trip.id)
                .put("endedAt", trip.endedAt)
                .put("distanceM", trip.distanceM)
        )
        index(context).writeText(next.toString())
    }

    fun history(context: Context): List<Trip> {
        if (!index(context).exists()) return emptyList()
        val arr = JSONArray(index(context).readText())
        return (0 until arr.length())
            .map { arr.getJSONObject(it) }
            .map { Trip(it.getLong("id"), it.getLong("endedAt"), it.getInt("distanceM")) }
            .sortedByDescending { it.id }
    }

    fun gpxFile(context: Context, trip: Trip): File = File(dir(context), trip.gpxName)

    fun gpxFile(context: Context, id: Long): File = File(dir(context), "cruiser-trip-$id.gpx")

    /** Copies a trip's GPX to Downloads; returns the display name or null on failure. */
    fun export(context: Context, trip: Trip): String? {
        val src = gpxFile(context, trip)
        if (!src.exists()) return null
        return try {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, trip.gpxName)
                put(MediaStore.Downloads.MIME_TYPE, "application/gpx+xml")
            }
            val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: return null
            context.contentResolver.openOutputStream(uri)?.use { out ->
                src.inputStream().use { it.copyTo(out) }
            } ?: return null
            trip.gpxName
        } catch (_: Exception) {
            null
        }
    }
}

/** Live GPX writer for one trip: opened at start, one `<trkpt>` per point, closed at end. */
class TripGpx(context: Context, private val tripId: Long) : AutoCloseable {

    private val file = TripStore.gpxFile(context, tripId)
    private val writer = FileWriter(file)

    init {
        writer.write(
            """<?xml version="1.0" encoding="UTF-8"?>
<gpx version="1.1" creator="Cruiser" xmlns="http://www.topografix.com/GPX/1/1">
<trk><trkseg>""",
        )
        writer.flush() // a track is worthless if a crash loses the whole file
    }

    fun point(lat: Double, lon: Double, timeMillis: Long) {
        writer.write(
            """<trkpt lat="$lat" lon="$lon"><time>${gpxTime(timeMillis)}</time></trkpt>""" + "\n",
        )
        writer.flush()
    }

    override fun close() {
        writer.write("</trkseg></trk></gpx>")
        writer.close()
    }

    companion object {
        fun gpxTime(millis: Long): String =
            java.time.Instant.ofEpochMilli(millis).toString()
    }
}
