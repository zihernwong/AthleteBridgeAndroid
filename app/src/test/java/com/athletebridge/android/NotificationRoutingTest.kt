package com.athletebridge.android

import com.athletebridge.android.service.NotificationService
import com.athletebridge.android.ui.navigation.deepLinkRoute
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A push tap goes: data payload → deep link (NotificationService.deepLinkFor, also used by
 * MainActivity for background taps) → nav route (deepLinkRoute, which knows the viewer's role).
 * Booking screens are role-specific:
 *  - accept_booking  → AcceptBookingViewModel reads coaches/{uid}/bookings  (coach only)
 *  - review_booking  → ReviewBookingViewModel reads clients/{uid}/bookings (client only)
 * so every push must land each recipient on a screen that works for their role.
 */
class NotificationRoutingTest {

    private enum class Role { COACH, CLIENT }

    private val shared = setOf(
        "bookings", "payments", "chat", "stringer_order_detail", "place_detail", "my_clubs",
        "club_announcements", "signup_events", "signup_event_detail", "tournament_partner"
    )
    private val coachScreens = shared + "accept_booking"
    private val clientScreens = shared + "review_booking"

    private fun push(type: String) = mapOf(
        "type" to type, "bookingId" to "b1", "placeId" to "p1", "stringerOrderId" to "o1", "eventId" to "e1", "tournamentId" to "t1"
    )

    private fun foreground(data: Map<String, String>) = NotificationService.deepLinkFor(data)

    private fun background(data: Map<String, String>) = fcmExtrasDeepLink(
        type = data["type"], chatId = data["chatId"], bookingId = data["bookingId"],
        orderId = data["stringerOrderId"] ?: data["orderId"], placeId = data["placeId"]
    )

    /** Where a tap actually lands: the push's deep link, resolved by navigation for the viewer's role. */
    private fun screenFor(type: String, role: Role): String? {
        val link = foreground(push(type))?.removePrefix("athletebridge://") ?: return null
        val parts = link.split('/')
        return deepLinkRoute(
            host = parts[0],
            id = parts.getOrElse(1) { "" },
            isCoach = role == Role.COACH,
            secondId = parts.getOrElse(2) { "" }
        )?.substringBefore('/')
    }

    private fun assertLandsOnScreenFor(role: Role, type: String, sentBy: String) {
        val screen = screenFor(type, role)
        val allowed = if (role == Role.COACH) coachScreens else clientScreens
        assertTrue("$type sent to a $role by $sentBy opens '$screen'", screen in allowed)
    }

    @Test
    fun `foreground and background taps open the same screen`() {
        val types = listOf(
            "booking_request", "booking_confirmed", "payment_submitted", "booking_accepted", "booking_rejected",
            "booking_rescheduled", "booking_declined", "payment_acknowledged", "payment_confirmed", "payment_reminder",
            "booking_cancelled", "stringer_order", "club_join_request", "club_approved", "club_announcement", "unknown"
        )
        for (type in types) assertEquals(type, foreground(push(type)), background(push(type)))
        val chat = mapOf("type" to "chat", "chatId" to "a_b")
        assertEquals("athletebridge://chat/a_b", foreground(chat))
        assertEquals(foreground(chat), background(chat))
    }

    @Test
    fun `booking pushes sent to the right role open the right screen`() {
        assertLandsOnScreenFor(Role.COACH, "booking_request", "BookingEditorViewModel.createBooking")
        assertLandsOnScreenFor(Role.CLIENT, "booking_accepted", "AcceptBookingViewModel.acceptBooking")
        assertLandsOnScreenFor(Role.CLIENT, "booking_rejected", "AcceptBookingViewModel.rejectBooking")
        assertLandsOnScreenFor(Role.COACH, "booking_confirmed", "ReviewBookingViewModel.confirmBooking")
        assertLandsOnScreenFor(Role.COACH, "payment_submitted", "ReviewBookingViewModel.submitPayment")
        assertLandsOnScreenFor(Role.CLIENT, "payment_acknowledged", "CoachConfirmedBookingsViewModel.acknowledgePayment")
        assertLandsOnScreenFor(Role.CLIENT, "booking_rescheduled", "CoachConfirmedBookingsViewModel.rescheduleBooking")
        assertLandsOnScreenFor(Role.CLIENT, "booking_cancelled", "CoachConfirmedBookingsViewModel.cancelBooking")
        assertLandsOnScreenFor(Role.COACH, "booking_cancelled", "ClientConfirmedBookingsViewModel.cancelBooking")
    }

    @Test
    fun `client tapping Booking Confirmed after the coach confirms payment opens a client screen`() {
        // BookingsViewModel / BookingDetailViewModel.confirmPaymentReceived send type "booking_confirmed" to the client.
        assertLandsOnScreenFor(Role.CLIENT, "booking_confirmed", "BookingDetailViewModel.confirmPaymentReceived")
    }

    @Test
    fun `coach tapping Booking Rescheduled from a client opens a coach screen`() {
        // ClientConfirmedBookingsViewModel.rescheduleBooking sends type "booking_rescheduled" to the coach.
        assertLandsOnScreenFor(Role.COACH, "booking_rescheduled", "ClientConfirmedBookingsViewModel.rescheduleBooking")
    }

    @Test
    fun `waitlist promotion opens the club's events when the push has no event id`() {
        // The pendingNotifications Cloud Function forwards placeId but not eventId.
        val link = foreground(mapOf("type" to "event_waitlist_promoted", "placeId" to "p1"))
        assertEquals("athletebridge://signup_events/p1", link)
        assertEquals("signup_events/p1", deepLinkRoute("signup_events", "p1", isCoach = false))
    }

    @Test
    fun `every notification type the Android app sends opens a screen`() {
        val sent = mapOf(
            "payment" to "PaymentsViewModel.notifyCoachOfPayment",
            "event_waitlist_promoted" to "PlaceRepository.promoteFromWaitlist",
            "tournament_partner" to "TournamentRepository.notifyCompatiblePartnerSeekers",
            "tournament_recommendation" to "TournamentRepository.recommendToClients",
            "stringer_order" to "StringerOrderDetailViewModel",
            "club_approved" to "ClubManagementViewModel.approveMember",
            "club_announcement" to "PlaceRepository.createAnnouncement"
        )
        val unrouted = sent.filterKeys { screenFor(it, Role.COACH) == null && screenFor(it, Role.CLIENT) == null }
        assertTrue("tapping these just opens the app: $unrouted", unrouted.isEmpty())
    }

    @Test
    fun `pushes from older iOS builds still open their screens`() {
        // iOS used to send booking pushes without a type, and its own stringing type names
        val untypedBooking = foreground(mapOf("bookingId" to "b1", "title" to "Booking Requested"))
        assertEquals("athletebridge://accept_booking/b1", untypedBooking)
        assertEquals("accept_booking", deepLinkRoute("accept_booking", "b1", isCoach = true)?.substringBefore('/'))
        assertEquals("review_booking", deepLinkRoute("accept_booking", "b1", isCoach = false)?.substringBefore('/'))
        for (type in listOf("stringer_order_placed", "stringer_order_update")) {
            assertEquals(type, "athletebridge://stringer_order_detail/o1", foreground(mapOf("type" to type, "stringerOrderId" to "o1")))
        }
        assertNull(foreground(mapOf("title" to "Hello")))
    }
}
