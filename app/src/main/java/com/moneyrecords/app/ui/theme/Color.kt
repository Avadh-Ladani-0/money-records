package com.moneyrecords.app.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// Monochrome base with a single red accent.
private val Ink = Color(0xFF0A0A0A)
private val Paper = Color(0xFFF4F4F2)
private val White = Color(0xFFFFFFFF)
private val Black = Color(0xFF000000)
private val Red = Color(0xFFE5322D)
private val RedLight = Color(0xFFD9302A)

internal val DarkScheme = darkColorScheme(
    primary = Color(0xFFF5F5F5),
    onPrimary = Black,
    primaryContainer = Color(0xFF1E1E1E),
    onPrimaryContainer = Color(0xFFF5F5F5),
    secondary = Color(0xFFBDBDBD),
    onSecondary = Black,
    secondaryContainer = Color(0xFF1E1E1E),
    onSecondaryContainer = Color(0xFFF5F5F5),
    tertiary = Red,
    onTertiary = White,
    background = Black,
    onBackground = Color(0xFFF5F5F5),
    surface = Black,
    onSurface = Color(0xFFF5F5F5),
    surfaceVariant = Color(0xFF161616),
    onSurfaceVariant = Color(0xFF8C8C8C),
    surfaceContainerLowest = Black,
    surfaceContainerLow = Color(0xFF0B0B0B),
    surfaceContainer = Color(0xFF111111),
    surfaceContainerHigh = Color(0xFF191919),
    surfaceContainerHighest = Color(0xFF222222),
    inverseSurface = Color(0xFFF5F5F5),
    inverseOnSurface = Black,
    outline = Color(0xFF2E2E2E),
    outlineVariant = Color(0xFF1E1E1E),
    error = Red,
    onError = White,
)

internal val LightScheme = lightColorScheme(
    primary = Ink,
    onPrimary = White,
    primaryContainer = Color(0xFFE6E6E3),
    onPrimaryContainer = Ink,
    secondary = Color(0xFF4A4A4A),
    onSecondary = White,
    secondaryContainer = Color(0xFFE6E6E3),
    onSecondaryContainer = Ink,
    tertiary = RedLight,
    onTertiary = White,
    background = Paper,
    onBackground = Ink,
    surface = Paper,
    onSurface = Ink,
    surfaceVariant = Color(0xFFEAEAE7),
    onSurfaceVariant = Color(0xFF6B6B6B),
    surfaceContainerLowest = White,
    surfaceContainerLow = Color(0xFFF9F9F8),
    surfaceContainer = White,
    surfaceContainerHigh = Color(0xFFEFEFEC),
    surfaceContainerHighest = Color(0xFFE6E6E3),
    inverseSurface = Ink,
    inverseOnSurface = White,
    outline = Color(0xFFD6D6D2),
    outlineVariant = Color(0xFFE4E4E0),
    error = RedLight,
    onError = White,
)

/** Brand colours Material's scheme has no slot for. */
@Immutable
data class MrColors(
    val accent: Color,
    val onAccent: Color,
    /** 1dp borders and dividers. */
    val hairline: Color,
    /** Unlit dot in dot-matrix elements. */
    val dotDim: Color,
    /** Faint background dot grid. */
    val dotGrid: Color,
    val positive: Color,
    val negative: Color,
)

internal val DarkMrColors = MrColors(
    accent = Red,
    onAccent = White,
    hairline = Color(0xFF242424),
    dotDim = Color(0xFF2A2A2A),
    dotGrid = Color(0xFF1C1C1C),
    positive = Color(0xFFF5F5F5),
    negative = Red,
)

internal val LightMrColors = MrColors(
    accent = RedLight,
    onAccent = White,
    hairline = Color(0xFFE0E0DC),
    dotDim = Color(0xFFDADAD6),
    dotGrid = Color(0xFFE6E6E2),
    positive = Ink,
    negative = RedLight,
)

val LocalMrColors = staticCompositionLocalOf { DarkMrColors }
