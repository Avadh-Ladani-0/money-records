package com.moneyrecords.app.ui.feature.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moneyrecords.app.backup.BackupCodec
import com.moneyrecords.app.backup.BackupError
import com.moneyrecords.app.backup.BackupFolder
import com.moneyrecords.app.backup.BackupManager
import com.moneyrecords.app.backup.BackupPreview
import com.moneyrecords.app.domain.repository.BackupPrefs
import com.moneyrecords.app.domain.repository.UserPrefsRepository
import com.moneyrecords.app.security.SecretStore
import com.moneyrecords.app.work.AutoBackupWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

data class BackupUiState(
    val prefs: BackupPrefs = BackupPrefs(),
    val busy: Boolean = false,
    /** A file picked for restore that needs its password. */
    val askPassword: Boolean = false,
    val passwordError: String? = null,
    /** A readable backup waiting for "Restore". */
    val preview: BackupPreview? = null,
)

@HiltViewModel
class BackupViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val backups: BackupManager,
    private val prefs: UserPrefsRepository,
    private val secrets: SecretStore,
    private val clock: Clock,
) : ViewModel() {
    private val _state = MutableStateFlow(BackupUiState())
    val state: StateFlow<BackupUiState> = _state.asStateFlow()
    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()
    private var pendingBytes: ByteArray? = null

    init {
        viewModelScope.launch { prefs.observeBackup().collect { p -> _state.update { it.copy(prefs = p) } } }
    }

    fun suggestedName(): String = BackupFolder.fileName(LocalDate.now(clock))

    private fun work(block: suspend () -> Unit) {
        viewModelScope.launch {
            _state.update { it.copy(busy = true) }
            try { block() } finally { _state.update { it.copy(busy = false) } }
        }
    }

    /** "Back up now" → the file the user created. */
    fun saveTo(uri: Uri) = work {
        val result = runCatching {
            val bytes = backups.createBackup()
            withContext(Dispatchers.IO) {
                context.contentResolver.openOutputStream(uri, "w")?.use { it.write(bytes) } ?: error("Can't write there")
            }
        }
        _messages.send(if (result.isSuccess) "Backup saved" else "Couldn't save the backup")
    }

    // ── Automatic ──

    fun setFolder(uri: Uri) {
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        runCatching { context.contentResolver.takePersistableUriPermission(uri, flags) }
        viewModelScope.launch {
            prefs.updateBackup { it.copy(folderUri = uri.toString(), folderName = BackupFolder.displayName(uri), autoEnabled = true, lastResult = null) }
            AutoBackupWorker.schedule(context)
            AutoBackupWorker.runNow(context)
            _messages.send("Weekly backups on — making the first one now")
        }
    }

    fun setAuto(on: Boolean) {
        viewModelScope.launch {
            prefs.updateBackup { it.copy(autoEnabled = on) }
            if (on) AutoBackupWorker.schedule(context) else AutoBackupWorker.cancel(context)
        }
    }

    fun runNow() {
        AutoBackupWorker.runNow(context)
        viewModelScope.launch { _messages.send("Backing up to your folder…") }
    }

    // ── Password ──

    fun setPassword(password: String) {
        viewModelScope.launch {
            prefs.updateBackup { it.copy(wrappedPassword = secrets.wrap(password)) }
            _messages.send("Backups will be protected with this password")
        }
    }

    fun removePassword() {
        viewModelScope.launch {
            prefs.updateBackup { it.copy(wrappedPassword = null) }
            _messages.send("Backup password removed")
        }
    }

    // ── Restore ──

    /** Reads the picked file; asks for a password if it needs one (trying the saved one first). */
    fun open(uri: Uri) = work {
        val bytes = runCatching {
            withContext(Dispatchers.IO) { context.contentResolver.openInputStream(uri)?.use { it.readBytes() } }
        }.getOrNull()
        if (bytes == null) { _messages.send("Couldn't open that file"); return@work }
        pendingBytes = bytes
        val saved = if (BackupCodec.isEncrypted(bytes)) backups.savedPassword() else null
        tryPreview(bytes, saved, askIfNeeded = true)
    }

    fun submitPassword(password: String) = work {
        val bytes = pendingBytes ?: return@work
        tryPreview(bytes, password, askIfNeeded = false)
    }

    private suspend fun tryPreview(bytes: ByteArray, password: String?, askIfNeeded: Boolean) {
        try {
            val preview = withContext(Dispatchers.Default) { backups.preview(bytes, password) }
            _state.update { it.copy(preview = preview, askPassword = false, passwordError = null) }
        } catch (e: BackupError.PasswordNeeded) {
            _state.update { it.copy(askPassword = true, passwordError = null) }
        } catch (e: BackupError.WrongPassword) {
            _state.update { it.copy(askPassword = true, passwordError = if (askIfNeeded) null else e.message) }
        } catch (e: BackupError) {
            pendingBytes = null
            _messages.send(e.message ?: "Couldn't read that backup")
        }
    }

    fun cancelRestore() {
        pendingBytes = null
        _state.update { it.copy(preview = null, askPassword = false, passwordError = null) }
    }

    fun restore() = work {
        val preview = _state.value.preview ?: return@work
        val result = runCatching { backups.restore(preview.file) }
        pendingBytes = null
        _state.update { it.copy(preview = null) }
        _messages.send(
            result.fold(
                onSuccess = { "Restored · ${it.summary}" },
                onFailure = { "Restore failed — nothing was changed" },
            ),
        )
    }
}
