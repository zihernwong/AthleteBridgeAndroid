package com.athletebridge.android.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(val route: String) {
    data object Auth : Screen("auth")

    // Bottom nav tabs
    data object Messages : Screen("messages")
    data object Home : Screen("home")
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
}

data class BottomNavItem(
    val screen: Screen,
    val label: String,
    val icon: ImageVector,
    val badgeCount: Int = 0
)

fun getCoachTabs(unreadCount: Int = 0): List<BottomNavItem> = listOf(
    BottomNavItem(Screen.Messages, "Messages", Icons.Filled.Email, unreadCount),
    BottomNavItem(Screen.Payments, "Payments", Icons.Filled.CreditCard),
    BottomNavItem(Screen.Reviews, "Reviews", Icons.Filled.Star),
    BottomNavItem(Screen.Bookings, "Bookings", Icons.Filled.DateRange),
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
