package com.athletebridge.android.data.repository

import com.athletebridge.android.data.model.PlayerToPlayWith
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlayerRepository @Inject constructor(
    private val firestore: FirebaseFirestore
) {
    suspend fun fetchPlayers(): List<PlayerToPlayWith> {
        return try {
            val snapshot = firestore.collection("playersToPlayWith").get().await()
            snapshot.documents.mapNotNull { doc ->
                doc.data?.let { parsePlayer(doc.id, it) }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun fetchPlayerByUserId(userId: String): PlayerToPlayWith? {
        return try {
            val snapshot = firestore.collection("playersToPlayWith")
                .whereEqualTo("userId", userId).get().await()
            snapshot.documents.firstOrNull()?.let { doc ->
                doc.data?.let { parsePlayer(doc.id, it) }
            }
        } catch (e: Exception) {
            null
        }
    }

    suspend fun createOrUpdatePlayer(player: PlayerToPlayWith): Result<Unit> {
        return try {
            val data = mutableMapOf<String, Any>(
                "userId" to player.userId,
                "name" to player.name,
                "skillLevel" to player.skillLevel,
                "city" to player.city,
                "availability" to player.availability,
                "linkedVenueIds" to player.linkedVenueIds
            )
            player.photoURLString?.let { data["photoURLString"] = it }

            if (player.id.isBlank()) {
                firestore.collection("playersToPlayWith").add(data).await()
            } else {
                firestore.collection("playersToPlayWith").document(player.id).set(data).await()
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deletePlayer(playerId: String): Result<Unit> {
        return try {
            firestore.collection("playersToPlayWith").document(playerId).delete().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun parsePlayer(id: String, data: Map<String, Any>): PlayerToPlayWith {
        return PlayerToPlayWith(
            id = id,
            userId = data["userId"] as? String ?: "",
            name = data["name"] as? String ?: "",
            photoURLString = data["photoURLString"] as? String,
            skillLevel = data["skillLevel"] as? String ?: "",
            city = data["city"] as? String ?: "",
            availability = (data["availability"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList(),
            linkedVenueIds = (data["linkedVenueIds"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
        )
    }
}
