package com.athletebridge.android.ui.navigation

import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.navDeepLink
import com.athletebridge.android.AppRefreshBus
import com.athletebridge.android.DeepLinkBus
import com.athletebridge.android.ui.auth.AuthScreen
import com.athletebridge.android.ui.auth.AuthViewModel
import com.athletebridge.android.ui.chat.ChatViewModel
import com.athletebridge.android.ui.bookings.AcceptBookingScreen
import com.athletebridge.android.ui.bookings.BookingEditorScreen
import com.athletebridge.android.ui.bookings.BookingsScreen
import com.athletebridge.android.ui.bookings.ClientConfirmedBookingsScreen
import com.athletebridge.android.ui.bookings.ClientRequestedBookingsScreen
import com.athletebridge.android.ui.bookings.CoachConfirmedBookingsScreen
import com.athletebridge.android.ui.bookings.BookingDetailScreen
import com.athletebridge.android.ui.bookings.ReviewBookingScreen
import com.athletebridge.android.ui.chat.ChatScreen
import com.athletebridge.android.ui.chat.MessagesScreen
import com.athletebridge.android.ui.client.ClientFormScreen
import com.athletebridge.android.ui.coach.AvailabilityTemplateScreen
import com.athletebridge.android.ui.coach.CoachDetailScreen
import com.athletebridge.android.ui.coach.CoachHomeScreen
import com.athletebridge.android.ui.coach.InputAwayTimeScreen
import com.athletebridge.android.ui.payments.PaymentsScreen
import com.athletebridge.android.ui.places.ClubAnnouncementsScreen
import com.athletebridge.android.ui.places.ClubManagementScreen
import com.athletebridge.android.ui.places.ManageEventScreen
import com.athletebridge.android.ui.places.CreateSignupEventScreen
import com.athletebridge.android.ui.places.MyClubsScreen
import com.athletebridge.android.ui.places.PlaceDetailScreen
import com.athletebridge.android.ui.places.PlacesToPlayScreen
import com.athletebridge.android.ui.places.SignupEventDetailScreen
import com.athletebridge.android.ui.places.SignupEventsScreen
import com.athletebridge.android.ui.players.PlayersToPlayWithScreen
import com.athletebridge.android.ui.profile.ProfileScreen
import com.athletebridge.android.ui.map.ExploreMapScreen
import com.athletebridge.android.ui.reviews.ReviewsScreen
import com.athletebridge.android.ui.roster.ClientCoachRosterScreen
import com.athletebridge.android.ui.roster.CoachClientRosterScreen
import com.athletebridge.android.ui.roster.RosterPersonScreen
import com.athletebridge.android.ui.stringing.EditStringerScreen
import com.athletebridge.android.ui.stringing.StringerDetailScreen
import com.athletebridge.android.ui.stringing.StringerOrderDetailScreen
import com.athletebridge.android.ui.stringing.StringingTabScreen
import com.athletebridge.android.ui.tournaments.TournamentPartnerScreen

@Composable
fun AppNavigation(
    authViewModel: AuthViewModel = hiltViewModel()
) {
    val uiState by authViewModel.uiState.collectAsState()

    when {
        uiState.user == null -> AuthScreen(viewModel = authViewModel)
        !uiState.userTypeLoaded -> {
            // User is logged in but we haven't fetched their type yet — show a
            // blank loading screen to avoid flashing the wrong role's UI.
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }
        else -> MainAppScaffold(authViewModel = authViewModel)
    }
}

/**
 * Navigates to the screen matching [uri] (athletebridge:// scheme).
 * Called from the DeepLinkBus observer in MainAppScaffold so deep links
 * always work regardless of when the NavHost was first composed.
 */
