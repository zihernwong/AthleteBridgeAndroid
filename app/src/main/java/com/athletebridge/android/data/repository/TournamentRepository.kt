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
        skillLevels: List<String>,
        joinerName: String = ""
    ): Result<Unit> {
        return try {
            // Snapshot pre-join state so we only notify existing seekers on a
            // first-time join, not on preference updates (matches iOS)
            val before = fetchTournament(tournamentId)
            val isNewJoin = before?.participants?.containsKey(userId) != true

            val participantData = mapOf(
                "gender" to gender,
                "events" to events,
                "skillLevels" to skillLevels
            )
            firestore.collection("tournaments").document(tournamentId)
                .update("participants.$userId", participantData)
                .await()

            if (isNewJoin && before != null) {
                notifyCompatiblePartnerSeekers(before, userId, joinerName, gender, events, skillLevels)
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Tell existing compatible partner seekers that a new player joined the pool. */
    private fun notifyCompatiblePartnerSeekers(
        tournament: Tournament,
        joinerUid: String,
        joinerName: String,
        gender: String,
        events: List<String>,
        skillLevels: List<String>
    ) {
        val displayName = joinerName.ifBlank { "A player" }
        val joinerInfo = TournamentParticipantInfo(gender, events, skillLevels)
        val eventText = events.sorted().joinToString(" / ")
        for ((uid, info) in tournament.participants) {
            if (uid == joinerUid) continue
            if (!partnersCompatible(info.gender, info.events.toSet(), info.skillLevels.toSet(), joinerInfo)) continue
            firestore.collection("pendingNotifications").document(uid)
                .collection("notifications").document()
                .set(
                    mapOf(
                        "title" to "New Tournament Partner Option",
                        "body" to "$displayName is looking for a $eventText partner at ${tournament.name}.",
                        "type" to "tournament_partner",
                        "tournamentId" to tournament.id,
                        "senderId" to joinerUid,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "delivered" to false
                    )
                )
        }
    }

    /** Link/unlink an app tournament to a web tournament manager doc. */
    suspend fun linkWebTournament(tournamentId: String, webId: String?): Result<Unit> {
        return try {
            val update: Any = webId ?: FieldValue.delete()
            firestore.collection("tournaments").document(tournamentId)
                .update("webTournamentId", update).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Coach action: send a tournament recommendation notification to every client
     * aggregated from the coach's bookings subcollection. Returns clients notified.
     */
    suspend fun recommendToClients(tournament: Tournament, coachUid: String, coachName: String): Int {
        return try {
            val snapshot = firestore.collection("coaches").document(coachUid)
                .collection("bookings").get().await()
            val clientIds = mutableSetOf<String>()
            for (doc in snapshot.documents) {
                val data = doc.data ?: continue
                for (key in listOf("ClientID", "clientID", "clientId")) {
                    when (val v = data[key]) {
                        is String -> if (v.isNotEmpty()) clientIds.add(v)
                        is com.google.firebase.firestore.DocumentReference -> clientIds.add(v.id)
                    }
                }
                (data["ClientIDs"] as? List<*>)?.forEach { v ->
                    when (v) {
                        is String -> if (v.isNotEmpty()) clientIds.add(v)
                        is com.google.firebase.firestore.DocumentReference -> clientIds.add(v.id)
                    }
                }
            }
            clientIds.remove(coachUid)
            val dateText = java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM)
                .format(tournament.startDate)
            val displayName = coachName.ifBlank { "Your coach" }
            for (clientId in clientIds) {
                firestore.collection("pendingNotifications").document(clientId)
                    .collection("notifications").document()
                    .set(
                        mapOf(
                            "title" to "Tournament Recommendation",
                            "body" to "$displayName recommends ${tournament.name} starting $dateText at ${tournament.location}.",
                            "type" to "tournament_recommendation",
                            "tournamentId" to tournament.id,
                            "senderId" to coachUid,
                            "createdAt" to FieldValue.serverTimestamp(),
                            "delivered" to false
                        )
                    )
            }
            clientIds.size
        } catch (e: Exception) {
            0
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
            participants = participants,
            webTournamentId = data["webTournamentId"] as? String
        )
    }

    companion object {
        /**
         * Gender/event/skill compatibility shared by partner search filtering and
         * join notifications; port of FirestoreManager.partnersCompatible on iOS.
         * `gender`/`events`/`skillLevels` describe one player, `other` the potential
         * partner; the rules are symmetric.
         */
        fun partnersCompatible(
            gender: String,
            events: Set<String>,
            skillLevels: Set<String>,
            other: TournamentParticipantInfo
        ): Boolean {
            // Skill levels must overlap; an empty selection on either side means any level
            if (skillLevels.isNotEmpty() && other.skillLevels.isNotEmpty()
                && skillLevels.intersect(other.skillLevels.toSet()).isEmpty()
            ) return false
            // At least one overlapping event must pass the gender rules
            val sharedEvents = events.intersect(other.events.toSet())
            if (sharedEvents.isEmpty()) return false
            for (event in sharedEvents) {
                when (event) {
                    "Men's Doubles" -> if (gender == "Male" && other.gender == "Male") return true
                    "Women's Doubles" -> if (gender == "Female" && other.gender == "Female") return true
                    "Mixed Doubles" -> if (gender != other.gender) return true
                    else -> return true
                }
            }
            return false
        }
    }
}
