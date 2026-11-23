package com.moneyrecords.app.ui.feature.record

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.moneyrecords.app.domain.money.Money
import com.moneyrecords.app.domain.split.Split
import com.moneyrecords.app.ui.components.ChoiceChip
import com.moneyrecords.app.ui.components.GhostButton
import com.moneyrecords.app.ui.components.MrTextField
import com.moneyrecords.app.ui.components.PillButton
import com.moneyrecords.app.ui.components.SectionLabel
import com.moneyrecords.app.ui.components.rememberHaptics
import com.moneyrecords.app.ui.theme.MrTheme
import com.moneyrecords.app.ui.theme.Space

private fun Long.asInput(): String = Money.format(this, symbol = "").replace(",", "")

/**
 * Split a bill you paid for a group. Equal shares by default (any paise left over are yours);
 * typing a share switches to your own amounts. Names are optional.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SplitSheet(
    total: Long,
    payerName: String?,
    current: SplitDraft?,
    people: List<String>,
    onDone: (SplitDraft?) -> Unit,
    onDismiss: () -> Unit,
) {
    val haptics = rememberHaptics()
    val names = remember { mutableStateListOf<String>().apply { addAll(current?.names?.map { it.orEmpty() } ?: listOf("")) } }
    // Shares as typed text: yours first. Null = follow the equal split.
    var typed by remember { mutableStateOf(current?.custom?.map { it.asInput() }) }
    val count = names.size + 1
    val equal = Split.equalShares(total.coerceAtLeast(0), count)
    val texts = typed?.takeIf { it.size == count } ?: equal.map { it.asInput() }
    val shares = texts.map { Money.parse(it) ?: -1L }
    val sum = shares.filter { it >= 0 }.sum()
    val valid = shares.all { it >= 0 } && shares.drop(1).all { it > 0 } && sum == total
    val draft = SplitDraft(names = names.map { it.trim().ifBlank { null } }, custom = typed?.let { shares })

    fun setPeople(n: Int) {
        if (n == count) return
        if (n < 2 || n > 20) { haptics.reject(); return }
        haptics.tick()
        while (names.size + 1 < n) names.add("")
        while (names.size + 1 > n) names.removeAt(names.lastIndex)
        typed = null // a new head-count starts from equal shares
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(
            Modifier.padding(horizontal = Space.screen).navigationBarsPadding().imePadding().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Space.md),
        ) {
            Text("Split this bill", style = MaterialTheme.typography.headlineSmall)
            Text(
                "${Money.format(total)} paid" + (payerName?.let { " from $it" } ?: "") +
                    ". Their part stays as spending until it's paid back and moved — it's listed under To collect.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            SectionLabel("People, you included")
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { setPeople(count - 1) }) { Icon(Icons.Outlined.Remove, contentDescription = "Fewer people") }
                Text("$count", style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                IconButton(onClick = { setPeople(count + 1) }) { Icon(Icons.Outlined.Add, contentDescription = "More people") }
                Spacer(Modifier.weight(1f))
                if (typed != null) TextButton(onClick = { typed = null }) { Text("Equal shares", color = MaterialTheme.colorScheme.onBackground) }
            }

            ShareRow(label = "You", name = null, amount = texts[0], onName = {}, onAmount = { v ->
                typed = texts.toMutableList().also { it[0] = v }
            })
            names.forEachIndexed { i, name ->
                ShareRow(
                    label = "Person ${i + 2}",
                    name = name,
                    amount = texts[i + 1],
                    onName = { names[i] = it.take(30) },
                    onAmount = { v -> typed = texts.toMutableList().also { it[i + 1] = v } },
                )
            }
            val unused = people.filter { p -> names.none { it.equals(p, ignoreCase = true) } }
            if (unused.isNotEmpty()) {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                    unused.take(10).forEach { p ->
                        ChoiceChip(p, selected = false, onClick = {
                            val empty = names.indexOfFirst { it.isBlank() }
                            if (empty >= 0) names[empty] = p else { names.add(p); typed = null }
                        })
                    }
                }
            }

            val toCollect = (sum - shares[0].coerceAtLeast(0)).coerceAtLeast(0)
            Text(
                if (valid) "${Money.format(shares[0])} yours + ${Money.format(toCollect)} to collect = ${Money.format(total)}"
                else "Shares add up to ${Money.format(sum)} — the bill is ${Money.format(total)}",
                style = MaterialTheme.typography.bodyMedium,
                color = if (valid) MaterialTheme.colorScheme.onSurfaceVariant else MrTheme.colors.danger,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                if (current != null) {
                    GhostButton("Don't split", onClick = { haptics.tick(); onDone(null); onDismiss() }, modifier = Modifier.weight(1f))
                }
                PillButton(
                    "Done",
                    onClick = { haptics.confirm(); onDone(draft); onDismiss() },
                    enabled = valid,
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(Space.lg))
        }
    }
}

@Composable
private fun ShareRow(label: String, name: String?, amount: String, onName: (String) -> Unit, onAmount: (String) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
        if (name == null) {
            Text(label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        } else {
            MrTextField(value = name, onValueChange = onName, label = "$label · name (optional)", modifier = Modifier.weight(1f))
        }
        MrTextField(
            value = amount,
            onValueChange = { onAmount(it.filter { c -> c.isDigit() || c == '.' }) },
            label = "₹",
            keyboardType = KeyboardType.Decimal,
            modifier = Modifier.width(120.dp),
        )
    }
}
