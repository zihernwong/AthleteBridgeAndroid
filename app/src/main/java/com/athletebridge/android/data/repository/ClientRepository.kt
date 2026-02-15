package com.athletebridge.android.data.repository

import com.athletebridge.android.data.model.Client
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ClientRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val storage: FirebaseStorage
) {
    suspend fun fetchClient(uid: String): Client? {
        return try {
            val doc = firestore.collection("clients").document(uid).get().await()
            if (!doc.exists()) return null
            parseClient(doc.id, doc.data ?: return null)
        } catch (e: Exception) {
            null
        }
    }

    suspend fun fetchAllClients(): List<Client> {
        return try {
            val snapshot = firestore.collection("clients").get().await()
            snapshot.documents.mapNotNull { doc ->
                doc.data?.let { parseClient(doc.id, it) }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun saveClient(client: Client): Result<Unit> {
        return try {
            val data = mapOf(
                "name" to client.name,
                "goals" to client.goals,
                "preferredAvailability" to client.preferredAvailability,
                "meetingPreference" to (client.meetingPreference ?: ""),
                "skillLevel" to (client.skillLevel ?: ""),
                "zipCode" to (client.zipCode ?: ""),
                "city" to (client.city ?: ""),
                "bio" to (client.bio ?: ""),
                "tournamentSoftwareLink" to (client.tournamentSoftwareLink ?: "")
            )
            firestore.collection("clients").document(client.id).set(data).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun parseClient(id: String, data: Map<String, Any>): Client {
        return Client(
            id = id,
            name = (data["name"] as? String ?: "").trim(),
            goals = (data["goals"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList(),
            preferredAvailability = (data["preferredAvailability"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList(),
            meetingPreference = data["meetingPreference"] as? String,
            skillLevel = data["skillLevel"] as? String,
            zipCode = data["zipCode"] as? String,
            city = data["city"] as? String,
            bio = data["bio"] as? String,
            tournamentSoftwareLink = data["tournamentSoftwareLink"] as? String
        )
    }
}
