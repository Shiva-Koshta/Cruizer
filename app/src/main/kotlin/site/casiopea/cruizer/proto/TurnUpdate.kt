package site.casiopea.cruizer.proto

/**
 * One turn-by-turn update, normalized from whatever turn source produced it
 * (nav notification parser, playground, fueller GOI).
 */
data class TurnUpdate(
    val kind: TurnKind,
    val distanceM: Int?,
    val exit: Int? = null,          // roundabout exit ordinal (1..9)
    val next: TurnKind? = null,
    val nextDistanceM: Int? = null,
    val arrivalSecondsFromNow: Int? = null,
    val routeRemainingM: Int? = null,
    val remainingSeconds: Int? = null,
    val ended: Boolean = false,     // route finished; caller stops nav frames
)

/** Neutral maneuver vocabulary from parsed instruction text. */
enum class TurnKind {
    DEPART, STRAIGHT, LEFT, RIGHT, SLIGHT_LEFT, SLIGHT_RIGHT,
    SHARP_LEFT, SHARP_RIGHT, KEEP_LEFT, KEEP_RIGHT, U_TURN,
    MERGE_LEFT, MERGE_RIGHT, FORK_LEFT, FORK_RIGHT,
    OFF_RAMP_LEFT, OFF_RAMP_RIGHT, ON_RAMP, ROUNDABOUT,
    FERRY, DESTINATION, RE_ROUTE, UNKNOWN;

    /** Map to the Pod icon; for "keep" we send the dedicated lane icons as the main icon. */
    fun code(): Int = when (this) {
        DEPART -> Icons.DEPART.code
        STRAIGHT -> Icons.STRAIGHT.code
        LEFT -> Icons.TURN_LEFT.code
        RIGHT -> Icons.TURN_RIGHT.code
        SLIGHT_LEFT -> Icons.SLIGHT_LEFT.code
        SLIGHT_RIGHT -> Icons.SLIGHT_RIGHT.code
        SHARP_LEFT -> Icons.SHARP_LEFT.code
        SHARP_RIGHT -> Icons.SHARP_RIGHT.code
        KEEP_LEFT -> Icons.KEEP_LANE_LEFT.code
        KEEP_RIGHT -> Icons.KEEP_LANE_RIGHT.code
        U_TURN -> Icons.U_TURN_CCW.code // left-hand direction is unknowable from text; CCW is the common case
        MERGE_LEFT -> Icons.MERGE_LEFT.code
        MERGE_RIGHT -> Icons.MERGE_RIGHT.code
        FORK_LEFT -> Icons.FORK_LEFT.code
        FORK_RIGHT -> Icons.FORK_RIGHT.code
        OFF_RAMP_LEFT -> Icons.OFF_RAMP_LEFT.code
        OFF_RAMP_RIGHT -> Icons.OFF_RAMP_RIGHT.code
        ON_RAMP -> Icons.ON_RAMP_RIGHT.code
        ROUNDABOUT -> Icons.ROUNDABOUT_CW.code // base icon; exitCode() is used when the ordinal is known
        FERRY -> Icons.FERRY_BOAT.code
        DESTINATION -> Icons.DESTINATION.code
        RE_ROUTE -> Icons.RE_ROUTE.code
        UNKNOWN -> -1
    }

    /** Small next-maneuver arrow: keep/exit families use the generic lane-highlight icons. */
    fun nextCode(): Int = when (this) {
        KEEP_LEFT, OFF_RAMP_LEFT -> Icons.KEEP_LANE_LEFT.code
        KEEP_RIGHT, OFF_RAMP_RIGHT -> Icons.KEEP_LANE_RIGHT.code
        else -> code()
    }
}
