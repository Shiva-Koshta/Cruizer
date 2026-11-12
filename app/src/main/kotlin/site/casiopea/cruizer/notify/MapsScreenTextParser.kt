package site.casiopea.cruizer.notify

import site.casiopea.cruizer.proto.TurnKind
import site.casiopea.cruizer.proto.TurnUpdate
import java.util.Calendar

/**
 * Pure parser for Google Maps' on-screen accessibility text.
 *
 * The nav screen has two regions: the instruction card on top (maneuver + distance
 * to the turn) and the bottom bar (time left, distance remaining, arrival clock).
 * Parsing them separately is what keeps the route distance from being mistaken for
 * the turn distance. The instruction card reuses [NavTextParser]'s tested regexes;
 * the bottom bar is matched by shape, so it works whether Maps exposes one merged
 * content description or three separate nodes.
 *
 * shortcut: the nav-control gate and the region split are written blind — confirm
 * against the debug capture (`adb logcat -s CruizerNav`, maps-a11y lines).
 */
object MapsScreenTextParser {

    /** One flattened window line with its vertical position (0 = top, 1 = bottom). */
    data class ScreenLine(val text: String, val y: Float = 0f)

    /** Bottom bar sits below this fraction of the screen height; the instruction card above. */
    private const val SUMMARY_TOP = 0.72f

    /** Strings that only exist on Maps' live navigation screen, not while browsing. */
    private val NAV_CONTROLS = listOf("exit", "search along route", "mute")

    private val HOURS_MIN = Regex("""(\d+)\s*(?:h|hr|hour)s?\s*(\d+)\s*min""", RegexOption.IGNORE_CASE)
    private val MINUTES = Regex("""(\d+)\s*min""", RegexOption.IGNORE_CASE)
    // Negative lookahead: "52 km/h" is a speed, not a route distance.
    private val DISTANCE = Regex(
        """(\d+(?:[.,]\d+)?)\s*(km|mi|ft|feet|yd|m)\b(?!\s*/\s*h)""",
        RegexOption.IGNORE_CASE,
    )
    private val CLOCK = Regex("""(\d{1,2}:\d{2})\s*(am|pm)?\b""", RegexOption.IGNORE_CASE)

    /**
     * Returns the turn when the window is a live route, or null when it isn't
     * (no nav control found — never parse a merely-open Maps app).
     */
    fun extract(lines: List<ScreenLine>, now: Calendar = Calendar.getInstance()): NavTextParser.Parsed? {
        val clean = lines
            .map { ScreenLine(it.text.trim(), it.y) }
            .filter { it.text.isNotEmpty() }
            .distinctBy { it.text }
        if (clean.isEmpty()) return null
        val all = clean.joinToString("\n") { it.text }.lowercase()
        if (NAV_CONTROLS.none(all::contains)) return null

        val summary = clean.filter { it.y >= SUMMARY_TOP }.joinToString(", ") { it.text }
        val instruction = clean.filter { it.y < SUMMARY_TOP }.joinToString("\n") { it.text }

        val parsed = NavTextParser.parse(instruction, now)
        if (parsed is NavTextParser.Parsed.Camera) return parsed
        val turn = (parsed as? NavTextParser.Parsed.Turn)?.update ?: TurnUpdate(TurnKind.UNKNOWN, null)
        return NavTextParser.Parsed.Turn(
            turn.copy(
                remainingSeconds = minutesSeconds(summary) ?: turn.remainingSeconds,
                routeRemainingM = summaryMeters(summary) ?: turn.routeRemainingM,
                arrivalSecondsFromNow = summaryEta(summary, now) ?: turn.arrivalSecondsFromNow,
            ),
        )
    }

    /** Distance token from the bottom bar; the local regex rejects "52 km/h". */
    private fun summaryMeters(summary: String): Int? =
        DISTANCE.find(summary)?.value?.let(NavTextParser::metersOf)

    /** "13 min" / "1 h 20 min" -> seconds. */
    private fun minutesSeconds(summary: String): Int? {
        HOURS_MIN.find(summary)?.let { m ->
            val h = m.groupValues[1].toIntOrNull() ?: return null
            val mm = m.groupValues[2].toIntOrNull() ?: return null
            if (h !in 0..23 || mm !in 0..59) return null
            return h * 3600 + mm * 60
        }
        return MINUTES.find(summary)?.groupValues?.get(1)?.toIntOrNull()
            ?.takeIf { it in 0..600 }?.times(60)
    }

    /** "11:55 AM" or "arrive 11:55" -> seconds from now. */
    private fun summaryEta(summary: String, now: Calendar): Int? {
        val m = CLOCK.find(summary) ?: return null
        return NavTextParser.etaSecondsFromClock(
            m.groupValues[1],
            m.groupValues[2].takeIf { it.isNotEmpty() },
            now,
        )
    }
}
