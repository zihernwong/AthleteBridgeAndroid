package com.athletebridge.android.data.repository

import com.athletebridge.android.data.model.Tournament
import com.athletebridge.android.data.model.TournamentParticipantInfo
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TournamentRepository @Inject constructor(
    private val firestore: FirebaseFirestore
) {
    suspend fun fetchTournaments(): List<Tournament> {
        return try {
            val snapshot = firestore.collection("tournaments").get().await()
            snapshot.documents.mapNotNull { doc ->
                doc.data?.let { parseTournament(doc.id, it) }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun createTournament(
        name: String,
        startDate: java.util.Date,
        endDate: java.util.Date,
        location: String,
        createdBy: String,
        signupLink: String?
    ): Result<Unit> {
        return try {
            val data = mutableMapOf<String, Any>(
                "name" to name,
                "startDate" to Timestamp(startDate),
                "endDate" to Timestamp(endDate),
                "location" to location,
                "createdBy" to createdBy,
                "participants" to emptyMap<String, Any>()
            )
            if (!signupLink.isNullOrBlank()) {
                data["signupLink"] = signupLink
            }
            firestore.collection("tournaments").add(data).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun joinTournament(
        tournamentId: String,
        userId: String,
        gender: String,
        events: List<String>,
        skillLevels: List<String>
    ): Result<Unit> {
        return try {
            val participantData = mapOf(
                "gender" to gender,
                "events" to events,
                "skillLevels" to skillLevels
            )
            firestore.collection("tournaments").document(tournamentId)
                .update("participants.$userId", participantData)
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun leaveTournament(
        tournamentId: String,
        userId: String
    ): Result<Unit> {
        return try {
            firestore.collection("tournaments").document(tournamentId)
                .update("participants.$userId", FieldValue.delete())
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchTournament(tournamentId: String): Tournament? {
        return try {
            val doc = firestore.collection("tournaments").document(tournamentId).get().await()
            doc.data?.let { parseTournament(doc.id, it) }
        } catch (e: Exception) {
            null
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseTournament(id: String, data: Map<String, Any>): Tournament {
        val participantsRaw = data["participants"] as? Map<String, Map<String, Any>> ?: emptyMap()
        val participants = participantsRaw.mapValues { (_, info) ->
            TournamentParticipantInfo(
                gender = info["gender"] as? String ?: "",
                events = (info["events"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList(),
                skillLevels = (info["skillLevels"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
            )
        }
        return Tournament(
            id = id,
            name = data["name"] as? String ?: "",
            startDate = (data["startDate"] as? Timestamp)?.toDate() ?: java.util.Date(),
            endDate = (data["endDate"] as? Timestamp)?.toDate() ?: java.util.Date(),
            location = data["location"] as? String ?: "",
            createdBy = data["createdBy"] as? String ?: "",
            signupLink = data["signupLink"] as? String,
            participants = participants
        )
    }
}
