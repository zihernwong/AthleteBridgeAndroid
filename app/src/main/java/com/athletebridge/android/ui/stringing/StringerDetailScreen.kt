package com.athletebridge.android.ui.stringing

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.athletebridge.android.data.model.BadmintonStringer
import com.athletebridge.android.data.repository.StringerRepository
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.athletebridge.android.ui.shared.AvatarView
import com.athletebridge.android.ui.theme.LogoGreen
import javax.inject.Inject

data class StringerDetailUiState(
    val isLoading: Boolean = false,
    val stringer: BadmintonStringer? = null,
    val selectedString: String = "",
    val notes: String = "",
    val isPlacingOrder: Boolean = false,
    val orderPlaced: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class StringerDetailViewModel @Inject constructor(
    private val stringerRepo: StringerRepository,
    private val auth: FirebaseAuth
) : ViewModel() {

    private val _uiState = MutableStateFlow(StringerDetailUiState())
    val uiState: StateFlow<StringerDetailUiState> = _uiState

    val currentUserId: String get() = auth.currentUser?.uid ?: ""

    fun loadStringer(stringerId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val stringer = stringerRepo.fetchStringer(stringerId)
            _uiState.update { it.copy(isLoading = false, stringer = stringer) }
        }
    }

    fun selectString(name: String) = _uiState.update { it.copy(selectedString = name) }
    fun setNotes(value: String) = _uiState.update { it.copy(notes = value) }

    fun placeOrder(buyerName: String) {
        val stringer = _uiState.value.stringer ?: return
        val selected = _uiState.value.selectedString
        if (selected.isBlank()) return
        val stringCost = stringer.strings[selected] ?: 0.0
        viewModelScope.launch {
            _uiState.update { it.copy(isPlacingOrder = true) }
            val result = stringerRepo.placeOrder(
                stringerId = stringer.id,
                stringerName = stringer.name,
                buyerId = currentUserId,
                buyerName = buyerName,
                selectedString = selected,
                stringCost = stringCost,
                laborCost = stringer.laborCost,
                notes = _uiState.value.notes.takeIf { it.isNotBlank() }
            )
            _uiState.update {
                it.copy(
                    isPlacingOrder = false,
                    orderPlaced = result.isSuccess,
                    error = result.exceptionOrNull()?.message
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StringerDetailScreen(
    stringerId: String,
    onBack: () -> Unit,
    buyerName: String,
    viewModel: StringerDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showOrderDialog by remember { mutableStateOf(false) }

    LaunchedEffect(stringerId) { viewModel.loadStringer(stringerId) }

    if (uiState.orderPlaced) {
        LaunchedEffect(Unit) { onBack() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(uiState.stringer?.name ?: "Stringer") },
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
            val stringer = uiState.stringer
            if (stringer == null) {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Text("Stringer not found")
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Header
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            AvatarView(url = stringer.photoURLString, name = stringer.name, size = 64.dp)
                            Spacer(Modifier.width(16.dp))
                            Column {
                                Text(stringer.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                                Text(
                                    "Labor: $${String.format("%.0f", stringer.laborCost)}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (stringer.meetupLocation.isNotBlank()) {
                                    Text(
                                        stringer.meetupLocation,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    // Strings offered
                    if (stringer.strings.isNotEmpty()) {
                        item {
                            Text("Available Strings", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        }
                        items(stringer.strings.keys.sorted()) { stringName ->
                            val price = stringer.strings[stringName] ?: 0.0
                            Card(Modifier.fillMaxWidth()) {
                                Row(
                                    Modifier.padding(16.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(stringName, fontWeight = FontWeight.Medium)
                                    Text("$${String.format("%.2f", price)}", color = LogoGreen, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }

                    // Place order button
                    item {
                        Button(
                            onClick = { showOrderDialog = true },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = LogoGreen)
                        ) {
                            Text("Place Order")
                        }
                    }
                }
            }
        }
    }

    if (showOrderDialog) {
        PlaceOrderDialog(
            stringer = uiState.stringer!!,
            selectedString = uiState.selectedString,
            notes = uiState.notes,
            isPlacing = uiState.isPlacingOrder,
            onStringSelected = { viewModel.selectString(it) },
            onNotesChanged = { viewModel.setNotes(it) },
            onConfirm = {
                viewModel.placeOrder(buyerName)
                showOrderDialog = false
            },
            onDismiss = { showOrderDialog = false }
        )
    }
}

@Composable
private fun PlaceOrderDialog(
    stringer: BadmintonStringer,
    selectedString: String,
    notes: String,
    isPlacing: Boolean,
    onStringSelected: (String) -> Unit,
    onNotesChanged: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Place Order") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Select a string:", style = MaterialTheme.typography.labelMedium)
                stringer.strings.keys.sorted().forEach { name ->
                    val price = stringer.strings[name] ?: 0.0
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedString == name,
                            onClick = { onStringSelected(name) }
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("$name — $${String.format("%.2f", price)}")
                    }
                }

                if (selectedString.isNotBlank()) {
                    val stringCost = stringer.strings[selectedString] ?: 0.0
                    val total = stringCost + stringer.laborCost
                    HorizontalDivider()
                    Text("Labor: $${String.format("%.2f", stringer.laborCost)}", style = MaterialTheme.typography.bodySmall)
                    Text("Total: $${String.format("%.2f", total)}", fontWeight = FontWeight.Bold)
                }

                OutlinedTextField(
                    value = notes,
                    onValueChange = onNotesChanged,
                    label = { Text("Notes (optional)") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = selectedString.isNotBlank() && !isPlacing
            ) { Text("Confirm Order") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
