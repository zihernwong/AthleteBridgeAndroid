package com.athletebridge.android.ui.stringing

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.athletebridge.android.data.model.BadmintonStringer
import com.athletebridge.android.data.repository.NotificationRepository
import com.athletebridge.android.data.repository.StringerRepository
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.athletebridge.android.ui.shared.AvatarView
import com.athletebridge.android.ui.theme.LogoBlue
import com.athletebridge.android.ui.theme.LogoGreen
import javax.inject.Inject

private val PRESET_STRINGS = listOf(
    "BG65", "BG65T", "BG66F", "BG66UM", "BG80", "BG80P",
    "EX63", "AB", "ABBT", "EX65", "EX68", "SKYARC"
)

private val TIMELINE_OPTIONS = listOf("ASAP", "Within 1 day", "Within 3 days", "No rush")

data class StringerDetailUiState(
    val isLoading: Boolean = false,
    val stringer: BadmintonStringer? = null,
    val racketName: String = "",
    val hasOwnString: Boolean = false,
    val selectedString: String = "",
    val tension: Int = 24,
    val timelinePreference: String = "No rush",
    val isPlacingOrder: Boolean = false,
    val orderPlaced: Boolean = false,
    val error: String? = null,
    val validationMessage: String? = null
)

@HiltViewModel
class StringerDetailViewModel @Inject constructor(
    private val stringerRepo: StringerRepository,
    private val notificationRepo: NotificationRepository,
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

    fun setRacketName(value: String) = _uiState.update { it.copy(racketName = value) }
    fun setHasOwnString(value: Boolean) = _uiState.update { it.copy(hasOwnString = value, selectedString = "") }
    fun selectString(name: String) = _uiState.update { it.copy(selectedString = name) }
    fun setTension(value: Int) = _uiState.update { it.copy(tension = value.coerceIn(16, 35)) }
    fun setTimeline(value: String) = _uiState.update { it.copy(timelinePreference = value) }
    fun clearValidation() = _uiState.update { it.copy(validationMessage = null) }

    fun placeOrder(buyerName: String) {
        val state = _uiState.value
        val stringer = state.stringer ?: return

        // Validate
        val missing = mutableListOf<String>()
        if (state.racketName.isBlank()) missing.add("racket name")
        if (!state.hasOwnString && state.selectedString.isBlank()) missing.add("string selection")
        if (missing.isNotEmpty()) {
            _uiState.update { it.copy(validationMessage = "Please provide: ${missing.joinToString(", ")}") }
            return
        }

        val stringCost = if (state.hasOwnString) 0.0 else (stringer.strings[state.selectedString] ?: 0.0)

        viewModelScope.launch {
            _uiState.update { it.copy(isPlacingOrder = true, error = null) }
            val result = stringerRepo.placeOrder(
                stringerId = stringer.id,
                stringerUserId = stringer.userId,
                stringerName = stringer.name,
                buyerId = currentUserId,
                buyerName = buyerName,
                racketName = state.racketName,
                hasOwnString = state.hasOwnString,
                selectedString = if (state.hasOwnString) "" else state.selectedString,
                timelinePreference = state.timelinePreference,
                tension = state.tension,
                stringCost = stringCost,
                laborCost = stringer.laborCost,
                notes = null
            )
            if (result.isSuccess && stringer.userId.isNotBlank()) {
                notificationRepo.sendNotification(
                    recipientId = stringer.userId,
                    title = "New Stringing Order",
                    body = "$buyerName has placed a stringing order for ${state.racketName.ifBlank { "a racket" }}.",
                    type = "stringer_order"
                )
            }
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

    LaunchedEffect(stringerId) { viewModel.loadStringer(stringerId) }

    if (uiState.orderPlaced) {
        LaunchedEffect(Unit) { onBack() }
    }

    uiState.validationMessage?.let { msg ->
        AlertDialog(
            onDismissRequest = viewModel::clearValidation,
            title = { Text("Missing Info") },
            text = { Text(msg) },
            confirmButton = {
                TextButton(onClick = viewModel::clearValidation) { Text("OK") }
            }
        )
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
                val isOwner = stringer.userId == viewModel.currentUserId
                // Build sorted strings that match preset order, then append extras
                val sortedStrings = PRESET_STRINGS.filter { stringer.strings.containsKey(it) } +
                        stringer.strings.keys.filter { it !in PRESET_STRINGS }.sorted()

                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Header: avatar + name + location
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            AvatarView(url = stringer.photoURLString, name = stringer.name, size = 56.dp)
                            Spacer(Modifier.width(16.dp))
                            Column {
                                Text(stringer.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                                if (stringer.meetupLocation.isNotBlank()) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            Icons.Default.LocationOn,
                                            contentDescription = null,
                                            modifier = Modifier.size(14.dp),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Spacer(Modifier.width(2.dp))
                                        Text(
                                            stringer.meetupLocation,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Owner: pricing summary
                    if (isOwner) {
                        item {
                            Card(Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("Pricing", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("Labor Per Racket", style = MaterialTheme.typography.bodyMedium)
                                        Text(
                                            "$${String.format("%.0f", stringer.laborCost)}",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = LogoGreen
                                        )
                                    }
                                    if (sortedStrings.isNotEmpty()) {
                                        HorizontalDivider()
                                        sortedStrings.forEach { name ->
                                            val cost = stringer.strings[name] ?: 0.0
                                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                                Text(name, style = MaterialTheme.typography.bodyMedium)
                                                Text(
                                                    "$${String.format("%.0f", cost)}",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Customer: inline order form
                    if (!isOwner) {
                        // Racket name
                        item {
                            Card(Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("Racket", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                    OutlinedTextField(
                                        value = uiState.racketName,
                                        onValueChange = viewModel::setRacketName,
                                        placeholder = { Text("e.g. Yonex Astrox 88D") },
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = true,
                                        leadingIcon = {
                                            Icon(Icons.Default.SportsTennis, contentDescription = null, modifier = Modifier.size(18.dp))
                                        }
                                    )
                                }
                            }
                        }

                        // String selection
                        item {
                            Card(Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("String", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                    Row(
                                        Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("I have my own string", style = MaterialTheme.typography.bodyMedium)
                                        Switch(
                                            checked = uiState.hasOwnString,
                                            onCheckedChange = viewModel::setHasOwnString,
                                            colors = SwitchDefaults.colors(checkedThumbColor = LogoGreen, checkedTrackColor = LogoGreen.copy(alpha = 0.4f))
                                        )
                                    }
                                    if (!uiState.hasOwnString) {
                                        if (sortedStrings.isEmpty()) {
                                            Text(
                                                "This stringer has not listed any strings.",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        } else {
                                            Text(
                                                "Select a string:",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            sortedStrings.forEach { name ->
                                                val cost = stringer.strings[name] ?: 0.0
                                                Row(
                                                    Modifier.fillMaxWidth(),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    val isSelected = uiState.selectedString == name
                                                    IconButton(
                                                        onClick = { viewModel.selectString(name) },
                                                        modifier = Modifier.size(32.dp)
                                                    ) {
                                                        Icon(
                                                            if (isSelected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                                                            contentDescription = null,
                                                            tint = if (isSelected) LogoGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                                                            modifier = Modifier.size(20.dp)
                                                        )
                                                    }
                                                    Spacer(Modifier.width(4.dp))
                                                    Text(name, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                                                    Text(
                                                        "$${String.format("%.0f", cost)}",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Tension stepper
                        item {
                            Card(Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(16.dp)) {
                                    Text("Tension (lbs)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                    Spacer(Modifier.height(8.dp))
                                    Row(
                                        Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        IconButton(onClick = { viewModel.setTension(uiState.tension - 1) }) {
                                            Icon(Icons.Default.Remove, contentDescription = "Decrease")
                                        }
                                        Text(
                                            "${uiState.tension} lbs",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        IconButton(onClick = { viewModel.setTension(uiState.tension + 1) }) {
                                            Icon(Icons.Default.Add, contentDescription = "Increase")
                                        }
                                    }
                                    Text(
                                        "Range: 16–35 lbs",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }

                        // Timeline preference
                        item {
                            Card(Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text("Timeline Preference", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                    Spacer(Modifier.height(4.dp))
                                    TIMELINE_OPTIONS.forEach { option ->
                                        val isSelected = uiState.timelinePreference == option
                                        Row(
                                            Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            IconButton(
                                                onClick = { viewModel.setTimeline(option) },
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Icon(
                                                    if (isSelected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                                                    contentDescription = null,
                                                    tint = if (isSelected) LogoGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }
                                            Spacer(Modifier.width(4.dp))
                                            Text(option, style = MaterialTheme.typography.bodyMedium)
                                        }
                                    }
                                }
                            }
                        }

                        // Order total summary
                        item {
                            val stringCost = if (uiState.hasOwnString) 0.0 else (stringer.strings[uiState.selectedString] ?: 0.0)
                            val total = stringer.laborCost + stringCost
                            Card(Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("Labor", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text("$${String.format("%.2f", stringer.laborCost)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    if (uiState.hasOwnString) {
                                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text("String", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            Text("Own string", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    } else if (uiState.selectedString.isNotBlank()) {
                                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text("String", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            Text("$${String.format("%.2f", stringCost)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                    HorizontalDivider()
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("Total", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                        Text(
                                            "$${String.format("%.2f", total)}",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = LogoGreen
                                        )
                                    }
                                }
                            }
                        }

                        // Submit button
                        item {
                            uiState.error?.let {
                                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                            }
                            Button(
                                onClick = { viewModel.placeOrder(buyerName) },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = !uiState.isPlacingOrder,
                                colors = ButtonDefaults.buttonColors(containerColor = LogoGreen)
                            ) {
                                if (uiState.isPlacingOrder) {
                                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White)
                                } else {
                                    Icon(Icons.Default.ShoppingCart, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text("Submit Stringing Order")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
