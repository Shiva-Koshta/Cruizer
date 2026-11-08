package app.cruiser.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.cruiser.Session.LiquidGlassMode

/**
 * Material You + Liquid Glass hybrid styling modifier.
 * Fuses Material You dynamic tonal colors with optical glass refraction,
 * specular edge glint, and surface caustic glare.
 */
@Composable
fun Modifier.liquidGlass(
    mode: LiquidGlassMode,
    shape: Shape = RoundedCornerShape(24.dp),
    tonalTint: Color? = null,
    borderWidth: Dp = 1.2.dp,
): Modifier {
    if (mode == LiquidGlassMode.OFF) return this

    val dark = isSystemInDarkTheme()
    val baseTint = tonalTint ?: if (dark) {
        MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.58f)
    } else {
        MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.72f)
    }

    val specularBorder = Brush.linearGradient(
        0.0f to Color.White.copy(alpha = if (dark) 0.55f else 0.70f),
        0.35f to Color.White.copy(alpha = 0.15f),
        0.80f to Color.White.copy(alpha = 0.04f),
        1.0f to Color.Transparent,
        start = Offset(0f, 0f),
        end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY),
    )

    var modifier = this.clip(shape)

    if (mode == LiquidGlassMode.FULL_BLUR) {
        modifier = modifier.blur(radius = 16.dp)
    }

    return modifier
        .background(baseTint)
        .border(width = borderWidth, brush = specularBorder, shape = shape)
        .drawWithContent {
            drawContent()
            // Specular lens flare / glare sheen across upper corner
            drawRect(
                brush = Brush.linearGradient(
                    0.0f to Color.White.copy(alpha = if (dark) 0.14f else 0.22f),
                    0.25f to Color.White.copy(alpha = 0.03f),
                    0.55f to Color.Transparent,
                    start = Offset(0f, 0f),
                    end = Offset(size.width * 0.75f, size.height * 0.75f),
                ),
            )
        }
}
