package com.athletebridge.android.ui.stringing

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.athletebridge.android.ui.theme.LogoBlue
import com.athletebridge.android.ui.theme.LogoGreen
import com.athletebridge.android.ui.theme.StatusCancelled
import java.text.SimpleDateFormat
import java.util.*

private val StatusOrange = Color(0xFFFF9500)
private val StatusPurple = Color(0xFF7C3AED)
private val StatusTeal   = Color(0xFF0D9488)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StringerOrderDetailScreen(
    orderId: String,
    onBack: () -> Unit,
    onMessage: (String) -> Unit,
    viewModel: StringerOrderDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(orderId) { viewModel.loadOrder(orderId) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Order Detail") },
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
        } else {
            val order = uiState.order
            if (order == null) {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Text("Order not found")
                }
            } else {
                val isStringer = viewModel.currentUserId == order.stringerId
                val otherPartyName = if (isStringer) order.buyerName else order.stringerName
                val roleLabel = if (isStringer) "Buyer" else "Stringer"

                val statusColor = when (order.status.lowercase()) {
                    "placed"           -> LogoBlue
                    "accepted"         -> StatusOrange
                    "stringing"        -> StatusPurple
                    "ready_for_pickup" -> StatusTeal
                    "picked_up", "completed" -> LogoGreen
                    "declined"         -> StatusCancelled
                    else               -> MaterialTheme.colorScheme.onSurfaceVariant
                }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Header: other party name + status badge
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                otherPartyName,
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                roleLabel,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = statusColor.copy(alpha = 0.15f)
                        ) {
                            Text(
                                order.statusDisplayName,
                                color = statusColor,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }
                    }

                    // Progress timeline (hidden for declined orders)
                    if (order.status.lowercase() != "declined") {
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(0.dp)) {
                                Text(
                                    "Order Progress",
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )
                                val stageLabels = mapOf(
                                    "placed" to "Order Placed",
                                    "accepted" to "Accepted by Stringer",
                                    "stringing" to "Stringing in Progress",
                                    "ready_for_pickup" to "Ready for Pickup",
                                    "picked_up" to "Picked Up"
                                )
                                val tsFmt = remember { SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()) }
                                order.timelineStages.forEachIndexed { index, stage ->
                                    val reached = order.isStageReached(stage)
                                    Row(verticalAlignment = Alignment.Top) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Icon(
                                                if (reached) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                                                contentDescription = null,
                                                tint = if (reached) LogoGreen else MaterialTheme.colorScheme.outlineVariant,
                                                modifier = Modifier.size(22.dp)
                                            )
                                            if (index < order.timelineStages.lastIndex) {
                                                Box(
                                                    Modifier
                                                        .width(2.dp)
                                                        .height(24.dp)
                                                        .padding(vertical = 2.dp)
                                                ) {
                                                    Surface(
                                                        color = if (reached) LogoGreen else MaterialTheme.colorScheme.outlineVariant,
                                                        modifier = Modifier.fillMaxSize()
                                                    ) {}
                                                }
                                            }
                                        }
                                        Spacer(Modifier.width(10.dp))
                                        Column(Modifier.padding(bottom = 8.dp)) {
                                            Text(
                                                stageLabels[stage] ?: stage,
                                                fontWeight = if (reached) FontWeight.SemiBold else FontWeight.Normal,
                                                color = if (reached) MaterialTheme.colorScheme.onSurface
                                                        else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            order.stageTimestamp(stage)?.takeIf { reached }?.let {
                                                Text(
                                                    tsFmt.format(it),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Order details card
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            // Racket
                            if (order.racketName.isNotBlank()) {
                                OrderDetailRow(
                                    icon = Icons.Default.SportsTennis,
                                    label = "Racket",
                                    value = order.racketName
                                )
                            }

                            // String
                            OrderDetailRow(
                                icon = Icons.Default.FitnessCenter,
                                label = "String",
                                value = if (order.hasOwnString) "Own string"
                                        else order.selectedString.ifBlank { "—" }
                            )

                            // Tension
                            if (order.tension > 0) {
                                OrderDetailRow(
                                    icon = Icons.Default.Speed,
                                    label = "Tension",
                                    value = "${order.tension} lbs"
                                )
                            }

                            // Timeline
                            if (order.timelinePreference.isNotBlank()) {
                                OrderDetailRow(
                                    icon = Icons.Default.Schedule,
                                    label = "Timeline",
                                    value = order.timelinePreference
                                )
                            }

                            // Labor cost
                            if (order.laborCost > 0) {
                                OrderDetailRow(
                                    icon = Icons.Default.Build,
                                    label = "Labor",
                                    value = "$${String.format("%.2f", order.laborCost)}"
                                )
                            }

                            // String cost (only if not own string)
                            if (!order.hasOwnString && order.stringCost > 0) {
                                OrderDetailRow(
                                    icon = Icons.Default.AttachMoney,
                                    label = "String Cost",
                                    value = "$${String.format("%.2f", order.stringCost)}"
                                )
                            }

                            HorizontalDivider()

                            // Order total
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.AttachMoney,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                        tint = LogoGreen
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        "Order Total",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                                Text(
                                    "$${String.format("%.2f", order.total)}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = LogoGreen
                                )
                            }

                            // Placed date
                            order.createdAt?.let { date ->
                                OrderDetailRow(
                                    icon = Icons.Default.CalendarMonth,
                                    label = "Placed",
                                    value = SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(date)
                                )
                            }

                            // Notes
                            order.notes?.takeIf { it.isNotBlank() }?.let { notes ->
                                OrderDetailRow(
                                    icon = Icons.Default.Notes,
                                    label = "Notes",
                                    value = notes
                                )
                            }
                        }
                    }

                    // Message button
                    val otherUserId = if (isStringer) order.buyerId else order.stringerId
                    Button(
                        onClick = { onMessage(otherUserId) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = LogoBlue)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Message, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(if (isStringer) "Message Buyer" else "Message Stringer")
                    }

                    // Action buttons (stringer)
                    if (isStringer) {
                        when (order.status.lowercase()) {
                            "placed" -> {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(
                                        onClick = { viewModel.updateStatus("accepted") },
                                        enabled = !uiState.isUpdating,
                                        colors = ButtonDefaults.buttonColors(containerColor = LogoGreen),
                                        modifier = Modifier.weight(1f)
                                    ) { Text("Accept") }
                                    Button(
                                        onClick = { viewModel.updateStatus("declined") },
                                        enabled = !uiState.isUpdating,
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = StatusCancelled.copy(alpha = 0.1f),
                                            contentColor = StatusCancelled
                                        ),
                                        modifier = Modifier.weight(1f)
                                    ) { Text("Decline") }
                                }
                            }
                            "accepted" -> {
                                Button(
                                    onClick = { viewModel.updateStatus("stringing") },
                                    enabled = !uiState.isUpdating,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = StatusPurple.copy(alpha = 0.15f),
                                        contentColor = StatusPurple
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                ) { Text("Mark as Stringing") }
                            }
                            "stringing" -> {
                                Button(
                                    onClick = { viewModel.updateStatus("ready_for_pickup") },
                                    enabled = !uiState.isUpdating,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = StatusTeal.copy(alpha = 0.15f),
                                        contentColor = StatusTeal
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                ) { Text("Mark as Ready For Pickup") }
                            }
                        }
                    }

                    // Action button (buyer: pick up)
                    if (!isStringer && order.status.lowercase() == "ready_for_pickup") {
                        Button(
                            onClick = { viewModel.updateStatus("picked_up") },
                            enabled = !uiState.isUpdating,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = LogoGreen.copy(alpha = 0.15f),
                                contentColor = LogoGreen
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Mark as Picked Up") }
                    }

                    if (uiState.isUpdating) {
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        }
                    }

                    uiState.error?.let {
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun OrderDetailRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String
) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.width(8.dp))
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
    }
}
