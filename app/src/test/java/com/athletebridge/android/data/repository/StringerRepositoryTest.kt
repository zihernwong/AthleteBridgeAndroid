package com.athletebridge.android.data.repository

import com.athletebridge.android.testutil.FakeFirestore
import com.athletebridge.android.testutil.withDefaults
import com.google.firebase.Timestamp
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Date
import java.util.Locale

class StringerRepositoryTest {

    private val fake = FakeFirestore()
    private val repo = StringerRepository(fake.instance)

    @Test
    fun `reads an iOS stringer with dollar-string prices and meetup names`() = runBlocking {
        fake.seed(
            "stringers/s1", mapOf(
                "createdBy" to "u1", "name" to "Raj",
                "stringsOffered" to mapOf("BG80" to "$12", "Aerobite" to "$14.50", "Kevlar" to "$1,250"),
                "laborCost" to "$10",
                "meetupLocationNames" to listOf("Egret", "Rec Center"),
                "isVerified" to true
            )
        )
        val s = repo.fetchStringer("s1")!!
        assertEquals("u1", s.userId)
        assertEquals(mapOf("BG80" to 12.0, "Aerobite" to 14.5, "Kevlar" to 1250.0), s.strings)
        assertEquals(10.0, s.laborCost, 0.0)
        assertEquals("Egret", s.meetupLocation)
        assertTrue(s.isVerified)
    }

    @Test
    fun `finds a stringer profile by owner on either platform`() = runBlocking {
        fake.seed("stringers/ios", mapOf("createdBy" to "u1", "name" to "iOS"))
        fake.seed("stringers/android", mapOf("userId" to "u2", "name" to "Android"))
        assertEquals("ios", repo.fetchStringerByUserId("u1")!!.id)
        assertEquals("android", repo.fetchStringerByUserId("u2")!!.id)
    }

    @Test
    fun `reads an older iOS order that used clientId, stringType and dollar strings`() = runBlocking {
        fake.seed(
            "stringerOrders/o1", mapOf(
                "stringerId" to "s1", "clientId" to "u2", "clientName" to "Sam",
                "stringType" to "BG80", "stringCost" to "$12", "laborCost" to "$10",
                "status" to "accepted", "statusHistory" to mapOf("accepted" to Timestamp(Date(5_000)))
            )
        )
        val o = repo.fetchOrder("o1")!!
        assertEquals("u2", o.buyerId)
        assertEquals("Sam", o.buyerName)
        assertEquals("BG80", o.selectedString)
        assertEquals(22.0, o.total, 0.0)
        assertEquals(Date(5_000), o.stageTimestamp("accepted"))
    }

    @Test
    fun `an order placed from the current iOS app is attributed to its buyer`() = runBlocking {
        // iOS submitStringerOrder stores the buyer's uid as "createdBy" (no clientId/buyerId).
        fake.seed(
            "stringerOrders/o2", mapOf(
                "stringerId" to "s1", "createdBy" to "u2", "buyerName" to "Sam",
                "racketName" to "Astrox 88D", "selectedString" to "BG80", "status" to "placed"
            )
        )
        val o = repo.fetchOrder("o2")!!
        assertEquals("Sam", o.buyerName)
        assertEquals("u2", o.buyerId)
    }

    @Test
    fun `order total is stored as a US dollar string on any phone locale`() = runBlocking {
        withDefaults("Europe/Berlin", Locale.GERMANY) {
            repo.placeOrder(
                stringerId = "s1", stringerUserId = "u1", stringerName = "Raj", buyerId = "u2", buyerName = "Sam",
                racketName = "Astrox 88D", hasOwnString = false, selectedString = "BG80", timelinePreference = "No rush",
                tension = 26, stringCost = 12.5, laborCost = 10.0, notes = null
            ).getOrThrow()
        }
        assertEquals("$22.50", fake.docsIn("stringerOrders").values.single()["orderTotal"])
    }

    @Test
    fun `new orders are written for both apps`() = runBlocking {
        repo.placeOrder(
            stringerId = "s1", stringerUserId = "u1", stringerName = "Raj", buyerId = "u2", buyerName = "Sam",
            racketName = "Astrox 88D", hasOwnString = true, selectedString = "", timelinePreference = "ASAP",
            tension = 27, stringCost = 0.0, laborCost = 15.0, notes = "Knot at 4 o'clock"
        ).getOrThrow()
        val (id, doc) = fake.docsIn("stringerOrders").entries.single().toPair()
        assertEquals("u2", doc["buyerId"])
        assertEquals("u2", doc["clientId"])
        assertEquals("placed", doc["status"])
        assertEquals(listOf(id), repo.fetchOrdersForBuyer("u2").map { it.id })
        assertEquals(listOf(id), repo.fetchOrdersForStringer("s1").map { it.id })
    }

    @Test
    fun `Android orders name the buyer the way iOS reads it, and iOS orders reach the buyer's list`() = runBlocking {
        repo.placeOrder(
            stringerId = "s1", stringerUserId = "u1", stringerName = "Raj", buyerId = "u2", buyerName = "Sam",
            racketName = "Astrox 88D", hasOwnString = false, selectedString = "BG80", timelinePreference = "No rush",
            tension = 26, stringCost = 12.0, laborCost = 10.0, notes = null
        ).getOrThrow()
        assertEquals("u2", fake.docsIn("stringerOrders").values.single()["createdBy"])
        fake.seed("stringerOrders/ios", mapOf("stringerId" to "s1", "createdBy" to "u2", "buyerName" to "Sam", "status" to "placed"))
        fake.seed("stringerOrders/old-ios", mapOf("stringerId" to "s1", "clientId" to "u2", "clientName" to "Sam", "status" to "placed"))
        assertEquals(3, repo.fetchOrdersForBuyer("u2").size)
    }

    @Test
    fun `status updates are timestamped for the order timeline`() = runBlocking {
        fake.seed("stringerOrders/o1", mapOf("stringerId" to "s1", "status" to "accepted"))
        repo.updateOrderStatus("o1", "ready_for_pickup").getOrThrow()
        val o = repo.fetchOrder("o1")!!
        assertEquals("ready_for_pickup", o.status)
        assertNotNull(o.stageTimestamp("ready_for_pickup"))
        assertTrue(o.isStageReached("stringing"))
    }
}
