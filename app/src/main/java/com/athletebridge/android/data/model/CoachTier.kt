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

    companion object {
        fun fromString(value: String): CoachTier {
            return entries.find { it.value == value.lowercase() } ?: FREE
        }
    }
}
