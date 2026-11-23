package com.athletebridge.android.data.repository

import com.athletebridge.android.testutil.FakeFirestore
import com.athletebridge.android.testutil.ts
import com.google.firebase.firestore.DocumentReference
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Date

class BookingRepositoryTest {

    private val fake = FakeFirestore()
    private val repo = BookingRepository(fake.instance)

    private val start = Date(1_900_000_000_000L)
    private val end = Date(start.time + 3_600_000)

    private fun mirrors(id: String, coachId: String = "c1", clientId: String = "u1") =
        listOf("bookings/$id", "coaches/$coachId/bookings/$id", "clients/$clientId/bookings/$id")

    private suspend fun newBooking(): String = repo.createBooking(
        clientId = "u1", clientName = "Alex", coachId = "c1", coachName = "Mei",
        startAt = start, endAt = end, location = "Egret", locationPlaceId = "p1", notes = "bring shuttles"
    ).getOrThrow()

    // ── Parsing ─────────────────────────────────────────────────────────────

    @Test
    fun `reads an iOS-written booking with PascalCase fields and DocumentReference ids`() = runBlocking {
        fake.seed(
            "coaches/c1/bookings/b1", mapOf(
                "ClientID" to fake.ref("clients/u1"),
                "CoachID" to fake.ref("coaches/c1"),
                "ClientName" to "Alex",
                "CoachName" to "Mei",
                "StartAt" to ts(start),
                "EndAt" to ts(end),
                "Status" to "confirmed",
                "PaymentStatus" to "paid",
                "RateUSD" to 60L,
                "Location" to "Egret",
                "Notes" to "bring shuttles"
            )
        )
        val b = repo.fetchBooking("b1", "c1")!!
        assertEquals("u1", b.clientID)
        assertEquals("c1", b.coachID)
        assertEquals("Alex", b.clientName)
        assertEquals("Mei", b.coachName)
        assertEquals(start, b.startAt)
        assertEquals(end, b.endAt)
        assertEquals("confirmed", b.status)
        assertEquals("paid", b.paymentStatus)
        assertEquals(60.0, b.rateUSD!!, 0.0)
        assertEquals("Egret", b.location)
        assertEquals("bring shuttles", b.notes)
    }

    @Test
    fun `reads an Android-written booking with camelCase fields`() = runBlocking {
        fake.seed(
            "clients/u1/bookings/b2", mapOf(
                "clientID" to "u1", "coachID" to "c1", "startAt" to ts(start), "endAt" to ts(end),
                "status" to "requested", "rateUSD" to 45.5, "locationPlaceId" to "p1", "recurrenceGroupId" to "g1"
            )
        )
        val b = repo.fetchClientBooking("b2", "u1")!!
        assertEquals("u1", b.clientID)
        assertEquals("requested", b.status)
        assertEquals(45.5, b.rateUSD!!, 0.0)
        assertEquals("p1", b.locationPlaceId)
        assertEquals("g1", b.recurrenceGroupId)
    }

    @Test
    fun `PascalCase Status wins when the two casings disagree`() = runBlocking {
        fake.seed("coaches/c1/bookings/b3", mapOf("Status" to "confirmed", "status" to "requested"))
        assertEquals("confirmed", repo.fetchBooking("b3", "c1")!!.status)
    }

    @Test
    fun `group fields accept DocumentReferences and numeric rates`() = runBlocking {
        fake.seed(
            "coaches/c1/bookings/g1", mapOf(
                "isGroupBooking" to true,
                "ClientIDs" to listOf(fake.ref("clients/u1"), "u2"),
                "CoachIDs" to listOf("c1", "c2"),
                "CoachAcceptances" to mapOf("c1" to true, "c2" to false),
                "CoachRates" to mapOf("c1" to 50L, "c2" to 62.5)
            )
        )
        val b = repo.fetchBooking("g1", "c1")!!
        assertEquals(listOf("u1", "u2"), b.clientIDs)
        assertEquals(listOf("c1", "c2"), b.allCoachIDs)
        assertEquals(mapOf("c1" to true, "c2" to false), b.coachAcceptances)
        assertEquals(mapOf("c1" to 50.0, "c2" to 62.5), b.coachRates)
    }

    @Test
    fun `fetchBookingAny falls back to the root mirror`() = runBlocking {
        fake.seed("bookings/b9", mapOf("Status" to "requested", "clientID" to "u1"))
        assertNotNull(repo.fetchBookingAny("b9", userId = "u1", userType = "CLIENT"))
        assertNotNull(repo.fetchBookingAny("b9", userId = "c1", userType = "coach"))
    }

