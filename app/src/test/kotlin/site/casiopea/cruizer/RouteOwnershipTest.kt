package site.casiopea.cruizer

import site.casiopea.cruizer.proto.TurnKind
import site.casiopea.cruizer.proto.TurnUpdate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Route ownership: only the source that started a ride may end it. */
class RouteOwnershipTest {

    private val turn = TurnUpdate(TurnKind.LEFT, 300)

    @Before
    fun clean() {
        NavRoute.navActive.value = false
        NavRoute.lastNavSource.value = null
        NavRoute.postTurn(null)
    }

    @Test
    fun anotherAppCannotEndTheRoute() {
        NavRoute.beginRoute("net.osmand")
        NavRoute.postTurn(turn)

        // Opening Maps for traffic must not tear down OsmAnd's live ride.
        NavRoute.endRoute("com.google.android.apps.maps")

        assertTrue(NavRoute.navActive.value)
        assertEquals("net.osmand", NavRoute.lastNavSource.value)
        assertEquals(turn, NavRoute.currentTurn.value)
    }

    @Test
    fun ownerEndsTheRoute() {
        NavRoute.beginRoute("net.osmand")
        NavRoute.postTurn(turn)

        NavRoute.endRoute("net.osmand")

        assertFalse(NavRoute.navActive.value)
        assertNull(NavRoute.lastNavSource.value)
        assertNull(NavRoute.currentTurn.value)
    }

    @Test
    fun arrivedTurnIsNotALiveRoute() {
        NavRoute.beginRoute("app.comaps")
        assertTrue(RideLog.liveRide.value.isActive)

        NavRoute.beginRoute("app.comaps", ended = true)

        assertFalse(NavRoute.navActive.value)
        // Arrival ends the ride: the next one must not inherit this clock.
        assertFalse(RideLog.liveRide.value.isActive)
        assertEquals(0L, RideLog.liveRide.value.elapsedSeconds)
    }

    @Test
    fun laterSourceTakesOwnership() {
        NavRoute.beginRoute("net.osmand")
        NavRoute.beginRoute("com.google.android.apps.maps")

        NavRoute.endRoute("net.osmand")

        assertTrue(NavRoute.navActive.value)
        assertEquals("com.google.android.apps.maps", NavRoute.lastNavSource.value)
    }
}
