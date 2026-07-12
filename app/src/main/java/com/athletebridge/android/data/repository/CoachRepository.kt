package com.athletebridge.android.data.repository

import com.athletebridge.android.data.model.Coach
import com.athletebridge.android.data.model.CoachTier
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CoachRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val storage: FirebaseStorage
) {
    suspend fun fetchCoach(uid: String, source: Source = Source.DEFAULT): Coach? {
        return try {
            val doc = firestore.collection("coaches").document(uid).get(source).await()
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
            // Split full name into FirstName / LastName to match iOS Firestore schema
            val parts = coach.name.trim().split(" ", limit = 2)
            val firstName = parts.getOrElse(0) { "" }
            val lastName = parts.getOrElse(1) { "" }

            val data = mapOf(
                "FirstName" to firstName,
                "LastName" to lastName,
                "name" to coach.name,         // keep lowercase copy for Android backward compat
                "specialties" to coach.specialties,
                "Specialties" to coach.specialties,
                "experienceYears" to coach.experienceYears,
                "ExperienceYears" to coach.experienceYears,
                "availability" to coach.availability,
                "Availability" to coach.availability,
                "bio" to (coach.bio ?: ""),
                "Bio" to (coach.bio ?: ""),
                "HourlyRate" to coach.hourlyRate,
                "hourlyRate" to coach.hourlyRate,
                "photoURLString" to (coach.photoURLString ?: ""),
                "meetingPreference" to (coach.meetingPreference ?: ""),
                "MeetingPreference" to (coach.meetingPreference ?: ""),
                "zipCode" to (coach.zipCode ?: ""),
                "ZipCode" to (coach.zipCode ?: ""),
                "city" to (coach.city ?: ""),
                "City" to (coach.city ?: ""),
                "payments" to (coach.payments ?: emptyMap<String, String>()),
                "rateRange" to (coach.rateRange ?: emptyList<Double>()),
                "RateRange" to (coach.rateRange ?: emptyList<Double>()),
                "tournamentSoftwareLink" to (coach.tournamentSoftwareLink ?: ""),
                "TournamentSoftwareLink" to (coach.tournamentSoftwareLink ?: ""),
                "subscriptionTier" to coach.subscriptionTier.value,
                "phoneVerified" to coach.phoneVerified,
                "PhoneVerified" to coach.phoneVerified,
                "phoneNumber" to (coach.phoneNumber ?: ""),
                "PhoneNumber" to (coach.phoneNumber ?: ""),
                "cancellationWindowHours" to coach.cancellationWindowHours
            )
            firestore.collection("coaches").document(coach.id).set(data).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun setPhoneVerified(uid: String, phoneNumber: String): Result<Unit> {
        return try {
            firestore.collection("coaches").document(uid)
                .update(mapOf(
                    "phoneVerified" to true,
                    "PhoneVerified" to true,
                    "phoneNumber" to phoneNumber,
                    "PhoneNumber" to phoneNumber
                )).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun savePhotoUrl(uid: String, photoUrl: String): Result<Unit> {
        return try {
            firestore.collection("coaches").document(uid)
                .update(mapOf(
                    "photoURLString" to photoUrl,
                    "PhotoURLString" to photoUrl
                )).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updatePayments(uid: String, payments: Map<String, String>): Result<Unit> {
        return try {
            firestore.collection("coaches").document(uid)
                .update("payments", payments).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun parseCoach(id: String, data: Map<String, Any>): Coach {
        // Helper to read a field by multiple possible key names (iOS uses capitalized, Android uses camelCase)
        fun str(vararg keys: String): String? = keys.firstNotNullOfOrNull { data[it] as? String }
        fun num(vararg keys: String): Number? = keys.firstNotNullOfOrNull { data[it] as? Number }
        fun list(vararg keys: String): List<*>? = keys.firstNotNullOfOrNull { data[it] as? List<*> }

        val tierRaw = str("subscriptionTier", "SubscriptionTier") ?: "free"
        return Coach(
            id = id,
            name = run {
                val first = str("FirstName", "firstName") ?: ""
                val last = str("LastName", "lastName") ?: ""
                val full = "$first $last".trim()
                full.ifBlank { str("name", "Name") ?: "" }
            },
            specialties = list("specialties", "Specialties")?.mapNotNull { it as? String } ?: emptyList(),
            experienceYears = num("experienceYears", "ExperienceYears", "experience")?.toInt() ?: 0,
            availability = list("availability", "Availability")?.mapNotNull { it as? String } ?: emptyList(),
            bio = str("bio", "Bio"),
            hourlyRate = num("hourlyRate", "HourlyRate", "Rate")?.toDouble(),
            tournamentSoftwareLink = str("tournamentSoftwareLink", "TournamentSoftwareLink"),
            photoURLString = str(
                "photoURLString", "PhotoURLString",
                "photoURL", "PhotoURL",
                "photoUrl", "PhotoUrl",
                "profileImageUrl", "profileImageURL",
                "profilePhotoUrl", "profilePhotoURL",
                "imageUrl", "imageURL"
            ),
            meetingPreference = str("meetingPreference", "MeetingPreference"),
            zipCode = str("zipCode", "ZipCode"),
            city = str("city", "City"),
            payments = ((data["payments"] ?: data["Payments"]) as? Map<*, *>)?.mapNotNull { (k, v) ->
                val key = k as? String ?: return@mapNotNull null
                val value = v as? String ?: return@mapNotNull null
                key to value
            }?.toMap(),
            rateRange = list("rateRange", "RateRange")?.mapNotNull { (it as? Number)?.toDouble() },
            subscriptionTier = CoachTier.fromString(tierRaw),
            phoneVerified = (data["phoneVerified"] ?: data["PhoneVerified"]) as? Boolean ?: false,
            phoneNumber = str("phoneNumber", "PhoneNumber"),
            cancellationWindowHours = num("cancellationWindowHours", "CancellationWindowHours")?.toInt() ?: 0,
            linkedPlaceIds = list("linkedPlaceIds", "LinkedPlaceIds")?.mapNotNull { it as? String } ?: emptyList()
        )
    }
}
