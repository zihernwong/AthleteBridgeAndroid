package com.athletebridge.android.data.model

import java.util.Date

data class TournamentParticipantInfo(
    val gender: String = "",
    val events: List<String> = emptyList(),
    val skillLevels: List<String> = emptyList()
)

data class Tournament(
    val id: String = "",
    val name: String = "",
    val startDate: Date = Date(),
    val endDate: Date = Date(),
    val location: String = "",
    val createdBy: String = "",
    val signupLink: String? = null,
    val participants: Map<String, TournamentParticipantInfo> = emptyMap(),
    // Link to a webTournaments doc managed by the web tournament manager (matches iOS)
    val webTournamentId: String? = null
)
