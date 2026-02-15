package com.athletebridge.android.ui.navigation

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.athletebridge.android.ui.auth.AuthScreen
import com.athletebridge.android.ui.auth.AuthViewModel
import com.athletebridge.android.ui.bookings.AcceptBookingScreen
import com.athletebridge.android.ui.bookings.BookingEditorScreen
import com.athletebridge.android.ui.bookings.BookingsScreen
import com.athletebridge.android.ui.bookings.CoachConfirmedBookingsScreen
import com.athletebridge.android.ui.bookings.ReviewBookingScreen
import com.athletebridge.android.ui.chat.ChatScreen
import com.athletebridge.android.ui.chat.MessagesScreen
import com.athletebridge.android.ui.client.ClientFormScreen
import com.athletebridge.android.ui.coach.CoachDetailScreen
import com.athletebridge.android.ui.payments.PaymentsScreen
import com.athletebridge.android.ui.profile.ProfileScreen
import com.athletebridge.android.ui.reviews.ReviewsScreen
import com.athletebridge.android.ui.tournaments.TournamentPartnerScreen

@Composable
fun AppNavigation(
    authViewModel: AuthViewModel = hiltViewModel()
) {
    val uiState by authViewModel.uiState.collectAsState()

    if (uiState.user == null) {
        AuthScreen(viewModel = authViewModel)
    } else {
        MainAppScaffold(authViewModel = authViewModel)
    }
}

@Composable
fun MainAppScaffold(
    authViewModel: AuthViewModel
) {
    val uiState by authViewModel.uiState.collectAsState()
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val isCoach = uiState.userType?.uppercase() == "COACH"
    val tabs = if (isCoach) getCoachTabs() else getClientTabs()

    // Determine if the current route is a top-level tab
    val topLevelRoutes = tabs.map { it.screen.route }.toSet()
    val showBottomBar = currentRoute in topLevelRoutes

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                BottomNavBar(
                    tabs = tabs,
                    selectedRoute = currentRoute ?: tabs.first().screen.route,
                    onTabSelected = { screen ->
                        navController.navigate(screen.route) {
                            popUpTo(navController.graph.startDestinationId) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }
        }
    ) { paddingValues ->
        NavHost(
            navController = navController,
            startDestination = if (isCoach) Screen.Payments.route else Screen.Home.route,
            modifier = Modifier.padding(paddingValues)
        ) {
            // Messages tab
            composable(Screen.Messages.route) {
                MessagesScreen(
                    onChatSelected = { chatId ->
                        navController.navigate(Screen.Chat.withId(chatId))
                    }
                )
            }

            // Home tab (Client only - search coaches)
            composable(Screen.Home.route) {
                ClientFormScreen(
                    onCoachSelected = { coachId ->
                        navController.navigate(Screen.CoachDetail.withId(coachId))
                    }
                )
            }

            // Bookings tab
            composable(Screen.Bookings.route) {
                BookingsScreen(
                    isCoach = isCoach,
                    onCreateBooking = {
                        // For clients, navigate to a coach selection first, then booking
                        // For now, navigate to home to pick a coach
                        navController.navigate(Screen.Home.route)
                    },
                    onAcceptBooking = { bookingId ->
                        navController.navigate(Screen.AcceptBooking.withId(bookingId))
                    },
                    onReviewBooking = { bookingId ->
                        navController.navigate(Screen.ReviewBooking.withId(bookingId))
                    },
                    onConfirmedBookings = {
                        navController.navigate(Screen.CoachConfirmedBookings.route)
                    }
                )
            }

            // Profile tab
            composable(Screen.Profile.route) {
                ProfileScreen(
                    onLogout = { authViewModel.logout() },
                    onTournamentPartner = {
                        navController.navigate(Screen.TournamentPartner.route)
                    }
                )
            }

            // Payments tab
            composable(Screen.Payments.route) {
                PaymentsScreen(isCoach = isCoach)
            }

            // Reviews tab
            composable(Screen.Reviews.route) {
                ReviewsScreen(isCoach = isCoach)
            }

            // Detail screens
            composable(
                route = Screen.Chat.route,
                arguments = listOf(navArgument("chatId") { type = NavType.StringType })
            ) { backStackEntry ->
                val chatId = backStackEntry.arguments?.getString("chatId") ?: ""
                ChatScreen(
                    chatId = chatId,
                    onBack = { navController.popBackStack() }
                )
            }

            composable(
                route = Screen.CoachDetail.route,
                arguments = listOf(navArgument("coachId") { type = NavType.StringType })
            ) { backStackEntry ->
                val coachId = backStackEntry.arguments?.getString("coachId") ?: ""
                CoachDetailScreen(
                    coachId = coachId,
                    onBack = { navController.popBackStack() },
                    onMessageCoach = { id ->
                        val chatId = listOf(uiState.user?.uid ?: "", id).sorted().joinToString("_")
                        navController.navigate(Screen.Chat.withId(chatId))
                    },
                    onBookCoach = { id ->
                        navController.navigate(Screen.CreateBooking.withId(id))
                    }
                )
            }

            // Create Booking
            composable(
                route = Screen.CreateBooking.route,
                arguments = listOf(navArgument("coachId") { type = NavType.StringType })
            ) { backStackEntry ->
                val coachId = backStackEntry.arguments?.getString("coachId") ?: ""
                BookingEditorScreen(
                    coachId = coachId,
                    onBack = { navController.popBackStack() },
                    onBookingCreated = {
                        navController.popBackStack()
                        // Navigate to bookings tab
                        navController.navigate(Screen.Bookings.route) {
                            launchSingleTop = true
                        }
                    }
                )
            }

            // Accept Booking (Coach)
            composable(
                route = Screen.AcceptBooking.route,
                arguments = listOf(navArgument("bookingId") { type = NavType.StringType })
            ) { backStackEntry ->
                val bookingId = backStackEntry.arguments?.getString("bookingId") ?: ""
                AcceptBookingScreen(
                    bookingId = bookingId,
                    onBack = { navController.popBackStack() }
                )
            }

            // Review Booking (Client)
            composable(
                route = Screen.ReviewBooking.route,
                arguments = listOf(navArgument("bookingId") { type = NavType.StringType })
            ) { backStackEntry ->
                val bookingId = backStackEntry.arguments?.getString("bookingId") ?: ""
                ReviewBookingScreen(
                    bookingId = bookingId,
                    onBack = { navController.popBackStack() }
                )
            }

            // Coach Confirmed Bookings
            composable(Screen.CoachConfirmedBookings.route) {
                CoachConfirmedBookingsScreen(
                    onBack = { navController.popBackStack() }
                )
            }

            // Tournament Partner
            composable(Screen.TournamentPartner.route) {
                TournamentPartnerScreen(
                    onBack = { navController.popBackStack() },
                    onMessagePartner = { partnerId ->
                        val chatId = listOf(uiState.user?.uid ?: "", partnerId).sorted().joinToString("_")
                        navController.navigate(Screen.Chat.withId(chatId))
                    }
                )
            }
        }
    }
}

@Composable
fun PlaceholderScreen(title: String, subtitle: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}
