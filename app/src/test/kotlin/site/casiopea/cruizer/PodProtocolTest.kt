package site.casiopea.cruizer

import site.casiopea.cruizer.proto.Icons
import site.casiopea.cruizer.proto.PodProtocol
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Byte-level assertions against the committed docs/PROTOCOL.md. The handshake and
 * ping CRCs are hardware-confirmed constants from wazetripper's documentation
 * (§3); all frames are 20 bytes = CRC-16/CCITT-FALSE over the 18-byte payload.
 */
class PodProtocolTest {

    private fun crcOf(f: ByteArray) = ((f[18].toInt() and 0xFF) shl 8) or (f[19].toInt() and 0xFF)

    @Test
    fun `frame is 20 bytes with trailing CRC`() {
        val f = PodProtocol.navFrame(
            maneuver = Icons.STRAIGHT.code,
            distanceM = 300,
            imperial = false,
            next = null,
            nextDistanceM = null,
            bottom = PodProtocol.Bottom.Remaining(600),
            night = false,
        )
        assertEquals(20, f.size)
        assertEquals(0x10, f[0].toInt())
        assertEquals(0x11, f[1].toInt())
        // CRC self-referential: recompute over [0..17] and compare with [18..19].
        assertEquals(crcOf(f), PodProtocol.crc16(f.copyOfRange(0, 18)))
    }

    @Test
    fun `handshake resume matches hardware constant 40 45`() {
        val f = PodProtocol.HANDSHAKE_RESUME
        assertEquals(0x21, f[0].toInt())
        assertEquals(0x00, f[1].toInt())
        assertEquals(0x4045, crcOf(f))
    }

    @Test
    fun `handshake pair-new matches hardware constant 50 A7`() {
        val f = PodProtocol.HANDSHAKE_PAIR_NEW
        assertEquals(0x21, f[0].toInt())
        assertEquals(0x01, f[1].toInt())
        assertEquals(0x50A7, crcOf(f))
    }

    @Test
    fun `ping firmware matches hardware constant 45 D9`() {
        val f = PodProtocol.PING_FW
        assertEquals(0x03, f[0].toInt())
        assertEquals(0x45D9, crcOf(f))
    }

    @Test
    fun `pin frame is ASCII digits after 0x20`() {
        val f = PodProtocol.pinFrame("123456")
        assertEquals(0x20, f[0].toInt())
        assertEquals("123456", String(f, 1, 6, Charsets.US_ASCII))
        assertEquals(0x36, f[6].toInt()) // '6' — bytes 7..17 remain zeros
        assertTrue(f.copyOfRange(7, 18).all { it == 0.toByte() })
    }

    @Test
    fun `set time 24h packs hour into low 6 bits`() {
        val f = PodProtocol.setTimeFrame(hour24 = 14, minute = 37, is12hFormat = false)
        assertEquals(0x50, f[0].toInt())
        assertEquals(14, f[1].toInt() and 0x3F)
        assertEquals(0, (f[1].toInt() ushr 6) and 0x03)
        assertEquals(37, f[2].toInt())
    }

    @Test
    fun `set time 12h packs AM-PM into bits 6-7`() {
        // 16:05 -> PM (fmt 2), hour 4.
        val p = PodProtocol.setTimeFrame(hour24 = 16, minute = 5, is12hFormat = true)
        assertEquals(4, p[1].toInt() and 0x3F)
        assertEquals(2, (p[1].toInt() ushr 6) and 0x03)
        // 00:30 -> AM (fmt 1), hour 12.
        val a = PodProtocol.setTimeFrame(hour24 = 0, minute = 30, is12hFormat = true)
        assertEquals(12, a[1].toInt() and 0x3F)
        assertEquals(1, (a[1].toInt() ushr 6) and 0x03)
    }

