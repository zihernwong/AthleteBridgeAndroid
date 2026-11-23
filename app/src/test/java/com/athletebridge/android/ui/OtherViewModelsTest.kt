package com.athletebridge.android.ui

import com.athletebridge.android.data.model.BookingItem
import com.athletebridge.android.data.model.Client
import com.athletebridge.android.data.model.Coach
import com.athletebridge.android.data.model.PlaceToPlay
import com.athletebridge.android.data.model.PlayerToPlayWith
import com.athletebridge.android.data.model.SignupEvent
import com.athletebridge.android.data.model.Tournament
import com.athletebridge.android.data.model.TournamentParticipantInfo
import com.athletebridge.android.data.repository.AuthRepository
import com.athletebridge.android.data.repository.BookingRepository
import com.athletebridge.android.data.repository.ClientRepository
import com.athletebridge.android.data.repository.CoachRepository
import com.athletebridge.android.data.repository.NotificationRepository
import com.athletebridge.android.data.repository.PlayerRepository
import com.athletebridge.android.data.repository.RatingSummary
import com.athletebridge.android.data.repository.ReviewRepository
import com.athletebridge.android.data.repository.StringerRepository
import com.athletebridge.android.data.repository.TournamentRepository
import com.athletebridge.android.data.repository.UserDirectoryRepository
import com.athletebridge.android.testutil.FakeFirestore
import com.athletebridge.android.testutil.MainDispatcherRule
import com.athletebridge.android.testutil.fakeUser
import com.athletebridge.android.ui.coach.CoachViewModel
import com.athletebridge.android.ui.places.ClubManagementUiState
import com.athletebridge.android.ui.players.PlayersToPlayWithUiState
import com.athletebridge.android.ui.players.PlayersToPlayWithViewModel
import com.athletebridge.android.ui.roster.RosterViewModel
import com.athletebridge.android.ui.stringing.StringerOrderDetailViewModel
import com.athletebridge.android.ui.tournaments.TournamentPartnerViewModel
import com.google.firebase.auth.FirebaseAuth
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.util.Date

class TournamentPartnerViewModelTest {

    @get:Rule val main = MainDispatcherRule()

    private val tournament = Tournament(
        id = "t1", name = "MN Open",
        participants = mapOf(
            "p1" to TournamentParticipantInfo("Female", listOf("Mixed Doubles"), listOf("A")),
            "p2" to TournamentParticipantInfo("Female", listOf("Mixed Doubles"), listOf("B")),
            "p3" to TournamentParticipantInfo("Male", listOf("Men's Doubles"), listOf("A"))
        )
    )

    private fun vm(): TournamentPartnerViewModel {
        val tournaments = mockk<TournamentRepository> { coEvery { fetchTournaments() } returns listOf(tournament, Tournament(id = "t2", name = "Chicago Classic")) }
        val clients = mockk<ClientRepository> {
            coEvery { fetchAllClients() } returns listOf(Client(id = "p1", name = "Pat"), Client(id = "p2", name = "Quinn"), Client(id = "p3", name = "Ravi"))
        }
        val coaches = mockk<CoachRepository> { coEvery { fetchAllCoaches() } returns emptyList() }
        val auth = mockk<AuthRepository> { every { currentUser } returns fakeUser("me") }
        return TournamentPartnerViewModel(tournaments, clients, coaches, auth).also {
            it.loadData()
            it.selectTournament(tournament)
        }
    }

    private fun TournamentPartnerViewModel.partners() = uiState.value.matchingPartners.map { it.name }.sorted()

    @Test
    fun `partner list follows your events and gender`() {
        val vm = vm()
        vm.setEvents(setOf("Mixed Doubles"))
        assertEquals(listOf("Pat", "Quinn"), vm.partners())
        vm.setGender("Female")
        assertEquals(emptyList<String>(), vm.partners())
        vm.setEvents(setOf("Men's Doubles"))
        vm.setGender("Male")
        assertEquals(listOf("Ravi"), vm.partners())
    }

    @Test
    fun `partner list follows your skill levels`() {
        val vm = vm()
        vm.setEvents(setOf("Mixed Doubles"))
        vm.setSkillLevels(setOf("A"))
        assertEquals(listOf("Pat"), vm.partners())
    }

    @Test
    fun `tournament search is case-insensitive`() {
        val vm = vm()
        vm.updateSearch("chicago")
        assertEquals(listOf("t2"), vm.uiState.value.filteredTournaments.map { it.id })
        vm.updateSearch("")
        assertEquals(2, vm.uiState.value.filteredTournaments.size)
    }
}

class RosterViewModelTest {

