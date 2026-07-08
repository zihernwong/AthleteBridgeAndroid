package com.athletebridge.android.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(val route: String) {
    data object Auth : Screen("auth")

    // Bottom nav tabs
    data object Messages : Screen("messages")
    data object Home : Screen("home")
    data object CoachHome : Screen("coach_home")
    data object Bookings : Screen("bookings")
    data object Profile : Screen("profile")
    data object Reviews : Screen("reviews")
    data object Payments : Screen("payments")

    // Detail screens (navigated to from tabs)
    data object CoachDetail : Screen("coach_detail/{coachId}") {
        fun withId(coachId: String) = "coach_detail/$coachId"
    }
    data object ClientDetail : Screen("client_detail/{clientId}") {
        fun withId(clientId: String) = "client_detail/$clientId"
    }
    data object Chat : Screen("chat/{chatId}") {
        fun withId(chatId: String) = "chat/$chatId"
    }
    data object BookingDetail : Screen("booking_detail/{bookingId}") {
        fun withId(bookingId: String) = "booking_detail/$bookingId"
    }
    data object AcceptBooking : Screen("accept_booking/{bookingId}") {
        fun withId(bookingId: String) = "accept_booking/$bookingId"
    }
    data object ReviewBooking : Screen("review_booking/{bookingId}") {
        fun withId(bookingId: String) = "review_booking/$bookingId"
    }
    data object CoachConfirmedBookings : Screen("coach_confirmed_bookings")
    data object ClientConfirmedBookings : Screen("client_confirmed_bookings")
    data object ClientRequestedBookings : Screen("client_requested_bookings")
    data object CreateBooking : Screen("create_booking/{coachId}") {
        fun withId(coachId: String) = "create_booking/$coachId"
    }
    data object SubmitReview : Screen("submit_review")
    data object TournamentPartner : Screen("tournament_partner")
    data object Settings : Screen("settings")

    // Stringing
    data object Stringing : Screen("stringing")
    data object StringerDetail : Screen("stringer_detail/{stringerId}") {
        fun withId(id: String) = "stringer_detail/$id"
    }
    data object StringerOrderDetail : Screen("stringer_order_detail/{orderId}") {
        fun withId(id: String) = "stringer_order_detail/$id"
    }
    data object EditStringer : Screen("edit_stringer")

    // Places to Play
    data object PlacesToPlay : Screen("places_to_play")
    data object PlaceDetail : Screen("place_detail/{placeId}") {
        fun withId(id: String) = "place_detail/$id"
    }
    data object MyClubs : Screen("my_clubs")
    data object ClubAnnouncements : Screen("club_announcements/{placeId}") {
        fun withId(id: String) = "club_announcements/$id"
    }
    data object SignupEvents : Screen("signup_events/{placeId}") {
        fun withId(id: String) = "signup_events/$id"
    }
    data object SignupEventDetail : Screen("signup_event_detail/{placeId}/{eventId}") {
        fun withIds(placeId: String, eventId: String) = "signup_event_detail/$placeId/$eventId"
    }
    data object CreateSignupEvent : Screen("create_signup_event/{placeId}") {
        fun withId(id: String) = "create_signup_event/$id"
    }

    // Players to Play With
    data object PlayersToPlayWith : Screen("players_to_play_with")

    // Coach selection flow (triggered from Bookings + button)
    data object SelectCoach : Screen("select_coach")

    // Coach away time management
    data object InputAwayTime : Screen("input_away_time")

    // Coach availability template
    data object AvailabilityTemplate : Screen("availability_template")

    // Rosters ("My Coaches" for clients, "My Clients" for coaches)
    data object MyCoaches : Screen("my_coaches")
    data object MyClients : Screen("my_clients")
    data object RosterPerson : Screen("roster_person/{personId}/{personName}") {
        fun withArgs(personId: String, personName: String) =
            "roster_person/$personId/${android.net.Uri.encode(personName.ifEmpty { " " })}"
    }

    // Discovery map of coaches and venues
    data object ExploreMap : Screen("explore_map")

    // Club management (Places to Play contact)
    data object ClubManagement : Screen("club_management")
    data object ManageEvent : Screen("manage_event/{eventId}") {
        fun withId(eventId: String) = "manage_event/$eventId"
    }
}

data class BottomNavItem(
    val screen: Screen,
    val label: String,
    val icon: ImageVector,
    val badgeCount: Int = 0
)

fun getCoachTabs(unreadCount: Int = 0): List<BottomNavItem> = listOf(
    BottomNavItem(Screen.Messages, "Messages", Icons.Filled.Email, unreadCount),
    BottomNavItem(Screen.CoachHome, "Home", Icons.Filled.Home),
    BottomNavItem(Screen.Bookings, "Bookings", Icons.Filled.DateRange),
    BottomNavItem(Screen.Payments, "Payments", Icons.Filled.CreditCard),
    BottomNavItem(Screen.Profile, "Profile", Icons.Filled.Person)
)

fun getClientTabs(unreadCount: Int = 0): List<BottomNavItem> = listOf(
    BottomNavItem(Screen.Messages, "Messages", Icons.Filled.Email, unreadCount),
    BottomNavItem(Screen.Home, "Home", Icons.Filled.Home),
    BottomNavItem(Screen.Bookings, "Bookings", Icons.Filled.DateRange),
    BottomNavItem(Screen.Profile, "Profile", Icons.Filled.Person),
    BottomNavItem(Screen.Payments, "Payments", Icons.Filled.CreditCard),
    BottomNavItem(Screen.Reviews, "Reviews", Icons.Filled.Star)
)
