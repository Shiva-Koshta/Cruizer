package app.cruiser.notify

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import app.cruiser.Session
import app.cruiser.proto.TurnKind
import app.cruiser.proto.TurnUpdate
import kotlin.math.roundToInt

/**
 * Parses the persistent navigation notifications published by OsmAnd, CoMaps,
 * Organic Maps, Google Maps and Waze into a [TurnUpdate] for the Pod.
 *
 * shortcut: everything is parsed from notification text only — we cannot get the
 * structured maneuver type, next-maneuver arrow or exit data a real TBT feed
 * carries. Upgrade by forking the source app (phase 2) if the text proves too coarse.
 */
class TurnSource : NotificationListenerService() {

    companion object {
        private val NAV_PACKAGES = setOf(
            "net.osmand", // OsmAnd / OsmAnd+
            "app.organicmaps",
            "app.comaps", // CoMaps (Organic Maps fork)
            "com.google.android.apps.maps",
            "com.waze",
        )

        private val DISTANCE = Regex(
            """(?:in\s+)?(\d+(?:[.,]\d+)?)\s*(m\b|km\b|ft\b|feet\b|mi\b|miles?\b|yd\b)""",
            RegexOption.IGNORE_CASE,
        )

        private val EXIT =
            Regex("""(?:take\s+the\s+)?(\d+)(?:st|nd|rd|th)\s+exit|exit\s+(\d+)""", RegexOption.IGNORE_CASE)

        private val CAMERA =
            Regex("""speed camera|fixed camera|average speed|radar""", RegexOption.IGNORE_CASE)

        private val ARRIVAL =
            Regex("""arriv\w*\D*(\d{1,2}:\d{2})\s*(AM|PM)?""", RegexOption.IGNORE_CASE)

        private val REMAINING_MIN =
            Regex("""(\d+)\s*(?:h|hr|hour)[s]?\s*(\d+)\s*min""", RegexOption.IGNORE_CASE)

        private val ARRIVED =
            Regex("""you (?:have )?(?:just )?arrived|reached your (?:destination|endpoint)""", RegexOption.IGNORE_CASE)

        /** Most-specific phrases first — first hit wins. */
        private val PHRASES: List<Pair<TurnKind, List<String>>> = listOf(
            TurnKind.U_TURN to listOf("u-turn", "uturn", "make a u"),
            TurnKind.ROUNDABOUT to listOf("roundabout", "traffic circle", "rotary"),
            TurnKind.SHARP_LEFT to listOf("sharp left", "hard left"),
            TurnKind.SHARP_RIGHT to listOf("sharp right", "hard right"),
            TurnKind.SLIGHT_LEFT to listOf("slight left", "bear left"),
            TurnKind.SLIGHT_RIGHT to listOf("slight right", "bear right"),
            TurnKind.KEEP_LEFT to listOf("keep left", "stay left", "continue left"),
            TurnKind.KEEP_RIGHT to listOf("keep right", "stay right", "continue right"),
            TurnKind.MERGE_LEFT to listOf("merge left", "merging left"),
            TurnKind.MERGE_RIGHT to listOf("merge right", "merging right"),
            TurnKind.FORK_LEFT to listOf("fork left", "left fork"),
            TurnKind.FORK_RIGHT to listOf("fork right", "right fork"),
            TurnKind.OFF_RAMP_LEFT to listOf("exit left", "left exit", "ramp on the left"),
            TurnKind.OFF_RAMP_RIGHT to listOf("exit right", "right exit", "ramp on the right"),
            TurnKind.ON_RAMP to listOf("on-ramp", "onramp"),
            TurnKind.FERRY to listOf("ferry"),
            TurnKind.DESTINATION to listOf("arrive", "destination"),
            TurnKind.LEFT to listOf("turn left", "left turn"),
            TurnKind.RIGHT to listOf("turn right", "right turn"),
            TurnKind.DEPART to listOf("go north", "go south", "go east", "go west", "head "),
            TurnKind.STRAIGHT to listOf("continue", "straight", "follow the road"),
        )
    }

    override fun onListenerConnected() {
        // A ride may already be underway when the service restarts — surface it now.
        activeNotifications.forEach(::tryParse)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        tryParse(sbn)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        if (isNavSource(sbn)) Session.postTurn(null)
    }

