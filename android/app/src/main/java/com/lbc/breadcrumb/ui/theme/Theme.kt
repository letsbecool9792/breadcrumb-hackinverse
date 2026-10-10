package com.lbc.breadcrumb.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = AmberBright,
    onPrimary = AmberOnContainer,
    primaryContainer = AmberContainerDark,
    onPrimaryContainer = AmberContainerLight,
    secondary = BoneDim,
    onSecondary = Ink,
    background = Ink,
    onBackground = Bone,
    surface = Ink,
    onSurface = Bone,
    surfaceVariant = InkElevated,
    onSurfaceVariant = BoneDim,
    outline = InkOutline,
    outlineVariant = InkBorder,
)

private val LightColorScheme = lightColorScheme(
    primary = AmberDeep,
    onPrimary = Parchment,
    primaryContainer = AmberContainerLight,
    onPrimaryContainer = AmberOnContainer,
    secondary = UmberDim,
    onSecondary = Parchment,
    background = Parchment,
    onBackground = Umber,
    surface = Parchment,
    onSurface = Umber,
    surfaceVariant = ParchmentVariant,
    onSurfaceVariant = UmberDim,
    outline = InkOutline,
    outlineVariant = ParchmentBorder,
)

/**
 * Dynamic colour is deliberately off. Breadcrumb has a strong mark and a dark
 * ink splash; letting the launcher wallpaper repaint the app would both break
 * the splash handoff and dissolve the brand.
 */
@Composable
fun BreadcrumbTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        typography = Typography,
        content = content,
    )
}
