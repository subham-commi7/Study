package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockReset
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.auth.OtpResetUiState
import com.example.data.auth.OtpStep
import com.example.data.auth.SecurityUtils
import com.example.ui.components.studyMateTextFieldColors
import com.example.ui.theme.AcademicBlue
import com.example.ui.theme.DangerRed
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.WarningOrange
import kotlinx.coroutines.delay

@Composable
fun AuthScreen(
    onLogin: (email: String, password: String) -> Unit,
    onRegister: (fullName: String, email: String, password: String, confirmPass: String) -> Unit,
    onGoogleSignIn: () -> Unit = {},
    onForgotPassword: (email: String, onResult: (isSuccess: Boolean, message: String) -> Unit) -> Unit = { _, _ -> },
    authError: String?,
    isLoading: Boolean,
    onClearError: () -> Unit,
    showOtpResetDialog: Boolean = false,
    otpResetState: OtpResetUiState = OtpResetUiState(),
    onOpenForgotPassword: (email: String) -> Unit = {},
    onDismissForgotPassword: () -> Unit = {},
    onRequestOtp: (email: String) -> Unit = {},
    onVerifyOtp: (otp: String) -> Unit = {},
    onResendOtp: () -> Unit = {},
    onResetPassword: (newPass: String, confirmPass: String) -> Unit = { _, _ -> },
    onCompleteReset: () -> String = { "" }
) {
    var selectedTab by remember { mutableIntStateOf(0) } // 0 = Login, 1 = Sign Up

    // Login Form Fields
    var loginEmail by remember { mutableStateOf("") }
    var loginPassword by remember { mutableStateOf("") }
    var loginPasswordVisible by remember { mutableStateOf(false) }

    // Sign Up Form Fields
    var signupName by remember { mutableStateOf("") }
    var signupEmail by remember { mutableStateOf("") }
    var signupPassword by remember { mutableStateOf("") }
    var signupConfirmPassword by remember { mutableStateOf("") }
    var signupPasswordVisible by remember { mutableStateOf(false) }

    val focusManager = LocalFocusManager.current
    val scrollState = rememberScrollState()

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .testTag("auth_screen"),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
                .verticalScroll(scrollState)
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(24.dp))

            // Branding Header
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(AcademicBlue.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.School,
                    contentDescription = "StudyMate",
                    tint = AcademicBlue,
                    modifier = Modifier.size(36.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "StudyMate",
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )

            Text(
                text = "Student Academic Intelligence",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(28.dp))

            // Segmented Tab: Login / Sign Up
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(4.dp)
                ) {
                    // Login Tab
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                selectedTab = 0
                                onClearError()
                            }
                            .testTag("tab_login"),
                        color = if (selectedTab == 0) MaterialTheme.colorScheme.surface else Color.Transparent,
                        shadowElevation = if (selectedTab == 0) 1.dp else 0.dp
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.padding(vertical = 10.dp)
                        ) {
                            Text(
                                text = "Login",
                                fontSize = 14.sp,
                                fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Medium,
                                color = if (selectedTab == 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Sign Up Tab
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                selectedTab = 1
                                onClearError()
                            }
                            .testTag("tab_signup"),
                        color = if (selectedTab == 1) MaterialTheme.colorScheme.surface else Color.Transparent,
                        shadowElevation = if (selectedTab == 1) 1.dp else 0.dp
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.padding(vertical = 10.dp)
                        ) {
                            Text(
                                text = "Create Account",
                                fontSize = 14.sp,
                                fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Medium,
                                color = if (selectedTab == 1) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Error Message Banner
            AnimatedVisibility(visible = !authError.isNullOrBlank()) {
                Surface(
                    color = DangerRed.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, DangerRed.copy(alpha = 0.3f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                ) {
                    Text(
                        text = authError ?: "",
                        fontSize = 13.sp,
                        color = DangerRed,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                    )
                }
            }

            // ==========================================
            // LOGIN FORM
            // ==========================================
            if (selectedTab == 0) {
                OutlinedTextField(
                    value = loginEmail,
                    onValueChange = {
                        loginEmail = it
                        onClearError()
                    },
                    label = { Text("Email") },
                    placeholder = { Text("student@example.com") },
                    leadingIcon = {
                        Icon(imageVector = Icons.Default.Email, contentDescription = null)
                    },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                    colors = studyMateTextFieldColors(),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("login_email_input")
                )

                Spacer(modifier = Modifier.height(14.dp))

                OutlinedTextField(
                    value = loginPassword,
                    onValueChange = {
                        loginPassword = it
                        onClearError()
                    },
                    label = { Text("Password") },
                    placeholder = { Text("Enter your password") },
                    leadingIcon = {
                        Icon(imageVector = Icons.Default.Lock, contentDescription = null)
                    },
                    trailingIcon = {
                        IconButton(onClick = { loginPasswordVisible = !loginPasswordVisible }) {
                            Icon(
                                imageVector = if (loginPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                contentDescription = if (loginPasswordVisible) "Hide password" else "Show password"
                            )
                        }
                    },
                    visualTransformation = if (loginPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = {
                        focusManager.clearFocus()
                        onLogin(loginEmail, loginPassword)
                    }),
                    colors = studyMateTextFieldColors(),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("login_password_input")
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(
                        onClick = {
                            onOpenForgotPassword(loginEmail.trim())
                        },
                        modifier = Modifier.testTag("forgot_password_button")
                    ) {
                        Text(
                            text = "Forgot Password?",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = {
                        focusManager.clearFocus()
                        onLogin(loginEmail, loginPassword)
                    },
                    enabled = !isLoading,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("login_submit_button")
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                    } else {
                        Text(text = "Login to StudyMate", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // OR Divider
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    HorizontalDivider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant)
                    Text(
                        text = "  OR  ",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    HorizontalDivider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant)
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Google Sign-In Button
                OutlinedButton(
                    onClick = onGoogleSignIn,
                    enabled = !isLoading,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.onSurface
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("login_google_button")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(26.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = "G",
                                    fontWeight = FontWeight.Black,
                                    fontSize = 15.sp,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Continue with Google",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(text = "New to StudyMate? ", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        text = "Create Account",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .clickable {
                                selectedTab = 1
                                onClearError()
                            }
                            .testTag("switch_to_signup")
                    )
                }
            }

            // ==========================================
            // SIGN UP FORM
            // ==========================================
            if (selectedTab == 1) {
                OutlinedTextField(
                    value = signupName,
                    onValueChange = {
                        signupName = it
                        onClearError()
                    },
                    label = { Text("Full Name") },
                    placeholder = { Text("Subhankar Sharma") },
                    leadingIcon = {
                        Icon(imageVector = Icons.Default.Person, contentDescription = null)
                    },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Next),
                    colors = studyMateTextFieldColors(),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("signup_name_input")
                )

                Spacer(modifier = Modifier.height(14.dp))

                OutlinedTextField(
                    value = signupEmail,
                    onValueChange = {
                        signupEmail = it
                        onClearError()
                    },
                    label = { Text("Email") },
                    placeholder = { Text("student@example.com") },
                    leadingIcon = {
                        Icon(imageVector = Icons.Default.Email, contentDescription = null)
                    },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                    colors = studyMateTextFieldColors(),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("signup_email_input")
                )

                Spacer(modifier = Modifier.height(14.dp))

                OutlinedTextField(
                    value = signupPassword,
                    onValueChange = {
                        signupPassword = it
                        onClearError()
                    },
                    label = { Text("Password (min 8 chars)") },
                    placeholder = { Text("Enter your password") },
                    leadingIcon = {
                        Icon(imageVector = Icons.Default.Lock, contentDescription = null)
                    },
                    trailingIcon = {
                        IconButton(onClick = { signupPasswordVisible = !signupPasswordVisible }) {
                            Icon(
                                imageVector = if (signupPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                contentDescription = if (signupPasswordVisible) "Hide password" else "Show password"
                            )
                        }
                    },
                    visualTransformation = if (signupPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Next),
                    colors = studyMateTextFieldColors(),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("signup_password_input")
                )

                Spacer(modifier = Modifier.height(14.dp))

                OutlinedTextField(
                    value = signupConfirmPassword,
                    onValueChange = {
                        signupConfirmPassword = it
                        onClearError()
                    },
                    label = { Text("Confirm Password") },
                    placeholder = { Text("Re-enter your password") },
                    leadingIcon = {
                        Icon(imageVector = Icons.Default.Lock, contentDescription = null)
                    },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = {
                        focusManager.clearFocus()
                        onRegister(signupName, signupEmail, signupPassword, signupConfirmPassword)
                    }),
                    colors = studyMateTextFieldColors(),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("signup_confirm_password_input")
                )

                Spacer(modifier = Modifier.height(20.dp))

                Button(
                    onClick = {
                        focusManager.clearFocus()
                        onRegister(signupName, signupEmail, signupPassword, signupConfirmPassword)
                    },
                    enabled = !isLoading,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("signup_submit_button")
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                    } else {
                        Text(text = "Create Account & Setup Profile", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // OR Divider
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    HorizontalDivider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant)
                    Text(
                        text = "  OR  ",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    HorizontalDivider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant)
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Google Sign-In Button (Sign Up)
                OutlinedButton(
                    onClick = onGoogleSignIn,
                    enabled = !isLoading,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.onSurface
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("signup_google_button")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(26.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = "G",
                                    fontWeight = FontWeight.Black,
                                    fontSize = 15.sp,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Continue with Google",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(text = "Already have an account? ", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        text = "Login",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .clickable {
                                selectedTab = 0
                                onClearError()
                            }
                            .testTag("switch_to_login")
                    )
                }
            }
        }
    }

    // ========================================================
    // SECURE REAL EMAIL OTP PASSWORD RESET MULTI-STEP DIALOG
    // ========================================================
    if (showOtpResetDialog) {
        OtpPasswordResetDialog(
            state = otpResetState,
            onDismiss = onDismissForgotPassword,
            onRequestOtp = onRequestOtp,
            onVerifyOtp = onVerifyOtp,
            onResendOtp = onResendOtp,
            onResetPassword = onResetPassword,
            onComplete = {
                val email = onCompleteReset()
                if (email.isNotBlank()) {
                    loginEmail = email
                }
                selectedTab = 0
            }
        )
    }
}

/**
 * Multi-Step Material 3 Dialog implementing the 4 required screens:
 * 1. Forgot Password? (Enter email -> Send OTP)
 * 2. Verify Your Email (Enter 6-digit OTP -> countdown & resend cooldown)
 * 3. Create New Password (8+ chars, uppercase, lowercase, number, matching confirmation)
 * 4. Password Reset Successful (Confirmation -> Back to Login)
 */
@Composable
fun OtpPasswordResetDialog(
    state: OtpResetUiState,
    onDismiss: () -> Unit,
    onRequestOtp: (email: String) -> Unit,
    onVerifyOtp: (otp: String) -> Unit,
    onResendOtp: () -> Unit,
    onResetPassword: (newPass: String, confirmPass: String) -> Unit,
    onComplete: () -> Unit
) {
    var emailInput by remember(state.email) { mutableStateOf(state.email) }
    var otpInput by remember { mutableStateOf("") }
    var newPasswordInput by remember { mutableStateOf("") }
    var confirmPasswordInput by remember { mutableStateOf("") }
    var newPasswordVisible by remember { mutableStateOf(false) }
    var confirmPasswordVisible by remember { mutableStateOf(false) }

    // Live countdown clock for expiry and resend cooldown
    var currentTimeMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(state.step) {
        while (true) {
            currentTimeMillis = System.currentTimeMillis()
            delay(1000)
        }
    }

    val expiryRemainingSeconds = remember(currentTimeMillis, state.expiresAtMillis) {
        if (state.expiresAtMillis > currentTimeMillis) {
            ((state.expiresAtMillis - currentTimeMillis) / 1000).toInt()
        } else 0
    }

    val cooldownRemainingSeconds = remember(currentTimeMillis, state.cooldownUntilMillis) {
        if (state.cooldownUntilMillis > currentTimeMillis) {
            ((state.cooldownUntilMillis - currentTimeMillis) / 1000).toInt()
        } else 0
    }

    val expiryMinutes = expiryRemainingSeconds / 60
    val expirySeconds = expiryRemainingSeconds % 60
    val formattedExpiry = String.format("%02d:%02d", expiryMinutes, expirySeconds)

    AlertDialog(
        onDismissRequest = {
            if (!state.isLoading) onDismiss()
        },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(AcademicBlue.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = when (state.step) {
                            OtpStep.EMAIL -> Icons.Default.LockReset
                            OtpStep.VERIFY_OTP -> Icons.Default.Email
                            OtpStep.NEW_PASSWORD -> Icons.Default.Lock
                            OtpStep.SUCCESS -> Icons.Default.CheckCircle
                        },
                        contentDescription = null,
                        tint = if (state.step == OtpStep.SUCCESS) SuccessGreen else AcademicBlue,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = when (state.step) {
                        OtpStep.EMAIL -> "Forgot Password?"
                        OtpStep.VERIFY_OTP -> "Verify Your Email"
                        OtpStep.NEW_PASSWORD -> "Create New Password"
                        OtpStep.SUCCESS -> "Password Reset Successful"
                    },
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                // ==========================================
                // SCREEN 1: FORGOT PASSWORD (EMAIL ENTRY)
                // ==========================================
                if (state.step == OtpStep.EMAIL) {
                    Text(
                        text = "Enter your registered email address and we will send you a 6-digit verification code.",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedTextField(
                        value = emailInput,
                        onValueChange = { emailInput = it },
                        label = { Text("Email") },
                        placeholder = { Text("student@example.com") },
                        leadingIcon = {
                            Icon(imageVector = Icons.Default.Email, contentDescription = null)
                        },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Done),
                        colors = studyMateTextFieldColors(),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("otp_email_input")
                    )
                }

                // ==========================================
                // SCREEN 2: VERIFY YOUR EMAIL (OTP ENTRY)
                // ==========================================
                if (state.step == OtpStep.VERIFY_OTP) {
                    Text(
                        text = "We sent a 6-digit verification code to your registered email address.",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = state.email,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = AcademicBlue
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    OutlinedTextField(
                        value = otpInput,
                        onValueChange = { if (it.length <= 6 && it.all { c -> c.isDigit() }) otpInput = it },
                        label = { Text("Verification Code") },
                        placeholder = { Text("123456") },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 4.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                        colors = studyMateTextFieldColors(),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("otp_code_input")
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Expiry Countdown
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (expiryRemainingSeconds > 0) {
                                "Code expires in $formattedExpiry"
                            } else {
                                "Code expired. Please request a new code."
                            },
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (expiryRemainingSeconds > 0) MaterialTheme.colorScheme.onSurfaceVariant else DangerRed
                        )

                        if (state.attemptsRemaining < 5) {
                            Text(
                                text = "${state.attemptsRemaining} attempts left",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = WarningOrange
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Resend Cooldown
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (cooldownRemainingSeconds > 0) {
                            Text(
                                text = "You can request another code in $cooldownRemainingSeconds seconds.",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            TextButton(
                                onClick = onResendOtp,
                                enabled = !state.isLoading,
                                modifier = Modifier.testTag("otp_resend_button")
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Resend OTP",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }

                // ==========================================
                // SCREEN 3: CREATE NEW PASSWORD
                // ==========================================
                if (state.step == OtpStep.NEW_PASSWORD) {
                    Text(
                        text = "Create a secure new password for your StudyMate account.",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    OutlinedTextField(
                        value = newPasswordInput,
                        onValueChange = { newPasswordInput = it },
                        label = { Text("New Password") },
                        placeholder = { Text("Enter your new password") },
                        leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                        trailingIcon = {
                            IconButton(onClick = { newPasswordVisible = !newPasswordVisible }) {
                                Icon(
                                    imageVector = if (newPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = null
                                )
                            }
                        },
                        visualTransformation = if (newPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Next),
                        colors = studyMateTextFieldColors(),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("otp_new_password_input")
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = confirmPasswordInput,
                        onValueChange = { confirmPasswordInput = it },
                        label = { Text("Confirm Password") },
                        placeholder = { Text("Re-enter your new password") },
                        leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                        trailingIcon = {
                            IconButton(onClick = { confirmPasswordVisible = !confirmPasswordVisible }) {
                                Icon(
                                    imageVector = if (confirmPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = null
                                )
                            }
                        },
                        visualTransformation = if (confirmPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                        colors = studyMateTextFieldColors(),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("otp_confirm_password_input")
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Real-time Password Requirements Checklist
                    PasswordRequirementItem(label = "Minimum 8 characters", isMet = SecurityUtils.hasMinPasswordLength(newPasswordInput))
                    PasswordRequirementItem(label = "At least 1 uppercase letter (A-Z)", isMet = SecurityUtils.hasUppercase(newPasswordInput))
                    PasswordRequirementItem(label = "At least 1 lowercase letter (a-z)", isMet = SecurityUtils.hasLowercase(newPasswordInput))
                    PasswordRequirementItem(label = "At least 1 number (0-9)", isMet = SecurityUtils.hasDigit(newPasswordInput))
                    PasswordRequirementItem(
                        label = "Matching confirmation",
                        isMet = confirmPasswordInput.isNotEmpty() && newPasswordInput == confirmPasswordInput
                    )
                }

                // ==========================================
                // SCREEN 4: PASSWORD RESET SUCCESSFUL
                // ==========================================
                if (state.step == OtpStep.SUCCESS) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "Your StudyMate password has been updated successfully.",
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "You can now log in with your new password.",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                }

                // Error Banner
                if (!state.error.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Surface(
                        color = DangerRed.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = state.error,
                            fontSize = 12.sp,
                            color = DangerRed,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }

                // Neutral / Success Info Banner
                if (!state.successMessage.isNullOrBlank() && state.step != OtpStep.SUCCESS) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Surface(
                        color = SuccessGreen.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = state.successMessage,
                            fontSize = 12.sp,
                            color = SuccessGreen,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            when (state.step) {
                OtpStep.EMAIL -> {
                    Button(
                        onClick = { onRequestOtp(emailInput.trim()) },
                        enabled = !state.isLoading && emailInput.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = AcademicBlue),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.testTag("otp_send_button")
                    ) {
                        if (state.isLoading) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Sending OTP...")
                            }
                        } else {
                            Text("Send OTP")
                        }
                    }
                }
                OtpStep.VERIFY_OTP -> {
                    Button(
                        onClick = { onVerifyOtp(otpInput.trim()) },
                        enabled = !state.isLoading && otpInput.length == 6,
                        colors = ButtonDefaults.buttonColors(containerColor = AcademicBlue),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.testTag("otp_verify_button")
                    ) {
                        if (state.isLoading) {
                            CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                        } else {
                            Text("Verify OTP")
                        }
                    }
                }
                OtpStep.NEW_PASSWORD -> {
                    Button(
                        onClick = { onResetPassword(newPasswordInput, confirmPasswordInput) },
                        enabled = !state.isLoading,
                        colors = ButtonDefaults.buttonColors(containerColor = AcademicBlue),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.testTag("otp_reset_password_button")
                    ) {
                        if (state.isLoading) {
                            CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                        } else {
                            Text("Reset Password")
                        }
                    }
                }
                OtpStep.SUCCESS -> {
                    Button(
                        onClick = onComplete,
                        colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.testTag("otp_back_to_login_button")
                    ) {
                        Text("Back to Login", fontWeight = FontWeight.Bold)
                    }
                }
            }
        },
        dismissButton = {
            if (state.step != OtpStep.SUCCESS) {
                TextButton(
                    onClick = onDismiss,
                    enabled = !state.isLoading
                ) {
                    Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    )
}

@Composable
private fun PasswordRequirementItem(label: String, isMet: Boolean) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 2.dp)
    ) {
        Icon(
            imageVector = if (isMet) Icons.Default.Check else Icons.Default.Close,
            contentDescription = null,
            tint = if (isMet) SuccessGreen else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.size(14.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = label,
            fontSize = 11.sp,
            color = if (isMet) SuccessGreen else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = if (isMet) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}
