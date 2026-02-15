package com.athletebridge.android.ui.reviews

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.athletebridge.android.data.model.Coach
import com.athletebridge.android.data.model.ReviewItem
import com.athletebridge.android.ui.theme.LogoGreen
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewsScreen(
    isCoach: Boolean,
    viewModel: ReviewsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(isCoach) {
        viewModel.loadReviews(isCoach)
    }

    var showSubmitDialog by remember { mutableStateOf(false) }

    // Handle submit success
    LaunchedEffect(uiState.submitSuccess) {
        if (uiState.submitSuccess) {
            showSubmitDialog = false
            viewModel.clearSubmitState()
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Reviews") }) },
        floatingActionButton = {
            if (!isCoach) {
                FloatingActionButton(
                    onClick = { showSubmitDialog = true },
                    containerColor = LogoGreen
                ) {
                    Icon(Icons.Default.Star, contentDescription = "Write Review", tint = Color.White)
                }
            }
        }
    ) { padding ->
        if (uiState.isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (uiState.reviews.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("No reviews yet", style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (isCoach) "Reviews about you will appear here"
                        else "Tap the star button to write a review",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (isCoach) {
                    item {
                        val avg = uiState.reviews.map { it.rating }.average()
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Average Rating", style = MaterialTheme.typography.labelLarge)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        String.format("%.1f", avg),
                                        style = MaterialTheme.typography.headlineLarge,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    StarRow(rating = avg.toInt())
                                }
                                Text("${uiState.reviews.size} review${if (uiState.reviews.size != 1) "s" else ""}", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }

                items(uiState.reviews, key = { it.id }) { review ->
                    ReviewCard(review = review)
                }
            }
        }
    }

    // Submit Review Dialog
    if (showSubmitDialog) {
        SubmitReviewDialog(
            coaches = uiState.coaches,
            isSubmitting = uiState.isSubmitting,
            error = uiState.submitError,
            onDismiss = {
                showSubmitDialog = false
                viewModel.clearSubmitState()
            },
            onSubmit = { coachId, coachName, rating, comment ->
                viewModel.submitReview(coachId, coachName, rating, comment)
            }
        )
    }
}

@Composable
fun SubmitReviewDialog(
    coaches: List<Coach>,
    isSubmitting: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onSubmit: (String, String, Int, String) -> Unit
) {
    var coachSearchText by remember { mutableStateOf("") }
    var selectedCoach by remember { mutableStateOf<Coach?>(null) }
    var rating by remember { mutableIntStateOf(0) }
    var comment by remember { mutableStateOf("") }

    val filteredCoaches = if (coachSearchText.length >= 1) {
        coaches.filter { it.name.contains(coachSearchText, ignoreCase = true) }.take(8)
    } else emptyList()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Write a Review") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // Coach search
                if (selectedCoach == null) {
                    OutlinedTextField(
                        value = coachSearchText,
                        onValueChange = { coachSearchText = it },
                        label = { Text("Search for a coach") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    filteredCoaches.forEach { coach ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedCoach = coach }
                        ) {
                            Text(
                                coach.name,
                                modifier = Modifier.padding(12.dp),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                } else {
                    Card(Modifier.fillMaxWidth()) {
                        Row(
                            Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(selectedCoach!!.name, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                            TextButton(onClick = { selectedCoach = null; coachSearchText = "" }) {
                                Text("Change")
                            }
                        }
                    }
                }

                // Rating
                Text("Rating", style = MaterialTheme.typography.labelMedium)
                Row {
                    (1..5).forEach { star ->
                        IconButton(onClick = { rating = star }) {
                            Icon(
                                if (star <= rating) Icons.Default.Star else Icons.Default.StarBorder,
                                contentDescription = "Star $star",
                                tint = LogoGreen,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }
                }

                // Comment
                OutlinedTextField(
                    value = comment,
                    onValueChange = { comment = it },
                    label = { Text("Your review") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    maxLines = 5
                )

                error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    selectedCoach?.let { coach ->
                        onSubmit(coach.id, coach.name, rating, comment)
                    }
                },
                enabled = selectedCoach != null && rating > 0 && comment.isNotBlank() && !isSubmitting
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp))
                } else {
                    Text("Submit")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun StarRow(rating: Int, maxStars: Int = 5) {
    Row {
        repeat(maxStars) { index ->
            Icon(
                if (index < rating) Icons.Default.Star else Icons.Default.StarBorder,
                contentDescription = null,
                tint = LogoGreen,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
fun ReviewCard(review: ReviewItem) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(review.reviewerName, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                StarRow(rating = review.rating)
            }
            if (review.comment.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(review.comment, style = MaterialTheme.typography.bodyMedium)
            }
            review.createdAt?.let { date ->
                Spacer(Modifier.height(4.dp))
                Text(
                    SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(date),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
