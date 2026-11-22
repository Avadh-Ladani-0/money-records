package com.moneyrecords.app.ui.icons

import androidx.compose.foundation.Canvas
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
) {
    val parts = ComboIcon.parts(iconKey)
    if (parts == null) {
        Icon(AppIcons.forKey(iconKey), contentDescription = null, tint = tint, modifier = modifier.size(size))
        return
    }
    val box = size * 1.6f
    val half = size * 0.72f
    Box(modifier.size(box)) {
        Icon(
            AppIcons.forKey(parts.first), contentDescription = null, tint = tint,
            modifier = Modifier.align(Alignment.TopStart).padding(start = box * 0.06f, top = box * 0.06f).size(half),
        )
        Icon(
            AppIcons.forKey(parts.second), contentDescription = null, tint = tint,
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
