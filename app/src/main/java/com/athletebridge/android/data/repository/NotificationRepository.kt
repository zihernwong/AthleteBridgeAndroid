package com.athletebridge.android.data.repository

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth
) {
    suspend fun saveFCMToken(token: String, userType: String?) {
        val uid = auth.currentUser?.uid
        Log.d(TAG, "saveFCMToken: uid=$uid userType=$userType token=${token.take(20)}...")
        if (uid == null) {
            Log.w(TAG, "saveFCMToken: no current user — skipping")
            return
        }
        val collection = if (userType?.uppercase() == "COACH") "coaches" else "clients"
        try {
            // Use set+merge so the field is created even if the document is new
            firestore.collection(collection).document(uid)
                .set(mapOf("deviceTokens" to FieldValue.arrayUnion(token)), SetOptions.merge())
                .await()
            Log.d(TAG, "saveFCMToken: SUCCESS wrote to $collection/$uid")
        } catch (e: Exception) {
            Log.e(TAG, "saveFCMToken: FAILED for $collection/$uid", e)
        }
    }

    suspend fun removeFCMToken(token: String, userType: String?) {
        val uid = auth.currentUser?.uid ?: return
        val collection = if (userType?.uppercase() == "COACH") "coaches" else "clients"
        try {
            firestore.collection(collection).document(uid)
                .set(mapOf("deviceTokens" to FieldValue.arrayRemove(token)), SetOptions.merge())
                .await()
        } catch (_: Exception) { }
    }

    companion object {
        private const val TAG = "FCM_DEBUG"
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