    private fun tryParse(sbn: StatusBarNotification) {
        if (!isNavSource(sbn)) return
        val body = text(sbn) ?: return
        val lower = body.lowercase()
        if (lower.isBlank()) return

        // Google Maps idles on "Guiding to <destination>" with no actionable turn.
        if (lower.startsWith("guiding to") && !DISTANCE.containsMatchIn(lower)) return

        if (CAMERA.containsMatchIn(lower)) {
            // Cameras arrive as separate/updated notifications; parse once and stay silent.
            postRadar(lower)
            return
        }
        Session.postRadar(null)

        if (ARRIVED.containsMatchIn(lower)) {
            Session.postTurn(TurnUpdate(TurnKind.DESTINATION, null, ended = true))
            return
        }
        Session.postTurn(build(lower))
    }

    private fun isNavSource(sbn: StatusBarNotification): Boolean =
        sbn.isOngoing && NAV_PACKAGES.any(sbn.packageName::startsWith)

    private fun text(sbn: StatusBarNotification): String? {
        val extras = sbn.notification.extras
        val title = extras.getCharSequence("android.title")?.toString()?.trim().orEmpty()
        // Google Maps puts the live instruction in bigText; OsmAnd uses text/subText.
        val body = listOfNotNull(
            extras.getCharSequence("android.bigText")?.toString(),
            extras.getCharSequence("android.text")?.toString(),
            extras.getCharSequence("android.subText")?.toString(),
        ).firstOrNull { it.isNotBlank() }?.trim() ?: return null
        if (title.isBlank()) return body
        if (body.startsWith(title)) return body
        return "$title\n$body"
    }

    private fun build(lower: String): TurnUpdate {
        val distances = DISTANCE.findAll(lower).take(2).toList().mapNotNull(::toMeters)
        val exit = EXIT.find(lower)?.groupValues
            ?.let { g -> (g[1].ifEmpty { g[2] }).toIntOrNull() }
            ?.takeIf { it in 1..9 }
        val kind = kindOf(lower)
        return TurnUpdate(
            kind = kind,
            distanceM = distances.firstOrNull(),
            exit = exit,
            // Notification text rarely names the next-next maneuver; the small arrow stays blank.
            next = null,
            nextDistanceM = null,
            arrivalSecondsFromNow = etaSeconds(lower),
            routeRemainingM = distances.getOrNull(1),
            remainingSeconds = remainingSeconds(lower),
            ended = kind == TurnKind.DESTINATION && distances.firstOrNull() == null,
        )
    }

    private fun kindOf(lower: String): TurnKind =
        PHRASES.firstOrNull { (_, phrases) -> phrases.any(lower::contains) }?.first
            ?: TurnKind.STRAIGHT

    private fun toMeters(m: MatchResult): Int? {
        val v = m.groupValues[1].replace(',', '.').toDoubleOrNull() ?: return null
        return when (m.groupValues[2].lowercase()) {
            "m" -> v.roundToInt()
            "km" -> (v * 1000).roundToInt()
            "ft", "feet" -> (v * 0.3048).roundToInt()
            "mi", "mile", "miles" -> (v * 1609.34).roundToInt()
            "yd" -> (v * 0.9144).roundToInt()
            else -> null
        }
    }

    private fun etaSeconds(lower: String): Int? {
        val m = ARRIVAL.find(lower) ?: return null
        var hour = m.groupValues[1].substringBefore(':').toIntOrNull() ?: return null
        val minute = m.groupValues[1].substringAfter(':').toIntOrNull() ?: return null
        if (hour !in 0..23 || minute !in 0..59) return null
        if (m.groupValues[2].equals("PM", true) && hour < 12) hour += 12
        if (m.groupValues[2].equals("AM", true) && hour == 12) hour = 0
        val now = java.util.Calendar.getInstance()
        var delta = (hour - now.get(java.util.Calendar.HOUR_OF_DAY)) * 60 +
            (minute - now.get(java.util.Calendar.MINUTE))
        if (delta < 0) delta += 24 * 60
        return delta.takeIf { it in 0..600 }?.times(60)
    }

    private fun remainingSeconds(lower: String): Int? {
        val m = REMAINING_MIN.find(lower) ?: return null
        val h = m.groupValues[1].toIntOrNull() ?: return null
        val mm = m.groupValues[2].toIntOrNull() ?: return null
        if (h !in 0..23 || mm !in 0..59) return null
        return h * 3600 + mm * 60
    }

    /** Best-effort: only within 300 m does the Pod's borrowed bottom line make sense. */
    private fun postRadar(lower: String) {
        val d = DISTANCE.find(lower)?.let(::toMeters)?.takeIf { it in 1..300 }
        Session.postRadar(d)
    }
}
