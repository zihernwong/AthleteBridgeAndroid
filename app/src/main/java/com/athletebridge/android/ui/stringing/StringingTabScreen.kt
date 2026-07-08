package com.athletebridge.android.ui.stringing

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.athletebridge.android.data.model.BadmintonStringer
import com.athletebridge.android.data.model.StringerOrder
import com.athletebridge.android.ui.shared.AvatarView
import androidx.compose.foundation.shape.RoundedCornerShape
import com.athletebridge.android.ui.theme.LogoBlue
import com.athletebridge.android.ui.theme.LogoGreen
import com.athletebridge.android.ui.theme.StatusCancelled
import java.text.SimpleDateFormat
import java.util.*

private val OrderStatusOrange = android.graphics.Color.parseColor("#FF9500").let {
    androidx.compose.ui.graphics.Color(it)
}
private val OrderStatusPurple = androidx.compose.ui.graphics.Color(0xFF7C3AED)
private val OrderStatusTeal   = androidx.compose.ui.graphics.Color(0xFF0D9488)

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
        PullToRefreshBox(
            isRefreshing = uiState.isRefreshing,
            onRefresh = { viewModel.refresh() },
            modifier = Modifier.fillMaxSize().padding(padding)
        ) {
            Column(Modifier.fillMaxSize()) {
                TabRow(selectedTabIndex = uiState.selectedTabIndex) {
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            selected = uiState.selectedTabIndex == index,
                            onClick = { viewModel.selectTab(index) },
                            text = { Text(title) }
                        )
                    }
                }

                if (uiState.isLoading && !uiState.isRefreshing) {
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
        val pending = orders.filter { it.status.lowercase() == "placed" }
        val active  = orders.filter { it.status.lowercase() in listOf("accepted", "stringing", "ready_for_pickup") }
        val history = orders.filter { it.status.lowercase() in listOf("completed", "picked_up", "declined") }

        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (pending.isNotEmpty()) {
                item {
                    Text(
                        "Pending",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
                items(pending, key = { it.id }) { order ->
                    OrderCard(order = order, showStringerName = false, onClick = { onOrderDetail(order.id) })
                }
            }
            if (active.isNotEmpty()) {
                item {
                    Text(
                        "Active",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = if (pending.isNotEmpty()) 8.dp else 4.dp, bottom = 4.dp)
                    )
                }
                items(active, key = { it.id }) { order ->
                    OrderCard(order = order, showStringerName = false, onClick = { onOrderDetail(order.id) })
                }
            }
            if (history.isNotEmpty()) {
                item {
                    Text(
                        "History",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = if (pending.isNotEmpty() || active.isNotEmpty()) 8.dp else 4.dp, bottom = 4.dp)
                    )
                }
                items(history, key = { it.id }) { order ->
                    OrderCard(order = order, showStringerName = false, onClick = { onOrderDetail(order.id) })
                }
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
        "placed"           -> LogoBlue
        "accepted"         -> OrderStatusOrange
        "stringing"        -> OrderStatusPurple
        "ready_for_pickup" -> OrderStatusTeal
        "picked_up", "completed" -> LogoGreen
        "declined"         -> StatusCancelled
        else               -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            // Header: name + status badge
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    if (showStringerName) order.stringerName else order.buyerName,
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f)
                )
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = statusColor.copy(alpha = 0.15f)
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

            // Racket row
            if (order.racketName.isNotBlank()) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(Icons.Default.SportsTennis, contentDescription = null, modifier = Modifier.size(12.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(order.racketName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            // String info
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(Icons.Default.FitnessCenter, contentDescription = null, modifier = Modifier.size(12.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                if (order.hasOwnString) {
                    Text("Has own string", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Text(
                        order.selectedString.ifBlank { "—" },
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium
                    )
                    if (order.stringCost > 0) {
                        Text(
                            " · $${String.format("%.0f", order.stringCost)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Specs row: tension + timeline
            if (order.tension > 0 || order.timelinePreference.isNotBlank()) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (order.tension > 0) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(Icons.Default.Speed, contentDescription = null, modifier = Modifier.size(12.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("${order.tension} lbs", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    if (order.timelinePreference.isNotBlank()) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(Icons.Default.Schedule, contentDescription = null, modifier = Modifier.size(12.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(order.timelinePreference, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            // Order total
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Order Total:", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                Text(
                    "$${String.format("%.2f", order.total)}",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = LogoGreen
                )
            }

            // Date
            order.createdAt?.let { date ->
                Text(
                    SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(date),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
