package com.athletebridge.android.ui.auth

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.athletebridge.android.ui.theme.LogoBlue
import com.athletebridge.android.ui.theme.LogoGreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthScreen(
    viewModel: AuthViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var fullName by remember { mutableStateOf("") }
    var isLogin by remember { mutableStateOf(true) }
    var selectedRole by remember { mutableStateOf("CLIENT") }
    val focusManager = LocalFocusManager.current

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 120.dp),
            verticalArrangement = Arrangement.Bottom,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // App title
            Text(
                text = "AthleteBridge",
                fontSize = 40.sp,
                fontWeight = FontWeight.Bold,
                color = LogoBlue,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            // Login / Sign Up title
            Text(
                text = if (isLogin) "Login" else "Create An Account",
                style = MaterialTheme.typography.headlineLarge,
                modifier = Modifier.padding(bottom = 20.dp)
            )

            // Role picker + name (sign up only)
            AnimatedVisibility(visible = !isLogin) {
                Column(modifier = Modifier.padding(bottom = 16.dp)) {
                    SingleChoiceSegmentedButtonRow(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        SegmentedButton(
                            selected = selectedRole == "CLIENT",
                            onClick = { selectedRole = "CLIENT" },
                            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                        ) {
                            Text("Client")
                        }
                        SegmentedButton(
                            selected = selectedRole == "COACH",
                            onClick = { selectedRole = "COACH" },
                            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                        ) {
                            Text("Coach")
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = fullName,
                        onValueChange = { fullName = it },
                        label = { Text("Full Name") },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Text,
                            imeAction = ImeAction.Next
                        ),
                        singleLine = true,
                        enabled = !uiState.isLoading,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // Email field
            OutlinedTextField(
                value = email,
                onValueChange = {
                    email = it
                    viewModel.clearError()
                },
                label = { Text("Email") },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Next
                ),
                singleLine = true,
                enabled = !uiState.isLoading,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Password field
            OutlinedTextField(
                value = password,
                onValueChange = {
                    password = it
                    viewModel.clearError()
                },
                label = { Text("Password") },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = { focusManager.clearFocus() }
                ),
                singleLine = true,
                enabled = !uiState.isLoading,
                modifier = Modifier.fillMaxWidth()
            )

            // Error message
            uiState.errorMessage?.let { error ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = error,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Submit button
            Button(
                onClick = {
                    focusManager.clearFocus()
                    val trimmedEmail = email.trim()
                    val trimmedPassword = password.trim()
                    if (trimmedEmail.isEmpty() || trimmedPassword.isEmpty()) {
                        return@Button
                    }
                    if (isLogin) {
                        viewModel.login(trimmedEmail, trimmedPassword)
                    } else {
                        viewModel.signUp(trimmedEmail, trimmedPassword, selectedRole, fullName.trim())
                    }
                },
                enabled = !uiState.isLoading && email.isNotBlank() && password.isNotBlank() &&
                        (isLogin || fullName.isNotBlank()),
                colors = ButtonDefaults.buttonColors(containerColor = LogoBlue),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                if (uiState.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(
                    text = when {
                        uiState.isLoading && isLogin -> "Logging in..."
                        uiState.isLoading -> "Signing up..."
                        isLogin -> "Login"
                        else -> "Sign Up"
                    },
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Toggle login/signup
            TextButton(
                onClick = {
                    isLogin = !isLogin
                    viewModel.clearError()
                }
            ) {
                Text(
                    text = if (isLogin) "Need an account? Sign up" else "Have an account? Login",
                    color = LogoBlue
                )
            }

            // Terms and Privacy notice shown only during sign-up
            if (!isLogin) {
                Text(
                    "By creating an account you agree to our Terms of Service and Privacy Policy.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp)
                )
            }
        }

        // Loading overlay
        if (uiState.isLoading) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = Color.Black.copy(alpha = 0.25f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        shadowElevation = 6.dp
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("Validating credentials...")
                        }
                    }
                }
            }
        }
    }
}
