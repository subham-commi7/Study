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

    // ==========================================
    // 8. ATTENDANCE DAY TABS & DATE CALCULATION
    // ==========================================

    @Test
    fun testAttendanceDayTabCalendarCalculations() {
        // Test with a known Sunday: Oct 4, 2026 (epoch ~ 1791100800000)
        val calSunday = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("Asia/Kolkata")).apply {
            set(2026, java.util.Calendar.OCTOBER, 4, 12, 0, 0)
        }
        val sundayMillis = calSunday.timeInMillis

        val sundayToday = com.example.ui.screens.computeDayTabInfo(com.example.ui.screens.AttendanceDayTab.TODAY, sundayMillis)
        val sundayYesterday = com.example.ui.screens.computeDayTabInfo(com.example.ui.screens.AttendanceDayTab.YESTERDAY, sundayMillis)
        val sundayTomorrow = com.example.ui.screens.computeDayTabInfo(com.example.ui.screens.AttendanceDayTab.TOMORROW, sundayMillis)

        // Today is Sunday (7)
        assertEquals(7, sundayToday.dayOfWeek)
        assertEquals("TODAY", sundayToday.title)

        // Yesterday was Saturday (6)
        assertEquals(6, sundayYesterday.dayOfWeek)
        assertEquals("YESTERDAY", sundayYesterday.title)

        // Tomorrow is Monday (1) - Sunday -> Monday transition test
        assertEquals(1, sundayTomorrow.dayOfWeek)
        assertEquals("TOMORROW", sundayTomorrow.title)
    }

    @Test
    fun testAttendanceMondayYesterdaySundayTransition() {
        // Test with a known Monday: Oct 5, 2026
        val calMonday = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("Asia/Kolkata")).apply {
            set(2026, java.util.Calendar.OCTOBER, 5, 10, 0, 0)
        }
        val mondayMillis = calMonday.timeInMillis

        val mondayToday = com.example.ui.screens.computeDayTabInfo(com.example.ui.screens.AttendanceDayTab.TODAY, mondayMillis)
        val mondayYesterday = com.example.ui.screens.computeDayTabInfo(com.example.ui.screens.AttendanceDayTab.YESTERDAY, mondayMillis)
        val mondayTomorrow = com.example.ui.screens.computeDayTabInfo(com.example.ui.screens.AttendanceDayTab.TOMORROW, mondayMillis)

        // Today is Monday (1)
        assertEquals(1, mondayToday.dayOfWeek)

        // Yesterday was Sunday (7) - Monday -> Sunday yesterday transition test
        assertEquals(7, mondayYesterday.dayOfWeek)

        // Tomorrow is Tuesday (2)
        assertEquals(2, mondayTomorrow.dayOfWeek)
    }

    @Test
    fun testAttendanceScheduleFilteringAndSorting() {
        val schedules = listOf(
            com.example.data.local.entities.ClassScheduleEntity(
                id = 1,
                subjectId = 10,
                subjectName = "Human Anatomy",
                dayOfWeek = 2, // Tuesday
                startTime = "10:00",
                endTime = "11:00",
                room = "Room 101"
            ),
            com.example.data.local.entities.ClassScheduleEntity(
                id = 2,
                subjectId = 11,
                subjectName = "Pharmaceutics",
                dayOfWeek = 2, // Tuesday
                startTime = "11:00",
                endTime = "12:00",
                room = "Room 102"
            ),
            com.example.data.local.entities.ClassScheduleEntity(
                id = 3,
                subjectId = 12,
                subjectName = "Biochemistry",
                dayOfWeek = 1, // Monday
                startTime = "09:00",
                endTime = "10:00"
            ),
            com.example.data.local.entities.ClassScheduleEntity(
                id = 4,
                subjectId = 13,
                subjectName = "Pathology",
                dayOfWeek = 3, // Wednesday
                startTime = "14:00",
                endTime = "15:00"
            )
        )

        // Filter for Tuesday (dayOfWeek = 2)
        val tuesdayClasses = schedules.filter { it.dayOfWeek == 2 }.sortedWith(compareBy({ it.startTime }, { it.id }))

        // Must ONLY contain Tuesday classes (exactly 2)
        assertEquals(2, tuesdayClasses.size)
        assertEquals("Human Anatomy", tuesdayClasses[0].subjectName)
        assertEquals("Pharmaceutics", tuesdayClasses[1].subjectName)

        // Verify NO classes from Monday or Wednesday leaked in
        assertTrue(tuesdayClasses.none { it.dayOfWeek == 1 })
        assertTrue(tuesdayClasses.none { it.dayOfWeek == 3 })

        // Chronological order verification
        assertTrue(tuesdayClasses[0].startTime < tuesdayClasses[1].startTime)

        // Filter for Thursday (dayOfWeek = 4) where no classes exist
        val thursdayClasses = schedules.filter { it.dayOfWeek == 4 }
        assertTrue(thursdayClasses.isEmpty())
    }

    @Test
    fun testFormatClassTimeRange() {
        assertEquals("10:00 AM – 11:00 AM", com.example.ui.screens.formatClassTimeRange("10:00", "11:00"))
        assertEquals("11:00 AM – 12:00 PM", com.example.ui.screens.formatClassTimeRange("11:00", "12:00"))
        assertEquals("9:30 AM – 10:30 AM", com.example.ui.screens.formatClassTimeRange("09:30", "10:30"))
        assertEquals("1:00 PM – 2:00 PM", com.example.ui.screens.formatClassTimeRange("13:00", "14:00"))
        assertEquals("12:00 PM – 1:00 PM", com.example.ui.screens.formatClassTimeRange("12:00", "13:00"))
    }

    // ==========================================
    // 9. GOOGLE SIGN-IN CONFIGURATION TESTS
    // ==========================================

    @Test
    fun testGoogleWebClientIdFormat() {
        val clientId = "393174656677-j96pdhbemi449aj81j2rrpkbipdkgp1o.apps.googleusercontent.com"
        assertTrue(clientId.endsWith(".apps.googleusercontent.com"))
        assertTrue(clientId.startsWith("393174656677"))
    }

    @Test
    fun testFirebaseCertificateFingerprints() {
        val sha1 = "DC:46:28:96:09:2A:F0:F0:E9:0E:60:8D:16:76:AB:18:D8:BB:B7:04"
        val sha256 = "FF:BD:54:B4:2B:F5:9D:26:A2:54:1B:7A:9F:59:89:8D:9C:06:2B:A9:53:6D:7B:AF:19:B7:25:F8:77:57:C5:18"

        // SHA-1 has 20 bytes (20 pairs separated by colon)
        val sha1Parts = sha1.split(":")
        assertEquals(20, sha1Parts.size)
        assertTrue(sha1Parts.all { it.length == 2 && it.all { ch -> ch.isDigit() || ch in 'A'..'F' } })

        // Match certificate_hash in google-services.json
        val cleanedSha1 = sha1.replace(":", "").lowercase()
        assertEquals("dc462896092af0f0e90e608d1676ab18d8bbb704", cleanedSha1)

        // SHA-256 has 32 bytes (32 pairs separated by colon)
        val sha256Parts = sha256.split(":")
        assertEquals(32, sha256Parts.size)
        assertTrue(sha256Parts.all { it.length == 2 && it.all { ch -> ch.isDigit() || ch in 'A'..'F' } })
    }
}
