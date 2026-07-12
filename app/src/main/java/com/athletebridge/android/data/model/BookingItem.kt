package com.athletebridge.android.data.model

import java.util.Date

data class BookingItem(
    val id: String = "",
    val clientID: String = "",
    val clientName: String? = null,
    val coachID: String = "",
    val coachName: String? = null,
    val startAt: Date? = null,
    val endAt: Date? = null,
    val location: String? = null,
    /** Firestore id of the linked Place to Play the booking was made against. */
    val locationPlaceId: String? = null,
    val notes: String? = null,
    val status: String? = null,
    val paymentStatus: String? = null,
    val rateUSD: Double? = null,
    val coachNote: String? = null,
    val rejectionReason: String? = null,
    val isGroupBooking: Boolean? = null,
    val creatorID: String? = null,
    val creatorType: String? = null,
    // Group booking fields
    val clientIDs: List<String>? = null,
    val clientNames: List<String>? = null,
    val coachIDs: List<String>? = null,
    val coachNames: List<String>? = null,
    val coachAcceptances: Map<String, Boolean>? = null,
    val clientConfirmations: Map<String, Boolean>? = null,
    val coachRates: Map<String, Double>? = null,
    val groupConfirmedClients: List<String>? = null,
    val groupDeclinedClients: List<String>? = null,
    val rejectedBy: String? = null,
    val clientDeclineReason: String? = null,
    val requiresPaymentUpfront: Boolean? = null,
    val sessionRecap: String? = null,          // coach-written, client-visible recap
    val recurrenceGroupId: String? = null,     // links bookings created as a weekly series
    val depositForfeited: Boolean? = null      // set when a client cancels inside the coach's window
) {
    val allCoachIDs: List<String>
        get() = coachIDs?.takeIf { it.isNotEmpty() }
            ?: listOfNotNull(coachID.takeIf { it.isNotEmpty() })

    val allCoachNames: List<String>
        get() = coachNames?.takeIf { it.isNotEmpty() }
            ?: listOfNotNull(coachName?.takeIf { it.isNotEmpty() })

    val allClientIDs: List<String>
        get() = clientIDs?.takeIf { it.isNotEmpty() }
            ?: listOfNotNull(clientID.takeIf { it.isNotEmpty() })

    val allClientNames: List<String>
        get() = clientNames?.takeIf { it.isNotEmpty() }
            ?: listOfNotNull(clientName?.takeIf { it.isNotEmpty() })
}
