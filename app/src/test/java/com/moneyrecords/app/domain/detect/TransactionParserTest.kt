package com.moneyrecords.app.domain.detect

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Common published alert formats (names, numbers and refs are made up). Real masked samples go below. */
class TransactionParserTest {
    private fun p(text: String) = TransactionParser.parse(text)

    @Test fun `sbi upi debit without a currency sign`() {
        val t = p("Dear UPI user A/C X1234 debited by 150.0 on date 23Nov26 trf to RAJU KUMAR Refno 432112345678. If not u? call 1800111109. -SBI")!!
        assertEquals(Direction.OUT, t.direction)
        assertEquals(150_00L, t.amount)
        assertEquals("1234", t.accountDigits)
        assertEquals("432112345678", t.ref)
        assertEquals("Raju Kumar", t.counterparty)
    }

    @Test fun `sbi upi credit`() {
        val t = p("Dear SBI UPI User, ur A/cX1234 credited by Rs500 on 23Nov26 by (Ref no 432112345679)")!!
        assertEquals(Direction.IN, t.direction)
        assertEquals(500_00L, t.amount)
        assertEquals("1234", t.accountDigits)
        assertEquals("432112345679", t.ref)
    }

    @Test fun `bob debit and credit`() {
        val d = p("Rs.1,250.50 debited from A/C XXXXXX5678 on 23-11-26. UPI Ref:432198765432. Not you? Call 18005700-BOB")!!
        assertEquals(Direction.OUT, d.direction); assertEquals(1_250_50L, d.amount); assertEquals("5678", d.accountDigits)
        val c = p("Rs.500.00 Credited to A/c ...5678 thru UPI/432198765433 by rahul@okaxis. Total Bal:Rs.10000.00CR -BOB")!!
        assertEquals(Direction.IN, c.direction); assertEquals(500_00L, c.amount); assertEquals("rahul@okaxis", c.counterparty)
    }

    @Test fun `payment app notifications`() {
        val paid = p("Paid ₹150 to Raju Tea Stall")!!
        assertEquals(Direction.OUT, paid.direction); assertEquals("Raju Tea Stall", paid.counterparty)
        val got = p("Rahul Shah paid you ₹200")!!
        assertEquals(Direction.IN, got.direction); assertEquals(200_00L, got.amount); assertEquals("Rahul Shah", got.counterparty)
        val recv = p("Received ₹300 from Amit")!!
        assertEquals(Direction.IN, recv.direction); assertEquals("Amit", recv.counterparty)
    }

    @Test fun `upi lite top up is recognised`() {
        val t = p("Dear UPI user A/C X1234 debited by 2000.0 on date 23Nov26 trf to UPI LITE Refno 432100000001 -SBI")!!
        assertTrue(t.upiLite)
        assertEquals(Direction.OUT, t.direction)
    }

    @Test fun `refunds and reversals come in`() {
        val t = p("Rs.150.00 reversed to your A/c XX1234 for failed txn UPI Ref 432100000002")!!
        assertEquals(Direction.IN, t.direction)
        assertTrue(t.reversal)
    }

    @Test fun `not transactions are ignored`() {
        assertNull(p("123456 is your OTP for a transaction of Rs.500 at Amazon. Do not share."))
        assertNull(p("Your credit card bill of Rs.4,500 is due on 05-12-26. Pay now."))
        assertNull(p("Rs.999 will be debited on 01-12-26 for your Netflix e-mandate."))
        assertNull(p("Rahul has requested money Rs 200 on Google Pay."))
        assertNull(p("Get flat cashback upto Rs 100 on your next payment!"))
        assertNull(p("Payment of Rs 150 to Raju failed. Money will be refunded if debited."))
        assertNull(p("Hi, see you tomorrow at 6"))
    }

    // ── Shapes of the user's real bank SMS (23 Nov), with made-up numbers and names ──

    @Test fun `sbi upi lite top up has no debit word`() {
        val t = p("Dear User UPI LITE Top-up on SBI Rs.2000.00 is successful. Ref No 432112340000. If not done by u, call 1800111109 -SBI")!!
        assertEquals(Direction.OUT, t.direction); assertEquals(2_000_00L, t.amount); assertTrue(t.upiLite)
        assertEquals("432112340000", t.ref); assertNull(t.counterparty)
    }

    @Test fun `sbi credit to your account is money in`() {
        val t = p("Dear Customer, Payment of Rs. 1500.00 credited to your Acc No. XXXXX1234 on 21/11/26-SBI")!!
        assertEquals(Direction.IN, t.direction); assertEquals(1_500_00L, t.amount); assertEquals("1234", t.accountDigits)
    }

