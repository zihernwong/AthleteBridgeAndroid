package com.athletebridge.android.data.repository

import com.athletebridge.android.data.model.ReviewItem
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

data class RatingSummary(val average: Double, val count: Int)

@Singleton
class ReviewRepository @Inject constructor(
    private val firestore: FirebaseFirestore
) {
    /** Average rating and review count per coach, aggregated over the whole reviews collection.
     *  Ratings may be stored as Number (Android) or String (iOS). */
    suspend fun fetchRatingSummaries(): Map<String, RatingSummary> {
        return try {
            val snapshot = firestore.collection("reviews").get().await()
            val byCoach = mutableMapOf<String, MutableList<Int>>()
            for (doc in snapshot.documents) {
                val data = doc.data ?: continue
                val coachId = data["coachId"] as? String ?: continue
                val rating = when (val r = data["rating"]) {
                    is Number -> r.toInt()
                    is String -> r.toIntOrNull() ?: continue
                    else -> continue
                }
                if (rating in 1..5) byCoach.getOrPut(coachId) { mutableListOf() }.add(rating)
            }
            byCoach.mapValues { (_, ratings) ->
                RatingSummary(average = ratings.average(), count = ratings.size)
            }
        } catch (e: Exception) {
            emptyMap()
        }
    }

    suspend fun fetchReviewsForCoach(coachId: String): List<ReviewItem> {
        return try {
            val snapshot = firestore.collection("reviews")
                .whereEqualTo("coachId", coachId)
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .get().await()
            snapshot.documents.mapNotNull { doc ->
                doc.data?.let { parseReview(doc.id, it) }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun submitReview(review: ReviewItem): Result<Unit> {
        return try {
            val data = mapOf(
                "reviewerId" to review.reviewerId,
                "reviewerName" to review.reviewerName,
                "coachId" to review.coachId,
                "rating" to review.rating,
                "comment" to review.comment,
                "createdAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
            )
            firestore.collection("reviews").add(data).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun parseReview(id: String, data: Map<String, Any>): ReviewItem {
        return ReviewItem(
            id = id,
            reviewerId = data["reviewerId"] as? String ?: "",
            reviewerName = data["reviewerName"] as? String ?: "",
            coachId = data["coachId"] as? String ?: "",
            rating = (data["rating"] as? Number)?.toInt() ?: 0,
            comment = data["comment"] as? String ?: "",
            createdAt = (data["createdAt"] as? Timestamp)?.toDate()
        )
    }
}
