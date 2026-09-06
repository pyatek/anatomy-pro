package com.ptk.anatomypro.core.designsystem

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val DarkColors = darkColorScheme(
    primary = Accent,
    onPrimary = Ground,
    background = Ground,
    onBackground = TextPrimary,
    surface = Surface,
    onSurface = TextPrimary,
    onSurfaceVariant = TextSecondary,
    outline = Hairline,
    error = IncorrectAmber,
)

private val LightColors = lightColorScheme(
    primary = AccentOnLight,
    onPrimary = AccentOnLightSurface,
    background = LightGround,
    onBackground = LightTextPrimary,
    surface = LightGround,
    onSurface = LightTextPrimary,
    onSurfaceVariant = LightTextTertiary,
    error = AccentOnLight,
)

/**
 * Dark is the default: the atlas and quiz — the screens the app is actually for — are dark,
 * and only the two reading-heavy screens are light.
 */
@Composable
fun AnatomyTheme(darkTheme: Boolean = true, content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (darkTheme) DarkColors else LightColors, content = content)
}
