package com.moneyrecords.app.ui.icons

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import com.moneyrecords.app.ui.theme.CategoryPalette
import com.moneyrecords.app.ui.theme.MrTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.moneyrecords.app.domain.repository.ComboIcon

/**
 * Draws a category icon. Plain keys draw one icon at [size]. Combined keys ("combo:a+b") draw a
 * split circle: the first icon top-left, the second bottom-right, divided by a thin diagonal —
 * filling the area a plain icon's badge would (about 1.6 × [size]).
 */
@Composable
fun CategoryIcon(
    iconKey: String?,
    modifier: Modifier = Modifier,
    size: Dp = 22.dp,
    tint: Color = LocalContentColor.current,
    /** Colour each icon by its category (Graphite); combined icons get one colour per half. */
    colored: Boolean = false,
) {
    val parts = ComboIcon.parts(iconKey)
    if (parts == null) {
        val t = if (colored) CategoryPalette.forKey(iconKey) else tint
        Icon(AppIcons.forKey(iconKey), contentDescription = null, tint = t, modifier = modifier.size(size))
        return
    }
    val firstTint = if (colored) CategoryPalette.forKey(parts.first) else tint
    val secondTint = if (colored) CategoryPalette.forKey(parts.second) else tint
    val box = size * 1.6f
    val half = size * 0.72f
    Box(modifier.size(box)) {
        Icon(
            AppIcons.forKey(parts.first), contentDescription = null, tint = firstTint,
            modifier = Modifier.align(Alignment.TopStart).padding(start = box * 0.06f, top = box * 0.06f).size(half),
        )
        Icon(
            AppIcons.forKey(parts.second), contentDescription = null, tint = secondTint,
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = box * 0.06f, bottom = box * 0.06f).size(half),
        )
        Canvas(Modifier.fillMaxSize()) {
            drawLine(
                color = tint.copy(alpha = 0.45f),
                start = Offset(this.size.width * 0.92f, this.size.height * 0.08f),
                end = Offset(this.size.width * 0.08f, this.size.height * 0.92f),
                strokeWidth = 1.2.dp.toPx(),
                cap = StrokeCap.Round,
            )
        }
    }
}

/** The colour that stands for a category in the current theme (ink in Nothing). */
@Composable
fun categoryColor(iconKey: String?): Color {
    if (!MrTheme.colors.tintCategories) return MaterialTheme.colorScheme.onSurface
    val parts = ComboIcon.parts(iconKey)
    return CategoryPalette.forKey(parts?.first ?: iconKey)
}

/**
 * A category's round badge: the one place that draws it, so every list, picker and budget row
 * matches. Graphite tints the badge and icon with the category's colour; Nothing stays monochrome.
 */
@Composable
fun CategoryBadge(
    iconKey: String?,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    iconSize: Dp = 22.dp,
    /** Replaces the background, e.g. for a selected cell. */
    background: Color? = null,
    tint: Color? = null,
) {
    val colors = MrTheme.colors
    val parts = ComboIcon.parts(iconKey)
    val base = if (colors.tintCategories) CategoryPalette.forKey(parts?.first ?: iconKey) else null
    val bg = background ?: base?.copy(alpha = 0.16f) ?: colors.badge
    Box(modifier.size(size).background(bg, CircleShape), contentAlignment = Alignment.Center) {
        CategoryIcon(
            iconKey,
            size = iconSize,
            tint = tint ?: MaterialTheme.colorScheme.onSurface,
            colored = tint == null && colors.tintCategories,
        )
    }
}
