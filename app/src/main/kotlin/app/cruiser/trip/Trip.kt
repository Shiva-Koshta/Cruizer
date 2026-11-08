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
    val title: String? = null,
    val bikeName: String? = null,
) {
    val seconds: Int get() = ((endedAt - id) / 1000).toInt()
    val avgSpeedKmh: Int get() = if (seconds > 0) ((distanceM / 1000f) / (seconds / 3600f)).toInt() else 0
}

/**
 * Trip history backed by a JSON index file + one GPX track file per trip in
 * filesDir/trips/. Deliberately no database for one list.
 */
object TripStore {

    private fun dir(context: Context) = File(context.filesDir, "trips").apply { mkdirs() }
    private fun index(context: Context) = File(dir(context), "index.json")

    /** One file name format for the writer and the export. */
    fun gpxFile(context: Context, id: Long): File = File(dir(context), "cruiser-trip-$id.gpx")

    /** A truncated index must not brick the Trips tab. */
    private fun readIndex(context: Context): JSONArray {
        val f = index(context)
        if (!f.exists()) return JSONArray()
        return runCatching { JSONArray(f.readText()) }.getOrElse { JSONArray() }
    }

    private fun writeIndex(context: Context, next: JSONArray) {
        val f = index(context)
        val tmp = File(f.parentFile, "${f.name}.tmp")
        tmp.writeText(next.toString())
        if (!tmp.renameTo(f)) {
            f.writeText(next.toString())
            tmp.delete()
        }
    }

    fun save(context: Context, trip: Trip) {
        val arr = readIndex(context)
        val next = JSONArray()
        (0 until arr.length())
            .mapNotNull { arr.optJSONObject(it) }
            .filterNot { it.optLong("id") == trip.id } // one entry per ride id
            .forEach { next.put(it) }
        val obj = JSONObject()
            .put("id", trip.id)
            .put("endedAt", trip.endedAt)
            .put("distanceM", trip.distanceM)
        if (trip.title != null) obj.put("title", trip.title)
        if (trip.bikeName != null) obj.put("bikeName", trip.bikeName)
        next.put(obj)
        writeIndex(context, next)
    }

    fun rename(context: Context, id: Long, newTitle: String) {
        val arr = readIndex(context)
        val next = JSONArray()
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            if (obj.optLong("id") == id) {
                obj.put("title", newTitle.trim())
            }
            next.put(obj)
        }
        writeIndex(context, next)
    }

    fun delete(context: Context, id: Long) {
        val arr = readIndex(context)
        val next = JSONArray()
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            if (obj.optLong("id") != id) {
                next.put(obj)
            }
        }
        writeIndex(context, next)
        gpxFile(context, id).delete()
    }

    fun history(context: Context): List<Trip> {
        val arr = readIndex(context)
        return (0 until arr.length())
            .mapNotNull { i ->
                arr.optJSONObject(i)?.takeIf { it.has("id") }?.let {
                    Trip(
                        id = it.optLong("id"),
                        endedAt = it.optLong("endedAt"),
                        distanceM = it.optInt("distanceM"),
                        title = it.optString("title").takeIf { s -> s.isNotBlank() },
                        bikeName = it.optString("bikeName").takeIf { s -> s.isNotBlank() },
                    )
                }
            }
            .sortedByDescending { it.id }
    }

    /** Shares a trip's GPX directly via Android's native share sheet (Strava, Komoot, WhatsApp, Drive). */
    fun share(context: Context, trip: Trip) {
        val src = gpxFile(context, trip.id)
        if (!src.exists()) return
        try {
            val uri = androidx.core.content.FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                src,
            )
            val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                type = "application/gpx+xml"
                putExtra(android.content.Intent.EXTRA_STREAM, uri)
                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(android.content.Intent.createChooser(intent, "Share Ride GPX"))
        } catch (_: Exception) {
        }
    }

    /** Copies a trip's GPX to Downloads; returns the display name or null on failure. */
    fun export(context: Context, trip: Trip): String? {
        val src = gpxFile(context, trip.id)
        if (!src.exists()) return null
        return try {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, src.name)
                put(MediaStore.Downloads.MIME_TYPE, "application/gpx+xml")
            }
            val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: return null
            context.contentResolver.openOutputStream(uri)?.use { out ->
                src.inputStream().use { it.copyTo(out) }
            } ?: return null
            src.name
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
