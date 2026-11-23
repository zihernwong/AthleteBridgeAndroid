package com.athletebridge.android.data.repository

import com.athletebridge.android.data.model.Client
import com.athletebridge.android.data.model.CoachTier
import com.athletebridge.android.testutil.FakeFirestore
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CoachRepositoryTest {

    private val fake = FakeFirestore()
    private val repo = CoachRepository(fake.instance, mockk(relaxed = true))

    private fun seedIosCoach() = fake.seed(
        "coaches/c1", mapOf(
            "FirstName" to "Mei", "LastName" to "Lin",
            "Specialties" to listOf("Footwork", "Doubles"),
            "ExperienceYears" to 7L,
            "HourlyRate" to 55L,
            "RateRange" to listOf(40L, 70.5),
            "Payments" to mapOf("venmo" to "@mei"),
            "subscriptionTier" to "free",
            "lifetimeTier" to "pro",
            "PhotoURL" to "https://img.test/mei.jpg",
            "cancellationWindowHours" to 24L,
            "linkedPlaceIds" to listOf("p1", "p2"),
            "deviceTokens" to listOf("fcm-token-1"),
            "stripeAccountId" to "acct_123"
        )
    )

    @Test
    fun `reads an iOS coach with split names, PascalCase fields and a lifetime comp`() = runBlocking {
        seedIosCoach()
        val coach = repo.fetchCoach("c1")!!
        assertEquals("Mei Lin", coach.name)
        assertEquals(listOf("Footwork", "Doubles"), coach.specialties)
        assertEquals(7, coach.experienceYears)
        assertEquals(55.0, coach.hourlyRate!!, 0.0)
        assertEquals(listOf(40.0, 70.5), coach.rateRange)
        assertEquals(mapOf("venmo" to "@mei"), coach.payments)
        assertEquals(CoachTier.PRO, coach.subscriptionTier)
        assertEquals("https://img.test/mei.jpg", coach.photoURLString)
        assertEquals(24, coach.cancellationWindowHours)
        assertEquals(listOf("p1", "p2"), coach.linkedPlaceIds)
    }

    @Test
    fun `reads a legacy Android coach that only has a name field`() = runBlocking {
        fake.seed("coaches/c2", mapOf("name" to "Raj Patel", "hourlyRate" to 40.0))
        val coach = repo.fetchCoach("c2")!!
        assertEquals("Raj Patel", coach.name)
        assertEquals(CoachTier.FREE, coach.subscriptionTier)
    }

    @Test
    fun `missing coach is null`() = runBlocking {
        assertNull(repo.fetchCoach("nobody"))
    }

    private suspend fun editProfile() {
        // ProfileViewModel builds a fresh Coach from the form and calls saveCoach.
        val loaded = repo.fetchCoach("c1")!!
        repo.saveCoach(loaded.copy(bio = "Now coaching juniors on Saturdays")).getOrThrow()
    }

    @Test
    fun `saving the profile writes the edited fields in both casings`() = runBlocking {
        seedIosCoach()
        editProfile()
        val doc = fake.doc("coaches/c1")!!
        assertEquals("Now coaching juniors on Saturdays", doc["bio"])
        assertEquals("Now coaching juniors on Saturdays", doc["Bio"])
        assertEquals("Mei", doc["FirstName"])
        assertEquals("Lin", doc["LastName"])
    }

    @Test
    fun `editing a coach profile keeps their preferred locations`() = runBlocking {
        // Without linkedPlaceIds, clients can no longer book this coach on Android or iOS.
        seedIosCoach()
        editProfile()
        assertEquals(listOf("p1", "p2"), fake.doc("coaches/c1")!!["linkedPlaceIds"])
    }

    @Test
    fun `editing a coach profile keeps their push notification tokens`() = runBlocking {
        seedIosCoach()
        editProfile()
        assertEquals(listOf("fcm-token-1"), fake.doc("coaches/c1")!!["deviceTokens"])
    }

    @Test
    fun `editing a comped coach's profile keeps the comp separate from billing`() = runBlocking {
        seedIosCoach()
        editProfile()
        val doc = fake.doc("coaches/c1")!!
        assertEquals("lifetimeTier must survive", "pro", doc["lifetimeTier"])
        assertEquals("billing tier must not be overwritten by the comp", "free", doc["subscriptionTier"])
        assertEquals("acct_123", doc["stripeAccountId"])
    }

    @Test
    fun `payment handles are saved`() = runBlocking {
        seedIosCoach()
        repo.updatePayments("c1", mapOf("venmo" to "@mei", "zelle" to "612-555-0100")).getOrThrow()
        assertEquals(mapOf("venmo" to "@mei", "zelle" to "612-555-0100"), repo.fetchCoach("c1")!!.payments)
    }
}

class ClientRepositoryTest {

    private val fake = FakeFirestore()
    private val repo = ClientRepository(fake.instance, mockk(relaxed = true))

    @Test
    fun `client names come from name, Name or FirstName plus LastName`() = runBlocking {
        fake.seed("clients/a", mapOf("Name" to "  Alex Chen "))
        fake.seed("clients/b", mapOf("FirstName" to "Sam", "LastName" to "Lee"))
        fake.seed("clients/c", mapOf("FirstName" to "Kim", "LastName" to ""))
        assertEquals("Alex Chen", repo.fetchClient("a")!!.name)
        assertEquals("Sam Lee", repo.fetchClient("b")!!.name)
        assertEquals("Kim", repo.fetchClient("c")!!.name)
    }

    @Test
    fun `availability falls back to the iOS field name`() = runBlocking {
        fake.seed("clients/a", mapOf("Availability" to listOf("Weekends")))
        assertEquals(listOf("Weekends"), repo.fetchClient("a")!!.preferredAvailability)
    }

    @Test
    fun `saving a client profile merges instead of wiping other fields`() = runBlocking {
        fake.seed("clients/a", mapOf("Name" to "Old", "deviceTokens" to listOf("tok"), "lastSeen" to fake.serverTime))
        repo.saveClient(Client(id = "a", name = "Sam Lee", skillLevel = "Intermediate")).getOrThrow()
        val doc = fake.doc("clients/a")!!
        assertEquals(listOf("tok"), doc["deviceTokens"])
        assertEquals("Sam", doc["FirstName"])
        assertEquals("Lee", doc["LastName"])
        assertEquals("Intermediate", doc["SkillLevel"])
    }
}
