package com.athletebridge.android.ui.profile

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.athletebridge.android.data.model.Coach
import com.athletebridge.android.data.model.Client
import com.athletebridge.android.data.repository.AuthRepository
import com.athletebridge.android.data.repository.CoachRepository
import com.athletebridge.android.data.repository.ClientRepository
import com.athletebridge.android.data.repository.PhotoRepository
import com.athletebridge.android.data.repository.SubjectRepository
import com.google.firebase.FirebaseException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import com.google.firebase.storage.FirebaseStorage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit
import javax.inject.Inject

enum class PhoneVerificationStep { IDLE, SENDING, CODE_SENT, VERIFYING, DONE, ERROR }

data class ProfileUiState(
    val isSaving: Boolean = false,
    val isUploadingPhoto: Boolean = false,
    val saveMessage: String? = null,
    val saveSuccess: Boolean = false,
    val subjectIds: List<String> = emptyList(),
    val currentCoach: Coach? = null,
    val currentClient: Client? = null,
    val userType: String? = null,
    val userEmail: String? = null,
    val isLoading: Boolean = true,
    val phoneVerificationStep: PhoneVerificationStep = PhoneVerificationStep.IDLE,
    val verificationId: String? = null,
    val phoneVerifyError: String? = null,
    val isDeleting: Boolean = false,
    val deleteError: String? = null,
    val accountDeleted: Boolean = false
)

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val coachRepository: CoachRepository,
    private val clientRepository: ClientRepository,
    private val photoRepository: PhotoRepository,
    private val subjectRepository: SubjectRepository,
    private val auth: FirebaseAuth,
    private val storage: FirebaseStorage
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
        imageBytes: ByteArray?,
        cancellationWindowHours: Int = 0
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
                payments = _uiState.value.currentCoach?.payments,
                phoneVerified = _uiState.value.currentCoach?.phoneVerified ?: false,
                phoneNumber = _uiState.value.currentCoach?.phoneNumber,
                cancellationWindowHours = cancellationWindowHours
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
                tournamentSoftwareLink = tournamentSoftwareLink,
                phoneVerified = _uiState.value.currentClient?.phoneVerified ?: false,
                phoneNumber = _uiState.value.currentClient?.phoneNumber
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

    // ── Photo Upload ──────────────────────────────────────────────────────────

    fun uploadProfilePhoto(uri: Uri) {
        val uid = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isUploadingPhoto = true, saveMessage = null)
            try {
                val ref = storage.reference.child("profile_photos/$uid.jpg")
                ref.putFile(uri).await()
                val downloadUrl = ref.downloadUrl.await().toString()
                coachRepository.savePhotoUrl(uid, downloadUrl)
                val updated = coachRepository.fetchCoach(uid)
                _uiState.value = _uiState.value.copy(
                    isUploadingPhoto = false,
                    currentCoach = updated,
                    saveMessage = "Profile photo updated"
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isUploadingPhoto = false,
                    saveMessage = "Photo upload failed: ${e.message}"
                )
            }
        }
    }

    // ── Phone Verification ─────────────────────────────────────────────────────

    fun sendPhoneVerification(phoneNumber: String, activity: Activity) {
        _uiState.value = _uiState.value.copy(
            phoneVerificationStep = PhoneVerificationStep.SENDING,
            phoneVerifyError = null
        )

        val callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
            override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                // Auto-verified (rare, device-level verification)
                viewModelScope.launch { applyCredential(credential, phoneNumber) }
            }

            override fun onVerificationFailed(e: FirebaseException) {
                _uiState.value = _uiState.value.copy(
                    phoneVerificationStep = PhoneVerificationStep.ERROR,
                    phoneVerifyError = e.message ?: "Verification failed"
                )
            }

            override fun onCodeSent(
                verificationId: String,
                token: PhoneAuthProvider.ForceResendingToken
            ) {
                _uiState.value = _uiState.value.copy(
                    phoneVerificationStep = PhoneVerificationStep.CODE_SENT,
                    verificationId = verificationId
                )
            }
        }

        val options = PhoneAuthOptions.newBuilder(auth)
            .setPhoneNumber(phoneNumber)
            .setTimeout(60L, TimeUnit.SECONDS)
            .setActivity(activity)
            .setCallbacks(callbacks)
            .build()

        PhoneAuthProvider.verifyPhoneNumber(options)
    }

    fun confirmPhoneCode(code: String, phoneNumber: String) {
        val vId = _uiState.value.verificationId ?: return
        _uiState.value = _uiState.value.copy(phoneVerificationStep = PhoneVerificationStep.VERIFYING)
        val credential = PhoneAuthProvider.getCredential(vId, code)
        viewModelScope.launch { applyCredential(credential, phoneNumber) }
    }

    private suspend fun applyCredential(credential: PhoneAuthCredential, phoneNumber: String) {
        try {
            auth.currentUser?.updatePhoneNumber(credential)?.await()
            markPhoneVerified(phoneNumber)
        } catch (e: Exception) {
            _uiState.value = _uiState.value.copy(
                phoneVerificationStep = PhoneVerificationStep.ERROR,
                phoneVerifyError = e.message ?: "Code verification failed"
            )
        }
    }

    private suspend fun markPhoneVerified(phoneNumber: String) {
        val uid = auth.currentUser?.uid ?: return
        val isCoach = _uiState.value.userType?.uppercase() == "COACH"
        if (isCoach) {
            coachRepository.setPhoneVerified(uid, phoneNumber)
            val updated = coachRepository.fetchCoach(uid)
            _uiState.value = _uiState.value.copy(
                phoneVerificationStep = PhoneVerificationStep.DONE,
                currentCoach = updated
            )
        } else {
            clientRepository.setPhoneVerified(uid, phoneNumber)
            val updated = clientRepository.fetchClient(uid)
            _uiState.value = _uiState.value.copy(
                phoneVerificationStep = PhoneVerificationStep.DONE,
                currentClient = updated
            )
        }
    }

    fun resetPhoneVerification() {
        _uiState.value = _uiState.value.copy(
            phoneVerificationStep = PhoneVerificationStep.IDLE,
            verificationId = null,
            phoneVerifyError = null
        )
    }

    // ── Other ─────────────────────────────────────────────────────────────────

    fun clearSaveMessage() {
        _uiState.value = _uiState.value.copy(saveMessage = null, saveSuccess = false)
    }

    fun logout() {
        authRepository.logout()
    }

    fun deleteAccount() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isDeleting = true, deleteError = null)
            val result = authRepository.deleteAccount()
            result.onSuccess {
                _uiState.value = _uiState.value.copy(isDeleting = false, accountDeleted = true)
            }.onFailure { e ->
                _uiState.value = _uiState.value.copy(isDeleting = false, deleteError = e.message)
            }
        }
    }
}
