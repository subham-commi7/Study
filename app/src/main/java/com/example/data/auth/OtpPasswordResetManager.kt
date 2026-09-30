package com.example.data.auth

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

enum class OtpStep {
    EMAIL,
    VERIFY_OTP,
    NEW_PASSWORD,
    SUCCESS
}

data class OtpResetUiState(
    val step: OtpStep = OtpStep.EMAIL,
    val email: String = "",
    val resetToken: String? = null,
    val expiresAtMillis: Long = 0L,
    val cooldownUntilMillis: Long = 0L,
    val attemptsRemaining: Int = 5,
    val isLoading: Boolean = false,
    val error: String? = null,
    val successMessage: String? = null
)

data class OtpSignupUiState(
    val isOpen: Boolean = false,
    val email: String = "",
    val fullName: String = "",
    val password: String = "",
    val signupToken: String? = null,
    val expiresAtMillis: Long = 0L,
    val cooldownUntilMillis: Long = 0L,
    val attemptsRemaining: Int = 5,
    val isLoading: Boolean = false,
    val error: String? = null,
    val successMessage: String? = null
)

interface IOtpPasswordResetService {
    suspend fun requestOtp(email: String): Result<OtpRequestResult>
    suspend fun verifyOtp(email: String, otp: String): Result<OtpVerifyResult>
    suspend fun resetPassword(email: String, resetToken: String, newPassword: String): Result<OtpResetResult>
}

interface IOtpSignupService {
    suspend fun requestSignupOtp(email: String, fullName: String): Result<OtpRequestResult>
    suspend fun verifySignupOtp(email: String, otp: String): Result<OtpSignupVerifyResult>
    suspend fun createVerifiedEmailAccount(
        email: String,
        signupToken: String,
        password: String,
        fullName: String
    ): Result<OtpSignupAccountResult>
}

data class OtpRequestResult(
    val success: Boolean,
    val message: String,
    val cooldownSeconds: Int = 30,
    val expiresInSeconds: Int = 300
)

data class OtpVerifyResult(
    val success: Boolean,
    val resetToken: String,
    val message: String
)

data class OtpResetResult(
    val success: Boolean,
    val message: String
)

data class OtpSignupVerifyResult(
    val success: Boolean,
    val signupToken: String,
    val message: String
)

data class OtpSignupAccountResult(
    val success: Boolean,
    val customToken: String? = null,
    val uid: String? = null,
    val message: String
)

