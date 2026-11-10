package site.casiopea.cruizer.notify

import java.util.Calendar

/**
 * Pure adapter from Google Maps' on-screen accessibility text to a turn update.
 * It gates and normalizes the flattened node text only; every regex lives in
 * [NavTextParser] (already unit-tested against real notification fixtures).
 *
 * shortcut: real Maps screen text was not captured yet, so the nav-mode gate
 * ("exit navigation") is written blind — calibrate against the debug a11y
 * capture (`adb logcat -s CruiserNav`, maps-a11y lines) after one ride.
 */
object MapsScreenTextParser {

    /** The nav screen carries an "Exit navigation" control; plain map browsing doesn't. */
    private val NAV_MODE = Regex("""exit\s+navigat""", RegexOption.IGNORE_CASE)

    /**
     * Returns the parsed turn, or null when the window isn't a live route
     * (no nav-mode control) — never parse a merely-open Maps app.
     */
    fun extract(lines: List<String>, now: Calendar = Calendar.getInstance()): NavTextParser.Parsed? {
        val clean = lines.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        val lower = clean.joinToString("\n").lowercase()
        if (!NAV_MODE.containsMatchIn(lower)) return null
        return NavTextParser.parse(clean.joinToString("\n"), now)
    }
}
