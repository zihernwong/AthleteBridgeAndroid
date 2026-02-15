package com.athletebridge.android.ui.payments

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.athletebridge.android.data.model.CoachTier
import com.athletebridge.android.ui.auth.AuthViewModel
import com.athletebridge.android.ui.theme.LogoBlue
import com.athletebridge.android.ui.theme.LogoGreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaymentsScreen(
    isCoach: Boolean,
    authViewModel: AuthViewModel = hiltViewModel()
) {
    val uiState by authViewModel.uiState.collectAsState()

    Scaffold(
        topBar = { TopAppBar(title = { Text("Payments") }) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (isCoach) {
                // Coach: Show subscription tier and payment methods
                val tier = uiState.currentCoach?.subscriptionTier ?: CoachTier.FREE
                val tierColor = when (tier) {
                    CoachTier.FREE -> Color.Gray
                    CoachTier.PLUS -> Color.Blue
                    CoachTier.PRO -> Color(0xFF9C27B0)
                }

                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Subscription", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(8.dp))
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = tierColor.copy(alpha = 0.12f)
                        ) {
                            Text(
                                tier.displayName,
                                color = tierColor,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }
                }

                // Payment methods
                Text("Payment Methods", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                val payments = uiState.currentCoach?.payments
                if (payments.isNullOrEmpty()) {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            Text("No payment methods set up", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("Add payment methods in your Profile", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                } else {
                    payments.forEach { (platform, handle) ->
                        Card(Modifier.fillMaxWidth()) {
                            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Payment, contentDescription = null, tint = LogoBlue)
                                Spacer(Modifier.width(12.dp))
                                Column {
                                    Text(platform.replaceFirstChar { it.uppercase() }, fontWeight = FontWeight.SemiBold)
                                    Text(handle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            } else {
                // Client: show payment-related info
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.CreditCard, contentDescription = null, modifier = Modifier.size(48.dp), tint = LogoBlue)
                        Spacer(Modifier.height(8.dp))
                        Text("Payments", style = MaterialTheme.typography.titleMedium)
                        Text("Payment information will be shown here when you have bookings", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}
