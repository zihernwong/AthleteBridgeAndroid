package com.athletebridge.android.ui.bookings

import android.content.Context
import com.athletebridge.android.data.model.BookingItem
import com.athletebridge.android.data.model.Coach
import com.athletebridge.android.data.model.CoachTier
import com.athletebridge.android.data.repository.AuthRepository
import com.athletebridge.android.data.repository.BookingRepository
import com.athletebridge.android.data.repository.CoachRepository
import com.athletebridge.android.data.repository.NotificationRepository
import com.athletebridge.android.testutil.FakeFirestore
import com.athletebridge.android.testutil.MainDispatcherRule
import com.athletebridge.android.testutil.fakeUser
import com.athletebridge.android.testutil.hoursFromNow
import com.athletebridge.android.util.ReminderScheduler
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.util.Date

/** Client/coach "confirmed bookings" screens: cancelling, rescheduling and the cancellation window. */
class ConfirmedBookingsViewModelTest {

    @get:Rule val main = MainDispatcherRule()

    private val bookingRepo = mockk<BookingRepository>()
    private val coachRepo = mockk<CoachRepository>()
    private val notifications = mockk<NotificationRepository>(relaxed = true)
    private val clientAuth = mockk<AuthRepository> { every { currentUser } returns fakeUser("u1") }
    private val coachAuth = mockk<AuthRepository> { every { currentUser } returns fakeUser("c1") }

    private fun booking(startsIn: Double, prepaid: Boolean = false) = BookingItem(
        id = "b1", coachID = "c1", coachName = "Mei", clientID = "u1", clientName = "Alex",
        startAt = hoursFromNow(startsIn), endAt = hoursFromNow(startsIn + 1), status = "confirmed",
        requiresPaymentUpfront = prepaid
    )

    private fun clientVm(vararg bookings: BookingItem, windowHours: Int = 24): ClientConfirmedBookingsViewModel {
        every { bookingRepo.observeClientBookings("u1") } returns flowOf(bookings.toList())
        coEvery { coachRepo.fetchCoach("c1", any()) } returns Coach(id = "c1", cancellationWindowHours = windowHours)
        return ClientConfirmedBookingsViewModel(bookingRepo, clientAuth, coachRepo, notifications).also { it.loadBookings() }
    }

    @Test
    fun `only confirmed bookings are listed, newest first`() {
        val later = booking(72.0).copy(id = "later")
        val sooner = booking(24.0).copy(id = "sooner")
        val vm = clientVm(sooner, later, booking(10.0).copy(id = "cancelled", status = "cancelled"))
        assertEquals(listOf("later", "sooner"), vm.uiState.value.bookings.map { it.id })
    }

    @Test
    fun `cancellation window uses the coach's policy`() {
        val vm = clientVm(booking(10.0), windowHours = 24)
        assertTrue(vm.isInsideCancellationWindow(booking(10.0)))
        assertFalse(vm.isInsideCancellationWindow(booking(30.0)))
        val noPolicy = clientVm(booking(10.0), windowHours = 0)
        assertFalse(noPolicy.isInsideCancellationWindow(booking(10.0)))
    }

    @Test
    fun `cancelling a prepaid session inside the window forfeits the deposit`() {
        coEvery { bookingRepo.cancelBooking(any(), any(), any(), any()) } returns Result.success(Unit)
        val vm = clientVm(booking(10.0, prepaid = true))
        vm.cancelBooking(booking(10.0, prepaid = true))
        vm.cancelBooking(booking(48.0, prepaid = true))
        vm.cancelBooking(booking(10.0, prepaid = false))
        coVerify(exactly = 1) { bookingRepo.cancelBooking("b1", "c1", "u1", true) }
        coVerify(exactly = 2) { bookingRepo.cancelBooking("b1", "c1", "u1", false) }
        coVerify(exactly = 3) { notifications.sendNotification("c1", "Booking Cancelled", any(), "b1", "u1", "booking_cancelled", any()) }
    }

    @Test
    fun `a client cancel that fails to save does not tell the coach it was cancelled`() {
        coEvery { bookingRepo.cancelBooking(any(), any(), any(), any()) } returns Result.failure(RuntimeException("offline"))
        val vm = clientVm(booking(48.0))
        vm.cancelBooking(booking(48.0))
        coVerify(exactly = 0) { notifications.sendNotification(any(), "Booking Cancelled", any(), any(), any(), any(), any()) }
        assertNotNull("the user should see the failure", vm.uiState.value.error)
    }

