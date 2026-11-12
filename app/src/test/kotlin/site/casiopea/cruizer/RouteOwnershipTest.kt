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
        Session.navActive.value = false
        Session.lastNavSource.value = null
        Session.postTurn(null)
    }

    @Test
    fun anotherAppCannotEndTheRoute() {
        Session.beginRoute("net.osmand")
        Session.postTurn(turn)

        // Opening Maps for traffic must not tear down OsmAnd's live ride.
        Session.endRoute("com.google.android.apps.maps")

        assertTrue(Session.navActive.value)
        assertEquals("net.osmand", Session.lastNavSource.value)
        assertEquals(turn, Session.currentTurn.value)
    }

    @Test
    fun ownerEndsTheRoute() {
        Session.beginRoute("net.osmand")
        Session.postTurn(turn)

        Session.endRoute("net.osmand")

        assertFalse(Session.navActive.value)
        assertNull(Session.lastNavSource.value)
        assertNull(Session.currentTurn.value)
    }

    @Test
    fun arrivedTurnIsNotALiveRoute() {
        Session.beginRoute("app.comaps", ended = true)
        assertFalse(Session.navActive.value)
    }

    @Test
    fun laterSourceTakesOwnership() {
        Session.beginRoute("net.osmand")
        Session.beginRoute("com.google.android.apps.maps")

        Session.endRoute("net.osmand")

        assertTrue(Session.navActive.value)
        assertEquals("com.google.android.apps.maps", Session.lastNavSource.value)
    }
}
