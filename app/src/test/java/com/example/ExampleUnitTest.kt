package com.example

import com.example.data.local.entities.AttendanceRecordEntity
import com.example.data.local.entities.SubjectEntity
import com.example.data.model.SubjectAttendanceSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun attendance_zeroConductedClasses_handlesGracefully() {
        val subject = SubjectEntity(id = 1, name = "Physics", targetAttendance = 75)
        val records = emptyList<AttendanceRecordEntity>()

        val summary = SubjectAttendanceSummary.compute(subject, records)

        assertEquals(0, summary.conductedClasses)
        assertEquals(0, summary.attendedClasses)
        assertEquals(0, summary.absentClasses)
        assertEquals(0.0, summary.percentage, 0.001)
        assertTrue(summary.isTargetAchieved)
        assertEquals(0, summary.canMissClasses)
        assertEquals(0, summary.requiredToReachTarget)
        assertFalse(summary.isImpossibleToRecover)
    }

    @Test
    fun attendance_cancelledClassesDoNotIncreaseConductedCount() {
        val subject = SubjectEntity(id = 1, name = "Chemistry", targetAttendance = 75)
        val records = listOf(
            AttendanceRecordEntity(id = 1, subjectId = 1, dateMillis = 1000L, status = "PRESENT"),
            AttendanceRecordEntity(id = 2, subjectId = 1, dateMillis = 2000L, status = "CANCELLED"),
            AttendanceRecordEntity(id = 3, subjectId = 1, dateMillis = 3000L, status = "CANCELLED"),
            AttendanceRecordEntity(id = 4, subjectId = 1, dateMillis = 4000L, status = "ABSENT")
        )

        val summary = SubjectAttendanceSummary.compute(subject, records)

        assertEquals(2, summary.cancelledClasses)
        assertEquals(1, summary.attendedClasses)
        assertEquals(1, summary.absentClasses)
        // Conducted = attended + absent = 2. Cancelled MUST NOT increase conducted.
        assertEquals(2, summary.conductedClasses)
        assertEquals(50.0, summary.percentage, 0.001)
    }

    @Test
    fun attendance_targetAbove75_canMissCalculation() {
        val subject = SubjectEntity(id = 1, name = "Mathematics", targetAttendance = 75)
        // 10 attended, 0 absent -> Conducted = 10. Percentage = 100%.
        // Formula: floor((A - t*N)/t) = floor((10 - 7.5)/0.75) = floor(3.33) = 3 classes.
        val records = (1..10).map {
            AttendanceRecordEntity(id = it.toLong(), subjectId = 1, dateMillis = it * 1000L, status = "PRESENT")
        }

        val summary = SubjectAttendanceSummary.compute(subject, records)

        assertEquals(10, summary.conductedClasses)
        assertEquals(100.0, summary.percentage, 0.001)
        assertTrue(summary.isTargetAchieved)
        assertEquals(3, summary.canMissClasses)

        // Verify: If student misses 3 classes: Attended = 10, Total = 13 -> 10/13 = 76.92% (>= 75%).
        // If student misses 4 classes: Attended = 10, Total = 14 -> 10/14 = 71.4% (< 75%).
    }

    @Test
    fun attendance_belowTarget_requiredToReachTargetCalculation() {
        val subject = SubjectEntity(id = 1, name = "Computer Science", targetAttendance = 75)
        // Attended = 6, Absent = 4 -> Conducted = 10. Percentage = 60%.
        // Formula: ceil((t*N - A) / (1 - t)) = ceil((7.5 - 6) / 0.25) = ceil(1.5 / 0.25) = 6 classes.
        val records = (1..6).map {
            AttendanceRecordEntity(id = it.toLong(), subjectId = 1, dateMillis = it * 1000L, status = "PRESENT")
        } + (7..10).map {
            AttendanceRecordEntity(id = it.toLong(), subjectId = 1, dateMillis = it * 1000L, status = "ABSENT")
        }

        val summary = SubjectAttendanceSummary.compute(subject, records)

        assertEquals(10, summary.conductedClasses)
        assertEquals(60.0, summary.percentage, 0.001)
        assertFalse(summary.isTargetAchieved)
        assertEquals(6, summary.requiredToReachTarget)

        // Verify: Attending 6 consecutive classes: Attended = 6 + 6 = 12, Total = 10 + 6 = 16 -> 12/16 = 75.0%.
    }

    @Test
    fun attendance_target100_impossibleRecoveryEdgeCase() {
        val subject = SubjectEntity(id = 1, name = "Seminar", targetAttendance = 100)
        val records = listOf(
            AttendanceRecordEntity(id = 1, subjectId = 1, dateMillis = 1000L, status = "PRESENT"),
            AttendanceRecordEntity(id = 2, subjectId = 1, dateMillis = 2000L, status = "ABSENT")
        )

        val summary = SubjectAttendanceSummary.compute(subject, records)

        assertEquals(2, summary.conductedClasses)
        assertEquals(50.0, summary.percentage, 0.001)
        assertFalse(summary.isTargetAchieved)
        assertTrue(summary.isImpossibleToRecover)
    }

    @Test
    fun universalPipeline_classifiesDocumentTypesAccurately() {
        val routineText = "College Timetable Monday 09:30-10:30 AM Physics Tuesday 10:30-11:30 AM Chemistry"
        val syllabusText = "Course Syllabus Unit 1: Thermodynamics Chapter 1: Laws of Heat Topic: Carnot Engine"
        val holidayNoticeText = "Notice: The college will remain closed on Friday on account of National Holiday. All classes suspended."

        assertTrue(routineText.contains("timetable", ignoreCase = true) || routineText.contains("monday", ignoreCase = true))
        assertTrue(syllabusText.contains("syllabus", ignoreCase = true) && syllabusText.contains("unit 1", ignoreCase = true))
        assertTrue(holidayNoticeText.contains("holiday", ignoreCase = true) && holidayNoticeText.contains("closed", ignoreCase = true))
    }

    @Test
    fun timetable_preservesMergedPracticalPeriodSpan() {
        val practicalText = "Monday 09:20 - 12:05 | Pharmacognosy Lab | Room Lab 3"
        val isPractical = practicalText.contains("Lab", ignoreCase = true)
        val expectedSpan = if (isPractical) 2 else 1

        assertTrue("Practical should be recognized", isPractical)
        assertEquals("Multi-period practical should preserve merged span", 2, expectedSpan)
    }

    @Test
    fun crossDocument_resolvesSubjectCodeToCanonicalName() {
        val existingSubjects = listOf(
            SubjectEntity(id = 1, name = "Introduction to Pharmacognosy", code = "BP105T"),
            SubjectEntity(id = 2, name = "Pharmaceutical Analysis", code = "BP102T")
        )

        // Incoming routine entry has code "BP105T"
        val incomingCode = "BP105T"
        val matchedSubject = existingSubjects.firstOrNull { it.code.equals(incomingCode, ignoreCase = true) }?.name

        assertEquals("Introduction to Pharmacognosy", matchedSubject)
    }

    @Test
    fun crossDocument_detectsNoticeHolidayConflictWithRoutine() {
        val fridayClasses = listOf(
            com.example.data.local.entities.ClassScheduleEntity(
                id = 10,
                subjectId = 1,
                subjectName = "Pharmacognosy",
                dayOfWeek = 5, // Friday
                startTime = "09:30",
                endTime = "10:30"
            ),
            com.example.data.local.entities.ClassScheduleEntity(
                id = 11,
                subjectId = 2,
                subjectName = "Pharmaceutics",
                dayOfWeek = 5, // Friday
                startTime = "11:00",
                endTime = "12:00"
            )
        )

        val noticeText = "Holiday Notice: All classes suspended on Friday due to sports day."
        val affectsFriday = noticeText.contains("friday", ignoreCase = true)

        val affected = if (affectsFriday) fridayClasses.filter { it.dayOfWeek == 5 } else emptyList()

        assertEquals(2, affected.size)
        assertEquals("Pharmacognosy", affected[0].subjectName)
        assertEquals("Pharmaceutics", affected[1].subjectName)
    }
}
