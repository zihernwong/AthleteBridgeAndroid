package com.athletebridge.android.data.repository

import com.athletebridge.android.data.model.ChatItem
import com.athletebridge.android.data.model.ChatMessage
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChatRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val storage: FirebaseStorage
) {
    fun observeMessages(chatId: String): Flow<List<ChatMessage>> = callbackFlow {
        val registration = firestore.collection("chats").document(chatId)
            .collection("messages")
            .orderBy("createdAt", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) { close(error); return@addSnapshotListener }
                val messages = snapshot?.documents?.mapNotNull { doc ->
                    doc.data?.let { parseMessage(doc.id, it) }
                } ?: emptyList()
                trySend(messages)
            }
        awaitClose { registration.remove() }
    }

    suspend fun sendMessage(chatId: String, senderId: String, text: String, senderName: String): Result<Unit> {
        return try {
            val batch = firestore.batch()
            val msgRef = firestore.collection("chats").document(chatId)
                .collection("messages").document()
            val chatRef = firestore.collection("chats").document(chatId)

            // Match iOS field names: createdAt, senderId, senderRef, text
            val senderDocRef = firestore.document("coaches/$senderId")
            val msgData = mapOf(
                "senderId" to senderId,
                "senderRef" to senderDocRef,
                "text" to text,
                "createdAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
            )
            batch.set(msgRef, msgData)

            // Match iOS field names: lastMessageText, lastMessageAt
            val chatUpdate = mapOf(
                "lastMessageText" to text,
                "lastMessageAt" to com.google.firebase.firestore.FieldValue.serverTimestamp(),
                "lastSenderId" to senderId
            )
            batch.update(chatRef, chatUpdate)
            batch.commit().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun createOrGetChat(currentUid: String, otherUid: String): String {
        val chatId = listOf(currentUid, otherUid).sorted().joinToString("_")
        val chatRef = firestore.collection("chats").document(chatId)
        val doc = chatRef.get().await()
        if (!doc.exists()) {
            // Store both participantRefs (DocumentReferences) and participants (UIDs) for iOS compatibility
            val currentCoachRef = firestore.collection("coaches").document(currentUid)
            val currentClientRef = firestore.collection("clients").document(currentUid)
            val otherCoachRef = firestore.collection("coaches").document(otherUid)
            val otherClientRef = firestore.collection("clients").document(otherUid)
            chatRef.set(
                mapOf(
                    "participantRefs" to listOf(currentCoachRef, otherCoachRef),
                    "participants" to listOf(currentUid, otherUid),
                    "createdAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
                )
            ).await()
        }
        return chatId
    }

    /**
     * Fetches chats using 3 parallel queries matching iOS behavior:
     * 1. participantRefs contains coaches/{uid} (DocumentReference)
     * 2. participantRefs contains clients/{uid} (DocumentReference)
     * 3. participants contains uid (String) - legacy
     * Results are merged and deduplicated.
     */
    suspend fun fetchChatsForUser(uid: String): List<ChatItem> {
        return try {
            val coachRef = firestore.collection("coaches").document(uid)
            val clientRef = firestore.collection("clients").document(uid)

            // Run all 3 queries
            val query1 = firestore.collection("chats")
                .whereArrayContains("participantRefs", coachRef)
                .get().await()
            val query2 = firestore.collection("chats")
                .whereArrayContains("participantRefs", clientRef)
                .get().await()
            val query3 = firestore.collection("chats")
                .whereArrayContains("participants", uid)
                .get().await()

            // Merge and deduplicate by document ID
            val allDocs = (query1.documents + query2.documents + query3.documents)
                .distinctBy { it.id }

            allDocs.mapNotNull { doc ->
                val data = doc.data ?: return@mapNotNull null
                parseChatItem(doc.id, data, uid)
            }.sortedByDescending { it.lastMessageTime }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun parseChatItem(id: String, data: Map<String, Any>, currentUid: String): ChatItem {
        // Extract participant UIDs from both participantRefs and participants
        val participantIds = mutableListOf<String>()

        // From participants array (string UIDs)
        @Suppress("UNCHECKED_CAST")
        val stringParticipants = (data["participants"] as? List<String>) ?: emptyList()
        participantIds.addAll(stringParticipants)

        // From participantRefs array (DocumentReferences) - extract UID from path
        @Suppress("UNCHECKED_CAST")
        val refParticipants = (data["participantRefs"] as? List<*>)?.mapNotNull { ref ->
            (ref as? DocumentReference)?.id
        } ?: emptyList()
        refParticipants.forEach { uid ->
            if (uid !in participantIds) participantIds.add(uid)
        }

        val otherUid = participantIds.firstOrNull { it != currentUid } ?: ""

        return ChatItem(
            id = id,
            participantIds = participantIds,
            participantNames = emptyMap(), // Resolved later by ViewModel
            lastMessage = (data["lastMessageText"] as? String)
                ?: (data["lastMessage"] as? String), // fallback for legacy
            lastMessageTime = (data["lastMessageAt"] as? Timestamp)?.toDate()
                ?: (data["lastMessageTime"] as? Timestamp)?.toDate(), // fallback
            lastSenderId = data["lastSenderId"] as? String
        )
    }

    private fun parseMessage(id: String, data: Map<String, Any>): ChatMessage {
        return ChatMessage(
            id = id,
            senderId = data["senderId"] as? String ?: "",
            text = data["text"] as? String ?: "",
            imageURL = data["imageURL"] as? String,
            // iOS uses "createdAt", fallback to "timestamp" for backward compatibility
            timestamp = (data["createdAt"] as? Timestamp)?.toDate()
                ?: (data["timestamp"] as? Timestamp)?.toDate(),
            isRead = data["isRead"] as? Boolean ?: false
        )
    }
}