class OtpPasswordResetManager(
    private val baseUrl: String = "https://us-central1-studymate-c9f21.cloudfunctions.net",
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()
) : IOtpPasswordResetService, IOtpSignupService {

    companion object {
        private const val JSON_MEDIA_TYPE = "application/json; charset=utf-8"
        const val OTP_LENGTH = 6
        const val OTP_EXPIRY_MILLIS = 5 * 60 * 1000L // 5 minutes
        const val RESEND_COOLDOWN_MILLIS = 30 * 1000L // 30 seconds
        const val MAX_ATTEMPTS = 5
    }

    // ==========================================
    // 1. FORGOT PASSWORD WORKFLOW
    // ==========================================

    override suspend fun requestOtp(email: String): Result<OtpRequestResult> = withContext(Dispatchers.IO) {
        val trimmedEmail = email.trim().lowercase()
        if (!SecurityUtils.isValidEmail(trimmedEmail)) {
            return@withContext Result.failure(IllegalArgumentException("Please enter a valid email address."))
        }

        val jsonBody = JSONObject().apply {
            put("email", trimmedEmail)
        }

        val request = Request.Builder()
            .url("$baseUrl/requestPasswordResetOtp")
            .post(jsonBody.toString().toRequestBody(JSON_MEDIA_TYPE.toMediaType()))
            .build()

        try {
            val response = httpClient.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""

            if (response.isSuccessful) {
                val json = JSONObject(responseBody)
                val msg = json.optString("message", "If an account exists for this email address, a verification code will be sent.")
                val cooldown = json.optInt("cooldownSeconds", 30)
                val expires = json.optInt("expiresInSeconds", 300)
                Result.success(OtpRequestResult(success = true, message = msg, cooldownSeconds = cooldown, expiresInSeconds = expires))
            } else if (response.code == 429) {
                val json = try { JSONObject(responseBody) } catch (_: Exception) { JSONObject() }
                val errorMsg = json.optString("error", "Please wait before requesting another code.")
                Result.failure(IllegalStateException(errorMsg))
            } else if (response.code == 404) {
                Result.failure(IllegalStateException("Cloud Function 'requestPasswordResetOtp' is not deployed yet on project studymate-c9f21 in region us-central1."))
            } else {
                val json = try { JSONObject(responseBody) } catch (_: Exception) { JSONObject() }
                val errorMsg = json.optString("error", "Failed to send verification code. Please verify backend deployment.")
                Result.failure(Exception(errorMsg))
            }
        } catch (e: IOException) {
            Result.failure(IOException("Network error. Please check your internet connection or verify Cloud Functions deployment on project studymate-c9f21."))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun verifyOtp(email: String, otp: String): Result<OtpVerifyResult> = withContext(Dispatchers.IO) {
        val trimmedEmail = email.trim().lowercase()
        val cleanOtp = otp.trim()

        if (!SecurityUtils.isValidOtp(cleanOtp)) {
            return@withContext Result.failure(IllegalArgumentException("Verification code must be exactly 6 digits."))
        }

        val jsonBody = JSONObject().apply {
            put("email", trimmedEmail)
            put("otp", cleanOtp)
        }

        val request = Request.Builder()
            .url("$baseUrl/verifyPasswordResetOtp")
            .post(jsonBody.toString().toRequestBody(JSON_MEDIA_TYPE.toMediaType()))
            .build()

        try {
            val response = httpClient.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""

            if (response.isSuccessful) {
                val json = JSONObject(responseBody)
                val token = json.optString("resetToken", "")
                val msg = json.optString("message", "Verification successful.")
                if (token.isBlank()) {
                    Result.failure(IllegalStateException("No reset token returned from server."))
                } else {
                    Result.success(OtpVerifyResult(success = true, resetToken = token, message = msg))
                }
            } else if (response.code == 404) {
                Result.failure(IllegalStateException("Cloud Function 'verifyPasswordResetOtp' is not deployed yet on project studymate-c9f21."))
            } else {
                val json = try { JSONObject(responseBody) } catch (_: Exception) { JSONObject() }
                val errorMsg = json.optString("error", "Verification failed. Please check the code and try again.")
                Result.failure(IllegalStateException(errorMsg))
            }
        } catch (e: IOException) {
            Result.failure(IOException("Network error during verification. Please check your connection."))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun resetPassword(
        email: String,
        resetToken: String,
        newPassword: String
    ): Result<OtpResetResult> = withContext(Dispatchers.IO) {
        val trimmedEmail = email.trim().lowercase()

        val pwdValidation = SecurityUtils.validatePasswordStrength(newPassword)
        if (pwdValidation != null) {
            return@withContext Result.failure(IllegalArgumentException(pwdValidation))
        }

        if (resetToken.isBlank()) {
            return@withContext Result.failure(IllegalStateException("Verification token missing. Please restart password reset."))
        }

        val jsonBody = JSONObject().apply {
            put("email", trimmedEmail)
            put("resetToken", resetToken)
            put("newPassword", newPassword)
        }

        val request = Request.Builder()
            .url("$baseUrl/resetPasswordWithToken")
            .post(jsonBody.toString().toRequestBody(JSON_MEDIA_TYPE.toMediaType()))
            .build()

        try {
            val response = httpClient.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""

            if (response.isSuccessful) {
                val json = JSONObject(responseBody)
                val msg = json.optString("message", "Your StudyMate password has been updated successfully.")
                Result.success(OtpResetResult(success = true, message = msg))
            } else if (response.code == 404) {
                Result.failure(IllegalStateException("Cloud Function 'resetPasswordWithToken' is not deployed yet on project studymate-c9f21."))
            } else {
                val json = try { JSONObject(responseBody) } catch (_: Exception) { JSONObject() }
                val errorMsg = json.optString("error", "Failed to update password. Please try again.")
                Result.failure(IllegalStateException(errorMsg))
            }
        } catch (e: IOException) {
            Result.failure(IOException("Network error while updating password."))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ==========================================
    // 2. CREATE ACCOUNT EMAIL OTP WORKFLOW
    // ==========================================

    override suspend fun requestSignupOtp(email: String, fullName: String): Result<OtpRequestResult> = withContext(Dispatchers.IO) {
        val trimmedEmail = email.trim().lowercase()
        if (!SecurityUtils.isValidEmail(trimmedEmail)) {
            return@withContext Result.failure(IllegalArgumentException("Please enter a valid email address."))
        }

        val jsonBody = JSONObject().apply {
            put("email", trimmedEmail)
            put("fullName", fullName.trim())
        }

        val request = Request.Builder()
            .url("$baseUrl/requestSignupOtp")
            .post(jsonBody.toString().toRequestBody(JSON_MEDIA_TYPE.toMediaType()))
            .build()

        try {
            val response = httpClient.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""

            if (response.isSuccessful) {
                val json = JSONObject(responseBody)
                val msg = json.optString("message", "A verification code has been sent to your email address.")
                val cooldown = json.optInt("cooldownSeconds", 30)
                val expires = json.optInt("expiresInSeconds", 300)
                Result.success(OtpRequestResult(success = true, message = msg, cooldownSeconds = cooldown, expiresInSeconds = expires))
            } else if (response.code == 429) {
                val json = try { JSONObject(responseBody) } catch (_: Exception) { JSONObject() }
                val errorMsg = json.optString("error", "Please wait before requesting another code.")
                Result.failure(IllegalStateException(errorMsg))
            } else if (response.code == 404) {
                Result.failure(IllegalStateException("Cloud Function 'requestSignupOtp' is not deployed yet on project studymate-c9f21 in region us-central1."))
            } else {
                val json = try { JSONObject(responseBody) } catch (_: Exception) { JSONObject() }
                val errorMsg = json.optString("error", "Failed to send signup verification code.")
                Result.failure(Exception(errorMsg))
            }
        } catch (e: IOException) {
            Result.failure(IOException("Network error while requesting verification code. Please check your connection."))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun verifySignupOtp(email: String, otp: String): Result<OtpSignupVerifyResult> = withContext(Dispatchers.IO) {
        val trimmedEmail = email.trim().lowercase()
        val cleanOtp = otp.trim()

        if (!SecurityUtils.isValidOtp(cleanOtp)) {
            return@withContext Result.failure(IllegalArgumentException("Verification code must be exactly 6 digits."))
        }

        val jsonBody = JSONObject().apply {
            put("email", trimmedEmail)
            put("otp", cleanOtp)
        }

        val request = Request.Builder()
            .url("$baseUrl/verifySignupOtp")
            .post(jsonBody.toString().toRequestBody(JSON_MEDIA_TYPE.toMediaType()))
            .build()

        try {
            val response = httpClient.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""

            if (response.isSuccessful) {
                val json = JSONObject(responseBody)
                val token = json.optString("signupToken", "")
                val msg = json.optString("message", "Email verified successfully.")
                if (token.isBlank()) {
                    Result.failure(IllegalStateException("No signup token returned from server."))
                } else {
                    Result.success(OtpSignupVerifyResult(success = true, signupToken = token, message = msg))
                }
            } else if (response.code == 404) {
                Result.failure(IllegalStateException("Cloud Function 'verifySignupOtp' is not deployed yet on project studymate-c9f21."))
            } else {
                val json = try { JSONObject(responseBody) } catch (_: Exception) { JSONObject() }
                val errorMsg = json.optString("error", "Verification failed. Please check the code and try again.")
                Result.failure(IllegalStateException(errorMsg))
            }
        } catch (e: IOException) {
            Result.failure(IOException("Network error during signup verification."))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun createVerifiedEmailAccount(
        email: String,
        signupToken: String,
        password: String,
        fullName: String
    ): Result<OtpSignupAccountResult> = withContext(Dispatchers.IO) {
        val trimmedEmail = email.trim().lowercase()

        val pwdValidation = SecurityUtils.validatePasswordStrength(password)
        if (pwdValidation != null) {
            return@withContext Result.failure(IllegalArgumentException(pwdValidation))
        }

        if (signupToken.isBlank()) {
            return@withContext Result.failure(IllegalStateException("Verification token missing. Please restart registration."))
        }

        val jsonBody = JSONObject().apply {
            put("email", trimmedEmail)
            put("signupToken", signupToken)
            put("password", password)
            put("fullName", fullName.trim())
        }

        val request = Request.Builder()
            .url("$baseUrl/createVerifiedEmailAccount")
            .post(jsonBody.toString().toRequestBody(JSON_MEDIA_TYPE.toMediaType()))
            .build()

        try {
            val response = httpClient.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""

            if (response.isSuccessful) {
                val json = JSONObject(responseBody)
                val token = json.optString("customToken", "").ifBlank { null }
                val uid = json.optString("uid", "").ifBlank { null }
                val msg = json.optString("message", "StudyMate account created and verified successfully.")
                Result.success(OtpSignupAccountResult(success = true, customToken = token, uid = uid, message = msg))
            } else if (response.code == 404) {
                Result.failure(IllegalStateException("Cloud Function 'createVerifiedEmailAccount' is not deployed yet on project studymate-c9f21."))
            } else {
                val json = try { JSONObject(responseBody) } catch (_: Exception) { JSONObject() }
                val errorMsg = json.optString("error", "Failed to create verified account.")
                Result.failure(IllegalStateException(errorMsg))
            }
        } catch (e: IOException) {
            Result.failure(IOException("Network error while creating account."))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
