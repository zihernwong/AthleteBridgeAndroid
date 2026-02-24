package com.athletebridge.android.data.model

data class PlayerToPlayWith(
    val id: String = "",
    val userId: String = "",
    val name: String = "",
    val photoURLString: String? = null,
    val skillLevel: String = "",
    val city: String = "",
    val availability: List<String> = emptyList(),
    val linkedVenueIds: List<String> = emptyList()
)
