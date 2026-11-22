package com.moneyrecords.app.domain.lock

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LockPolicyTest {
    private val on = AppLockPrefs(enabled = true, relockAfterSeconds = 60)
    private val now = 1_000_000L

    @Test fun `never locks when turned off`() {
        assertFalse(LockPolicy.shouldLock(AppLockPrefs(enabled = false), coldStart = true, backgroundedAtMillis = 0, nowMillis = now))
    }

    @Test fun `locks on a fresh start`() {
        assertTrue(LockPolicy.shouldLock(on, coldStart = true, backgroundedAtMillis = null, nowMillis = now))
    }

    @Test fun `re-locks only after the chosen time away`() {
        assertFalse(LockPolicy.shouldLock(on, false, backgroundedAtMillis = now - 59_000, nowMillis = now))
        assertTrue(LockPolicy.shouldLock(on, false, backgroundedAtMillis = now - 60_000, nowMillis = now))
        assertFalse(LockPolicy.shouldLock(on, false, backgroundedAtMillis = null, nowMillis = now))
        val immediately = on.copy(relockAfterSeconds = 0)
        assertTrue(LockPolicy.shouldLock(immediately, false, backgroundedAtMillis = now - 1, nowMillis = now))
        val fiveMin = on.copy(relockAfterSeconds = 300)
        assertFalse(LockPolicy.shouldLock(fiveMin, false, backgroundedAtMillis = now - 299_000, nowMillis = now))
    }

    @Test fun `labels`() {
        assertEquals(listOf("Immediately", "30 s", "1 min", "5 min"), AppLockPrefs.RELOCK_CHOICES.map(LockPolicy::label))
    }
}
