package com.athletebridge.android.data.repository

import com.athletebridge.android.data.model.BookingItem
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
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
        notes: String?,
        recurrenceGroupId: String? = null
    ): Result<String> {
        return try {
            val clientDocRef = firestore.collection("clients").document(clientId)
            val coachDocRef  = firestore.collection("coaches").document(coachId)

            val bookingData = mutableMapOf<String, Any>(
                // camelCase (Android)
                "clientID"   to clientId,
                "clientName" to clientName,
                "coachID"    to coachId,
                "coachName"  to coachName,
                "startAt"    to Timestamp(startAt),
                "endAt"      to Timestamp(endAt),
                "status"     to "requested",
                "createdAt"  to FieldValue.serverTimestamp(),
                "creatorID"  to clientId,
                "creatorType" to "client",
                // PascalCase + DocumentReference (iOS)
                "ClientID"   to clientDocRef,
                "ClientName" to clientName,
                "CoachID"    to coachDocRef,
                "CoachName"  to coachName,
                "StartAt"    to Timestamp(startAt),
                "EndAt"      to Timestamp(endAt),
                "Status"     to "requested",
            )
            location?.let { bookingData["location"] = it; bookingData["Location"] = it }
            notes?.let { bookingData["notes"] = it; bookingData["Notes"] = it }
            recurrenceGroupId?.let { bookingData["recurrenceGroupId"] = it }

            // Create in client subcollection (gets the generated ID)
            val docRef = firestore.collection("clients").document(clientId)
                .collection("bookings").add(bookingData).await()
            val bookingId = docRef.id

            // Mirror to root bookings collection (iOS reads/writes here)
            firestore.collection("bookings").document(bookingId)
                .set(bookingData).await()

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
                "Status" to "requested",
                "isGroupBooking" to true,
                "coachAcceptances" to coachAcceptances,
                "clientConfirmations" to clientConfirmations,
                "createdAt" to FieldValue.serverTimestamp(),
                "creatorID" to clientId,
                "creatorType" to "client"
            )
            location?.let { bookingData["location"] = it }
            notes?.let { bookingData["notes"] = it }

            // Create in first client subcollection (gets the generated ID)
            val docRef = firestore.collection("clients").document(clientId)
                .collection("bookings").add(bookingData).await()
            val bookingId = docRef.id

            // Mirror to root bookings collection (iOS reads/writes here)
            firestore.collection("bookings").document(bookingId)
                .set(bookingData).await()

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
        coachNote: String?,
        requiresPaymentUpfront: Boolean = false
    ): Result<Unit> {
        return try {
            val newStatus = if (requiresPaymentUpfront) "pending_payment" else "Pending Acceptance"
            val updates = mutableMapOf<String, Any>(
                "status" to newStatus,
                "Status" to newStatus,
                "RateUSD" to rateUSD,
                "pendingAt" to FieldValue.serverTimestamp()
            )
            if (requiresPaymentUpfront) updates["requiresPaymentUpfront"] = true
            coachNote?.let { updates["coachNote"] = it }

            val batch = firestore.batch()
            batch.update(firestore.collection("bookings").document(bookingId), updates)
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
            batch.update(firestore.collection("bookings").document(bookingId), updates)

            // Update all coach subcollections
            for (cid in allCoachIds) {
                batch.update(
                    firestore.collection("coaches").document(cid)
                        .collection("bookings").document(bookingId),
                    updates
                )
            }

            // Update all client subcollections
            for (cid in allClientIds) {
                batch.update(
                    firestore.collection("clients").document(cid)
                        .collection("bookings").document(bookingId),
                    updates
                )
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
                    "Status" to "Pending Acceptance",
                    "pendingAt" to FieldValue.serverTimestamp()
                )
                statusBatch.update(firestore.collection("bookings").document(bookingId), statusUpdate)
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
                "Status" to "rejected",
                "rejectedBy" to coachId,
                "rejectionReason" to reason,
                "rejectedAt" to FieldValue.serverTimestamp()
            )
            val batch = firestore.batch()
            batch.update(firestore.collection("bookings").document(bookingId), updates)
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
            val updates = mapOf<String, Any>("status" to "confirmed", "Status" to "confirmed")
            val batch = firestore.batch()
            batch.update(firestore.collection("bookings").document(bookingId), updates)
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
            batch.update(firestore.collection("bookings").document(bookingId), updates)
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
                val statusUpdate = mapOf<String, Any>("status" to "confirmed", "Status" to "confirmed")
                statusBatch.update(firestore.collection("bookings").document(bookingId), statusUpdate)
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
            val updates = mapOf<String, Any>("status" to "declined_by_client", "Status" to "declined_by_client")
            val batch = firestore.batch()
            batch.update(firestore.collection("bookings").document(bookingId), updates)
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

    /** Cancel a booking; when the client cancels inside the coach's cancellation window,
     *  the deposit is marked forfeited so both sides see why the money isn't returned. */
    suspend fun cancelBooking(
        bookingId: String,
        coachId: String,
        clientId: String,
        forfeitDeposit: Boolean = false
    ): Result<Unit> {
        return try {
            val updates = mutableMapOf<String, Any>("status" to "cancelled", "Status" to "cancelled")
            if (forfeitDeposit) updates["depositForfeited"] = true
            val batch = firestore.batch()
            batch.update(firestore.collection("bookings").document(bookingId), updates)
            if (coachId.isNotEmpty()) {
                batch.update(
                    firestore.collection("coaches").document(coachId)
                        .collection("bookings").document(bookingId),
                    updates
                )
            }
            if (clientId.isNotEmpty()) {
                batch.update(
                    firestore.collection("clients").document(clientId)
                        .collection("bookings").document(bookingId),
                    updates
                )
            }
            batch.commit().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateBookingStatus(bookingId: String, status: String, coachId: String, clientId: String): Result<Unit> {
        return try {
            val updates = mapOf("status" to status, "Status" to status)
            val batch = firestore.batch()
            batch.update(firestore.collection("bookings").document(bookingId), updates)
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

    suspend fun acknowledgePayment(
        bookingId: String,
        coachId: String,
        clientId: String
    ): Result<Unit> {
        return try {
            val updates = mapOf<String, Any>("paymentStatus" to "paid", "PaymentStatus" to "paid")
            val batch = firestore.batch()
            batch.update(firestore.collection("bookings").document(bookingId), updates)
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
                "StartAt" to Timestamp(newStart),
                "EndAt" to Timestamp(newEnd),
                "status" to "Pending Acceptance",
                "Status" to "Pending Acceptance"
            )
            val batch = firestore.batch()
            batch.update(firestore.collection("bookings").document(bookingId), updates)
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

    /** Coach's private session notes, mirrored to all three booking documents (matches iOS saveSessionNotes). */
    suspend fun saveSessionNotes(
        bookingId: String,
        coachId: String,
        clientId: String,
        notes: String
    ): Result<Unit> = saveBookingField(bookingId, coachId, clientId, "coachNote", notes)

    /** Coach-written, client-visible recap of the session. */
    suspend fun saveSessionRecap(
        bookingId: String,
        coachId: String,
        clientId: String,
        recap: String
    ): Result<Unit> = saveBookingField(bookingId, coachId, clientId, "sessionRecap", recap)

    private suspend fun saveBookingField(
        bookingId: String,
        coachId: String,
        clientId: String,
        field: String,
        value: String
    ): Result<Unit> {
        return try {
            val payload = mapOf<String, Any>(field to value)
            val batch = firestore.batch()
            batch.update(firestore.collection("bookings").document(bookingId), payload)
            batch.update(
                firestore.collection("coaches").document(coachId)
                    .collection("bookings").document(bookingId),
                payload
            )
            if (clientId.isNotEmpty()) {
                batch.update(
                    firestore.collection("clients").document(clientId)
                        .collection("bookings").document(bookingId),
                    payload
                )
            }
            batch.commit().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Resolve a booking by id: try the current user's subcollection first, then the root mirror. */
    suspend fun fetchBookingAny(bookingId: String, userId: String, userType: String?): BookingItem? {
        val collection = if ((userType ?: "").uppercase() == "COACH") "coaches" else "clients"
        try {
            val doc = firestore.collection(collection).document(userId)
                .collection("bookings").document(bookingId).get().await()
            doc.data?.let { return parseBooking(doc.id, it) }
        } catch (_: Exception) {}
        return try {
            val doc = firestore.collection("bookings").document(bookingId).get().await()
            doc.data?.let { parseBooking(doc.id, it) }
        } catch (e: Exception) {
            null
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

    /** Fetch all bookings for a coach whose start time falls within [dayStart, dayEnd).
     *  Queries both camelCase (Android) and PascalCase (iOS) field names and deduplicates by id. */
    suspend fun fetchBookingsForCoach(coachId: String, dayStart: Date, dayEnd: Date): List<BookingItem> {
        val seen = mutableSetOf<String>()
        val result = mutableListOf<BookingItem>()
        val ref = firestore.collection("coaches").document(coachId).collection("bookings")
        for (field in listOf("startAt", "StartAt")) {
            try {
                ref.whereGreaterThanOrEqualTo(field, Timestamp(dayStart))
                    .whereLessThan(field, Timestamp(dayEnd))
                    .get().await()
                    .documents
                    .forEach { doc ->
                        doc.data?.let { parseBooking(doc.id, it) }
                            ?.takeIf { seen.add(it.id) }
                            ?.let { result.add(it) }
                    }
            } catch (_: Exception) {}
        }
        return result
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseBooking(id: String, data: Map<String, Any>): BookingItem {
        // iOS writes PascalCase field names; Android writes camelCase. Try both.
        fun str(camel: String, pascal: String): String? =
            data[camel] as? String ?: data[pascal] as? String

        // For ID fields iOS may write a DocumentReference instead of a plain String
        fun id(camel: String, pascal: String): String =
            data[camel] as? String
                ?: data[pascal] as? String
                ?: (data[pascal] as? DocumentReference)?.id
                ?: (data[camel] as? DocumentReference)?.id
                ?: ""

        fun ts(camel: String, pascal: String): java.util.Date? =
            ((data[camel] ?: data[pascal]) as? Timestamp)?.toDate()

        fun strList(camel: String, pascal: String): List<String>? =
            ((data[camel] ?: data[pascal]) as? List<*>)?.mapNotNull {
                it as? String ?: (it as? DocumentReference)?.id
            }

        fun boolMap(camel: String, pascal: String): Map<String, Boolean>? =
            ((data[camel] ?: data[pascal]) as? Map<*, *>)?.mapNotNull { (k, v) ->
                val key = k as? String ?: return@mapNotNull null
                val value = v as? Boolean ?: return@mapNotNull null
                key to value
            }?.toMap()

        fun doubleMap(camel: String, pascal: String): Map<String, Double>? =
            ((data[camel] ?: data[pascal]) as? Map<*, *>)?.mapNotNull { (k, v) ->
                val key = k as? String ?: return@mapNotNull null
                val value = (v as? Number)?.toDouble() ?: return@mapNotNull null
                key to value
            }?.toMap()

        return BookingItem(
            id = id,
            clientID = id("clientID", "ClientID"),
            clientName = str("clientName", "ClientName"),
            coachID = id("coachID", "CoachID"),
            coachName = str("coachName", "CoachName"),
            startAt = ts("startAt", "StartAt"),
            endAt = ts("endAt", "EndAt"),
            location = str("location", "Location"),
            notes = str("notes", "Notes"),
            status = str("Status", "status"),
            paymentStatus = str("PaymentStatus", "paymentStatus"),
            rateUSD = ((data["RateUSD"] ?: data["rateUSD"]) as? Number)?.toDouble(),
            coachNote = str("coachNote", "CoachNote"),
            rejectionReason = str("rejectionReason", "RejectionReason"),
            isGroupBooking = (data["isGroupBooking"] as? Boolean) ?: (data["IsGroupBooking"] as? Boolean),
            creatorID = data["creatorID"] as? String,
            creatorType = data["creatorType"] as? String,
            clientIDs = strList("clientIDs", "ClientIDs"),
            clientNames = strList("clientNames", "ClientNames"),
            coachIDs = strList("coachIDs", "CoachIDs"),
            coachNames = strList("coachNames", "CoachNames"),
            coachAcceptances = boolMap("coachAcceptances", "CoachAcceptances"),
            clientConfirmations = boolMap("clientConfirmations", "ClientConfirmations"),
            coachRates = doubleMap("coachRates", "CoachRates"),
            groupConfirmedClients = (data["groupConfirmedClients"] as? List<*>)?.mapNotNull { it as? String },
            groupDeclinedClients = (data["groupDeclinedClients"] as? List<*>)?.mapNotNull { it as? String },
            rejectedBy = data["rejectedBy"] as? String,
            clientDeclineReason = data["clientDeclineReason"] as? String,
            requiresPaymentUpfront = (data["requiresPaymentUpfront"] ?: data["RequiresPaymentUpfront"]) as? Boolean,
            sessionRecap = str("sessionRecap", "SessionRecap"),
            recurrenceGroupId = data["recurrenceGroupId"] as? String,
            depositForfeited = data["depositForfeited"] as? Boolean
        )
    }

    /**
     * Fetches a page of bookings for [userId], starting after [lastDoc] (cursor-based pagination).
     * Returns the list of bookings and the last document snapshot (for the next page call), or
     * null if there are no more pages.
     */
    suspend fun fetchBookingsPage(
        userId: String,
        userType: String,
        lastDoc: DocumentSnapshot?,
        pageSize: Int = 20
    ): Pair<List<BookingItem>, DocumentSnapshot?> {
        return try {
            val collection = if (userType.uppercase() == "COACH") "coaches" else "clients"
            var query = firestore.collection(collection).document(userId)
                .collection("bookings")
                .orderBy("StartAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .limit(pageSize.toLong())
            if (lastDoc != null) {
                query = query.startAfter(lastDoc)
            }
            val snapshot = query.get().await()
            val bookings = snapshot.documents.mapNotNull { doc ->
                doc.data?.let { parseBooking(doc.id, it) }
            }
            val newLastDoc = if (snapshot.documents.size < pageSize) null else snapshot.documents.lastOrNull()
            Pair(bookings, newLastDoc)
        } catch (e: Exception) {
            Pair(emptyList(), null)
        }
    }

    suspend fun submitPayment(
        bookingId: String,
        clientId: String,
        coachId: String
    ): Result<Unit> {
        return try {
            val updates = mapOf<String, Any>(
                "Status" to "payment_submitted",
                "status" to "payment_submitted",
                "paymentSubmittedAt" to FieldValue.serverTimestamp(),
                "paymentSubmittedByClient" to clientId
            )
            val batch = firestore.batch()
            batch.update(firestore.collection("bookings").document(bookingId), updates)
            if (coachId.isNotEmpty()) {
                batch.update(
                    firestore.collection("coaches").document(coachId)
                        .collection("bookings").document(bookingId),
                    updates
                )
            }
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

    suspend fun confirmPaymentReceived(
        bookingId: String,
        coachId: String,
        clientId: String
    ): Result<Unit> {
        return try {
            val updates = mapOf<String, Any>(
                "status" to "confirmed",
                "Status" to "confirmed",
                "paymentStatus" to "paid",
                "PaymentStatus" to "paid",
                "paymentConfirmedByCoach" to coachId,
                "paymentConfirmedAt" to FieldValue.serverTimestamp(),
                "confirmedAt" to FieldValue.serverTimestamp()
            )
            val batch = firestore.batch()
            batch.update(firestore.collection("bookings").document(bookingId), updates)
            if (coachId.isNotEmpty()) {
                batch.update(
                    firestore.collection("coaches").document(coachId)
                        .collection("bookings").document(bookingId),
                    updates
                )
            }
            if (clientId.isNotEmpty()) {
                batch.update(
                    firestore.collection("clients").document(clientId)
                        .collection("bookings").document(bookingId),
                    updates
                )
            }
            batch.commit().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
