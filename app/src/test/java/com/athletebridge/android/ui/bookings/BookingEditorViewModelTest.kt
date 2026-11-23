package com.athletebridge.android.ui.bookings

import com.athletebridge.android.data.model.Client
import com.athletebridge.android.data.model.Coach
import com.athletebridge.android.data.model.PlaceToPlay
import com.athletebridge.android.data.repository.AuthRepository
import com.athletebridge.android.data.repository.AwayTimeRepository
import com.athletebridge.android.data.repository.BookingRepository
import com.athletebridge.android.data.repository.ClientRepository
import com.athletebridge.android.data.repository.CoachRepository
import com.athletebridge.android.data.repository.NotificationRepository
import com.athletebridge.android.data.repository.PlaceRepository
import com.athletebridge.android.testutil.CHICAGO
import com.athletebridge.android.testutil.MainDispatcherRule
import com.athletebridge.android.testutil.fakeUser
import com.athletebridge.android.testutil.hoursFromNow
import com.athletebridge.android.testutil.localHour
import com.athletebridge.android.testutil.withDefaults
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Date

class BookingEditorViewModelTest {

    @get:Rule val main = MainDispatcherRule()

    private val bookingRepo = mockk<BookingRepository>()
    private val awayRepo = mockk<AwayTimeRepository>()
    private val coachRepo = mockk<CoachRepository>()
    private val clientRepo = mockk<ClientRepository>()
    private val authRepo = mockk<AuthRepository>()
    private val notifications = mockk<NotificationRepository>(relaxed = true)
    private val placeRepo = mockk<PlaceRepository>()

    private data class Created(val start: Date, val end: Date, val placeId: String?, val groupId: String?)
    private val created = mutableListOf<Created>()

    @Before
    fun setUp() {
        every { authRepo.currentUser } returns fakeUser("u1", name = "Alex")
        coEvery { clientRepo.fetchClient("u1", any()) } returns Client(id = "u1", name = "Alex Chen")
        coEvery { placeRepo.fetchPlaces() } returns listOf(PlaceToPlay(id = "p1", name = "Egret"), PlaceToPlay(id = "p2", name = "Elsewhere"))
        coEvery { bookingRepo.fetchBookingsForCoach(any(), any(), any()) } returns emptyList()
        coEvery { awayRepo.fetchAwayTimes(any(), any(), any()) } returns emptyList()
        coEvery { bookingRepo.createBooking(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } answers {
            created += Created(arg(4), arg(5), arg(7), arg(9))
            Result.success("b${created.size}")
        }
    }

    private fun editor(linkedPlaces: List<String> = listOf("p1")): BookingEditorViewModel {
        coEvery { coachRepo.fetchCoach("c1", any()) } returns Coach(id = "c1", name = "Mei", linkedPlaceIds = linkedPlaces)
        return BookingEditorViewModel(bookingRepo, awayRepo, coachRepo, clientRepo, authRepo, notifications, placeRepo)
            .also { it.loadCoach("c1") }
    }

    private fun BookingEditorViewModel.book(start: Date, minutes: Int = 60, placeId: String? = "p1", weeks: Int = 1) =
        createBooking("c1", start, Date(start.time + minutes * 60_000L), "Egret", placeId, notes = null, repeatWeeks = weeks)

    private fun assertRejected(vm: BookingEditorViewModel, messagePart: String) {
        assertTrue("error was ${vm.uiState.value.error}", vm.uiState.value.error?.contains(messagePart) == true)
        assertTrue(created.isEmpty())
    }

    // ── Validation ──────────────────────────────────────────────────────────

    @Test
    fun `only the coach's preferred locations can be booked`() {
        val vm = editor()
        assertEquals(listOf("p1"), vm.uiState.value.coachPlaces.map { it.id })
        assertEquals("Alex Chen", vm.uiState.value.clientName)
    }

    @Test
    fun `booking a coach with no preferred locations is refused`() {
        val vm = editor(linkedPlaces = emptyList())
        vm.book(hoursFromNow(48.0))
        assertRejected(vm, "preferred locations")
    }

    @Test
    fun `a location must be picked`() {
        val vm = editor()
        vm.book(hoursFromNow(48.0), placeId = null)
        assertRejected(vm, "select a location")
    }

    @Test
    fun `sessions must end after they start and last at least an hour`() {
        val vm = editor()
        vm.book(hoursFromNow(48.0), minutes = -30)
        assertRejected(vm, "End time must be after start time")
        vm.book(hoursFromNow(48.0), minutes = 30)
        assertRejected(vm, "Minimum booking duration is 1 hour")
    }

    @Test
    fun `sessions in the past are refused`() {
        val vm = editor()
        vm.book(hoursFromNow(-2.0))
        assertRejected(vm, "in the future")
    }

    // ── Creating ────────────────────────────────────────────────────────────

    @Test
    fun `a single booking is requested and the coach is notified once`() {
        val vm = editor()
        vm.book(hoursFromNow(48.0))
        assertEquals(1, created.size)
        assertNull(created.single().groupId)
        assertEquals("p1", created.single().placeId)
        assertTrue(vm.uiState.value.bookingCreated)
        coVerify(exactly = 1) {
            notifications.sendNotification("c1", "New Booking Request", any(), "b1", "u1", "booking_request", any())
        }
    }

    @Test
    fun `a weekly series lands on the same weekday each week and shares a series id`() {
        withDefaults(CHICAGO) {
            val vm = editor()
            val noonInTwoDays = ZonedDateTime.now(ZoneId.of(CHICAGO)).plusDays(2)
                .withHour(12).withMinute(0).withSecond(0).withNano(0)
            vm.book(Date.from(noonInTwoDays.toInstant()), weeks = 4)
            assertEquals(4, created.size)
            assertNotNull(created.first().groupId)
            assertEquals(1, created.map { it.groupId }.toSet().size)
            val days = created.map { it.start.toInstant().atZone(ZoneId.of(CHICAGO)).toLocalDate() }
            days.zipWithNext().forEach { (a, b) -> assertEquals(a.plusWeeks(1), b) }
            coVerify(exactly = 1) { notifications.sendNotification("c1", any(), match { it.contains("4 weeks") }, any(), any(), any(), any()) }
        }
    }

    @Test
    fun `weekly series is capped at 12 weeks`() {
        val vm = editor()
        vm.book(hoursFromNow(48.0), weeks = 30)
        assertEquals(12, created.size)
    }

    @Test
    fun `a weekly series keeps the same start time across a daylight saving change`() {
        withDefaults(CHICAGO) {
            val zone = ZoneId.of(CHICAGO)
            // Next US clock change at least 4 days away; book 6 PM three days before it, repeating weekly.
            var transition = zone.rules.nextTransition(Instant.now())
            while (transition.instant.isBefore(Instant.now().plusSeconds(4 * 86_400))) {
                transition = zone.rules.nextTransition(transition.instant)
            }
            val sixPmBefore = ZonedDateTime.ofInstant(transition.instant, zone).toLocalDate().minusDays(3)
                .atTime(18, 0).atZone(zone)
            val vm = editor()
            vm.book(Date.from(sixPmBefore.toInstant()), weeks = 2)
            assertEquals(2, created.size)
            assertEquals(18, localHour(CHICAGO, created[0].start))
            assertEquals("the second week should still start at 6 PM", 18, localHour(CHICAGO, created[1].start))
        }
    }
}
