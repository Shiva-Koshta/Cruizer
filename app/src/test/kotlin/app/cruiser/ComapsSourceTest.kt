package app.cruiser

import app.cruiser.notify.ComapsSource
import app.cruiser.proto.TurnKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Pure provider-row -> TurnUpdate mapping (CoMaps NavigationContentProvider contract). */
class ComapsSourceTest {

    @Test
    fun slightRightWithKmDistance() {
        val u = ComapsSource.turnUpdate("TURN_SLIGHT_RIGHT", null, "1.2 km", "14 km", 1200, 0)!!
        assertEquals(TurnKind.SLIGHT_RIGHT, u.kind)
        assertEquals(1200, u.distanceM)
        assertEquals(14_000, u.routeRemainingM)
        assertEquals(1200, u.remainingSeconds)
        assertEquals(1200, u.arrivalSecondsFromNow)
        assertNull(u.exit)
    }

    @Test
    fun roundaboutCarriesExitNumber() {
        val u = ComapsSource.turnUpdate("ENTER_ROUND_ABOUT", null, "300 m", "5.4 km", 420, 3)!!
        assertEquals(TurnKind.ROUNDABOUT, u.kind)
        assertEquals(3, u.exit)
        assertEquals(300, u.distanceM)
    }

    @Test
    fun exitHighwayMapsToOffRamp() {
        val u = ComapsSource.turnUpdate("EXIT_HIGHWAY_TO_RIGHT", null, "250 m", null, null, 0)!!
        assertEquals(TurnKind.OFF_RAMP_RIGHT, u.kind)
        assertNull(u.routeRemainingM)
    }

    @Test
    fun pedestrianFallback() {
        val u = ComapsSource.turnUpdate(null, "TURN_LEFT", "50 m", null, null, 0)!!
        assertEquals(TurnKind.LEFT, u.kind)
    }

    @Test
    fun reachedDestinationEndsRoute() {
        val u = ComapsSource.turnUpdate("REACHED_YOUR_DESTINATION", null, null, "0 m", 0, 0)!!
        assertEquals(TurnKind.DESTINATION, u.kind)
        assertEquals(true, u.ended)
    }

    @Test
    fun unknownDirectionIsNull() {
        assertNull(ComapsSource.turnUpdate("SOMETHING_NEW", null, "100 m", null, null, 0))
    }
}
