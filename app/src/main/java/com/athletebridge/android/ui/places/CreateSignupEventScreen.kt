package com.athletebridge.android.ui.places

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.athletebridge.android.data.repository.PlaceRepository
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject
import com.athletebridge.android.ui.theme.LogoGreen

data class CreateSignupEventUiState(
    val isSaving: Boolean = false,
    val saveSuccess: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class CreateSignupEventViewModel @Inject constructor(
    private val placeRepo: PlaceRepository,
    private val auth: FirebaseAuth
) : ViewModel() {

    private val _uiState = MutableStateFlow(CreateSignupEventUiState())
    val uiState: StateFlow<CreateSignupEventUiState> = _uiState

    fun createEvent(
        placeId: String,
        title: String,
        description: String?,
        date: Date,
        capacity: Int,
        repeatsWeekly: Boolean = false,
        feeUSD: Double? = null,
        paymentLink: String? = null,
        allowSelfReportPaid: Boolean = false
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            val uid = auth.currentUser?.uid ?: ""
            val place = placeRepo.fetchPlace(placeId)
            val result = placeRepo.createEvent(
                placeId = placeId,
                placeName = place?.name ?: "",
                title = title,
                description = description,
                eventDate = date,
                location = place?.address ?: "",
                maxSignups = capacity,
                createdBy = uid,
                recurrence = if (repeatsWeekly) "weekly" else null,
                feeUSD = feeUSD,
                paymentLink = paymentLink,
                allowSelfReportPaid = allowSelfReportPaid
            )
            _uiState.update {
                it.copy(isSaving = false, saveSuccess = result.isSuccess, error = result.exceptionOrNull()?.message)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateSignupEventScreen(
    placeId: String,
    onBack: () -> Unit,
    viewModel: CreateSignupEventViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var capacityText by remember { mutableStateOf("") }
    var selectedDateMillis by remember { mutableLongStateOf(System.currentTimeMillis() + 86400000L) }
    var showDatePicker by remember { mutableStateOf(false) }
    var repeatsWeekly by remember { mutableStateOf(false) }
    var feeText by remember { mutableStateOf("") }
    var paymentLink by remember { mutableStateOf("") }
    var allowSelfReport by remember { mutableStateOf(false) }

    if (uiState.saveSuccess) {
        LaunchedEffect(Unit) { onBack() }
    }

    val dateFmt = remember { SimpleDateFormat("MMM d, yyyy", Locale.getDefault()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Create Event") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Event Title") },
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Description (optional)") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3
            )

            OutlinedTextField(
                value = capacityText,
                onValueChange = { capacityText = it },
                label = { Text("Max signups") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )

            Text("Date")
            OutlinedButton(
                onClick = { showDatePicker = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(dateFmt.format(Date(selectedDateMillis)))
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Repeats Weekly")
                Switch(checked = repeatsWeekly, onCheckedChange = { repeatsWeekly = it })
            }

            OutlinedTextField(
                value = feeText,
                onValueChange = { feeText = it },
                label = { Text("Fee per player, e.g. 8 (optional)") },
                prefix = { Text("$") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = paymentLink,
                onValueChange = { paymentLink = it },
                label = { Text("Payment link or @venmo-handle (optional)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Players can mark themselves paid")
                Switch(checked = allowSelfReport, onCheckedChange = { allowSelfReport = it })
            }
            Text(
                "Players see the fee and a payment button on the signup page. You can mark who has paid on the event screen.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            uiState.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

            Button(
                onClick = {
                    viewModel.createEvent(
                        placeId = placeId,
                        title = title,
                        description = description.takeIf { it.isNotBlank() },
                        date = Date(selectedDateMillis),
                        capacity = capacityText.toIntOrNull() ?: 0,
                        repeatsWeekly = repeatsWeekly,
                        feeUSD = feeText.removePrefix("$").trim().toDoubleOrNull(),
                        paymentLink = paymentLink.trim().takeIf { it.isNotBlank() },
                        allowSelfReportPaid = allowSelfReport
                    )
                },
                enabled = title.isNotBlank() && (capacityText.toIntOrNull() ?: 0) > 0 && !uiState.isSaving,
                colors = ButtonDefaults.buttonColors(containerColor = LogoGreen),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (uiState.isSaving) CircularProgressIndicator(Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary)
                else Text("Create Event")
            }
        }
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = selectedDateMillis)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { selectedDateMillis = it }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Cancel") } }
        ) { DatePicker(state = datePickerState) }
    }
}
