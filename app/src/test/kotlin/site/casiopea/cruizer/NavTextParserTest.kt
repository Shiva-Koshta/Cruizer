package site.casiopea.cruizer

import site.casiopea.cruizer.notify.NavTextParser
import site.casiopea.cruizer.notify.NavTextParser.Parsed
import site.casiopea.cruizer.proto.TurnKind
import java.util.Calendar
import java.util.GregorianCalendar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Fixtures are real-world notification text shapes (title\nbigText as TurnSource assembles them). */
class NavTextParserTest {

    private fun now(hour: Int, minute: Int): Calendar =
        GregorianCalendar(2026, Calendar.NOVEMBER, 7, hour, minute)

    @Test
    fun osmandTurnLine() {
        val p = NavTextParser.parse("OsmAnd\nTurn right in 250 m onto MG Road") as Parsed.Turn
        assertEquals(TurnKind.RIGHT, p.update.kind)
        assertEquals(250, p.update.distanceM)
        assertNull(p.update.exit)
    }

    @Test
    fun roundaboutExit() {
        val p = NavTextParser.parse("Take the 3rd exit at the roundabout in 300 m") as Parsed.Turn
        assertEquals(TurnKind.ROUNDABOUT, p.update.kind)
        assertEquals(3, p.update.exit)
        assertEquals(300, p.update.distanceM)
    }

    @Test
    fun mapsEtaClock() {
        val p = NavTextParser.parse(
            "Turn right onto Hosur Road\nin 400 m · arriving at 4:10 PM",
            now(15, 0),
        ) as Parsed.Turn
        assertEquals(TurnKind.RIGHT, p.update.kind)
        assertEquals(400, p.update.distanceM)
        // 15:00 -> 16:10 = 70 min
        assertEquals(70 * 60, p.update.arrivalSecondsFromNow)
    }

    @Test
    fun mapsBottomLineDistanceAndTime() {
        val p = NavTextParser.parse("Turn left onto Ring Road\n24 min · 31 km", now(10, 0)) as Parsed.Turn
        assertEquals(TurnKind.LEFT, p.update.kind)
        assertEquals(24 * 60, p.update.remainingSeconds)
        assertEquals(31_000, p.update.routeRemainingM)
    }

    @Test
    fun routeDistanceNeverBecomesTheTurnDistance() {
        // The instruction carries no distance; the bottom line's 4.6 km is the
        // route. It used to be sent as the distance to the turn.
        val p = NavTextParser.parse(
            "Turn right onto MG Road\n13 min · 4.6 km · 11:55 ETA",
            now(11, 0),
        ) as Parsed.Turn
        assertNull(p.update.distanceM)
        assertEquals(4600, p.update.routeRemainingM)
    }

    @Test
    fun destinationTextWithRouteDistanceIsNotArrival() {
        val p = NavTextParser.parse("Your destination is on the right\n13 min · 4.6 km") as Parsed.Turn
        assertEquals(TurnKind.DESTINATION, p.update.kind)
        assertFalse(p.update.ended)
    }

    @Test
    fun mapsBottomLineMiles() {
        val p = NavTextParser.parse("Turn left\n20 mins · 5.2 mi") as Parsed.Turn
        assertEquals(8369, p.update.routeRemainingM) // 5.2 * 1609.34
    }

    @Test
    fun mapsBottomLineFeetAndMeters() {
        val p = NavTextParser.parse("Continue straight\n24 min · 1500 ft") as Parsed.Turn
        assertEquals(457, p.update.routeRemainingM) // 1500 * 0.3048
        val q = NavTextParser.parse("Continue straight\n24 min · 800 m") as Parsed.Turn
        assertEquals(800, q.update.routeRemainingM)
    }

    @Test
    fun mapsStandaloneMinutes() {
        val p = NavTextParser.parse("Continue straight\n24 min") as Parsed.Turn
        assertEquals(24 * 60, p.update.remainingSeconds)
    }

    @Test
    fun minutesOnlyCountsOnlyOnTheLastLine() {
        // A "5 min" note inside the instruction is not the route's time left.
        val p = NavTextParser.parse("In 5 min turn right\nHead east") as Parsed.Turn
        assertNull(p.update.remainingSeconds)
    }

    @Test
    fun turkishLocaleDoesNotBreakPhrases() {
        // "TURN RIGHT".lowercase() in tr_TR is "turn rıght" (dotless i): the phrase
        // must still match, so the parser lowercases with Locale.ROOT.
        val saved = java.util.Locale.getDefault()
        java.util.Locale.setDefault(java.util.Locale.forLanguageTag("tr-TR"))
        try {
            val p = NavTextParser.parse("TURN RIGHT\nIN 250 M") as Parsed.Turn
            assertEquals(TurnKind.RIGHT, p.update.kind)
            assertEquals(250, p.update.distanceM)
        } finally {
            java.util.Locale.setDefault(saved)
        }
    }

    @Test
    fun mapsEtaLineIsNotArrival() {
        // Live Maps notification: title "Head east", subText "Arrive 10:20".
        // "arrive" must not turn this into DESTINATION, or the route ends on the spot.
        val p = NavTextParser.parse("Head east\nArrive 10:20", now(10, 0)) as Parsed.Turn
        assertEquals(TurnKind.DEPART, p.update.kind)
        assertFalse(p.update.ended)
        assertEquals(20 * 60, p.update.arrivalSecondsFromNow)
    }

    @Test
    fun arrivedEndsRoute() {
        val p = NavTextParser.parse("You have arrived\nKazhakootam") as Parsed.Turn
        assertEquals(TurnKind.DESTINATION, p.update.kind)
        assertTrue(p.update.ended)
    }

