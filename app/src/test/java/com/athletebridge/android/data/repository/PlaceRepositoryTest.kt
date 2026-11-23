package com.athletebridge.android.data.repository

import com.athletebridge.android.data.model.ClubMember
import com.athletebridge.android.data.model.SignupEvent
import com.athletebridge.android.testutil.CHICAGO
import com.athletebridge.android.testutil.FakeFirestore
import com.athletebridge.android.testutil.localDate
import com.athletebridge.android.testutil.localHour
import com.athletebridge.android.testutil.ts
import com.athletebridge.android.testutil.withDefaults
import com.google.firebase.Timestamp
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Date

class PlaceRepositoryTest {

    private val fake = FakeFirestore()
    private val repo = PlaceRepository(fake.instance)

    // ── Places ──────────────────────────────────────────────────────────────

    @Test
    fun `reads an iOS club with member maps, contactUid, co-admins and playingTimes`() = runBlocking {
        fake.seed(
            "placesToPlay/p1", mapOf(
                "name" to "Egret Badminton Club",
                "address" to "123 Court St",
                "playingTimes" to mapOf("Monday" to "6-9 PM"),
                "pricePerSession" to "$10",
                "members" to mapOf("u1" to mapOf("name" to "Alex", "joinedAt" to Timestamp.now())),
                "pendingMembers" to mapOf("u2" to mapOf("name" to "")),
                "contactUid" to "u1",
                "admins" to mapOf("u3" to mapOf("name" to "Sam")),
                "createdBy" to "u0"
            )
        )
        val place = repo.fetchPlace("p1")!!
        assertEquals(mapOf("Monday" to "6-9 PM"), place.weeklySchedule)
        assertEquals("$10", place.pricing)
        assertEquals(listOf("u1"), place.memberList)
        assertEquals(listOf("u2"), place.pendingMembers)
        assertEquals(listOf(ClubMember("u1", "Alex")), place.members)
        assertEquals(listOf(ClubMember("u2", "u2")), place.pendingMemberEntries) // blank name falls back to uid
        assertEquals("u1", place.contactUserId)
        assertEquals(listOf("u3"), place.adminIds)
        assertTrue(place.isAdmin("u3"))
    }

    @Test
    fun `approving a request moves the member over with their stored name`() = runBlocking {
        fake.seed("placesToPlay/p1", mapOf("name" to "Egret", "members" to emptyMap<String, Any>(), "pendingMembers" to emptyMap<String, Any>()))
        repo.requestMembership("p1", "u9", "Jordan").getOrThrow()
        assertEquals(listOf("u9"), repo.fetchPlace("p1")!!.pendingMembers)

        repo.approveMembership("p1", "u9").getOrThrow()
        val place = repo.fetchPlace("p1")!!
        assertTrue(place.pendingMembers.isEmpty())
        assertEquals(listOf(ClubMember("u9", "Jordan")), place.members)
    }

    @Test
    fun `announcements notify every member except the sender`() = runBlocking {
        fake.seed("placesToPlay/p1", mapOf("members" to mapOf("admin" to mapOf("name" to "A"), "m1" to mapOf("name" to "B"), "m2" to mapOf("name" to "C"))))
        repo.createAnnouncement("p1", "Holiday hours", "Closed Thursday", createdBy = "admin").getOrThrow()
        assertEquals(1, fake.docsIn("placesToPlay/p1/announcements").size)
        assertEquals(1, fake.docsIn("pendingNotifications/m1/notifications").size)
        assertEquals(1, fake.docsIn("pendingNotifications/m2/notifications").size)
        assertTrue(fake.docsIn("pendingNotifications/admin/notifications").isEmpty())
        val n = fake.docsIn("pendingNotifications/m1/notifications").values.single()
        assertEquals("club_announcement", n["type"])
        assertEquals("p1", n["placeId"])
    }

    // ── Signup events ───────────────────────────────────────────────────────

    private val eventDate = localDate(CHICAGO, 2026, 12, 3, 19, 0)

    private fun seedEvent(id: String = "e1", max: Long = 2, signups: Map<String, Any> = emptyMap(), waitlist: Map<String, Any> = emptyMap()) {
        fake.seed(
            "signupEvents/$id", mapOf(
                "title" to "Open Play", "placeId" to "p1", "placeName" to "Egret", "eventDate" to ts(eventDate),
                "maxSignups" to max, "signupCount" to signups.size.toLong(), "signups" to signups, "waitlist" to waitlist,
                "createdBy" to "admin"
            )
        )
    }

    private fun entry(userId: String, at: Long) = mapOf("name" to userId, "email" to "$userId@x.test", "userId" to userId, "signedUpAt" to Timestamp(Date(at)), "joinedAt" to Timestamp(Date(at)), "paid" to false)

