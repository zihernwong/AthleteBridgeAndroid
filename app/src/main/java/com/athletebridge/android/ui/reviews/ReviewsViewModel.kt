package com.athletebridge.android.ui.reviews

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.athletebridge.android.data.model.Coach
import com.athletebridge.android.data.model.ReviewItem
import com.athletebridge.android.data.repository.AuthRepository
import com.athletebridge.android.data.repository.CoachRepository
import com.athletebridge.android.data.repository.ReviewRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ReviewsUiState(
    val reviews: List<ReviewItem> = emptyList(),
    val isLoading: Boolean = true,
    val coaches: List<Coach> = emptyList(),
    val isSubmitting: Boolean = false,
    val submitSuccess: Boolean = false,
    val submitError: String? = null
)

@HiltViewModel
class ReviewsViewModel @Inject constructor(
    private val reviewRepository: ReviewRepository,
    private val coachRepository: CoachRepository,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ReviewsUiState())
    val uiState: StateFlow<ReviewsUiState> = _uiState.asStateFlow()

    fun loadReviews(isCoach: Boolean) {
        val uid = authRepository.currentUser?.uid ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val reviews = if (isCoach) {
                reviewRepository.fetchReviewsForCoach(uid)
            } else {
                reviewRepository.fetchReviewsByReviewer(uid)
            }
            // Also load coaches for the submit dialog
            val coaches = if (!isCoach) coachRepository.fetchAllCoaches() else emptyList()
            _uiState.value = ReviewsUiState(reviews = reviews, isLoading = false, coaches = coaches)
        }
    }

    fun submitReview(coachId: String, coachName: String, rating: Int, comment: String) {
        val uid = authRepository.currentUser?.uid ?: return
        val userName = authRepository.currentUser?.displayName ?: "Anonymous"

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSubmitting = true, submitError = null, submitSuccess = false)
            val review = ReviewItem(
                reviewerId = uid,
                reviewerName = userName,
                coachId = coachId,
                rating = rating,
                comment = comment
            )
            val result = reviewRepository.submitReview(review)
            result.onSuccess {
                _uiState.value = _uiState.value.copy(isSubmitting = false, submitSuccess = true)
                // Reload reviews
                loadReviews(false)
            }.onFailure { e ->
                _uiState.value = _uiState.value.copy(isSubmitting = false, submitError = e.message)
            }
        }
    }

    fun clearSubmitState() {
        _uiState.value = _uiState.value.copy(submitSuccess = false, submitError = null)
    }
}