    @get:Rule val main = MainDispatcherRule()

    private val directory = mockk<UserDirectoryRepository> {
        coEvery { fetchUserPhotoUrl(any()) } returns null
        coEvery { fetchLastSeen(any()) } returns emptyMap()
    }

    @Test
    fun `coach roster totals confirmed revenue per client, including group participants`() {
        val bookings = mockk<BookingRepository> {
            every { observeCoachBookings("c1") } returns flowOf(
                listOf(
                    BookingItem(id = "1", clientID = "u1", clientName = "Alex", status = "confirmed", rateUSD = 50.0, startAt = Date(1_000)),
                    BookingItem(id = "2", clientID = "u1", clientName = "Alex", status = "cancelled", rateUSD = 50.0, startAt = Date(2_000)),
                    BookingItem(
                        id = "3", clientID = "u1", clientIDs = listOf("u1", "u2"), clientNames = listOf("Alex", "Sam"),
                        status = "Confirmed", rateUSD = 40.0, startAt = Date(3_000)
                    )
                )
            )
        }
        val auth = mockk<AuthRepository> { every { currentUser } returns fakeUser("c1") }
        val vm = RosterViewModel(bookings, auth, mockk(relaxed = true), directory).also { it.loadClientRoster() }
        val entries = vm.uiState.value.entries.associateBy { it.id }
        assertEquals(90.0, entries.getValue("u1").totalPaid, 0.0)
        assertEquals(40.0, entries.getValue("u2").totalPaid, 0.0)
        assertEquals("Sam", entries.getValue("u2").name)
        assertEquals(Date(3_000), entries.getValue("u1").lastSessionDate)
    }

    @Test
    fun `client roster lists each coach once, most recent first`() {
        val bookings = mockk<BookingRepository> {
            every { observeClientBookings("u1") } returns flowOf(
                listOf(
                    BookingItem(id = "1", coachID = "c1", coachName = "Mei", startAt = Date(1_000)),
                    BookingItem(id = "2", coachID = "c2", coachName = "Raj", startAt = Date(5_000)),
                    BookingItem(id = "3", coachID = "c1", coachName = "Mei", startAt = Date(3_000))
                )
            )
        }
        val auth = mockk<AuthRepository> { every { currentUser } returns fakeUser("u1") }
        val vm = RosterViewModel(bookings, auth, mockk(relaxed = true), directory).also { it.loadCoachRoster() }
        assertEquals(listOf("c2", "c1"), vm.uiState.value.entries.map { it.id })
        assertEquals(2, vm.uiState.value.entries.single { it.id == "c1" }.totalSessions)
    }
}

class CoachSearchTest {

    @get:Rule val main = MainDispatcherRule()

    private val coaches = listOf(
        Coach(id = "1", name = "Mei Lin", specialties = listOf("Footwork & Agility", "Doubles"), availability = listOf("Weekends")),
        Coach(id = "2", name = "Raj Patel", specialties = listOf("Singles"), availability = listOf("Weekday evenings")),
        Coach(id = "3", name = "Meiko Tan", specialties = listOf("Juniors"), availability = listOf("Weekends"))
    )

    @Test
    fun `search, goals and availability filters combine`() {
        val vm = CoachViewModel(
            mockk<CoachRepository> { coEvery { fetchAllCoaches() } returns coaches },
            mockk<ReviewRepository> { coEvery { fetchRatingSummaries() } returns mapOf("1" to RatingSummary(4.5, 2)) }
        )
        assertEquals(listOf("1", "3"), vm.filterCoaches(" mei ", emptyList(), emptyList()).map { it.id })
        assertEquals(listOf("1"), vm.filterCoaches("", listOf("footwork"), emptyList()).map { it.id })
        assertEquals(listOf("2"), vm.filterCoaches("", emptyList(), listOf("Weekday evenings")).map { it.id })
        assertEquals(listOf("3"), vm.filterCoaches("mei", listOf("Juniors"), listOf("Weekends")).map { it.id })
        assertEquals(4.5, vm.uiState.value.ratingSummaries.getValue("1").average, 0.0)
    }
}

class PlayersFilterTest {

    @get:Rule val main = MainDispatcherRule()

    private val players = listOf(
        PlayerToPlayWith(id = "a", name = "Alex", skillLevel = "Intermediate", city = "Minneapolis", availability = listOf("Weekends")),
        PlayerToPlayWith(id = "b", name = "Sam", skillLevel = "Advanced", city = "St. Paul", availability = listOf("Weeknights"))
    )

