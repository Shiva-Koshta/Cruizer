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

    /**
     * The bottom bar carries a duration ("13 min", "1 h 20 min") or a clock; the
     * instruction card does not. Matching by shape survives landscape and a maps
     * build that moves the sheet, which a fixed screen fraction did not.
     */
    private val MANEUVER_WORD =
        Regex("""\b(turn|keep|merge|exit|arrive|continue|roundabout|u-turn)\b""", RegexOption.IGNORE_CASE)

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
     * (no nav controls found — never parse a merely-open Maps app).
     */
    fun extract(lines: List<ScreenLine>, now: Calendar = Calendar.getInstance()): TurnUpdate? {
        val clean = lines
            .map { ScreenLine(it.text.trim(), it.y) }
            .filter { it.text.isNotEmpty() }
            .distinctBy { it.text to it.y }
        if (clean.isEmpty()) return null
        if (!isNavigating(clean)) return null

        // Summary = every line at or below the first line that carries a duration or
        // a clock. Finding that anchor by shape (not by a screen fraction) survives
        // landscape and a maps build that moves the sheet.
        val summaryTop = clean.filter { isSummary(it.text) }.minOfOrNull { it.y } ?: Float.MAX_VALUE
        val summary = clean.filter { it.y >= summaryTop }.joinToString(", ") { it.text }
        val instruction = clean.filter { it.y < summaryTop }.joinToString("\n") { it.text }

        val parsed = NavTextParser.parse(instruction, now)
        val turn = (parsed as? NavTextParser.Parsed.Turn)?.update ?: TurnUpdate(TurnKind.UNKNOWN, null)
        return turn.copy(
            remainingSeconds = minutesSeconds(summary) ?: turn.remainingSeconds,
            routeRemainingM = summaryMeters(summary) ?: turn.routeRemainingM,
            arrivalSecondsFromNow = summaryEta(summary, now) ?: turn.arrivalSecondsFromNow,
        )
    }

    /**
     * True when [lines] come from Maps' live navigation screen, not a browse or
     * search screen. Two controls, not one: a search result named "Exit 5
     * Parking" must not pass the gate on its own.
     */
    fun isNavigating(lines: List<ScreenLine>): Boolean {
        val all = lines.joinToString("\n") { it.text }.lowercase()
        val controls = NAV_CONTROLS.count(all::contains)
        if (controls >= 2) return true
        if (controls == 0) return false
        // One control plus a maneuver line ("In 500 m", "Turn right in 300 m") over a
        // duration summary: keeps the path alive when Maps renames a control, without
        // treating a route preview (a bare "4.6 km · 13 min" card) as a live route.
        val hasSummary = lines.any { isSummary(it.text) }
        val hasTurn = lines.any {
            !isSummary(it.text) && DISTANCE.containsMatchIn(it.text) &&
                (MANEUVER_WORD.containsMatchIn(it.text) || IN_DISTANCE.containsMatchIn(it.text))
        }
        return hasSummary && hasTurn
    }

    private val IN_DISTANCE = Regex("""\bin\s+\d""", RegexOption.IGNORE_CASE)

    /** A line the bottom bar would print: a duration or a clock, not a maneuver. */
    private fun isSummary(text: String): Boolean =
        CLOCK.containsMatchIn(text) ||
            (MINUTES.containsMatchIn(text) && !MANEUVER_WORD.containsMatchIn(text))

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
