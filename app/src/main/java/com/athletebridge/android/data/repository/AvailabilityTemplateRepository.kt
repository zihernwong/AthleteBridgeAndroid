package com.athletebridge.android.data.repository

import com.athletebridge.android.data.model.AvailabilityTemplate
import com.athletebridge.android.data.model.DayAvailability
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AvailabilityTemplateRepository @Inject constructor(
    private val firestore: FirebaseFirestore
) {
    private fun docRef(coachId: String) =
        firestore.collection("coaches").document(coachId)
            .collection("availabilityTemplate").document("template")

    suspend fun fetchTemplate(coachId: String): AvailabilityTemplate? {
        return try {
            val doc = docRef(coachId).get().await()
            if (!doc.exists()) return null
            val rawSlots = (doc.data?.get("slots") as? List<*>) ?: return AvailabilityTemplate(coachId = coachId)
            val slots = rawSlots.mapNotNull { item ->
                val map = item as? Map<*, *> ?: return@mapNotNull null
                DayAvailability(
                    dayOfWeek = (map["dayOfWeek"] as? Number)?.toInt() ?: 1,
                    startHour = (map["startHour"] as? Number)?.toInt() ?: 8,
                    startMinute = (map["startMinute"] as? Number)?.toInt() ?: 0,
                    endHour = (map["endHour"] as? Number)?.toInt() ?: 17,
                    endMinute = (map["endMinute"] as? Number)?.toInt() ?: 0,
                    enabled = (map["enabled"] as? Boolean) ?: true
                )
            }
            AvailabilityTemplate(coachId = coachId, slots = slots)
        } catch (e: Exception) {
            null
        }
    }

    suspend fun saveTemplate(coachId: String, template: AvailabilityTemplate): Result<Unit> {
        return try {
            val slotData = template.slots.map { slot ->
                mapOf(
                    "dayOfWeek" to slot.dayOfWeek,
                    "startHour" to slot.startHour,
                    "startMinute" to slot.startMinute,
                    "endHour" to slot.endHour,
                    "endMinute" to slot.endMinute,
                    "enabled" to slot.enabled
                )
            }
            docRef(coachId).set(mapOf("slots" to slotData, "coachId" to coachId)).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