    @Test
    fun `filters combine`() {
        val s = PlayersToPlayWithUiState(players = players)
        assertEquals(listOf("a", "b"), s.filteredPlayers.map { it.id })
        assertEquals(listOf("b"), s.copy(filterSkillLevel = "Advanced").filteredPlayers.map { it.id })
        assertEquals(listOf("a"), s.copy(filterAvailability = "Weekends").filteredPlayers.map { it.id })
        assertEquals(listOf("b"), s.copy(searchQuery = "PAUL").filteredPlayers.map { it.id })
        assertEquals(emptyList<String>(), s.copy(searchQuery = "paul", filterSkillLevel = "Intermediate").filteredPlayers.map { it.id })
    }

    @Test
    fun `tapping the active filter chip again clears it and your own listing is hidden`() {
        val repo = mockk<PlayerRepository> {
            coEvery { fetchPlayers() } returns players + PlayerToPlayWith(id = "me-listing", userId = "me", name = "Me")
            coEvery { fetchPlayerByUserId("me") } returns PlayerToPlayWith(id = "me-listing", userId = "me")
        }
        val vm = PlayersToPlayWithViewModel(repo, mockk<FirebaseAuth> { every { currentUser } returns fakeUser("me") })
        vm.loadData()
        assertEquals(listOf("a", "b"), vm.uiState.value.players.map { it.id })
        vm.setFilterSkillLevel("Advanced")
        assertEquals("Advanced", vm.uiState.value.filterSkillLevel)
        vm.setFilterSkillLevel("Advanced")
        assertEquals("", vm.uiState.value.filterSkillLevel)
    }
}

class ClubManagementStateTest {

    @Test
    fun `upcoming events only include future, dated events for that club`() {
        val now = System.currentTimeMillis()
        val state = ClubManagementUiState(
            events = listOf(
                SignupEvent(id = "past", placeId = "p1", eventDate = Date(now - 86_400_000)),
                SignupEvent(id = "next", placeId = "p1", eventDate = Date(now + 86_400_000)),
                SignupEvent(id = "other", placeId = "p2", eventDate = Date(now + 86_400_000)),
                SignupEvent(id = "undated", placeId = "p1")
            )
        )
        assertEquals(listOf("next"), state.upcomingEvents("p1").map { it.id })
    }

    @Test
    fun `the club contact can manage the club`() {
        val state = ClubManagementUiState(places = listOf(PlaceToPlay(id = "p1", contactUserId = "lead")))
        assertEquals(listOf("p1"), state.myPlaces("lead").map { it.id })
    }

    @Test
    fun `co-admins added on iOS can manage the club on Android too`() {
        // iOS PlacesToPlayContactView lists clubs where place.isAdmin(uid); PlaceToPlay.isAdmin already models this.
        val state = ClubManagementUiState(places = listOf(PlaceToPlay(id = "p1", contactUserId = "lead", adminIds = listOf("co-admin"))))
        assertEquals(listOf("p1"), state.myPlaces("co-admin").map { it.id })
    }
}

class StringerOrderNotificationTest {

    @get:Rule val main = MainDispatcherRule()

    private val fake = FakeFirestore()
    private val notifications = mockk<NotificationRepository>(relaxed = true)
    private val auth = mockk<FirebaseAuth> { every { currentUser } returns fakeUser("stringer-uid") }

    private fun vm(orderId: String) =
        StringerOrderDetailViewModel(StringerRepository(fake.instance), notifications, auth).also { it.loadOrder(orderId) }

    @Test
    fun `buyer of an Android order hears when the racket is ready`() {
        fake.seed(
            "stringerOrders/o1", mapOf(
                "stringerId" to "s1", "stringerUserId" to "stringer-uid", "stringerName" to "Raj",
                "buyerId" to "u2", "clientId" to "u2", "buyerName" to "Sam", "status" to "stringing"
            )
        )
        val vm = vm("o1")
        vm.updateStatus("ready_for_pickup")
        coVerify { notifications.sendNotification("u2", "Ready For Pickup", any(), null, null, "stringer_order", mapOf("stringerOrderId" to "o1")) }
        assertEquals("ready_for_pickup", vm.uiState.value.order?.status)
    }

    @Test
    fun `buyer of an iOS order hears when the racket is ready`() {
        // Orders placed from the iOS app store the buyer as "createdBy".
        fake.seed(
            "stringerOrders/o2", mapOf(
                "stringerId" to "s1", "createdBy" to "u2", "buyerName" to "Sam", "status" to "stringing"
            )
        )
        vm("o2").updateStatus("ready_for_pickup")
        coVerify { notifications.sendNotification("u2", "Ready For Pickup", any(), any(), any(), "stringer_order", any()) }
    }
}
