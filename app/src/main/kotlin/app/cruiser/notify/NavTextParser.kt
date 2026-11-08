package app.cruiser.notify

import app.cruiser.proto.TurnKind
import app.cruiser.proto.TurnUpdate
import java.util.Calendar
import kotlin.math.roundToInt

/**
 * Pure text -> [TurnUpdate] parser over the persistent navigation notifications
 * published by OsmAnd, CoMaps, Organic Maps, Google Maps and Waze. No Android
 * dependencies (java.util only) so it is unit-testable against real fixtures.
 *
 * shortcut: everything is parsed from notification text only — we cannot get the
 * structured maneuver type, next-maneuver arrow or exit data a real TBT feed
 * carries. Upgrade by forking the source app (phase 2) if the text proves too coarse.
 */
object NavTextParser {

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

    /** Google Maps 3-part bottom line: "13 min · 4.6 mi · 11:55 ETA" (also "24 min · 12 km · 5:30 PM", "24 min · 12 km · 17:30"). */
    private val MAPS_BOTTOM_3 =
        Regex(
            """(\d+)\s*min[s]?\s*·\s*(\d+(?:[.,]\d+)?\s*(?:m\b|km\b|ft\b|feet\b|mi\b|miles?\b|yd\b))\s*·\s*(?:(?:eta\s*)?(\d{1,2}:\d{2})\s*(am|pm)?(?:\s*eta)?)""",
            RegexOption.IGNORE_CASE,
        )

    /** Google Maps 2-part bottom line: "24 min · 31 km" (also "20 mins · 5.2 mi", "24 min · 900 ft"). */
    private val MAPS_BOTTOM =
        Regex(
            """(\d+)\s*min[s]?\s*·\s*(\d+(?:[.,]\d+)?)\s*(m\b|km\b|ft\b|feet\b|mi\b|miles?\b|yd\b)""",
            RegexOption.IGNORE_CASE,
        )

