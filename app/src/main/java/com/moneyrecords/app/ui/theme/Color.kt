package com.moneyrecords.app.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** The two looks the user can pick in Settings → Appearance. */
enum class AppTheme(val label: String, val description: String) {
    NOTHING("Nothing", "Pure black, white type, red only when something needs attention"),
    GRAPHITE("Graphite", "Soft dark grey; colour tells income, spending and status apart"),
}

private val White = Color(0xFFFFFFFF)
private val Black = Color(0xFF000000)
private val Red = Color(0xFFE5322D)

// ── Nothing: monochrome, red reserved for alarms and the one main action ─────────────────────────

private val NothingInk = Color(0xFFF5F5F5)

internal val NothingScheme = darkColorScheme(
    primary = NothingInk,
    onPrimary = Black,
    primaryContainer = Color(0xFF1E1E1E),
    onPrimaryContainer = NothingInk,
    secondary = Color(0xFFBDBDBD),
    onSecondary = Black,
    secondaryContainer = Color(0xFF1E1E1E),
    onSecondaryContainer = NothingInk,
    tertiary = Red,
    onTertiary = White,
    background = Black,
    onBackground = NothingInk,
    surface = Black,
    onSurface = NothingInk,
    surfaceVariant = Color(0xFF161616),
    onSurfaceVariant = Color(0xFF9A9A9A),
    surfaceContainerLowest = Black,
    surfaceContainerLow = Color(0xFF0B0B0B),
    surfaceContainer = Color(0xFF111111),
    surfaceContainerHigh = Color(0xFF1C1C1C),
    surfaceContainerHighest = Color(0xFF262626),
    inverseSurface = NothingInk,
    inverseOnSurface = Black,
    outline = Color(0xFF2E2E2E),
    outlineVariant = Color(0xFF1E1E1E),
    error = Red,
    onError = White,
)

// ── Graphite: charcoal grey, colour carries meaning ──────────────────────────────────────────────

private val GraphiteInk = Color(0xFFECEDEF)
private val GraphiteMuted = Color(0xFFA3A7AF)
private val GraphiteGreen = Color(0xFF4CC38A)
private val GraphiteCoral = Color(0xFFF2685C)
private val GraphiteBlue = Color(0xFF6EA8FE)
private val GraphiteAmber = Color(0xFFF5B544)

internal val GraphiteScheme = darkColorScheme(
    primary = GraphiteInk,
    onPrimary = Color(0xFF1C1D21),
    primaryContainer = Color(0xFF3A3D44),
    onPrimaryContainer = GraphiteInk,
    secondary = GraphiteMuted,
    onSecondary = Color(0xFF1C1D21),
    secondaryContainer = Color(0xFF3A3D44),
    onSecondaryContainer = GraphiteInk,
    tertiary = GraphiteBlue,
    onTertiary = Color(0xFF0E1A2E),
    background = Color(0xFF24262B),
    onBackground = GraphiteInk,
    surface = Color(0xFF24262B),
    onSurface = GraphiteInk,
    surfaceVariant = Color(0xFF2F3238),
    onSurfaceVariant = GraphiteMuted,
    surfaceContainerLowest = Color(0xFF1E2024),
    surfaceContainerLow = Color(0xFF292B30),
    surfaceContainer = Color(0xFF2F3238),
    surfaceContainerHigh = Color(0xFF383B42),
    surfaceContainerHighest = Color(0xFF42464E),
    inverseSurface = GraphiteInk,
    inverseOnSurface = Color(0xFF1C1D21),
    outline = Color(0xFF4A4E57),
    outlineVariant = Color(0xFF383B42),
    error = GraphiteCoral,
    onError = Color(0xFF2B0B08),
)

