package com.athletebridge.android.data.model

import com.athletebridge.android.testutil.withDefaults
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Date
import java.util.Locale

class SignupEventTest {

    private fun signup(id: String, userId: String?, at: Long? = null, paid: Boolean = false, selfPaid: Boolean = false) =
        EventSignup(id = id, name = id, userId = userId, signedUpAt = at?.let { Date(it) }, paid = paid, selfPaid = selfPaid)

    // ── Venmo handle extraction ─────────────────────────────────────────────

    @Test
    fun `venmoHandle understands the handle and profile link formats`() {
        assertEquals("john", SignupEvent.venmoHandle("@john"))
        assertEquals("john", SignupEvent.venmoHandle("venmo.com/john"))
        assertEquals("john", SignupEvent.venmoHandle("https://venmo.com/u/john"))
        assertEquals("john", SignupEvent.venmoHandle("https://account.venmo.com/u/john?utm=share"))
        assertEquals("john", SignupEvent.venmoHandle("https://venmo.com/john/"))
    }

    @Test
    fun `venmoHandle ignores links that are not Venmo`() {
        assertNull(SignupEvent.venmoHandle("https://buy.stripe.com/abc123"))
        assertNull(SignupEvent.venmoHandle("paypal.me/john"))
        assertNull(SignupEvent.venmoHandle("venmo.com"))
        assertNull(SignupEvent.venmoHandle("https://venmo.com/"))
    }

    @Test
    fun `venmoHandle does not mistake a Venmo QR share link for a username`() {
        // Venmo's "Share profile" / QR links look like venmo.com/code?user_id=... — "code" is not the user.
        assertNull(SignupEvent.venmoHandle("https://venmo.com/code?user_id=1234567890123456789"))
    }

    // ── Payment URL resolution ──────────────────────────────────────────────

    @Test
    fun `Venmo handle with a fee gets the amount and an encoded note prefilled`() {
        val event = SignupEvent(title = "Open Play & Drills", feeUSD = 12.5, paymentLink = "@egret-club")
        assertEquals(
            "https://venmo.com/egret-club?txn=pay&amount=12.50&note=Open%20Play%20%26%20Drills",
            event.resolvedPaymentURL
        )
    }

    @Test
    fun `fee amount always uses a dot decimal even on a German phone`() {
        withDefaults("Europe/Berlin", Locale.GERMANY) {
            val event = SignupEvent(title = "Training", feeUSD = 7.0, paymentLink = "@club")
            assertTrue(event.resolvedPaymentURL!!.contains("amount=7.00"))
        }
    }

    @Test
    fun `non Venmo links open unchanged and bare domains get https`() {
        assertEquals("https://buy.stripe.com/x", SignupEvent(feeUSD = 10.0, paymentLink = "https://buy.stripe.com/x").resolvedPaymentURL)
        assertEquals("https://paypal.me/club", SignupEvent(feeUSD = 10.0, paymentLink = "paypal.me/club").resolvedPaymentURL)
    }

    @Test
    fun `blank payment link resolves to nothing`() {
        assertNull(SignupEvent(paymentLink = "   ").resolvedPaymentURL)
        assertNull(SignupEvent(paymentLink = null).resolvedPaymentURL)
    }

    @Test
    fun `Venmo handle without a fee still opens a valid Venmo profile`() {
        // Organizer set "@egret-club" as the payment link but no per-player fee.
        val url = SignupEvent(title = "Open Play", feeUSD = null, paymentLink = "@egret-club").resolvedPaymentURL
        assertTrue("got $url", url!!.startsWith("https://venmo.com/"))
        assertTrue("got $url", url.contains("egret-club"))
    }

    // ── Capacity, waitlist, payments ────────────────────────────────────────

    @Test
    fun `capacity math`() {
        val event = SignupEvent(maxSignups = 10, signupCount = 7)
        assertEquals(3, event.spotsRemaining)
        assertFalse(event.isFull)
        assertTrue(SignupEvent(maxSignups = 10, signupCount = 10).isFull)
        assertEquals(0, SignupEvent(maxSignups = 10, signupCount = 12).spotsRemaining)
    }

    @Test
    fun `waitlist position is 1-based and ordered by join time`() {
        val event = SignupEvent(
            waitlist = listOf(signup("w3", "u3", at = 3_000), signup("w1", "u1", at = 1_000), signup("w2", "u2", at = 2_000))
        )
        assertEquals(1, event.waitlistPosition("u1"))
        assertEquals(2, event.waitlistPosition("u2"))
        assertEquals(3, event.waitlistPosition("u3"))
        assertNull(event.waitlistPosition("nobody"))
        assertTrue(event.isWaitlisted("u2"))
    }

    @Test
    fun `registration and payment counts`() {
        val event = SignupEvent(
            signups = listOf(
                signup("a", "u1", paid = true),
                signup("b", "u2", selfPaid = true),
                signup("c", "u3", paid = true, selfPaid = true),
                signup("d", null) // web signup, no account
            )
        )
        assertTrue(event.isRegistered("u2"))
        assertFalse(event.isRegistered("u9"))
        assertEquals(2, event.paidCount)
        assertEquals(1, event.selfReportedCount) // self-reported but not yet confirmed
    }

    @Test
    fun `share URL falls back to the web signup page`() {
        assertEquals("https://athletesbridge.com/signup/?event=e1", SignupEvent(id = "e1").shareURL)
        assertEquals("https://x.test/e", SignupEvent(id = "e1", shareableURL = "https://x.test/e").shareURL)
    }

    @Test
    fun `weekly recurrence flag`() {
        assertTrue(SignupEvent(recurrence = "weekly").isRecurringWeekly)
        assertFalse(SignupEvent(recurrence = null).isRecurringWeekly)
    }
}