    @Test
    fun `reads iOS events, including the signups and waitlist maps`() = runBlocking {
        seedEvent(signups = mapOf("k1" to entry("u1", 1_000)), waitlist = mapOf("w1" to entry("u2", 2_000)))
        val event = repo.fetchEvents("p1").single()
        assertEquals("Open Play", event.title)
        assertEquals(eventDate, event.eventDate)
        assertEquals(2, event.maxSignups)
        assertEquals(1, event.signupCount)
        assertEquals("u1", event.signups.single().userId)
        assertEquals(Date(2_000), event.waitlist.single().signedUpAt) // waitlist uses joinedAt
    }

    @Test
    fun `registering adds a signup and bumps the count`() = runBlocking {
        seedEvent()
        repo.registerForEvent("e1", "u1", "Alex", "alex@x.test").getOrThrow()
        val event = repo.fetchEvents("p1").single()
        assertEquals(1, event.signupCount)
        assertTrue(event.isRegistered("u1"))
    }

    @Test
    fun `registering twice or into a full event is refused`() = runBlocking {
        seedEvent(max = 2)
        repo.registerForEvent("e1", "u1", "Alex", "a@x.test").getOrThrow()
        assertEquals("You're already signed up", repo.registerForEvent("e1", "u1", "Alex", "a@x.test").exceptionOrNull()?.message)
        repo.registerForEvent("e1", "u2", "Sam", "s@x.test").getOrThrow()
        assertEquals("This event is full", repo.registerForEvent("e1", "u3", "Kim", "k@x.test").exceptionOrNull()?.message)
        assertEquals(2, repo.fetchEvents("p1").single().signupCount)
    }

    @Test
    fun `leaving a full event promotes the earliest waitlister and notifies them`() = runBlocking {
        seedEvent(
            max = 1,
            signups = mapOf("k1" to entry("u1", 1_000)),
            waitlist = mapOf("late" to entry("u3", 5_000), "early" to entry("u2", 2_000))
        )
        repo.unregisterFromEvent("e1", "u1").getOrThrow()
        val event = repo.fetchEvents("p1").single()
        assertEquals(listOf("u2"), event.signups.map { it.userId })
        assertEquals(listOf("u3"), event.waitlist.map { it.userId })
        assertEquals(1, event.signupCount)
        val note = fake.docsIn("pendingNotifications/u2/notifications").values.single()
        assertEquals("event_waitlist_promoted", note["type"])
    }

    @Test
    fun `leaving the waitlist removes only your entry`() = runBlocking {
        seedEvent(waitlist = mapOf("a" to entry("u2", 1), "b" to entry("u3", 2)))
        repo.leaveWaitlist("e1", "u2").getOrThrow()
        assertEquals(listOf("u3"), repo.fetchEvents("p1").single().waitlist.map { it.userId })
    }

    @Test
    fun `self-reported payment is recorded on your signup`() = runBlocking {
        seedEvent(signups = mapOf("k1" to entry("u1", 1)))
        repo.selfReportPaid("e1", "u1").getOrThrow()
        val s = repo.fetchEvents("p1").single().signups.single()
        assertTrue(s.selfPaid)
        assertFalse(s.paid)
    }

    private fun event(date: Date) = SignupEvent(
        id = "e1", placeId = "p1", placeName = "Egret", title = "Open Play", eventDate = date,
        location = "123 Court St", maxSignups = 20, createdBy = "admin", recurrence = "weekly",
        feeUSD = 10.0, paymentLink = "@egret", allowSelfReportPaid = true
    )

    private fun createdOccurrence(): Map<String, Any?> =
        fake.docsIn("signupEvents").values.single()

    @Test
    fun `next weekly occurrence copies the settings with a fresh signup list`() = runBlocking {
        repo.createNextOccurrence(event(eventDate)).getOrThrow()
        val next = createdOccurrence()
        assertEquals("Open Play", next["title"])
        assertEquals(20L, (next["maxSignups"] as Number).toLong())
        assertEquals(0L, (next["signupCount"] as Number).toLong())
        assertEquals(emptyMap<String, Any>(), next["signups"])
        assertEquals("weekly", next["recurrence"])
        assertEquals(10.0, next["feeUSD"])
        assertEquals("@egret", next["paymentLink"])
        assertEquals(true, next["allowSelfReportPaid"])
    }

    @Test
    fun `next weekly occurrence is refused when it already exists`() = runBlocking {
        val nextWeek = Date(eventDate.time + 7L * 24 * 3_600_000)
        fake.seed("signupEvents/existing", mapOf("title" to "Open Play", "placeId" to "p1", "eventDate" to ts(nextWeek)))
        val result = repo.createNextOccurrence(event(eventDate))
        assertEquals("Next week's event already exists.", result.exceptionOrNull()?.message)
        assertEquals(1, fake.docsIn("signupEvents").size)
    }

