package com.moneyrecords.app.ui.feature.accounts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moneyrecords.app.domain.model.AccountType
import com.moneyrecords.app.domain.model.DeleteOutcome
import com.moneyrecords.app.ui.components.ChoiceChip
import com.moneyrecords.app.ui.components.EditorTopBar
import com.moneyrecords.app.ui.components.GhostButton
import com.moneyrecords.app.ui.components.MrTextField
import com.moneyrecords.app.ui.components.PillButton
import com.moneyrecords.app.ui.components.SectionLabel
import com.moneyrecords.app.ui.icons.AppIcons
import com.moneyrecords.app.ui.theme.MrTheme
import com.moneyrecords.app.ui.theme.Space

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AccountEditorScreen(
    onClose: () -> Unit,
    onMessage: (String) -> Unit,
    viewModel: AccountEditorViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var confirmDelete by remember { mutableStateOf(false) }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                AccountEditorEvent.Saved -> onClose()
                is AccountEditorEvent.Removed -> {
                    onMessage(
                        if (event.outcome == DeleteOutcome.DELETED) "Account deleted"
                        else "Account has records, so it was archived instead",
                    )
                    onClose()
                }
            }
        }
    }

    Column(Modifier.fillMaxSize().imePadding()) {
        EditorTopBar(
            title = if (state.isNew) "New account" else "Edit account",
            onClose = onClose,
            onDelete = if (state.isNew) null else ({ confirmDelete = true }),
        )
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Space.screen),
            verticalArrangement = Arrangement.spacedBy(Space.lg),
        ) {
            MrTextField(value = state.name, onValueChange = viewModel::onName, label = "Name")

            SectionLabel("Type")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.sm), verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                AccountType.entries.filter { it != AccountType.UPI_LITE || !state.otherLiteExists || state.type == it }.forEach { type ->
                    ChoiceChip(
                        text = type.label(),
                        selected = state.type == type,
                        onClick = { viewModel.onType(type) },
                        icon = AppIcons.forKey(type.defaultIconKey()),
                    )
                }
            }

            MrTextField(
                value = state.openingText,
                onValueChange = viewModel::onOpening,
                label = "Opening balance",
                prefix = "₹ ",
                keyboardType = KeyboardType.Decimal,
                isError = state.openingPaise == null,
                supportingText = "What this account held before you started tracking. Use − for a card that starts in debt.",
            )

            if (state.type == AccountType.UPI_LITE) {
                LiteSection(state, viewModel)
            }

            if (state.type == AccountType.BANK || state.type == AccountType.CARD) {
                MrTextField(
                    value = state.digits,
                    onValueChange = viewModel::onDigits,
                    label = "Last 4 digits (optional)",
                    keyboardType = KeyboardType.Number,
                    supportingText = "Lets MoneyRecords recognise this account in bank SMS, like \"A/c X1234\".",
                )
            }

            if (!state.isNew) {
                GhostButton(
                    text = if (state.archived) "Unarchive account" else "Archive account",
                    onClick = viewModel::toggleArchived,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            state.error?.let { Text(it, color = MrTheme.colors.danger, style = MaterialTheme.typography.bodyMedium) }
            Spacer(Modifier.height(Space.lg))
        }
        PillButton(
            text = "Save",
            onClick = viewModel::save,
            enabled = state.canSave && !state.loading,
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(Space.screen),
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete account?") },
            text = { Text("If this account has records it will be archived instead, so your history stays intact.") },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; viewModel.delete() }) {
                    Text("Delete", color = MrTheme.colors.danger)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

/**
 * UPI Lite: a small on-device wallet inside one payment app, topped up from one bank account.
 * Knowing both lets MoneyRecords turn "UPI LITE Top-up on SBI" SMS into transfers and recognise
 * Lite payments (they never send an SMS).
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun LiteSection(state: AccountEditorState, viewModel: AccountEditorViewModel) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val apps = androidx.compose.runtime.remember { com.moneyrecords.app.detect.PaymentApps.installed(context) }
    SectionLabel("Enabled in")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.sm), verticalArrangement = Arrangement.spacedBy(Space.sm)) {
        apps.forEach { app -> ChoiceChip(app.name, selected = state.liteApp == app.pkg, onClick = { viewModel.onLiteApp(app.pkg) }) }
    }
    SectionLabel("Funded by")
    if (state.banks.isEmpty()) {
        Text("Add your bank account first (type Bank).", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    } else {
        com.moneyrecords.app.ui.components.AccountDropdown("Bank account", state.banks, state.fundingAccountId, viewModel::onFunding, Modifier.fillMaxWidth())
    }
    Text(
        "UPI Lite takes payments up to ₹1,000 and holds up to ₹5,000. Paying from it sends no SMS — MoneyRecords " +
            "catches those payments from the app's notification or the Scan & pay widget. A top-up SMS from the bank " +
            "becomes a transfer into this account. Only one UPI app can have UPI Lite at a time.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
