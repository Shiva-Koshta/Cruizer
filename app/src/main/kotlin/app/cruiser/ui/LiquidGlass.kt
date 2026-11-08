package app.cruiser.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.cruiser.Session

/**
 * Material You + Liquid Glass hybrid styling modifier.
 * Creates an authentic vertical gradient of transparency:
 * Top: 78% opacity rich Material You dynamic tone -> Slopes down to 25% translucent liquid glass.
 * Specular hairline rim along the perimeter.
 * Caches all gradient shaders with `remember` for locked 120fps scrolling.
 */
@Composable
fun Modifier.liquidGlass(
    enabled: Boolean,
    shape: Shape = RoundedCornerShape(24.dp),
    borderWidth: Dp = 1.dp,
): Modifier {
    if (!enabled) return this

    val dark = isSystemInDarkTheme()
    val topColor = MaterialTheme.colorScheme.surfaceContainerHighest
    val bottomColor = MaterialTheme.colorScheme.surfaceContainerLow

    val glassBackground = remember(dark, topColor, bottomColor) {
        Brush.verticalGradient(
            0.0f to topColor.copy(alpha = if (dark) 0.78f else 0.85f),
            0.40f to topColor.copy(alpha = if (dark) 0.55f else 0.65f),
            1.0f to bottomColor.copy(alpha = if (dark) 0.25f else 0.35f),
        )
    }

    val glassBorder = remember(dark) {
        Brush.linearGradient(
            0.0f to Color.White.copy(alpha = if (dark) 0.45f else 0.60f),
            0.35f to Color.White.copy(alpha = 0.12f),
            0.85f to Color.White.copy(alpha = 0.03f),
            1.0f to Color.Transparent,
        )
    }

    return this
        .clip(shape)
        .background(glassBackground)
        .border(width = borderWidth, brush = glassBorder, shape = shape)
        .drawWithContent {
            drawContent()
            // Subtle specular lens sheen across top 25% of the glass
            drawRect(
                brush = Brush.verticalGradient(
                    0.0f to Color.White.copy(alpha = if (dark) 0.08f else 0.14f),
                    0.25f to Color.Transparent,
                    startY = 0f,
                    endY = size.height * 0.35f,
                ),
            )
        }
}

/**
 * Universal Material You + Liquid Glass Card used across Home and Settings.
 * Slopes from rich dynamic Material You colors at the top into translucent liquid glass at the bottom.
 * Caches all gradient shaders with `remember` for locked 120fps scrolling.
 */
@Composable
fun CruiserGlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(24.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val s by Session.settings.collectAsState()
    val isGlass = s.liquidGlass
    val dark = isSystemInDarkTheme()
    val topColor = MaterialTheme.colorScheme.surfaceContainerHigh
    val bottomColor = MaterialTheme.colorScheme.surfaceContainerLow

    val glassBackground = remember(isGlass, dark, topColor, bottomColor) {
        if (!isGlass) null
        else {
            Brush.verticalGradient(
                0.0f to topColor.copy(alpha = if (dark) 0.78f else 0.85f),
                0.40f to topColor.copy(alpha = if (dark) 0.55f else 0.65f),
                1.0f to bottomColor.copy(alpha = if (dark) 0.25f else 0.35f),
            )
        }
    }

    val glassBorder = remember(isGlass, dark) {
        if (!isGlass) null
        else {
            Brush.linearGradient(
                0.0f to Color.White.copy(alpha = if (dark) 0.45f else 0.60f),
                0.35f to Color.White.copy(alpha = 0.12f),
                0.85f to Color.White.copy(alpha = 0.03f),
                1.0f to Color.Transparent,
            )
        }
    }

    val cardModifier = if (isGlass && glassBackground != null && glassBorder != null) {
        modifier
            .clip(shape)
            .background(glassBackground)
            .border(width = 1.dp, brush = glassBorder, shape = shape)
            .drawWithContent {
                drawContent()
                drawRect(
                    brush = Brush.verticalGradient(
                        0.0f to Color.White.copy(alpha = if (dark) 0.08f else 0.14f),
                        0.25f to Color.Transparent,
                        startY = 0f,
                        endY = size.height * 0.35f,
                    ),
                )
            }
    } else {
        modifier
    }

    Card(
        modifier = cardModifier,
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
