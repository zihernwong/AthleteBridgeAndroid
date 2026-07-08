package com.athletebridge.android.data.model

import java.util.Date

data class StringerOrder(
    val id: String = "",
    val stringerId: String = "",       // Firestore doc ID of the stringer
    val stringerUserId: String = "",   // auth UID of the stringer (for notifications)
    val stringerName: String = "",
    val buyerId: String = "",
    val buyerName: String = "",
    val racketName: String = "",
    val hasOwnString: Boolean = false,
    val selectedString: String = "",
    val timelinePreference: String = "No rush",
    val tension: Int = 0,
    val stringCost: Double = 0.0,
    val laborCost: Double = 0.0,
    val status: String = "placed",
    val createdAt: Date? = null,
    val notes: String? = null,
    val statusHistory: Map<String, Date> = emptyMap()
) {
    val total: Double get() = stringCost + laborCost

    /** Ordered pipeline stages for the timeline (declined orders show no pipeline). */
    val timelineStages: List<String>
        get() = listOf("placed", "accepted", "stringing", "ready_for_pickup", "picked_up")

    fun stageTimestamp(stage: String): Date? =
        if (stage == "placed") statusHistory[stage] ?: createdAt else statusHistory[stage]

    fun isStageReached(stage: String): Boolean {
        val currentIdx = timelineStages.indexOf(status.lowercase())
        val stageIdx = timelineStages.indexOf(stage)
        if (stageIdx < 0) return false
        if (currentIdx >= 0) return stageIdx <= currentIdx
        // completed counts as everything done
        return status.lowercase() == "completed"
    }

    val statusDisplayName: String
        get() = when (status.lowercase()) {
            "placed" -> "Placed"
            "accepted" -> "Accepted"
            "stringing" -> "Stringing"
            "ready_for_pickup" -> "Ready For Pickup"
            "picked_up" -> "Picked Up"
            "completed" -> "Completed"
            "declined" -> "Declined"
            else -> status.replace("_", " ").split(" ")
                .joinToString(" ") { it.lowercase().replaceFirstChar { c -> c.uppercase() } }
        }

    val isActive: Boolean
        get() = status.lowercase() in listOf("placed", "accepted", "stringing", "ready_for_pickup")

    val isPending: Boolean
        get() = status.lowercase() == "placed"
}
