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
import com.example.data.auth.ForgotPasswordUiState
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
    authError: String?,
    isLoading: Boolean,
    onClearError: () -> Unit,
    showForgotPasswordDialog: Boolean = false,
    forgotPasswordState: ForgotPasswordUiState = ForgotPasswordUiState(),
    onOpenForgotPassword: (email: String) -> Unit = {},
    onDismissForgotPassword: () -> Unit = {},
    onSendPasswordResetLink: (email: String) -> Unit = {},
    onConfirmPasswordReset: (actionCode: String, newPass: String, confirmPass: String) -> Unit = { _, _, _ -> }
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
    // FIREBASE NATIVE PASSWORD RESET EMAIL LINK FLOW DIALOG
    // ========================================================
    if (showForgotPasswordDialog) {
        FirebasePasswordResetDialog(
            state = forgotPasswordState,
            onDismiss = onDismissForgotPassword,
            onSendResetLink = onSendPasswordResetLink,
            onConfirmReset = onConfirmPasswordReset
        )
    }
}

/**
 * Firebase Native Password Reset Dialog:
 * Uses Firebase Authentication's built-in password reset email functionality:
 * 1. User enters email -> sends standard Firebase reset link to user's inbox.
 * 2. User opens link or enters action code -> validates password and calls confirmPasswordReset.
 * 3. Shows clear success confirmation and returns to Login.
 *
 * NO custom OTP backend, NO Cloud Functions, NO custom SMTP required.
 */
