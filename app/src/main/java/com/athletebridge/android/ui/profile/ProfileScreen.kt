package com.athletebridge.android.ui.profile

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.athletebridge.android.ui.shared.AvatarView
import com.athletebridge.android.ui.shared.ChipMultiSelect
import com.athletebridge.android.ui.theme.LogoBlue
import com.athletebridge.android.ui.theme.LogoGreen
import java.io.ByteArrayOutputStream

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    onLogout: () -> Unit = {},
    onTournamentPartner: () -> Unit = {},
    viewModel: ProfileViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val isCoach = uiState.userType?.uppercase() == "COACH"
    val isEditMode = if (isCoach) uiState.currentCoach != null else uiState.currentClient != null

    // Form state
    var name by remember { mutableStateOf("") }
    var hasPopulated by remember { mutableStateOf(false) }

    // Client fields
    var selectedGoals by remember { mutableStateOf(setOf<String>()) }
    var selectedClientAvailability by remember { mutableStateOf(setOf<String>()) }
    var clientMeetingPreference by remember { mutableStateOf("In-Person") }
    var clientSkillLevel by remember { mutableStateOf("Beginner") }
    var clientZipCode by remember { mutableStateOf("") }
    var clientCity by remember { mutableStateOf("") }
    var clientBio by remember { mutableStateOf("") }
    var clientTournamentLink by remember { mutableStateOf("") }

    // Coach fields
    var selectedSpecialties by remember { mutableStateOf(setOf<String>()) }
    var selectedCoachAvailability by remember { mutableStateOf(setOf<String>()) }
    var coachMeetingPreference by remember { mutableStateOf("In-Person") }
    var experienceYears by remember { mutableIntStateOf(0) }
    var hourlyRateText by remember { mutableStateOf("") }
    var coachRateLowerText by remember { mutableStateOf("") }
    var coachRateUpperText by remember { mutableStateOf("") }
    var coachBio by remember { mutableStateOf("") }
    var coachZipCode by remember { mutableStateOf("") }
    var coachCity by remember { mutableStateOf("") }
    var coachTournamentLink by remember { mutableStateOf("") }

    // Photo
    var selectedImageBytes by remember { mutableStateOf<ByteArray?>(null) }
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        uri?.let {
            context.contentResolver.openInputStream(it)?.use { stream ->
                val bitmap = BitmapFactory.decodeStream(stream)
                if (bitmap != null) {
                    val maxDim = 1024
                    val scale = maxDim.toFloat() / maxOf(bitmap.width, bitmap.height)
                    val resized = if (scale < 1f) {
                        Bitmap.createScaledBitmap(
                            bitmap,
                            (bitmap.width * scale).toInt(),
                            (bitmap.height * scale).toInt(),
                            true
                        )
                    } else bitmap
                    val baos = ByteArrayOutputStream()
                    resized.compress(Bitmap.CompressFormat.JPEG, 75, baos)
                    selectedImageBytes = baos.toByteArray()
                }
            }
        }
    }

    // Populate from existing profile
    LaunchedEffect(uiState.currentCoach, uiState.currentClient) {
        if (hasPopulated) return@LaunchedEffect
        uiState.currentCoach?.let { coach ->
            name = coach.name
            selectedSpecialties = coach.specialties.toSet()
            selectedCoachAvailability = coach.availability.toSet()
            experienceYears = coach.experienceYears
            coachBio = coach.bio ?: ""
            coachMeetingPreference = coach.meetingPreference ?: "In-Person"
            hourlyRateText = coach.hourlyRate?.let { String.format("%.2f", it) } ?: ""
            coachZipCode = coach.zipCode ?: ""
            coachCity = coach.city ?: ""
            coach.rateRange?.let { rr ->
                if (rr.size >= 2) {
                    coachRateLowerText = String.format("%.2f", rr[0])
                    coachRateUpperText = String.format("%.2f", rr[1])
                }
            }
            coachTournamentLink = coach.tournamentSoftwareLink ?: ""
            hasPopulated = true
        }
        uiState.currentClient?.let { client ->
            name = client.name
            selectedGoals = client.goals.toSet()
            selectedClientAvailability = client.preferredAvailability.toSet()
            clientMeetingPreference = client.meetingPreference ?: "In-Person"
            clientSkillLevel = client.skillLevel ?: "Beginner"
            clientZipCode = client.zipCode ?: ""
            clientCity = client.city ?: ""
            clientBio = client.bio ?: ""
            clientTournamentLink = client.tournamentSoftwareLink ?: ""
            hasPopulated = true
        }
    }

    // Show snackbar on save
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(uiState.saveMessage) {
        uiState.saveMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearSaveMessage()
        }
    }

    val availabilityOptions = listOf("Morning", "Afternoon", "Evening")
    val meetingOptions = listOf("In-Person", "Virtual")
    val skillLevelOptions = listOf("Beginner", "Intermediate", "Advanced")

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(if (isEditMode) "Edit Profile" else "Create Profile") },
                actions = {
                    TextButton(onClick = {
                        viewModel.logout()
                        onLogout()
                    }) {
                        Text("Logout", color = MaterialTheme.colorScheme.error)
                    }
                }
            )
        }
    ) { padding ->
        if (uiState.isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Email section
                uiState.userEmail?.let { email ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = MaterialTheme.shapes.extraLarge,
                                color = LogoGreen
                            ) {
                                Icon(
                                    Icons.Default.Email,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.padding(8.dp)
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text("Signed in as", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(email, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                // Name
                Text("Full Name", style = MaterialTheme.typography.labelLarge)
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Full Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Photo section
                Text("Profile Photo", style = MaterialTheme.typography.labelLarge)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val photoUrl = if (isCoach) uiState.currentCoach?.photoURLString else null
                    AvatarView(
                        url = if (selectedImageBytes != null) null else photoUrl,
                        name = name,
                        size = 90.dp
                    )
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Button(
                            onClick = {
                                photoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            }
                        ) {
                            Text(if (selectedImageBytes == null) "Choose Photo" else "Change Photo")
                        }
                        if (selectedImageBytes != null) {
                            Text("Will be uploaded on save", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }

                HorizontalDivider()

                // Role-specific fields
                if (isCoach) {
                    // === COACH SECTION ===
                    Text("Coach Details", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

                    // Specialties
                    Text("Specialties", style = MaterialTheme.typography.labelLarge)
                    if (uiState.subjectIds.isNotEmpty()) {
                        ChipMultiSelect(
                            items = uiState.subjectIds,
                            selection = selectedSpecialties,
                            onSelectionChange = { selectedSpecialties = it }
                        )
                    }

                    // Availability
                    Text("Availability", style = MaterialTheme.typography.labelLarge)
                    ChipMultiSelect(
                        items = availabilityOptions,
                        selection = selectedCoachAvailability,
                        onSelectionChange = { selectedCoachAvailability = it }
                    )

                    // Experience
                    Text("Experience (years)", style = MaterialTheme.typography.labelLarge)
                    OutlinedTextField(
                        value = experienceYears.toString(),
                        onValueChange = { experienceYears = it.toIntOrNull() ?: 0 },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Meeting Preference
                    Text("Meeting Preference", style = MaterialTheme.typography.labelLarge)
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        meetingOptions.forEachIndexed { index, option ->
                            SegmentedButton(
                                selected = coachMeetingPreference == option,
                                onClick = { coachMeetingPreference = option },
                                shape = SegmentedButtonDefaults.itemShape(index, meetingOptions.size)
                            ) { Text(option) }
                        }
                    }

                    // Hourly Rate
                    Text("Hourly Rate (USD)", style = MaterialTheme.typography.labelLarge)
                    OutlinedTextField(
                        value = hourlyRateText,
                        onValueChange = { hourlyRateText = it },
                        label = { Text("e.g. 50.00") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Rate Range
                    Text("Rate Range (USD)", style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = coachRateLowerText,
                            onValueChange = { coachRateLowerText = it },
                            label = { Text("Lower") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = coachRateUpperText,
                            onValueChange = { coachRateUpperText = it },
                            label = { Text("Upper") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    // Zip Code
                    Text("Zip Code", style = MaterialTheme.typography.labelLarge)
                    OutlinedTextField(
                        value = coachZipCode,
                        onValueChange = { coachZipCode = it },
                        label = { Text("e.g. 55401") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (coachCity.isNotBlank()) {
                        Text("City: $coachCity", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }

                    // Bio
                    Text("Biography", style = MaterialTheme.typography.labelLarge)
                    OutlinedTextField(
                        value = coachBio,
                        onValueChange = { coachBio = it },
                        minLines = 4,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Tournament Software Link
                    Text("Tournamentsoftware Profile Link", style = MaterialTheme.typography.labelLarge)
                    OutlinedTextField(
                        value = coachTournamentLink,
                        onValueChange = { coachTournamentLink = it },
                        label = { Text("https://...") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    // === CLIENT SECTION ===
                    Text("Client Details", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

                    // Goals
                    Text("Goals", style = MaterialTheme.typography.labelLarge)
                    if (uiState.subjectIds.isNotEmpty()) {
                        ChipMultiSelect(
                            items = uiState.subjectIds,
                            selection = selectedGoals,
                            onSelectionChange = { selectedGoals = it }
                        )
                    }

                    // Availability
                    Text("Preferred Availability", style = MaterialTheme.typography.labelLarge)
                    ChipMultiSelect(
                        items = availabilityOptions,
                        selection = selectedClientAvailability,
                        onSelectionChange = { selectedClientAvailability = it }
                    )

                    // Meeting Preference
                    Text("Meeting Preference", style = MaterialTheme.typography.labelLarge)
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        meetingOptions.forEachIndexed { index, option ->
                            SegmentedButton(
                                selected = clientMeetingPreference == option,
                                onClick = { clientMeetingPreference = option },
                                shape = SegmentedButtonDefaults.itemShape(index, meetingOptions.size)
                            ) { Text(option) }
                        }
                    }

                    // Skill Level
                    Text("Skill Level", style = MaterialTheme.typography.labelLarge)
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        skillLevelOptions.forEachIndexed { index, option ->
                            SegmentedButton(
                                selected = clientSkillLevel == option,
                                onClick = { clientSkillLevel = option },
                                shape = SegmentedButtonDefaults.itemShape(index, skillLevelOptions.size)
                            ) { Text(option) }
                        }
                    }

                    // Zip Code
                    Text("Zip Code", style = MaterialTheme.typography.labelLarge)
                    OutlinedTextField(
                        value = clientZipCode,
                        onValueChange = { clientZipCode = it },
                        label = { Text("e.g. 55401") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (clientCity.isNotBlank()) {
                        Text("City: $clientCity", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }

                    // Bio
                    Text("Biography", style = MaterialTheme.typography.labelLarge)
                    OutlinedTextField(
                        value = clientBio,
                        onValueChange = { clientBio = it },
                        minLines = 4,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Tournament Software Link
                    Text("Tournamentsoftware Profile Link", style = MaterialTheme.typography.labelLarge)
                    OutlinedTextField(
                        value = clientTournamentLink,
                        onValueChange = { clientTournamentLink = it },
                        label = { Text("https://...") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                HorizontalDivider()

                // Tournament Partner button
                OutlinedButton(
                    onClick = onTournamentPartner,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.SportsTennis, contentDescription = null, tint = LogoBlue)
                    Spacer(Modifier.width(8.dp))
                    Text("Find a Tournament Partner", color = LogoBlue)
                }

                HorizontalDivider()

                // Save button
                Button(
                    onClick = {
                        if (isCoach) {
                            val lower = coachRateLowerText.toDoubleOrNull()
                            val upper = coachRateUpperText.toDoubleOrNull()
                            val rateRange = when {
                                lower != null && upper != null -> listOf(lower, upper)
                                lower != null -> listOf(lower)
                                upper != null -> listOf(upper)
                                else -> null
                            }
                            viewModel.saveCoachProfile(
                                name = name,
                                specialties = selectedSpecialties.toList(),
                                availability = selectedCoachAvailability.toList(),
                                experienceYears = experienceYears,
                                hourlyRate = hourlyRateText.toDoubleOrNull(),
                                meetingPreference = coachMeetingPreference,
                                bio = coachBio,
                                zipCode = coachZipCode.ifBlank { null },
                                city = coachCity.ifBlank { null },
                                rateRange = rateRange,
                                tournamentSoftwareLink = coachTournamentLink.trim().ifBlank { null },
                                imageBytes = selectedImageBytes
                            )
                        } else {
                            viewModel.saveClientProfile(
                                name = name,
                                goals = selectedGoals.toList(),
                                preferredAvailability = selectedClientAvailability.toList(),
                                meetingPreference = clientMeetingPreference,
                                skillLevel = clientSkillLevel,
                                zipCode = clientZipCode.ifBlank { null },
                                city = clientCity.ifBlank { null },
                                bio = clientBio.ifBlank { null },
                                tournamentSoftwareLink = clientTournamentLink.trim().ifBlank { null },
                                imageBytes = selectedImageBytes
                            )
                        }
                    },
                    enabled = !uiState.isSaving,
                    colors = ButtonDefaults.buttonColors(containerColor = LogoGreen),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                ) {
                    if (uiState.isSaving) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                    }
                    Text("Save Profile", fontWeight = FontWeight.SemiBold)
                }

                Spacer(Modifier.height(32.dp))
            }
        }
    }
}
