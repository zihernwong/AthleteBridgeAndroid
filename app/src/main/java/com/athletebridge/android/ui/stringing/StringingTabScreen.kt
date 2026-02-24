package com.athletebridge.android.ui.stringing

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.athletebridge.android.data.model.BadmintonStringer
import com.athletebridge.android.data.model.StringerOrder
import com.athletebridge.android.ui.shared.AvatarView
import com.athletebridge.android.ui.theme.LogoBlue
import com.athletebridge.android.ui.theme.LogoGreen
import com.athletebridge.android.ui.theme.StatusCancelled
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StringingTabScreen(
    onBack: () -> Unit,
    onStringerDetail: (String) -> Unit,
    onOrderDetail: (String) -> Unit,
    onEditStringer: () -> Unit,
    viewModel: StringingTabViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) { viewModel.loadData() }

    val isStringer = uiState.myStringerProfile != null
    val tabs = if (isStringer) listOf("Browse", "My Orders", "Incoming") else listOf("Browse", "My Orders")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Badminton Stringers") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = onEditStringer) {
                        Icon(
                            if (isStringer) Icons.Default.Edit else Icons.Default.Add,
                            contentDescription = if (isStringer) "Edit Profile" else "Become a Stringer"
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            TabRow(selectedTabIndex = uiState.selectedTabIndex) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = uiState.selectedTabIndex == index,
                        onClick = { viewModel.selectTab(index) },
                        text = { Text(title) }
                    )
                }
            }

            if (uiState.isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                when (uiState.selectedTabIndex) {
                    0 -> StringersBrowseTab(
                        stringers = uiState.stringers,
                        onStringerDetail = onStringerDetail
                    )
                    1 -> MyStringerOrdersTab(
                        orders = uiState.myOrders,
                        onOrderDetail = onOrderDetail
                    )
                    2 -> if (isStringer) IncomingOrdersTab(
                        orders = uiState.incomingOrders,
                        onOrderDetail = onOrderDetail
                    )
                }
            }
        }
    }
}

@Composable
private fun StringersBrowseTab(
    stringers: List<BadmintonStringer>,
    onStringerDetail: (String) -> Unit
) {
    if (stringers.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.SportsBaseball, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                Text("No stringers registered yet", style = MaterialTheme.typography.titleMedium)
                Text("Be the first to register as a stringer!", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    } else {
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(stringers, key = { it.id }) { stringer ->
                StringerCard(stringer = stringer, onClick = { onStringerDetail(stringer.id) })
            }
        }
    }
}

@Composable
private fun MyStringerOrdersTab(
    orders: List<StringerOrder>,
    onOrderDetail: (String) -> Unit
) {
    if (orders.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No orders placed yet", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    } else {
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(orders, key = { it.id }) { order ->
                OrderCard(order = order, showStringerName = true, onClick = { onOrderDetail(order.id) })
            }
        }
    }
}

@Composable
private fun IncomingOrdersTab(
    orders: List<StringerOrder>,
    onOrderDetail: (String) -> Unit
) {
    if (orders.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No incoming orders", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    } else {
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(orders, key = { it.id }) { order ->
                OrderCard(order = order, showStringerName = false, onClick = { onOrderDetail(order.id) })
            }
        }
    }
}

@Composable
fun StringerCard(stringer: BadmintonStringer, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AvatarView(url = stringer.photoURLString, name = stringer.name, size = 52.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(stringer.name, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium)
                Text(
                    "Labor: $${String.format("%.0f", stringer.laborCost)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (stringer.strings.isNotEmpty()) {
                    Text(
                        stringer.stringOptions.take(3).joinToString(", "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (stringer.meetupLocation.isNotBlank()) {
                    Text(
                        stringer.meetupLocation,
                        style = MaterialTheme.typography.bodySmall,
                        color = LogoBlue
                    )
                }
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun OrderCard(
    order: StringerOrder,
    showStringerName: Boolean,
    onClick: () -> Unit
) {
    val statusColor = when (order.status.lowercase()) {
        "placed" -> MaterialTheme.colorScheme.primary
        "accepted" -> LogoBlue
        "stringing" -> LogoGreen
        "completed" -> LogoGreen
        "declined" -> StatusCancelled
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        if (showStringerName) order.stringerName else order.buyerName,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        order.selectedString,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = statusColor.copy(alpha = 0.12f)
                ) {
                    Text(
                        order.statusDisplayName,
                        color = statusColor,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "Total: $${String.format("%.2f", order.total)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            order.createdAt?.let { date ->
                val fmt = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
                Text(fmt.format(date), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
