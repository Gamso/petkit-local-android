package org.petkitlocal.core.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf

private val LocalPkColors = staticCompositionLocalOf { LightPkColors }
private val LocalPkTypography = staticCompositionLocalOf { DefaultPkTypography }
private val LocalPkShapes = staticCompositionLocalOf { PkShapes() }
private val LocalPkSpacing = staticCompositionLocalOf { PkSpacing() }

/**
 * The app's theme. Components read the tokens from [PkTheme.colors] and its siblings; Material 3
 * components underneath get a colour scheme and type scale derived from the same tokens, so a
 * stray Material default still looks like the app.
 */
@Composable
fun PkTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val colors = if (darkTheme) DarkPkColors else LightPkColors
    val typography = DefaultPkTypography
    CompositionLocalProvider(
        LocalPkColors provides colors,
        LocalPkTypography provides typography,
        LocalPkShapes provides PkShapes(),
        LocalPkSpacing provides PkSpacing(),
    ) {
        MaterialTheme(
            colorScheme = colors.toMaterial(),
            typography = typography.toMaterial(),
            content = content,
        )
    }
}

object PkTheme {
    val colors: PkColors
        @Composable @ReadOnlyComposable
        get() = LocalPkColors.current

    val typography: PkTypography
        @Composable @ReadOnlyComposable
        get() = LocalPkTypography.current

    val shapes: PkShapes
        @Composable @ReadOnlyComposable
        get() = LocalPkShapes.current

    val spacing: PkSpacing
        @Composable @ReadOnlyComposable
        get() = LocalPkSpacing.current
}

private fun PkColors.toMaterial(): ColorScheme {
    val base = if (isDark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = accent,
        onPrimary = onAccent,
        primaryContainer = accentContainer,
        onPrimaryContainer = onAccentContainer,
        secondary = secondary,
        onSecondary = onSecondary,
        background = background,
        onBackground = onSurface,
        surface = surface,
        onSurface = onSurface,
        surfaceVariant = surfaceMuted,
        onSurfaceVariant = onSurfaceMuted,
        surfaceContainer = surface,
        surfaceContainerHigh = surface,
        surfaceContainerLow = surfaceMuted,
        error = danger,
        onError = onDanger,
        outline = outline,
        outlineVariant = outline,
    )
}

private fun PkTypography.toMaterial() = Typography(
    displaySmall = display,
    headlineLarge = headlineLarge,
    headlineSmall = headlineSmall,
    titleLarge = titleLarge,
    titleMedium = titleMedium,
    bodyLarge = bodyLarge,
    bodyMedium = bodyMedium,
    labelLarge = labelLarge,
    labelMedium = labelMedium,
    labelSmall = labelSmall,
)
