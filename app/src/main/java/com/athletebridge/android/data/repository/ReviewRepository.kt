package com.athletebridge.android.data.repository

import com.athletebridge.android.data.model.ReviewItem
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

data class RatingSummary(val average: Double, val count: Int)

@Singleton
class ReviewRepository @Inject constructor(
    private val firestore: FirebaseFirestore
) {
    // Reviews come from both apps: Android writes reviewerId/coachId/rating (a number)/comment and iOS
    // ClientID/CoachID/Rating (text)/RatingMessage. Both now write both; reading either shows every review.

    /** Average rating and review count per coach, aggregated over the whole reviews collection. */
    suspend fun fetchRatingSummaries(): Map<String, RatingSummary> {
        return try {
            val snapshot = firestore.collection("reviews").get().await()
            val byCoach = mutableMapOf<String, MutableList<Int>>()
            for (doc in snapshot.documents) {
                val data = doc.data ?: continue
                val coachId = coachIdOf(data) ?: continue
                val rating = parseRating(data["rating"] ?: data["Rating"]) ?: continue
                if (rating in 1..5) byCoach.getOrPut(coachId) { mutableListOf() }.add(rating)
            }
            byCoach.mapValues { (_, ratings) ->
                RatingSummary(average = ratings.average(), count = ratings.size)
            }
        } catch (e: Exception) {
            emptyMap()
        }
    }

    /** A coach's reviews, newest first. Read in full and matched here: iOS stores the coach as CoachID — an id,
     *  a "coaches/{id}" path or a reference — which no single equality query on coachId can find. */
    suspend fun fetchReviewsForCoach(coachId: String): List<ReviewItem> = allReviews().filter { it.coachId == coachId }

    /** Reviews written by one user (the client's own Reviews tab), newest first. */
    suspend fun fetchReviewsByReviewer(reviewerId: String): List<ReviewItem> = allReviews().filter { it.reviewerId == reviewerId }

    private suspend fun allReviews(): List<ReviewItem> = try {
        val reviews = firestore.collection("reviews").get().await()
            .documents.mapNotNull { doc -> doc.data?.let { parseReview(doc.id, it) } }
        // Reviews saved by older iOS builds carry no reviewer name: look each reviewer up once, as iOS does
        val names = reviews.filter { it.reviewerName.isBlank() && it.reviewerId.isNotBlank() }
            .map { it.reviewerId }.distinct()
            .associateWith { profileName(it) }
        reviews.map { if (it.reviewerName.isBlank()) it.copy(reviewerName = names[it.reviewerId].orEmpty()) else it }
            .sortedByDescending { it.createdAt }
    } catch (e: Exception) {
        emptyList()
    }

    /** A user's display name from their client profile (name), else their coach profile (FirstName LastName). */
    private suspend fun profileName(uid: String): String {
        for (collection in listOf("clients", "coaches")) {
            val data = runCatching { firestore.collection(collection).document(uid).get().await().data }.getOrNull() ?: continue
            val split = listOfNotNull(data["FirstName"] as? String, data["LastName"] as? String).joinToString(" ").trim()
            val name = listOf(data["name"] as? String, data["Name"] as? String, split).firstOrNull { !it.isNullOrBlank() }
            if (name != null) return name.trim()
        }
        return ""
    }

    suspend fun submitReview(review: ReviewItem): Result<Unit> {
        return try {
            val data = mapOf(
                "reviewerId" to review.reviewerId,
                "reviewerName" to review.reviewerName,
                "coachId" to review.coachId,
                "rating" to review.rating,
                "comment" to review.comment,
                "createdAt" to com.google.firebase.firestore.FieldValue.serverTimestamp(),
                // The iOS app reads reviews by these names (and the rating as text)
                "ClientID" to review.reviewerId,
                "CoachID" to review.coachId,
                "Rating" to review.rating.toString(),
                "RatingMessage" to review.comment
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
            reviewerId = data["reviewerId"] as? String ?: personId(data["ClientID"]) ?: "",
            reviewerName = data["reviewerName"] as? String ?: data["ClientName"] as? String ?: "",
            coachId = coachIdOf(data) ?: "",
            rating = parseRating(data["rating"] ?: data["Rating"]) ?: 0,
            comment = data["comment"] as? String ?: data["RatingMessage"] as? String ?: "",
            createdAt = (data["createdAt"] as? Timestamp)?.toDate()
        )
    }

    private fun coachIdOf(data: Map<String, Any>): String? = data["coachId"] as? String ?: personId(data["CoachID"])

    /** An id as iOS may store it: the id itself, a "coaches/{id}" path, or a DocumentReference. */
    private fun personId(value: Any?): String? = when (value) {
        is String -> value.substringAfterLast('/').takeIf { it.isNotEmpty() }
        is DocumentReference -> value.id
        else -> null
    }

    /** Ratings are stored as numbers by current apps and as strings by older iOS builds. */
    private fun parseRating(value: Any?): Int? = when (value) {
        is Number -> value.toInt()
        is String -> value.trim().toIntOrNull()
        else -> null
    }
}