private fun navigateToDeepLink(navController: NavController, uri: Uri) {
    if (uri.scheme != "athletebridge") return
    val id = uri.pathSegments.firstOrNull() ?: ""
    when (uri.host) {
        "messages" -> navController.navigate(Screen.Messages.route) { launchSingleTop = true }
        "chat" -> navController.navigate(Screen.Chat.withId(id)) { launchSingleTop = true }
        "accept_booking" -> navController.navigate(Screen.AcceptBooking.withId(id)) { launchSingleTop = true }
        "review_booking" -> navController.navigate(Screen.ReviewBooking.withId(id)) { launchSingleTop = true }
        "bookings" -> navController.navigate(Screen.Bookings.route) { launchSingleTop = true }
        "payments" -> navController.navigate(Screen.Payments.route) { launchSingleTop = true }
        "stringer_order_detail" -> navController.navigate(Screen.StringerOrderDetail.withId(id)) { launchSingleTop = true }
        "place_detail" -> navController.navigate(Screen.PlaceDetail.withId(id)) { launchSingleTop = true }
        "my_clubs" -> navController.navigate(Screen.MyClubs.route) { launchSingleTop = true }
        "club_announcements" -> navController.navigate(Screen.ClubAnnouncements.withId(id)) { launchSingleTop = true }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppScaffold(
    authViewModel: AuthViewModel,
    chatViewModel: ChatViewModel = hiltViewModel()
) {
    val uiState by authViewModel.uiState.collectAsState()
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val isCoach = uiState.userType?.uppercase() == "COACH"

    // Consume any pending deep link from DeepLinkBus (posted by MainActivity or
    // NotificationService). Using StateFlow means the URI is preserved even if
    // auth loading delays NavHost composition past the first onResume.
    val pendingDeepLink by DeepLinkBus.pending.collectAsState()
    LaunchedEffect(pendingDeepLink) {
        val uri = pendingDeepLink ?: return@LaunchedEffect
        navigateToDeepLink(navController, uri)
        DeepLinkBus.consume()
    }

    // Observe AppRefreshBus — re-fetch profile/subscription tier on ALL events so
    // data stays fresh after returning from Stripe checkout or receiving FCM messages.
    LaunchedEffect(Unit) {
        AppRefreshBus.events.collect { event ->
            if (event == AppRefreshBus.Event.ALL) {
                authViewModel.refreshProfile()
            }
        }
    }

    // Start observing chats for the badge count as soon as the scaffold is created.
    LaunchedEffect(Unit) {
        chatViewModel.observeChats()
    }
    val unreadCount by chatViewModel.unreadCount.collectAsState()

    val tabs = if (isCoach) getCoachTabs(unreadCount) else getClientTabs(unreadCount)

    val topLevelRoutes = tabs.map { it.screen.route }.toSet()
    val showBottomBar = currentRoute in topLevelRoutes

    // User display info
    val userName = if (isCoach) uiState.currentCoach?.name ?: "" else uiState.currentClient?.name ?: ""
    val photoURL: String? = if (isCoach) uiState.currentCoach?.photoURLString else null

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
            startDestination = if (isCoach) Screen.CoachHome.route else Screen.Home.route,
            modifier = Modifier.padding(paddingValues)
        ) {
            // Messages tab
            composable(Screen.Messages.route) {
                MessagesScreen(
                    onChatSelected = { chatId ->
                        navController.navigate(Screen.Chat.withId(chatId))
                    },
                    onNewMessage = {
                        // Navigate to coach/client search to find someone to message
                        navController.navigate(if (isCoach) Screen.Bookings.route else Screen.Home.route) {
                            popUpTo(navController.graph.startDestinationId) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }

            // Coach Home tab
            composable(Screen.CoachHome.route) {
                CoachHomeScreen(
                    onTournaments = { navController.navigate(Screen.TournamentPartner.route) },
                    onPlacesToPlay = { navController.navigate(Screen.PlacesToPlay.route) },
                    onMyClubs = { navController.navigate(Screen.MyClubs.route) },
                    onStringing = { navController.navigate(Screen.Stringing.route) },
                    onPlayersToPlayWith = { navController.navigate(Screen.PlayersToPlayWith.route) },
                    onAvailabilityTemplate = { navController.navigate(Screen.AvailabilityTemplate.route) },
                    onMyClients = { navController.navigate(Screen.MyClients.route) },
                    onClubManagement = { navController.navigate(Screen.ClubManagement.route) },
                    onReviews = { navController.navigate(Screen.Reviews.route) },
                    onExploreMap = { navController.navigate(Screen.ExploreMap.route) }
                )
            }

            // Availability Template (Coach only)
            composable(Screen.AvailabilityTemplate.route) {
                AvailabilityTemplateScreen(onBack = { navController.popBackStack() })
            }

            // Home tab (Client only - search coaches)
            composable(Screen.Home.route) {
                ClientFormScreen(
                    onCoachSelected = { coachId ->
                        navController.navigate(Screen.CoachDetail.withId(coachId))
                    },
                    onPlacesToPlay = { navController.navigate(Screen.PlacesToPlay.route) },
                    onPlayersToPlayWith = { navController.navigate(Screen.PlayersToPlayWith.route) },
                    onStringing = { navController.navigate(Screen.Stringing.route) },
                    onTournamentPartner = { navController.navigate(Screen.TournamentPartner.route) },
                    onMyCoaches = { navController.navigate(Screen.MyCoaches.route) },
                    onMyClubs = { navController.navigate(Screen.MyClubs.route) },
                    onClubManagement = { navController.navigate(Screen.ClubManagement.route) },
                    onExploreMap = { navController.navigate(Screen.ExploreMap.route) }
                )
            }

            // Bookings tab
            composable(
                route = Screen.Bookings.route,
                deepLinks = listOf(navDeepLink { uriPattern = "athletebridge://bookings" })
            ) {
                BookingsScreen(
                    isCoach = isCoach,
                    onCreateBooking = {
                        navController.navigate(Screen.SelectCoach.route)
                    },
                    onInputAwayTime = {
                        navController.navigate(Screen.InputAwayTime.route)
                    },
                    onAcceptBooking = { bookingId ->
                        navController.navigate(Screen.AcceptBooking.withId(bookingId))
                    },
                    onReviewBooking = { bookingId ->
                        navController.navigate(Screen.ReviewBooking.withId(bookingId))
                    },
                    onConfirmedBookings = {
                        navController.navigate(Screen.CoachConfirmedBookings.route)
                    },
                    onClientConfirmedBookings = {
                        navController.navigate(Screen.ClientConfirmedBookings.route)
                    },
                    onClientRequestedBookings = {
                        navController.navigate(Screen.ClientRequestedBookings.route)
                    },
                    onBookingDetail = { bookingId ->
                        navController.navigate(Screen.BookingDetail.withId(bookingId))
                    }
                )
            }

            // Input Away Time (Coach only)
            composable(Screen.InputAwayTime.route) {
                InputAwayTimeScreen(onBack = { navController.popBackStack() })
            }

            // Profile tab
            composable(Screen.Profile.route) {
                ProfileScreen(
                    onLogout = { authViewModel.logout() }
                )
            }

            // Payments tab
            composable(
                route = Screen.Payments.route,
                deepLinks = listOf(navDeepLink { uriPattern = "athletebridge://payments" })
            ) {
                PaymentsScreen(isCoach = isCoach)
            }

            // Reviews tab
            composable(Screen.Reviews.route) {
                ReviewsScreen(isCoach = isCoach)
            }

            // Detail screens
            composable(
                route = Screen.Chat.route,
                arguments = listOf(navArgument("chatId") { type = NavType.StringType }),
                deepLinks = listOf(navDeepLink { uriPattern = "athletebridge://chat/{chatId}" })
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
                        navController.navigate(Screen.Bookings.route) {
                            launchSingleTop = true
                        }
                    }
                )
            }

            // Accept Booking (Coach)
            composable(
                route = Screen.AcceptBooking.route,
                arguments = listOf(navArgument("bookingId") { type = NavType.StringType }),
                deepLinks = listOf(navDeepLink { uriPattern = "athletebridge://accept_booking/{bookingId}" })
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
                arguments = listOf(navArgument("bookingId") { type = NavType.StringType }),
                deepLinks = listOf(navDeepLink { uriPattern = "athletebridge://review_booking/{bookingId}" })
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

            // Client Confirmed Bookings
            composable(Screen.ClientConfirmedBookings.route) {
                ClientConfirmedBookingsScreen(
                    onBack = { navController.popBackStack() },
                    onReviewBooking = { bookingId ->
                        navController.navigate(Screen.ReviewBooking.withId(bookingId))
                    },
                    onBookingDetail = { bookingId ->
                        navController.navigate(Screen.BookingDetail.withId(bookingId))
                    }
                )
            }

            // Booking Detail (both roles)
            composable(
                route = Screen.BookingDetail.route,
                arguments = listOf(navArgument("bookingId") { type = NavType.StringType }),
                deepLinks = listOf(navDeepLink { uriPattern = "athletebridge://booking_detail/{bookingId}" })
            ) { backStackEntry ->
                val bookingId = backStackEntry.arguments?.getString("bookingId") ?: ""
                BookingDetailScreen(
                    bookingId = bookingId,
                    isCoach = isCoach,
                    onBack = { navController.popBackStack() }
                )
            }

            // ── Rosters ────────────────────────────────────────────────────────────

            composable(Screen.MyCoaches.route) {
                ClientCoachRosterScreen(
                    onBack = { navController.popBackStack() },
                    onOpenChat = { chatId -> navController.navigate(Screen.Chat.withId(chatId)) },
                    onPersonDetail = { id, name ->
                        navController.navigate(Screen.RosterPerson.withArgs(id, name))
                    }
                )
            }

            composable(Screen.MyClients.route) {
                CoachClientRosterScreen(
                    onBack = { navController.popBackStack() },
                    onOpenChat = { chatId -> navController.navigate(Screen.Chat.withId(chatId)) },
                    onPersonDetail = { id, name ->
                        navController.navigate(Screen.RosterPerson.withArgs(id, name))
                    }
                )
            }

            composable(
                route = Screen.RosterPerson.route,
                arguments = listOf(
                    navArgument("personId") { type = NavType.StringType },
                    navArgument("personName") { type = NavType.StringType }
                )
            ) { backStackEntry ->
                val personId = backStackEntry.arguments?.getString("personId") ?: ""
                val personName = backStackEntry.arguments?.getString("personName")?.trim() ?: ""
                RosterPersonScreen(
                    personId = personId,
                    personName = personName,
                    isCoach = isCoach,
                    onBack = { navController.popBackStack() },
                    onBookingDetail = { bookingId ->
                        navController.navigate(Screen.BookingDetail.withId(bookingId))
                    }
                )
            }

            // Discovery map (coaches + venues)
            composable(Screen.ExploreMap.route) {
                ExploreMapScreen(
                    onBack = { navController.popBackStack() },
                    onPlaceDetail = { id -> navController.navigate(Screen.PlaceDetail.withId(id)) },
                    onCoachDetail = { id -> navController.navigate(Screen.CoachDetail.withId(id)) }
                )
            }

            // ── Club Management ────────────────────────────────────────────────────

            composable(Screen.ClubManagement.route) {
                ClubManagementScreen(
                    onBack = { navController.popBackStack() },
                    onManageEvent = { eventId ->
                        navController.navigate(Screen.ManageEvent.withId(eventId))
                    }
                )
            }

            composable(
                route = Screen.ManageEvent.route,
                arguments = listOf(navArgument("eventId") { type = NavType.StringType })
            ) { backStackEntry ->
                val eventId = backStackEntry.arguments?.getString("eventId") ?: ""
                ManageEventScreen(
                    eventId = eventId,
                    onBack = { navController.popBackStack() }
                )
            }

            // Client Requested Bookings
            composable(Screen.ClientRequestedBookings.route) {
                ClientRequestedBookingsScreen(
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

            // ── Stringing ──────────────────────────────────────────────────────────

            composable(Screen.Stringing.route) {
                StringingTabScreen(
                    onBack = { navController.popBackStack() },
                    onStringerDetail = { id -> navController.navigate(Screen.StringerDetail.withId(id)) },
                    onOrderDetail = { id -> navController.navigate(Screen.StringerOrderDetail.withId(id)) },
                    onEditStringer = { navController.navigate(Screen.EditStringer.route) }
                )
            }

            composable(
                route = Screen.StringerDetail.route,
                arguments = listOf(navArgument("stringerId") { type = NavType.StringType })
            ) { backStackEntry ->
                val stringerId = backStackEntry.arguments?.getString("stringerId") ?: ""
                StringerDetailScreen(
                    stringerId = stringerId,
                    onBack = { navController.popBackStack() },
                    buyerName = userName
                )
            }

            composable(
                route = Screen.StringerOrderDetail.route,
                arguments = listOf(navArgument("orderId") { type = NavType.StringType }),
                deepLinks = listOf(navDeepLink { uriPattern = "athletebridge://stringer_order_detail/{orderId}" })
            ) { backStackEntry ->
                val orderId = backStackEntry.arguments?.getString("orderId") ?: ""
                StringerOrderDetailScreen(
                    orderId = orderId,
                    onBack = { navController.popBackStack() },
                    onMessage = { otherUserId ->
                        val chatId = listOf(uiState.user?.uid ?: "", otherUserId).sorted().joinToString("_")
                        navController.navigate(Screen.Chat.withId(chatId))
                    }
                )
            }

            composable(Screen.EditStringer.route) {
                EditStringerScreen(
                    onBack = { navController.popBackStack() },
                    userName = userName,
                    photoURL = photoURL
                )
            }

            // ── Places to Play ─────────────────────────────────────────────────────

            composable(Screen.PlacesToPlay.route) {
                PlacesToPlayScreen(
                    onBack = { navController.popBackStack() },
                    onPlaceDetail = { id -> navController.navigate(Screen.PlaceDetail.withId(id)) }
                )
            }

            composable(
                route = Screen.PlaceDetail.route,
                arguments = listOf(navArgument("placeId") { type = NavType.StringType })
            ) { backStackEntry ->
                val placeId = backStackEntry.arguments?.getString("placeId") ?: ""
                PlaceDetailScreen(
                    placeId = placeId,
                    onBack = { navController.popBackStack() },
                    onAnnouncements = { id -> navController.navigate(Screen.ClubAnnouncements.withId(id)) },
                    onEvents = { id -> navController.navigate(Screen.SignupEvents.withId(id)) },
                    onCreateEvent = { id -> navController.navigate(Screen.CreateSignupEvent.withId(id)) }
                )
            }

            composable(Screen.MyClubs.route) {
                MyClubsScreen(
                    onBack = { navController.popBackStack() },
                    onPlaceDetail = { id -> navController.navigate(Screen.PlaceDetail.withId(id)) }
                )
            }

            composable(
                route = Screen.ClubAnnouncements.route,
                arguments = listOf(navArgument("placeId") { type = NavType.StringType })
            ) { backStackEntry ->
                val placeId = backStackEntry.arguments?.getString("placeId") ?: ""
                ClubAnnouncementsScreen(
                    placeId = placeId,
                    onBack = { navController.popBackStack() }
                )
            }

            composable(
                route = Screen.SignupEvents.route,
                arguments = listOf(navArgument("placeId") { type = NavType.StringType })
            ) { backStackEntry ->
                val placeId = backStackEntry.arguments?.getString("placeId") ?: ""
                SignupEventsScreen(
                    placeId = placeId,
                    onBack = { navController.popBackStack() },
                    onEventDetail = { eventId ->
                        navController.navigate(Screen.SignupEventDetail.withIds(placeId, eventId))
                    }
                )
            }

            composable(
                route = Screen.SignupEventDetail.route,
                arguments = listOf(
                    navArgument("placeId") { type = NavType.StringType },
                    navArgument("eventId") { type = NavType.StringType }
                )
            ) { backStackEntry ->
                val placeId = backStackEntry.arguments?.getString("placeId") ?: ""
                val eventId = backStackEntry.arguments?.getString("eventId") ?: ""
                SignupEventDetailScreen(
                    placeId = placeId,
                    eventId = eventId,
                    onBack = { navController.popBackStack() }
                )
            }

            composable(
                route = Screen.CreateSignupEvent.route,
                arguments = listOf(navArgument("placeId") { type = NavType.StringType })
            ) { backStackEntry ->
                val placeId = backStackEntry.arguments?.getString("placeId") ?: ""
                CreateSignupEventScreen(
                    placeId = placeId,
                    onBack = { navController.popBackStack() }
                )
            }

            // Select Coach (pushed from Bookings + button — not a tab)
            composable(Screen.SelectCoach.route) {
                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = { Text("Find a Coach") },
                            navigationIcon = {
                                IconButton(onClick = { navController.popBackStack() }) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                                }
                            }
                        )
                    }
                ) { padding ->
                    ClientFormScreen(
                        modifier = Modifier.padding(padding),
                        showTitle = false,
                        onCoachSelected = { coachId ->
                            navController.navigate(Screen.CreateBooking.withId(coachId))
                        },
                        onPlacesToPlay = { navController.navigate(Screen.PlacesToPlay.route) },
                        onPlayersToPlayWith = { navController.navigate(Screen.PlayersToPlayWith.route) },
                        onStringing = { navController.navigate(Screen.Stringing.route) },
                        onTournamentPartner = { navController.navigate(Screen.TournamentPartner.route) }
                    )
                }
            }

            // ── Players to Play With ───────────────────────────────────────────────

            composable(Screen.PlayersToPlayWith.route) {
                PlayersToPlayWithScreen(
                    onBack = { navController.popBackStack() },
                    onMessagePlayer = { playerId ->
                        val chatId = listOf(uiState.user?.uid ?: "", playerId).sorted().joinToString("_")
                        navController.navigate(Screen.Chat.withId(chatId))
                    },
                    userName = userName,
                    photoURL = photoURL
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
