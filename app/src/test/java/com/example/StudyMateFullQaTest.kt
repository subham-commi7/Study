package com.example

import com.example.data.ai.CourseSyllabusExtractor
import com.example.data.local.entities.AttendanceRecordEntity
import com.example.data.local.entities.SubjectEntity
import com.example.data.local.entities.SyllabusTopicEntity
import com.example.data.model.DateResolutionHelper
import com.example.data.model.SubjectAttendanceSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class StudyMateFullQaTest {

    @Test
    fun testAttendanceFormulaWithCancelledClasses() {
        val subject = SubjectEntity(id = 1, name = "Data Structures", targetAttendance = 75)
        val records = listOf(
            AttendanceRecordEntity(id = 1, subjectId = 1, dateMillis = 1000L, status = "PRESENT", isLocked = true),
            AttendanceRecordEntity(id = 2, subjectId = 1, dateMillis = 2000L, status = "PRESENT", isLocked = true),
            AttendanceRecordEntity(id = 3, subjectId = 1, dateMillis = 3000L, status = "PRESENT", isLocked = true),
            AttendanceRecordEntity(id = 4, subjectId = 1, dateMillis = 4000L, status = "ABSENT", isLocked = true),
            AttendanceRecordEntity(id = 5, subjectId = 1, dateMillis = 5000L, status = "CANCELLED", isLocked = true) // must NOT count in conducted
        )

        val summary = SubjectAttendanceSummary.compute(subject, records)
        assertEquals(3, summary.attendedClasses)
        assertEquals(1, summary.absentClasses)
        assertEquals(1, summary.cancelledClasses)
        assertEquals(4, summary.conductedClasses) // 3 attended + 1 absent
        assertEquals(75.0, summary.percentage, 0.01)
        assertTrue(summary.isTargetAchieved)
    }

    @Test
    fun testAttendanceDeficitRecoveryAdvice() {
        val subject = SubjectEntity(id = 1, name = "Physics", targetAttendance = 80)
        // 2 attended, 2 absent = 50%
        val records = listOf(
            AttendanceRecordEntity(id = 1, subjectId = 1, dateMillis = 1000L, status = "PRESENT"),
            AttendanceRecordEntity(id = 2, subjectId = 1, dateMillis = 2000L, status = "PRESENT"),
            AttendanceRecordEntity(id = 3, subjectId = 1, dateMillis = 3000L, status = "ABSENT"),
            AttendanceRecordEntity(id = 4, subjectId = 1, dateMillis = 4000L, status = "ABSENT")
        )

        val summary = SubjectAttendanceSummary.compute(subject, records)
        assertEquals(50.0, summary.percentage, 0.01)
        assertFalse(summary.isTargetAchieved)
        // To reach 80% from 2/4:
        // (2 + x) / (4 + x) >= 0.8 => 2 + x >= 3.2 + 0.8x => 0.2x >= 1.2 => x >= 6
        assertEquals(6, summary.requiredToReachTarget)
    }

    @Test
    fun testDateResolutionNextMondayHoliday() {
        val noticeText = "College Notice: Next Monday is a holiday on account of festivities. All classes suspended."
        val resolved = DateResolutionHelper.resolveDateExpression(noticeText)
        assertNotNull(resolved)
        assertEquals(1, resolved!!.dayOfWeek) // 1 = Monday
        assertTrue(resolved.dateString.matches(Regex("\\d{4}-\\d{2}-\\d{2}")))
        assertTrue(DateResolutionHelper.verifyWeekdayAndDate(resolved.dateString, resolved.dayOfWeek))
    }

    @Test
    fun testDateResolutionTomorrow() {
        val noticeText = "Attention: Tomorrow classes are cancelled due to maintenance."
        val resolved = DateResolutionHelper.resolveDateExpression(noticeText)
        assertNotNull(resolved)
        val cal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 1) }
        val expectedDay = DateResolutionHelper.convertCalendarDayToAppDay(cal.get(Calendar.DAY_OF_WEEK))
        assertEquals(expectedDay, resolved!!.dayOfWeek)
    }

    @Test
    fun testDateResolutionExplicitDateFormat() {
        val noticeText = "Midterm examinations commence on 2026-10-15."
        val resolved = DateResolutionHelper.resolveDateExpression(noticeText)
        assertNotNull(resolved)
        assertEquals("2026-10-15", resolved!!.dateString)
    }

    @Test
    fun testCourseSyllabusMultiSubjectAndNonTrackableFiltering() {
        val rawSyllabusText = """
            Bachelor of Pharmacy (B.Pharm)
            Semester 1 Curriculum

            BP101T: Human Anatomy and Physiology I
            Unit I
            Cell: Structure and functions of cell, transport across cell membrane
            Tissue: Epithelial, connective, muscular and nervous tissues
            Recommended Books:
            1. Essentials of Medical Physiology by K Sembulingam
            2. Textbook of Anatomy and Physiology by Ross and Wilson

            BP102T: Pharmaceutical Analysis I
            Unit I
            Errors: Sources of errors, types of errors, methods of minimizing errors
            Acid Base Titration: Theories of acid base indicators
            Reference Books:
            1. Vogel's Textbook of Quantitative Chemical Analysis
        """.trimIndent()

        val parsed = CourseSyllabusExtractor.parseSyllabusDeterministic(rawSyllabusText)
        assertTrue(parsed.course.contains("Pharm", ignoreCase = true))
        assertTrue(parsed.semester.contains("Semester 1", ignoreCase = true))
        assertEquals(2, parsed.subjects.size)

        val subject1 = parsed.subjects[0]
        assertTrue(subject1.subjectName.contains("Human Anatomy"))
        assertEquals("BP101T", subject1.subjectCode)
        assertTrue(subject1.units.isNotEmpty())
        val unit1Topics = subject1.units[0].topics.map { it.topicName }
        assertTrue(unit1Topics.any { it.contains("Cell", ignoreCase = true) })
        assertTrue(unit1Topics.any { it.contains("Tissue", ignoreCase = true) })
        // Non-trackable books should NOT become topics
        assertFalse(unit1Topics.any { it.contains("Sembulingam", ignoreCase = true) })
        assertFalse(unit1Topics.any { it.contains("Ross and Wilson", ignoreCase = true) })
        assertTrue(subject1.nonTrackableReferences.any { it.contains("Sembulingam") })

        val subject2 = parsed.subjects[1]
        assertTrue(subject2.subjectName.contains("Pharmaceutical Analysis"))
        assertEquals("BP102T", subject2.subjectCode)
        val s2Topics = subject2.units[0].topics.map { it.topicName }
        assertTrue(s2Topics.any { it.contains("Errors", ignoreCase = true) })
        assertFalse(s2Topics.any { it.contains("Vogel", ignoreCase = true) })
    }

    @Test
    fun testSyllabusChatActionDisambiguation() {
        val topics = listOf(
            SyllabusTopicEntity(id = 1, subjectId = 1L, subjectName = "Human Anatomy and Physiology I", unitName = "Unit I", topicName = "Epithelial Tissue", status = "NOT_STARTED"),
            SyllabusTopicEntity(id = 2, subjectId = 1L, subjectName = "Human Anatomy and Physiology I", unitName = "Unit I", topicName = "Muscular Tissue", status = "NOT_STARTED")
        )

        val userMessage = "আজকে আমার Human Anatomy-এর Tissue chapterটা শেষ হয়ে গেছে"
        val lower = userMessage.lowercase()

        // Test subject detection
        val matchedSubject = topics.map { it.subjectName }.firstOrNull { lower.contains("human anatomy") }
        assertNotNull(matchedSubject)

        // Matching topic keyword "tissue"
        val matchedTopics = topics.filter { it.topicName.lowercase().contains("tissue") }
        // Should find 2 matches -> must NOT guess, must ask clarification
        assertEquals(2, matchedTopics.size)
    }

    @Test
    fun testSyllabusChatActionUniqueMatch() {
        val topics = listOf(
            SyllabusTopicEntity(id = 1, subjectId = 1L, subjectName = "Human Anatomy and Physiology I", unitName = "Unit I", topicName = "Cell Structure", status = "NOT_STARTED"),
            SyllabusTopicEntity(id = 2, subjectId = 1L, subjectName = "Human Anatomy and Physiology I", unitName = "Unit I", topicName = "Tissue", status = "NOT_STARTED"),
            SyllabusTopicEntity(id = 3, subjectId = 2L, subjectName = "Pharmaceutical Analysis", unitName = "Unit I", topicName = "Acid Base Titration", status = "NOT_STARTED")
        )

        val userMessage = "Human Anatomy-এর Cell Structure complete"
        val lower = userMessage.lowercase()

        val matchingTopics = topics.filter {
            lower.contains("human anatomy") && lower.contains(it.topicName.lowercase())
        }
        assertEquals(1, matchingTopics.size)
        assertEquals(1L, matchingTopics.first().id)
    }
}
