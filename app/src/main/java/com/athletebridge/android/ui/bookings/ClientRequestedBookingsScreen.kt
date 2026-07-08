package com.athletebridge.android.ui.bookings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClientRequestedBookingsScreen(
    onBack: () -> Unit,
    onReviewBooking: (String) -> Unit = {},
    viewModel: BookingsViewModel = hiltViewModel()
) {
    LaunchedEffect(Unit) { viewModel.loadBookings(isCoach = false) }
    val uiState by viewModel.uiState.collectAsState()

    // Show "requested" bookings (awaiting coach acceptance) — future only
    val requested = remember(uiState.bookings) {
        uiState.bookings.filter {
            (it.status ?: "").lowercase() == "requested"
        }.sortedBy { it.startAt }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Requested Bookings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        if (uiState.isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (requested.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(
                    "No requested bookings",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(requested, key = { it.id }) { booking ->
                    BookingRowView(booking = booking, isCoach = false)
                }
            }
        }
    }
}
