package com.athletebridge.android.ui.profile

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
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
import android.app.Activity
import androidx.compose.foundation.BorderStroke
import com.athletebridge.android.data.model.CoachTier
import com.athletebridge.android.data.repository.BillingRepository
import com.athletebridge.android.ui.payments.PaymentsViewModel
import com.athletebridge.android.ui.theme.LogoBlue
import com.athletebridge.android.ui.theme.LogoGreen
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    onLogout: () -> Unit = {},
    viewModel: ProfileViewModel = hiltViewModel(),
    paymentsViewModel: PaymentsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val paymentsState by paymentsViewModel.uiState.collectAsState()
    val context = LocalContext.current
    // LocalContext may be a ContextThemeWrapper rather than the Activity itself —
    // unwrap the chain so the Play billing flow always gets a real Activity.
    val activity = remember(context) {
        var ctx: android.content.Context = context
        while (ctx is android.content.ContextWrapper) {
            if (ctx is Activity) return@remember ctx
            ctx = ctx.baseContext
        }
        null
    }
    val isCoach = uiState.userType?.uppercase() == "COACH"
    val isEditMode = if (isCoach) uiState.currentCoach != null else uiState.currentClient != null
    val isPhoneVerified = if (isCoach) uiState.currentCoach?.phoneVerified == true
                          else uiState.currentClient?.phoneVerified == true

    var showManageSubscription by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    // Navigate away when account deletion succeeds
    LaunchedEffect(uiState.accountDeleted) {
        if (uiState.accountDeleted) onLogout()
    }

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
    var cancellationWindowText by remember { mutableStateOf("") }

    // Phone verification
    var phoneCountryCode by remember { mutableStateOf("+1") }
    var phoneNumberText by remember { mutableStateOf("") }
    var phoneCodeText by remember { mutableStateOf("") }
    var showCountryPicker by remember { mutableStateOf(false) }

    // Reset phone state when verification completes
    LaunchedEffect(uiState.phoneVerificationStep) {
        if (uiState.phoneVerificationStep == PhoneVerificationStep.DONE) {
            phoneNumberText = ""
            phoneCodeText = ""
        }
    }

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

    // Direct photo upload launcher (for coaches — uploads directly to Firebase Storage)
    val directPhotoUploadLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { viewModel.uploadProfilePhoto(it) }
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
            cancellationWindowText = if (coach.cancellationWindowHours > 0) coach.cancellationWindowHours.toString() else ""
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
                // Profile header
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val headerPhotoUrl = if (isCoach) uiState.currentCoach?.photoURLString else null
                        Box {
                            AvatarView(
                                url = if (selectedImageBytes != null) null else headerPhotoUrl,
                                name = name,
                                size = 72.dp,
                                phoneVerified = isPhoneVerified,
                                modifier = if (isCoach) Modifier.clickable {
                                    directPhotoUploadLauncher.launch("image/*")
                                } else Modifier
                            )
                            if (uiState.isUploadingPhoto) {
                                CircularProgressIndicator(
                                    modifier = Modifier
                                        .align(Alignment.Center)
                                        .size(32.dp),
                                    strokeWidth = 3.dp
                                )
                            } else if (isCoach) {
                                Icon(
                                    Icons.Default.CameraAlt,
                                    contentDescription = "Change photo",
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .size(20.dp),
                                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                )
                            }
                        }
                        Spacer(Modifier.width(16.dp))
                        Column {
                            Text(
                                name.ifBlank { if (isCoach) "Coach" else "Client" },
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            uiState.userEmail?.let {
                                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (isCoach) {
                                val tier = uiState.currentCoach?.subscriptionTier
                                if (tier != null) {
                                    Spacer(Modifier.height(4.dp))
                                    Surface(
                                        shape = MaterialTheme.shapes.small,
                                        color = when (tier.value) {
                                            "pro" -> LogoGreen.copy(alpha = 0.15f)
                                            "plus" -> LogoBlue.copy(alpha = 0.15f)
                                            else -> MaterialTheme.colorScheme.surfaceVariant
                                        }
                                    ) {
                                        Text(
                                            tier.value.replaceFirstChar { it.uppercase() },
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = when (tier.value) {
                                                "pro" -> LogoGreen
                                                "plus" -> LogoBlue
                                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                                            },
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

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

                // Subscription card (coach only)
                if (isCoach) {
                    val coachTier = uiState.currentCoach?.subscriptionTier ?: CoachTier.FREE
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Subscription",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f)
                            )
                            Surface(
                                shape = MaterialTheme.shapes.small,
                                color = subscriptionTierColor(coachTier).copy(alpha = 0.12f)
                            ) {
                                Text(
                                    coachTier.displayName,
                                    color = subscriptionTierColor(coachTier),
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            TextButton(onClick = { showManageSubscription = true }) {
                                Text("Manage")
                            }
                        }
                    }
                }

                // Phone Verification section
                PhoneVerificationSection(
                    isVerified = isPhoneVerified,
                    step = uiState.phoneVerificationStep,
                    error = uiState.phoneVerifyError,
                    countryCode = phoneCountryCode,
                    phoneNumber = phoneNumberText,
                    code = phoneCodeText,
                    showCountryPicker = showCountryPicker,
                    onCountryCodeClick = { showCountryPicker = true },
                    onCountryCodeSelected = { phoneCountryCode = it; showCountryPicker = false },
                    onDismissCountryPicker = { showCountryPicker = false },
                    onPhoneNumberChange = { phoneNumberText = it },
                    onCodeChange = { phoneCodeText = it },
                    onSendCode = {
                        if (activity != null) {
                            val fullNumber = "$phoneCountryCode${phoneNumberText.trimStart('0')}"
                            viewModel.sendPhoneVerification(fullNumber, activity)
                        }
                    },
                    onVerify = {
                        val fullNumber = "$phoneCountryCode${phoneNumberText.trimStart('0')}"
                        viewModel.confirmPhoneCode(phoneCodeText.trim(), fullNumber)
                    },
                    onReset = { viewModel.resetPhoneVerification() }
                )

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
                        size = 90.dp,
                        phoneVerified = isPhoneVerified
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

                    // Cancellation policy
                    Text("Cancellation Policy (hours)", style = MaterialTheme.typography.labelLarge)
                    OutlinedTextField(
                        value = cancellationWindowText,
                        onValueChange = { cancellationWindowText = it },
                        label = { Text("e.g. 24 — 0 disables the policy") },
                        supportingText = { Text("Clients cancelling within this many hours of a session forfeit their deposit") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

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
                                imageBytes = selectedImageBytes,
                                cancellationWindowHours = cancellationWindowText.toIntOrNull()?.coerceAtLeast(0) ?: 0
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

                // Delete account error
                uiState.deleteError?.let {
                    Text(
                        it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                OutlinedButton(
                    onClick = { showDeleteDialog = true },
                    enabled = !uiState.isDeleting,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (uiState.isDeleting) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Text("Delete Account")
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                TextButton(
                    onClick = {
                        val intent = android.content.Intent(
                            android.content.Intent.ACTION_VIEW,
                            android.net.Uri.parse("https://athletesbridge.com/privacy")
                        )
                        context.startActivity(intent)
                    },
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) {
                    Text(
                        "Privacy Policy",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                TextButton(
                    onClick = {
                        val intent = android.content.Intent(
                            android.content.Intent.ACTION_VIEW,
                            android.net.Uri.parse("https://athletesbridge.com/terms")
                        )
                        context.startActivity(intent)
                    },
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) {
                    Text(
                        "Terms of Service",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(Modifier.height(32.dp))
            }
        }
    }

    // Delete Account confirmation dialog
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Account") },
            text = {
                Text("This will permanently delete your account and all associated data. This action cannot be undone.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        viewModel.deleteAccount()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Manage Subscription bottom sheet
    if (showManageSubscription) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        val coachTier = uiState.currentCoach?.subscriptionTier ?: CoachTier.FREE
        val coachId = uiState.currentCoach?.id
        val scope = rememberCoroutineScope()

        // Load Play products when the sheet opens
        LaunchedEffect(Unit) {
            paymentsViewModel.loadPlayProducts()
        }

        // Watch for successful Play purchase and sync tier
        LaunchedEffect(coachId) {
            coachId?.let { paymentsViewModel.handlePlayPurchaseResult(it) }
        }

        // Show success snackbar on purchase completion (handled by purchaseSuccess flag)
        val purchaseSuccess = paymentsState.purchaseSuccess
        LaunchedEffect(purchaseSuccess) {
            if (purchaseSuccess) {
                paymentsViewModel.clearPurchaseSuccess()
                showManageSubscription = false
            }
        }

        ModalBottomSheet(
            onDismissRequest = {
                paymentsViewModel.clearMessages()
                showManageSubscription = false
            },
            sheetState = sheetState
        ) {
            ManageSubscriptionContent(
                currentTier = coachTier,
                isLoadingCheckout = paymentsState.isLoadingProducts,
                errorMessage = paymentsState.errorMessage,
                onSubscribePlus = {
                    if (activity != null) {
                        paymentsViewModel.subscribeTo(activity, BillingRepository.PRODUCT_PLUS)
                    }
                },
                onSubscribePro = {
                    if (activity != null) {
                        paymentsViewModel.subscribeTo(activity, BillingRepository.PRODUCT_PRO)
                    }
                },
                onManageBilling = {
                    val intent = android.content.Intent(
                        android.content.Intent.ACTION_VIEW,
                        android.net.Uri.parse("https://play.google.com/store/account/subscriptions")
                    )
                    context.startActivity(intent)
                }
            )
        }
    }
}

// ── Phone Verification ────────────────────────────────────────────────────────

private val commonCountryCodes = listOf(
    "+1" to "🇺🇸 United States (+1)",
    "+44" to "🇬🇧 United Kingdom (+44)",
    "+61" to "🇦🇺 Australia (+61)",
    "+81" to "🇯🇵 Japan (+81)",
    "+82" to "🇰🇷 South Korea (+82)",
    "+86" to "🇨🇳 China (+86)",
    "+33" to "🇫🇷 France (+33)",
    "+49" to "🇩🇪 Germany (+49)",
    "+39" to "🇮🇹 Italy (+39)",
    "+34" to "🇪🇸 Spain (+34)",
    "+55" to "🇧🇷 Brazil (+55)",
    "+52" to "🇲🇽 Mexico (+52)",
    "+91" to "🇮🇳 India (+91)",
    "+65" to "🇸🇬 Singapore (+65)",
    "+60" to "🇲🇾 Malaysia (+60)",
    "+64" to "🇳🇿 New Zealand (+64)"
)

@Composable
private fun PhoneVerificationSection(
    isVerified: Boolean,
    step: PhoneVerificationStep,
    error: String?,
    countryCode: String,
    phoneNumber: String,
    code: String,
    showCountryPicker: Boolean,
    onCountryCodeClick: () -> Unit,
    onCountryCodeSelected: (String) -> Unit,
    onDismissCountryPicker: () -> Unit,
    onPhoneNumberChange: (String) -> Unit,
    onCodeChange: (String) -> Unit,
    onSendCode: () -> Unit,
    onVerify: () -> Unit,
    onReset: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Phone Verification", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                if (isVerified) {
                    Surface(shape = MaterialTheme.shapes.extraSmall, color = LogoGreen.copy(alpha = 0.12f)) {
                        Row(
                            Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = LogoGreen, modifier = Modifier.size(14.dp))
                            Text("Verified", color = LogoGreen, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }

            when {
                isVerified -> {
                    Text(
                        "Your phone number has been verified.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                step == PhoneVerificationStep.CODE_SENT || step == PhoneVerificationStep.VERIFYING -> {
                    Text("Enter the 6-digit code sent to your phone.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedTextField(
                        value = code,
                        onValueChange = onCodeChange,
                        label = { Text("Verification Code") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = onReset, modifier = Modifier.weight(1f)) { Text("Resend") }
                        Button(
                            onClick = onVerify,
                            enabled = code.length == 6 && step != PhoneVerificationStep.VERIFYING,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = LogoGreen)
                        ) {
                            if (step == PhoneVerificationStep.VERIFYING) {
                                CircularProgressIndicator(Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                            } else {
                                Text("Verify")
                            }
                        }
                    }
                }

                else -> {
                    Text("Verify your phone number to show a verified badge on your profile.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = onCountryCodeClick) { Text(countryCode) }
                        OutlinedTextField(
                            value = phoneNumber,
                            onValueChange = onPhoneNumberChange,
                            label = { Text("Phone Number") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Button(
                        onClick = onSendCode,
                        enabled = phoneNumber.isNotBlank() && step != PhoneVerificationStep.SENDING,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = LogoGreen)
                    ) {
                        if (step == PhoneVerificationStep.SENDING) {
                            CircularProgressIndicator(Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                            Text("Sending…")
                        } else {
                            Text("Send Verification Code")
                        }
                    }
                }
            }
        }
    }

    // Country code picker dialog
    if (showCountryPicker) {
        AlertDialog(
            onDismissRequest = onDismissCountryPicker,
            title = { Text("Select Country Code") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    commonCountryCodes.forEach { (code, label) ->
                        TextButton(
                            onClick = { onCountryCodeSelected(code) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(label, modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = onDismissCountryPicker) { Text("Cancel") }
            }
        )
    }
}

// ── Subscription helpers ─────────────────────────────────────────────────────

private fun subscriptionTierColor(tier: CoachTier): androidx.compose.ui.graphics.Color = when (tier) {
    CoachTier.FREE -> androidx.compose.ui.graphics.Color.Gray
    CoachTier.PLUS -> LogoBlue
    CoachTier.PRO -> LogoGreen
}

@Composable
private fun ManageSubscriptionContent(
    currentTier: CoachTier,
    isLoadingCheckout: Boolean,
    errorMessage: String? = null,
    onSubscribePlus: () -> Unit,
    onSubscribePro: () -> Unit,
    onManageBilling: () -> Unit
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            "Subscription",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )

        errorMessage?.let {
            Surface(
                Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.errorContainer
            ) {
                Text(
                    it,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }

        Surface(
            Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.secondaryContainer
        ) {
            Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "Current Plan",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Text(
                    currentTier.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = subscriptionTierColor(currentTier)
                )
            }
        }

        SubscriptionTierCard(
            tier = CoachTier.FREE, currentTier = currentTier,
            features = listOf("Basic coaching profile"),
            isLoading = false, onSubscribe = null
        )
        SubscriptionTierCard(
            tier = CoachTier.PLUS, currentTier = currentTier,
            features = listOf("Revenue Insights"),
            isLoading = isLoadingCheckout,
            onSubscribe = if (currentTier != CoachTier.PLUS) onSubscribePlus else null
        )
        SubscriptionTierCard(
            tier = CoachTier.PRO, currentTier = currentTier,
            features = listOf("Revenue Insights", "Listed in Coach Search Engine"),
            isLoading = isLoadingCheckout,
            onSubscribe = if (currentTier != CoachTier.PRO) onSubscribePro else null
        )

        if (currentTier != CoachTier.FREE) {
            OutlinedButton(
                onClick = onManageBilling,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.CreditCard, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Manage Billing")
            }
        }

        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun SubscriptionTierCard(
    tier: CoachTier,
    currentTier: CoachTier,
    features: List<String>,
    isLoading: Boolean,
    onSubscribe: (() -> Unit)?
) {
    val isCurrent = tier == currentTier
    val color = subscriptionTierColor(tier)
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        border = if (isCurrent) BorderStroke(2.dp, color)
                 else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    tier.displayName,
                    fontWeight = FontWeight.Bold,
                    color = color,
                    modifier = Modifier.weight(1f)
                )
                if (isCurrent) {
                    Surface(shape = MaterialTheme.shapes.extraSmall, color = color.copy(alpha = 0.15f)) {
                        Text(
                            "Current", color = color, fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }
            }
            features.forEach { feature ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.CheckCircle, contentDescription = null,
                        tint = color, modifier = Modifier.size(14.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(feature, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (!isCurrent && tier != CoachTier.FREE && onSubscribe != null) {
                Spacer(Modifier.height(4.dp))
                Button(
                    onClick = onSubscribe,
                    enabled = !isLoading,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = color)
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            Modifier.size(16.dp), strokeWidth = 2.dp,
                            color = androidx.compose.ui.graphics.Color.White
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Starting checkout…")
                    } else {
                        Text("Subscribe to ${tier.displayName}")
                    }
                }
            }
        }
    }
}
