package com.athletebridge.android.data.model

/** A member or pending member of a club, keyed by uid with the display name iOS stores in the map entry. */
data class ClubMember(
    val id: String = "",
    val name: String = ""
)

data class PlaceToPlay(
    val id: String = "",
    val name: String = "",
    val address: String = "",
    val coordinates: Map<String, Double>? = null,
    val weeklySchedule: Map<String, String> = emptyMap(),
    val pricing: String? = null,
    val memberList: List<String> = emptyList(),
    val pendingMembers: List<String> = emptyList(),
    val members: List<ClubMember> = emptyList(),
    val pendingMemberEntries: List<ClubMember> = emptyList(),
    val createdBy: String = "",
    val contactUserId: String? = null,
    val contactName: String? = null,
    /** Club profile picture uploaded by an admin (shared with iOS via "photoURL"). */
    val photoURL: String? = null,
    /** Club admin UIDs from the iOS multi-admin "admins" map. */
    val adminIds: List<String> = emptyList()
) {
    fun isMember(userId: String) = memberList.contains(userId)
    fun isPending(userId: String) = pendingMembers.contains(userId)
    fun isCreator(userId: String) = createdBy == userId || contactUserId == userId
    fun isAdmin(userId: String) =
        userId.isNotEmpty() && (adminIds.contains(userId) || contactUserId == userId || createdBy == userId)

    val memberCount: Int get() = memberList.size
}
