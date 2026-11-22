package site.casiopea.cruizer.ui

/**
 * Frame lab presets (debug only): colour test from issues/01 §4b, then bench tests from issues/02.
 * [hex] is the 18-byte payload (zero-padded, CRC added by the lab); [hold] = re-send every tick.
 */
data class LabPreset(val group: String, val id: String, val hex: String, val expect: String, val hold: Boolean = true)

private const val NAV = "10 11"
private const val NO_NEXT = "FF FF FF 41 FF FF FF"
private const val COMPASS = "10 11 41 00 00 00 00 FF 00 00 00 FF FF FF"

/** Icon at 50 m, day, no next arrow, blank bottom line (the wazetripper layout). */
private fun icon(group: String, id: String, code: String, expect: String) =
    LabPreset(group, id, "$NAV $code 00 32 01 00 $NO_NEXT", expect)

val LAB_PRESETS: List<LabPreset> = listOf(
    LabPreset("Colour", "C1", "$NAV 15 00 14 01 40 $NO_NEXT", "red arrow (Super Pod baseline)"),
    LabPreset("Colour", "C2", "$NAV 15 00 14 01 40 FF FF FF F1 FF FF FF", "B&W if [10] is the switch"),
    LabPreset("Colour", "C3", "$NAV 15 00 14 01 40 FF FF FF 11 FF FF FF", "B&W if [10] is the switch"),
    LabPreset("Colour", "C4", "$NAV 15 00 14 01 40 09 00 C8 41 FF FF FF", "red (isolates [8-9])"),
    LabPreset("Colour", "C5", "$NAV 15 00 14 01 40 09 00 C8 11 0E 1E 00", "B&W (today's Cruizer frame)"),
    LabPreset("Colour", "C6", "$NAV 15 00 14 01 40 FF FF FF 41 0E 1E 00", "red (isolates ETA bottom line)"),
    LabPreset("Colour", "C7", "$NAV 15 00 14 01 40 FF FF FF 31 FF FF FF", "? ([10]=31 highway)"),
    LabPreset("Colour", "C8", "$NAV 15 00 0A 01 50 $NO_NEXT", "yellow arrow"),
    LabPreset("Colour", "C9", "$NAV 15 00 3C 01 20 $NO_NEXT", "partial red shaft"),

    icon("B1 roundabout", "B1-a", "0A", "clockwise, no number?"),
    icon("B1 roundabout", "B1-b", "0C", "clockwise + 2?"),
    icon("B1 roundabout", "B1-c", "13", "clockwise + 9?"),
    icon("B1 roundabout", "B1-d", "31", "counter-clockwise"),
    icon("B1 roundabout", "B1-e", "33", "counter-clockwise + 2?"),

    LabPreset("B2 colour [6]", "B2-a", "$NAV 15 01 F4 01 00 $NO_NEXT", "plain"),
    LabPreset("B2 colour [6]", "B2-b", "$NAV 15 00 3C 01 20 $NO_NEXT", "partial red"),
    LabPreset("B2 colour [6]", "B2-c", "$NAV 15 00 14 01 40 $NO_NEXT", "red arrow"),
    LabPreset("B2 colour [6]", "B2-d", "$NAV 15 00 0A 01 50 $NO_NEXT", "yellow arrow"),
    LabPreset("B2 colour [6]", "B2-e", "$NAV 15 00 0A 01 51 $NO_NEXT", "yellow, night theme"),
    LabPreset("B2 colour [6]", "B2-f", "$NAV 15 01 F4 01 60 $NO_NEXT", "unknown (curiosity)"),

    LabPreset("B3 next arrow", "B3-a", "$NAV 15 00 32 01 40 14 FF FF 41 FF FF FF", "small arrow shown?"),
    LabPreset("B3 next arrow", "B3-b", "$NAV 15 00 32 01 40 14 00 C8 11 FF FF FF", "small arrow? 200 m near it?"),
    LabPreset("B3 next arrow", "B3-c", "$NAV 15 00 32 01 40 FF FF FF F1 FF FF FF", "difference vs 41?"),
    LabPreset("B3 next arrow", "B3-d1", "$NAV 15 00 32 01 40 FF FF FF 31 FF FF FF", "difference vs 41?"),
    LabPreset("B3 next arrow", "B3-d2", "$NAV 15 00 32 01 40 FF FF FF 42 FF FF FF", "difference vs 41?"),

    icon("B4 keep", "B4-a", "28", "keep-left glyph?"),
    icon("B4 keep", "B4-b", "27", "keep-right glyph?"),
    icon("B4 keep", "B4-c", "2B", "large icon drawn?"),
    icon("B4 keep", "B4-d", "2C", "large icon drawn?"),

    LabPreset("B5 12h time", "B5-a", "50 83 1E", "3:30 PM (Cruizer PM=0x80|3)", hold = false),
    LabPreset("B5 12h time", "B5-b", "50 03 1E", "3:30 PM (wazetripper plain)", hold = false),
    LabPreset("B5 12h time", "B5-c", "50 0F 1E 01", "3:30 PM (Super 24h + [3]=01)", hold = false),
    LabPreset("B5 12h time", "B5-d", "50 49 1E", "9:30 AM", hold = false),
    LabPreset("B5 12h time", "B5-e1", "50 8C 00", "12:00 PM (noon = 12)", hold = false),
    LabPreset("B5 12h time", "B5-e2", "50 80 00", "12:00 PM (noon = 0)", hold = false),
    LabPreset("B5 12h time", "B5-f1", "$NAV 15 00 32 01 00 FF FF FF 41 8C 1E 00", "ETA bottom 12:30 (12)"),
    LabPreset("B5 12h time", "B5-f2", "$NAV 15 00 32 01 00 FF FF FF 41 80 1E 00", "ETA bottom 12:30 (0)"),

    LabPreset("B6 duration", "B6-a", "$NAV 15 00 32 01 00 FF FF FF 41 01 1E 01", "1:30? or a distance?"),
    LabPreset("B6 duration", "B6-b", "$NAV 15 00 32 01 00 FF FF FF 41 01 1E 03", "record"),

    LabPreset("B9 compass", "B9-N", "$COMPASS 10", "N"),
    LabPreset("B9 compass", "B9-E", "$COMPASS 20", "E"),
    LabPreset("B9 compass", "B9-NE", "$COMPASS 50", "NE (disputed: NE vs NW)"),
    LabPreset("B9 compass", "B9-NW", "$COMPASS 60", "NW"),
    LabPreset("B9 compass", "B9-night", "10 11 41 00 00 00 01 FF 00 00 00 FF FF FF 10", "N, night palette"),

    LabPreset("B10 status", "B10-a", "$NAV 44 00 00 01 00 FF 00 00 41 FF FF FF", "battery glyph"),
    LabPreset("B10 status", "B10-b", "$NAV 42 00 00 01 00 FF 00 00 41 FF FF FF", "no-data glyph"),
    LabPreset("B10 status", "B10-c", "$NAV 1C 00 00 01 00 FF 00 00 41 FF FF FF", "recalculating glyph"),

    LabPreset("B11 Pod info", "B11-fw", "03", "reply 03 xx yy … (firmware)", hold = false),
    LabPreset("B11 Pod info", "B11-sn", "30", "reply 30 + ASCII serial", hold = false),

    icon("B12 U-turn", "B12-a", "1A", "right-around (clockwise)?"),
    icon("B12 U-turn", "B12-b", "3D", "left-around?"),

    LabPreset("B13 speedo", "B13-a", "$NAV 09 00 3C 01 00 $NO_NEXT", "60 m straight: readable as speed?"),
    LabPreset("B13 speedo", "B13-b", "$NAV FF 00 3C 01 00 $NO_NEXT", "blank icon + 60?"),
)
