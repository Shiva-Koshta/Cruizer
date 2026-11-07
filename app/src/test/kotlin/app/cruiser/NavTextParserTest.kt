package app.cruiser

import app.cruiser.notify.NavTextParser
import app.cruiser.notify.NavTextParser.Parsed
import app.cruiser.proto.TurnKind
import java.util.Calendar
import java.util.GregorianCalendar
import org.junit.Assert.assertEquals
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
    fun mapsBottomLineMiles() {
        val p = NavTextParser.parse("Turn left\n20 mins · 5.2 mi") as Parsed.Turn
        assertEquals(8369, p.update.routeRemainingM) // 5.2 * 1609.34
    }

    @Test
    fun mapsStandaloneMinutes() {
        val p = NavTextParser.parse("Continue straight\n24 min") as Parsed.Turn
        assertEquals(24 * 60, p.update.remainingSeconds)
    }

    @Test
    fun guidingToIsIdle() {
        assertEquals(Parsed.Idle, NavTextParser.parse("Google Maps\nGuiding to Kazhakootam"))
    }

    @Test
    fun arrived() {
        val p = NavTextParser.parse("You have arrived at your destination") as Parsed.Turn
        assertEquals(TurnKind.DESTINATION, p.update.kind)
        assertTrue(p.update.ended)
    }

    @Test
    fun camera() {
        val p = NavTextParser.parse("Speed camera in 120 m") as Parsed.Camera
        assertEquals(120, p.meters)
    }

    @Test
    fun cameraFarIsNotReported() {
        val p = NavTextParser.parse("Speed camera in 2 km") as Parsed.Camera
        assertNull(p.meters)
    }
}
