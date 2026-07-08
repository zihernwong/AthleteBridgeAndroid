package com.athletebridge.android.data.model

data class DayAvailability(
    val dayOfWeek: Int, // 1=Monday, 7=Sunday
    val startHour: Int,
    val startMinute: Int,
    val endHour: Int,
    val endMinute: Int,
    val enabled: Boolean = true
)

data class AvailabilityTemplate(
    val id: String = "",
    val coachId: String = "",
    val slots: List<DayAvailability> = emptyList()
)
