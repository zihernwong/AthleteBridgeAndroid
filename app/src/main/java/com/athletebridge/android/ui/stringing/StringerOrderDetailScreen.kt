package com.athletebridge.android.ui.stringing

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.athletebridge.android.ui.theme.LogoBlue
import com.athletebridge.android.ui.theme.LogoGreen
import com.athletebridge.android.ui.theme.StatusCancelled
import java.text.SimpleDateFormat
import java.util.*

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
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Order info card
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Order Details", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            HorizontalDivider()
                            InfoRow("String", order.selectedString)
                            InfoRow("String Cost", "$${String.format("%.2f", order.stringCost)}")
                            InfoRow("Labor Cost", "$${String.format("%.2f", order.laborCost)}")
                            InfoRow("Total", "$${String.format("%.2f", order.total)}")
                            if (isStringer) InfoRow("Customer", order.buyerName) else InfoRow("Stringer", order.stringerName)
                            order.createdAt?.let { date ->
                                InfoRow("Placed", SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(date))
                            }
                            order.notes?.takeIf { it.isNotBlank() }?.let {
                                InfoRow("Notes", it)
                            }

                            // Status badge
                            val statusColor = when (order.status.lowercase()) {
                                "placed" -> MaterialTheme.colorScheme.primary
                                "accepted" -> LogoBlue
                                "stringing" -> LogoGreen
                                "completed" -> LogoGreen
                                "declined" -> StatusCancelled
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            }
                            Surface(shape = MaterialTheme.shapes.small, color = statusColor.copy(alpha = 0.12f)) {
                                Text(
                                    order.statusDisplayName,
                                    color = statusColor,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }

                    // Action buttons for stringer
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
                                    OutlinedButton(
                                        onClick = { viewModel.updateStatus("declined") },
                                        enabled = !uiState.isUpdating,
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = StatusCancelled),
                                        modifier = Modifier.weight(1f)
                                    ) { Text("Decline") }
                                }
                            }
                            "accepted" -> {
                                Button(
                                    onClick = { viewModel.updateStatus("stringing") },
                                    enabled = !uiState.isUpdating,
                                    colors = ButtonDefaults.buttonColors(containerColor = LogoBlue),
                                    modifier = Modifier.fillMaxWidth()
                                ) { Text("Mark as Stringing") }
                            }
                            "stringing" -> {
                                Button(
                                    onClick = { viewModel.updateStatus("completed") },
                                    enabled = !uiState.isUpdating,
                                    colors = ButtonDefaults.buttonColors(containerColor = LogoGreen),
                                    modifier = Modifier.fillMaxWidth()
                                ) { Text("Mark as Complete") }
                            }
                        }
                    }

                    // Message button
                    val otherUserId = if (isStringer) order.buyerId else order.stringerId
                    OutlinedButton(
                        onClick = { onMessage(otherUserId) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(if (isStringer) "Message Customer" else "Message Stringer")
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        Text(value, fontWeight = FontWeight.Medium, style = MaterialTheme.typography.bodyMedium)
    }
}
