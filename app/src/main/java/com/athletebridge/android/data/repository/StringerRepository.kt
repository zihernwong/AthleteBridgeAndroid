package com.athletebridge.android.data.repository

import com.athletebridge.android.data.model.BadmintonStringer
import com.athletebridge.android.data.model.StringerOrder
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
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
            // iOS stores creator uid under "createdBy"; Android uses "userId". Try both.
            val byCreatedBy = firestore.collection("stringers")
                .whereEqualTo("createdBy", userId).get().await()
            val doc = byCreatedBy.documents.firstOrNull()
                ?: firestore.collection("stringers")
                    .whereEqualTo("userId", userId).get().await()
                    .documents.firstOrNull()
            doc?.data?.let { parseStringer(doc.id, it) }
        } catch (e: Exception) {
            null
        }
    }

    suspend fun createOrUpdateStringer(stringer: BadmintonStringer): Result<String> {
        return try {
            val data = mutableMapOf<String, Any>(
                "userId" to stringer.userId,
                "createdBy" to stringer.userId,           // iOS compatibility
                "name" to stringer.name,
                "strings" to stringer.strings,
                // iOS shows prices as typed text ("$15"); parsePrice reads either form back
                "stringsOffered" to stringer.strings.mapValues { (_, price) -> priceText(price) },
                "laborCost" to priceText(stringer.laborCost),
                "meetupLocation" to stringer.meetupLocation,
                "meetupLocationNames" to listOf(stringer.meetupLocation) // iOS compatibility
            )
            stringer.photoURLString?.let { data["photoURLString"] = it }
            stringer.meetupCoordinates?.let { data["meetupCoordinates"] = it }

            // iOS lists stringers newest first by createdAt; older iOS builds drop a profile without one
            val id = if (stringer.id.isBlank()) {
                data["createdAt"] = FieldValue.serverTimestamp()
                firestore.collection("stringers").add(data).await().id
            } else {
                val ref = firestore.collection("stringers").document(stringer.id)
                // Profiles saved before Android wrote createdAt get one on their next edit
                if (ref.get().await().get("createdAt") == null) data["createdAt"] = FieldValue.serverTimestamp()
                // Merge: keep what this form doesn't edit (iOS meetup locations, isVerified, createdAt)
                ref.set(data, SetOptions.merge()).await()
                stringer.id
            }
            Result.success(id)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun placeOrder(
        stringerId: String,
        stringerUserId: String,
        stringerName: String,
        buyerId: String,
        buyerName: String,
        racketName: String,
        hasOwnString: Boolean,
        selectedString: String,
        timelinePreference: String,
        tension: Int,
        stringCost: Double,
        laborCost: Double,
        notes: String?
    ): Result<Unit> {
        return try {
            val orderTotal = stringCost + laborCost
            val data = mutableMapOf<String, Any>(
                "stringerId" to stringerId,
                "stringerUserId" to stringerUserId,
                "stringerName" to stringerName,
                "buyerId" to buyerId,      "clientId" to buyerId,       // iOS compatibility
                "createdBy" to buyerId,                                  // current iOS reads the buyer from createdBy
                "buyerName" to buyerName,  "clientName" to buyerName,   // iOS compatibility
                "racketName" to racketName,
                "hasOwnString" to hasOwnString,
                "selectedString" to selectedString,
                "stringType" to selectedString,                          // iOS compatibility
                "timelinePreference" to timelinePreference,
                "tension" to tension,
                "stringCost" to stringCost,
                "laborCost" to laborCost,
                // iOS compatibility (stores as "$x.xx"); Locale.US so a European phone doesn't write "$22,50"
                "orderTotal" to "$${String.format(java.util.Locale.US, "%.2f", orderTotal)}",
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

    /** A buyer's orders: Android writes buyerId, older iOS builds wrote clientId, current iOS writes createdBy. */
    suspend fun fetchOrdersForBuyer(buyerId: String): List<StringerOrder> {
        val seen = mutableSetOf<String>()
        val orders = mutableListOf<StringerOrder>()
        for (field in listOf("buyerId", "clientId", "createdBy")) {
            try {
                firestore.collection("stringerOrders").whereEqualTo(field, buyerId).get().await()
                    .documents.forEach { doc ->
                        if (seen.add(doc.id)) doc.data?.let { orders.add(parseOrder(doc.id, it)) }
                    }
            } catch (_: Exception) {}
        }
        return orders
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
                .update(mapOf(
                    "status" to status,
                    // timestamped per stage so both apps can render an order timeline
                    "statusHistory.$status" to com.google.firebase.firestore.FieldValue.serverTimestamp()
                )).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** "$15" or "$12.50", the way iOS stores typed prices. */
    private fun priceText(amount: Double): String =
        if (amount == Math.rint(amount)) "$" + amount.toLong() else "$" + String.format(java.util.Locale.US, "%.2f", amount)

    /** Parses a price value that may be a Number (Android) or a "$10" String (iOS). */
    private fun parsePrice(value: Any?): Double = when (value) {
        is Number -> value.toDouble()
        is String -> value.replace("$", "").replace(",", "").trim().toDoubleOrNull() ?: 0.0
        else -> 0.0
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseStringer(id: String, data: Map<String, Any>): BadmintonStringer {
        // iOS uses "stringsOffered"; Android uses "strings". Try both.
        val stringsRaw = (data["stringsOffered"] as? Map<String, Any>)
            ?: (data["strings"] as? Map<String, Any>)
            ?: emptyMap()
        val strings = stringsRaw.mapValues { (_, v) -> parsePrice(v) }

        // iOS uses "meetupLocationNames" (String array); Android uses "meetupLocation" (String).
        val meetupLocation = (data["meetupLocationNames"] as? List<*>)
            ?.filterIsInstance<String>()
            ?.firstOrNull()
            ?: data["meetupLocation"] as? String
            ?: ""

        return BadmintonStringer(
            id = id,
            // iOS uses "createdBy"; Android uses "userId"
            userId = data["createdBy"] as? String
                ?: data["userId"] as? String
                ?: "",
            name = data["name"] as? String ?: "",
            photoURLString = data["photoURLString"] as? String,
            strings = strings,
            laborCost = parsePrice(data["laborCost"]),
            meetupLocation = meetupLocation,
            meetupCoordinates = data["meetupCoordinates"] as? Map<String, Double>,
            isVerified = data["isVerified"] as? Boolean ?: false
        )
    }

    private fun parseOrder(id: String, data: Map<String, Any>): StringerOrder {
        return StringerOrder(
            id = id,
            stringerId = data["stringerId"] as? String ?: "",
            stringerUserId = data["stringerUserId"] as? String ?: "",
            stringerName = data["stringerName"] as? String ?: "",
            // Older iOS: "clientId"/"clientName"; Android: "buyerId"/"buyerName"; current iOS: "createdBy"/"buyerName"
            buyerId = data["clientId"] as? String ?: data["buyerId"] as? String ?: data["createdBy"] as? String ?: "",
            buyerName = data["clientName"] as? String ?: data["buyerName"] as? String ?: "",
            racketName = data["racketName"] as? String ?: "",
            hasOwnString = data["hasOwnString"] as? Boolean ?: false,
            // iOS uses "stringType"; Android uses "selectedString"
            selectedString = data["stringType"] as? String ?: data["selectedString"] as? String ?: "",
            timelinePreference = data["timelinePreference"] as? String ?: "No rush",
            tension = (data["tension"] as? Number)?.toInt() ?: 0,
            // iOS stores prices as "$5" strings; Android stores as Double
            stringCost = parsePrice(data["stringCost"]),
            laborCost = parsePrice(data["laborCost"]),
            status = data["status"] as? String ?: "placed",
            createdAt = (data["createdAt"] as? Timestamp)?.toDate(),
            notes = data["notes"] as? String,
            statusHistory = (data["statusHistory"] as? Map<*, *>)?.mapNotNull { (k, v) ->
                val key = k as? String ?: return@mapNotNull null
                val ts = (v as? Timestamp)?.toDate() ?: return@mapNotNull null
                key to ts
            }?.toMap() ?: emptyMap()
        )
    }
}
