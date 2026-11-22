package site.casiopea.cruizer.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import android.os.Build
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

/**
 * Material 3 Expressive with Material You dynamic color: the palette follows the
 * user's wallpaper, and the expressive motion scheme animates all components.
 * No custom typography — the default expressive type scale is the design.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun CruiserTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val context = LocalContext.current
    // Material You dynamic color needs Android 12; Android 11 gets the baseline palette.
    val scheme = when {
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S -> if (dark) darkColorScheme() else lightColorScheme()
        dark -> dynamicDarkColorScheme(context)
        else -> dynamicLightColorScheme(context)
    }
    MaterialExpressiveTheme(colorScheme = scheme) {
        // Root surface: paints the scheme's background behind everything (the
        // activity window background is none of the scheme's business).
        androidx.compose.material3.Surface(Modifier.fillMaxSize()) { content() }
    }
}
