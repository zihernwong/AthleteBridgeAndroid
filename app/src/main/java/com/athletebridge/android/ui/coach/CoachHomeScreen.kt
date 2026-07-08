package com.athletebridge.android.ui.coach

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.athletebridge.android.ui.theme.LogoBlue
import com.athletebridge.android.ui.theme.LogoGreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CoachHomeScreen(
    onTournaments: () -> Unit,
    onPlacesToPlay: () -> Unit,
    onMyClubs: () -> Unit,
    onStringing: () -> Unit,
    onPlayersToPlayWith: () -> Unit,
    onAvailabilityTemplate: () -> Unit = {},
    onMyClients: () -> Unit = {},
    onClubManagement: () -> Unit = {},
    onReviews: () -> Unit = {},
    onExploreMap: () -> Unit = {},
    viewModel: CoachHomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) { viewModel.loadData() }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Home") }) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "Quick Access",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            HomeShortcutCard(
                icon = Icons.Default.People,
                title = "My Clients",
                subtitle = "See every client you've coached, message them, and view history",
                iconTint = LogoGreen,
                onClick = onMyClients
            )

            HomeShortcutCard(
                icon = Icons.Default.EmojiEvents,
                title = "Upcoming Tournaments",
                subtitle = "Find and suggest badminton tournaments",
                iconTint = LogoGreen,
                onClick = onTournaments
            )

            HomeShortcutCard(
                icon = Icons.Default.LocationOn,
                title = "Places to Play",
                subtitle = "Browse courts and badminton clubs near you",
                iconTint = LogoBlue,
                onClick = onPlacesToPlay
            )

            HomeShortcutCard(
                icon = Icons.Default.Map,
                title = "Explore Map",
                subtitle = "See coaches and venues near you on a map",
                iconTint = LogoGreen,
                onClick = onExploreMap
            )

            HomeShortcutCard(
                icon = Icons.Default.Groups,
                title = "My Clubs",
                subtitle = "View your club memberships",
                iconTint = LogoBlue,
                onClick = onMyClubs
            )

            HomeShortcutCard(
                icon = Icons.Default.Build,
                title = "Badminton Stringers",
                subtitle = "Find stringers or manage your orders",
                iconTint = LogoGreen,
                badge = if (uiState.pendingStringerOrders > 0) "${uiState.pendingStringerOrders} pending" else null,
                onClick = onStringing
            )

            HomeShortcutCard(
                icon = Icons.Default.People,
                title = "Players to Play With",
                subtitle = "Find hitting partners in your area",
                iconTint = LogoBlue,
                onClick = onPlayersToPlayWith
            )

            HomeShortcutCard(
                icon = Icons.Default.CalendarMonth,
                title = "Weekly Availability",
                subtitle = "Set your recurring weekly availability template",
                iconTint = LogoGreen,
                onClick = onAvailabilityTemplate
            )

            HomeShortcutCard(
                icon = Icons.Default.AdminPanelSettings,
                title = "Club Management",
                subtitle = "Manage clubs you're the contact for — members, requests, events",
                iconTint = LogoBlue,
                onClick = onClubManagement
            )

            HomeShortcutCard(
                icon = Icons.Default.Star,
                title = "Reviews",
                subtitle = "See what clients are saying about you",
                iconTint = LogoGreen,
                onClick = onReviews
            )
        }
    }
}

@Composable
private fun HomeShortcutCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    iconTint: androidx.compose.ui.graphics.Color,
    badge: String? = null,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = iconTint.copy(alpha = 0.12f),
                modifier = Modifier.size(48.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(28.dp))
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(title, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleSmall)
                    badge?.let { badgeText ->
                        Spacer(Modifier.width(8.dp))
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = LogoGreen.copy(alpha = 0.15f)
                        ) {
                            Text(
                                badgeText,
                                style = MaterialTheme.typography.labelSmall,
                                color = LogoGreen,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
