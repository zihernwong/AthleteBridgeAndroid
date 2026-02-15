package com.athletebridge.android.ui.profile

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.athletebridge.android.data.model.Coach
import com.athletebridge.android.data.model.Client
import com.athletebridge.android.data.repository.AuthRepository
import com.athletebridge.android.data.repository.CoachRepository
import com.athletebridge.android.data.repository.ClientRepository
import com.athletebridge.android.data.repository.PhotoRepository
import com.athletebridge.android.data.repository.SubjectRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import javax.inject.Inject

data class ProfileUiState(
    val isSaving: Boolean = false,
    val saveMessage: String? = null,
    val saveSuccess: Boolean = false,
    val subjectIds: List<String> = emptyList(),
    val currentCoach: Coach? = null,
    val currentClient: Client? = null,
    val userType: String? = null,
    val userEmail: String? = null,
    val isLoading: Boolean = true
)

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val coachRepository: CoachRepository,
    private val clientRepository: ClientRepository,
    private val photoRepository: PhotoRepository,
    private val subjectRepository: SubjectRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    init {
        loadProfile()
    }

    fun loadProfile() {
        viewModelScope.launch {
            val uid = authRepository.currentUser?.uid ?: return@launch
            _uiState.value = _uiState.value.copy(
                isLoading = true,
                userEmail = authRepository.currentUser?.email
            )

            var userType = authRepository.fetchUserType(uid)
            val subjects = subjectRepository.fetchSubjectIds()

            // If userType is null, detect from existing profile data
            if (userType == null) {
                val coach = coachRepository.fetchCoach(uid)
                if (coach != null) {
                    userType = "COACH"
                } else {
                    val client = clientRepository.fetchClient(uid)
                    if (client != null) {
                        userType = "CLIENT"
                    }
                }
            }

            if (userType?.uppercase() == "COACH") {
                val coach = coachRepository.fetchCoach(uid)
                _uiState.value = _uiState.value.copy(
                    userType = userType,
                    currentCoach = coach,
                    subjectIds = subjects,
                    isLoading = false
                )
            } else {
                val client = clientRepository.fetchClient(uid)
                _uiState.value = _uiState.value.copy(
                    userType = userType,
                    currentClient = client,
                    subjectIds = subjects,
                    isLoading = false
                )
            }
        }
    }

    fun saveCoachProfile(
        name: String,
        specialties: List<String>,
        availability: List<String>,
        experienceYears: Int,
        hourlyRate: Double?,
        meetingPreference: String,
        bio: String,
        zipCode: String?,
        city: String?,
        rateRange: List<Double>?,
        tournamentSoftwareLink: String?,
        imageBytes: ByteArray?
    ) {
        val uid = authRepository.currentUser?.uid ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true, saveMessage = null, saveSuccess = false)

            var photoURL = _uiState.value.currentCoach?.photoURLString
            if (imageBytes != null) {
                val uploadResult = photoRepository.uploadProfileImage(uid, imageBytes)
                uploadResult.fold(
                    onSuccess = { photoURL = it },
                    onFailure = {
                        _uiState.value = _uiState.value.copy(
                            isSaving = false,
                            saveMessage = "Image upload failed: ${it.message}"
                        )
                        return@launch
                    }
                )
            }

            val coach = Coach(
                id = uid,
                name = name.ifBlank { "Unnamed" },
                specialties = specialties,
                availability = availability,
                experienceYears = experienceYears,
                hourlyRate = hourlyRate,
                meetingPreference = meetingPreference,
                bio = bio.ifBlank { null },
                zipCode = zipCode,
                city = city,
                rateRange = rateRange,
                tournamentSoftwareLink = tournamentSoftwareLink,
                photoURLString = photoURL,
                subscriptionTier = _uiState.value.currentCoach?.subscriptionTier ?: com.athletebridge.android.data.model.CoachTier.FREE,
                payments = _uiState.value.currentCoach?.payments
            )

            val result = coachRepository.saveCoach(coach)
            result.fold(
                onSuccess = {
                    val updated = coachRepository.fetchCoach(uid)
                    _uiState.value = _uiState.value.copy(
                        isSaving = false,
                        saveMessage = "Coach profile saved",
                        saveSuccess = true,
                        currentCoach = updated
                    )
                },
                onFailure = {
                    _uiState.value = _uiState.value.copy(
                        isSaving = false,
                        saveMessage = "Error: ${it.message}"
                    )
                }
            )
        }
    }

    fun saveClientProfile(
        name: String,
        goals: List<String>,
        preferredAvailability: List<String>,
        meetingPreference: String,
        skillLevel: String,
        zipCode: String?,
        city: String?,
        bio: String?,
        tournamentSoftwareLink: String?,
        imageBytes: ByteArray?
    ) {
        val uid = authRepository.currentUser?.uid ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true, saveMessage = null, saveSuccess = false)

            if (imageBytes != null) {
                val uploadResult = photoRepository.uploadProfileImage(uid, imageBytes)
                uploadResult.onFailure {
                    _uiState.value = _uiState.value.copy(
                        isSaving = false,
                        saveMessage = "Image upload failed: ${it.message}"
                    )
                    return@launch
                }
            }

            val client = Client(
                id = uid,
                name = name.ifBlank { "Unnamed" },
                goals = goals,
                preferredAvailability = preferredAvailability,
                meetingPreference = meetingPreference,
                skillLevel = skillLevel,
                zipCode = zipCode,
                city = city,
                bio = bio,
                tournamentSoftwareLink = tournamentSoftwareLink
            )

            val result = clientRepository.saveClient(client)
            result.fold(
                onSuccess = {
                    val updated = clientRepository.fetchClient(uid)
                    _uiState.value = _uiState.value.copy(
                        isSaving = false,
                        saveMessage = "Client profile saved",
                        saveSuccess = true,
                        currentClient = updated
                    )
                },
                onFailure = {
                    _uiState.value = _uiState.value.copy(
                        isSaving = false,
                        saveMessage = "Error: ${it.message}"
                    )
                }
            )
        }
    }

    fun clearSaveMessage() {
        _uiState.value = _uiState.value.copy(saveMessage = null, saveSuccess = false)
    }

    fun logout() {
        authRepository.logout()
    }
}
