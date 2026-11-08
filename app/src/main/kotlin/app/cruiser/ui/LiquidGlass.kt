package app.cruiser.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.cruiser.Session
import app.cruiser.Session.LiquidGlassMode

/**
 * Ambient dynamic lighting mesh placed behind screens.
 * Provides the contrast and color depth that makes glass transparency immediately obvious.
 */
@Composable
fun AmbientLightMesh(modifier: Modifier = Modifier) {
    val dark = isSystemInDarkTheme()
    val s by Session.settings.collectAsState()
    if (s.liquidGlassMode == LiquidGlassMode.OFF) return

    Box(
        modifier = modifier
            .fillMaxSize()
            .drawWithContent {
                // Top-right warm motorcycle amber aura
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0xFFFFB74D).copy(alpha = if (dark) 0.18f else 0.25f),
                            Color(0xFFFF9800).copy(alpha = if (dark) 0.08f else 0.12f),
                            Color.Transparent,
                        ),
                        center = Offset(size.width * 0.85f, size.height * 0.18f),
                        radius = size.width * 0.75f,
                    ),
                )
                // Bottom-left cool slate/cyan refraction glow
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF29B6F6).copy(alpha = if (dark) 0.14f else 0.18f),
                            Color(0xFF0288D1).copy(alpha = if (dark) 0.05f else 0.08f),
                            Color.Transparent,
                        ),
                        center = Offset(size.width * 0.15f, size.height * 0.82f),
                        radius = size.width * 0.80f,
                    ),
                )
            },
    )
}

/**
 * Apple-style Liquid Glass modifier with chromatic edge refraction and lens glare.
 * Never blurs foreground content: text, digits, and icons remain 100% crisp.
 */
@Composable
fun Modifier.liquidGlass(
    mode: LiquidGlassMode,
    shape: Shape = RoundedCornerShape(24.dp),
    tonalTint: Color? = null,
    borderWidth: Dp = 1.dp,
): Modifier {
    if (mode == LiquidGlassMode.OFF) return this

    val dark = isSystemInDarkTheme()
    // Smoked crystal body: 38% - 48% opacity lets ambient light shine directly through
    val baseTint = tonalTint ?: if (dark) {
        Color(0xFF141824).copy(alpha = 0.42f)
    } else {
        Color.White.copy(alpha = 0.58f)
    }

    // Chromatic / Spectral Bevel: warm amber light on top-left, cool cyan on bottom-right
    val chromaticBorder = Brush.linearGradient(
        0.0f to Color(0xFFFFD54F).copy(alpha = if (dark) 0.55f else 0.70f), // Top-left amber glint
        0.25f to Color.White.copy(alpha = if (dark) 0.40f else 0.60f),      // Specular highlight
        0.65f to Color.White.copy(alpha = 0.10f),
        0.85f to Color(0xFF81D4FA).copy(alpha = if (dark) 0.25f else 0.35f), // Bottom-right cyan refraction
        1.0f to Color.Transparent,
        start = Offset(0f, 0f),
        end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY),
    )

    return this
        .clip(shape)
        .background(baseTint)
        .border(width = borderWidth, brush = chromaticBorder, shape = shape)
        .drawWithContent {
            drawContent()
            // Parabolic lens glare across upper 35% of the glass
            drawRect(
                brush = Brush.verticalGradient(
                    0.0f to Color.White.copy(alpha = if (dark) 0.10f else 0.18f),
                    0.35f to Color.White.copy(alpha = 0.02f),
                    0.60f to Color.Transparent,
                    startY = 0f,
                    endY = size.height * 0.45f,
                ),
            )
        }
}

/**
 * Universal Glass Card used across Home and Settings.
 * Seamlessly toggles between Material 3 tonal elevation and optical liquid glass.
 */
@Composable
fun CruiserGlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(24.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val s by Session.settings.collectAsState()
    val isGlass = s.liquidGlassMode != LiquidGlassMode.OFF

    Card(
        modifier = modifier.liquidGlass(mode = s.liquidGlassMode, shape = shape),
        shape = shape,
        colors = CardDefaults.cardColors(
            containerColor = if (isGlass) Color.Transparent else MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            content = content,
        )
    }
}