    @Test
    fun `next weekly occurrence keeps the same local start time across a daylight saving change`() = runBlocking {
        withDefaults(CHICAGO) {
            // Thursday 7 PM CDT; US clocks fall back on Sunday Nov 1, 2026.
            val thursdayBeforeFallBack = localDate(CHICAGO, 2026, 10, 29, 19, 0)
            repo.createNextOccurrence(event(thursdayBeforeFallBack)).getOrThrow()
            val next = (createdOccurrence()["eventDate"] as Timestamp).toDate()
            assertEquals("next week's event should still start at 7 PM", 19, localHour(CHICAGO, next))
        }
    }

    @Test
    fun `new events record the organizer's time zone for the server's weekly roll`() = runBlocking {
        withDefaults(CHICAGO) {
            repo.createEvent("p1", "Egret", "Open Play", null, eventDate, "123 Court St", 20, "admin").getOrThrow()
        }
        assertEquals(CHICAGO, fake.docsIn("signupEvents").values.single()["timeZone"])
    }

    @Test
    fun `rolling an iOS event forward keeps its settings, uses its time zone and links the two`() = runBlocking {
        val thursday = localDate(CHICAGO, 2026, 10, 29, 19, 0) // clocks fall back Nov 1
        val opens = localDate(CHICAGO, 2026, 10, 27, 9, 0)
        fake.seed(
            "signupEvents/e1", mapOf(
                "title" to "Open Play", "placeId" to "p1", "placeName" to "Egret", "eventDate" to ts(thursday),
                "maxSignups" to 20L, "signupCount" to 1L, "signups" to mapOf("k1" to entry("u1", 1)),
                "createdBy" to "admin", "recurrence" to "weekly", "timeZone" to CHICAGO,
                "waiverText" to "Play at your own risk", "notes" to "Bring indoor shoes", "opensAt" to ts(opens)
            )
        )
        withDefaults("Asia/Tokyo") { // the organizer's phone happens to be abroad
            repo.createNextOccurrence(repo.fetchEvents("p1").single()).getOrThrow()
        }
        val (nextId, next) = fake.docsIn("signupEvents").entries.single { it.key != "e1" }.toPair()
        assertEquals(19, localHour(CHICAGO, (next["eventDate"] as Timestamp).toDate()))
        assertEquals(9, localHour(CHICAGO, (next["opensAt"] as Timestamp).toDate()))
        assertEquals("Play at your own risk", next["waiverText"])
        assertEquals("Bring indoor shoes", next["notes"])
        assertEquals(0L, (next["signupCount"] as Number).toLong())
        assertEquals(emptyMap<String, Any>(), next["signups"])
        assertEquals("e1", next["previousOccurrenceId"])
        assertEquals(nextId, fake.doc("signupEvents/e1")!!["nextOccurrenceId"])
    }

    @Test
    fun `an event already rolled forward elsewhere is not rolled again`() = runBlocking {
        fake.seed("signupEvents/e1", mapOf("title" to "Open Play", "placeId" to "p1", "eventDate" to ts(eventDate), "nextOccurrenceId" to "e2"))
        val result = repo.createNextOccurrence(event(eventDate))
        assertTrue(result.isFailure)
        assertEquals(1, fake.docsIn("signupEvents").size)
    }

    @Test
    fun `claiming a club makes you an admin iOS recognizes`() = runBlocking {
        fake.seed("placesToPlay/p1", mapOf("name" to "Egret", "members" to emptyMap<String, Any>()))
        repo.assignPlaceContact("p1", "u1", "Alex").getOrThrow()
        val place = repo.fetchPlace("p1")!!
        assertEquals(listOf("u1"), place.adminIds)
        assertEquals("u1", place.contactUserId)
        assertTrue(place.isMember("u1"))
    }

    @Test
    fun `a second admin keeps the contact, and leaving hands the contact over`() = runBlocking {
        fake.seed("placesToPlay/p1", mapOf("name" to "Egret", "members" to emptyMap<String, Any>()))
        repo.assignPlaceContact("p1", "u1", "Alex").getOrThrow()
        repo.assignPlaceContact("p1", "u2", "Sam").getOrThrow()
        assertEquals("u1", repo.fetchPlace("p1")!!.contactUserId)

        repo.removePlaceContact("p1", "u1").getOrThrow()
        val place = repo.fetchPlace("p1")!!
        assertEquals(listOf("u2"), place.adminIds)
        assertEquals("u2", place.contactUserId)
        assertEquals("Sam", place.contactName)

        repo.removePlaceContact("p1", "u2").getOrThrow()
        assertNull(repo.fetchPlace("p1")!!.contactUserId)
    }

    @Test
    fun `missing event date fails cleanly`() = runBlocking {
        val result = repo.createNextOccurrence(SignupEvent(id = "e1", eventDate = null))
        assertTrue(result.isFailure)
        assertNull(fake.docsIn("signupEvents").values.firstOrNull())
    }
}
