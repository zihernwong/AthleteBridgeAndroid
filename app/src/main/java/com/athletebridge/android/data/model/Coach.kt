package com.athletebridge.android.data.model

data class Coach(
    val id: String = "",
    val name: String = "",
    val specialties: List<String> = emptyList(),
    val experienceYears: Int = 0,
    val availability: List<String> = emptyList(),
    val bio: String? = null,
    val hourlyRate: Double? = null,
    val tournamentSoftwareLink: String? = null,
    val photoURLString: String? = null,
    val meetingPreference: String? = null,
    val zipCode: String? = null,
    val city: String? = null,
    val payments: Map<String, String>? = null,
    val rateRange: List<Double>? = null,
    val subscriptionTier: CoachTier = CoachTier.FREE,
    val linkedPlaceIds: List<String> = emptyList(),
    val phoneVerified: Boolean = false,
    val phoneNumber: String? = null,
    // Client cancellations within this many hours of the session forfeit the deposit (0 = no policy)
    val cancellationWindowHours: Int = 0
) {
    val hasValidName: Boolean get() = name.isNotBlank()

    val minimumRate: Double?
        get() = rateRange?.firstOrNull() ?: hourlyRate

    val maximumRate: Double?
        get() = if (rateRange != null && rateRange.size >= 2) rateRange[1]
                else rateRange?.firstOrNull() ?: hourlyRate
}
