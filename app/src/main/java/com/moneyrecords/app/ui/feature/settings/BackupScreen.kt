package com.moneyrecords.app.ui.feature.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.fragment.app.FragmentActivity
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moneyrecords.app.ui.components.GhostButton
import com.moneyrecords.app.ui.components.ListRow
import com.moneyrecords.app.ui.components.PillButton
import com.moneyrecords.app.ui.components.ScreenHeader
import com.moneyrecords.app.ui.components.SectionLabel
import com.moneyrecords.app.ui.components.SwitchRow
import com.moneyrecords.app.ui.components.rememberHaptics
import com.moneyrecords.app.ui.navigation.AppViewModel
import com.moneyrecords.app.ui.theme.MrTheme
import com.moneyrecords.app.ui.theme.Space
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val whenFormat = DateTimeFormatter.ofPattern("d MMM yyyy, h:mm a", Locale.getDefault())
private fun Long.asWhen(): String = Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).format(whenFormat)

/**
 * Settings → Backup & restore. A backup is one file with everything; it stays wherever you save it
 * (Downloads, Drive…). Restore replaces what's in the app after showing what the file holds.
 */
@Composable
fun BackupScreen(
    onBack: () -> Unit,
    onMessage: (String) -> Unit,
    viewModel: BackupViewModel = hiltViewModel(),
    appViewModel: AppViewModel = hiltViewModel(LocalContext.current as FragmentActivity),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val p = state.prefs
    val activity = LocalContext.current as FragmentActivity
    val haptics = rememberHaptics()
    var passwordDialog by remember { mutableStateOf(false) }
    LaunchedEffect(viewModel) { viewModel.messages.collect(onMessage) }

    val create = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri: Uri? ->
        uri?.let(viewModel::saveTo)
    }
    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        uri?.let(viewModel::setFolder)
    }
    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let(viewModel::open)
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = Space.screen, vertical = Space.lg)) {
        item { ScreenHeader(title = "Backup & restore", subtitle = "Your data stays on this phone unless you save it somewhere", onBack = onBack) }
        if (state.busy) item { LinearProgressIndicator(Modifier.fillMaxWidth().padding(bottom = Space.md), color = MaterialTheme.colorScheme.onBackground) }

        item { SectionLabel("Back up") }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(Space.sm), modifier = Modifier.padding(vertical = Space.sm)) {
                PillButton("Back up now", onClick = { create.launch(viewModel.suggestedName()) }, enabled = !state.busy, modifier = Modifier.fillMaxWidth())
                Hint(
                    "One file with your accounts, categories, records, budgets, recurring items and settings." +
                        if (p.hasPassword) " Protected with your backup password." else "",
                )
            }
        }

        item { Spacer(Modifier.height(Space.lg)); SectionLabel("Automatic") }
        item {
            SwitchRow(
                title = "Weekly backup",
                subtitle = if (p.folderUri == null) "Pick a folder once — a backup is saved there every week" else "Keeps the newest 5 in your folder",
                icon = Icons.Outlined.Schedule,
                checked = p.autoEnabled && p.folderUri != null,
                onCheckedChange = { on -> if (on && p.folderUri == null) pickFolder.launch(null) else viewModel.setAuto(on) },
            )
        }
        if (p.folderUri != null) {
            item {
                ListRow(
                    title = "Folder",
                    subtitle = p.folderName ?: "Selected folder",
                    icon = Icons.Outlined.Folder,
                    onClick = { pickFolder.launch(null) },
                    trailing = { TextButton(onClick = { pickFolder.launch(null) }) { Text("Change", color = MaterialTheme.colorScheme.onBackground) } },
                )
            }
            item {
                val failed = p.lastResult != null && p.lastResult != "ok"
                ListRow(
                    title = "Last backup",
                    subtitle = when {
                        failed -> p.lastResult
                        p.lastBackupAtMillis != null -> p.lastBackupAtMillis.asWhen()
                        else -> "Not yet"
                    },
                    subtitleLines = 2,
                    icon = Icons.Outlined.History,
                    trailing = { TextButton(onClick = viewModel::runNow) { Text("Run now", color = MaterialTheme.colorScheme.onBackground) } },
                )
                if (failed) Hint("Pick the folder again to fix this.", danger = true)
            }
        }

        item { Spacer(Modifier.height(Space.lg)); SectionLabel("Password") }
        item {
            ListRow(
                title = "Backup password",
                subtitle = if (p.hasPassword) "On — backups are encrypted" else "Off — anyone with the file can read it",
                icon = Icons.Outlined.Key,
                onClick = { passwordDialog = true },
            )
        }

        item { Spacer(Modifier.height(Space.lg)); SectionLabel("Restore") }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(Space.sm), modifier = Modifier.padding(vertical = Space.sm)) {
                GhostButton("Restore from a file", onClick = { pickFile.launch(arrayOf("application/json", "application/octet-stream", "text/plain", "*/*")) }, enabled = !state.busy, modifier = Modifier.fillMaxWidth())
                Hint("You'll see what's in the file before anything changes.")
            }
        }
        item { Spacer(Modifier.height(Space.xxl)) }
    }

    if (passwordDialog) {
        PasswordSetupDialog(
            hasPassword = p.hasPassword,
            onSet = { viewModel.setPassword(it); passwordDialog = false },
            onRemove = { viewModel.removePassword(); passwordDialog = false },
            onDismiss = { passwordDialog = false },
        )
    }
    if (state.askPassword) {
        AskPasswordDialog(error = state.passwordError, onSubmit = viewModel::submitPassword, onDismiss = viewModel::cancelRestore)
    }
    state.preview?.let { preview ->
        AlertDialog(
            onDismissRequest = viewModel::cancelRestore,
            title = { Text("Restore this backup?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                    Text("From ${preview.file.createdAt.asWhen()}", style = MaterialTheme.typography.titleSmall)
                    Text(preview.counts.summary, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "This replaces everything in MoneyRecords now. A copy of your current data is kept on this phone first.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val lock = appViewModel.appLock
                    if (lock.settings.value.enabled) {
                        lock.confirm(activity, "Confirm restore") { ok -> if (ok) { haptics.confirm(); viewModel.restore() } }
                    } else {
                        haptics.confirm(); viewModel.restore()
                    }
                }) { Text("Restore", color = MrTheme.colors.danger) }
            },
            dismissButton = { TextButton(onClick = viewModel::cancelRestore) { Text("Cancel") } },
        )
    }
}