    // ── Writes ──────────────────────────────────────────────────────────────

    @Test
    fun `createBooking writes one identical document to all three mirrors, in both casings`() = runBlocking {
        val id = newBooking()
        val docs = mirrors(id).map { fake.doc(it) ?: error("missing mirror $it") }
        assertTrue("mirrors differ", docs.all { it == docs[0] })
        val d = docs[0]
        assertEquals("requested", d["status"])
        assertEquals("requested", d["Status"])
        assertEquals(ts(start), d["startAt"])
        assertEquals(ts(start), d["StartAt"])
        assertEquals("u1", d["clientID"])
        assertEquals("clients/u1", (d["ClientID"] as DocumentReference).path)
        assertEquals("coaches/c1", (d["CoachID"] as DocumentReference).path)
        assertEquals("p1", d["locationPlaceId"])
    }

    private fun assertStatusEverywhere(id: String, expected: String) {
        for (path in mirrors(id)) {
            val d = fake.doc(path)!!
            assertEquals("$path status", expected, d["status"])
            assertEquals("$path Status", expected, d["Status"])
        }
    }

    @Test
    fun `every status change keeps all three mirrors in sync, in both casings`() = runBlocking {
        fun case(status: String, action: suspend (String) -> Result<Unit>) = status to action
        val cases = listOf(
            case("Pending Acceptance") { id -> repo.acceptBooking(id, "c1", "u1", 60.0, "see you") },
            case("pending_payment") { id -> repo.acceptBooking(id, "c1", "u1", 60.0, null, requiresPaymentUpfront = true) },
            case("rejected") { id -> repo.rejectBooking(id, "c1", "u1", "fully booked") },
            case("confirmed") { id -> repo.confirmBooking(id, "c1", "u1") },
            case("declined_by_client") { id -> repo.declineBooking(id, "c1", "u1") },
            case("cancelled") { id -> repo.cancelBooking(id, "c1", "u1") },
            case("payment_submitted") { id -> repo.submitPayment(id, "u1", "c1") },
            case("confirmed") { id -> repo.confirmPaymentReceived(id, "c1", "u1") },
            case("no_show") { id -> repo.updateBookingStatus(id, "no_show", "c1", "u1") }
        )
        for ((expected, action) in cases) {
            val id = newBooking()
            action(id).getOrThrow()
            assertStatusEverywhere(id, expected)
            assertEquals(expected, repo.fetchBooking(id, "c1")!!.status)
        }
    }

    @Test
    fun `reschedule moves both time casings and asks the other side to re-accept`() = runBlocking {
        val id = newBooking()
        val newStart = Date(start.time + 86_400_000)
        repo.rescheduleBooking(id, "c1", "u1", newStart, Date(newStart.time + 3_600_000)).getOrThrow()
        assertStatusEverywhere(id, "Pending Acceptance")
        for (path in mirrors(id)) {
            assertEquals(ts(newStart), fake.doc(path)!!["startAt"])
            assertEquals(ts(newStart), fake.doc(path)!!["StartAt"])
        }
    }

    @Test
    fun `confirming payment marks it paid in both casings`() = runBlocking {
        val id = newBooking()
        repo.confirmPaymentReceived(id, "c1", "u1").getOrThrow()
        for (path in mirrors(id)) {
            assertEquals("paid", fake.doc(path)!!["paymentStatus"])
            assertEquals("paid", fake.doc(path)!!["PaymentStatus"])
        }
    }

    @Test
    fun `late client cancellation records the forfeited deposit on every mirror`() = runBlocking {
        val id = newBooking()
        repo.cancelBooking(id, "c1", "u1", forfeitDeposit = true).getOrThrow()
        for (path in mirrors(id)) assertEquals(true, fake.doc(path)!!["depositForfeited"])
    }

    @Test
    fun `a failed mirror write fails the whole status change`() = runBlocking {
        val id = newBooking()
        fake.writes.clear()
        val result = repo.confirmBooking(id, coachId = "c1", clientId = "someone-else")
        assertTrue(result.isFailure)
        assertStatusEverywhere(id, "requested") // nothing half-applied
    }

    // ── Group bookings ──────────────────────────────────────────────────────

    private suspend fun newGroupBooking(): String = repo.createGroupBooking(
        clientId = "u1", clientName = "Alex",
        coachIds = listOf("c1", "c2"), coachNames = listOf("Mei", "Raj"),
        clientIds = listOf("u1", "u2"), clientNames = listOf("Alex", "Sam"),
        startAt = start, endAt = end, location = "Egret", notes = null
    ).getOrThrow()

