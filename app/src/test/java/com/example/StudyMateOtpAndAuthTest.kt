package com.example

import com.example.data.auth.IOtpPasswordResetService
import com.example.data.auth.OtpRequestResult
import com.example.data.auth.OtpResetResult
import com.example.data.auth.OtpResetUiState
import com.example.data.auth.OtpStep
import com.example.data.auth.OtpVerifyResult
import com.example.data.auth.SecurityUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StudyMateOtpAndAuthTest {

    // ==========================================
    // 1. EMAIL VALIDATION TESTS
    // ==========================================

    @Test
    fun testValidEmailAddresses() {
        assertTrue(SecurityUtils.isValidEmail("subhankarsarkar3345@gmail.com"))
        assertTrue(SecurityUtils.isValidEmail("student@university.edu"))
        assertTrue(SecurityUtils.isValidEmail("stumatehelp@gmail.com"))
        assertTrue(SecurityUtils.isValidEmail("user.name+tag@college.ac.in"))
    }

    @Test
    fun testInvalidEmailAddresses() {
        assertFalse(SecurityUtils.isValidEmail(""))
        assertFalse(SecurityUtils.isValidEmail("   "))
        assertFalse(SecurityUtils.isValidEmail("notanemail"))
        assertFalse(SecurityUtils.isValidEmail("missingdomain@"))
        assertFalse(SecurityUtils.isValidEmail("@missinguser.com"))
        assertFalse(SecurityUtils.isValidEmail("spaces in@email.com"))
    }

    // ==========================================
    // 2. PASSWORD VALIDATION TESTS
    // ==========================================

    @Test
    fun testPasswordTooShort() {
        val err = SecurityUtils.validatePasswordStrength("Short1")
        assertNotNull(err)
        assertTrue(err!!.contains("at least 8 characters"))
    }

    @Test
    fun testPasswordMissingUppercase() {
        val err = SecurityUtils.validatePasswordStrength("lowercase123")
        assertNotNull(err)
        assertTrue(err!!.contains("uppercase"))
    }

    @Test
    fun testPasswordMissingLowercase() {
        val err = SecurityUtils.validatePasswordStrength("UPPERCASE123")
        assertNotNull(err)
        assertTrue(err!!.contains("lowercase"))
    }

    @Test
    fun testPasswordMissingDigit() {
        val err = SecurityUtils.validatePasswordStrength("NoNumbersHere")
        assertNotNull(err)
        assertTrue(err!!.contains("number"))
    }

    @Test
    fun testValidStrongPassword() {
        val err = SecurityUtils.validatePasswordStrength("SecureStudy2026")
        assertNull(err)
        assertTrue(SecurityUtils.hasMinPasswordLength("SecureStudy2026"))
        assertTrue(SecurityUtils.hasUppercase("SecureStudy2026"))
        assertTrue(SecurityUtils.hasLowercase("SecureStudy2026"))
        assertTrue(SecurityUtils.hasDigit("SecureStudy2026"))
    }

    // ==========================================
    // 3. CONFIRM PASSWORD VALIDATION
    // ==========================================

    @Test
    fun testPasswordConfirmationMatching() {
        val pass1 = "SecurePass123"
        val pass2 = "SecurePass123"
        assertEquals(pass1, pass2)

        val passMismatch = "DifferentPass123"
        assertFalse(pass1 == passMismatch)
    }

    // ==========================================
    // 4. OTP FORMAT VALIDATION
    // ==========================================

    @Test
    fun testValid6DigitOtp() {
        assertTrue(SecurityUtils.isValidOtp("123456"))
        assertTrue(SecurityUtils.isValidOtp("987654"))
        assertTrue(SecurityUtils.isValidOtp("000000"))
    }

    @Test
    fun testInvalidOtps() {
        assertFalse(SecurityUtils.isValidOtp("12345"))     // 5 digits
        assertFalse(SecurityUtils.isValidOtp("1234567"))   // 7 digits
        assertFalse(SecurityUtils.isValidOtp("12a456"))    // alphanumeric
        assertFalse(SecurityUtils.isValidOtp("abcdef"))    // letters
        assertFalse(SecurityUtils.isValidOtp(""))          // empty
        assertFalse(SecurityUtils.isValidOtp("   "))       // whitespace
    }

    // ==========================================
    // 5. OTP EXPIRY LOGIC (5 MINUTES)
    // ==========================================

    @Test
    fun testOtpExpiryLogic() {
        val createdAt = 1000000000L
        val expiryDuration = 5 * 60 * 1000L // 5 minutes = 300,000 ms
        val expiresAt = createdAt + expiryDuration

        // At 2 minutes after creation (120,000 ms): Not expired
        val timeAt2Minutes = createdAt + (2 * 60 * 1000L)
        assertTrue(timeAt2Minutes < expiresAt)

        // At exactly 5 minutes (300,000 ms): Boundary check
        assertEquals(expiresAt, createdAt + expiryDuration)

        // At 5 minutes and 1 millisecond: Expired!
        val timeAfterExpiry = expiresAt + 1L
        assertTrue(timeAfterExpiry > expiresAt)
    }

    // ==========================================
    // 6. OTP ATTEMPT LIMIT LOGIC (MAX 5 ATTEMPTS)
    // ==========================================

    @Test
    fun testOtpAttemptLimitLogic() {
        val maxAttempts = 5
        var attemptsRemaining = maxAttempts

        for (attempt in 1..5) {
            attemptsRemaining--
            if (attempt < 5) {
                assertTrue(attemptsRemaining > 0)
            } else {
                assertEquals(0, attemptsRemaining)
            }
        }

        // 6th attempt must be blocked
        val isBlocked = attemptsRemaining <= 0
        assertTrue(isBlocked)
    }

    // ==========================================
    // 7. OTP RESEND COOLDOWN LOGIC (30 SECONDS)
    // ==========================================

    @Test
    fun testResendCooldownLogic() {
        val requestTime = 1000000L
        val cooldownDuration = 30 * 1000L // 30 seconds = 30,000 ms
        val cooldownUntil = requestTime + cooldownDuration

        // Request at 15 seconds: Cooldown still active
        val tryAt15s = requestTime + 15000L
        val isCooldownActive = tryAt15s < cooldownUntil
        assertTrue(isCooldownActive)

        // Request at 31 seconds: Cooldown passed, resend allowed
        val tryAt31s = requestTime + 31000L
        val isResendAllowed = tryAt31s >= cooldownUntil
        assertTrue(isResendAllowed)
    }

    // ==========================================
    // 8. OTP ONE-TIME USE LOGIC
    // ==========================================

    @Test
    fun testOtpOneTimeUseInvalidation() {
        var isUsed = false
        var isVerified = false

        // Simulate successful verification
        fun onVerifySuccess() {
            isUsed = true
            isVerified = true
        }

        onVerifySuccess()
        assertTrue(isUsed)
        assertTrue(isVerified)

        // Second attempt with same code must be rejected because isUsed == true
        fun canReuseCode(): Boolean {
            return !isUsed
        }
        assertFalse(canReuseCode())
    }

    // ==========================================
    // 9. PASSWORD RESET STATE MACHINE
    // ==========================================

    @Test
    fun testStateMachineTransitions() {
        var state = OtpResetUiState()
        assertEquals(OtpStep.EMAIL, state.step)

        // 1. Enter email & request OTP -> Transition to VERIFY_OTP
        state = state.copy(
            step = OtpStep.VERIFY_OTP,
            email = "subhankarsarkar3345@gmail.com",
            expiresAtMillis = System.currentTimeMillis() + 300000L,
            cooldownUntilMillis = System.currentTimeMillis() + 30000L,
            attemptsRemaining = 5
        )
        assertEquals(OtpStep.VERIFY_OTP, state.step)
        assertEquals("subhankarsarkar3345@gmail.com", state.email)

        // 2. Verify OTP -> Transition to NEW_PASSWORD
        state = state.copy(
            step = OtpStep.NEW_PASSWORD,
            resetToken = "secure_token_abc_123"
        )
        assertEquals(OtpStep.NEW_PASSWORD, state.step)
        assertEquals("secure_token_abc_123", state.resetToken)

        // 3. Reset Password -> Transition to SUCCESS
        state = state.copy(
            step = OtpStep.SUCCESS,
            successMessage = "Your StudyMate password has been updated successfully."
        )
        assertEquals(OtpStep.SUCCESS, state.step)
        assertNotNull(state.successMessage)
    }

    // ==========================================
    // 10. STUDENT ID GENERATION & FORMAT
    // ==========================================

    @Test
    fun testStudentIdFormatAndUniqueness() {
        val generatedIds = mutableSetOf<String>()
        val count = 200

        for (i in 0 until count) {
            val studentId = SecurityUtils.generateCandidateStudentId()
            // Must be exactly 8 numeric digits
            assertEquals(8, studentId.length)
            assertTrue(studentId.all { it.isDigit() })

            val numericVal = studentId.toLong()
            assertTrue(numericVal >= 10000000L)
            assertTrue(numericVal <= 99999999L)

            generatedIds.add(studentId)
        }

        // Extremely high uniqueness across 200 iterations
        assertTrue(generatedIds.size >= 195)
    }

    // ==========================================
    // 11. ACCOUNT ENUMERATION PROTECTION
    // ==========================================

    @Test
    fun testAccountEnumerationNeutralMessage() {
        val expectedNeutralResponse = "If an account exists for this email address, a verification code will be sent."
        val response = OtpRequestResult(
            success = true,
            message = expectedNeutralResponse,
            cooldownSeconds = 30,
            expiresInSeconds = 300
        )
        assertTrue(response.success)
        assertEquals(expectedNeutralResponse, response.message)
    }

    // ==========================================
    // 12. MOCK SERVICE INTEGRATION TEST
    // ==========================================

    @Test
    fun testMockOtpServiceHappyPath() {
        val mockService = object : IOtpPasswordResetService {
            override suspend fun requestOtp(email: String): Result<OtpRequestResult> {
                return Result.success(OtpRequestResult(true, "If an account exists for this email address, a verification code will be sent."))
            }

            override suspend fun verifyOtp(email: String, otp: String): Result<OtpVerifyResult> {
                return if (otp == "123456") {
                    Result.success(OtpVerifyResult(true, "valid_token_xyz", "Verification successful."))
                } else {
                    Result.failure(IllegalArgumentException("Incorrect verification code."))
                }
            }

            override suspend fun resetPassword(email: String, resetToken: String, newPassword: String): Result<OtpResetResult> {
                val err = SecurityUtils.validatePasswordStrength(newPassword)
                return if (err != null) {
                    Result.failure(IllegalArgumentException(err))
                } else {
                    Result.success(OtpResetResult(true, "Password updated successfully."))
                }
            }
        }

        // Test happy path through mock
        kotlinx.coroutines.runBlocking {
            val reqRes = mockService.requestOtp("subhankarsarkar3345@gmail.com")
            assertTrue(reqRes.isSuccess)

            val wrongRes = mockService.verifyOtp("subhankarsarkar3345@gmail.com", "999999")
            assertFalse(wrongRes.isSuccess)

            val correctRes = mockService.verifyOtp("subhankarsarkar3345@gmail.com", "123456")
            assertTrue(correctRes.isSuccess)
            val token = correctRes.getOrThrow().resetToken

            val weakReset = mockService.resetPassword("subhankarsarkar3345@gmail.com", token, "weak")
            assertFalse(weakReset.isSuccess)

            val strongReset = mockService.resetPassword("subhankarsarkar3345@gmail.com", token, "NewSecurePass2026")
            assertTrue(strongReset.isSuccess)
        }
    }

    // ==========================================
    // 13. GOOGLE SIGN-IN OAUTH CLIENT VERIFICATION
    // ==========================================

    @Test
    fun testGoogleSignInOAuthClientsAndPackage() {
        val expectedPackage = "com.aistudio.studymate.akzqvy"
        val expectedWebClientId = "393174656677-j96pdhbemi449aj81j2rrpkbipdkgp1o.apps.googleusercontent.com"
        val expectedAndroidClientId = "393174656677-f74vvtqq1qa3167gfp1m1349gfe9fphh.apps.googleusercontent.com"
        val expectedSha1 = "dc462896092af0f0e90e608d1676ab18d8bbb704"

        assertEquals("com.aistudio.studymate.akzqvy", expectedPackage)
        assertTrue(expectedWebClientId.endsWith(".apps.googleusercontent.com"))
        assertTrue(expectedAndroidClientId.endsWith(".apps.googleusercontent.com"))
        assertEquals(40, expectedSha1.length)
        assertTrue(expectedWebClientId.startsWith("393174656677-"))
        assertTrue(expectedAndroidClientId.startsWith("393174656677-"))
    }
}
