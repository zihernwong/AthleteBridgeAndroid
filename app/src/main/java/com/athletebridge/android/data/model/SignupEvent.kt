package com.athletebridge.android.data.model

import java.util.Date

data class SignupEvent(
    val id: String = "",
    val placeId: String = "",
    val title: String = "",
    val description: String? = null,
    val date: Date? = null,
    val capacity: Int = 0,
    val registeredUserIds: List<String> = emptyList(),
    val shareableURL: String? = null,
    val createdBy: String = ""
) {
    val spotsRemaining: Int get() = (capacity - registeredUserIds.size).coerceAtLeast(0)
    val isFull: Boolean get() = capacity > 0 && registeredUserIds.size >= capacity
    fun isRegistered(userId: String) = registeredUserIds.contains(userId)
}
