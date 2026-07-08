package com.athletebridge.android.ui.coach

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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.athletebridge.android.data.model.AwayTime
import com.athletebridge.android.data.repository.AuthRepository
import com.athletebridge.android.data.repository.AwayTimeRepository
import com.athletebridge.android.ui.theme.LogoGreen
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

// ── ViewModel ─────────────────────────────────────────────────────────────────

data class InputAwayTimeUiState(
    val awayTimes: List<AwayTime> = emptyList(),
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val saved: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class InputAwayTimeViewModel @Inject constructor(
    private val awayTimeRepository: AwayTimeRepository,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(InputAwayTimeUiState())
    val uiState: StateFlow<InputAwayTimeUiState> = _uiState.asStateFlow()

    private val coachId: String get() = authRepository.currentUser?.uid ?: ""

    fun loadAwayTimes() {
        viewModelScope.launch {
            awayTimeRepository.observeAwayTimes(coachId).collect { list ->
                _uiState.value = _uiState.value.copy(awayTimes = list, isLoading = false)
            }
        }
    }

    fun saveAwayTime(startAt: Date, endAt: Date, notes: String?) {
        if (coachId.isEmpty()) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true, error = null)
            val result = awayTimeRepository.saveAwayTime(coachId, startAt, endAt, notes)
            result.onSuccess {
                _uiState.value = _uiState.value.copy(isSaving = false, saved = true)
            }.onFailure { e ->
                _uiState.value = _uiState.value.copy(isSaving = false, error = e.message)
            }
        }
    }

    fun deleteAwayTime(id: String) {
        viewModelScope.launch {
            awayTimeRepository.deleteAwayTime(coachId, id)
        }
    }

    fun clearSaved() {
        _uiState.value = _uiState.value.copy(saved = false)
    }
}