@Composable
private fun Hint(text: String, danger: Boolean = false) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = if (danger) MrTheme.colors.danger else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = Space.xs),
    )
}

@Composable
private fun PasswordField(value: String, onChange: (String) -> Unit, label: String) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun PasswordSetupDialog(hasPassword: Boolean, onSet: (String) -> Unit, onRemove: () -> Unit, onDismiss: () -> Unit) {
    var first by remember { mutableStateOf("") }
    var second by remember { mutableStateOf("") }
    val error = when {
        first.isNotEmpty() && first.length < 6 -> "At least 6 characters"
        second.isNotEmpty() && second != first -> "Passwords don't match"
        else -> null
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (hasPassword) "Change backup password" else "Set a backup password") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                PasswordField(first, { first = it }, "Password")
                PasswordField(second, { second = it }, "Repeat password")
                Text(
                    error ?: "New backups are encrypted with it. If you forget it, those backups can't be opened.",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (error != null) MrTheme.colors.danger else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (hasPassword) {
                    TextButton(onClick = onRemove) { Text("Remove password", color = MrTheme.colors.danger) }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSet(first) }, enabled = error == null && first.length >= 6 && first == second) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun AskPasswordDialog(error: String?, onSubmit: (String) -> Unit, onDismiss: () -> Unit) {
    var password by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("This backup has a password") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                PasswordField(password, { password = it }, "Backup password")
                error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MrTheme.colors.danger) }
            }
        },
        confirmButton = { TextButton(onClick = { onSubmit(password) }, enabled = password.isNotEmpty()) { Text("Open") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
