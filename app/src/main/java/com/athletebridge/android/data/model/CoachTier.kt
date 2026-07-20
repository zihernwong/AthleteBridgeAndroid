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

    val rank: Int
        get() = when (this) {
            FREE -> 0
            PLUS -> 1
            PRO -> 2
        }

    fun hasAccess(feature: String): Boolean = when (feature) {
        // iOS feature keys kept in sync with CoachTier.hasAccess in Models.swift
        "revenue_insights", "paymentSummary", "earningsForecaster", "metricHistory", "recommendToClients" ->
            this == PLUS || this == PRO
        "listing_visibility" -> this == PRO
        else -> true
    }

    companion object {
        fun fromString(value: String): CoachTier {
            return entries.find { it.value == value.lowercase() } ?: FREE
        }

        /**
         * Effective tier for a coach doc: the StoreKit/Play-synced subscriptionTier
         * or a manually granted lifetimeTier, whichever is higher. lifetimeTier is
         * never touched by billing sync, so comp accounts keep access permanently.
         */
        fun effective(subscriptionRaw: String?, lifetimeRaw: String?): CoachTier {
            val sub = fromString(subscriptionRaw ?: "free")
            val life = fromString(lifetimeRaw ?: "free")
            return if (life.rank > sub.rank) life else sub
        }
    }
}
