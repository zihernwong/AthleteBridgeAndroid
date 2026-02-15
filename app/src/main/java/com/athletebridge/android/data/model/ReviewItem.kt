package com.athletebridge.android.data.model

import java.util.Date

data class ReviewItem(
    val id: String = "",
    val reviewerId: String = "",
    val reviewerName: String = "",
    val coachId: String = "",
    val rating: Int = 0,
    val comment: String = "",
    val createdAt: Date? = null
)