    @Test
    fun guidingToIsIdle() {
        assertEquals(Parsed.Idle, NavTextParser.parse("Google Maps\nGuiding to Kazhakootam"))
    }

    @Test
    fun startingNavigationIsIdle() {
        assertEquals(Parsed.Idle, NavTextParser.parse("Starting navigation…"))
    }

    @Test
    fun arrived() {
        val p = NavTextParser.parse("You have arrived at your destination") as Parsed.Turn
        assertEquals(TurnKind.DESTINATION, p.update.kind)
        assertTrue(p.update.ended)
    }

    @Test
    fun secondInlineDistanceIsNotRouteRemaining() {
        // "then turn right in 1.2 km" is the NEXT maneuver, not the distance to the destination.
        val p = NavTextParser.parse("Turn left in 300 m, then turn right in 1.2 km") as Parsed.Turn
        assertEquals(300, p.update.distanceM)
        assertNull(p.update.routeRemainingM)
    }

    @Test
    fun nonBreakingSpaceInDistance() {
        // Android notifications frequently format distances using non-breaking space (0xA0).
        val p = NavTextParser.parse("Turn right in 350\u00A0m onto Main Street") as Parsed.Turn
        assertEquals(TurnKind.RIGHT, p.update.kind)
        assertEquals(350, p.update.distanceM)
    }

    @Test
    fun metersOfHandlesUnicodeSpaces() {
        assertEquals(1200, NavTextParser.metersOf("1.2\u00A0km"))
        assertEquals(500, NavTextParser.metersOf("500\u202Fm"))
    }

    @Test
    fun unknownDirectionWhenNoManeuverKeyword() {
        val p = NavTextParser.parse("Main Street in 400 m") as Parsed.Turn
        assertEquals(TurnKind.UNKNOWN, p.update.kind)
        assertEquals(400, p.update.distanceM)
    }

    @Test
    fun googleMapsPhrases() {
        val right = NavTextParser.parse("Right onto Main St in 300 m") as Parsed.Turn
        assertEquals(TurnKind.RIGHT, right.update.kind)
        assertEquals(300, right.update.distanceM)

        val left = NavTextParser.parse("Left onto 4th Ave in 150 m") as Parsed.Turn
        assertEquals(TurnKind.LEFT, left.update.kind)
        assertEquals(150, left.update.distanceM)

        val exit = NavTextParser.parse("Take exit 14B toward Airport in 800 m") as Parsed.Turn
        assertEquals(TurnKind.OFF_RAMP_RIGHT, exit.update.kind)
        assertEquals(800, exit.update.distanceM)
    }

    @Test
    fun reRouteDetected() {
        val p = NavTextParser.parse("Recalculating route in 500 m") as Parsed.Turn
        assertEquals(TurnKind.RE_ROUTE, p.update.kind)
        assertEquals(500, p.update.distanceM)
    }

    @Test
    fun googleMapsThreePartSubTextWithEta() {
        // "13 min · 4.6 mi · 11:55 ETA" parsed at 11:00 AM -> 55 min arrival
        val p = NavTextParser.parse("Turn left\n13 min · 4.6 mi · 11:55 ETA", now(11, 0)) as Parsed.Turn
        assertEquals(TurnKind.LEFT, p.update.kind)
        assertEquals(13 * 60, p.update.remainingSeconds)
        assertEquals(7403, p.update.routeRemainingM) // 4.6 * 1609.34
        assertEquals(55 * 60, p.update.arrivalSecondsFromNow)
    }

    @Test
    fun googleMapsThreePartSubText24hClock() {
        // "24 min · 12 km · 17:30" parsed at 16:30 -> 60 min arrival
        val p = NavTextParser.parse("Turn right\n24 min · 12 km · 17:30", now(16, 30)) as Parsed.Turn
        assertEquals(TurnKind.RIGHT, p.update.kind)
        assertEquals(24 * 60, p.update.remainingSeconds)
        assertEquals(12_000, p.update.routeRemainingM)
        assertEquals(60 * 60, p.update.arrivalSecondsFromNow)
    }

    @Test
    fun arriveInstructionKeepsItsManeuverWhenAnEtaLineFollows() {
        // The ETA scrubber used to span newlines, deleting "arrive at destination"
        // along with the clock, so the last turn of the ride parsed as UNKNOWN.
        val p = NavTextParser.parse("In 200 m, arrive at destination\n10:20", now(10, 0)) as Parsed.Turn
        assertEquals(TurnKind.DESTINATION, p.update.kind)
        assertEquals(200, p.update.distanceM)
        assertFalse(p.update.ended) // still 200 m to go
    }

    @Test
    fun etaHoursAwayIsKept() {
        // A 1200 km leg has a 14.5 h ETA; the old 10 h cap blanked the arrival line.
        val p = NavTextParser.parse("Turn right\n24 min · 12 km · 22:30", now(8, 0)) as Parsed.Turn
        assertEquals(14 * 3600 + 30 * 60, p.update.arrivalSecondsFromNow)
    }

    @Test
    fun instructionAfterTheBottomLineStillParses() {
        // Some posts put the summary first; cutting the body at the bottom line
        // threw the maneuver away.
        val p = NavTextParser.parse("13 min · 4.6 km · 11:55\nTurn right onto MG Road", now(11, 0)) as Parsed.Turn
        assertEquals(TurnKind.RIGHT, p.update.kind)
        assertNull(p.update.distanceM)
        assertEquals(4600, p.update.routeRemainingM)
    }

    @Test
    fun speedIsNotTheTurnDistance() {
        val p = NavTextParser.parse("Turn right onto Main St\n52 km/h") as Parsed.Turn
        assertEquals(TurnKind.RIGHT, p.update.kind)
        assertNull(p.update.distanceM)
    }
}
