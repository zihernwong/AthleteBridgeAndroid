package com.athletebridge.android.data.repository

import com.athletebridge.android.data.model.BookingItem
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BookingRepository @Inject constructor(
    private val firestore: FirebaseFirestore
) {
    fun observeCoachBookings(coachId: String): Flow<List<BookingItem>> = callbackFlow {
        val registration = firestore.collection("coaches").document(coachId)
            .collection("bookings")
            .addSnapshotListener { snapshot, error ->
                if (error != null) { close(error); return@addSnapshotListener }
                val bookings = snapshot?.documents?.mapNotNull { doc ->
                    doc.data?.let { parseBooking(doc.id, it) }
                } ?: emptyList()
                trySend(bookings)
            }
        awaitClose { registration.remove() }
    }

    fun observeClientBookings(clientId: String): Flow<List<BookingItem>> = callbackFlow {
        val registration = firestore.collection("clients").document(clientId)
            .collection("bookings")
            .addSnapshotListener { snapshot, error ->
                if (error != null) { close(error); return@addSnapshotListener }
                val bookings = snapshot?.documents?.mapNotNull { doc ->
                    doc.data?.let { parseBooking(doc.id, it) }
                } ?: emptyList()
                trySend(bookings)
            }
        awaitClose { registration.remove() }
    }

    suspend fun createBooking(
        clientId: String,
        clientName: String,
        coachId: String,
        coachName: String,
        startAt: Date,
        endAt: Date,
        location: String?,
        notes: String?
    ): Result<String> {
        return try {
            val bookingData = mutableMapOf<String, Any>(
                "clientID" to clientId,
                "clientName" to clientName,
                "coachID" to coachId,
                "coachName" to coachName,
                "startAt" to Timestamp(startAt),
                "endAt" to Timestamp(endAt),
                "status" to "requested",
                "createdAt" to FieldValue.serverTimestamp(),
                "creatorID" to clientId,
                "creatorType" to "client"
            )
            location?.let { bookingData["location"] = it }
            notes?.let { bookingData["notes"] = it }

            // Create in client subcollection
            val docRef = firestore.collection("clients").document(clientId)
                .collection("bookings").add(bookingData).await()
            val bookingId = docRef.id

            // Mirror to coach subcollection
            firestore.collection("coaches").document(coachId)
                .collection("bookings").document(bookingId)
                .set(bookingData).await()

            Result.success(bookingId)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun createGroupBooking(
        clientId: String,
        clientName: String,
        coachIds: List<String>,
        coachNames: List<String>,
        clientIds: List<String>,
        clientNames: List<String>,
        startAt: Date,
        endAt: Date,
        location: String?,
        notes: String?
    ): Result<String> {
        return try {
            val coachAcceptances = coachIds.associateWith { false }
            val clientConfirmations = clientIds.associateWith { false }

            val bookingData = mutableMapOf<String, Any>(
                "clientID" to clientId,
                "clientName" to clientName,
                "coachIDs" to coachIds,
                "coachNames" to coachNames,
                "clientIDs" to clientIds,
                "clientNames" to clientNames,
                "startAt" to Timestamp(startAt),
                "endAt" to Timestamp(endAt),
                "status" to "requested",
                "isGroupBooking" to true,
                "coachAcceptances" to coachAcceptances,
                "clientConfirmations" to clientConfirmations,
                "createdAt" to FieldValue.serverTimestamp(),
                "creatorID" to clientId,
                "creatorType" to "client"
            )
            location?.let { bookingData["location"] = it }
            notes?.let { bookingData["notes"] = it }

            // Create in first client subcollection
            val docRef = firestore.collection("clients").document(clientId)
                .collection("bookings").add(bookingData).await()
            val bookingId = docRef.id

            // Mirror to all client subcollections
            for (cid in clientIds) {
                if (cid != clientId) {
                    firestore.collection("clients").document(cid)
                        .collection("bookings").document(bookingId)
                        .set(bookingData).await()
                }
            }

            // Mirror to all coach subcollections
            for (coachId2 in coachIds) {
                firestore.collection("coaches").document(coachId2)
                    .collection("bookings").document(bookingId)
                    .set(bookingData).await()
            }

            Result.success(bookingId)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun acceptBooking(
        bookingId: String,
        coachId: String,
        clientId: String,
        rateUSD: Double,
        coachNote: String?
    ): Result<Unit> {
        return try {
            val updates = mutableMapOf<String, Any>(
                "status" to "Pending Acceptance",
                "RateUSD" to rateUSD,
                "pendingAt" to FieldValue.serverTimestamp()
            )
            coachNote?.let { updates["coachNote"] = it }

            val batch = firestore.batch()
            val coachRef = firestore.collection("coaches").document(coachId)
                .collection("bookings").document(bookingId)
            val clientRef = firestore.collection("clients").document(clientId)
                .collection("bookings").document(bookingId)

            batch.update(coachRef, updates)
            batch.update(clientRef, updates)
            batch.commit().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun acceptGroupBookingAsCoach(
        bookingId: String,
        coachId: String,
        rateUSD: Double,
        coachNote: String?,
        allCoachIds: List<String>,
        allClientIds: List<String>
    ): Result<Unit> {
        return try {
            val updates = mutableMapOf<String, Any>(
                "coachAcceptances.$coachId" to true,
                "coachRates.$coachId" to rateUSD
            )
            coachNote?.let { updates["coachNote"] = it }

            val batch = firestore.batch()

            // Update all coach subcollections
            for (cid in allCoachIds) {
                val ref = firestore.collection("coaches").document(cid)
                    .collection("bookings").document(bookingId)
                batch.update(ref, updates)
            }

            // Update all client subcollections
            for (cid in allClientIds) {
                val ref = firestore.collection("clients").document(cid)
                    .collection("bookings").document(bookingId)
                batch.update(ref, updates)
            }

            batch.commit().await()

            // Check if all coaches accepted → update status to "Pending Acceptance"
            val docSnap = firestore.collection("coaches").document(allCoachIds.first())
                .collection("bookings").document(bookingId).get().await()
            val acceptances = (docSnap.data?.get("coachAcceptances") as? Map<*, *>)
                ?.values?.all { it == true } ?: false

            if (acceptances) {
                val statusBatch = firestore.batch()
                val statusUpdate = mapOf<String, Any>(
                    "status" to "Pending Acceptance",
                    "pendingAt" to FieldValue.serverTimestamp()
                )
                for (cid in allCoachIds) {
                    statusBatch.update(
                        firestore.collection("coaches").document(cid)
                            .collection("bookings").document(bookingId),
                        statusUpdate
                    )
                }
                for (cid in allClientIds) {
                    statusBatch.update(
                        firestore.collection("clients").document(cid)
                            .collection("bookings").document(bookingId),
                        statusUpdate
                    )
                }
                statusBatch.commit().await()
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun rejectBooking(
        bookingId: String,
        coachId: String,
        clientId: String,
        reason: String
    ): Result<Unit> {
        return try {
            val updates = mapOf<String, Any>(
                "status" to "rejected",
                "rejectedBy" to coachId,
                "rejectionReason" to reason,
                "rejectedAt" to FieldValue.serverTimestamp()
            )
            val batch = firestore.batch()
            batch.update(
                firestore.collection("coaches").document(coachId)
                    .collection("bookings").document(bookingId),
                updates
            )
            batch.update(
                firestore.collection("clients").document(clientId)
                    .collection("bookings").document(bookingId),
                updates
            )
            batch.commit().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun confirmBooking(
        bookingId: String,
        coachId: String,
        clientId: String
    ): Result<Unit> {
        return try {
            val updates = mapOf<String, Any>("status" to "confirmed")
            val batch = firestore.batch()
            batch.update(
                firestore.collection("coaches").document(coachId)
                    .collection("bookings").document(bookingId),
                updates
            )
            batch.update(
                firestore.collection("clients").document(clientId)
                    .collection("bookings").document(bookingId),
                updates
            )
            batch.commit().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun confirmGroupBookingAsClient(
        bookingId: String,
        clientId: String,
        allCoachIds: List<String>,
        allClientIds: List<String>
    ): Result<Unit> {
        return try {
            val updates = mapOf<String, Any>(
                "clientConfirmations.$clientId" to true
            )
            val batch = firestore.batch()
            for (cid in allCoachIds) {
                batch.update(
                    firestore.collection("coaches").document(cid)
                        .collection("bookings").document(bookingId),
                    updates
                )
            }
            for (cid in allClientIds) {
                batch.update(
                    firestore.collection("clients").document(cid)
                        .collection("bookings").document(bookingId),
                    updates
                )
            }
            batch.commit().await()

            // Check if all clients confirmed → update status
            val docSnap = firestore.collection("clients").document(allClientIds.first())
                .collection("bookings").document(bookingId).get().await()
            val confirmations = (docSnap.data?.get("clientConfirmations") as? Map<*, *>)
                ?.values?.all { it == true } ?: false

            if (confirmations) {
                val statusBatch = firestore.batch()
                val statusUpdate = mapOf<String, Any>("status" to "confirmed")
                for (cid in allCoachIds) {
                    statusBatch.update(
                        firestore.collection("coaches").document(cid)
                            .collection("bookings").document(bookingId),
                        statusUpdate
                    )
                }
                for (cid in allClientIds) {
                    statusBatch.update(
                        firestore.collection("clients").document(cid)
                            .collection("bookings").document(bookingId),
                        statusUpdate
                    )
                }
                statusBatch.commit().await()
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun declineBooking(
        bookingId: String,
        coachId: String,
        clientId: String
    ): Result<Unit> {
        return try {
            val updates = mapOf<String, Any>("status" to "declined_by_client")
            val batch = firestore.batch()
            batch.update(
                firestore.collection("coaches").document(coachId)
                    .collection("bookings").document(bookingId),
                updates
            )
            batch.update(
                firestore.collection("clients").document(clientId)
                    .collection("bookings").document(bookingId),
                updates
            )
            batch.commit().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateBookingStatus(bookingId: String, status: String, coachId: String, clientId: String): Result<Unit> {
        return try {
            val batch = firestore.batch()
            val coachRef = firestore.collection("coaches").document(coachId)
                .collection("bookings").document(bookingId)
            val clientRef = firestore.collection("clients").document(clientId)
                .collection("bookings").document(bookingId)
            batch.update(coachRef, "status", status)
            batch.update(clientRef, "status", status)
            batch.commit().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun acknowledgePayment(
        bookingId: String,
        coachId: String,
        clientId: String
    ): Result<Unit> {
        return try {
            val updates = mapOf<String, Any>("paymentStatus" to "paid")
            val batch = firestore.batch()
            batch.update(
                firestore.collection("coaches").document(coachId)
                    .collection("bookings").document(bookingId),
                updates
            )
            batch.update(
                firestore.collection("clients").document(clientId)
                    .collection("bookings").document(bookingId),
                updates
            )
            batch.commit().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun rescheduleBooking(
        bookingId: String,
        coachId: String,
        clientId: String,
        newStart: Date,
        newEnd: Date
    ): Result<Unit> {
        return try {
            val updates = mapOf<String, Any>(
                "startAt" to Timestamp(newStart),
                "endAt" to Timestamp(newEnd),
                "status" to "Pending Acceptance"
            )
            val batch = firestore.batch()
            batch.update(
                firestore.collection("coaches").document(coachId)
                    .collection("bookings").document(bookingId),
                updates
            )
            batch.update(
                firestore.collection("clients").document(clientId)
                    .collection("bookings").document(bookingId),
                updates
            )
            batch.commit().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchBooking(bookingId: String, coachId: String): BookingItem? {
        return try {
            val doc = firestore.collection("coaches").document(coachId)
                .collection("bookings").document(bookingId).get().await()
            doc.data?.let { parseBooking(doc.id, it) }
        } catch (e: Exception) {
            null
        }
    }

    suspend fun fetchClientBooking(bookingId: String, clientId: String): BookingItem? {
        return try {
            val doc = firestore.collection("clients").document(clientId)
                .collection("bookings").document(bookingId).get().await()
            doc.data?.let { parseBooking(doc.id, it) }
        } catch (e: Exception) {
            null
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseBooking(id: String, data: Map<String, Any>): BookingItem {
        return BookingItem(
            id = id,
            clientID = data["clientID"] as? String ?: "",
            clientName = data["clientName"] as? String,
            coachID = data["coachID"] as? String ?: "",
            coachName = data["coachName"] as? String,
            startAt = (data["startAt"] as? Timestamp)?.toDate(),
            endAt = (data["endAt"] as? Timestamp)?.toDate(),
            location = data["location"] as? String,
            notes = data["notes"] as? String,
            status = (data["status"] as? String) ?: (data["Status"] as? String),
            paymentStatus = data["paymentStatus"] as? String,
            rateUSD = (data["RateUSD"] as? Number)?.toDouble() ?: (data["rateUSD"] as? Number)?.toDouble(),
            coachNote = (data["coachNote"] as? String) ?: (data["CoachNote"] as? String),
            rejectionReason = data["rejectionReason"] as? String,
            isGroupBooking = data["isGroupBooking"] as? Boolean,
            creatorID = data["creatorID"] as? String,
            creatorType = data["creatorType"] as? String,
            clientIDs = (data["clientIDs"] as? List<*>)?.mapNotNull { it as? String },
            clientNames = (data["clientNames"] as? List<*>)?.mapNotNull { it as? String },
            coachIDs = (data["coachIDs"] as? List<*>)?.mapNotNull { it as? String },
            coachNames = (data["coachNames"] as? List<*>)?.mapNotNull { it as? String },
            coachAcceptances = (data["coachAcceptances"] as? Map<*, *>)?.mapNotNull { (k, v) ->
                val key = k as? String ?: return@mapNotNull null
                val value = v as? Boolean ?: return@mapNotNull null
                key to value
            }?.toMap(),
            clientConfirmations = (data["clientConfirmations"] as? Map<*, *>)?.mapNotNull { (k, v) ->
                val key = k as? String ?: return@mapNotNull null
                val value = v as? Boolean ?: return@mapNotNull null
                key to value
            }?.toMap(),
            coachRates = (data["coachRates"] as? Map<*, *>)?.mapNotNull { (k, v) ->
                val key = k as? String ?: return@mapNotNull null
                val value = (v as? Number)?.toDouble() ?: return@mapNotNull null
                key to value
            }?.toMap(),
            groupConfirmedClients = (data["groupConfirmedClients"] as? List<*>)?.mapNotNull { it as? String },
            groupDeclinedClients = (data["groupDeclinedClients"] as? List<*>)?.mapNotNull { it as? String }
        )
    }
}
