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
                AccountType.entries.forEach { type ->
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
