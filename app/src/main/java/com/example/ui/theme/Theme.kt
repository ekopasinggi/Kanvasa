package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val StudioLightColorScheme = lightColorScheme(
    primary = DeepInkPrimary,
    onPrimary = DeepInkOnPrimary,
    primaryContainer = DeepInkContainer,
    onPrimaryContainer = OnDeepInkContainer,
    secondary = CobaltAccent,
    onSecondary = OnCobaltAccent,
    secondaryContainer = CobaltContainer,
    onSecondaryContainer = OnCobaltContainer,
    tertiary = TerracottaAccent,
    onTertiary = OnTerracottaAccent,
    tertiaryContainer = TerracottaContainer,
    onTertiaryContainer = OnTerracottaContainer,
    background = StudioPaperBg,
    onBackground = TextPrimaryInk,
    surface = StudioSurface,
    onSurface = TextPrimaryInk,
    surfaceVariant = StudioSurfaceContainer,
    onSurfaceVariant = TextSecondarySlate,
    surfaceContainerLowest = StudioSurface,
    surfaceContainerLow = StudioSurfaceContainerLow,
    surfaceContainer = StudioSurfaceContainer,
    surfaceContainerHigh = StudioSurfaceContainerHigh,
    outline = StudioOutline,
    outlineVariant = StudioOutlineSubtle,
    error = DangerCrimson
)

@Composable
fun KanvasaTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = StudioLightColorScheme,
        typography = Typography,
        content = content
    )
}

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = false,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    KanvasaTheme(content = content)
}