    @Test
    fun `a coach reschedule that fails to save does not notify the client`() {
        every { bookingRepo.observeCoachBookings("c1") } returns flowOf(listOf(booking(48.0)))
        coEvery { bookingRepo.rescheduleBooking(any(), any(), any(), any(), any()) } returns Result.failure(RuntimeException("offline"))
        val vm = CoachConfirmedBookingsViewModel(bookingRepo, coachAuth, notifications).also { it.loadBookings() }
        vm.rescheduleBooking(booking(48.0), hoursFromNow(72.0), hoursFromNow(73.0))
        coVerify(exactly = 0) { notifications.sendNotification(any(), "Booking Rescheduled", any(), any(), any(), any(), any()) }
        assertNotNull("the coach should see the failure", vm.uiState.value.error)
    }
}

/** Real BookingRepository on the in-memory Firestore: group bookings touch five mirror documents. */
class GroupBookingCancellationTest {

    @get:Rule val main = MainDispatcherRule()

    private val fake = FakeFirestore()
    private val repo = BookingRepository(fake.instance)
    private val notifications = mockk<NotificationRepository>(relaxed = true)
    private val start = Date(System.currentTimeMillis() + 7 * 86_400_000L)

    private fun confirmedGroupBooking(): Pair<String, List<String>> = runBlocking {
        val id = repo.createGroupBooking(
            clientId = "u1", clientName = "Alex", coachIds = listOf("c1", "c2"), coachNames = listOf("Mei", "Raj"),
            clientIds = listOf("u1", "u2"), clientNames = listOf("Alex", "Sam"),
            startAt = start, endAt = Date(start.time + 3_600_000), location = "Egret", notes = null
        ).getOrThrow()
        val paths = listOf("bookings/$id", "coaches/c1/bookings/$id", "coaches/c2/bookings/$id", "clients/u1/bookings/$id", "clients/u2/bookings/$id")
        for (p in paths) fake.seed(p, fake.doc(p)!! + mapOf("status" to "confirmed", "Status" to "confirmed"))
        id to paths
    }

    private fun assertCancelledEverywhere(paths: List<String>) {
        val stale = paths.filter { fake.doc(it)!!["Status"] != "cancelled" }
        assertTrue("still shows as confirmed for: $stale", stale.isEmpty())
    }

    @Test
    fun `a client cancelling a group booking cancels it for every participant`() {
        val (id, paths) = confirmedGroupBooking()
        val booking = runBlocking { repo.fetchClientBooking(id, "u1")!! }
        val auth = mockk<AuthRepository> { every { currentUser } returns fakeUser("u1") }
        val vm = ClientConfirmedBookingsViewModel(repo, auth, mockk(relaxed = true), notifications)
        vm.cancelBooking(booking)
        assertCancelledEverywhere(paths)
    }

    @Test
    fun `a coach cancelling a group booking cancels it for every participant`() {
        val (id, paths) = confirmedGroupBooking()
        val booking = runBlocking { repo.fetchBooking(id, "c1")!! }
        val auth = mockk<AuthRepository> { every { currentUser } returns fakeUser("c1") }
        val vm = CoachConfirmedBookingsViewModel(repo, auth, notifications)
        vm.cancelBooking(booking)
        assertCancelledEverywhere(paths)
    }
}

class AcceptBookingViewModelTest {

    @get:Rule val main = MainDispatcherRule()

    private val bookingRepo = mockk<BookingRepository>()
    private val coachRepo = mockk<CoachRepository>()
    private val notifications = mockk<NotificationRepository>(relaxed = true)
    private val auth = mockk<AuthRepository> { every { currentUser } returns fakeUser("c1") }

    private fun vm(booking: BookingItem, tier: CoachTier): AcceptBookingViewModel {
        coEvery { bookingRepo.fetchBooking(booking.id, "c1") } returns booking
        coEvery { coachRepo.fetchCoach("c1", any()) } returns Coach(id = "c1", hourlyRate = 60.0, subscriptionTier = tier)
        coEvery { bookingRepo.acceptBooking(any(), any(), any(), any(), any(), any()) } returns Result.success(Unit)
        coEvery { bookingRepo.acceptGroupBookingAsCoach(any(), any(), any(), any(), any(), any()) } returns Result.success(Unit)
        return AcceptBookingViewModel(bookingRepo, coachRepo, auth, notifications).also { it.loadBooking(booking.id) }
    }

    private val single = BookingItem(id = "b1", coachID = "c1", clientID = "u1", status = "requested")
    private val group = BookingItem(id = "g1", isGroupBooking = true, coachIDs = listOf("c1", "c2"), clientIDs = listOf("u1", "u2"), status = "requested")

