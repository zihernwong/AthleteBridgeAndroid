package com.athletebridge.android.data.repository

import android.util.Log
import com.athletebridge.android.data.model.ChatItem
import com.athletebridge.android.data.model.ChatMessage
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "ChatRepository"

@Singleton
class ChatRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val storage: FirebaseStorage
) {
    fun observeMessages(chatId: String): Flow<List<ChatMessage>> = callbackFlow {
        // No orderBy — Firestore's orderBy silently excludes documents missing the field,
        // which drops any iOS messages stored with "timestamp" instead of "createdAt".
        // We sort client-side instead, handling both field names via parseMessage.
        val messagesRef = firestore.collection("chats").document(chatId)
            .collection("messages")

        Log.d(TAG, "observeMessages: attaching listener for chatId=$chatId")

        // The snapshot listener fires immediately with cached data (if any), then again
        // with server data — no separate initial fetch needed.
        val registration = messagesRef.addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.e(TAG, "observeMessages: listener error for chatId=$chatId", error)
                trySend(emptyList()) // Unblock the spinner on error so screen doesn't hang
                return@addSnapshotListener
            }
            val count = snapshot?.documents?.size ?: 0
            Log.d(TAG, "observeMessages: received $count docs for chatId=$chatId, fromCache=${snapshot?.metadata?.isFromCache}")
            // ESTIMATE gives a just-sent message (server time still pending, e.g. offline) a local time
            // instead of null; anything still without a time sorts last, never above older messages.
            val messages = snapshot?.documents?.mapNotNull { doc ->
                doc.getData(DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)?.let { parseMessage(doc.id, it) }
            }?.sortedWith(compareBy(nullsLast<java.util.Date>()) { it.timestamp }) ?: emptyList()
            trySend(messages)
        }
        awaitClose {
            Log.d(TAG, "observeMessages: removing listener for chatId=$chatId")
            registration.remove()
        }
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
            // Also mark sender as having read (they just sent the message)
            val chatUpdate = mapOf(
                "lastMessageText" to text,
                "lastMessageAt" to com.google.firebase.firestore.FieldValue.serverTimestamp(),
                "lastSenderId" to senderId,
                "readBy.$senderId" to com.google.firebase.firestore.FieldValue.serverTimestamp()
            )
            batch.update(chatRef, chatUpdate)
            batch.commit().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Send a photo message: uploads already done, this writes the message doc with imageURL. */
    suspend fun sendImageMessage(chatId: String, senderId: String, imageUrl: String): Result<Unit> {
        return try {
            val batch = firestore.batch()
            val msgRef = firestore.collection("chats").document(chatId)
                .collection("messages").document()
            val chatRef = firestore.collection("chats").document(chatId)
            batch.set(msgRef, mapOf(
                "senderId" to senderId,
                "text" to "",
                "imageURL" to imageUrl,
                "createdAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
            ))
            batch.update(chatRef, mapOf(
                "lastMessageText" to "📷 Photo",
                "lastMessageAt" to com.google.firebase.firestore.FieldValue.serverTimestamp(),
                "lastSenderId" to senderId,
                "readBy.$senderId" to com.google.firebase.firestore.FieldValue.serverTimestamp()
            ))
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
     * Observes chats in real-time using 3 snapshot listeners matching iOS behavior:
     * 1. participantRefs contains coaches/{uid} (DocumentReference)
     * 2. participantRefs contains clients/{uid} (DocumentReference)
     * 3. participants contains uid (String)
     * All three listeners merge and deduplicate on every update, so the chat list
     * stays live — new messages from iOS update lastMessageText on the chat doc and
     * the list refreshes automatically.
     */
    fun observeChatsForUser(uid: String): Flow<List<ChatItem>> = callbackFlow {
        val coachRef = firestore.collection("coaches").document(uid)
        val clientRef = firestore.collection("clients").document(uid)

        // Each listener writes to its own slot; merge() combines all three.
        val results = Array<List<ChatItem>>(3) { emptyList() }

        fun merge() {
            val merged = results.flatMap { it }
                .distinctBy { it.id }
                .sortedByDescending { it.lastMessageTime }
            trySend(merged)
        }

        val reg1 = firestore.collection("chats")
            .whereArrayContains("participantRefs", coachRef)
            .addSnapshotListener { snapshot, error ->
                if (error != null) { Log.e(TAG, "observeChats q1 error uid=$uid", error); return@addSnapshotListener }
                results[0] = snapshot?.documents?.mapNotNull { doc -> doc.data?.let { parseChatItem(doc.id, it, uid) } } ?: emptyList()
                merge()
            }

        val reg2 = firestore.collection("chats")
            .whereArrayContains("participantRefs", clientRef)
            .addSnapshotListener { snapshot, error ->
                if (error != null) { Log.e(TAG, "observeChats q2 error uid=$uid", error); return@addSnapshotListener }
                results[1] = snapshot?.documents?.mapNotNull { doc -> doc.data?.let { parseChatItem(doc.id, it, uid) } } ?: emptyList()
                merge()
            }

        val reg3 = firestore.collection("chats")
            .whereArrayContains("participants", uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) { Log.e(TAG, "observeChats q3 error uid=$uid", error); return@addSnapshotListener }
                results[2] = snapshot?.documents?.mapNotNull { doc -> doc.data?.let { parseChatItem(doc.id, it, uid) } } ?: emptyList()
                merge()
            }

        awaitClose {
            reg1.remove()
            reg2.remove()
            reg3.remove()
        }
    }

    /** Emits whether the other participant is typing (chats/{chatId}/typing/{otherUid}, matches iOS).
     *  Typing state older than 10 seconds is treated as stale/false. */
    fun observeTyping(chatId: String, otherUid: String): Flow<Boolean> = callbackFlow {
        val registration = firestore.collection("chats").document(chatId)
            .collection("typing").document(otherUid)
            .addSnapshotListener { snap, _ ->
                val data = snap?.data
                var isTyping = data?.get("isTyping") as? Boolean ?: false
                val updatedAt = (data?.get("updatedAt") as? Timestamp)?.toDate()
                if (isTyping && updatedAt != null &&
                    System.currentTimeMillis() - updatedAt.time > 10_000
                ) {
                    isTyping = false
                }
                trySend(isTyping)
            }
        awaitClose { registration.remove() }
    }

    suspend fun setTyping(chatId: String, uid: String, isTyping: Boolean) {
        try {
            firestore.collection("chats").document(chatId)
                .collection("typing").document(uid)
                .set(mapOf(
                    "isTyping" to isTyping,
                    "updatedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
                )).await()
        } catch (e: Exception) {
            Log.e(TAG, "setTyping failed chatId=$chatId", e)
        }
    }

    suspend fun markChatRead(chatId: String, uid: String) {
        try {
            firestore.collection("chats").document(chatId)
                .update("readBy.$uid", com.google.firebase.firestore.FieldValue.serverTimestamp())
                .await()
        } catch (e: Exception) {
            Log.e(TAG, "markChatRead failed chatId=$chatId uid=$uid", e)
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

        val lastMessageTime = (data["lastMessageAt"] as? Timestamp)?.toDate()
            ?: (data["lastMessageTime"] as? Timestamp)?.toDate()
        val lastSenderId = data["lastSenderId"] as? String

        // Unread = a message arrived after we last read the chat. Compare times rather than trusting
        // lastSenderId: iOS never writes it, so it is missing or stale after an iOS reply. Our own sends
        // set readBy.<us> in the same write as lastMessageAt, so they never count as unread.
        @Suppress("UNCHECKED_CAST")
        val readByMap = data["readBy"] as? Map<String, Any>
        val lastReadAt = (readByMap?.get(currentUid) as? Timestamp)?.toDate()
        val isUnread = when {
            lastReadAt != null -> lastMessageTime != null && lastMessageTime.after(lastReadAt)
            // Never opened: unread if there's a message we didn't send ourselves
            else -> (lastMessageTime != null || lastSenderId != null) && lastSenderId != currentUid
        }

        return ChatItem(
            id = id,
            participantIds = participantIds,
            participantNames = emptyMap(), // Resolved later by ViewModel
            lastMessage = (data["lastMessageText"] as? String)
                ?: (data["lastMessage"] as? String), // fallback for legacy
            lastMessageTime = lastMessageTime,
            lastSenderId = lastSenderId,
            unreadCount = if (isUnread) 1 else 0
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
