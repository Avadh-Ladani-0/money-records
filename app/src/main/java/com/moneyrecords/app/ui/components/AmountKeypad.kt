package com.moneyrecords.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.moneyrecords.app.ui.theme.Space

/** Keys the keypad can send. Operators use the display symbols (+ − × ÷). */
sealed interface KeypadKey {
    data class Digit(val char: Char) : KeypadKey
    data object Dot : KeypadKey
    data class Operator(val symbol: Char) : KeypadKey
    data object Backspace : KeypadKey
    data object Clear : KeypadKey
    data object Equals : KeypadKey
    data object Percent : KeypadKey
}

private val amountRows: List<List<KeypadKey>> = listOf(
    listOf(KeypadKey.Digit('7'), KeypadKey.Digit('8'), KeypadKey.Digit('9'), KeypadKey.Operator('÷')),
    listOf(KeypadKey.Digit('4'), KeypadKey.Digit('5'), KeypadKey.Digit('6'), KeypadKey.Operator('×')),
    listOf(KeypadKey.Digit('1'), KeypadKey.Digit('2'), KeypadKey.Digit('3'), KeypadKey.Operator('−')),
    listOf(KeypadKey.Dot, KeypadKey.Digit('0'), KeypadKey.Backspace, KeypadKey.Operator('+')),
)

/**
 * The ₹ keypad used when entering a record amount. Long-press ⌫ clears.
 * [accepts] tells whether a key would change the input; refused keys give a "reject" buzz.
 */
@Composable
fun AmountKeypad(
    onKey: (KeypadKey) -> Unit,
    modifier: Modifier = Modifier,
    keyHeight: Dp = 54.dp,
    accepts: (KeypadKey) -> Boolean = { true },
) {
    KeyGrid(amountRows, onKey, modifier, keyHeight, accepts)
}

/** Generic key grid shared by the amount keypad and the floating calculator. */
@Composable
fun KeyGrid(
    rows: List<List<KeypadKey>>,
    onKey: (KeypadKey) -> Unit,
    modifier: Modifier = Modifier,
    keyHeight: Dp = 54.dp,
    accepts: (KeypadKey) -> Boolean = { true },
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Space.sm)) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                row.forEach { key -> Key(key, onKey, accepts, Modifier.weight(1f).heightIn(min = keyHeight)) }
            }
        }
    }
}

@Composable
private fun Key(key: KeypadKey, onKey: (KeypadKey) -> Unit, accepts: (KeypadKey) -> Boolean, modifier: Modifier) {
    val haptics = rememberHaptics()
    val isOperator = key is KeypadKey.Operator || key is KeypadKey.Percent || key is KeypadKey.Equals || key is KeypadKey.Clear
    val label = when (key) {
        is KeypadKey.Digit -> key.char.toString()
        KeypadKey.Dot -> "."
        is KeypadKey.Operator -> key.symbol.toString()
        KeypadKey.Percent -> "%"
        KeypadKey.Equals -> "="
        KeypadKey.Clear -> "C"
        KeypadKey.Backspace -> null
    }
    val description = when (key) {
        KeypadKey.Backspace -> "Delete"
        is KeypadKey.Operator -> when (key.symbol) { '+' -> "Plus"; '−' -> "Minus"; '×' -> "Times"; else -> "Divide" }
        KeypadKey.Dot -> "Decimal point"
        KeypadKey.Percent -> "Percent"
        KeypadKey.Equals -> "Equals"
        KeypadKey.Clear -> "Clear"
        is KeypadKey.Digit -> key.char.toString()
    }
    Box(
        modifier
            .clip(MaterialTheme.shapes.medium)
            .background(
                if (key == KeypadKey.Equals) MaterialTheme.colorScheme.onBackground
                else MaterialTheme.colorScheme.surfaceContainerHigh,
            )
            .combinedClickable(
                role = Role.Button,
                onClick = {
                    // A tap on every key; a refused key (third decimal, too long) buzzes differently.
                    if (accepts(key)) haptics.tick() else haptics.reject()
                    onKey(key)
                },
                onLongClick = if (key == KeypadKey.Backspace) ({
                    haptics.longPress()
                    onKey(KeypadKey.Clear)
                }) else null,
                onLongClickLabel = if (key == KeypadKey.Backspace) "Clear" else null,
            )
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        if (label != null) {
            Text(
                label,
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Medium),
                color = when {
                    key == KeypadKey.Equals -> MaterialTheme.colorScheme.background
                    isOperator -> MaterialTheme.colorScheme.onSurfaceVariant
                    else -> MaterialTheme.colorScheme.onSurface
                },
            )
        } else {
            Icon(Icons.AutoMirrored.Outlined.Backspace, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * Applies a key to calculator input text. Keeps input well-formed: one dot per number, at most two
 * decimals, no leading operators except minus, a new operator replaces a trailing one.
 * [allowNegative] lets the calculator start with "−"; the amount keypad doesn't.
 */
fun applyKey(text: String, key: KeypadKey, evaluate: (String) -> String?, allowNegative: Boolean = false): String {
    val operators = "+−×÷"
    val lastNumber = text.takeLastWhile { it.isDigit() || it == '.' }
    return when (key) {
        is KeypadKey.Digit -> when {
            text.length >= 40 -> text
            lastNumber.contains('.') && lastNumber.substringAfter('.').length >= 2 -> text
            lastNumber == "0" -> text.dropLast(1) + key.char
            text.endsWith("%") -> text
            else -> text + key.char
        }
        KeypadKey.Dot -> when {
            lastNumber.contains('.') || text.endsWith("%") -> text
            lastNumber.isEmpty() -> text + "0."
            else -> "$text."
        }
        is KeypadKey.Operator -> when {
            text.isEmpty() -> if (allowNegative && key.symbol == '−') "−" else text
            text == "−" -> text
            text.last() in operators -> text.dropLast(1) + key.symbol
            text.last() == '.' -> text.dropLast(1) + key.symbol
            else -> text + key.symbol
        }
        KeypadKey.Percent -> if (lastNumber.isNotEmpty() && !text.endsWith(".")) "$text%" else text
        KeypadKey.Backspace -> text.dropLast(1)
        KeypadKey.Clear -> ""
        KeypadKey.Equals -> evaluate(text) ?: text
    }
}
