package com.athletebridge.android.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.athletebridge.android.data.model.Coach
import com.athletebridge.android.data.model.Client
import com.athletebridge.android.data.repository.AuthRepository
import com.athletebridge.android.data.repository.CoachRepository
import com.athletebridge.android.data.repository.ClientRepository
import com.google.firebase.auth.FirebaseUser
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
    val currentClient: Client? = null
)

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val coachRepository: CoachRepository,
    private val clientRepository: ClientRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AuthUiState(user = authRepository.currentUser))
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            authRepository.observeAuthState().collect { user ->
                _uiState.value = _uiState.value.copy(user = user)
                if (user != null) {
                    loadUserProfile(user.uid)
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

    fun signUp(email: String, password: String, userType: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            val result = authRepository.signUp(email.trim(), password.trim(), userType)
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
    }

    fun refreshProfile() {
        val uid = _uiState.value.user?.uid ?: return
        viewModelScope.launch {
            loadUserProfile(uid)
        }
    }

    val isCoach: Boolean
        get() = _uiState.value.userType?.uppercase() == "COACH"

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
