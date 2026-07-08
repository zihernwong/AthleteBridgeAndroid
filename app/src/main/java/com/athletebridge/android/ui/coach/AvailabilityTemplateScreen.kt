package com.athletebridge.android.ui.coach

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import com.athletebridge.android.data.model.AvailabilityTemplate
import com.athletebridge.android.data.model.DayAvailability
import com.athletebridge.android.data.repository.AuthRepository
import com.athletebridge.android.data.repository.AvailabilityTemplateRepository
import com.athletebridge.android.ui.theme.LogoBlue
import com.athletebridge.android.ui.theme.LogoGreen
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

// ── ViewModel ─────────────────────────────────────────────────────────────────

private val DAY_NAMES = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")

data class AvailabilityTemplateUiState(
    val slots: List<DayAvailability> = DAY_NAMES.mapIndexed { index, _ ->
        DayAvailability(
            dayOfWeek = index + 1,
            startHour = 8,
            startMinute = 0,
            endHour = 17,
            endMinute = 0,
            enabled = index < 5 // Mon-Fri enabled by default
        )
    },
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val saved: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class AvailabilityTemplateViewModel @Inject constructor(
    private val templateRepo: AvailabilityTemplateRepository,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AvailabilityTemplateUiState())
    val uiState: StateFlow<AvailabilityTemplateUiState> = _uiState.asStateFlow()

    private val coachId: String get() = authRepository.currentUser?.uid ?: ""

    fun loadTemplate() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val template = templateRepo.fetchTemplate(coachId)
            if (template != null && template.slots.isNotEmpty()) {
                // Merge loaded slots with default 7-day structure so all days are present
                val merged = (1..7).map { day ->
                    template.slots.firstOrNull { it.dayOfWeek == day }
                        ?: DayAvailability(dayOfWeek = day, startHour = 8, startMinute = 0, endHour = 17, endMinute = 0, enabled = false)
                }
                _uiState.value = _uiState.value.copy(isLoading = false, slots = merged)
            } else {
                _uiState.value = _uiState.value.copy(isLoading = false)
            }
        }
    }

    fun toggleDay(dayIndex: Int, enabled: Boolean) {
        val slots = _uiState.value.slots.toMutableList()
        slots[dayIndex] = slots[dayIndex].copy(enabled = enabled)
        _uiState.value = _uiState.value.copy(slots = slots)
    }

    fun updateStartTime(dayIndex: Int, hour: Int, minute: Int) {
        val slots = _uiState.value.slots.toMutableList()
        slots[dayIndex] = slots[dayIndex].copy(startHour = hour, startMinute = minute)
        _uiState.value = _uiState.value.copy(slots = slots)
    }

    fun updateEndTime(dayIndex: Int, hour: Int, minute: Int) {
        val slots = _uiState.value.slots.toMutableList()
        slots[dayIndex] = slots[dayIndex].copy(endHour = hour, endMinute = minute)
        _uiState.value = _uiState.value.copy(slots = slots)
    }

    fun saveTemplate() {
        val id = coachId
        if (id.isEmpty()) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true, error = null)
            val template = AvailabilityTemplate(coachId = id, slots = _uiState.value.slots)
            val result = templateRepo.saveTemplate(id, template)
            result.onSuccess {
                _uiState.value = _uiState.value.copy(isSaving = false, saved = true)
            }.onFailure { e ->
                _uiState.value = _uiState.value.copy(isSaving = false, error = e.message)
            }
        }
    }

    fun clearSaved() {
        _uiState.value = _uiState.value.copy(saved = false)
    }
}

// ── Screen ────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AvailabilityTemplateScreen(
    onBack: () -> Unit,
    viewModel: AvailabilityTemplateViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) { viewModel.loadTemplate() }
    LaunchedEffect(uiState.saved) {
        if (uiState.saved) {
            onBack()
        }
    }

    // Track which day is showing the start/end time picker
    var startPickerDay by remember { mutableStateOf<Int?>(null) }
    var endPickerDay by remember { mutableStateOf<Int?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Weekly Availability Template") },
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
            Column(
                modifier = Modifier.fillMaxSize().padding(padding)
            ) {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(uiState.slots) { index, slot ->
                        DayAvailabilityCard(
                            dayName = DAY_NAMES[index],
                            slot = slot,
                            onToggle = { enabled -> viewModel.toggleDay(index, enabled) },
                            onStartTimeClick = { startPickerDay = index },
                            onEndTimeClick = { endPickerDay = index }
                        )
                    }
                }

                uiState.error?.let { err ->
                    Text(
                        err,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }

                Button(
                    onClick = { viewModel.saveTemplate() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    enabled = !uiState.isSaving,
                    colors = ButtonDefaults.buttonColors(containerColor = LogoGreen)
                ) {
                    if (uiState.isSaving) {
                        CircularProgressIndicator(Modifier.size(18.dp), color = MaterialTheme.colorScheme.onPrimary)
                    } else {
                        Text("Save Template")
                    }
                }
            }
        }
    }

    // Start time picker dialog
    startPickerDay?.let { dayIndex ->
        val slot = uiState.slots[dayIndex]
        val tpState = rememberTimePickerState(
            initialHour = slot.startHour,
            initialMinute = slot.startMinute
        )
        AlertDialog(
            onDismissRequest = { startPickerDay = null },
            title = { Text("Start Time — ${DAY_NAMES[dayIndex]}") },
            text = { TimePicker(state = tpState) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.updateStartTime(dayIndex, tpState.hour, tpState.minute)
                    startPickerDay = null
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { startPickerDay = null }) { Text("Cancel") } }
        )
    }

    // End time picker dialog
    endPickerDay?.let { dayIndex ->
        val slot = uiState.slots[dayIndex]
        val tpState = rememberTimePickerState(
            initialHour = slot.endHour,
            initialMinute = slot.endMinute
        )
        AlertDialog(
            onDismissRequest = { endPickerDay = null },
            title = { Text("End Time — ${DAY_NAMES[dayIndex]}") },
            text = { TimePicker(state = tpState) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.updateEndTime(dayIndex, tpState.hour, tpState.minute)
                    endPickerDay = null
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { endPickerDay = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun DayAvailabilityCard(
    dayName: String,
    slot: DayAvailability,
    onToggle: (Boolean) -> Unit,
    onStartTimeClick: () -> Unit,
    onEndTimeClick: () -> Unit
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(dayName, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleSmall)
                Switch(
                    checked = slot.enabled,
                    onCheckedChange = onToggle,
                    colors = SwitchDefaults.colors(checkedThumbColor = LogoBlue)
                )
            }
            if (slot.enabled) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onStartTimeClick, modifier = Modifier.weight(1f)) {
                        Text(
                            "Start: %02d:%02d".format(slot.startHour, slot.startMinute),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    OutlinedButton(onClick = onEndTimeClick, modifier = Modifier.weight(1f)) {
                        Text(
                            "End: %02d:%02d".format(slot.endHour, slot.endMinute),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
    }
}