    private fun groupMirrors(id: String) = listOf(
        "bookings/$id", "coaches/c1/bookings/$id", "coaches/c2/bookings/$id",
        "clients/u1/bookings/$id", "clients/u2/bookings/$id"
    )

    @Test
    fun `group booking needs every coach to accept before it moves on`() = runBlocking {
        val id = newGroupBooking()
        val coaches = listOf("c1", "c2")
        val clients = listOf("u1", "u2")

        repo.acceptGroupBookingAsCoach(id, "c1", 50.0, null, coaches, clients).getOrThrow()
        for (p in groupMirrors(id)) assertEquals(p, "requested", fake.doc(p)!!["Status"])

        repo.acceptGroupBookingAsCoach(id, "c2", 55.0, null, coaches, clients).getOrThrow()
        for (p in groupMirrors(id)) assertEquals(p, "Pending Acceptance", fake.doc(p)!!["Status"])
        assertEquals(mapOf("c1" to 50.0, "c2" to 55.0), repo.fetchBooking(id, "c2")!!.coachRates)
    }

    @Test
    fun `group booking is confirmed only after every client confirms`() = runBlocking {
        val id = newGroupBooking()
        val coaches = listOf("c1", "c2")
        val clients = listOf("u1", "u2")
        repo.acceptGroupBookingAsCoach(id, "c1", 50.0, null, coaches, clients).getOrThrow()
        repo.acceptGroupBookingAsCoach(id, "c2", 50.0, null, coaches, clients).getOrThrow()

        repo.confirmGroupBookingAsClient(id, "u1", coaches, clients).getOrThrow()
        for (p in groupMirrors(id)) assertEquals(p, "Pending Acceptance", fake.doc(p)!!["Status"])

        repo.confirmGroupBookingAsClient(id, "u2", coaches, clients).getOrThrow()
        for (p in groupMirrors(id)) assertEquals(p, "confirmed", fake.doc(p)!!["Status"])
    }

    @Test
    fun `group acceptance waits for a coach missing from the acceptance map`() = runBlocking {
        val id = newGroupBooking()
        // e.g. a coach added after creation: the maps don't list them yet
        for (p in groupMirrors(id)) {
            fake.seed(p, fake.doc(p)!! + mapOf("coachAcceptances" to mapOf("c1" to false), "CoachAcceptances" to mapOf("c1" to false)))
        }
        repo.acceptGroupBookingAsCoach(id, "c1", 50.0, null, listOf("c1", "c2"), listOf("u1", "u2")).getOrThrow()
        for (p in groupMirrors(id)) assertEquals(p, "requested", fake.doc(p)!!["Status"])
    }

    @Test
    fun `a client can decline a group booking even though it has no single coach`() = runBlocking {
        val id = newGroupBooking()
        repo.declineBooking(id, coachId = "", clientId = "u2", otherCoachIds = listOf("c1", "c2"), otherClientIds = listOf("u1")).getOrThrow()
        for (p in groupMirrors(id)) assertEquals(p, "declined_by_client", fake.doc(p)!!["Status"])
    }

    @Test
    fun `a coach rejecting a group booking updates every participant and shows the coach declined`() = runBlocking {
        val id = newGroupBooking()
        repo.rejectBooking(id, "c1", "u1", "Out of town", otherCoachIds = listOf("c2"), otherClientIds = listOf("u2")).getOrThrow()
        for (p in groupMirrors(id)) assertEquals(p, "rejected", fake.doc(p)!!["Status"])
        assertEquals(false, (fake.doc("bookings/$id")!!["CoachAcceptances"] as Map<*, *>)["c1"])
        assertEquals(false, repo.fetchBooking(id, "c2")!!.coachAcceptances!!["c1"])
    }

    // ── Queries ─────────────────────────────────────────────────────────────

    @Test
    fun `day window query finds Android and iOS bookings once each`() = runBlocking {
        val inWindow = Date(start.time + 2 * 3_600_000)
        fake.seed("coaches/c1/bookings/android", mapOf("startAt" to ts(inWindow), "StartAt" to ts(inWindow)))
        fake.seed("coaches/c1/bookings/ios", mapOf("StartAt" to ts(inWindow)))
        fake.seed("coaches/c1/bookings/tomorrow", mapOf("startAt" to ts(Date(start.time + 30 * 3_600_000))))
        val found = repo.fetchBookingsForCoach("c1", start, Date(start.time + 24 * 3_600_000)).map { it.id }
        assertEquals(listOf("android", "ios"), found.sorted())
    }
}
