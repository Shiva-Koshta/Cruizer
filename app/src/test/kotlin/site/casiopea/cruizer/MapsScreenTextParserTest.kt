package site.casiopea.cruizer

import site.casiopea.cruizer.notify.MapsScreenTextParser
import site.casiopea.cruizer.notify.NavTextParser
import site.casiopea.cruizer.proto.TurnKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Google Maps on-screen accessibility text → turn updates (pure parser). */
class MapsScreenTextParserTest {

    private fun lines(vararg s: String) = listOf(*s)

    @Test
    fun plainMapBrowseIsIgnored() {
        // No "exit navigation" control: never treat a merely-open Maps as a route.
        assertNull(
            MapsScreenTextParser.extract(
                lines("345 Main St", "Restaurants", "Search here"),
            ),
        )
    }

    @Test
    fun navModeTurnWithDistance() {
        val p = MapsScreenTextParser.extract(
            lines(
                "In 500 m",
                "Turn right onto Market St",
                "Exit navigation",
                "4 min", "2.1 km",
            ),
        )
        assertTrue(p is NavTextParser.Parsed.Turn)
        val u = (p as NavTextParser.Parsed.Turn).update
        assertEquals(TurnKind.RIGHT, u.kind)
        assertEquals(500, u.distanceM)
    }

    @Test
    fun duplicateNodesDeduplicate() {
        val p = MapsScreenTextParser.extract(
            lines("500 m", "500 m", "Turn left", "Exit navigation"),
        )
        assertEquals(TurnKind.LEFT, (p as NavTextParser.Parsed.Turn).update.kind)
        assertEquals(500, p.update.distanceM)
    }

    @Test
    fun etaLineOnScreenParses() {
        val now = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, 14)
            set(java.util.Calendar.MINUTE, 30)
        }
        val p = MapsScreenTextParser.extract(
            lines("Head east", "Exit navigation", "Arrive 3:45 PM"),
            now,
        )
        val u = (p as NavTextParser.Parsed.Turn).update
        assertEquals(TurnKind.DEPART, u.kind)
        assertTrue(u.arrivalSecondsFromNow != null)
    }
}
