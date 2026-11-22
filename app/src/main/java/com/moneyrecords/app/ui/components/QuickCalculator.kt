package com.moneyrecords.app.ui.components

import android.content.ClipData
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Calculate
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.moneyrecords.app.domain.calc.Expression
import com.moneyrecords.app.domain.repository.CalculatorPrefs
import com.moneyrecords.app.ui.theme.MrTheme
import com.moneyrecords.app.ui.theme.Space
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private val calculatorRows: List<List<KeypadKey>> = listOf(
    listOf(KeypadKey.Clear, KeypadKey.Backspace, KeypadKey.Percent, KeypadKey.Operator('÷')),
    listOf(KeypadKey.Digit('7'), KeypadKey.Digit('8'), KeypadKey.Digit('9'), KeypadKey.Operator('×')),
    listOf(KeypadKey.Digit('4'), KeypadKey.Digit('5'), KeypadKey.Digit('6'), KeypadKey.Operator('−')),
    listOf(KeypadKey.Digit('1'), KeypadKey.Digit('2'), KeypadKey.Digit('3'), KeypadKey.Operator('+')),
    listOf(KeypadKey.Digit('0'), KeypadKey.Dot, KeypadKey.Equals),
)

/**
 * Calculator tab docked to the right edge of the screen. Drag it up or down; its height is stored as a
 * fraction of the screen. It slides off-screen while content scrolls ([scrollSignal] changes) or the
 * keyboard is open, and slides back once things are idle, so it never sits over what you're reading.
 */
@Composable
fun QuickCalculatorOverlay(
    prefs: CalculatorPrefs,
    scrollSignal: State<Int>,
    onMoved: (x: Float, y: Float) -> Unit,
) {
    var open by rememberSaveable { mutableStateOf(false) }
    var input by rememberSaveable { mutableStateOf("") }
    var scrolling by remember { mutableStateOf(false) }
    val imeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0

    // Any scroll hides the tab; it returns after a short quiet period.
    LaunchedEffect(scrollSignal.value) {
        if (scrollSignal.value == 0) return@LaunchedEffect
        scrolling = true
        delay(900)
        scrolling = false
    }
    val hidden = scrolling || imeVisible

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val tabWidth = 34.dp
        val tabHeight = 56.dp
        val topLimit = with(density) { WindowInsets.statusBars.getTop(this).toFloat() + 8.dp.toPx() }
        val bottomLimit = with(density) { (maxHeight - tabHeight).toPx() - WindowInsets.navigationBars.getBottom(this) - 8.dp.toPx() }
        var y by remember(prefs.y, topLimit, bottomLimit) {
            mutableFloatStateOf(topLimit + prefs.y * (bottomLimit - topLimit))
        }
        val slide by animateDpAsState(if (hidden) tabWidth + 4.dp else 0.dp, label = "calcSlide")
        val shape = RoundedCornerShape(topStart = 18.dp, bottomStart = 18.dp)

        Box(
            Modifier
                .align(Alignment.TopEnd)
                .offset { IntOffset(slide.roundToPx(), y.roundToInt()) }
                .size(tabWidth, tabHeight)
                .shadow(6.dp, shape)
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                .border(BorderStroke(1.dp, MrTheme.colors.hairline), shape)
                .pointerInput(topLimit, bottomLimit) {
                    detectVerticalDragGestures(
                        onDragEnd = { onMoved(1f, ((y - topLimit) / (bottomLimit - topLimit)).coerceIn(0f, 1f)) },
                    ) { change, dragAmount ->
                        change.consume()
                        y = (y + dragAmount).coerceIn(topLimit, bottomLimit)
                    }
                }
                .clickable { open = true }
                .semantics { contentDescription = "Calculator" },
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.Calculate, contentDescription = null, tint = MaterialTheme.colorScheme.onBackground, modifier = Modifier.size(20.dp))
        }
    }

    if (open) {
        Dialog(onDismissRequest = { open = false }) {
            QuickCalculatorCard(input = input, onInput = { input = it }, onClose = { open = false })
        }
    }
}

@Composable
private fun QuickCalculatorCard(input: String, onInput: (String) -> Unit, onClose: () -> Unit) {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    var copied by remember { mutableStateOf(false) }
    val result = Expression.evaluate(input)
    val resultText = result?.let(Expression::toPlainText)

    LaunchedEffect(copied) {
        if (copied) { delay(1_500); copied = false }
    }

    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MrTheme.colors.hairline),
        modifier = Modifier.widthIn(max = 360.dp),
    ) {
        Column(Modifier.padding(Space.lg), verticalArrangement = Arrangement.spacedBy(Space.md)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionLabel("Calculator", Modifier.weight(1f))
                IconButton(onClick = onClose) { Icon(Icons.Outlined.Close, contentDescription = "Close calculator") }
            }
            Text(
                input.ifEmpty { " " },
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.End,
                maxLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                BasicText(
                    text = resultText ?: if (input.isEmpty()) "0" else "—",
                    style = MaterialTheme.typography.displayMedium.copy(color = MaterialTheme.colorScheme.onBackground, textAlign = TextAlign.End),
                    maxLines = 1,
                    autoSize = TextAutoSize.StepBased(minFontSize = 20.sp, maxFontSize = 44.sp, stepSize = 2.sp),
                    modifier = Modifier.weight(1f),
                )
                IconButton(
                    enabled = resultText != null,
                    onClick = {
                        val value = resultText ?: return@IconButton
                        scope.launch {
                            clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("Calculator result", value)))
                            copied = true
                        }
                    },
                ) { Icon(Icons.Outlined.ContentCopy, contentDescription = "Copy result") }
            }
            Text(
                if (copied) "Copied $resultText" else " ",
                style = MaterialTheme.typography.labelMedium,
                color = MrTheme.colors.accent,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.End,
            )
            KeyGrid(
                rows = calculatorRows,
                onKey = { key -> onInput(applyKey(input, key, evaluate = { Expression.evaluate(it)?.let(Expression::toPlainText) }, allowNegative = true)) },
                keyHeight = 52.dp,
            )
        }
    }
}
