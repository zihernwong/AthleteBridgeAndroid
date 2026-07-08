package com.athletebridge.android.data.model

import java.util.Date

data class AwayTime(
    val id: String = "",
    val coachId: String = "",
    val startAt: Date? = null,
    val endAt: Date? = null,
    val notes: String? = null
)
