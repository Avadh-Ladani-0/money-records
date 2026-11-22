package com.moneyrecords.app.security

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.moneyrecords.app.domain.lock.AppLockPrefs
import com.moneyrecords.app.domain.lock.LockPolicy
import com.moneyrecords.app.domain.repository.UserPrefsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton

/** Fingerprint/face, or the phone's PIN/pattern/password. */
private const val AUTHENTICATORS = BIOMETRIC_WEAK or DEVICE_CREDENTIAL

/** Whether the phone has a screen lock (or biometrics) the app can use. */
fun canUseScreenLock(context: Context): Boolean =
    BiometricManager.from(context).canAuthenticate(AUTHENTICATORS) == BiometricManager.BIOMETRIC_SUCCESS

/**
 * Shows Android's unlock prompt (fingerprint/face or the phone's PIN/pattern).
 * [onResult] gets true on success, false if cancelled or failed.
 */
fun authenticate(activity: FragmentActivity, title: String, subtitle: String? = null, onResult: (Boolean) -> Unit) {
    val prompt = BiometricPrompt(
        activity,
        ContextCompat.getMainExecutor(activity),
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onResult(true)
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) = onResult(false)
            // onAuthenticationFailed = one bad attempt; the prompt stays open, so nothing to do.
        },
    )
    val info = BiometricPrompt.PromptInfo.Builder()
        .setTitle(title)
        .apply { if (subtitle != null) setSubtitle(subtitle) }
        .setAllowedAuthenticators(AUTHENTICATORS)
        .build()
    prompt.authenticate(info)
}

/**
 * Decides when the app is locked. Locks on a fresh start and after the chosen time away; the unlock
 * prompt itself (which briefly covers the app) never counts as "away".
 */
@Singleton
class AppLockManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val prefs: UserPrefsRepository,
    private val clock: Clock,
) : DefaultLifecycleObserver {
    // Locked until settings are known, so content never flashes before the lock screen.
    private val _locked = MutableStateFlow(true)
    val locked: StateFlow<Boolean> = _locked.asStateFlow()

    private val _settings = MutableStateFlow(AppLockPrefs())
    val settings: StateFlow<AppLockPrefs> = _settings.asStateFlow()

    private var coldStart = true
    private var backgroundedAt: Long? = null
    /** True while our own unlock prompt is showing. */
    @Volatile var authenticating = false
        private set

    fun start(scope: CoroutineScope) {
        scope.launch {
            var first = true
            prefs.observeAppLock().collect { s ->
                _settings.value = s
                if (!s.enabled) _locked.value = false
                else if (first) _locked.value = LockPolicy.shouldLock(s, coldStart = true, backgroundedAtMillis = null, nowMillis = clock.millis())
                first = false
            }
        }
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
    }

    override fun onStop(owner: LifecycleOwner) {
        if (authenticating) return
        coldStart = false
        backgroundedAt = clock.millis()
    }

    override fun onStart(owner: LifecycleOwner) {
        if (authenticating) return
        if (LockPolicy.shouldLock(_settings.value, coldStart = false, backgroundedAtMillis = backgroundedAt, nowMillis = clock.millis())) {
            _locked.value = true
        }
        backgroundedAt = null
    }

    /**
     * Runs the unlock prompt and unlocks on success. [force] (the Unlock button) always opens a new
     * prompt: Android can cancel a prompt without a callback (e.g. opening recent apps mid-prompt),
     * which would otherwise leave [authenticating] stuck and the button dead.
     */
    fun promptUnlock(activity: FragmentActivity, force: Boolean = false) {
        if (authenticating && !force) return
        authenticating = true
        authenticate(activity, "Unlock MoneyRecords", "Use your fingerprint, face or phone screen lock") { ok ->
            authenticating = false
            backgroundedAt = null
            coldStart = false
            if (ok) _locked.value = false
        }
    }

    /** For settings changes that must be confirmed with the screen lock (turning the lock on/off). */
    fun confirm(activity: FragmentActivity, title: String, onResult: (Boolean) -> Unit) {
        authenticating = true
        authenticate(activity, title) { ok ->
            authenticating = false
            backgroundedAt = null
            onResult(ok)
        }
    }

    suspend fun update(change: (AppLockPrefs) -> AppLockPrefs) {
        prefs.updateAppLock(change)
        // Turning the lock on doesn't lock you out right now; it applies from the next time away.
        if (!_settings.value.enabled) _locked.value = false
    }
}
