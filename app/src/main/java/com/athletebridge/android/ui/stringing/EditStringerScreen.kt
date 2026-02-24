package com.athletebridge.android.ui.stringing

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.athletebridge.android.ui.theme.LogoGreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditStringerScreen(
    onBack: () -> Unit,
    userName: String,
    photoURL: String?,
    viewModel: EditStringerViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) { viewModel.loadExisting() }

    if (uiState.saveSuccess) {
        LaunchedEffect(Unit) { onBack() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (uiState.existingProfile != null) "Edit Stringer Profile" else "Become a Stringer") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text("Strings & Pricing", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }

            items(uiState.strings.keys.sorted()) { name ->
                val price = uiState.strings[name] ?: 0.0
                Card(Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(name, fontWeight = FontWeight.Medium)
                            Text("$${String.format("%.2f", price)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = { viewModel.removeString(name) }) {
                            Icon(Icons.Default.Delete, contentDescription = "Remove", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }

            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Add String", style = MaterialTheme.typography.labelLarge)
                        OutlinedTextField(
                            value = uiState.newStringName,
                            onValueChange = { viewModel.setNewStringName(it) },
                            label = { Text("String Name (e.g. BG65)") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = uiState.newStringPrice,
                            onValueChange = { viewModel.setNewStringPrice(it) },
                            label = { Text("Price ($)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Button(
                            onClick = { viewModel.addString() },
                            enabled = uiState.newStringName.isNotBlank() && uiState.newStringPrice.isNotBlank(),
                            colors = ButtonDefaults.buttonColors(containerColor = LogoGreen)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Spacer(Modifier.width(4.dp))
                            Text("Add")
                        }
                    }
                }
            }

            item {
                OutlinedTextField(
                    value = uiState.laborCost,
                    onValueChange = { viewModel.setLaborCost(it) },
                    label = { Text("Labor Cost ($)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            item {
                OutlinedTextField(
                    value = uiState.meetupLocation,
                    onValueChange = { viewModel.setMeetupLocation(it) },
                    label = { Text("Meetup Location") },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            uiState.error?.let { error ->
                item { Text(error, color = MaterialTheme.colorScheme.error) }
            }

            item {
                Button(
                    onClick = { viewModel.save(userName, photoURL) },
                    enabled = !uiState.isSaving && uiState.meetupLocation.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = LogoGreen),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (uiState.isSaving) CircularProgressIndicator(Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary)
                    else Text("Save Profile")
                }
            }
        }
    }
}
