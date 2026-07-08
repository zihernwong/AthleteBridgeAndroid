package com.athletebridge.android.data.model

data class Client(
    val id: String = "",
    val name: String = "",
    val goals: List<String> = emptyList(),
    val preferredAvailability: List<String> = emptyList(),
    val meetingPreference: String? = null,
    val skillLevel: String? = null,
    val zipCode: String? = null,
    val city: String? = null,
    val bio: String? = null,
    val tournamentSoftwareLink: String? = null,
    val phoneVerified: Boolean = false,
    val phoneNumber: String? = null
) {
    val hasValidName: Boolean get() = name.isNotBlank()
}
