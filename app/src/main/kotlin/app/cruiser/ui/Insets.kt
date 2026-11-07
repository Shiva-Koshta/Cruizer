package app.cruiser.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.runtime.Composable

/** Shared top-level inset padding for tab screens. */
@Composable
fun safeDrawingInsets(): WindowInsets = WindowInsets.safeDrawing
