package app.pantopus.android.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColors = lightColorScheme(
    primary = PantopusPrimary,
    onPrimary = PantopusOnPrimary,
    primaryContainer = PantopusPrimaryContainer,
    onPrimaryContainer = PantopusOnPrimaryContainer,
    secondary = PantopusSecondary,
    onSecondary = PantopusOnSecondary,
    secondaryContainer = PantopusSecondaryContainer,
    onSecondaryContainer = PantopusOnSecondaryContainer,
    background = PantopusBackground,
    onBackground = PantopusOnBackground,
    surface = PantopusSurface,
    onSurface = PantopusOnSurface,
    error = PantopusError,
    onError = PantopusOnError
)

private val DarkColors = darkColorScheme(
    primary = PantopusPrimaryDark,
    onPrimary = PantopusOnPrimaryDark,
    background = PantopusBackgroundDark,
    onBackground = PantopusOnBackgroundDark
)

@Composable
fun PantopusTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(colorScheme = colorScheme, typography = PantopusTypography, content = content)
}
