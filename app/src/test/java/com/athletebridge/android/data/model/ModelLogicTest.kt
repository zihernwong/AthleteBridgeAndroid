package com.athletebridge.android.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Date

class CoachTierTest {

    @Test
    fun `fromString is case-insensitive and defaults to free`() {
        assertEquals(CoachTier.PRO, CoachTier.fromString("PRO"))
        assertEquals(CoachTier.PLUS, CoachTier.fromString("Plus"))
        assertEquals(CoachTier.FREE, CoachTier.fromString("platinum"))
        assertEquals(CoachTier.FREE, CoachTier.fromString(""))
    }

    @Test
    fun `effective tier is the higher of subscription and lifetime comp`() {
        assertEquals(CoachTier.PRO, CoachTier.effective("free", "pro"))
        assertEquals(CoachTier.PRO, CoachTier.effective("pro", "plus"))
        assertEquals(CoachTier.PLUS, CoachTier.effective(null, "plus"))
        assertEquals(CoachTier.FREE, CoachTier.effective(null, null))
    }

    @Test
    fun `feature gates match the iOS matrix`() {
        val plusFeatures = listOf("revenue_insights", "paymentSummary", "earningsForecaster", "metricHistory", "recommendToClients")
        for (f in plusFeatures) {
            assertFalse(f, CoachTier.FREE.hasAccess(f))
            assertTrue(f, CoachTier.PLUS.hasAccess(f))
            assertTrue(f, CoachTier.PRO.hasAccess(f))
        }
        assertFalse(CoachTier.PLUS.hasAccess("listing_visibility"))
        assertTrue(CoachTier.PRO.hasAccess("listing_visibility"))
        assertTrue(CoachTier.FREE.hasAccess("messaging"))
    }
}

class CoachTest {

    @Test
    fun `rate range takes precedence over hourly rate`() {
        val coach = Coach(hourlyRate = 40.0, rateRange = listOf(50.0, 80.0))
        assertEquals(50.0, coach.minimumRate!!, 0.0)
        assertEquals(80.0, coach.maximumRate!!, 0.0)
    }

    @Test
    fun `single-value or empty rate range falls back sensibly`() {
        assertEquals(60.0, Coach(rateRange = listOf(60.0)).maximumRate!!, 0.0)
        assertEquals(45.0, Coach(hourlyRate = 45.0, rateRange = emptyList()).minimumRate!!, 0.0)
        assertEquals(45.0, Coach(hourlyRate = 45.0, rateRange = emptyList()).maximumRate!!, 0.0)
        assertNull(Coach().minimumRate)
    }

    @Test
    fun `blank names are not valid`() {
        assertFalse(Coach(name = "   ").hasValidName)
        assertTrue(Coach(name = "Mei").hasValidName)
    }
}

class BookingItemTest {

    @Test
    fun `single booking exposes its one coach and client`() {
        val b = BookingItem(coachID = "c1", coachName = "Mei", clientID = "u1", clientName = "Alex")
        assertEquals(listOf("c1"), b.allCoachIDs)
        assertEquals(listOf("Mei"), b.allCoachNames)
        assertEquals(listOf("u1"), b.allClientIDs)
        assertEquals(listOf("Alex"), b.allClientNames)
    }

    @Test
    fun `group booking lists win over the single fields`() {
        val b = BookingItem(coachID = "c1", coachIDs = listOf("c1", "c2"), clientID = "u1", clientIDs = listOf("u1", "u2"))
        assertEquals(listOf("c1", "c2"), b.allCoachIDs)
        assertEquals(listOf("u1", "u2"), b.allClientIDs)
    }

    @Test
    fun `empty ids never leak into participant lists`() {
        val b = BookingItem(coachID = "", coachIDs = emptyList(), clientID = "", coachName = "")
        assertTrue(b.allCoachIDs.isEmpty())
        assertTrue(b.allClientIDs.isEmpty())
        assertTrue(b.allCoachNames.isEmpty())
    }
}

class StringerOrderTest {

    @Test
    fun `total is string plus labor`() {
        assertEquals(27.5, StringerOrder(stringCost = 12.5, laborCost = 15.0).total, 0.0)
    }

    @Test
    fun `timeline stages reached follow the pipeline`() {
        val order = StringerOrder(status = "stringing")
        assertTrue(order.isStageReached("placed"))
        assertTrue(order.isStageReached("accepted"))
        assertTrue(order.isStageReached("stringing"))
        assertFalse(order.isStageReached("ready_for_pickup"))
        assertFalse(order.isStageReached("not_a_stage"))
    }

    @Test
    fun `completed orders show every stage and declined orders show none`() {
        val completed = StringerOrder(status = "completed")
        val declined = StringerOrder(status = "declined")
        for (stage in completed.timelineStages) {
            assertTrue(stage, completed.isStageReached(stage))
            assertFalse(stage, declined.isStageReached(stage))
        }
    }

    @Test
    fun `status matching ignores case`() {
        assertTrue(StringerOrder(status = "Ready_For_Pickup").isStageReached("ready_for_pickup"))
        assertTrue(StringerOrder(status = "PLACED").isPending)
        assertTrue(StringerOrder(status = "Stringing").isActive)
        assertFalse(StringerOrder(status = "picked_up").isActive)
    }

    @Test
    fun `display names, including unknown statuses`() {
        assertEquals("Ready For Pickup", StringerOrder(status = "ready_for_pickup").statusDisplayName)
        assertEquals("Awaiting Strings", StringerOrder(status = "awaiting_strings").statusDisplayName)
    }

    @Test
    fun `placed stage falls back to createdAt`() {
        val created = Date(1_000)
        val accepted = Date(2_000)
        val order = StringerOrder(createdAt = created, statusHistory = mapOf("accepted" to accepted))
        assertEquals(created, order.stageTimestamp("placed"))
        assertEquals(accepted, order.stageTimestamp("accepted"))
        assertNull(order.stageTimestamp("stringing"))
    }
}

class PlaceToPlayTest {

    private val place = PlaceToPlay(
        memberList = listOf("m1"),
        pendingMembers = listOf("p1"),
        createdBy = "creator",
        contactUserId = "contact",
        adminIds = listOf("admin")
    )

    @Test
    fun `membership checks`() {
        assertTrue(place.isMember("m1"))
        assertFalse(place.isMember("p1"))
        assertTrue(place.isPending("p1"))
        assertEquals(1, place.memberCount)
    }

    @Test
    fun `admins include co-admins, the contact and the creator`() {
        assertTrue(place.isAdmin("admin"))
        assertTrue(place.isAdmin("contact"))
        assertTrue(place.isAdmin("creator"))
        assertFalse(place.isAdmin("m1"))
    }

    @Test
    fun `a signed-out user is never an admin, even of a club with no creator`() {
        assertFalse(PlaceToPlay(createdBy = "", contactUserId = null).isAdmin(""))
    }
}

class BadmintonStringerTest {

    @Test
    fun `string options are sorted for display`() {
        val stringer = BadmintonStringer(strings = mapOf("BG80" to 12.0, "Aerobite" to 14.0, "Exbolt 63" to 15.0))
        assertEquals(listOf("Aerobite", "BG80", "Exbolt 63"), stringer.stringOptions)
    }
}
