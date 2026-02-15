package com.athletebridge.android.data.model

import java.util.Date

data class ChatMessage(
    val id: String = "",
    val senderId: String = "",
    val text: String = "",
    val imageURL: String? = null,
    val timestamp: Date? = null,
    val isRead: Boolean = false,
    val readBy: Map<String, Date>? = null
)

data class ChatItem(
    val id: String = "",
    val participantIds: List<String> = emptyList(),
    val participantNames: Map<String, String> = emptyMap(),
    val lastMessage: String? = null,
    val lastMessageTime: Date? = null,
    val lastSenderId: String? = null,
    val unreadCount: Int = 0
)
