package com.athletebridge.android.ui.navigation

/**
 * Nav route for an `athletebridge://host/id[/secondId]` deep link.
 *
 * Push types describe the event, not the recipient (e.g. "booking_confirmed" is sent to coaches when a
 * client confirms and to clients when a coach confirms payment), so booking links open the booking screen
 * for the viewer's role: coaches act in AcceptBooking (reads coaches/{uid}/bookings), clients in
 * ReviewBooking (reads clients/{uid}/bookings). Opening the other role's screen showed an empty booking.
 */
internal fun deepLinkRoute(host: String?, id: String, isCoach: Boolean, secondId: String = ""): String? =
    when (host) {
        "messages" -> Screen.Messages.route
        "chat" -> Screen.Chat.withId(id)
        "accept_booking", "review_booking" ->
            if (isCoach) Screen.AcceptBooking.withId(id) else Screen.ReviewBooking.withId(id)
        "bookings" -> Screen.Bookings.route
        "payments" -> Screen.Payments.route
        "stringer_order_detail" -> Screen.StringerOrderDetail.withId(id)
        "place_detail" -> Screen.PlaceDetail.withId(id)
        "my_clubs" -> Screen.MyClubs.route
        "club_announcements" -> Screen.ClubAnnouncements.withId(id)
        "signup_events" -> Screen.SignupEvents.withId(id)
        "signup_event_detail" ->
            if (secondId.isNotEmpty()) Screen.SignupEventDetail.withIds(id, secondId) else Screen.SignupEvents.withId(id)
        "tournament_partner" -> Screen.TournamentPartner.route
        else -> null
    }
