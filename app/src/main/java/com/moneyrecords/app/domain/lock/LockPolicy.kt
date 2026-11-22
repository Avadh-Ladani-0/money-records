package com.moneyrecords.app.domain.lock

/** App-lock settings. Off by default; unlocking uses the phone's own screen lock. */
data class AppLockPrefs(
    val enabled: Boolean = false,
    /** 0 = lock immediately when the app leaves the screen. */
    val relockAfterSeconds: Int = 60,
    /** Blank preview in recent apps (also blocks screenshots). */
    val hideInRecents: Boolean = true,
) {
    companion object {
        val RELOCK_CHOICES = listOf(0, 30, 60, 300)
    }
}

/** When the app should show the lock screen. */
object LockPolicy {
    /**
     * @param coldStart the app process just started (no previous visit to compare against)
     * @param backgroundedAtMillis when the app last left the screen, or null if it hasn't
     */
    fun shouldLock(prefs: AppLockPrefs, coldStart: Boolean, backgroundedAtMillis: Long?, nowMillis: Long): Boolean {
        if (!prefs.enabled) return false
        if (coldStart) return true
        val since = backgroundedAtMillis ?: return false
        return nowMillis - since >= prefs.relockAfterSeconds * 1_000L
    }

    fun label(seconds: Int): String = when (seconds) {
        0 -> "Immediately"
        in 1..59 -> "$seconds s"
        60 -> "1 min"
        else -> "${seconds / 60} min"
    }
}
