package site.casiopea.cruizer.notify

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Icon
import site.casiopea.cruizer.proto.TurnKind

/**
 * Extracts and matches Google Maps maneuver icons from Notification largeIcon (API 23+).
 * Renders the icon onto a 32x32 monochrome bitmap and compares against known Google Maps
 * arrow templates using bitwise Hamming distance (adapted from Gadgetbridge).
 */
object GoogleMapsIconMatcher {

    class Template(val kind: TurnKind, val bits: IntArray)

    val TEMPLATES: List<Template> = listOf(
        Template(TurnKind.STRAIGHT, intArrayOf(
            0x00000000.toInt(), 0x00000000.toInt(), 0x00000000.toInt(), 0x00000000.toInt(),
            0x00008000.toInt(), 0x0001C000.toInt(), 0x0003E000.toInt(), 0x000FF000.toInt(),
            0x001FF800.toInt(), 0x003FFC00.toInt(), 0x007FFE00.toInt(), 0x00FBDF00.toInt(),
            0x00F3CF00.toInt(), 0x0063C700.toInt(), 0x0003C000.toInt(), 0x0003C000.toInt(),
            0x0003C000.toInt(), 0x0003C000.toInt(), 0x0003C000.toInt(), 0x0003C000.toInt(),
            0x00000000.toInt(), 0x0003C000.toInt(), 0x0003C000.toInt(), 0x0003C000.toInt(),
            0x00000000.toInt(), 0x0003C000.toInt(), 0x0003C000.toInt(), 0x0003C000.toInt(),
            0x00000000.toInt(), 0x00000000.toInt(), 0x00000000.toInt(), 0x00000000.toInt()
        )),
        Template(TurnKind.STRAIGHT, intArrayOf(
            0x00000000.toInt(), 0x00000000.toInt(), 0x00000000.toInt(), 0x00000000.toInt(),
            0x00008000.toInt(), 0x0001C000.toInt(), 0x0003E000.toInt(), 0x000FF000.toInt(),
            0x001FF800.toInt(), 0x003FFC00.toInt(), 0x007FFE00.toInt(), 0x00FBDF00.toInt(),
            0x00F3CF00.toInt(), 0x0063C700.toInt(), 0x0003C000.toInt(), 0x0003C000.toInt(),
            0x0003C000.toInt(), 0x0003C000.toInt(), 0x0003C000.toInt(), 0x0003C000.toInt(),
            0x0003C000.toInt(), 0x0003C000.toInt(), 0x0003C000.toInt(), 0x0003C000.toInt(),
            0x0003C000.toInt(), 0x0003C000.toInt(), 0x0003C000.toInt(), 0x0003C000.toInt(),
            0x00000000.toInt(), 0x00000000.toInt(), 0x00000000.toInt(), 0x00000000.toInt()
        )),
        Template(TurnKind.LEFT, intArrayOf(
            0x00000000.toInt(), 0x00000000.toInt(), 0x00000000.toInt(), 0x00000000.toInt(),
            0x00000000.toInt(), 0x00300000.toInt(), 0x00780000.toInt(), 0x00F80000.toInt(),
            0x01F00000.toInt(), 0x03E00000.toInt(), 0x07C00000.toInt(), 0x0FFFFF80.toInt(),
            0x1FFFFFE0.toInt(), 0x0FFFFFF0.toInt(), 0x078001F8.toInt(), 0x03C00078.toInt(),
            0x03E00038.toInt(), 0x01F00038.toInt(), 0x00F80038.toInt(), 0x00780038.toInt(),
            0x00100038.toInt(), 0x00000038.toInt(), 0x00000038.toInt(), 0x00000038.toInt(),
            0x00000038.toInt(), 0x00000038.toInt(), 0x00000038.toInt(), 0x00000038.toInt(),
            0x00000000.toInt(), 0x00000000.toInt(), 0x00000000.toInt(), 0x00000000.toInt()
        )),
        Template(TurnKind.RIGHT, intArrayOf(
            0x00000000.toInt(), 0x00000000.toInt(), 0x00000000.toInt(), 0x00000000.toInt(),
            0x00000000.toInt(), 0x00000C00.toInt(), 0x00001E00.toInt(), 0x00001F00.toInt(),
            0x00000F80.toInt(), 0x000007C0.toInt(), 0x000003E0.toInt(), 0x01FFFFF0.toInt(),
            0x07FFFFF8.toInt(), 0x0FFFFFF8.toInt(), 0x0F8001F0.toInt(), 0x1E0003E0.toInt(),
            0x1C0007C0.toInt(), 0x1C000F80.toInt(), 0x1C001F00.toInt(), 0x1C001E00.toInt(),
            0x1C000800.toInt(), 0x1C000000.toInt(), 0x1C000000.toInt(), 0x1C000000.toInt(),
            0x1C000000.toInt(), 0x1C000000.toInt(), 0x1C000000.toInt(), 0x1C000000.toInt(),
            0x00000000.toInt(), 0x00000000.toInt(), 0x00000000.toInt(), 0x00000000.toInt()
        )),
        Template(TurnKind.SLIGHT_LEFT, intArrayOf(
            0x00000000.toInt(), 0x00000000.toInt(), 0x00000000.toInt(), 0x00000000.toInt(),
            0x00FFF000.toInt(), 0x00FFF000.toInt(), 0x00FFF000.toInt(), 0x00FC0000.toInt(),
            0x00FE0000.toInt(), 0x00FF0000.toInt(), 0x00EF8000.toInt(), 0x00E7C000.toInt(),
            0x00E3E000.toInt(), 0x00E1F000.toInt(), 0x00E0F800.toInt(), 0x00E07C00.toInt(),
            0x00003E00.toInt(), 0x00001E00.toInt(), 0x00000F00.toInt(), 0x00000700.toInt(),
            0x00000780.toInt(), 0x00000780.toInt(), 0x00000380.toInt(), 0x00000380.toInt(),
            0x00000380.toInt(), 0x00000380.toInt(), 0x00000380.toInt(), 0x00000380.toInt(),
            0x00000000.toInt(), 0x00000000.toInt(), 0x00000000.toInt(), 0x00000000.toInt()
        )),
        Template(TurnKind.SLIGHT_LEFT, intArrayOf(
            0x00000000.toInt(), 0x00000000.toInt(), 0x00000000.toInt(), 0x00000000.toInt(),
            0x03FF8000.toInt(), 0x07FF8000.toInt(), 0x07FF8000.toInt(), 0x07F00000.toInt(),
            0x07F00000.toInt(), 0x07F80000.toInt(), 0x07FC0000.toInt(), 0x07BE0000.toInt(),
            0x078F0000.toInt(), 0x078F8000.toInt(), 0x0787C000.toInt(), 0x0303E000.toInt(),
            0x0001E000.toInt(), 0x0001F000.toInt(), 0x0000F000.toInt(), 0x0000F000.toInt(),
            0x0000F000.toInt(), 0x0000F000.toInt(), 0x0000F000.toInt(), 0x0000F000.toInt(),
            0x0000F000.toInt(), 0x0000F000.toInt(), 0x0000F000.toInt(), 0x0000F000.toInt(),
            0x00000000.toInt(), 0x00000000.toInt(), 0x00000000.toInt(), 0x00000000.toInt()
        )),
        Template(TurnKind.SLIGHT_LEFT, intArrayOf(
            0x00000000.toInt(), 0x00000000.toInt(), 0x00000000.toInt(), 0x00000000.toInt(),
            0x07FF8000.toInt(), 0x07FF8000.toInt(), 0x07FF8000.toInt(), 0x07E00000.toInt(),
            0x07F00000.toInt(), 0x07F80000.toInt(), 0x07FC0000.toInt(), 0x07BE0000.toInt(),
            0x079F0000.toInt(), 0x078F8000.toInt(), 0x0787C000.toInt(), 0x0303C000.toInt(),
            0x0001E000.toInt(), 0x0001E000.toInt(), 0x0000F000.toInt(), 0x0000F000.toInt(),
            0x0000F000.toInt(), 0x0000F000.toInt(), 0x0000F000.toInt(), 0x0000F000.toInt(),
            0x0000F000.toInt(), 0x0000F000.toInt(), 0x0000F000.toInt(), 0x0000F000.toInt(),
            0x00000000.toInt(), 0x00000000.toInt(), 0x00000000.toInt(), 0x00000000.toInt()
        )),
        Template(TurnKind.SLIGHT_RIGHT, intArrayOf(
            0x00000000.toInt(), 0x00000000.toInt(), 0x00000000.toInt(), 0x00000000.toInt(),
            0x000FFF00.toInt(), 0x000FFF00.toInt(), 0x000FFF00.toInt(), 0x00003F00.toInt(),
            0x00007F00.toInt(), 0x0000FF00.toInt(), 0x0001F700.toInt(), 0x0003E700.toInt(),
            0x0007C700.toInt(), 0x000F8700.toInt(), 0x001F0700.toInt(), 0x003E0700.toInt(),
            0x007C0000.toInt(), 0x00780000.toInt(), 0x00F00000.toInt(), 0x00E00000.toInt(),
            0x01E00000.toInt(), 0x01E00000.toInt(), 0x01C00000.toInt(), 0x01C00000.toInt(),
            0x01C00000.toInt(), 0x01C00000.toInt(), 0x01C00000.toInt(), 0x01C00000.toInt(),
            0x00000000.toInt(), 0x00000000.toInt(), 0x00000000.toInt(), 0x00000000.toInt()
        )),
        Template(TurnKind.SLIGHT_RIGHT, intArrayOf(
            0x00000000.toInt(), 0x00000000.toInt(), 0x00000000.toInt(), 0x00000000.toInt(),
            0x00007C00.toInt(), 0x00007E00.toInt(), 0x00003F80.toInt(), 0x00000FE0.toInt(),
            0x000007F0.toInt(), 0x00001FF0.toInt(), 0x0000FFF0.toInt(), 0x0003FFE0.toInt(),
            0x000FFBE0.toInt(), 0x001FC3C0.toInt(), 0x007E0380.toInt(), 0x03F80780.toInt(),
            0x03F00F00.toInt(), 0x03E00700.toInt(), 0x03E00200.toInt(), 0x03C00000.toInt(),
            0x03800000.toInt(), 0x07800000.toInt(), 0x07800000.toInt(), 0x07800000.toInt(),
            0x07800000.toInt(), 0x07800000.toInt(), 0x07800000.toInt(), 0x07800000.toInt(),
            0x00000000.toInt(), 0x00000000.toInt(), 0x00000000.toInt(), 0x00000000.toInt()
        )),
        Template(TurnKind.SLIGHT_RIGHT, intArrayOf(
            0x00000000.toInt(), 0x00000000.toInt(), 0x00000000.toInt(), 0x00000000.toInt(),
            0x0001FFE0.toInt(), 0x0001FFE0.toInt(), 0x0001FFE0.toInt(), 0x000007E0.toInt(),
            0x00000FE0.toInt(), 0x00001FE0.toInt(), 0x00003FE0.toInt(), 0x00007DE0.toInt(),
            0x0000F9E0.toInt(), 0x0001F1E0.toInt(), 0x0003E1E0.toInt(), 0x0003C0C0.toInt(),
            0x00078000.toInt(), 0x00078000.toInt(), 0x000F0000.toInt(), 0x000F0000.toInt(),
            0x000F0000.toInt(), 0x000F0000.toInt(), 0x000F0000.toInt(), 0x000F0000.toInt(),
            0x000F0000.toInt(), 0x000F0000.toInt(), 0x000F0000.toInt(), 0x000F0000.toInt(),
            0x00000000.toInt(), 0x00000000.toInt(), 0x00000000.toInt(), 0x00000000.toInt()
        )),
        Template(TurnKind.SLIGHT_RIGHT, intArrayOf(
            0x00000000.toInt(), 0x00000000.toInt(), 0x00000000.toInt(), 0x00003000.toInt(),
            0x00003800.toInt(), 0x00007E00.toInt(), 0x00003F80.toInt(), 0x00000FE0.toInt(),
            0x000003F0.toInt(), 0x00000FF0.toInt(), 0x0000FFF0.toInt(), 0x0003FFE0.toInt(),
            0x000FFDE0.toInt(), 0x001FC3C0.toInt(), 0x003E03C0.toInt(), 0x00F80780.toInt(),
            0x01F00700.toInt(), 0x01E00F00.toInt(), 0x03E00200.toInt(), 0x03C00000.toInt(),
            0x03C00000.toInt(), 0x07800000.toInt(), 0x07800000.toInt(), 0x07800000.toInt(),
            0x07800000.toInt(), 0x07800000.toInt(), 0x07800000.toInt(), 0x07800000.toInt(),
            0x00000000.toInt(), 0x00000000.toInt(), 0x00000000.toInt(), 0x00000000.toInt()
        )),
        Template(TurnKind.U_TURN, intArrayOf(
            0x00000000.toInt(), 0x00000000.toInt(), 0x00000000.toInt(), 0x00000000.toInt(),
            0x0000FC00.toInt(), 0x0003FF00.toInt(), 0x0007FF80.toInt(), 0x000F8FC0.toInt(),
            0x001E03E0.toInt(), 0x003C01E0.toInt(), 0x003C00F0.toInt(), 0x003800F0.toInt(),
            0x00380070.toInt(), 0x00380070.toInt(), 0x00380070.toInt(), 0x0C38C070.toInt(),
            0x1E39E070.toInt(), 0x0F3BE070.toInt(), 0x0FBFC070.toInt(), 0x07FF8070.toInt(),
            0x03FF0070.toInt(), 0x01FE0070.toInt(), 0x00FC0070.toInt(), 0x00380070.toInt(),
            0x00300070.toInt(), 0x00000070.toInt(), 0x00000070.toInt(), 0x00000070.toInt(),
            0x00000000.toInt(), 0x00000000.toInt(), 0x00000000.toInt(), 0x00000000.toInt()
        )),
        Template(TurnKind.U_TURN, intArrayOf(
            0x00000000.toInt(), 0x00000000.toInt(), 0x00000000.toInt(), 0x00000000.toInt(),
            0x003F0000.toInt(), 0x00FFC000.toInt(), 0x01FFE000.toInt(), 0x03F1F000.toInt(),
            0x07C07800.toInt(), 0x07803C00.toInt(), 0x0F003C00.toInt(), 0x0F001C00.toInt(),
            0x0E001C00.toInt(), 0x0E001C00.toInt(), 0x0E001C00.toInt(), 0x0E031C30.toInt(),
            0x0E079C78.toInt(), 0x0E07DCF0.toInt(), 0x0E03FDF0.toInt(), 0x0E01FFE0.toInt(),
            0x0E00FFC0.toInt(), 0x0E007F80.toInt(), 0x0E003F00.toInt(), 0x0E001C00.toInt(),
            0x0E000C00.toInt(), 0x0E000000.toInt(), 0x0E000000.toInt(), 0x0E000000.toInt(),
            0x00000000.toInt(), 0x00000000.toInt(), 0x00000000.toInt(), 0x00000000.toInt()
        )),
        Template(TurnKind.KEEP_LEFT, intArrayOf(
            0x00000000.toInt(), 0x00000000.toInt(), 0x00000000.toInt(), 0x00008000.toInt(),
            0x0001C000.toInt(), 0x0007E000.toInt(), 0x0007F000.toInt(), 0x000FF800.toInt(),
            0x003FFC00.toInt(), 0x007DFE00.toInt(), 0x0079DF00.toInt(), 0x00F1CF00.toInt(),
            0x0061C600.toInt(), 0x0001C000.toInt(), 0x0001C000.toInt(), 0x0001E000.toInt(),
            0x0001E000.toInt(), 0x0001F000.toInt(), 0x0000F800.toInt(), 0x00007C00.toInt(),
            0x00003E00.toInt(), 0x00001E00.toInt(), 0x00000F00.toInt(), 0x00000700.toInt(),
            0x00000780.toInt(), 0x00000380.toInt(), 0x00000380.toInt(), 0x00000380.toInt(),
            0x00000000.toInt(), 0x00000000.toInt(), 0x00000000.toInt(), 0x00000000.toInt()
        )),
        Template(TurnKind.KEEP_RIGHT, intArrayOf(
            0x00000000.toInt(), 0x00000000.toInt(), 0x00000000.toInt(), 0x00010000.toInt(),
            0x00038000.toInt(), 0x0007E000.toInt(), 0x000FE000.toInt(), 0x001FF000.toInt(),
            0x003FFC00.toInt(), 0x007FBE00.toInt(), 0x00FB9E00.toInt(), 0x00F38F00.toInt(),
            0x00638600.toInt(), 0x00038000.toInt(), 0x00038000.toInt(), 0x00078000.toInt(),
            0x00078000.toInt(), 0x000F8000.toInt(), 0x001F0000.toInt(), 0x003E0000.toInt(),
            0x007C0000.toInt(), 0x00780000.toInt(), 0x00F00000.toInt(), 0x00E00000.toInt(),
            0x01E00000.toInt(), 0x01C00000.toInt(), 0x01C00000.toInt(), 0x01C00000.toInt(),
            0x00000000.toInt(), 0x00000000.toInt(), 0x00000000.toInt(), 0x00000000.toInt()
        )),
        Template(TurnKind.DESTINATION, intArrayOf(
            0x00000000.toInt(), 0x00000000.toInt(), 0x00000000.toInt(), 0x000FF000.toInt(),
            0x003FFC00.toInt(), 0x007C3E00.toInt(), 0x00F00F00.toInt(), 0x01E00780.toInt(),
            0x01C00380.toInt(), 0x038001C0.toInt(), 0x038001C0.toInt(), 0x0303C0C0.toInt(),
            0x0707E0E0.toInt(), 0x0707E0E0.toInt(), 0x0703C0E0.toInt(), 0x0703C0E0.toInt(),
            0x030000C0.toInt(), 0x038001C0.toInt(), 0x038001C0.toInt(), 0x01C00380.toInt(),
            0x00E00700.toInt(), 0x00F00F00.toInt(), 0x00700E00.toInt(), 0x00381C00.toInt(),
            0x001C3800.toInt(), 0x000FF000.toInt(), 0x0007E000.toInt(), 0x0003C000.toInt(),
            0x00018000.toInt(), 0x00000000.toInt(), 0x00000000.toInt(), 0x00000000.toInt()
        )),
        Template(TurnKind.DESTINATION, intArrayOf(
            0x00000000.toInt(), 0x00E00000.toInt(), 0x07F80000.toInt(), 0x0FFE0000.toInt(),
            0x1E0F0000.toInt(), 0x3C070000.toInt(), 0x38038000.toInt(), 0x30438000.toInt(),
            0x70E18000.toInt(), 0x71E18000.toInt(), 0x70E18000.toInt(), 0x30038000.toInt(),
            0x38038000.toInt(), 0x38070000.toInt(), 0x1C070000.toInt(), 0x1C0E31E0.toInt(),
            0x0E0E71E0.toInt(), 0x0F1C7FF0.toInt(), 0x073CFFF0.toInt(), 0x03B9FFF0.toInt(),
            0x03F1F1F0.toInt(), 0x01E3F1F8.toInt(), 0x00E3F1F8.toInt(), 0x0047FFF8.toInt(),
            0x0007FFFC.toInt(), 0x0007F1FC.toInt(), 0x000FF1FC.toInt(), 0x000FF1FE.toInt(),
            0x00000000.toInt(), 0x00000000.toInt(), 0x00000000.toInt(), 0x00000000.toInt()
        )),
        Template(TurnKind.DESTINATION, intArrayOf(
            0x00000000.toInt(), 0x00000700.toInt(), 0x00003FE0.toInt(), 0x00007FF0.toInt(),
            0x0000F078.toInt(), 0x0000E03C.toInt(), 0x0001C01C.toInt(), 0x0001C20C.toInt(),
            0x0001870E.toInt(), 0x0001878E.toInt(), 0x0001870E.toInt(), 0x0001C00C.toInt(),
            0x0001C01C.toInt(), 0x0000E01C.toInt(), 0x0000E038.toInt(), 0x078C7038.toInt(),
            0x078E7070.toInt(), 0x07FE38F0.toInt(), 0x0FFF3CE0.toInt(), 0x0FFF9DC0.toInt(),
            0x0F8F8F80.toInt(), 0x1F8FC780.toInt(), 0x1F8FC700.toInt(), 0x1FFFE200.toInt(),
            0x3FFFE000.toInt(), 0x3F8FE000.toInt(), 0x3F8FF000.toInt(), 0x7F8FF000.toInt(),
            0x00000000.toInt(), 0x00000000.toInt(), 0x00000000.toInt(), 0x00000000.toInt()
        )),
        Template(TurnKind.ROUNDABOUT, intArrayOf(
            0x00000000.toInt(), 0x00000000.toInt(), 0x00000000.toInt(), 0x00000000.toInt(),
            0x01FFC000.toInt(), 0x01FFE000.toInt(), 0x01FFE000.toInt(), 0x01F00000.toInt(),
            0x01F80000.toInt(), 0x01FC0000.toInt(), 0x01FE0000.toInt(), 0x01DF0000.toInt(),
            0x01CF8000.toInt(), 0x01C7C000.toInt(), 0x01C78000.toInt(), 0x01CF0000.toInt(),
            0x000F0000.toInt(), 0x000E0000.toInt(), 0x000E0000.toInt(), 0x000F0000.toInt(),
            0x000F0000.toInt(), 0x00078000.toInt(), 0x0003F800.toInt(), 0x0003F800.toInt(),
            0x0000F800.toInt(), 0x00003800.toInt(), 0x00003800.toInt(), 0x00003800.toInt(),
            0x00000000.toInt(), 0x00000000.toInt(), 0x00000000.toInt(), 0x00000000.toInt()
        )),
        Template(TurnKind.ROUNDABOUT, intArrayOf(
            0x00000000.toInt(), 0x00000000.toInt(), 0x00000000.toInt(), 0x00000000.toInt(),
            0x0003FF80.toInt(), 0x0007FF80.toInt(), 0x0007FF80.toInt(), 0x00000F80.toInt(),
            0x00001F80.toInt(), 0x00003F80.toInt(), 0x00007F80.toInt(), 0x0000FB80.toInt(),
            0x0001F380.toInt(), 0x0003E380.toInt(), 0x0001E380.toInt(), 0x0000F380.toInt(),
            0x0000F000.toInt(), 0x00007000.toInt(), 0x00007000.toInt(), 0x0000F000.toInt(),
            0x0000F000.toInt(), 0x0001E000.toInt(), 0x001FC000.toInt(), 0x001FC000.toInt(),
            0x001F0000.toInt(), 0x001C0000.toInt(), 0x001C0000.toInt(), 0x001C0000.toInt(),
            0x00000000.toInt(), 0x00000000.toInt(), 0x00000000.toInt(), 0x00000000.toInt()
        )),
        Template(TurnKind.ROUNDABOUT, intArrayOf(
            0x00000000.toInt(), 0x00018000.toInt(), 0x0003C000.toInt(), 0x0007E000.toInt(),
            0x000FF000.toInt(), 0x001FF800.toInt(), 0x003FFC00.toInt(), 0x007BDE00.toInt(),
            0x00F3CF00.toInt(), 0x00E3C700.toInt(), 0x0003C000.toInt(), 0x000FC000.toInt(),
            0x001FC000.toInt(), 0x003FC000.toInt(), 0x007C0000.toInt(), 0x00780000.toInt(),
            0x00700000.toInt(), 0x00700000.toInt(), 0x00700000.toInt(), 0x00700000.toInt(),
            0x00780000.toInt(), 0x007C0000.toInt(), 0x003FC000.toInt(), 0x001FC000.toInt(),
            0x000FC000.toInt(), 0x0003C000.toInt(), 0x0003C000.toInt(), 0x0003C000.toInt(),
            0x00000000.toInt(), 0x00000000.toInt(), 0x00000000.toInt(), 0x00000000.toInt()
        )),
        Template(TurnKind.ROUNDABOUT, intArrayOf(
            0x00000000.toInt(), 0x00018000.toInt(), 0x0003C000.toInt(), 0x0007E000.toInt(),
            0x000FF000.toInt(), 0x001FF800.toInt(), 0x003FFC00.toInt(), 0x007BDE00.toInt(),
            0x00F3CF00.toInt(), 0x00E3C700.toInt(), 0x0003C000.toInt(), 0x0003F000.toInt(),
            0x0003F800.toInt(), 0x0003FC00.toInt(), 0x00003E00.toInt(), 0x00001E00.toInt(),
            0x00000E00.toInt(), 0x00000E00.toInt(), 0x00000E00.toInt(), 0x00000E00.toInt(),
            0x00001E00.toInt(), 0x00003E00.toInt(), 0x0003FC00.toInt(), 0x0003F800.toInt(),
            0x0003F000.toInt(), 0x0003C000.toInt(), 0x0003C000.toInt(), 0x0003C000.toInt(),
            0x00000000.toInt(), 0x00000000.toInt(), 0x00000000.toInt(), 0x00000000.toInt()
        )),
        Template(TurnKind.ROUNDABOUT, intArrayOf(
            0x00000000.toInt(), 0x00000000.toInt(), 0x00000000.toInt(), 0x00000000.toInt(),
            0x00000000.toInt(), 0x00000000.toInt(), 0x00600000.toInt(), 0x00F00000.toInt(),
            0x01F00000.toInt(), 0x03E00000.toInt(), 0x07C00000.toInt(), 0x0F800000.toInt(),
            0x1FFFE000.toInt(), 0x3FFFE000.toInt(), 0x3FFFF000.toInt(), 0x1FFFF000.toInt(),
            0x0F80F000.toInt(), 0x07C07C00.toInt(), 0x03E03F80.toInt(), 0x01F03FC0.toInt(),
            0x00F00F80.toInt(), 0x00600380.toInt(), 0x00000380.toInt(), 0x00000380.toInt(),
            0x00000380.toInt(), 0x00000380.toInt(), 0x00000380.toInt(), 0x00000380.toInt(),
            0x00000000.toInt(), 0x00000000.toInt(), 0x00000000.toInt(), 0x00000000.toInt()
        )),
        Template(TurnKind.ROUNDABOUT, intArrayOf(
            0x00000000.toInt(), 0x00000000.toInt(), 0x00000000.toInt(), 0x00000000.toInt(),
            0x00000000.toInt(), 0x00000000.toInt(), 0x00000600.toInt(), 0x00000F00.toInt(),
            0x00000F80.toInt(), 0x000007C0.toInt(), 0x000003E0.toInt(), 0x000001F0.toInt(),
            0x0007FFF8.toInt(), 0x0007FFFC.toInt(), 0x000FFFFC.toInt(), 0x000FFFF8.toInt(),
            0x000F01F0.toInt(), 0x003E03E0.toInt(), 0x01FC07C0.toInt(), 0x03FC0F80.toInt(),
            0x01F00F00.toInt(), 0x01C00600.toInt(), 0x01C00000.toInt(), 0x01C00000.toInt(),
            0x01C00000.toInt(), 0x01C00000.toInt(), 0x01C00000.toInt(), 0x01C00000.toInt(),
            0x00000000.toInt(), 0x00000000.toInt(), 0x00000000.toInt(), 0x00000000.toInt()
        )),
        Template(TurnKind.ROUNDABOUT, intArrayOf(
            0x00000000.toInt(), 0x00000000.toInt(), 0x00000000.toInt(), 0x00000000.toInt(),
            0x07FF8000.toInt(), 0x07FF8000.toInt(), 0x07FF8000.toInt(), 0x07F00000.toInt(),
            0x07F00000.toInt(), 0x07F80000.toInt(), 0x07FC0000.toInt(), 0x07BEFE00.toInt(),
            0x079FFF00.toInt(), 0x078FFF80.toInt(), 0x0787C7C0.toInt(), 0x030303C0.toInt(),
            0x000001E0.toInt(), 0x000001E0.toInt(), 0x000001E0.toInt(), 0x000001E0.toInt(),
            0x000003C0.toInt(), 0x000007C0.toInt(), 0x00007F80.toInt(), 0x00007F00.toInt(),
            0x00007E00.toInt(), 0x00003800.toInt(), 0x00003800.toInt(), 0x00003800.toInt(),
            0x00000000.toInt(), 0x00000000.toInt(), 0x00000000.toInt(), 0x00000000.toInt()
        )),
        Template(TurnKind.ROUNDABOUT, intArrayOf(
            0x00000000.toInt(), 0x00000000.toInt(), 0x00000000.toInt(), 0x00000000.toInt(),
            0x0001FFE0.toInt(), 0x0001FFE0.toInt(), 0x0001FFE0.toInt(), 0x00000FE0.toInt(),
            0x00000FE0.toInt(), 0x00001FE0.toInt(), 0x00003FE0.toInt(), 0x007F7DE0.toInt(),
            0x00FFF9E0.toInt(), 0x01FFF1E0.toInt(), 0x03E3E1E0.toInt(), 0x03C0C0C0.toInt(),
            0x07800000.toInt(), 0x07800000.toInt(), 0x07800000.toInt(), 0x07800000.toInt(),
            0x03C00000.toInt(), 0x03E00000.toInt(), 0x01FE0000.toInt(), 0x00FE0000.toInt(),
            0x007E0000.toInt(), 0x001C0000.toInt(), 0x001C0000.toInt(), 0x001C0000.toInt(),
            0x00000000.toInt(), 0x00000000.toInt(), 0x00000000.toInt(), 0x00000000.toInt()
        )),
        Template(TurnKind.ROUNDABOUT, intArrayOf(
            0x00000000.toInt(), 0x00000000.toInt(), 0x00000000.toInt(), 0x00000000.toInt(),
            0x00000000.toInt(), 0x00000000.toInt(), 0x00600000.toInt(), 0x00F00FE0.toInt(),
            0x01F03FF0.toInt(), 0x03E07FF8.toInt(), 0x07C07C7C.toInt(), 0x0F80F03C.toInt(),
            0x1FFFF01E.toInt(), 0x3FFFE01E.toInt(), 0x3FFFE01E.toInt(), 0x1FFFE01E.toInt(),
            0x0F80003C.toInt(), 0x07C0007C.toInt(), 0x03E007F8.toInt(), 0x01F007F0.toInt(),
            0x00F007E0.toInt(), 0x00600380.toInt(), 0x00000380.toInt(), 0x00000380.toInt(),
            0x00000380.toInt(), 0x00000380.toInt(), 0x00000380.toInt(), 0x00000380.toInt(),
            0x00000000.toInt(), 0x00000000.toInt(), 0x00000000.toInt(), 0x00000000.toInt()
        )),
        Template(TurnKind.ROUNDABOUT, intArrayOf(
            0x00000000.toInt(), 0x00000000.toInt(), 0x00000000.toInt(), 0x00000000.toInt(),
            0x00000000.toInt(), 0x00000000.toInt(), 0x00000600.toInt(), 0x07F00F00.toInt(),
            0x0FFC0F80.toInt(), 0x1FFE07C0.toInt(), 0x3E3E03E0.toInt(), 0x3C0F01F0.toInt(),
            0x780FFFF8.toInt(), 0x7807FFFC.toInt(), 0x7807FFFC.toInt(), 0x7807FFF8.toInt(),
            0x3C0001F0.toInt(), 0x3E0003E0.toInt(), 0x1FE007C0.toInt(), 0x0FE00F80.toInt(),
            0x07E00F00.toInt(), 0x01C00600.toInt(), 0x01C00000.toInt(), 0x01C00000.toInt(),
            0x01C00000.toInt(), 0x01C00000.toInt(), 0x01C00000.toInt(), 0x01C00000.toInt(),
            0x00000000.toInt(), 0x00000000.toInt(), 0x00000000.toInt(), 0x00000000.toInt()
        )),
        Template(TurnKind.ROUNDABOUT, intArrayOf(
            0x00000000.toInt(), 0x00000000.toInt(), 0x00000000.toInt(), 0x0007F000.toInt(),
            0x000FFC00.toInt(), 0x001FFC00.toInt(), 0x003E1E00.toInt(), 0x003C0F00.toInt(),
            0x00780F00.toInt(), 0x00780700.toInt(), 0x00780700.toInt(), 0x00780F00.toInt(),
            0x003C0F00.toInt(), 0x003E1E00.toInt(), 0x001F3C00.toInt(), 0x000F3C00.toInt(),
            0x000F2000.toInt(), 0x000F0000.toInt(), 0x018F1800.toInt(), 0x03CF3C00.toInt(),
            0x01EF7C00.toInt(), 0x00FFF800.toInt(), 0x007FF000.toInt(), 0x003FE800.toInt(),
            0x001FD800.toInt(), 0x000FB800.toInt(), 0x00073800.toInt(), 0x00023800.toInt(),
            0x00000000.toInt(), 0x00000000.toInt(), 0x00000000.toInt(), 0x00000000.toInt()
        )),
        Template(TurnKind.ROUNDABOUT, intArrayOf(
            0x00000000.toInt(), 0x00000000.toInt(), 0x00000000.toInt(), 0x000FE000.toInt(),
            0x003FF000.toInt(), 0x003FF800.toInt(), 0x00787C00.toInt(), 0x00F03C00.toInt(),
            0x00F01E00.toInt(), 0x00E01E00.toInt(), 0x00E01E00.toInt(), 0x00F01E00.toInt(),
            0x00F03C00.toInt(), 0x00787C00.toInt(), 0x003CF800.toInt(), 0x003CF000.toInt(),
            0x0004F000.toInt(), 0x0000F000.toInt(), 0x0018F180.toInt(), 0x003CF3C0.toInt(),
            0x003EF780.toInt(), 0x001FFF00.toInt(), 0x000FFE00.toInt(), 0x0017FC00.toInt(),
            0x001BF800.toInt(), 0x001DF000.toInt(), 0x001CE000.toInt(), 0x001C4000.toInt(),
            0x00000000.toInt(), 0x00000000.toInt(), 0x00000000.toInt(), 0x00000000.toInt()
        )),
        Template(TurnKind.MERGE_RIGHT, intArrayOf(
            0x00000000.toInt(), 0x00000000.toInt(), 0x00000000.toInt(), 0x00000000.toInt(),
            0x00018000.toInt(), 0x0003C000.toInt(), 0x0007E000.toInt(), 0x000FF000.toInt(),
            0x001FF800.toInt(), 0x003FFC00.toInt(), 0x007FFE00.toInt(), 0x007FFE00.toInt(),
            0x00F3CF00.toInt(), 0x0063C600.toInt(), 0x0003C000.toInt(), 0x0003C000.toInt(),
            0x0007E000.toInt(), 0x0007E000.toInt(), 0x000FF000.toInt(), 0x001E7800.toInt(),
            0x003E7C00.toInt(), 0x007C3C00.toInt(), 0x00781E00.toInt(), 0x00700E00.toInt(),
            0x00F00F00.toInt(), 0x00F00F00.toInt(), 0x00E00700.toInt(), 0x00E00700.toInt(),
            0x00000000.toInt(), 0x00000000.toInt(), 0x00000000.toInt(), 0x00000000.toInt()
        )),
        Template(TurnKind.MERGE_RIGHT, intArrayOf(
            0x00000000.toInt(), 0x00000000.toInt(), 0x00000000.toInt(), 0x00000000.toInt(),
            0x00018000.toInt(), 0x0003C000.toInt(), 0x0007E000.toInt(), 0x000FF000.toInt(),
            0x001FF800.toInt(), 0x003FFC00.toInt(), 0x007FFE00.toInt(), 0x00FBDF00.toInt(),
            0x00F3CF00.toInt(), 0x0063C600.toInt(), 0x0003C000.toInt(), 0x0003C000.toInt(),
            0x0007E000.toInt(), 0x0007E000.toInt(), 0x000FF000.toInt(), 0x001E7800.toInt(),
            0x003C3C00.toInt(), 0x003C3C00.toInt(), 0x00781E00.toInt(), 0x00700E00.toInt(),
            0x00F00F00.toInt(), 0x00E00700.toInt(), 0x00E00700.toInt(), 0x00E00700.toInt(),
            0x00000000.toInt(), 0x00000000.toInt(), 0x00000000.toInt(), 0x00000000.toInt()
        )),
    )

