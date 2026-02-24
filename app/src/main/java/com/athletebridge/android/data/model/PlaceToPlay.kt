package com.athletebridge.android.data.model

data class PlaceToPlay(
    val id: String = "",
    val name: String = "",
    val address: String = "",
    val coordinates: Map<String, Double>? = null,
    val weeklySchedule: Map<String, String> = emptyMap(),
    val pricing: String? = null,
    val memberList: List<String> = emptyList(),
    val pendingMembers: List<String> = emptyList(),
    val createdBy: String = "",
    val contactUserId: String? = null
) {
    fun isMember(userId: String) = memberList.contains(userId)
    fun isPending(userId: String) = pendingMembers.contains(userId)
    fun isCreator(userId: String) = createdBy == userId

    val memberCount: Int get() = memberList.size
}
