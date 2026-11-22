package com.moneyrecords.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.unit.dp

internal val MrShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(36.dp),
)

/** Spacing scale — use these instead of ad-hoc dp values. */
object Space {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp
    val xxl = 32.dp
    val screen = 20.dp
    /** Room left under the last list item so the add button never covers it. */
    val fabClearance = 96.dp
    /** Right-edge room for the floating calculator handle. */
    val handle = 14.dp
}

@Composable
fun MoneyRecordsTheme(
    theme: AppTheme = AppTheme.NOTHING,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalMrColors provides mrColorsFor(theme)) {
        MaterialTheme(
            colorScheme = schemeFor(theme),
            typography = MrTypography,
            shapes = MrShapes,
            content = content,
        )
    }
}

object MrTheme {
    val colors: MrColors
        @Composable @ReadOnlyComposable
        get() = LocalMrColors.current
}
