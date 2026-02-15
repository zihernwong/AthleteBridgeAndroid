package com.athletebridge.android.data.repository

import com.athletebridge.android.data.model.Coach
import com.athletebridge.android.data.model.CoachTier
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CoachRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val storage: FirebaseStorage
) {
    suspend fun fetchCoach(uid: String): Coach? {
        return try {
            val doc = firestore.collection("coaches").document(uid).get().await()
            if (!doc.exists()) return null
            parseCoach(doc.id, doc.data ?: return null)
        } catch (e: Exception) {
            null
        }
    }

    suspend fun fetchAllCoaches(): List<Coach> {
        return try {
            val snapshot = firestore.collection("coaches").get().await()
            snapshot.documents.mapNotNull { doc ->
                doc.data?.let { parseCoach(doc.id, it) }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun saveCoach(coach: Coach): Result<Unit> {
        return try {
            val data = mapOf(
                "name" to coach.name,
                "specialties" to coach.specialties,
                "experienceYears" to coach.experienceYears,
                "availability" to coach.availability,
                "bio" to (coach.bio ?: ""),
                "hourlyRate" to coach.hourlyRate,
                "photoURLString" to (coach.photoURLString ?: ""),
                "meetingPreference" to (coach.meetingPreference ?: ""),
                "zipCode" to (coach.zipCode ?: ""),
                "city" to (coach.city ?: ""),
                "payments" to (coach.payments ?: emptyMap<String, String>()),
                "rateRange" to (coach.rateRange ?: emptyList<Double>()),
                "tournamentSoftwareLink" to (coach.tournamentSoftwareLink ?: ""),
                "subscriptionTier" to coach.subscriptionTier.value
            )
            firestore.collection("coaches").document(coach.id).set(data).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun parseCoach(id: String, data: Map<String, Any>): Coach {
        val tierRaw = data["subscriptionTier"] as? String ?: "free"
        return Coach(
            id = id,
            name = (data["name"] as? String ?: "").trim(),
            specialties = (data["specialties"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList(),
            experienceYears = (data["experienceYears"] as? Number)?.toInt() ?: 0,
            availability = (data["availability"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList(),
            bio = data["bio"] as? String,
            hourlyRate = (data["hourlyRate"] as? Number)?.toDouble(),
            tournamentSoftwareLink = data["tournamentSoftwareLink"] as? String,
            photoURLString = (data["PhotoURL"] as? String)
                ?: (data["photoURL"] as? String)
                ?: (data["photoUrl"] as? String)
                ?: (data["photoURLString"] as? String),
            meetingPreference = data["meetingPreference"] as? String,
            zipCode = data["zipCode"] as? String,
            city = data["city"] as? String,
            payments = (data["payments"] as? Map<*, *>)?.mapNotNull { (k, v) ->
                val key = k as? String ?: return@mapNotNull null
                val value = v as? String ?: return@mapNotNull null
                key to value
            }?.toMap(),
            rateRange = (data["rateRange"] as? List<*>)?.mapNotNull { (it as? Number)?.toDouble() },
            subscriptionTier = CoachTier.fromString(tierRaw)
        )
    }
}