    @Test fun `sbi upi reversal and deemed reversal credits`() {
        val r = p("Your A/C XXXXX1234 has credit for UPI/REVERSAL/432112345678/RAJU of Rs 150.00 on 22/11/26. Avl Bal Rs 2,345.67.-SBI")!!
        assertEquals(Direction.IN, r.direction); assertTrue(r.reversal); assertEquals(150_00L, r.amount)
        val drc = p("Your A/C XXXXX1234 has credit for UPI/DRC/432112345678/ABC/ of Rs 99.00 on 22/11/26. Avl Bal Rs 2,345.67.-SBI")!!
        assertEquals(Direction.IN, drc.direction); assertTrue(drc.reversal)
    }

    @Test fun `sbi atm withdrawal`() {
        val t = p("Dear SBI Customer, Rs.500 withdrawn at PNB ATM T1234567 from A/cX1234 on 05Oct26 Transaction Number 1234. Available Balance Rs.1000.00. If not withdrawn by you, forward this SMS to 9223008333 / call 1800111109 or 09449112211 to block your card. Call 1800111109 if cash not received.")!!
        assertEquals(Direction.OUT, t.direction); assertEquals(500_00L, t.amount); assertEquals("1234", t.accountDigits)
        assertEquals("ATM withdrawal", t.counterparty)
    }

    @Test fun `kalupur debit and credit`() {
        val d = p("A/C XX5678 IS DEBITED BY RS.250.00 ON 20-11-26 BY UPI.BAL RS.4000.00 BAL WITH FFD RS.4000.00.INFO: SWIGGY  - KCCBL")!!
        assertEquals(Direction.OUT, d.direction); assertEquals(250_00L, d.amount); assertEquals("5678", d.accountDigits)
        assertEquals("Swiggy", d.counterparty)
        val c = p("A/C XX5678 IS CREDITED BY RS.12.00 ON 20-11-26 BY INTEREST. AVL BAL IS RS.4012.00.INFO: 1:Int.Pd:01-08-26  to 31-10-26 -KCCBL")!!
        assertEquals(Direction.IN, c.direction); assertEquals(12_00L, c.amount); assertEquals("Interest", c.counterparty)
        val own = p("A/c XX5678 debited by Rs.1000.00 on 20-11-26 and credited to a/c XX1234 (RefNo 432112349999).Not you? forward this SMS to 9999999999 to block UPI - KCCBL")!!
        assertEquals(Direction.OUT, own.direction); assertEquals("5678", own.accountDigits); assertEquals("432112349999", own.ref)
    }

    @Test fun `bob upi credit`() {
        val t = p("Dear BOB UPI User: Your account is credited with INR 300.00 on 21-11-2026 08:15:00 PM by RAHUL SHAH Ref No 432100001111; AvlBal: Rs5000.00 - BOB")!!
        assertEquals(Direction.IN, t.direction); assertEquals(300_00L, t.amount); assertEquals("Rahul Shah", t.counterparty)
    }

    @Test fun `salary by neft`() {
        val t = p("Dear Customer, INR 45,000.00 credited to your A/c No XX1234 on 01/11/26 through NEFT with UTR IDFB12345 by ACME PVT LTD PAYROLL, INFO: SALARY OCT-SBI")!!
        assertEquals(Direction.IN, t.direction); assertEquals(45_000_00L, t.amount); assertEquals("1234", t.accountDigits)
    }

    @Test fun `account notices from the same banks are ignored`() {
        assertNull(p("UPI AutoPay for netflix@okaxis debit of Rs.199.00 is scheduled on 01/12/26, abc@okaxis. Please ensure sufficient balance in your account. - KCCBL"))
        assertNull(p("Your UPI-Mandate is successfully cancelled towards Crunchyroll for 99.00 from A/c No.XXXXXX1234. UMN:abc@okicici -SBI"))
        assertNull(p("22/11/26, 10:00 Avail Bal in A/c xxx1234: Rs. 100.00 CR 21/11/26: 50.00 DR,TO TRFR -SBI"))
        assertNull(p("Dear SBI Customer, transaction dated 05-Sep-26 10:00:00 through card ending 1234 has been declined due to insufficient funds."))
        assertNull(p("Dear Customer,you have done 3 out of 5 free transactions at Non-SBI ATM in India this month."))
        assertNull(p("Dear Customer, PIN for your MasterCard ending with 1234 has been set. YONO SBI"))
    }
}