@Composable
fun FirebasePasswordResetDialog(
    state: ForgotPasswordUiState,
    onDismiss: () -> Unit,
    onSendResetLink: (email: String) -> Unit,
    onConfirmReset: (actionCode: String, newPass: String, confirmPass: String) -> Unit
) {
    var emailInput by remember(state.email) { mutableStateOf(state.email) }
    var actionCodeInput by remember(state.actionCode) { mutableStateOf(state.actionCode ?: "") }
    var newPasswordInput by remember { mutableStateOf("") }
    var confirmPasswordInput by remember { mutableStateOf("") }
    var newPasswordVisible by remember { mutableStateOf(false) }
    var confirmPasswordVisible by remember { mutableStateOf(false) }
    var isManualCodeMode by remember(state.isResettingWithCode) { mutableStateOf(state.isResettingWithCode) }
    var localClientError by remember { mutableStateOf<String?>(null) }

    val isEnteringNewPassword = isManualCodeMode || state.isResettingWithCode || !state.actionCode.isNullOrBlank()

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
                        imageVector = if (state.successMessage != null) {
                            Icons.Default.CheckCircle
                        } else if (isEnteringNewPassword) {
                            Icons.Default.Lock
                        } else {
                            Icons.Default.LockReset
                        },
                        contentDescription = null,
                        tint = if (state.successMessage != null) SuccessGreen else AcademicBlue,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = if (state.successMessage != null) {
                        "Password Reset Successful"
                    } else if (isEnteringNewPassword) {
                        "Create New Password"
                    } else if (state.isSent) {
                        "Reset Link Sent"
                    } else {
                        "Forgot Password"
                    },
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                if (state.successMessage != null) {
                    Surface(
                        color = SuccessGreen.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                text = state.successMessage ?: "Password updated successfully!",
                                fontSize = 14.sp,
                                color = SuccessGreen,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "You can now log in to StudyMate using your new password.",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else if (isEnteringNewPassword) {
                    Text(
                        text = "Set your new StudyMate password using the Firebase reset action code.",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    if (state.actionCode.isNullOrBlank()) {
                        OutlinedTextField(
                            value = actionCodeInput,
                            onValueChange = { input ->
                                localClientError = null
                                val cleaned = if (input.contains("oobCode=")) {
                                    input.substringAfter("oobCode=").substringBefore("&").trim()
                                } else {
                                    input.trim()
                                }
                                actionCodeInput = cleaned
                            },
                            label = { Text("Reset Code or Link") },
                            placeholder = { Text("Paste code or link from email") },
                            leadingIcon = {
                                Icon(Icons.Default.LockReset, contentDescription = null)
                            },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                            colors = studyMateTextFieldColors(),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("reset_action_code_input")
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                    }

                    OutlinedTextField(
                        value = newPasswordInput,
                        onValueChange = {
                            newPasswordInput = it
                            localClientError = null
                        },
                        label = { Text("New Password (min 8 chars)") },
                        placeholder = { Text("Enter new password") },
                        leadingIcon = {
                            Icon(Icons.Default.Lock, contentDescription = null)
                        },
                        trailingIcon = {
                            IconButton(onClick = { newPasswordVisible = !newPasswordVisible }) {
                                Icon(
                                    imageVector = if (newPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = if (newPasswordVisible) "Hide password" else "Show password"
                                )
                            }
                        },
                        visualTransformation = if (newPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Next),
                        colors = studyMateTextFieldColors(),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("reset_new_password_input")
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = confirmPasswordInput,
                        onValueChange = {
                            confirmPasswordInput = it
                            localClientError = null
                        },
                        label = { Text("Confirm New Password") },
                        placeholder = { Text("Re-enter new password") },
                        leadingIcon = {
                            Icon(Icons.Default.Lock, contentDescription = null)
                        },
                        trailingIcon = {
                            IconButton(onClick = { confirmPasswordVisible = !confirmPasswordVisible }) {
                                Icon(
                                    imageVector = if (confirmPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = if (confirmPasswordVisible) "Hide password" else "Show password"
                                )
                            }
                        },
                        visualTransformation = if (confirmPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                        colors = studyMateTextFieldColors(),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("reset_confirm_password_input")
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Must contain at least 8 characters, an uppercase letter, a lowercase letter, a digit, and a special character.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    TextButton(
                        onClick = { isManualCodeMode = false },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("Need a reset link instead?", fontSize = 12.sp, color = AcademicBlue)
                    }
                } else if (state.isSent) {
                    Surface(
                        color = AcademicBlue.copy(alpha = 0.08f),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                text = "Check your email for the password reset link.",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = AcademicBlue
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "We sent an email to " + state.email + ". Click the secure link in the email to reset your password.",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = {
                                onSendResetLink(state.email)
                            },
                            enabled = !state.isLoading
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Resend Email", fontSize = 13.sp)
                        }

                        TextButton(
                            onClick = {
                                isManualCodeMode = true
                            }
                        ) {
                            Text("Have a code or link?", fontSize = 13.sp, color = AcademicBlue)
                        }
                    }
                } else {
                    Text(
                        text = "Enter your registered email address and Firebase will send you a password reset link.",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedTextField(
                        value = emailInput,
                        onValueChange = {
                            emailInput = it
                            localClientError = null
                        },
                        label = { Text("Registered Email") },
                        placeholder = { Text("student@example.com") },
                        leadingIcon = {
                            Icon(Icons.Default.Email, contentDescription = null)
                        },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = {
                            if (emailInput.isNotBlank() && !state.isLoading) {
                                onSendResetLink(emailInput.trim())
                            }
                        }),
                        colors = studyMateTextFieldColors(),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("forgot_password_email_input")
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    TextButton(
                        onClick = { isManualCodeMode = true },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("Already opened link / have code?", fontSize = 12.sp, color = AcademicBlue)
                    }
                }

                val activeError = state.error ?: localClientError
                if (!activeError.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Surface(
                        color = DangerRed.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = activeError,
                            fontSize = 12.sp,
                            color = DangerRed,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (state.successMessage != null) {
                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.testTag("reset_success_done_button")
                ) {
                    Text("Return to Login")
                }
            } else if (isEnteringNewPassword) {
                Button(
                    onClick = {
                        val finalCode = (state.actionCode ?: actionCodeInput).trim()
                        if (finalCode.isBlank()) {
                            localClientError = "Please enter or paste the reset code or link from your email."
                            return@Button
                        }
                        if (newPasswordInput.isEmpty()) {
                            localClientError = "Please enter a new password."
                            return@Button
                        }
                        if (newPasswordInput != confirmPasswordInput) {
                            localClientError = "Passwords do not match."
                            return@Button
                        }
                        val pwdValidation = SecurityUtils.validatePasswordStrength(newPasswordInput)
                        if (pwdValidation != null) {
                            localClientError = pwdValidation
                            return@Button
                        }
                        localClientError = null
                        onConfirmReset(finalCode, newPasswordInput, confirmPasswordInput)
                    },
                    enabled = !state.isLoading,
                    colors = ButtonDefaults.buttonColors(containerColor = AcademicBlue),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.testTag("confirm_new_password_button")
                ) {
                    if (state.isLoading) {
                        CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                    } else {
                        Text("Reset Password")
                    }
                }
            } else if (state.isSent) {
                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = AcademicBlue),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.testTag("reset_link_sent_ok_button")
                ) {
                    Text("Done")
                }
            } else {
                Button(
                    onClick = {
                        val trimmed = emailInput.trim()
                        if (!SecurityUtils.isValidEmail(trimmed)) {
                            localClientError = "Please enter a valid email address."
                            return@Button
                        }
                        localClientError = null
                        onSendResetLink(trimmed)
                    },
                    enabled = !state.isLoading && emailInput.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = AcademicBlue),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.testTag("send_reset_link_button")
                ) {
                    if (state.isLoading) {
                        CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                    } else {
                        Text("Send Reset Link")
                    }
                }
            }
        },
        dismissButton = {
            if (state.successMessage == null && !state.isSent) {
                TextButton(
                    onClick = onDismiss,
                    enabled = !state.isLoading,
                    modifier = Modifier.testTag("forgot_password_cancel_button")
                ) {
                    Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    )
}