    /** Packs 32x32 RGBA pixels into 32 ints (1 bit per pixel alpha/luminance). */
    fun packPixels(pixelsRGBA: IntArray): IntArray {
        val pack = IntArray(32)
        // If transparent pixels exist, use alpha threshold (transparent canvas mode)
        val hasTransparency = pixelsRGBA.any { (it ushr 24) < 128 }
        if (hasTransparency) {
            for (y in 0 until 32) {
                var row = 0
                for (x in 0 until 32) {
                    val pixel = if ((pixelsRGBA[y * 32 + x] and 0x80000000.toInt()) != 0) 1 else 0
                    row = (row shl 1) or pixel
                }
                pack[y] = row
            }
            return pack
        }

        // Opaque background badge: threshold bright foreground arrow pixels against background
        var totalLum = 0L
        for (p in pixelsRGBA) {
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF
            totalLum += (r * 299 + g * 587 + b * 114) / 1000
        }
        val avgLum = (totalLum / (32 * 32)).toInt()
        val threshold = (avgLum + 35).coerceAtMost(220)

        for (y in 0 until 32) {
            var row = 0
            for (x in 0 until 32) {
                val p = pixelsRGBA[y * 32 + x]
                val r = (p shr 16) and 0xFF
                val g = (p shr 8) and 0xFF
                val b = p and 0xFF
                val lum = (r * 299 + g * 587 + b * 114) / 1000
                row = (row shl 1) or (if (lum >= threshold) 1 else 0)
            }
            pack[y] = row
        }
        return pack
    }

    /**
     * Pure matcher: computes Hamming distance against known templates.
     * Matches if bit difference across the 32x32 bitmap is < 32 bits.
     */
    fun matchPack(pack: IntArray, maxDiff: Int = 32): TurnKind? {
        var bestDiff = maxDiff
        var bestKind: TurnKind? = null
        for (t in TEMPLATES) {
            var diff = 0
            for (j in 0 until 32) {
                diff += java.lang.Integer.bitCount(t.bits[j] xor pack[j])
                if (diff >= bestDiff) break
            }
            if (diff < bestDiff) {
                bestDiff = diff
                bestKind = t.kind
            }
        }
        return bestKind
    }

    /** Android boundary: draws Icon onto 32x32 canvas, extracts pixels, and matches. */
    fun matchIcon(icon: Icon?, context: Context): TurnKind? {
        if (icon == null) return null
        return try {
            val drawable = icon.loadDrawable(context) ?: return null
            val bitmap = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            drawable.setBounds(0, 0, 32, 32)
            drawable.draw(canvas)
            val pixels = IntArray(32 * 32)
            bitmap.getPixels(pixels, 0, 32, 0, 0, 32, 32)
            matchPack(packPixels(pixels))
        } catch (_: Exception) {
            null
        }
    }
}
