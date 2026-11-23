package com.moneyrecords.app.ui.feature.collect

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import com.moneyrecords.app.domain.money.Money
import com.moneyrecords.app.domain.split.AwaitingMove
import com.moneyrecords.app.ui.components.MrCard
import com.moneyrecords.app.ui.components.MrTextField
import com.moneyrecords.app.ui.components.PillButton
import com.moneyrecords.app.ui.components.rememberHaptics
import com.moneyrecords.app.ui.theme.Space

internal fun Long.asInput(): String = Money.format(this, symbol = "").replace(",", "")

/**
 * "₹450 collected in SBI · 3 repayments — Move to UPI Lite". Tapping asks how much was moved
 * (everything by default) — after doing it in the bank app.
 */
@Composable
fun MoveCard(move: AwaitingMove, onMove: (amount: Long) -> Unit, modifier: Modifier = Modifier) {
    var asking by remember { mutableStateOf(false) }
    MrCard(modifier = modifier, contentPadding = PaddingValues(horizontal = Space.xl, vertical = Space.lg)) {
        Text("${Money.format(move.total)} collected in ${move.heldInName}", style = MaterialTheme.typography.titleMedium)
        Text(
            (if (move.count > 1) "${move.count} repayments · " else "") +
                "after moving it to ${move.payerName} in your bank app, tap below — it'll look as if it came straight back.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(Space.md))
        PillButton("Move to ${move.payerName}", onClick = { asking = true }, modifier = Modifier.fillMaxWidth())
    }
    if (asking) MoveSheet(move, onMove = onMove, onDismiss = { asking = false })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MoveSheet(move: AwaitingMove, onMove: (Long) -> Unit, onDismiss: () -> Unit) {
    val haptics = rememberHaptics()
    var text by remember { mutableStateOf(move.total.asInput()) }
    val amount = Money.parse(text)
    val valid = amount != null && amount in 1..move.total
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(
            Modifier.padding(horizontal = Space.screen).navigationBarsPadding().imePadding(),
            verticalArrangement = Arrangement.spacedBy(Space.md),
        ) {
            Text("Move to ${move.payerName}", style = MaterialTheme.typography.headlineSmall)
            Text(
                "How much did you move from ${move.heldInName} to ${move.payerName}? The oldest repayments go first; " +
                    "anything not moved keeps waiting.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            MrTextField(
                value = text,
                onValueChange = { text = it.filter { c -> c.isDigit() || c == '.' } },
                label = "Amount moved",
                prefix = "₹ ",
                keyboardType = KeyboardType.Decimal,
                isError = !valid,
                supportingText = when {
                    amount != null && amount > move.total -> "Only ${Money.format(move.total)} is waiting"
                    amount != null && amount in 1 until move.total -> "${Money.format(move.total - amount)} will keep waiting in ${move.heldInName}"
                    else -> null
                },
            )
            PillButton(
                "Move",
                enabled = valid,
                onClick = { haptics.confirm(); onMove(amount!!); onDismiss() },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(Space.lg))
        }
    }
}
