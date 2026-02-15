package com.athletebridge.android.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth
) {
    suspend fun saveFCMToken(token: String, userType: String?) {
        val uid = auth.currentUser?.uid ?: return
        val collection = if (userType?.uppercase() == "COACH") "coaches" else "clients"
        try {
            firestore.collection(collection).document(uid)
                .update("deviceTokens", FieldValue.arrayUnion(token))
                .await()
        } catch (_: Exception) { }
    }

    suspend fun removeFCMToken(token: String, userType: String?) {
        val uid = auth.currentUser?.uid ?: return
        val collection = if (userType?.uppercase() == "COACH") "coaches" else "clients"
        try {
            firestore.collection(collection).document(uid)
                .update("deviceTokens", FieldValue.arrayRemove(token))
                .await()
        } catch (_: Exception) { }
    }

    suspend fun sendNotification(
        recipientId: String,
        title: String,
        body: String,
        bookingId: String? = null,
        senderId: String? = null,
        type: String? = null,
        extraData: Map<String, Any> = emptyMap()
    ) {
        val data = mutableMapOf<String, Any>(
            "title" to title,
            "body" to body,
            "delivered" to false,
            "createdAt" to FieldValue.serverTimestamp()
        )
        bookingId?.let { data["bookingId"] = it }
        senderId?.let { data["senderId"] = it }
        type?.let { data["type"] = it }
        data.putAll(extraData)

        try {
            firestore.collection("pendingNotifications")
                .document(recipientId)
                .collection("notifications")
                .add(data)
                .await()
        } catch (_: Exception) { }
    }
}
