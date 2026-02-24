package com.athletebridge.android.data.repository

import com.athletebridge.android.data.model.ClubAnnouncement
import com.athletebridge.android.data.model.PlaceToPlay
import com.athletebridge.android.data.model.SignupEvent
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlaceRepository @Inject constructor(
    private val firestore: FirebaseFirestore
) {
    // ── Places ────────────────────────────────────────────────────────────────

    suspend fun fetchPlaces(): List<PlaceToPlay> {
        return try {
            val snapshot = firestore.collection("placesToPlay").get().await()
            snapshot.documents.mapNotNull { doc ->
                doc.data?.let { parsePlace(doc.id, it) }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun fetchPlace(placeId: String): PlaceToPlay? {
        return try {
            val doc = firestore.collection("placesToPlay").document(placeId).get().await()
            doc.data?.let { parsePlace(doc.id, it) }
        } catch (e: Exception) {
            null
        }
    }

    suspend fun createPlace(
        name: String,
        address: String,
        weeklySchedule: Map<String, String>,
        pricing: String?,
        createdBy: String
    ): Result<String> {
        return try {
            val data = mutableMapOf<String, Any>(
                "name" to name,
                "address" to address,
                "weeklySchedule" to weeklySchedule,
                "memberList" to emptyList<String>(),
                "pendingMembers" to emptyList<String>(),
                "createdBy" to createdBy
            )
            pricing?.let { data["pricing"] = it }
            val ref = firestore.collection("placesToPlay").add(data).await()
            Result.success(ref.id)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deletePlace(placeId: String): Result<Unit> {
        return try {
            firestore.collection("placesToPlay").document(placeId).delete().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun requestMembership(placeId: String, userId: String): Result<Unit> {
        return try {
            firestore.collection("placesToPlay").document(placeId)
                .update("pendingMembers", FieldValue.arrayUnion(userId)).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun cancelMembershipRequest(placeId: String, userId: String): Result<Unit> {
        return try {
            firestore.collection("placesToPlay").document(placeId)
                .update("pendingMembers", FieldValue.arrayRemove(userId)).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun approveMembership(placeId: String, userId: String): Result<Unit> {
        return try {
            val ref = firestore.collection("placesToPlay").document(placeId)
            firestore.runBatch { batch ->
                batch.update(ref, "pendingMembers", FieldValue.arrayRemove(userId))
                batch.update(ref, "memberList", FieldValue.arrayUnion(userId))
            }.await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun leaveMembership(placeId: String, userId: String): Result<Unit> {
        return try {
            firestore.collection("placesToPlay").document(placeId)
                .update("memberList", FieldValue.arrayRemove(userId)).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ── Announcements ─────────────────────────────────────────────────────────

    suspend fun fetchAnnouncements(placeId: String): List<ClubAnnouncement> {
        return try {
            val snapshot = firestore.collection("placesToPlay").document(placeId)
                .collection("announcements").get().await()
            snapshot.documents.mapNotNull { doc ->
                doc.data?.let { parseAnnouncement(doc.id, placeId, it) }
            }.sortedByDescending { it.createdAt }
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun createAnnouncement(
        placeId: String,
        title: String,
        body: String,
        createdBy: String
    ): Result<Unit> {
        return try {
            val data = mapOf(
                "title" to title,
                "body" to body,
                "createdBy" to createdBy,
                "createdAt" to FieldValue.serverTimestamp()
            )
            firestore.collection("placesToPlay").document(placeId)
                .collection("announcements").add(data).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ── Signup Events ─────────────────────────────────────────────────────────

    suspend fun fetchEvents(placeId: String): List<SignupEvent> {
        return try {
            val snapshot = firestore.collection("placesToPlay").document(placeId)
                .collection("signupEvents").get().await()
            snapshot.documents.mapNotNull { doc ->
                doc.data?.let { parseEvent(doc.id, placeId, it) }
            }.sortedBy { it.date }
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun createEvent(
        placeId: String,
        title: String,
        description: String?,
        date: java.util.Date,
        capacity: Int,
        createdBy: String
    ): Result<Unit> {
        return try {
            val data = mutableMapOf<String, Any>(
                "title" to title,
                "date" to Timestamp(date),
                "capacity" to capacity,
                "registeredUserIds" to emptyList<String>(),
                "createdBy" to createdBy
            )
            description?.let { data["description"] = it }
            firestore.collection("placesToPlay").document(placeId)
                .collection("signupEvents").add(data).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun registerForEvent(placeId: String, eventId: String, userId: String): Result<Unit> {
        return try {
            firestore.collection("placesToPlay").document(placeId)
                .collection("signupEvents").document(eventId)
                .update("registeredUserIds", FieldValue.arrayUnion(userId)).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun unregisterFromEvent(placeId: String, eventId: String, userId: String): Result<Unit> {
        return try {
            firestore.collection("placesToPlay").document(placeId)
                .collection("signupEvents").document(eventId)
                .update("registeredUserIds", FieldValue.arrayRemove(userId)).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ── Parsers ───────────────────────────────────────────────────────────────

    @Suppress("UNCHECKED_CAST")
    private fun parsePlace(id: String, data: Map<String, Any>): PlaceToPlay {
        val schedule = (data["weeklySchedule"] as? Map<String, String>) ?: emptyMap()
        return PlaceToPlay(
            id = id,
            name = data["name"] as? String ?: "",
            address = data["address"] as? String ?: "",
            weeklySchedule = schedule,
            pricing = data["pricing"] as? String,
            memberList = (data["memberList"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList(),
            pendingMembers = (data["pendingMembers"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList(),
            createdBy = data["createdBy"] as? String ?: "",
            contactUserId = data["contactUserId"] as? String
        )
    }

    private fun parseAnnouncement(id: String, placeId: String, data: Map<String, Any>): ClubAnnouncement {
        return ClubAnnouncement(
            id = id,
            placeId = placeId,
            title = data["title"] as? String ?: "",
            body = data["body"] as? String ?: "",
            createdAt = (data["createdAt"] as? Timestamp)?.toDate(),
            createdBy = data["createdBy"] as? String ?: ""
        )
    }

    private fun parseEvent(id: String, placeId: String, data: Map<String, Any>): SignupEvent {
        return SignupEvent(
            id = id,
            placeId = placeId,
            title = data["title"] as? String ?: "",
            description = data["description"] as? String,
            date = (data["date"] as? Timestamp)?.toDate(),
            capacity = (data["capacity"] as? Number)?.toInt() ?: 0,
            registeredUserIds = (data["registeredUserIds"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList(),
            shareableURL = data["shareableURL"] as? String,
            createdBy = data["createdBy"] as? String ?: ""
        )
    }
}
