package com.athletebridge.android.data.model

import java.util.Date

data class StringerOrder(
    val id: String = "",
    val stringerId: String = "",
    val stringerName: String = "",
    val buyerId: String = "",
    val buyerName: String = "",
    val selectedString: String = "",
    val stringCost: Double = 0.0,
    val laborCost: Double = 0.0,
    val status: String = "placed",
    val createdAt: Date? = null,
    val notes: String? = null
) {
    val total: Double get() = stringCost + laborCost

    val statusDisplayName: String
        get() = when (status.lowercase()) {
            "placed" -> "Placed"
            "accepted" -> "Accepted"
            "stringing" -> "Stringing"
            "completed" -> "Completed"
            "declined" -> "Declined"
            else -> status.replaceFirstChar { it.uppercase() }
        }

    val isActive: Boolean
        get() = status.lowercase() in listOf("placed", "accepted", "stringing")

    val isPending: Boolean
        get() = status.lowercase() == "placed"
}
