package com.athletebridge.android.data.repository

import com.athletebridge.android.data.model.ClubAnnouncement
import com.athletebridge.android.data.model.PlaceToPlay
import com.athletebridge.android.data.model.SignupEvent
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import java.time.ZoneId
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlaceRepository @Inject constructor(
    private val firestore: FirebaseFirestore
) {
    companion object {
        /** Settings carried into next week's occurrence — the same list the server's rollWeeklySignupEvents copies. */
        private val ROLLED_EVENT_KEYS = listOf(
            "title", "description", "location", "placeId", "placeName", "maxSignups", "createdBy", "recurrence",
            "feeUSD", "paymentLink", "allowSelfReportPaid", "opensHoursBefore", "notes", "waiverText", "timeZone"
        )
    }

    // ── Places ────────────────────────────────────────────────────────────────

    suspend fun fetchPlaces(): List<PlaceToPlay> {
        return try {
            val snapshot = firestore.collection("placesToPlay").get().await()
            snapshot.documents.mapNotNull { doc ->
                doc.data?.let { parsePlace(doc.id, it) }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun fetchPlace(placeId: String): PlaceToPlay? {
        return try {
            val doc = firestore.collection("placesToPlay").document(placeId).get().await()
            doc.data?.let { parsePlace(doc.id, it) }
        } catch (e: Exception) {
            null
        }
    }

    suspend fun createPlace(
        name: String,
        address: String,
        weeklySchedule: Map<String, String>,
        pricing: String?,
        createdBy: String
    ): Result<String> {
        return try {
            val data = mutableMapOf<String, Any>(
                "name" to name,
                "address" to address,
                "weeklySchedule" to weeklySchedule,
                "members" to emptyMap<String, Any>(),
                "pendingMembers" to emptyMap<String, Any>(),
                "createdBy" to createdBy
            )
            pricing?.let { data["pricing"] = it }
            val ref = firestore.collection("placesToPlay").add(data).await()
            Result.success(ref.id)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Upload a club profile picture and store its URL on the place document.
     *  Same storage path and field as iOS (clubImages/{placeId}.jpg, "photoURL"). */
    suspend fun uploadClubPhoto(placeId: String, imageBytes: ByteArray): Result<String> {
        return try {
            val ref = com.google.firebase.storage.FirebaseStorage.getInstance()
                .reference.child("clubImages").child("$placeId.jpg")
            val metadata = com.google.firebase.storage.StorageMetadata.Builder()
                .setContentType("image/jpeg")
                .build()
            ref.putBytes(imageBytes, metadata).await()
            val url = ref.downloadUrl.await().toString()
            firestore.collection("placesToPlay").document(placeId)
                .update("photoURL", url).await()
            Result.success(url)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deletePlace(placeId: String): Result<Unit> {
        return try {
            firestore.collection("placesToPlay").document(placeId).delete().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun requestMembership(placeId: String, userId: String, userName: String): Result<Unit> {
        return try {
            firestore.collection("placesToPlay").document(placeId)
                .update("pendingMembers.$userId", mapOf(
                    "name" to userName,
                    "requestedAt" to FieldValue.serverTimestamp()
                )).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun cancelMembershipRequest(placeId: String, userId: String): Result<Unit> {
        return try {
            firestore.collection("placesToPlay").document(placeId)
                .update("pendingMembers.$userId", FieldValue.delete()).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun approveMembership(placeId: String, userId: String): Result<Unit> {
        return try {
            // Read pending entry to carry forward the user's stored name
            val doc = firestore.collection("placesToPlay").document(placeId).get().await()
            val pendingEntry = ((doc.data?.get("pendingMembers") as? Map<*, *>)?.get(userId) as? Map<*, *>)
            val userName = pendingEntry?.get("name") as? String ?: ""

            val ref = firestore.collection("placesToPlay").document(placeId)
            firestore.runBatch { batch ->
                batch.update(ref, "pendingMembers.$userId", FieldValue.delete())
                batch.update(ref, "members.$userId", mapOf(
                    "name" to userName,
                    "joinedAt" to FieldValue.serverTimestamp()
                ))
            }.await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun leaveMembership(placeId: String, userId: String): Result<Unit> {
        return try {
            firestore.collection("placesToPlay").document(placeId)
                .update("members.$userId", FieldValue.delete()).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Become a club admin; also joins the members map (matches iOS assignPlaceContact).
     *  The legacy single-contact fields keep pointing at the first admin. */
    suspend fun assignPlaceContact(placeId: String, userId: String, userName: String): Result<Unit> {
        return try {
            val ref = firestore.collection("placesToPlay").document(placeId)
            val current = ref.get().await().data
            val updates = mutableMapOf<String, Any>(
                "admins.$userId" to mapOf("name" to userName, "addedAt" to Timestamp.now()),
                "members.$userId" to mapOf("name" to userName, "joinedAt" to FieldValue.serverTimestamp())
            )
            val contact = (current?.get("contactUid") ?: current?.get("contactUserId")) as? String
            if (contact.isNullOrEmpty()) {
                updates["contactUid"] = userId
                updates["contactName"] = userName
            }
            ref.update(updates).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Stop being a club admin. If you were the legacy contact, another admin takes over (matches iOS). */
    suspend fun removePlaceContact(placeId: String, userId: String): Result<Unit> {
        return try {
            val ref = firestore.collection("placesToPlay").document(placeId)
            val current = ref.get().await().data
            val updates = mutableMapOf<String, Any>("admins.$userId" to FieldValue.delete())
            if (current?.get("contactUid") == userId) {
                val next = (current?.get("admins") as? Map<*, *>)?.entries?.firstOrNull { it.key != userId }
                if (next != null) {
                    updates["contactUid"] = next.key as String
                    updates["contactName"] = ((next.value as? Map<*, *>)?.get("name") as? String).orEmpty()
                } else {
                    updates["contactUid"] = FieldValue.delete()
                    updates["contactName"] = FieldValue.delete()
                }
            }
            if (current?.get("contactUserId") == userId) updates["contactUserId"] = FieldValue.delete()
            ref.update(updates).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Contact declines a pending join request. */
    suspend fun rejectMembership(placeId: String, userId: String): Result<Unit> =
        cancelMembershipRequest(placeId, userId)

    /** Contact removes an existing member from the club. */
    suspend fun removeMember(placeId: String, userId: String): Result<Unit> =
        leaveMembership(placeId, userId)

    // ── Announcements ─────────────────────────────────────────────────────────

    suspend fun fetchAnnouncements(placeId: String): List<ClubAnnouncement> {
        return try {
            val snapshot = firestore.collection("placesToPlay").document(placeId)
                .collection("announcements").get().await()
            snapshot.documents.mapNotNull { doc ->
                doc.data?.let { parseAnnouncement(doc.id, placeId, it) }
            }.sortedByDescending { it.createdAt }
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun createAnnouncement(
        placeId: String,
        title: String,
        body: String,
        createdBy: String,
        senderName: String? = null
    ): Result<Unit> {
        return try {
            val data = mutableMapOf<String, Any>(
                "title" to title,
                "body" to body,
                "createdBy" to createdBy,
                "createdAt" to FieldValue.serverTimestamp()
            )
            senderName?.let { data["senderName"] = it }
            val announcementRef = firestore.collection("placesToPlay").document(placeId)
                .collection("announcements").document()
            announcementRef.set(data).await()

            // Push notification to every member except the sender (matches iOS sendClubAnnouncement)
            val memberIds = fetchPlace(placeId)?.memberList.orEmpty().filter { it != createdBy }
            if (memberIds.isNotEmpty()) {
                val batch = firestore.batch()
                for (memberId in memberIds) {
                    val notifRef = firestore.collection("pendingNotifications").document(memberId)
                        .collection("notifications").document()
                    batch.set(notifRef, mapOf(
                        "title" to title,
                        "body" to body,
                        "type" to "club_announcement",
                        "placeId" to placeId,
                        "announcementId" to announcementRef.id,
                        "senderId" to createdBy,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "delivered" to false
                    ))
                }
                batch.commit().await()
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ── Signup Events ─────────────────────────────────────────────────────────
    // Events live in the root "signupEvents" collection with iOS field names
    // (eventDate, maxSignups, signupCount, signups map) so both apps see the same data.

    suspend fun fetchAllEvents(): List<SignupEvent> {
        return try {
            val snapshot = firestore.collection("signupEvents")
                .orderBy("eventDate").get().await()
            snapshot.documents.mapNotNull { doc ->
                doc.data?.let { parseEvent(doc.id, it) }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun fetchEvents(placeId: String): List<SignupEvent> =
        fetchAllEvents().filter { it.placeId == placeId }

    suspend fun createEvent(
        placeId: String,
        placeName: String,
        title: String,
        description: String?,
        eventDate: java.util.Date,
        location: String,
        maxSignups: Int,
        createdBy: String,
        recurrence: String? = null,
        feeUSD: Double? = null,
        paymentLink: String? = null,
        allowSelfReportPaid: Boolean = false
    ): Result<Unit> {
        return try {
            val data = mutableMapOf<String, Any>(
                "title" to title,
                "description" to (description ?: ""),
                "eventDate" to Timestamp(eventDate),
                "location" to location,
                "placeId" to placeId,
                "placeName" to placeName,
                "maxSignups" to maxSignups,
                "signupCount" to 0,
                "createdBy" to createdBy,
                "createdAt" to FieldValue.serverTimestamp(),
                "signups" to emptyMap<String, Any>(),
                // Lets the server's weekly roll keep the same local start time (iOS writes this too)
                "timeZone" to java.util.TimeZone.getDefault().id
            )
            if (recurrence != null) data["recurrence"] = recurrence
            if (feeUSD != null && feeUSD > 0) data["feeUSD"] = feeUSD
            if (!paymentLink.isNullOrBlank()) data["paymentLink"] = paymentLink
            if (allowSelfReportPaid) data["allowSelfReportPaid"] = true
            firestore.collection("signupEvents").add(data).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Roll a weekly event forward: create next week's occurrence with a fresh signup list and link the
     * two (nextOccurrenceId / previousOccurrenceId) like iOS and the server's weekly roll. The new date
     * keeps the same local start time in the event's time zone, even across a daylight saving change.
     * Fails if next week's occurrence already exists.
     */
    suspend fun createNextOccurrence(event: SignupEvent): Result<Unit> {
        val baseDate = event.eventDate ?: return Result.failure(IllegalStateException("Event has no date"))
        return try {
            val sourceRef = event.id.takeIf { it.isNotBlank() }?.let { firestore.collection("signupEvents").document(it) }
            val source: Map<String, Any> = sourceRef?.let { ref -> runCatching { ref.get().await().data }.getOrNull() }
                ?: emptyMap()
            if (source["nextOccurrenceId"] != null) {
                return Result.failure(IllegalStateException("Next week's event already exists."))
            }

            val zone = (source["timeZone"] as? String)?.let { runCatching { ZoneId.of(it) }.getOrNull() }
                ?: ZoneId.systemDefault()
            fun sameTimeNextWeek(date: Date): Date = Date.from(date.toInstant().atZone(zone).plusWeeks(1).toInstant())
            val nextDate = sameTimeNextWeek(baseDate)

            val duplicate = fetchAllEvents().any { existing ->
                existing.placeId == event.placeId && existing.title == event.title &&
                    existing.eventDate != null &&
                    kotlin.math.abs(existing.eventDate.time - nextDate.time) < 24L * 60 * 60 * 1000
            }
            if (duplicate) return Result.failure(IllegalStateException("Next week's event already exists."))

            // Copy what the server's roll copies (incl. iOS-only settings like the waiver and opening time),
            // falling back to the in-memory event when the source document couldn't be read.
            val data = mutableMapOf<String, Any>()
            for (key in ROLLED_EVENT_KEYS) source[key]?.let { data[key] = it }
            data.putIfAbsent("title", event.title)
            data.putIfAbsent("description", event.description ?: "")
            data.putIfAbsent("location", event.location)
            data.putIfAbsent("placeId", event.placeId)
            data.putIfAbsent("placeName", event.placeName)
            data.putIfAbsent("maxSignups", event.maxSignups)
            data.putIfAbsent("createdBy", event.createdBy)
            event.recurrence?.let { data.putIfAbsent("recurrence", it) }
            event.feeUSD?.takeIf { it > 0 }?.let { data.putIfAbsent("feeUSD", it) }
            event.paymentLink?.takeIf { it.isNotBlank() }?.let { data.putIfAbsent("paymentLink", it) }
            if (event.allowSelfReportPaid) data.putIfAbsent("allowSelfReportPaid", true)
            data.putIfAbsent("timeZone", zone.id)
            (source["opensAt"] as? Timestamp)?.let { data["opensAt"] = Timestamp(sameTimeNextWeek(it.toDate())) }
            data["eventDate"] = Timestamp(nextDate)
            data["signupCount"] = 0
            data["signups"] = emptyMap<String, Any>()
            data["createdAt"] = FieldValue.serverTimestamp()
            if (sourceRef != null) data["previousOccurrenceId"] = event.id

            val nextRef = firestore.collection("signupEvents").add(data).await()
            if (sourceRef != null && source.isNotEmpty()) {
                // Linking only prevents duplicates elsewhere; the new event already exists either way
                runCatching { sourceRef.update("nextOccurrenceId", nextRef.id).await() }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Transactionally register, enforcing capacity and one signup per user (matches iOS signupForEvent). */
    suspend fun registerForEvent(eventId: String, userId: String, userName: String, userEmail: String): Result<Unit> {
        return try {
            val ref = firestore.collection("signupEvents").document(eventId)
            firestore.runTransaction { txn ->
                val snap = txn.get(ref)
                val data = snap.data ?: throw IllegalStateException("Event not found")
                val count = (data["signupCount"] as? Number)?.toInt() ?: 0
                val max = (data["maxSignups"] as? Number)?.toInt() ?: 0
                if (count >= max) throw IllegalStateException("This event is full")
                val existing = (data["signups"] as? Map<*, *>)?.values?.any {
                    ((it as? Map<*, *>)?.get("userId") as? String) == userId
                } ?: false
                if (existing) throw IllegalStateException("You're already signed up")
                val key = java.util.UUID.randomUUID().toString()
                txn.update(ref, mapOf(
                    "signups.$key" to mapOf(
                        "name" to userName,
                        "email" to userEmail,
                        "userId" to userId,
                        "signedUpAt" to Timestamp.now(),
                        "paid" to false
                    ),
                    "signupCount" to FieldValue.increment(1)
                ))
            }.await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Remove the current user's own signup (looked up by userId in the signups map). */
    suspend fun unregisterFromEvent(eventId: String, userId: String): Result<Unit> {
        return try {
            val ref = firestore.collection("signupEvents").document(eventId)
            val doc = ref.get().await()
            val signupKey = (doc.data?.get("signups") as? Map<*, *>)?.entries?.firstOrNull { (_, v) ->
                ((v as? Map<*, *>)?.get("userId") as? String) == userId
            }?.key as? String ?: return Result.failure(IllegalStateException("Signup not found"))
            removeSignup(eventId, signupKey)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Contact removes any signup by its map key (matches iOS removeSignupFromEvent).
     *  When a spot opens up, the first person on the waitlist is promoted automatically. */
    suspend fun removeSignup(eventId: String, signupId: String): Result<Unit> {
        return try {
            firestore.collection("signupEvents").document(eventId).update(
                mapOf(
                    "signups.$signupId" to FieldValue.delete(),
                    "signupCount" to FieldValue.increment(-1)
                )
            ).await()
            promoteFromWaitlist(eventId)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ── Waitlist ──────────────────────────────────────────────────────────────

    suspend fun joinWaitlist(eventId: String, userId: String, userName: String, userEmail: String): Result<Unit> {
        return try {
            val key = java.util.UUID.randomUUID().toString()
            firestore.collection("signupEvents").document(eventId).update(
                "waitlist.$key", mapOf(
                    "name" to userName,
                    "email" to userEmail,
                    "userId" to userId,
                    "joinedAt" to Timestamp.now()
                )
            ).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun leaveWaitlist(eventId: String, userId: String): Result<Unit> {
        return try {
            val doc = firestore.collection("signupEvents").document(eventId).get().await()
            val key = (doc.data?.get("waitlist") as? Map<*, *>)?.entries?.firstOrNull { (_, v) ->
                ((v as? Map<*, *>)?.get("userId") as? String) == userId
            }?.key as? String ?: return Result.success(Unit)
            firestore.collection("signupEvents").document(eventId)
                .update("waitlist.$key", FieldValue.delete()).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Move the earliest-joined waitlist entry into signups when capacity allows, and notify them. */
    private suspend fun promoteFromWaitlist(eventId: String) {
        try {
            val ref = firestore.collection("signupEvents").document(eventId)
            val data = ref.get().await().data ?: return
            val count = (data["signupCount"] as? Number)?.toInt() ?: 0
            val max = (data["maxSignups"] as? Number)?.toInt() ?: 0
            if (count >= max) return
            val first = (data["waitlist"] as? Map<*, *>)?.entries?.mapNotNull { (k, v) ->
                val key = k as? String ?: return@mapNotNull null
                val info = v as? Map<*, *> ?: return@mapNotNull null
                val joined = (info["joinedAt"] as? Timestamp)?.toDate() ?: java.util.Date(0)
                Triple(key, info, joined)
            }?.minByOrNull { it.third } ?: return

            ref.update(mapOf(
                "waitlist.${first.first}" to FieldValue.delete(),
                "signups.${first.first}" to mapOf(
                    "name" to (first.second["name"] ?: ""),
                    "email" to (first.second["email"] ?: ""),
                    "userId" to first.second["userId"],
                    "signedUpAt" to Timestamp.now(),
                    "paid" to false
                ),
                "signupCount" to FieldValue.increment(1)
            )).await()

            val promotedUserId = first.second["userId"] as? String
            if (!promotedUserId.isNullOrEmpty()) {
                val title = data["title"] as? String ?: "an event"
                firestore.collection("pendingNotifications").document(promotedUserId)
                    .collection("notifications").add(mapOf(
                        "title" to "You're in!",
                        "body" to "A spot opened up in \"$title\" — you've been moved off the waitlist.",
                        "type" to "event_waitlist_promoted",
                        "eventId" to eventId,
                        "placeId" to (data["placeId"] ?: ""),
                        "createdAt" to FieldValue.serverTimestamp(),
                        "delivered" to false
                    )).await()
            }
        } catch (_: Exception) {}
    }

    suspend fun toggleSignupPaid(eventId: String, signupId: String, paid: Boolean): Result<Unit> {
        return try {
            firestore.collection("signupEvents").document(eventId)
                .update("signups.$signupId.paid", paid).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateEventCapacity(eventId: String, newMax: Int): Result<Unit> {
        return try {
            firestore.collection("signupEvents").document(eventId)
                .update("maxSignups", newMax).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ── Parsers ───────────────────────────────────────────────────────────────

    @Suppress("UNCHECKED_CAST")
    private fun parsePlace(id: String, data: Map<String, Any>): PlaceToPlay {
        // iOS stores members as a map {uid: {name, joinedAt}}; Android legacy stored as a list
        val memberList: List<String> = (data["members"] as? Map<*, *>)?.keys
            ?.mapNotNull { it as? String }
            ?: (data["memberList"] as? List<*>)?.mapNotNull { it as? String }
            ?: emptyList()

        // iOS stores pendingMembers as a map {uid: {name, requestedAt}}; Android legacy stored as a list
        val pendingMembers: List<String> = (data["pendingMembers"] as? Map<*, *>)?.keys
            ?.mapNotNull { it as? String }
            ?: (data["pendingMemberList"] as? List<*>)?.mapNotNull { it as? String }
            ?: emptyList()

        // iOS uses "weeklySchedule" or "playingTimes"
        val schedule = (data["weeklySchedule"] as? Map<String, String>)
            ?: (data["playingTimes"] as? Map<String, String>)
            ?: emptyMap()

        fun memberEntries(field: String): List<com.athletebridge.android.data.model.ClubMember> =
            (data[field] as? Map<*, *>)?.mapNotNull { (k, v) ->
                val uid = k as? String ?: return@mapNotNull null
                val name = ((v as? Map<*, *>)?.get("name") as? String).orEmpty()
                com.athletebridge.android.data.model.ClubMember(uid, name.ifEmpty { uid })
            } ?: emptyList()

        return PlaceToPlay(
            id = id,
            name = data["name"] as? String ?: "",
            address = data["address"] as? String ?: "",
            weeklySchedule = schedule,
            pricing = (data["pricing"] ?: data["pricePerSession"]) as? String,
            memberList = memberList,
            pendingMembers = pendingMembers,
            members = memberEntries("members"),
            pendingMemberEntries = memberEntries("pendingMembers"),
            createdBy = data["createdBy"] as? String ?: "",
            // iOS stores leader UID as "contactUid"; Android used "contactUserId"
            contactUserId = (data["contactUid"] ?: data["contactUserId"]) as? String,
            contactName = data["contactName"] as? String,
            photoURL = data["photoURL"] as? String,
            adminIds = (data["admins"] as? Map<*, *>)?.keys?.mapNotNull { it as? String } ?: emptyList()
        )
    }

    private fun parseAnnouncement(id: String, placeId: String, data: Map<String, Any>): ClubAnnouncement {
        return ClubAnnouncement(
            id = id,
            placeId = placeId,
            title = data["title"] as? String ?: "",
            body = data["body"] as? String ?: "",
            createdAt = (data["createdAt"] as? Timestamp)?.toDate(),
            createdBy = data["createdBy"] as? String ?: ""
        )
    }

    private fun parseEvent(id: String, data: Map<String, Any>): SignupEvent {
        fun signupEntries(field: String, dateField: String): List<com.athletebridge.android.data.model.EventSignup> =
            (data[field] as? Map<*, *>)?.mapNotNull { (k, v) ->
                val key = k as? String ?: return@mapNotNull null
                val info = v as? Map<*, *> ?: return@mapNotNull null
                com.athletebridge.android.data.model.EventSignup(
                    id = key,
                    name = info["name"] as? String ?: "",
                    email = info["email"] as? String ?: "",
                    userId = info["userId"] as? String,
                    signedUpAt = (info[dateField] as? Timestamp)?.toDate(),
                    paid = info["paid"] as? Boolean ?: false,
                    selfPaid = info["selfPaid"] as? Boolean ?: false
                )
            } ?: emptyList()

        val signups = signupEntries("signups", "signedUpAt")
        val waitlist = signupEntries("waitlist", "joinedAt")

        return SignupEvent(
            id = id,
            placeId = data["placeId"] as? String ?: "",
            placeName = data["placeName"] as? String ?: "",
            title = data["title"] as? String ?: "",
            description = data["description"] as? String,
            eventDate = ((data["eventDate"] ?: data["date"]) as? Timestamp)?.toDate(),
            location = data["location"] as? String ?: "",
            maxSignups = ((data["maxSignups"] ?: data["capacity"]) as? Number)?.toInt() ?: 0,
            signupCount = (data["signupCount"] as? Number)?.toInt() ?: signups.size,
            signups = signups,
            waitlist = waitlist,
            shareableURL = data["shareableURL"] as? String,
            createdBy = data["createdBy"] as? String ?: "",
            recurrence = data["recurrence"] as? String,
            feeUSD = (data["feeUSD"] as? Number)?.toDouble(),
            paymentLink = data["paymentLink"] as? String,
            allowSelfReportPaid = data["allowSelfReportPaid"] as? Boolean ?: false
        )
    }

    /** Player marks their own signup as paid — pending organizer confirmation. */
    suspend fun selfReportPaid(eventId: String, userId: String): Result<Unit> {
        return try {
            val doc = firestore.collection("signupEvents").document(eventId).get().await()
            val signupKey = (doc.data?.get("signups") as? Map<*, *>)?.entries?.firstOrNull { (_, v) ->
                ((v as? Map<*, *>)?.get("userId") as? String) == userId
            }?.key as? String ?: return Result.failure(IllegalStateException("Signup not found"))
            firestore.collection("signupEvents").document(eventId)
                .update("signups.$signupKey.selfPaid", true).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Organizer setting: whether players may mark themselves paid. */
    suspend fun setAllowSelfReport(eventId: String, allowed: Boolean): Result<Unit> {
        return try {
            firestore.collection("signupEvents").document(eventId)
                .update("allowSelfReportPaid", allowed).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
