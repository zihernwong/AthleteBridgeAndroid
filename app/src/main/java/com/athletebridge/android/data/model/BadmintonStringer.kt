package com.athletebridge.android.data.model

data class BadmintonStringer(
    val id: String = "",
    val userId: String = "",
    val name: String = "",
    val photoURLString: String? = null,
    val strings: Map<String, Double> = emptyMap(),
    val laborCost: Double = 0.0,
    val meetupLocation: String = "",
    val meetupCoordinates: Map<String, Double>? = null,
    val isVerified: Boolean = false
) {
    val totalBasePrice: Double
        get() = laborCost

    val stringOptions: List<String>
        get() = strings.keys.sorted()
}
