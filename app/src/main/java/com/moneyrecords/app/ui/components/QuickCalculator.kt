package com.moneyrecords.app.ui.components

import android.content.ClipData
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.ui.geometry.Offset
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
 * A draggable calculator bubble drawn over the whole app. Its position is stored as a fraction of
 * the screen so it survives rotation and different screen sizes.
 */
@Composable
fun QuickCalculatorOverlay(prefs: CalculatorPrefs, onMoved: (x: Float, y: Float) -> Unit) {
    var open by rememberSaveable { mutableStateOf(false) }
    var input by rememberSaveable { mutableStateOf("") }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val bubble = 52.dp
        val margin = 8.dp
        val maxX = with(density) { (maxWidth - bubble - margin).toPx() }
        val maxY = with(density) { (maxHeight - bubble - margin).toPx() }
        val minPx = with(density) { margin.toPx() }
        var offset by remember(prefs.x, prefs.y, maxX, maxY) {
            mutableStateOf(Offset(minPx + prefs.x * (maxX - minPx), minPx + prefs.y * (maxY - minPx)))
        }

        Box(
            Modifier
                .offset { IntOffset(offset.x.roundToInt(), offset.y.roundToInt()) }
                .size(bubble)
                .shadow(8.dp, CircleShape)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                .border(BorderStroke(1.dp, MrTheme.colors.hairline), CircleShape)
                .pointerInput(maxX, maxY) {
                    detectDragGestures(
                        onDragEnd = {
                            onMoved(
                                ((offset.x - minPx) / (maxX - minPx)).coerceIn(0f, 1f),
                                ((offset.y - minPx) / (maxY - minPx)).coerceIn(0f, 1f),
                            )
                        },
                    ) { change, drag ->
                        change.consume()
                        offset = Offset(
                            (offset.x + drag.x).coerceIn(minPx, maxX),
                            (offset.y + drag.y).coerceIn(minPx, maxY),
                        )
                    }
                }
                .clickable { open = true }
                .semantics { contentDescription = "Calculator" },
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.Calculate, contentDescription = null, tint = MaterialTheme.colorScheme.onBackground)
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
