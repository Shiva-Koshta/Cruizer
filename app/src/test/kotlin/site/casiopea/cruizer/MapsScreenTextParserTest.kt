package site.casiopea.cruizer

import site.casiopea.cruizer.notify.MapsScreenTextParser
import site.casiopea.cruizer.notify.MapsScreenTextParser.ScreenLine
import site.casiopea.cruizer.proto.TurnKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

/** Google Maps on-screen accessibility text → turn updates (pure parser). */
class MapsScreenTextParserTest {

    private fun at(y: Float, vararg s: String) = s.map { ScreenLine(it, y) }

    private fun noon(): Calendar = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 12)
        set(Calendar.MINUTE, 0)
    }

    private fun turn(lines: List<ScreenLine>): site.casiopea.cruizer.proto.TurnUpdate =
        MapsScreenTextParser.extract(lines, noon())!!

    @Test
    fun plainMapBrowseIsIgnored() {
        // No nav control: never treat a merely-open Maps as a route.
        assertNull(
            MapsScreenTextParser.extract(
                at(0.1f, "345 Main St", "Restaurants") + at(0.9f, "2.5 km", "12 min"),
            ),
        )
    }

    @Test
    fun bottomBarFeedsRouteSummary() {
        val u = turn(
            at(0.1f, "In 500 m", "Turn right onto Market St") +
                at(0.9f, "4.6 km", "13 min", "12:30 PM", "Exit", "Mute"),
        )
        assertEquals(TurnKind.RIGHT, u.kind)
        assertEquals(500, u.distanceM)
        assertEquals(4600, u.routeRemainingM)
        assertEquals(13 * 60, u.remainingSeconds)
        assertEquals(30 * 60, u.arrivalSecondsFromNow)
    }

    @Test
    fun routeDistanceNeverBecomesTurnDistance() {
        // Instruction card names the maneuver without a distance; the only distance
        // on screen is the bottom bar's route remaining.
        val u = turn(
            at(0.1f, "Turn left onto 5th Ave") +
                at(0.9f, "4.6 km", "13 min", "Exit", "Mute"),
        )
        assertEquals(TurnKind.LEFT, u.kind)
        assertNull(u.distanceM)
        assertEquals(4600, u.routeRemainingM)
    }

    @Test
    fun mergedContentDescriptionStillParses() {
        // Maps may expose the whole bottom bar as one node instead of three.
        val u = turn(
            at(0.1f, "In 300 m", "Turn right") +
                at(0.9f, "Exit navigation, Mute, 4.6 km, 13 min, arrive 12:30 PM"),
        )
        assertEquals(TurnKind.RIGHT, u.kind)
        assertEquals(300, u.distanceM)
        assertEquals(4600, u.routeRemainingM)
        assertEquals(13 * 60, u.remainingSeconds)
        assertTrue(u.arrivalSecondsFromNow != null)
    }

    @Test
    fun hoursAndMinutes() {
        val u = turn(
            at(0.1f, "In 800 m", "Turn right") +
                at(0.9f, "1 h 20 min", "58 km", "Exit", "Mute"),
        )
        assertEquals((60 + 20) * 60, u.remainingSeconds)
        assertEquals(58_000, u.routeRemainingM)
    }

    @Test
    fun speedIsNotMistakenForDistance() {
        val u = turn(
            at(0.1f, "In 500 m", "Turn right") +
                at(0.9f, "52 km/h", "13 min", "Exit", "Mute"),
        )
        assertEquals(500, u.distanceM)
        assertNull(u.routeRemainingM)
    }

    @Test
    fun duplicateNodesDeduplicate() {
        val u = turn(
            at(0.1f, "500 m", "500 m", "Turn left") + at(0.9f, "Exit", "Mute", "13 min"),
        )
        assertEquals(TurnKind.LEFT, u.kind)
        assertEquals(500, u.distanceM)
        assertEquals(13 * 60, u.remainingSeconds)
    }
}
