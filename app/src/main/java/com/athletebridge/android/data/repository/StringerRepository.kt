package com.athletebridge.android.data.repository

import com.athletebridge.android.data.model.BadmintonStringer
import com.athletebridge.android.data.model.StringerOrder
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StringerRepository @Inject constructor(
    private val firestore: FirebaseFirestore
) {
    suspend fun fetchStringers(): List<BadmintonStringer> {
        return try {
            val snapshot = firestore.collection("stringers").get().await()
            snapshot.documents.mapNotNull { doc ->
                doc.data?.let { parseStringer(doc.id, it) }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun fetchStringer(stringerId: String): BadmintonStringer? {
        return try {
            val doc = firestore.collection("stringers").document(stringerId).get().await()
            doc.data?.let { parseStringer(doc.id, it) }
        } catch (e: Exception) {
            null
        }
    }

    suspend fun fetchStringerByUserId(userId: String): BadmintonStringer? {
        return try {
            val snapshot = firestore.collection("stringers")
                .whereEqualTo("userId", userId).get().await()
            snapshot.documents.firstOrNull()?.let { doc ->
                doc.data?.let { parseStringer(doc.id, it) }
            }
        } catch (e: Exception) {
            null
        }
    }

    suspend fun createOrUpdateStringer(stringer: BadmintonStringer): Result<String> {
        return try {
            val data = mutableMapOf<String, Any>(
                "userId" to stringer.userId,
                "name" to stringer.name,
                "strings" to stringer.strings,
                "laborCost" to stringer.laborCost,
                "meetupLocation" to stringer.meetupLocation
            )
            stringer.photoURLString?.let { data["photoURLString"] = it }
            stringer.meetupCoordinates?.let { data["meetupCoordinates"] = it }

            val id = if (stringer.id.isBlank()) {
                val ref = firestore.collection("stringers").add(data).await()
                ref.id
            } else {
                firestore.collection("stringers").document(stringer.id).set(data).await()
                stringer.id
            }
            Result.success(id)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun placeOrder(
        stringerId: String,
        stringerName: String,
        buyerId: String,
        buyerName: String,
        selectedString: String,
        stringCost: Double,
        laborCost: Double,
        notes: String?
    ): Result<Unit> {
        return try {
            val data = mutableMapOf<String, Any>(
                "stringerId" to stringerId,
                "stringerName" to stringerName,
                "buyerId" to buyerId,
                "buyerName" to buyerName,
                "selectedString" to selectedString,
                "stringCost" to stringCost,
                "laborCost" to laborCost,
                "status" to "placed",
                "createdAt" to FieldValue.serverTimestamp()
            )
            notes?.let { data["notes"] = it }
            firestore.collection("stringerOrders").add(data).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchOrdersForStringer(stringerId: String): List<StringerOrder> {
        return try {
            val snapshot = firestore.collection("stringerOrders")
                .whereEqualTo("stringerId", stringerId).get().await()
            snapshot.documents.mapNotNull { doc ->
                doc.data?.let { parseOrder(doc.id, it) }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun fetchOrdersForBuyer(buyerId: String): List<StringerOrder> {
        return try {
            val snapshot = firestore.collection("stringerOrders")
                .whereEqualTo("buyerId", buyerId).get().await()
            snapshot.documents.mapNotNull { doc ->
                doc.data?.let { parseOrder(doc.id, it) }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun fetchOrder(orderId: String): StringerOrder? {
        return try {
            val doc = firestore.collection("stringerOrders").document(orderId).get().await()
            doc.data?.let { parseOrder(doc.id, it) }
        } catch (e: Exception) {
            null
        }
    }

    suspend fun updateOrderStatus(orderId: String, status: String): Result<Unit> {
        return try {
            firestore.collection("stringerOrders").document(orderId)
                .update("status", status).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseStringer(id: String, data: Map<String, Any>): BadmintonStringer {
        val stringsRaw = data["strings"] as? Map<String, Any> ?: emptyMap()
        val strings = stringsRaw.mapValues { (_, v) ->
            when (v) {
                is Double -> v
                is Long -> v.toDouble()
                is Number -> v.toDouble()
                else -> 0.0
            }
        }
        return BadmintonStringer(
            id = id,
            userId = data["userId"] as? String ?: "",
            name = data["name"] as? String ?: "",
            photoURLString = data["photoURLString"] as? String,
            strings = strings,
            laborCost = (data["laborCost"] as? Number)?.toDouble() ?: 0.0,
            meetupLocation = data["meetupLocation"] as? String ?: "",
            meetupCoordinates = data["meetupCoordinates"] as? Map<String, Double>,
            isVerified = data["isVerified"] as? Boolean ?: false
        )
    }

    private fun parseOrder(id: String, data: Map<String, Any>): StringerOrder {
        return StringerOrder(
            id = id,
            stringerId = data["stringerId"] as? String ?: "",
            stringerName = data["stringerName"] as? String ?: "",
            buyerId = data["buyerId"] as? String ?: "",
            buyerName = data["buyerName"] as? String ?: "",
            selectedString = data["selectedString"] as? String ?: "",
            stringCost = (data["stringCost"] as? Number)?.toDouble() ?: 0.0,
            laborCost = (data["laborCost"] as? Number)?.toDouble() ?: 0.0,
            status = data["status"] as? String ?: "placed",
            createdAt = (data["createdAt"] as? Timestamp)?.toDate(),
            notes = data["notes"] as? String
        )
    }
}