    @Test
    fun `intensity ladder follows the RE buckets`() {
        assertEquals(4, PodProtocol.intensity(15, Icons.TURN_RIGHT.code))
        assertEquals(3, PodProtocol.intensity(35, Icons.TURN_RIGHT.code))
        assertEquals(2, PodProtocol.intensity(55, Icons.TURN_RIGHT.code))
        assertEquals(1, PodProtocol.intensity(75, Icons.TURN_RIGHT.code))
        assertEquals(0, PodProtocol.intensity(95, Icons.TURN_RIGHT.code))
        assertEquals(5, PodProtocol.intensity(300, Icons.TURN_RIGHT.code))
        assertEquals(6, PodProtocol.intensity(900, Icons.TURN_RIGHT.code))
        // Keep maneuvers hold intensity 4 through the mid buckets (canonical behavior).
        assertEquals(4, PodProtocol.intensity(55, Icons.KEEP_LANE_LEFT.code))
        assertEquals(4, PodProtocol.intensity(85, Icons.KEEP_LANE_RIGHT.code))
    }

    @Test
    fun `nav frame packs icon distance and night flag`() {
        val f = PodProtocol.navFrame(
            maneuver = Icons.TURN_RIGHT.code, // 0x15
            distanceM = 55,
            imperial = false,
            next = Icons.STRAIGHT.code, // 0x09
            nextDistanceM = 220,
            bottom = PodProtocol.Bottom.Remaining(2 * 3600 + 5 * 60),
            night = true,
        )
        assertEquals(0x15, f[2].toInt())
        // 55 m -> ceil to 60? No: <=100 step 10 -> 60, sent as meters raw with unit 1.
        assertEquals(60, (f[3].toInt() shl 8) or f[4].toInt())
        assertEquals(1, f[5].toInt())
        // [6] = intensity(2 for 55) << 4 | night(1).
        assertEquals(0x21, f[6].toInt())
        // Next arrow carries data while intensity < 5.
        assertEquals(0x09, f[7].toInt())
        val nextVal = PodProtocol.encodeDistance(220, false)
        assertEquals(nextVal[0], ((f[8].toInt() and 0xFF) shl 8) or (f[9].toInt() and 0xFF))
        assertEquals(nextVal[1], (f[10].toInt() ushr 4) and 0x0F)
        // Bottom: time mode, etaFormat 0, remaining rendered h:mm = 2:05
        assertEquals(2, f[11].toInt())
        assertEquals(5, f[12].toInt())
        assertEquals(0, f[13].toInt())
    }

    @Test
    fun `next arrow suppressed above intensity 5`() {
        val f = PodProtocol.navFrame(
            maneuver = Icons.STRAIGHT.code,
            distanceM = 900, // intensity 6
            imperial = false,
            next = Icons.STRAIGHT.code,
            nextDistanceM = 100,
            bottom = PodProtocol.Bottom.Total(5000, false),
            night = false,
        )
        assertEquals(0xFF, f[7].toInt() and 0xFF)
        assertEquals(0xFF, f[8].toInt() and 0xFF)
        assertEquals(0xFF, f[9].toInt() and 0xFF)
        // Bottom Total: value BE at [11-12], unit at [13]. 5000 m -> hundreds (unit 2).
        assertEquals(50, (f[11].toInt() shl 8) or f[12].toInt())
        assertEquals(2, f[13].toInt())
    }

    @Test
    fun `distance encoder picks meters or hundreds`() {
        // metric
        assertTrue(PodProtocol.encodeDistance(85, false).contentEquals(intArrayOf(90, 1)))
        assertTrue(PodProtocol.encodeDistance(1200, false).contentEquals(intArrayOf(12, 2)))
        // imperial beyond 550 ft switches to tenth-miles
        val tenMiles = PodProtocol.encodeDistance(16093, true)
        assertEquals(3, tenMiles[1])
        assertEquals(99, tenMiles[0]) // 10 mi x10, truncated
    }

    @Test
    fun `bottom none blanks the line instead of showing 0 m`() {
        val f = PodProtocol.navFrame(
            maneuver = Icons.STRAIGHT.code,
            distanceM = 300,
            imperial = false,
            next = null,
            nextDistanceM = null,
            bottom = PodProtocol.Bottom.None,
            night = false,
        )
        assertEquals(0xFF, f[11].toInt() and 0xFF)
        assertEquals(0xFF, f[12].toInt() and 0xFF)
        assertEquals(0xFF, f[13].toInt() and 0xFF)
        assertEquals(crcOf(f), PodProtocol.crc16(f.copyOfRange(0, 18)))
    }
}