    @Test
    fun `loading shows the booking and the coach's default rate`() {
        val vm = vm(single, CoachTier.FREE)
        assertEquals("b1", vm.uiState.value.booking?.id)
        assertEquals(60.0, vm.uiState.value.coachHourlyRate!!, 0.0)
    }

    @Test
    fun `Pro coaches collect payment before the booking is confirmed`() {
        vm(single, CoachTier.PRO).acceptBooking("b1", 75.0, "See you")
        coVerify { bookingRepo.acceptBooking("b1", "c1", "u1", 75.0, "See you", true) }
        coVerify { notifications.sendNotification("u1", "Payment Required", any(), "b1", "c1", "booking_accepted", any()) }
    }

    @Test
    fun `Free coaches go straight to client confirmation`() {
        vm(single, CoachTier.FREE).acceptBooking("b1", 50.0, null)
        coVerify { bookingRepo.acceptBooking("b1", "c1", "u1", 50.0, null, false) }
        coVerify { notifications.sendNotification("u1", "Booking Accepted", any(), "b1", "c1", "booking_accepted", any()) }
    }

    @Test
    fun `group acceptance updates every participant`() {
        vm(group, CoachTier.FREE).acceptBooking("g1", 40.0, null)
        coVerify { bookingRepo.acceptGroupBookingAsCoach("g1", "c1", 40.0, null, listOf("c1", "c2"), listOf("u1", "u2")) }
        coVerify(exactly = 2) { notifications.sendNotification(any(), any(), any(), "g1", "c1", "booking_accepted", any()) }
    }

    @Test
    fun `a Pro coach accepting a group booking doesn't ask clients for a payment the group flow never collects`() {
        // acceptGroupBookingAsCoach ignores requiresPaymentUpfront, so there is no pending_payment step for groups.
        vm(group, CoachTier.PRO).acceptBooking("g1", 40.0, null)
        coVerify(exactly = 0) { notifications.sendNotification(any(), "Payment Required", any(), any(), any(), any(), any()) }
    }

    @Test
    fun `rejecting tells the client why`() {
        coEvery { bookingRepo.rejectBooking(any(), any(), any(), any()) } returns Result.success(Unit)
        val vm = vm(single, CoachTier.FREE)
        vm.rejectBooking("b1", "Out of town")
        coVerify { notifications.sendNotification("u1", "Booking Rejected", match { it.contains("Out of town") }, "b1", "c1", "booking_rejected", any()) }
        assertTrue(vm.uiState.value.actionComplete)
    }
}

class ReviewBookingViewModelTest {

    @get:Rule val main = MainDispatcherRule()

    private val bookingRepo = mockk<BookingRepository>()
    private val notifications = mockk<NotificationRepository>(relaxed = true)
    private val auth = mockk<AuthRepository> { every { currentUser } returns fakeUser("u1") }
    private val booking = BookingItem(id = "b1", coachID = "c1", coachName = "Mei", clientID = "u1", clientName = "Alex", startAt = hoursFromNow(48.0), status = "Pending Acceptance")

    private fun vm(): ReviewBookingViewModel {
        mockkObject(ReminderScheduler)
        every { ReminderScheduler.scheduleReminder(any(), any(), any()) } just Runs
        coEvery { bookingRepo.fetchClientBooking("b1", "u1") } returns booking
        return ReviewBookingViewModel(bookingRepo, mockk(relaxed = true), auth, notifications, mockk<Context>(relaxed = true))
            .also { it.loadBooking("b1") }
    }

    @After
    fun tearDown() = unmockkAll()

    @Test
    fun `confirming notifies the coach and schedules reminders`() {
        coEvery { bookingRepo.confirmBooking(any(), any(), any()) } returns Result.success(Unit)
        val vm = vm()
        vm.confirmBooking("b1")
        coVerify { bookingRepo.confirmBooking("b1", "c1", "u1") }
        coVerify { notifications.sendNotification("c1", "Booking Confirmed", any(), "b1", "u1", "booking_confirmed", any()) }
        verify { ReminderScheduler.scheduleReminder(any(), booking, "Mei") }
        assertTrue(vm.uiState.value.actionComplete)
    }

    @Test
    fun `a failed confirmation shows an error and schedules nothing`() {
        coEvery { bookingRepo.confirmBooking(any(), any(), any()) } returns Result.failure(RuntimeException("offline"))
        val vm = vm()
        vm.confirmBooking("b1")
        assertEquals("offline", vm.uiState.value.error)
        verify(exactly = 0) { ReminderScheduler.scheduleReminder(any(), any(), any()) }
        coVerify(exactly = 0) { notifications.sendNotification(any(), any(), any(), any(), any(), any(), any()) }
    }
}
