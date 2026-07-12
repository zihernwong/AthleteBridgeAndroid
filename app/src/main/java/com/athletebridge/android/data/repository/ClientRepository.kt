package com.athletebridge.android.data.repository

import com.athletebridge.android.data.model.Client
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.Source
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ClientRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val storage: FirebaseStorage
) {
    suspend fun fetchClient(uid: String, source: Source = Source.DEFAULT): Client? {
        return try {
            val doc = firestore.collection("clients").document(uid).get(source).await()
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
            // Split full name into FirstName / LastName to match iOS Firestore schema
            val parts = client.name.trim().split(" ", limit = 2)
            val firstName = parts.getOrElse(0) { "" }
            val lastName = parts.getOrElse(1) { "" }

            val data = mapOf(
                "name" to client.name,          // Android camelCase
                "Name" to client.name,          // iOS PascalCase
                "FirstName" to firstName,       // iOS split name
                "LastName" to lastName,
                "goals" to client.goals,
                "Goals" to client.goals,
                "preferredAvailability" to client.preferredAvailability,
                "PreferredAvailability" to client.preferredAvailability,
                "meetingPreference" to (client.meetingPreference ?: ""),
                "MeetingPreference" to (client.meetingPreference ?: ""),
                "skillLevel" to (client.skillLevel ?: ""),
                "SkillLevel" to (client.skillLevel ?: ""),
                "zipCode" to (client.zipCode ?: ""),
                "ZipCode" to (client.zipCode ?: ""),
                "city" to (client.city ?: ""),
                "City" to (client.city ?: ""),
                "bio" to (client.bio ?: ""),
                "Bio" to (client.bio ?: ""),
                "tournamentSoftwareLink" to (client.tournamentSoftwareLink ?: ""),
                "TournamentSoftwareLink" to (client.tournamentSoftwareLink ?: ""),
                "phoneVerified" to client.phoneVerified,
                "PhoneVerified" to client.phoneVerified,
                "phoneNumber" to (client.phoneNumber ?: ""),
                "PhoneNumber" to (client.phoneNumber ?: "")
            )
            firestore.collection("clients").document(client.id).set(data, SetOptions.merge()).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun parseClient(id: String, data: Map<String, Any>): Client {
        fun str(vararg keys: String): String? = keys.firstNotNullOfOrNull { data[it] as? String }
        fun list(vararg keys: String): List<*>? = keys.firstNotNullOfOrNull { data[it] as? List<*> }

        // Try full name fields first; fall back to FirstName + LastName (iOS convention)
        val name = str("name", "Name")?.trim()?.takeIf { it.isNotBlank() }
            ?: listOfNotNull(
                (data["FirstName"] as? String)?.trim()?.takeIf { it.isNotBlank() },
                (data["LastName"] as? String)?.trim()?.takeIf { it.isNotBlank() }
            ).joinToString(" ").trim()

        return Client(
            id = id,
            name = name,
            goals = list("goals", "Goals")?.mapNotNull { it as? String } ?: emptyList(),
            preferredAvailability = list("preferredAvailability", "PreferredAvailability", "availability", "Availability")?.mapNotNull { it as? String } ?: emptyList(),
            meetingPreference = str("meetingPreference", "MeetingPreference"),
            skillLevel = str("skillLevel", "SkillLevel"),
            zipCode = str("zipCode", "ZipCode"),
            city = str("city", "City"),
            bio = str("bio", "Bio"),
            tournamentSoftwareLink = str("tournamentSoftwareLink", "TournamentSoftwareLink"),
            phoneVerified = (data["phoneVerified"] ?: data["PhoneVerified"]) as? Boolean ?: false,
            phoneNumber = str("phoneNumber", "PhoneNumber")
        )
    }

    suspend fun setPhoneVerified(uid: String, phoneNumber: String): Result<Unit> {
        return try {
            firestore.collection("clients").document(uid)
                .update(mapOf(
                    "phoneVerified" to true,
                    "phoneNumber" to phoneNumber
                )).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
