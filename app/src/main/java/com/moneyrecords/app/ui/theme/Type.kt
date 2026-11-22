package com.moneyrecords.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.moneyrecords.app.R

// Both fonts are variable fonts (SIL OFL 1.1, licences in assets/licenses).
private fun dotoFont(weight: Int) = Font(
    resId = R.font.doto,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(
        FontVariation.weight(weight),
        // Fully round dots (0 = square) for the dot-matrix display look.
        FontVariation.Setting("ROND", 100f),
    ),
)

private fun interFont(weight: Int) = Font(
    resId = R.font.inter,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

/** Dot-matrix display face — used for amounts and big numbers only. */
val DotoFamily = FontFamily(dotoFont(500), dotoFont(700), dotoFont(900))

val InterFamily = FontFamily(interFont(400), interFont(500), interFont(600), interFont(700))

/** Tabular figures so amounts line up in lists. */
const val TabularNums = "tnum"

private fun inter(size: Int, weight: Int, line: Int, tracking: Double = 0.0) = TextStyle(
    fontFamily = InterFamily,
    fontWeight = FontWeight(weight),
    fontSize = size.sp,
    lineHeight = line.sp,
    letterSpacing = tracking.sp,
)

private fun doto(size: Int, line: Int, weight: Int = 900) = TextStyle(
    fontFamily = DotoFamily,
    fontWeight = FontWeight(weight),
    fontSize = size.sp,
    lineHeight = line.sp,
    fontFeatureSettings = TabularNums,
)

internal val MrTypography = Typography(
    displayLarge = doto(60, 64),
    displayMedium = doto(44, 48),
    displaySmall = doto(32, 36),
    headlineLarge = inter(32, 600, 40, -0.6),
    headlineMedium = inter(28, 600, 34, -0.5),
    headlineSmall = inter(24, 600, 30, -0.3),
    titleLarge = inter(20, 600, 26, -0.2),
    titleMedium = inter(16, 600, 22),
    titleSmall = inter(14, 600, 20),
    bodyLarge = inter(16, 400, 24),
    bodyMedium = inter(14, 400, 20),
    bodySmall = inter(12, 400, 16),
    labelLarge = inter(14, 600, 20, 0.1),
    // Small labels are rendered uppercase with wide tracking (see SectionLabel).
    labelMedium = inter(12, 600, 16, 1.2),
    labelSmall = inter(12, 500, 16, 1.0),
)
