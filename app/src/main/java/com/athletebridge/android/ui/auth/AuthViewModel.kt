package com.athletebridge.android.ui.auth

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.athletebridge.android.data.model.Coach
import com.athletebridge.android.data.model.Client
import com.athletebridge.android.data.repository.AuthRepository
import com.athletebridge.android.data.repository.CoachRepository
import com.athletebridge.android.data.repository.ClientRepository
import com.athletebridge.android.data.repository.NotificationRepository
import com.athletebridge.android.service.NotificationService
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.tasks.await
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AuthUiState(
    val user: FirebaseUser? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val userType: String? = null,
    val userTypeLoaded: Boolean = false,
    val currentCoach: Coach? = null,
    val currentClient: Client? = null,
    val resetEmailSentTo: String? = null
)

@HiltViewModel
class AuthViewModel @Inject constructor(
    application: Application,
    private val authRepository: AuthRepository,
    private val coachRepository: CoachRepository,
    private val clientRepository: ClientRepository,
    private val notificationRepository: NotificationRepository
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(AuthUiState(user = authRepository.currentUser))
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            authRepository.observeAuthState().collect { user ->
                _uiState.value = _uiState.value.copy(user = user)
                if (user != null) {
                    loadUserProfile(user.uid)
                    authRepository.updateLastSeen()
                } else {
                    _uiState.value = _uiState.value.copy(
                        userType = null,
                        userTypeLoaded = false,
                        currentCoach = null,
                        currentClient = null
                    )
                }
            }
        }
    }

    fun signUp(email: String, password: String, userType: String, name: String = "") {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            val result = authRepository.signUp(email.trim(), password.trim(), userType, name.trim())
            result.fold(
                onSuccess = {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        userType = userType
                    )
                },
                onFailure = { e ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = mapAuthError(e)
                    )
                }
            )
        }
    }

    fun login(email: String, password: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            val result = authRepository.login(email.trim(), password.trim())
            result.fold(
                onSuccess = {
                    _uiState.value = _uiState.value.copy(isLoading = false)
                },
                onFailure = { e ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = mapAuthError(e)
                    )
                }
            )
        }
    }

    fun logout() {
        authRepository.logout()
    }

    fun sendPasswordReset(email: String) {
        val trimmed = email.trim()
        if (!trimmed.contains("@") || !trimmed.contains(".")) {
            _uiState.value = _uiState.value.copy(
                errorMessage = "Enter your email above, then tap Forgot your password."
            )
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            authRepository.sendPasswordReset(trimmed).fold(
                onSuccess = {
                    _uiState.value = _uiState.value.copy(isLoading = false, resetEmailSentTo = trimmed)
                },
                onFailure = { e ->
                    _uiState.value = _uiState.value.copy(isLoading = false, errorMessage = mapAuthError(e))
                }
            )
        }
    }

    fun clearResetEmailSent() {
        _uiState.value = _uiState.value.copy(resetEmailSentTo = null)
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    private suspend fun loadUserProfile(uid: String) {
        val type = authRepository.fetchUserType(uid)
        _uiState.value = _uiState.value.copy(
            userType = type,
            userTypeLoaded = true
        )

        if (type?.uppercase() == "COACH") {
            val coach = coachRepository.fetchCoach(uid)
            _uiState.value = _uiState.value.copy(currentCoach = coach)
        } else {
            val client = clientRepository.fetchClient(uid)
            _uiState.value = _uiState.value.copy(currentClient = client)
        }

        // Register FCM token so this device can receive push notifications.
        // We try two sources: (1) the live token from FirebaseMessaging, and
        // (2) the token stashed in SharedPreferences by onNewToken() if it fired
        // before the user logged in (race condition at first install).
        Log.d(FCM_TAG, "loadUserProfile: fetching live FCM token for uid=$uid")
        try {
            val liveToken = FirebaseMessaging.getInstance().token.await()
            Log.d(FCM_TAG, "loadUserProfile: got live token=${liveToken.take(20)}...")
            // Persist locally (idempotent) and save to Firestore
            NotificationService.savePendingToken(getApplication(), liveToken)
            NotificationService.saveTokenToFirestore(uid, liveToken)
            NotificationService.clearPendingToken(getApplication())
        } catch (e: Exception) {
            Log.e(FCM_TAG, "loadUserProfile: live token fetch FAILED", e)
            // Live token fetch failed — fall back to any token we stored earlier
            val pending = NotificationService.getPendingToken(getApplication())
            Log.d(FCM_TAG, "loadUserProfile: pending token in prefs=${if (pending != null) pending.take(20) + "..." else "null"}")
            if (pending != null) {
                NotificationService.saveTokenToFirestore(uid, pending)
                NotificationService.clearPendingToken(getApplication())
            } else {
                Log.w(FCM_TAG, "loadUserProfile: no token available to save — this device will NOT receive push notifications!")
            }
        }
    }

    fun refreshProfile() {
        val uid = _uiState.value.user?.uid ?: return
        viewModelScope.launch {
            loadUserProfile(uid)
        }
    }

    val isCoach: Boolean
        get() = _uiState.value.userType?.uppercase() == "COACH"

    companion object {
        private const val FCM_TAG = "FCM_DEBUG"
    }

    private fun mapAuthError(e: Throwable): String {
        val message = e.message ?: return "Authentication failed. Please try again."
        return when {
            "INVALID_EMAIL" in message || "invalid email" in message.lowercase() ->
                "Invalid email address."
            "EMAIL_ALREADY_IN_USE" in message || "email address is already in use" in message.lowercase() ->
                "This email is already in use."
            "WEAK_PASSWORD" in message || "password is too weak" in message.lowercase() ->
                "Password is too weak. Try again."
            "WRONG_PASSWORD" in message || "password is invalid" in message.lowercase() ->
                "Invalid password. Please try again."
            "USER_NOT_FOUND" in message || "no user record" in message.lowercase() ->
                "No user found for that email."
            "USER_DISABLED" in message ->
                "This user account has been disabled."
            "NETWORK" in message.uppercase() ->
                "Network error. Please try again."
            "INVALID_LOGIN_CREDENTIALS" in message.uppercase() || "invalid credential" in message.lowercase() ->
                "Invalid credentials. Please try again."
            else -> "Authentication failed. Please try again."
        }
    }
}
