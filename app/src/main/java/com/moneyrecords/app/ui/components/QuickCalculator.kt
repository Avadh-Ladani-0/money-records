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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.graphics.graphicsLayer
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
 * Calculator handle: a slim sliver on the right edge of the screen. Drag it up or down; its height is stored as a
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

    val haptics = rememberHaptics()
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        // A slim sliver that fits inside the screen's right margin, so it never covers amounts or
        // switches; the touch area is wider than what's drawn.
        val touchWidth = 40.dp
        val touchHeight = 72.dp
        val sliverWidth = 12.dp
        val sliverHeight = 56.dp
        val topLimit = with(density) { WindowInsets.statusBars.getTop(this).toFloat() + 8.dp.toPx() }
        val bottomLimit = with(density) { (maxHeight - touchHeight).toPx() - WindowInsets.navigationBars.getBottom(this) - 8.dp.toPx() }
        var y by remember(prefs.y, topLimit, bottomLimit) {
            mutableFloatStateOf(topLimit + prefs.y * (bottomLimit - topLimit))
        }
        val step = with(density) { 64.dp.toPx() }
        fun save() = onMoved(1f, ((y - topLimit) / (bottomLimit - topLimit)).coerceIn(0f, 1f))
        val slide by animateDpAsState(if (hidden) touchWidth else 0.dp, label = "calcSlide")
        val shape = RoundedCornerShape(topStart = 8.dp, bottomStart = 8.dp)

        Box(
            Modifier
                .align(Alignment.TopEnd)
                .offset { IntOffset(slide.roundToPx(), y.roundToInt()) }
                .size(touchWidth, touchHeight)
                .pointerInput(topLimit, bottomLimit) {
                    detectVerticalDragGestures(
                        onDragStart = { haptics.dragStart() },
                        onDragEnd = { haptics.dragEnd(); save() },
                    ) { change, dragAmount ->
                        change.consume()
                        y = (y + dragAmount).coerceIn(topLimit, bottomLimit)
                    }
                }
                .clickable(role = Role.Button, onClickLabel = "Open calculator") { open = true }
                .semantics {
                    contentDescription = "Calculator"
                    customActions = listOf(
                        CustomAccessibilityAction("Move up") { y = (y - step).coerceIn(topLimit, bottomLimit); save(); true },
                        CustomAccessibilityAction("Move down") { y = (y + step).coerceIn(topLimit, bottomLimit); save(); true },
                    )
                },
            contentAlignment = Alignment.CenterEnd,
        ) {
            Column(
                Modifier
                    .size(sliverWidth, sliverHeight)
                    .clip(shape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                    .border(BorderStroke(1.dp, MrTheme.colors.hairline), shape),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
            ) {
                repeat(3) {
                    Box(Modifier.size(4.dp).background(MaterialTheme.colorScheme.onSurfaceVariant, CircleShape))
                }
            }
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
    val haptics = rememberHaptics()
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
                    modifier = Modifier.weight(1f).semantics {
                        contentDescription = "Result ${resultText ?: "none"}"
                        liveRegion = LiveRegionMode.Polite
                    },
                )
                IconButton(
                    enabled = resultText != null,
                    onClick = {
                        val value = resultText ?: return@IconButton
                        scope.launch {
                            clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("Calculator result", value)))
                            haptics.confirm()
                            copied = true
                        }
                    },
                ) { Icon(Icons.Outlined.ContentCopy, contentDescription = "Copy result") }
            }
            val copiedAlpha by animateFloatAsState(if (copied) 1f else 0f, label = "copied")
            Text(
                if (copied) "Copied $resultText" else " ",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.End,
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer { alpha = copiedAlpha }
                    .semantics { liveRegion = LiveRegionMode.Polite },
            )
            val evaluate: (String) -> String? = { Expression.evaluate(it)?.let(Expression::toPlainText) }
            KeyGrid(
                rows = calculatorRows,
                onKey = { key -> onInput(applyKey(input, key, evaluate = evaluate, allowNegative = true)) },
                keyHeight = 52.dp,
                accepts = { key -> key == KeypadKey.Clear || applyKey(input, key, evaluate, allowNegative = true) != input },
            )
        }
    }
}
