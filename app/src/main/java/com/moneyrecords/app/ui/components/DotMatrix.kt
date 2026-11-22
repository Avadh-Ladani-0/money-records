package com.moneyrecords.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.moneyrecords.app.ui.theme.MrTheme
import kotlin.math.floor
import kotlin.math.max

/**
 * A progress bar drawn as a row of dots that light up in turn.
 * Past 100% the lit dots switch to the accent colour to flag an overrun.
 */
@Composable
fun DotProgress(
    progress: Float,
    modifier: Modifier = Modifier,
    dotSize: Dp = 8.dp,
    activeColor: Color = MaterialTheme.colorScheme.onBackground,
    overColor: Color = MrTheme.colors.accent,
    dimColor: Color = MrTheme.colors.dotDim,
) {
    val over = progress > 1f
    val animated by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 700),
        label = "dotProgress",
    )
    val lit = if (over) overColor else activeColor
    Canvas(
        modifier
            .fillMaxWidth()
            .height(dotSize)
            .semantics { progressBarRangeInfo = ProgressBarRangeInfo(progress.coerceIn(0f, 1f), 0f..1f) },
    ) {
        val d = size.height
        val minGap = d * 0.6f
        val count = max(1, floor((size.width + minGap) / (d + minGap)).toInt())
        val gap = if (count > 1) (size.width - count * d) / (count - 1) else 0f
        val filled = animated * count
        val r = d / 2
        for (i in 0 until count) {
            val center = Offset(r + i * (d + gap), r)
            drawCircle(dimColor, r, center)
            val amount = (filled - i).coerceIn(0f, 1f)
            if (amount > 0f) drawCircle(lit.copy(alpha = lit.alpha * amount), r, center)
        }
    }
}

/** A faint dot grid behind content — the brand's background texture. */
fun Modifier.dotGrid(color: Color, spacing: Dp = 14.dp, radius: Dp = 1.dp): Modifier = drawBehind {
    val step = spacing.toPx()
    val r = radius.toPx()
    var y = step / 2
    while (y < size.height) {
        var x = step / 2
        while (x < size.width) {
            drawCircle(color, r, Offset(x, y))
            x += step
        }
        y += step
    }
}

/**
 * A small dot-matrix glyph from rows of '#' (lit) and '.' (dim); spaces are ignored.
 * Used for empty-state illustrations.
 */
@Composable
fun DotGlyph(
    pattern: List<String>,
    modifier: Modifier = Modifier,
    dot: Dp = 6.dp,
    gap: Dp = 4.dp,
    litColor: Color = MaterialTheme.colorScheme.onBackground,
    accentColor: Color = MrTheme.colors.accent,
    dimColor: Color = MrTheme.colors.dotDim,
) {
    val rows = pattern.map { it.replace(" ", "") }
    val cols = rows.maxOfOrNull { it.length } ?: 0
    val w = dot * cols + gap * (cols - 1).coerceAtLeast(0)
    val h = dot * rows.size + gap * (rows.size - 1).coerceAtLeast(0)
    Canvas(modifier.size(w, h)) {
        val d = dot.toPx()
        val g = gap.toPx()
        rows.forEachIndexed { row, line ->
            line.forEachIndexed { col, c ->
                val color = when (c) {
                    '#' -> litColor
                    '*' -> accentColor
                    else -> dimColor
                }
                drawCircle(color, d / 2, Offset(col * (d + g) + d / 2, row * (d + g) + d / 2))
            }
        }
    }
}

object Glyphs {
    val Empty = listOf(
        ". . . . . . .",
        ". # # # # # .",
        ". # . . . # .",
        ". # . * . # .",
        ". # . . . # .",
        ". # # # # # .",
        ". . . . . . .",
    )
    val Chart = listOf(
        ". . . . . . *",
        ". . . . # . #",
        ". . # . # . #",
        "# . # . # . #",
    )
}
