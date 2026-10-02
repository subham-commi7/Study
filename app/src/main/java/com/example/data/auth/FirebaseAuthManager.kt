package com.example.data.auth

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.ActionCodeResult
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthActionCodeException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.userProfileChangeRequest
import kotlinx.coroutines.tasks.await
import java.util.concurrent.CancellationException

/**
 * Native Firebase Authentication Manager:
 * Uses Firebase Authentication free Spark tier features exclusively:
 * - Native Email/Password registration
 * - Native Email Verification links (sendEmailVerification)
 * - Native Password Reset email links (sendPasswordResetEmail)
 * - Native Action Code verification & confirmation (confirmPasswordReset, applyActionCode)
 * - Google Sign-In with Credential Manager API
 *
 * NO Cloud Functions, NO custom SMTP, NO Secret Manager required.
 */
class FirebaseAuthManager(
    private val customAuth: FirebaseAuth? = null
) {
    private val auth: FirebaseAuth?
        get() = try {
            customAuth ?: FirebaseAuth.getInstance()
        } catch (_: Throwable) {
            null
        }

    val currentUser: FirebaseUser?
        get() = try {
            auth?.currentUser
        } catch (_: Throwable) {
            null
        }

    val isEmailVerified: Boolean
        get() = try {
            auth?.currentUser?.isEmailVerified == true
        } catch (_: Throwable) {
            false
        }

    /**
     * Creates an account with Email + Password, updates user profile,
     * and sends Firebase's native verification email link.
     */
    suspend fun createUserWithEmailAndPassword(
        email: String,
        password: String,
        fullName: String
    ): Result<FirebaseUser> {
        val trimmedEmail = email.trim()
        if (!SecurityUtils.isValidEmail(trimmedEmail)) {
            return Result.failure(IllegalArgumentException("Please enter a valid email address."))
        }
        val passValidation = SecurityUtils.validatePasswordStrength(password)
        if (passValidation != null) {
            return Result.failure(IllegalArgumentException(passValidation))
        }

        val activeAuth = auth ?: return Result.failure(IllegalStateException("Firebase is not initialized."))

        return try {
            val authResult = activeAuth.createUserWithEmailAndPassword(trimmedEmail, password).await()
            val user = authResult.user ?: return Result.failure(IllegalStateException("User creation failed."))

            if (fullName.isNotBlank()) {
                try {
                    val profileUpdates = userProfileChangeRequest {
                        displayName = fullName.trim()
                    }
                    user.updateProfile(profileUpdates).await()
                } catch (_: Exception) {
                    // Non-fatal if profile display name update fails
                }
            }

            // Send Firebase native email verification link
            try {
                user.sendEmailVerification().await()
            } catch (e: Exception) {
                // Non-fatal for account creation, but logged
            }

            Result.success(user)
        } catch (e: FirebaseAuthWeakPasswordException) {
            Result.failure(IllegalArgumentException("Password is too weak: ${e.reason ?: "Must be at least 8 characters."}"))
        } catch (e: FirebaseAuthUserCollisionException) {
            Result.failure(IllegalStateException("An account with email $trimmedEmail already exists. Please log in."))
        } catch (e: FirebaseAuthInvalidCredentialsException) {
            Result.failure(IllegalArgumentException("Invalid email format or credentials."))
        } catch (e: FirebaseNetworkException) {
            Result.failure(IllegalStateException("Network error. Please check your internet connection."))
        } catch (e: Exception) {
            Result.failure(Exception(e.localizedMessage ?: "Registration failed. Please try again."))
        }
    }

    /**
     * Signs in with Email and Password using standard Firebase Authentication.
     */
    suspend fun signInWithEmailAndPassword(
        email: String,
        password: String
    ): Result<FirebaseUser> {
        val trimmedEmail = email.trim()
        if (trimmedEmail.isEmpty()) {
            return Result.failure(IllegalArgumentException("Please enter your email."))
        }
        if (password.isEmpty()) {
            return Result.failure(IllegalArgumentException("Please enter your password."))
        }

        val activeAuth = auth ?: return Result.failure(IllegalStateException("Firebase is not initialized."))

        return try {
            val authResult = activeAuth.signInWithEmailAndPassword(trimmedEmail, password).await()
            val user = authResult.user ?: return Result.failure(IllegalStateException("Sign in failed."))
            Result.success(user)
        } catch (e: FirebaseAuthInvalidUserException) {
            Result.failure(IllegalArgumentException("No account found with this email, or account is disabled."))
        } catch (e: FirebaseAuthInvalidCredentialsException) {
            Result.failure(IllegalArgumentException("Incorrect email or password. Please try again."))
        } catch (e: FirebaseNetworkException) {
            Result.failure(IllegalStateException("Network connection error. Please check your internet connection."))
        } catch (e: Exception) {
            Result.failure(Exception(e.localizedMessage ?: "Login failed. Please try again."))
        }
    }

    /**
     * Sends Firebase native password-reset email containing a secure reset link.
     */
    suspend fun sendPasswordResetEmail(email: String): Result<Unit> {
        val trimmedEmail = email.trim()
        if (!SecurityUtils.isValidEmail(trimmedEmail)) {
            return Result.failure(IllegalArgumentException("Please enter a valid email address."))
        }

        val activeAuth = auth ?: return Result.failure(IllegalStateException("Firebase is not initialized."))

        return try {
            activeAuth.sendPasswordResetEmail(trimmedEmail).await()
            Result.success(Unit)
        } catch (e: FirebaseAuthInvalidUserException) {
            Result.failure(IllegalArgumentException("No account registered with $trimmedEmail."))
        } catch (e: FirebaseNetworkException) {
            Result.failure(IllegalStateException("Network error. Please check your internet connection."))
        } catch (e: Exception) {
            Result.failure(Exception(e.localizedMessage ?: "Failed to send password reset email."))
        }
    }

    /**
     * Sends Firebase native verification email to current user.
     */
    suspend fun sendEmailVerification(): Result<Unit> {
        val user = currentUser ?: return Result.failure(IllegalStateException("No authenticated user."))
        return try {
            user.sendEmailVerification().await()
            Result.success(Unit)
        } catch (e: FirebaseNetworkException) {
            Result.failure(IllegalStateException("Network error. Please check your internet connection."))
        } catch (e: Exception) {
            Result.failure(Exception(e.localizedMessage ?: "Failed to send verification email."))
        }
    }

    /**
     * Refreshes Firebase user profile to update emailVerified state.
     */
    suspend fun reloadUser(): Result<FirebaseUser> {
        val user = currentUser ?: return Result.failure(IllegalStateException("No authenticated user."))
        return try {
            user.reload().await()
            Result.success(user)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Verifies Firebase action code for password reset and returns associated email.
     */
    suspend fun verifyPasswordResetCode(actionCode: String): Result<String> {
        val activeAuth = auth ?: return Result.failure(IllegalStateException("Firebase is not initialized."))
        val cleanCode = actionCode.trim()
        if (cleanCode.isBlank()) {
            return Result.failure(IllegalArgumentException("Password reset link or code is missing."))
        }
        return try {
            val email = activeAuth.verifyPasswordResetCode(cleanCode).await()
            Result.success(email)
        } catch (e: FirebaseAuthActionCodeException) {
            Result.failure(IllegalArgumentException("The password reset link is invalid, expired, or has already been used."))
        } catch (e: FirebaseAuthInvalidCredentialsException) {
            Result.failure(IllegalArgumentException("The password reset link is invalid, expired, or has already been used."))
        } catch (e: FirebaseNetworkException) {
            Result.failure(IllegalStateException("Network error. Please check your internet connection."))
        } catch (e: Exception) {
            Result.failure(Exception(e.localizedMessage ?: "Invalid or expired password reset link."))
        }
    }

    /**
     * Completes password reset using Firebase native action code.
     */
    suspend fun confirmPasswordReset(actionCode: String, newPassword: String): Result<Unit> {
        val activeAuth = auth ?: return Result.failure(IllegalStateException("Firebase is not initialized."))
        val cleanCode = actionCode.trim()
        if (cleanCode.isBlank()) {
            return Result.failure(IllegalArgumentException("Password reset code or link is missing."))
        }

        val pwdValidation = SecurityUtils.validatePasswordStrength(newPassword)
        if (pwdValidation != null) {
            return Result.failure(IllegalArgumentException(pwdValidation))
        }

        return try {
            activeAuth.confirmPasswordReset(cleanCode, newPassword).await()
            Result.success(Unit)
        } catch (e: FirebaseAuthActionCodeException) {
            Result.failure(IllegalArgumentException("The password reset link has expired or has already been used. Please request a new link."))
        } catch (e: FirebaseAuthInvalidCredentialsException) {
            Result.failure(IllegalArgumentException("The password reset link is invalid or expired. Please request a new link."))
        } catch (e: FirebaseAuthWeakPasswordException) {
            Result.failure(IllegalArgumentException("Password is too weak: ${e.reason ?: "Must be at least 8 characters."}"))
        } catch (e: FirebaseNetworkException) {
            Result.failure(IllegalStateException("Network error. Please check your internet connection."))
        } catch (e: Exception) {
            Result.failure(Exception(e.localizedMessage ?: "Failed to reset password."))
        }
    }

    /**
     * Applies Firebase action code (e.g. for email verification).
     */
    suspend fun applyActionCode(actionCode: String): Result<Unit> {
        val activeAuth = auth ?: return Result.failure(IllegalStateException("Firebase is not initialized."))
        val cleanCode = actionCode.trim()
        if (cleanCode.isBlank()) {
            return Result.failure(IllegalArgumentException("Action code is missing."))
        }
        return try {
            activeAuth.applyActionCode(cleanCode).await()
            activeAuth.currentUser?.reload()?.await()
            Result.success(Unit)
        } catch (e: FirebaseAuthActionCodeException) {
            Result.failure(IllegalArgumentException("The link is expired or has already been used."))
        } catch (e: FirebaseAuthInvalidCredentialsException) {
            Result.failure(IllegalArgumentException("The link is invalid or expired."))
        } catch (e: Exception) {
            Result.failure(Exception(e.localizedMessage ?: "Failed to verify email link."))
        }
    }

    /**
     * Google Sign-In via Credential Manager.
     */
    suspend fun signInWithGoogle(
        context: Context,
        serverClientId: String
    ): Result<FirebaseUser> {
        val activeAuth = auth ?: return Result.failure(IllegalStateException("Firebase is not initialized."))

        if (serverClientId.isBlank()) {
            return Result.failure(
                IllegalStateException(
                    "Google Sign-In Web Client ID is not configured.\n" +
                    "To enable Google Sign-In, add your OAuth Web Client ID in strings.xml (default_web_client_id) or google-services.json.\n" +
                    "Firebase Console Fingerprints for project studymate-c9f21:\n" +
                    "Debug SHA-1: 20:6F:A8:FE:02:1B:70:63:52:A2:07:C4:CF:71:50:D0:A1:A9:15:2F\n" +
                    "Debug SHA-256: 2F:C0:26:E0:1F:73:0B:1F:94:F8:62:AE:FC:8B:E7:0E:FA:8F:AD:DB:CF:C3:31:98:63:B4:92:E7:D4:79:AA:94\n" +
                    "Release SHA-1: 8A:6D:A4:D3:66:D9:A3:EF:19:11:10:75:29:37:01:E7:CE:DE:78:49\n" +
                    "Release SHA-256: 8C:C5:35:1C:E7:DE:9C:33:C7:FD:97:05:92:26:17:4F:94:06:0E:DD:22:36:6C:A9:B5:3F:D9:CC:03:3A:63:90"
                )
            )
        }

        return try {
            val credentialManager = CredentialManager.create(context)

            // Primary option: Google ID option showing all accounts on device + add account
            val googleIdOption = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)
                .setServerClientId(serverClientId)
                .setAutoSelectEnabled(false)
                .build()

            val credential = try {
                val request = GetCredentialRequest.Builder()
                    .addCredentialOption(googleIdOption)
                    .build()
                val result = credentialManager.getCredential(context = context, request = request)
                result.credential
            } catch (unsupported: Exception) {
                if (unsupported is GetCredentialCancellationException) throw unsupported
                // Fallback option: GetSignInWithGoogleOption
                val signInWithGoogleOption = GetSignInWithGoogleOption.Builder(serverClientId).build()
                val fallbackRequest = GetCredentialRequest.Builder()
                    .addCredentialOption(signInWithGoogleOption)
                    .build()
                val fallbackResult = credentialManager.getCredential(context = context, request = fallbackRequest)
                fallbackResult.credential
            }

            val idToken: String? = when {
                credential is CustomCredential && (
                    credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL ||
                    credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_SIWG_CREDENTIAL
                ) -> {
                    val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                    googleIdTokenCredential.idToken
                }
                credential is CustomCredential -> {
                    try {
                        val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                        googleIdTokenCredential.idToken
                    } catch (_: Exception) {
                        null
                    }
                }
                else -> null
            }

            if (idToken.isNullOrBlank()) {
                return Result.failure(IllegalStateException("Failed to obtain authentication token from Google."))
            }

            val authCredential = GoogleAuthProvider.getCredential(idToken, null)
            val authResult = activeAuth.signInWithCredential(authCredential).await()
            val user = authResult.user ?: return Result.failure(IllegalStateException("Firebase authentication with Google failed."))
            Result.success(user)
        } catch (e: GetCredentialCancellationException) {
            Result.failure(CancellationException("Google sign-in was cancelled."))
        } catch (e: NoCredentialException) {
            Result.failure(IllegalStateException("No Google account is available on this device. Please add a Google account and try again."))
        } catch (e: GetCredentialException) {
            Result.failure(Exception(e.localizedMessage ?: "Google sign-in failed. Please verify SHA-1 and SHA-256 fingerprints in Firebase Console."))
        } catch (e: FirebaseNetworkException) {
            Result.failure(IllegalStateException("Network connection error during Google sign-in."))
        } catch (e: Exception) {
            Result.failure(Exception(e.localizedMessage ?: "Google sign-in error."))
        }
    }

    fun signOut() {
        try {
            auth?.signOut()
        } catch (_: Exception) {}
    }
}
