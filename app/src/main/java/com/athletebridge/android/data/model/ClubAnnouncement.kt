package com.athletebridge.android.data.model

import java.util.Date

data class ClubAnnouncement(
    val id: String = "",
    val placeId: String = "",
    val title: String = "",
    val body: String = "",
    val createdAt: Date? = null,
    val createdBy: String = ""
)
