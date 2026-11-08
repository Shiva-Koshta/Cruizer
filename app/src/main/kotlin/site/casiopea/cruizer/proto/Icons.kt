package site.casiopea.cruizer.proto

/**
 * Pod maneuver icons — canonical values from the RE app (mgj enum), see docs/PROTOCOL.md §5.
 * Roundabout-exit icons (0x32 + n) come from hardware observation by wazetripper and are
 * marked Likely there; the RE app itself never sends exits.
 */
enum class Icons(val code: Int) {
    UNKNOWN(-1),
    DESTINATION(0x00),
    DESTINATION_LEFT(0x01),
    DESTINATION_RIGHT(0x02),
    MERGE_RIGHT(0x03),
    MERGE_LEFT(0x04),
    FORK_RIGHT(0x05),
    FORK_LEFT(0x06),
    OFF_RAMP_RIGHT(0x07),
    OFF_RAMP_LEFT(0x08),
    STRAIGHT(0x09),
    ROUNDABOUT_CW(0x0A),          // + (exit - 1) for exits 1..9 -> 0x32 range is wrong; 0x0A+? see exitCode()
    TURN_LEFT(0x14),
    TURN_RIGHT(0x15),
    SHARP_LEFT(0x16),
    SHARP_RIGHT(0x17),
    SLIGHT_LEFT(0x18),
    SLIGHT_RIGHT(0x19),
    U_TURN_CW(0x1A),
    MERGE(0x1B),
    RE_ROUTE(0x1C),
    ON_RAMP_RIGHT(0x1D),
    ON_RAMP_LEFT(0x1E),
    ON_RAMP_SLIGHT_RIGHT(0x1F),
    ON_RAMP_SLIGHT_LEFT(0x20),
    ON_RAMP_SHARP_RIGHT(0x21),
    ON_RAMP_SHARP_LEFT(0x22),
    OFF_RAMP_SLIGHT_RIGHT(0x2D),
    OFF_RAMP_SLIGHT_LEFT(0x2E),
    OFF_RAMP_KEEP_SHARP_RIGHT(0x2F),
    OFF_RAMP_KEEP_SHARP_LEFT(0x30),
    ROUNDABOUT_CCW(0x31),
    CALL(0x40),
    ROUNDABOUT_EXIT_FIRST(0x32),  // hardware-observed sequence start (wazetripper, Likely)
    DEPART(0x3C),
    U_TURN_CCW(0x3D),
    FERRY_BOAT(0x3E),
    FERRY_TRAIN(0x3F),
    MOBILE_DATA(0x42),
    KEEP_LANE_LEFT(0x2B),
    KEEP_LANE_RIGHT(0x2C),
    LOW_BATTERY(0x44),
    ROUTE_STARTED(0x45),
    BLANK(0xFF);

    companion object {
        /** Right-hand-traffic roundabout exit icon (exits 1..9; clamped). Likely per wazetripper. */
        fun exitCode(exit: Int): Int = 0x32 + (exit.coerceIn(1, 9) - 1)
    }
}
