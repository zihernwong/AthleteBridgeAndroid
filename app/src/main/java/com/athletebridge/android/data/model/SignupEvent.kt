package com.athletebridge.android.data.model

import java.util.Date

/** One registration inside a signup event (matches iOS SignupEventSignup — map entry under "signups"). */
data class EventSignup(
    val id: String = "",        // map key
    val name: String = "",
    val email: String = "",
    val userId: String? = null, // null for web signups
    val signedUpAt: Date? = null,
    val paid: Boolean = false,
    // Player tapped "I've Paid" — pending the organizer's confirmation
    val selfPaid: Boolean = false
)

/**
 * Stored in the root "signupEvents" collection with iOS field names
 * (eventDate, maxSignups, signupCount, signups map) so events are shared cross-platform.
 */
data class SignupEvent(
    val id: String = "",
    val placeId: String = "",
    val placeName: String = "",
    val title: String = "",
    val description: String? = null,
    val eventDate: Date? = null,
    val location: String = "",
    val maxSignups: Int = 0,
    val signupCount: Int = 0,
    val signups: List<EventSignup> = emptyList(),
    val waitlist: List<EventSignup> = emptyList(),
    val shareableURL: String? = null,
    val createdBy: String = "",
    // "weekly" for repeating events; the creator can roll the next occurrence forward
    val recurrence: String? = null,
    // Optional per-player fee and where to pay it (e.g. a Stripe payment link)
    val feeUSD: Double? = null,
    val paymentLink: String? = null,
    // Organizer setting: players may mark themselves paid (pending confirmation)
    val allowSelfReportPaid: Boolean = false
) {
    val isRecurringWeekly: Boolean get() = recurrence == "weekly"
    val paidCount: Int get() = signups.count { it.paid }
    val selfReportedCount: Int get() = signups.count { it.selfPaid && !it.paid }

    /**
     * Payment URL players tap. Venmo destinations (an @handle or venmo.com link)
     * get the fee amount and event title prefilled; other links open unchanged.
     */
    val resolvedPaymentURL: String?
        get() {
            val raw = paymentLink?.trim().takeUnless { it.isNullOrEmpty() } ?: return null
            val handle = venmoHandle(raw)
            val fee = feeUSD
            if (handle != null && fee != null && fee > 0) {
                val amount = String.format(java.util.Locale.US, "%.2f", fee)
                val note = java.net.URLEncoder.encode(title, "UTF-8").replace("+", "%20")
                return "https://venmo.com/${java.net.URLEncoder.encode(handle, "UTF-8")}?txn=pay&amount=$amount&note=$note"
            }
            // A bare "@handle" isn't a URL ("https://@handle" goes nowhere); open the Venmo profile instead
            if (handle != null && raw.startsWith("@")) {
                return "https://venmo.com/u/${java.net.URLEncoder.encode(handle, "UTF-8")}"
            }
            return if (raw.lowercase().startsWith("http")) raw else "https://$raw"
        }

    companion object {
        /** Venmo paths that are app pages, not usernames (e.g. QR/share links: venmo.com/code?user_id=…). */
        private val VENMO_RESERVED_PATHS = setOf("code", "u", "pay", "account", "signup", "login", "settings")

        /** "@handle", "venmo.com/handle", or "venmo.com/u/handle" → "handle"; null for non-Venmo links. */
        fun venmoHandle(raw: String): String? {
            if (raw.startsWith("@")) return raw.drop(1).takeIf { it.isNotBlank() }
            if (!raw.lowercase().contains("venmo.com")) return null
            val last = raw.substringBefore("?").split("/").filter { it.isNotEmpty() }.lastOrNull() ?: return null
            if (last.lowercase().contains("venmo.com")) return null
            if (last.lowercase() in VENMO_RESERVED_PATHS) return null
            return last.removePrefix("@")
        }
    }
    val spotsRemaining: Int get() = (maxSignups - signupCount).coerceAtLeast(0)
    val isFull: Boolean get() = maxSignups > 0 && signupCount >= maxSignups
    fun isRegistered(userId: String) = signups.any { it.userId == userId }
    fun isWaitlisted(userId: String) = waitlist.any { it.userId == userId }
    /** 1-based position in the waitlist queue, or null if not waitlisted. */
    fun waitlistPosition(userId: String): Int? =
        waitlist.sortedBy { it.signedUpAt ?: java.util.Date(0) }
            .indexOfFirst { it.userId == userId }
            .takeIf { it >= 0 }?.plus(1)
    val shareURL: String get() = shareableURL ?: "https://athletesbridge.com/signup/?event=$id"
}
