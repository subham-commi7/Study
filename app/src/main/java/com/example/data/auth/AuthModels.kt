package com.example.data.auth

/**
 * State for Firebase Native Password Reset Link flow.
 * - Enter email -> Send Firebase Reset Link -> Prompt user to check inbox.
 * - If user opens action code link or enters code, allows entering new password.
 */
data class ForgotPasswordUiState(
    val isOpen: Boolean = false,
    val email: String = "",
    val isSent: Boolean = false,
    val isLoading: Boolean = false,
    val error: String? = null,
    val successMessage: String? = null,
    val actionCode: String? = null,
    val isResettingWithCode: Boolean = false
)
