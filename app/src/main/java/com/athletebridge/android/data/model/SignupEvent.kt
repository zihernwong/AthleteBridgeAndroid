package com.athletebridge.android.data.model

import java.util.Date

/** One registration inside a signup event (matches iOS SignupEventSignup — map entry under "signups"). */
data class EventSignup(
    val id: String = "",        // map key
    val name: String = "",
    val email: String = "",
    val userId: String? = null, // null for web signups
    val signedUpAt: Date? = null,
    val paid: Boolean = false
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
    val paymentLink: String? = null
) {
    val isRecurringWeekly: Boolean get() = recurrence == "weekly"
    val paidCount: Int get() = signups.count { it.paid }
    val spotsRemaining: Int get() = (maxSignups - signupCount).coerceAtLeast(0)
    val isFull: Boolean get() = maxSignups > 0 && signupCount >= maxSignups
    fun isRegistered(userId: String) = signups.any { it.userId == userId }
    fun isWaitlisted(userId: String) = waitlist.any { it.userId == userId }
    /** 1-based position in the waitlist queue, or null if not waitlisted. */
    fun waitlistPosition(userId: String): Int? =
        waitlist.sortedBy { it.signedUpAt ?: java.util.Date(0) }
            .indexOfFirst { it.userId == userId }
            .takeIf { it >= 0 }?.plus(1)
    val shareURL: String get() = shareableURL ?: "https://athletebridge-63176.web.app/signup/?event=$id"
}
