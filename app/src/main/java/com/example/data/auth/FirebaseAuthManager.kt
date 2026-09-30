package com.example.data.auth

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.userProfileChangeRequest
import kotlinx.coroutines.tasks.await
import java.util.concurrent.CancellationException

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
            Result.success(user)
        } catch (e: FirebaseAuthWeakPasswordException) {
            Result.failure(IllegalArgumentException("Password is too weak: ${e.reason ?: "Must be at least 6 characters."}"))
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

    suspend fun signInWithCustomToken(customToken: String): Result<FirebaseUser> {
        val activeAuth = auth ?: return Result.failure(IllegalStateException("Firebase is not initialized."))
        return try {
            val authResult = activeAuth.signInWithCustomToken(customToken).await()
            val user = authResult.user ?: return Result.failure(IllegalStateException("Sign in failed with token."))
            Result.success(user)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

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
                    "Firebase Console Fingerprints to register for project studymate-c9f21:\n" +
                    "SHA-1: DC:46:28:96:09:2A:F0:F0:E9:0E:60:8D:16:76:AB:18:D8:BB:B7:04\n" +
                    "SHA-256: FF:BD:54:B4:2B:F5:9D:26:A2:54:1B:7A:9F:59:89:8D:9C:06:2B:A9:53:6D:7B:AF:19:B7:25:F8:77:57:C5:18"
                )
            )
        }

        return try {
            val credentialManager = CredentialManager.create(context)
            val googleIdOption = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)
                .setServerClientId(serverClientId)
                .setAutoSelectEnabled(false)
                .build()

            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()

            val result = credentialManager.getCredential(context = context, request = request)
            val credential = result.credential

            if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                val idToken = googleIdTokenCredential.idToken
                val authCredential = GoogleAuthProvider.getCredential(idToken, null)
                val authResult = activeAuth.signInWithCredential(authCredential).await()
                val user = authResult.user ?: return Result.failure(IllegalStateException("Firebase authentication with Google failed."))
                Result.success(user)
            } else {
                Result.failure(IllegalStateException("Unexpected credential format received from Google."))
            }
        } catch (e: GetCredentialCancellationException) {
            Result.failure(CancellationException("Google sign-in was cancelled."))
        } catch (e: NoCredentialException) {
            Result.failure(IllegalStateException("No Google account found on device."))
        } catch (e: GetCredentialException) {
            Result.failure(Exception(e.localizedMessage ?: "Google sign-in failed. Please verify SHA-1 in Firebase Console."))
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
