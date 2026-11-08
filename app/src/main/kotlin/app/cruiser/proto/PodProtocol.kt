package app.cruiser.proto

/**
 * Tripper Pod BLE protocol engine.
 *
 * Byte layout independently derived from the RE app v10.1.2345 and cross-checked
 * against wazetripper's docs/PROTOCOL.md — see docs/PROTOCOL.md in this repo.
 * Packets: 20 bytes = 18 payload + CRC-16/CCITT-FALSE (poly 0x1021, init 0xFFFF,
 * MSB-first, no final XOR, big-endian).
 */
object PodProtocol {

    const val SERVICE_UUID = "01FF0100-BA5E-F4EE-5CA1-EB1E5E4B1CE0"
    const val CHAR_UUID = "01FF0101-BA5E-F4EE-5CA1-EB1E5E4B1CE0"

    // Fixed handshakes (CRCs verified against hardware-confirmed constants).
    val HANDSHAKE_PAIR_NEW = frame { it[0] = 0x21; it[1] = 0x01 } // 21 01 ... 50 A7
    val HANDSHAKE_RESUME = frame { it[0] = 0x21; it[1] = 0x00 }   // 21 00 ... 40 45
    val PING_FW = frame { it[0] = 0x03 }                          // 03 ... 45 D9

    fun crc16(payload: ByteArray): Int {
        var crc = 0xFFFF
        for (b in payload) {
            crc = (crc xor ((b.toInt() and 0xFF) shl 8)) and 0xFFFF
            repeat(8) {
                crc = if (crc and 0x8000 != 0) ((crc shl 1) xor 0x1021) and 0xFFFF
                else (crc shl 1) and 0xFFFF
            }
        }
        return crc and 0xFFFF
    }

    fun withCrc(payload18: ByteArray): ByteArray {
        require(payload18.size == 18)
        val out = ByteArray(20)
        payload18.copyInto(out)
        val crc = crc16(payload18)
        out[18] = ((crc shr 8) and 0xFF).toByte()
        out[19] = (crc and 0xFF).toByte()
        return out
    }

    private fun frame(body: (ByteArray) -> Unit): ByteArray {
        val p = ByteArray(18)
        body(p)
        return withCrc(p)
    }

    /** Phone -> Pod PIN confirmation: [0]=0x20, [1..6]=PIN ASCII (6 digits per the Pod's UI). */
    fun pinFrame(pin: String): ByteArray {
        require(pin.length == 6) { "Pod PIN is 6 digits" }
        return frame {
            it[0] = 0x20
            pin.toByteArray(Charsets.UTF_8).forEachIndexed { i, b -> it[i + 1] = b }
        }
    }

    /** [1]=(hour & 0x3F) | (fmt << 6); fmt: 0 = 24h, 1 = AM, 2 = PM. Canonical (wazetripper had PM wrong). */
    fun setTimeFrame(hour24: Int, minute: Int, is12hFormat: Boolean): ByteArray =
        withCrc(
            build18 {
                it[0] = 0x50
                if (!is12hFormat) {
                    it[1] = (hour24 and 0x3F).toByte()
                } else {
                    val h12 = if (hour24 % 12 == 0) 12 else hour24 % 12
                    val fmt = if (hour24 < 12) 0x01 else 0x02
                    it[1] = ((h12 and 0x3F) or (fmt shl 6)).toByte()
                }
                it[2] = minute.toByte()
            }
        )

    /** Overlay/control commands: 0x40 + sub; 0x05 = call overlay (re-sent every second while a call is active). */
    fun callFrame(): ByteArray = cmdFrame(0x05)
    fun cmdFrame(sub: Byte): ByteArray = withCrc(build18 { it[0] = 0x40; it[1] = sub })

    /** Distance encoders: unit 1 = meters, 2 = meters/100, 3 = miles x10, 4 = feet. */
    fun encodeDistance(meters: Int, imperial: Boolean): IntArray {
        val rounded =
            if (meters <= 100) roundTo(meters, 10) else roundTo(meters, 50)
        if (!imperial) {
            return if (rounded <= 999) intArrayOf(rounded, 1)
            else intArrayOf(rounded / 100, 2)
        }
        val feet = (rounded * 3.281).toInt()
        return if (feet > 550) intArrayOf((meters * 6.21371192E-4 * 10).toInt(), 3)
        else intArrayOf(feet, 4)
    }

    private fun roundTo(v: Int, step: Int) = ((v + step - 1) / step) * step

    /**
     * Intensity ladder from the RE app (canonical). Distances bucket the Pod's screen
     * brightness: 0-20 m -> 4, <=40 -> 3, <=60 -> 2, <=80 -> 1, <=100 -> 0,
     * 100-500 -> 5, >500 -> 6. KEEP maneuvers (codes 43/44) use 4 in the mid ranges.
     */
    fun intensity(distanceM: Int, maneuverCode: Int): Int {
        if (distanceM < 21) return 4
        if (distanceM < 41) return if (isKeep(maneuverCode)) 4 else 3
        if (distanceM < 61) return if (isKeep(maneuverCode)) 4 else 2
        if (distanceM < 81) return if (isKeep(maneuverCode)) 4 else 1
        if (distanceM < 101) return if (isKeep(maneuverCode)) 4 else 0
        if (distanceM < 501) return 5
        return 6
    }

    private fun isKeep(code: Int) =
        code == Icons.KEEP_LANE_LEFT.code || code == Icons.KEEP_LANE_RIGHT.code

