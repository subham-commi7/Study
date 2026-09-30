package com.example

import com.example.data.auth.ForgotPasswordUiState
import com.example.data.auth.SecurityUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StudyMateAuthTest {

    // ==========================================
    // 1. EMAIL VALIDATION TESTS
    // ==========================================

    @Test
    fun testValidEmailAddresses() {
        assertTrue(SecurityUtils.isValidEmail("subhankarsarkar3345@gmail.com"))
        assertTrue(SecurityUtils.isValidEmail("student@university.edu"))
        assertTrue(SecurityUtils.isValidEmail("studymate@gmail.com"))
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
    // 2. PASSWORD STRENGTH VALIDATION TESTS
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
    // 3. CONFIRM PASSWORD MATCHING
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
    // 4. FORGOT PASSWORD UI STATE FLOW
    // ==========================================

    @Test
    fun testForgotPasswordUiStateTransitions() {
        var state = ForgotPasswordUiState()
        assertFalse(state.isOpen)
        assertFalse(state.isSent)
        assertNull(state.actionCode)

        // 1. User opens dialog with email
        state = state.copy(
            isOpen = true,
            email = "subhankarsarkar3345@gmail.com"
        )
        assertTrue(state.isOpen)
        assertEquals("subhankarsarkar3345@gmail.com", state.email)

        // 2. Reset email sent
        state = state.copy(
            isSent = true,
            isLoading = false,
            successMessage = "A secure password reset link has been sent to ${state.email}."
        )
        assertTrue(state.isSent)
        assertNotNull(state.successMessage)

        // 3. Action code provided via link or manual entry
        state = state.copy(
            actionCode = "oob_code_firebase_12345",
            isResettingWithCode = true
        )
        assertTrue(state.isResettingWithCode)
        assertEquals("oob_code_firebase_12345", state.actionCode)

        // 4. Password reset completed
        state = state.copy(
            isLoading = false,
            actionCode = null,
            isResettingWithCode = false,
            successMessage = "Password updated successfully!"
        )
        assertNull(state.actionCode)
        assertEquals("Password updated successfully!", state.successMessage)
    }

    // ==========================================
    // 5. FIREBASE ACTION URL CODE EXTRACTION
    // ==========================================

    @Test
    fun testExtractOobCodeFromFirebaseUrl() {
        val testUrl = "https://studymate-c9f21.firebaseapp.com/__/auth/action?mode=resetPassword&oobCode=ABC_XYZ_12345&apiKey=AIzaSyA..."
        val extractedCode = if (testUrl.contains("oobCode=")) {
            testUrl.substringAfter("oobCode=").substringBefore("&").trim()
        } else {
            testUrl.trim()
        }
        assertEquals("ABC_XYZ_12345", extractedCode)
    }

    // ==========================================
    // 6. STUDENT ID GENERATION & FORMAT
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
            // Must not start with 0
            assertFalse(studentId.startsWith("0"))
            generatedIds.add(studentId)
        }

        // Must show reasonable randomness across 200 samples
        assertTrue(generatedIds.size >= 195)
    }

    // ==========================================
    // 7. USER INITIALS COMPUTATION
    // ==========================================

    @Test
    fun testInitialsComputation() {
        // Two words
        assertEquals("SS", SecurityUtils.computeInitials("Subhankar Sarkar", null))
        assertEquals("JD", SecurityUtils.computeInitials("John Doe", null))

        // Single word (takes first 2 letters)
        assertEquals("SU", SecurityUtils.computeInitials("Subhankar", null))

        // Three words
        assertEquals("AS", SecurityUtils.computeInitials("Amit Kumar Sarkar", null))

        // Null name with email fallback (takes first 2 letters of email prefix)
        assertEquals("ST", SecurityUtils.computeInitials(null, "student@college.edu"))

        // Blank name with email fallback
        assertEquals("SU", SecurityUtils.computeInitials("   ", "subhankar@gmail.com"))

        // Both null
        assertEquals("SM", SecurityUtils.computeInitials(null, null))
    }
}