// ── Screen ────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InputAwayTimeScreen(
    onBack: () -> Unit,
    viewModel: InputAwayTimeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) { viewModel.loadAwayTimes() }

    var showForm by remember { mutableStateOf(false) }
    var notes by remember { mutableStateOf("") }

    val nowPlusOneHour = remember { Calendar.getInstance().apply { add(Calendar.HOUR, 1); set(Calendar.MINUTE, 0) }.timeInMillis }
    val nowPlusTwoHours = remember { Calendar.getInstance().apply { add(Calendar.HOUR, 2); set(Calendar.MINUTE, 0) }.timeInMillis }

    var startDate by remember { mutableLongStateOf(nowPlusOneHour) }
    var endDate by remember { mutableLongStateOf(nowPlusTwoHours) }

    var showStartDatePicker by remember { mutableStateOf(false) }
    var showEndDatePicker by remember { mutableStateOf(false) }
    var showStartTimePicker by remember { mutableStateOf(false) }
    var showEndTimePicker by remember { mutableStateOf(false) }

    val dateFormat = remember { SimpleDateFormat("MMM d, yyyy", Locale.getDefault()) }
    val timeFormat = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }
    val displayFormat = remember { SimpleDateFormat("MMM d, yyyy h:mm a", Locale.getDefault()) }

    LaunchedEffect(uiState.saved) {
        if (uiState.saved) {
            showForm = false
            notes = ""
            val c1 = Calendar.getInstance().apply { add(Calendar.HOUR, 1); set(Calendar.MINUTE, 0) }
            val c2 = Calendar.getInstance().apply { add(Calendar.HOUR, 2); set(Calendar.MINUTE, 0) }
            startDate = c1.timeInMillis
            endDate = c2.timeInMillis
            viewModel.clearSaved()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Time Away") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            if (!showForm) {
                FloatingActionButton(
                    onClick = { showForm = true },
                    containerColor = LogoGreen
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add away time")
                }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ── Add form ──────────────────────────────────────────────────────
            if (showForm) {
                item {
                    Card(Modifier.fillMaxWidth()) {
                        Column(
                            Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text("Add Away Time", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

                            Text("Start", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = { showStartDatePicker = true }, Modifier.weight(1f)) {
                                    Text(dateFormat.format(Date(startDate)))
                                }
                                OutlinedButton(onClick = { showStartTimePicker = true }, Modifier.weight(1f)) {
                                    Text(timeFormat.format(Date(startDate)))
                                }
                            }

                            Text("End", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = { showEndDatePicker = true }, Modifier.weight(1f)) {
                                    Text(dateFormat.format(Date(endDate)))
                                }
                                OutlinedButton(onClick = { showEndTimePicker = true }, Modifier.weight(1f)) {
                                    Text(timeFormat.format(Date(endDate)))
                                }
                            }

                            OutlinedTextField(
                                value = notes,
                                onValueChange = { notes = it },
                                label = { Text("Reason (optional, e.g. Vacation)") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )

                            if (endDate <= startDate) {
                                Text("End must be after start", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                            }

                            uiState.error?.let { err ->
                                Text(err, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = { showForm = false }, Modifier.weight(1f)) {
                                    Text("Cancel")
                                }
                                Button(
                                    onClick = {
                                        viewModel.saveAwayTime(
                                            startAt = Date(startDate),
                                            endAt = Date(endDate),
                                            notes = notes.takeIf { it.isNotBlank() }
                                        )
                                    },
                                    modifier = Modifier.weight(1f),
                                    enabled = !uiState.isSaving && endDate > startDate,
                                    colors = ButtonDefaults.buttonColors(containerColor = LogoGreen)
                                ) {
                                    if (uiState.isSaving) {
                                        CircularProgressIndicator(Modifier.size(18.dp), color = MaterialTheme.colorScheme.onPrimary)
                                    } else {
                                        Text("Save")
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // ── Existing away times ───────────────────────────────────────────
            if (uiState.isLoading) {
                item {
                    Box(Modifier.fillMaxWidth().padding(vertical = 32.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
            } else if (uiState.awayTimes.isEmpty() && !showForm) {
                item {
                    Box(Modifier.fillMaxWidth().padding(vertical = 48.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("No away times set", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("Tap + to block off time when you're unavailable", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            } else {
                val now = Date()
                val upcoming = uiState.awayTimes.filter { it.endAt?.after(now) == true }
                val past = uiState.awayTimes.filter { it.endAt?.after(now) != true }

                if (upcoming.isNotEmpty()) {
                    item {
                        Text("Upcoming", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    items(upcoming, key = { it.id }) { awayTime ->
                        AwayTimeCard(awayTime, displayFormat, onDelete = { viewModel.deleteAwayTime(awayTime.id) })
                    }
                }

                if (past.isNotEmpty()) {
                    item {
                        Spacer(Modifier.height(4.dp))
                        Text("Past", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    items(past, key = { it.id }) { awayTime ->
                        AwayTimeCard(awayTime, displayFormat, onDelete = { viewModel.deleteAwayTime(awayTime.id) })
                    }
                }
            }

            item { Spacer(Modifier.height(72.dp)) }
        }
    }

    // ── Dialogs ───────────────────────────────────────────────────────────────

    if (showStartDatePicker) {
        val dpState = rememberDatePickerState(initialSelectedDateMillis = startDate)
        DatePickerDialog(
            onDismissRequest = { showStartDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    dpState.selectedDateMillis?.let { millis ->
                        val oldCal = Calendar.getInstance().apply { timeInMillis = startDate }
                        val newCal = Calendar.getInstance().apply { timeInMillis = millis }
                        newCal.set(Calendar.HOUR_OF_DAY, oldCal.get(Calendar.HOUR_OF_DAY))
                        newCal.set(Calendar.MINUTE, oldCal.get(Calendar.MINUTE))
                        startDate = newCal.timeInMillis
                    }
                    showStartDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showStartDatePicker = false }) { Text("Cancel") } }
        ) { DatePicker(state = dpState) }
    }

    if (showEndDatePicker) {
        val dpState = rememberDatePickerState(initialSelectedDateMillis = endDate)
        DatePickerDialog(
            onDismissRequest = { showEndDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    dpState.selectedDateMillis?.let { millis ->
                        val oldCal = Calendar.getInstance().apply { timeInMillis = endDate }
                        val newCal = Calendar.getInstance().apply { timeInMillis = millis }
                        newCal.set(Calendar.HOUR_OF_DAY, oldCal.get(Calendar.HOUR_OF_DAY))
                        newCal.set(Calendar.MINUTE, oldCal.get(Calendar.MINUTE))
                        endDate = newCal.timeInMillis
                    }
                    showEndDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showEndDatePicker = false }) { Text("Cancel") } }
        ) { DatePicker(state = dpState) }
    }

    if (showStartTimePicker) {
        val cal = Calendar.getInstance().apply { timeInMillis = startDate }
        val tpState = rememberTimePickerState(
            initialHour = cal.get(Calendar.HOUR_OF_DAY),
            initialMinute = (cal.get(Calendar.MINUTE) / 30) * 30
        )
        AlertDialog(
            onDismissRequest = { showStartTimePicker = false },
            title = { Text("Select Start Time") },
            text = { TimePicker(state = tpState) },
            confirmButton = {
                TextButton(onClick = {
                    val newCal = Calendar.getInstance().apply { timeInMillis = startDate }
                    newCal.set(Calendar.HOUR_OF_DAY, tpState.hour)
                    newCal.set(Calendar.MINUTE, (tpState.minute / 30) * 30)
                    startDate = newCal.timeInMillis
                    showStartTimePicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showStartTimePicker = false }) { Text("Cancel") } }
        )
    }

    if (showEndTimePicker) {
        val cal = Calendar.getInstance().apply { timeInMillis = endDate }
        val tpState = rememberTimePickerState(
            initialHour = cal.get(Calendar.HOUR_OF_DAY),
            initialMinute = (cal.get(Calendar.MINUTE) / 30) * 30
        )
        AlertDialog(
            onDismissRequest = { showEndTimePicker = false },
            title = { Text("Select End Time") },
            text = { TimePicker(state = tpState) },
            confirmButton = {
                TextButton(onClick = {
                    val newCal = Calendar.getInstance().apply { timeInMillis = endDate }
                    newCal.set(Calendar.HOUR_OF_DAY, tpState.hour)
                    newCal.set(Calendar.MINUTE, (tpState.minute / 30) * 30)
                    endDate = newCal.timeInMillis
                    showEndTimePicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showEndTimePicker = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun AwayTimeCard(
    awayTime: AwayTime,
    displayFormat: SimpleDateFormat,
    onDelete: () -> Unit
) {
    Card(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    awayTime.notes?.takeIf { it.isNotBlank() } ?: "Away",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                awayTime.startAt?.let { start ->
                    Text(
                        displayFormat.format(start),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                awayTime.endAt?.let { end ->
                    Text(
                        "Until ${displayFormat.format(end)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}