    /** Standalone Google Maps bottom line without distance: "24 min". */
    private val MINUTES_ONLY =
        Regex("""^(\d+)\s*min[s]?$""", setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE))

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
        TurnKind.OFF_RAMP_RIGHT to listOf("exit right", "right exit", "ramp on the right", "take exit", "take the exit"),
        TurnKind.ON_RAMP to listOf("on-ramp", "onramp"),
        TurnKind.FERRY to listOf("ferry"),
        TurnKind.DESTINATION to listOf("arrive", "destination"),
        TurnKind.LEFT to listOf("turn left", "left turn", "left onto", "left on ", "take the left", "left toward", ", left"),
        TurnKind.RIGHT to listOf("turn right", "right turn", "right onto", "right on ", "take the right", "right toward", ", right"),
        TurnKind.DEPART to listOf("go north", "go south", "go east", "go west", "head "),
        TurnKind.STRAIGHT to listOf("continue", "straight", "follow the road"),
    )

    /** Enum kind result of parsing: a turn, a camera alert (m in 1..300), or nothing actionable. */
    sealed interface Parsed {
        data class Turn(val update: TurnUpdate) : Parsed
        data class Camera(val meters: Int?) : Parsed
        data object Idle : Parsed // no actionable content (e.g. Maps' "Guiding to …")
    }

    fun parse(body: String, now: Calendar = Calendar.getInstance()): Parsed {
        val lower = body.lowercase().replace('\u00A0', ' ').replace('\u202F', ' ')
        if (lower.isBlank()) return Parsed.Idle

        // Maps idles on "Guiding to <destination>" and flashes "Starting navigation…"
        // before the first instruction — neither is actionable.
        if (lower.contains("guiding to") && !DISTANCE.containsMatchIn(lower)) return Parsed.Idle
        if (lower.contains("starting navigation")) return Parsed.Idle

        if (CAMERA.containsMatchIn(lower)) {
            // Cameras arrive as separate/updated notifications; report once and stay silent.
            val d = DISTANCE.find(lower)?.let(::toMeters)?.takeIf { it in 1..300 }
            return Parsed.Camera(d)
        }

        if (ARRIVED.containsMatchIn(lower)) {
            return Parsed.Turn(TurnUpdate(TurnKind.DESTINATION, null, ended = true))
        }

        return Parsed.Turn(build(lower, now))
    }

    private fun build(lower: String, now: Calendar): TurnUpdate {
        val distance = DISTANCE.find(lower)?.let(::toMeters)
        val exit = EXIT.find(lower)?.groupValues
            ?.let { g -> (g[1].ifEmpty { g[2] }).toIntOrNull() }
            ?.takeIf { it in 1..9 }
        val maps3 = MAPS_BOTTOM_3.find(lower)
        val mapsBottom = maps3 ?: MAPS_BOTTOM.find(lower)
        val kind = kindOf(lower)
        return TurnUpdate(
            kind = kind,
            distanceM = distance,
            exit = exit,
            // Notification text rarely names the next-next maneuver; the small arrow stays blank.
            next = null,
            nextDistanceM = null,
            arrivalSecondsFromNow = etaSeconds(lower, now),
            // Distance to destination only from the Maps bottom line: a second inline
            // distance is the *next* maneuver, not the route remaining.
            routeRemainingM = maps3?.let { metersOf(it.groupValues[2]) }
                ?: mapsBottom?.let(::bottomRemaining),
            remainingSeconds = remainingSeconds(lower)
                ?: mapsBottom?.groupValues?.get(1)?.toIntOrNull()?.times(60),
            ended = kind == TurnKind.DESTINATION && distance == null,
        )
    }

    private fun bottomRemaining(m: MatchResult): Int? =
        toMeters(m.groupValues[2], m.groupValues[3])

    private fun kindOf(lower: String): TurnKind =
        PHRASES.firstOrNull { (_, phrases) -> phrases.any(lower::contains) }?.first
            ?: TurnKind.UNKNOWN

    private fun toMeters(m: MatchResult): Int? = toMeters(m.groupValues[1], m.groupValues[2])

    /** Public hook for the structured sources (CoMaps provider sends formatted "1.2 km" strings). */
    fun metersOf(text: String): Int? =
        DISTANCE.find(text.replace('\u00A0', ' ').replace('\u202F', ' '))?.let(::toMeters)

    private fun toMeters(value: String, unit: String): Int? {
        val v = value.replace(',', '.').toDoubleOrNull() ?: return null
        return when (unit.lowercase()) {
            "m" -> v.roundToInt()
            "km" -> (v * 1000).roundToInt()
            "ft", "feet" -> (v * 0.3048).roundToInt()
            "mi", "mile", "miles" -> (v * 1609.34).roundToInt()
            "yd" -> (v * 0.9144).roundToInt()
            else -> null
        }
    }

    /** "arriving at 4:10 PM" or "11:55 ETA" clock -> seconds from now (capped at 10 h). */
    fun etaSeconds(lower: String, now: Calendar = Calendar.getInstance()): Int? {
        ARRIVAL.find(lower)?.let { m ->
            return etaSecondsFromClock(m.groupValues[1], m.groupValues[2].takeIf { it.isNotEmpty() }, now)
        }
        MAPS_BOTTOM_3.find(lower)?.let { m ->
            return etaSecondsFromClock(m.groupValues[3], m.groupValues[4].takeIf { it.isNotEmpty() }, now)
        }
        return null
    }

    fun etaSecondsFromClock(clock: String, amPm: String?, now: Calendar = Calendar.getInstance()): Int? {
        var hour = clock.substringBefore(':').toIntOrNull() ?: return null
        val minute = clock.substringAfter(':').toIntOrNull() ?: return null
        if (hour !in 0..23 || minute !in 0..59) return null
        if (amPm.equals("PM", true) && hour < 12) hour += 12
        if (amPm.equals("AM", true) && hour == 12) hour = 0
        var delta = (hour - now.get(Calendar.HOUR_OF_DAY)) * 60 +
            (minute - now.get(Calendar.MINUTE))
        if (delta < 0) delta += 24 * 60
        return delta.takeIf { it in 0..600 }?.times(60)
    }

    private fun remainingSeconds(lower: String): Int? {
        REMAINING_MIN.find(lower)?.let { m ->
            val h = m.groupValues[1].toIntOrNull() ?: return null
            val mm = m.groupValues[2].toIntOrNull() ?: return null
            if (h !in 0..23 || mm !in 0..59) return null
            return h * 3600 + mm * 60
        }
        // Google Maps standalone bottom line: "24 min".
        MINUTES_ONLY.find(lower)?.let { m ->
            return m.groupValues[1].toIntOrNull()?.takeIf { it in 1..600 }?.times(60)
        }
        return null
    }
}
