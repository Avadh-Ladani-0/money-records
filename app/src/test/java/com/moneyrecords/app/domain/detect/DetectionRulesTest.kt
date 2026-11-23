package com.moneyrecords.app.domain.detect

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DetectionRulesTest {
    private val t0 = 1_800_000_000_000L
    private fun d(source: DetectSource, amount: Long, at: Long = t0, dir: Direction = Direction.OUT, ref: String? = null, account: Long? = 1, origin: String = "x") =
        Detected(source = source, origin = origin, direction = dir, amount = amount, accountId = account, ref = ref, occurredAt = at)

    @Test fun `bank sms and app notification for one payment are one item`() {
        val sms = d(DetectSource.SMS, 150_00, ref = "432112345678")
        assertTrue(DetectionRules.sameMovement(sms, d(DetectSource.NOTIFICATION, 150_00, at = t0 + 30_000, account = null)))
        assertTrue(DetectionRules.sameMovement(sms, d(DetectSource.NOTIFICATION, 999_00, at = t0 + 3_600_000, ref = "432112345678")))
        assertFalse(DetectionRules.sameMovement(sms, d(DetectSource.NOTIFICATION, 150_00, at = t0 + 11 * 60_000)))
        assertFalse(DetectionRules.sameMovement(sms, d(DetectSource.NOTIFICATION, 150_00, dir = Direction.IN)))
    }

    @Test fun `two real payments of the same amount from one bank stay separate`() {
        val a = d(DetectSource.SMS, 50_00, ref = "1111111")
        assertFalse(DetectionRules.sameMovement(a, d(DetectSource.SMS, 50_00, at = t0 + 60_000, ref = "2222222")))
    }

    @Test fun `a pay widget tap takes the next payment alert`() {
        val tap = d(DetectSource.PAY_WIDGET, 0)
        assertTrue(DetectionRules.sameMovement(tap, d(DetectSource.NOTIFICATION, 120_00, at = t0 + 90_000)))
        assertFalse(DetectionRules.sameMovement(tap, d(DetectSource.NOTIFICATION, 120_00, at = t0 - 60_000)))
        assertFalse(DetectionRules.sameMovement(tap, d(DetectSource.NOTIFICATION, 120_00, at = t0 + 25 * 60_000)))
        val merged = DetectionRules.merge(tap, d(DetectSource.NOTIFICATION, 120_00, at = t0 + 90_000).copy(counterparty = "Raju"))
        assertEquals(120_00L, merged.amount); assertEquals("Raju", merged.counterparty)
    }

    @Test fun `out of one account and into another is a transfer`() {
        val out = d(DetectSource.SMS, 2_000_00, account = 1)
        assertTrue(DetectionRules.transferPair(out, d(DetectSource.SMS, 2_000_00, at = t0 + 120_000, dir = Direction.IN, account = 2)))
        assertFalse(DetectionRules.transferPair(out, d(DetectSource.SMS, 2_000_00, at = t0 + 120_000, dir = Direction.IN, account = 1)))
    }

    @Test fun `account comes from digits, else from the app`() {
        val digits = mapOf(1L to "1234", 2L to "5678")
        assertEquals(2L, DetectionRules.resolveAccount("5678", "AD-BOBTXN", digits, emptyMap()))
        assertEquals(3L, DetectionRules.resolveAccount(null, "com.google.android.apps.nbu.paisa.user", digits, mapOf("com.google.android.apps.nbu.paisa.user" to 3L)))
        assertEquals(null, DetectionRules.resolveAccount("9999", "AD-HDFC", digits, emptyMap()))
    }

    @Test fun `all of a bank's senders share one key`() {
        val k = com.moneyrecords.app.detect.SmsSources::bankKey
        assertEquals("sms:SBI", k("JD-SBIUPI-S")); assertEquals("sms:SBI", k("JK-ATMSBI-S")); assertEquals("sms:SBI", k("JM-CBSSBI-S"))
        assertEquals("sms:BOB", k("JK-BOBSMS-S")); assertEquals("sms:KCB", k("JX-KCBANK-S")); assertEquals("sms:KCB", k("CP-KCBMSG-S"))
        assertEquals("sms:RZRPAY", k("JX-RZRPAY-S"))
        assertFalse(com.moneyrecords.app.detect.SmsSources.isBankSender("+919876543210"))
    }

    @Test fun `app payments without a bank sms come from UPI Lite when they can`() {
        val gpay = "com.google.android.apps.nbu.paisa.user"
        fun pick(dir: Direction, amount: Long, usual: Long? = 1) =
            DetectionRules.accountForApp(gpay, dir, amount, liteAccountId = 3, liteApp = gpay, linked = setOf(1, 2), usual = usual)
        assertEquals(3L, pick(Direction.OUT, 150_00))        // small, no SMS → UPI Lite
        assertEquals(3L, pick(Direction.OUT, 1_000_00))      // the Lite limit itself
        assertEquals(3L, pick(Direction.OUT, 0))             // pay-widget tap, amount unknown yet
        assertEquals(1L, pick(Direction.OUT, 1_000_01))      // too big for Lite → the usual bank
        assertEquals(1L, pick(Direction.IN, 200_00))         // money never comes into Lite
        assertEquals(1L, pick(Direction.IN, 200_00, usual = null))
        // An app without Lite: always a bank.
        assertEquals(2L, DetectionRules.accountForApp("com.phonepe.app", Direction.OUT, 50_00, 3, gpay, setOf(2), null))
    }

    @Test fun `a bank's messages match the account named after it`() {
        val m = com.moneyrecords.app.detect.SmsSources::nameMatches
        assertTrue(m("sms:SBI", "SBI")); assertTrue(m("sms:SBI", "State Bank savings"))
        assertTrue(m("sms:KCB", "Kalupur")); assertTrue(m("sms:BOB", "BOB"))
        assertFalse(m("sms:SBI", "BOB")); assertFalse(m("sms:RZRPAY", "SBI"))
    }
}
