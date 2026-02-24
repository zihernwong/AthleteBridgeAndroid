package com.athletebridge.android.data.model

enum class CoachTier(val value: String) {
    FREE("free"),
    PLUS("plus"),
    PRO("pro");

    val displayName: String
        get() = when (this) {
            FREE -> "Coach Free"
            PLUS -> "Coach Plus"
            PRO -> "Coach Pro"
        }

    fun hasAccess(feature: String): Boolean = when (feature) {
        "revenue_insights" -> this == PLUS || this == PRO
        "listing_visibility" -> this == PRO
        else -> true
    }

    companion object {
        fun fromString(value: String): CoachTier {
            return entries.find { it.value == value.lowercase() } ?: FREE
        }
    }
}