    /** Bottom line: ETA arrival time (fmt 0/1/2), time remaining (fmt 10/20/30) or total distance. */
    sealed interface Bottom {
        data class Arrival(val secondsFromNow: Int, val is12hFormat: Boolean) : Bottom
        data class Remaining(val seconds: Int) : Bottom
        data class Total(val meters: Int, val imperial: Boolean) : Bottom

        /** No data for the selected mode: blank the line rather than showing a false "0 m". */
        data object None : Bottom
    }

    /**
     * Navigation frame (0x10 0x11). exitRoundabout: 1-based exit when emitting a roundabout,
     * else 0. nextDistanceOnlyNear mirrors the RE app (next data only when intensity < 5).
     */
    fun navFrame(
        maneuver: Int,
        distanceM: Int,
        imperial: Boolean,
        next: Int?,
        nextDistanceM: Int?,
        bottom: Bottom,
        night: Boolean,
        exit: Int = 0,
        intensityOverride: Int? = null,
    ): ByteArray {
        val (val0, unit0) = encodeDistance(distanceM, imperial).let { it[0] to it[1] }
        val intensity = intensityOverride ?: intensity(distanceM, maneuver)
        val nextVal = nextDistanceM?.let { encodeDistance(it, imperial) }
        // Next arrow only carries data while the current maneuver is close (RE behavior).
        val hasNext = next != null && intensity < 5
        val p = build18 {
            it[0] = 0x10
            it[1] = 0x11
            it[2] = (maneuver and 0xFF).toByte()
            it[3] = ((val0 shr 8) and 0xFF).toByte()
            it[4] = (val0 and 0xFF).toByte()
            it[5] = unit0.toByte()
            it[6] = (((intensity and 0x0F) shl 4) or (if (night) 1 else 0)).toByte()
            it[7] = (if (hasNext) next else 0xFF).toByte()
            if (hasNext && nextVal != null) {
                it[8] = ((nextVal[0] shr 8) and 0xFF).toByte()
                it[9] = (nextVal[0] and 0xFF).toByte()
            } else {
                it[8] = 0xFF.toByte(); it[9] = 0xFF.toByte()
            }
            it[10] = ((((nextVal?.get(1) ?: 0xF) and 0x0F) shl 4)
                or (if (exit > 0) 2 else 1)).toByte()
        }

        when (val b = bottom) {
            is Bottom.Arrival -> {
                val cal = java.util.Calendar.getInstance().apply {
                    add(java.util.Calendar.SECOND, b.secondsFromNow)
                }
                val h24 = cal.get(java.util.Calendar.HOUR_OF_DAY)
                val m = cal.get(java.util.Calendar.MINUTE)
                val (fmt, hourPart) =
                    if (!bottom.is12hFormat) 0x00 to h24
                    else (if (h24 < 12) 0x01 else 0x02) to (if (h24 % 12 == 0) 12 else h24 % 12)
                p[11] = ((fmt shl 6) or (hourPart and 0x3F)).toByte()
                p[12] = m.toByte()
                p[13] = 0 // mode 0 = time display
            }
            is Bottom.Remaining -> {
                // The verified layout has no "time remaining" format (wazetripper's
                // fmt 10/20 doesn't fit the byte); we render remaining time as a
                // clock-style h:mm with etaFormat 0. Hours cap at 63 (6 bits) —
                // past that (2.6 days) the caller should fall back to distance.
                val h = (b.seconds / 3600).coerceAtMost(63)
                val mm = b.seconds % 3600 / 60
                p[11] = (h and 0x3F).toByte()
                p[12] = mm.toByte()
                p[13] = 0
            }
            is Bottom.Total -> {
                val (v, u) = encodeDistance(b.meters, b.imperial)
                p[11] = ((v shr 8) and 0xFF).toByte()
                p[12] = (v and 0xFF).toByte()
                p[13] = u.toByte() // distance mode: any non-zero [13]
            }
            Bottom.None -> {
                p[11] = 0xFF.toByte(); p[12] = 0xFF.toByte(); p[13] = 0xFF.toByte()
            }
        }
        return withCrc(p)
    }

    fun build18(body: (ByteArray) -> Unit): ByteArray {
        val p = ByteArray(18)
        body(p)
        return p
    }

    /** Idle/informational screens that reuse the nav frame with no data (low battery 0x44, no-data 0x42, re-route 0x1C). */
    fun idleFrame(icon: Int, night: Boolean): ByteArray = withCrc(
        build18 {
            it[0] = 0x10; it[1] = 0x11
            it[2] = icon.toByte()
            it[5] = 0x01
            it[6] = (if (night) 1 else 0).toByte()
            it[7] = 0xFF.toByte()
        }
    )

    /** Status overlay screens: route started (0x45), low battery (0x44), re-routing (0x1C). */
    fun statusFrame(icon: Icons, night: Boolean): ByteArray = withCrc(
        build18 {
            it[0] = 0x10; it[1] = 0x11
            it[2] = icon.code.toByte()
            it[5] = 0x01
            it[6] = (if (night) 1 else 0).toByte()
            it[7] = 0xFF.toByte()
            it[10] = 0x41
            it[11] = 0xFF.toByte(); it[12] = 0xFF.toByte(); it[13] = 0xFF.toByte()
        }
    )

    fun routeStartedFrame(night: Boolean): ByteArray = statusFrame(Icons.ROUTE_STARTED, night)
    fun lowBatteryFrame(night: Boolean): ByteArray = statusFrame(Icons.LOW_BATTERY, night)
    fun reRouteFrame(night: Boolean): ByteArray = statusFrame(Icons.RE_ROUTE, night)
}