/** Colours Material's scheme has no slot for. Money and status colours always come from here. */
@Immutable
data class MrColors(
    /** The one main action (add button, Save) and the selected-tab dot. */
    val accent: Color,
    val onAccent: Color,
    /** 1dp borders and dividers. */
    val hairline: Color,
    /** Unlit dot in dot-matrix elements. */
    val dotDim: Color,
    /** Faint background dot grid. */
    val dotGrid: Color,
    /** Income amounts. */
    val positive: Color,
    /** Expense amounts in lists and totals. */
    val expense: Color,
    /** Transfer amounts. */
    val transfer: Color,
    /** A balance below zero — an alarm. */
    val negative: Color,
    /** A budget close to its limit. */
    val warning: Color,
    /** A budget over its limit — an alarm. */
    val over: Color,
    /** Error messages and destructive actions. */
    val danger: Color,
    /** Switch track when on. */
    val switchOn: Color,
    val onSwitchOn: Color,
    /** Whether categories get their own colour (Graphite) or stay monochrome (Nothing). */
    val tintCategories: Boolean,
    /** Background of a category's round badge when [tintCategories] is off. */
    val badge: Color,
)

internal val NothingMrColors = MrColors(
    accent = Red,
    onAccent = White,
    hairline = Color(0xFF242424),
    dotDim = Color(0xFF2A2A2A),
    dotGrid = Color(0xFF1C1C1C),
    positive = NothingInk,
    expense = NothingInk,
    transfer = Color(0xFF9A9A9A),
    negative = Red,
    // Budget status: white = fine, yellow = 80% used, red = over.
    warning = Color(0xFFFFC83D),
    over = Red,
    danger = Red,
    switchOn = NothingInk,
    onSwitchOn = Black,
    tintCategories = false,
    badge = Color(0xFF1C1C1C),
)

internal val GraphiteMrColors = MrColors(
    accent = GraphiteInk,
    onAccent = Color(0xFF1C1D21),
    hairline = Color(0xFF383B42),
    dotDim = Color(0xFF3A3D44),
    dotGrid = Color(0xFF2B2D32),
    positive = GraphiteGreen,
    expense = GraphiteCoral,
    transfer = GraphiteBlue,
    negative = GraphiteCoral,
    warning = GraphiteAmber,
    over = GraphiteCoral,
    danger = GraphiteCoral,
    switchOn = GraphiteGreen,
    onSwitchOn = Color(0xFF0B2A1C),
    tintCategories = true,
    badge = Color(0xFF383B42),
)

fun schemeFor(theme: AppTheme): ColorScheme = when (theme) {
    AppTheme.NOTHING -> NothingScheme
    AppTheme.GRAPHITE -> GraphiteScheme
}

fun mrColorsFor(theme: AppTheme): MrColors = when (theme) {
    AppTheme.NOTHING -> NothingMrColors
    AppTheme.GRAPHITE -> GraphiteMrColors
}

val LocalMrColors = staticCompositionLocalOf { NothingMrColors }

/**
 * Muted category colours for Graphite, keyed by icon key so no database change is needed.
 * Unknown keys (custom icons added later) fall back to a stable pick from the same palette.
 */
object CategoryPalette {
    private val byKey = mapOf(
        "food" to Color(0xFFF59E5B),
        "groceries" to Color(0xFF8FCB6B),
        "transport" to Color(0xFF4FC4CF),
        "fuel" to Color(0xFFE8C15A),
        "shopping" to Color(0xFFEF8FB4),
        "bills" to Color(0xFFC38AF0),
        "rent" to Color(0xFF8B9CF7),
        "health" to Color(0xFFF2817A),
        "education" to Color(0xFF6FB8F2),
        "entertainment" to Color(0xFFD98BE0),
        "travel" to Color(0xFF52C7B0),
        "personal" to Color(0xFFE5A3D2),
        "gift" to Color(0xFFF0B66E),
        "other" to Color(0xFFA9AEB8),
        "salary" to Color(0xFF4CC38A),
        "business" to Color(0xFF7FC7A0),
        "interest" to Color(0xFF9AD27A),
        "refund" to Color(0xFF6ED3C3),
        "cash" to Color(0xFF8FCB6B),
        "bank" to Color(0xFF8B9CF7),
        "card" to Color(0xFFC38AF0),
        "wallet" to Color(0xFFF59E5B),
    )
    private val fallback = byKey.values.distinct()

    fun forKey(key: String?): Color {
        if (key == null) return byKey.getValue("other")
        return byKey[key] ?: fallback[Math.floorMod(key.hashCode(), fallback.size)]
    }
}
