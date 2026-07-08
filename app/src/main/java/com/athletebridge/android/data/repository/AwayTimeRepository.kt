package com.athletebridge.android.data.repository

import com.athletebridge.android.data.model.AwayTime
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AwayTimeRepository @Inject constructor(
    private val firestore: FirebaseFirestore
) {
    private fun coachRef(coachId: String) =
        firestore.collection("coaches").document(coachId).collection("awayTimes")

    /** Live stream of all away times for a coach, ordered by start time. */
    fun observeAwayTimes(coachId: String): Flow<List<AwayTime>> = callbackFlow {
        val reg = coachRef(coachId)
            .orderBy("startAt", Query.Direction.ASCENDING)
            .addSnapshotListener { snap, err ->
                if (err != null) { close(err); return@addSnapshotListener }
                trySend(snap?.documents?.mapNotNull { doc ->
                    doc.data?.let { parse(doc.id, it) }
                } ?: emptyList())
            }
        awaitClose { reg.remove() }
    }

    /** Fetch away times that overlap with [dayStart, dayEnd) for the calendar grid.
     *  Queries both camelCase and PascalCase field names for iOS/Android compatibility. */
    suspend fun fetchAwayTimes(coachId: String, dayStart: Date, dayEnd: Date): List<AwayTime> {
        val seen = mutableSetOf<String>()
        val result = mutableListOf<AwayTime>()
        val ref = coachRef(coachId)
        // Fetch blocks whose startAt falls in the day window
        for (field in listOf("startAt", "StartAt")) {
            try {
                ref.whereGreaterThanOrEqualTo(field, Timestamp(dayStart))
                    .whereLessThan(field, Timestamp(dayEnd))
                    .get().await()
                    .documents
                    .forEach { doc ->
                        doc.data?.let { parse(doc.id, it) }
                            ?.takeIf { seen.add(it.id) }
                            ?.let { result.add(it) }
                    }
            } catch (_: Exception) {}
        }
        // Also catch multi-day blocks that START before today but END after dayStart
        for (field in listOf("endAt", "EndAt")) {
            try {
                ref.whereGreaterThan(field, Timestamp(dayStart))
                    .get().await()
                    .documents
                    .forEach { doc ->
                        doc.data?.let { parse(doc.id, it) }
                            ?.takeIf { seen.add(it.id) }
                            ?.let { result.add(it) }
                    }
            } catch (_: Exception) {}
        }
        return result
    }

    suspend fun saveAwayTime(
        coachId: String,
        startAt: Date,
        endAt: Date,
        notes: String?
    ): Result<Unit> = try {
        val data = mutableMapOf<String, Any>(
            "coachId" to coachId,
            "startAt" to Timestamp(startAt),
            "endAt" to Timestamp(endAt),
            // Mirror PascalCase for iOS compatibility
            "StartAt" to Timestamp(startAt),
            "EndAt" to Timestamp(endAt),
            "createdAt" to FieldValue.serverTimestamp()
        )
        notes?.takeIf { it.isNotBlank() }?.let { data["notes"] = it }
        coachRef(coachId).add(data).await()
        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(e)
    }

    suspend fun deleteAwayTime(coachId: String, awayTimeId: String): Result<Unit> = try {
        coachRef(coachId).document(awayTimeId).delete().await()
        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(e)
    }

    private fun parse(id: String, data: Map<String, Any>): AwayTime {
        fun ts(camel: String, pascal: String) =
            ((data[camel] ?: data[pascal]) as? Timestamp)?.toDate()
        return AwayTime(
            id = id,
            coachId = data["coachId"] as? String ?: "",
            startAt = ts("startAt", "StartAt"),
            endAt = ts("endAt", "EndAt"),
            notes = data["notes"] as? String
        )
    }
}
