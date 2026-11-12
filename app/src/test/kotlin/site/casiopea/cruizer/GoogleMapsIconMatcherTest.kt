package site.casiopea.cruizer

import site.casiopea.cruizer.notify.GoogleMapsIconMatcher
import site.casiopea.cruizer.proto.TurnKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GoogleMapsIconMatcherTest {

    @Test
    fun everyTemplateMatchesItsOwnKind() {
        // Catches a template pasted under the wrong kind or duplicated across kinds.
        GoogleMapsIconMatcher.TEMPLATES.forEach { t ->
            assertEquals(t.kind, GoogleMapsIconMatcher.matchPack(t.bits))
        }
    }

    @Test
    fun thresholdIsExclusiveAt32Bits() {
        val t = GoogleMapsIconMatcher.TEMPLATES.first { it.kind == TurnKind.LEFT }
        fun flip(n: Int) = t.bits.clone().also { b ->
            repeat(n) { i -> b[i / 32] = b[i / 32] xor (1 shl (i % 32)) }
        }
        assertEquals(TurnKind.LEFT, GoogleMapsIconMatcher.matchPack(flip(31)))
        assertNotEquals(TurnKind.LEFT, GoogleMapsIconMatcher.matchPack(flip(32)))
    }

    @Test
    fun matchesKnownTemplatesExactly() {
        // Find first template for LEFT, RIGHT, STRAIGHT, DESTINATION, U_TURN
        val left = GoogleMapsIconMatcher.TEMPLATES.first { it.kind == TurnKind.LEFT }
        assertEquals(TurnKind.LEFT, GoogleMapsIconMatcher.matchPack(left.bits))

        val right = GoogleMapsIconMatcher.TEMPLATES.first { it.kind == TurnKind.RIGHT }
        assertEquals(TurnKind.RIGHT, GoogleMapsIconMatcher.matchPack(right.bits))

        val straight = GoogleMapsIconMatcher.TEMPLATES.first { it.kind == TurnKind.STRAIGHT }
        assertEquals(TurnKind.STRAIGHT, GoogleMapsIconMatcher.matchPack(straight.bits))

        val dest = GoogleMapsIconMatcher.TEMPLATES.first { it.kind == TurnKind.DESTINATION }
        assertEquals(TurnKind.DESTINATION, GoogleMapsIconMatcher.matchPack(dest.bits))

        val uturn = GoogleMapsIconMatcher.TEMPLATES.first { it.kind == TurnKind.U_TURN }
        assertEquals(TurnKind.U_TURN, GoogleMapsIconMatcher.matchPack(uturn.bits))
    }

    @Test
    fun matchesWithSmallBitNoise() {
        val left = GoogleMapsIconMatcher.TEMPLATES.first { it.kind == TurnKind.LEFT }
        val noisy = left.bits.clone()
        // Flip 5 bits (well below the 32-bit threshold)
        noisy[5] = noisy[5] xor 0b11111
        assertEquals(TurnKind.LEFT, GoogleMapsIconMatcher.matchPack(noisy))
    }

    @Test
    fun randomBitsDoNotMatch() {
        // Inverted or all-ones pattern does not match any template
        val inverted = IntArray(32) { -1 }
        assertNull(GoogleMapsIconMatcher.matchPack(inverted))

        val empty = IntArray(32) { 0 }
        assertNull(GoogleMapsIconMatcher.matchPack(empty))
    }

    @Test
    fun packsPixelsCorrectly() {
        val pixels = IntArray(32 * 32)
        // Set top-left pixel opaque
        pixels[0] = 0xFF000000.toInt()
        val packed = GoogleMapsIconMatcher.packPixels(pixels)
        // High bit of row 0 must be 1
        assertEquals(1, (packed[0] ushr 31) and 1)
        assertEquals(0, packed[1])
    }
}
