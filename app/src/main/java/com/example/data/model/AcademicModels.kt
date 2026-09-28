package com.example.data.model

import com.example.data.local.entities.AcademicTaskEntity
import com.example.data.local.entities.AttendanceRecordEntity
import com.example.data.local.entities.ClassScheduleEntity
import com.example.data.local.entities.DocumentEntity
import com.example.data.local.entities.NoticeEntity
import com.example.data.local.entities.SubjectEntity
import com.example.data.local.entities.SyllabusTopicEntity
import kotlin.math.ceil
import kotlin.math.floor

data class SubjectAttendanceSummary(
    val subject: SubjectEntity,
    val attendedClasses: Int,
    val absentClasses: Int,
    val cancelledClasses: Int,
    val conductedClasses: Int,
    val percentage: Double,
    val canMissClasses: Int,
    val requiredToReachTarget: Int,
    val isImpossibleToRecover: Boolean,
    val isTargetAchieved: Boolean
) {
    companion object {
        fun compute(
            subject: SubjectEntity,
            records: List<AttendanceRecordEntity>
        ): SubjectAttendanceSummary {
            var attended = 0
            var absent = 0
            var cancelled = 0

            for (record in records) {
                when (record.status.uppercase()) {
                    "PRESENT" -> attended++
                    "ABSENT" -> absent++
                    "CANCELLED" -> cancelled++
                }
            }

            val conducted = attended + absent
            val targetPercent = subject.targetAttendance.coerceIn(1, 100)
            val t = targetPercent / 100.0

            val percentage = if (conducted > 0) {
                (attended.toDouble() / conducted.toDouble()) * 100.0
            } else {
                0.0
            }

            val isTargetAchieved = conducted == 0 || percentage >= targetPercent
            var canMiss = 0
            var required = 0
            var impossible = false

            if (conducted == 0) {
                canMiss = 0
                required = 0
            } else if (percentage >= targetPercent) {
                if (targetPercent > 0) {
                    val num = 100 * attended - targetPercent * conducted
                    canMiss = (num / targetPercent).coerceAtLeast(0)
                }
            } else {
                if (targetPercent >= 100) {
                    impossible = true
                } else {
                    val num = targetPercent * conducted - 100 * attended
                    val den = 100 - targetPercent
                    required = ceil(num.toDouble() / den.toDouble()).toInt().coerceAtLeast(1)
                }
            }

            return SubjectAttendanceSummary(
                subject = subject,
                attendedClasses = attended,
                absentClasses = absent,
                cancelledClasses = cancelled,
                conductedClasses = conducted,
                percentage = percentage,
                canMissClasses = canMiss,
                requiredToReachTarget = required,
                isImpossibleToRecover = impossible,
                isTargetAchieved = isTargetAchieved
            )
        }
    }
}

data class NextClassInfo(
    val schedule: ClassScheduleEntity,
    val startsInMinutes: Long,
    val isHappeningNow: Boolean
)

enum class DocumentType(val displayName: String) {
    ROUTINE("Routine / Timetable"),
    SYLLABUS("Syllabus / Curriculum"),
    NOTICE("College Notice / Circular"),
    HOLIDAY_NOTICE("Holiday Announcement"),
    EXAM_SCHEDULE("Exam Schedule"),
    ASSIGNMENT("Assignment Notice"),
    NOTES("Subject Notes / Study Material"),
    QUESTION_PAPER("Question Paper"),
    PRACTICAL_SCHEDULE("Practical / Lab Schedule"),
    ACADEMIC_CALENDAR("Academic Calendar"),
    ATTENDANCE_DOCUMENT("Attendance Record"),
    OTHER("Other Academic Document")
}

data class ExtractedRoutineItem(
    val dayOfWeek: Int,
    val dayName: String,
    val startTime: String,
    val endTime: String,
    val subject: String,
    val subjectCode: String = "",
    val teacher: String = "",
    val room: String = "",
    val activityType: String = "Theory", // Theory, Practical, Tutorial, Break, Lunch
    val mergedSpan: Int = 1,
    val confidence: String = "HIGH", // HIGH, MEDIUM, LOW
    val sourceInfo: String = "",
    val needsReview: Boolean = false,
    val reviewReason: String = ""
)

data class ExtractedSyllabusTopic(
    val subject: String,
    val subjectCode: String = "",
    val course: String = "",
    val semester: String = "",
    val unit: String,
    val chapter: String = "",
    val topic: String,
    val subtopic: String = "",
    val weightage: String = "",
    val classification: String = "Theory",
    val confidence: String = "HIGH",
    val sourceInfo: String = "",
    val sourcePage: Int = 1,
    val isTrackable: Boolean = true,
    val isReferenceOnly: Boolean = false,
    val isUnresolved: Boolean = false,
    val needsReview: Boolean = false
)

data class CourseSyllabusPackage(
    val course: String = "",
    val semester: String = "",
    val subjects: List<ExtractedSubjectSyllabus> = emptyList(),
    val unresolvedItems: List<ExtractedTopicItem> = emptyList(),
    val sourceFileName: String = ""
)

data class ExtractedSubjectSyllabus(
    val subjectName: String,
    val subjectCode: String = "",
    val units: List<ExtractedUnit> = emptyList(),
    val nonTrackableReferences: List<String> = emptyList()
)

data class ExtractedUnit(
    val unitName: String,
    val topics: List<ExtractedTopicItem> = emptyList()
)

data class ExtractedTopicItem(
    val id: String = java.util.UUID.randomUUID().toString(),
    val chapterName: String = "",
    val topicName: String,
    val subtopics: List<String> = emptyList(),
    val sourcePage: Int = 1,
    val isTrackable: Boolean = true,
    val isReferenceOnly: Boolean = false,
    val isUnresolved: Boolean = false,
    val confidence: String = "HIGH"
)

data class ExtractedNoticeData(
    val title: String,
    val noticeType: String = "GENERAL", // HOLIDAY, CANCELLATION, RESCHEDULE, EXAM, ASSIGNMENT, GENERAL
    val issueDate: String = "",
    val institution: String = "",
    val affectedDate: String = "",
    val affectedSubject: String = "",
    val sourceFacts: String,
    val simpleExplanation: String,
    val importantDates: List<String> = emptyList(),
    val deadlines: List<String> = emptyList(),
    val isHoliday: Boolean = false,
    val holidayDetails: String = "",
    val routineChangeDetected: Boolean = false,
    val routineChangeSummary: String = "",
    val proposedChanges: List<ExtractedRoutineItem> = emptyList(),
    val affectedRoutineClasses: List<ClassScheduleEntity> = emptyList(),
    val confidence: String = "HIGH",
    val sourceInfo: String = ""
)

data class ExtractedNotesData(
    val subject: String,
    val chapter: String = "",
    val topic: String = "",
    val summary: String,
    val definitions: List<Pair<String, String>> = emptyList(),
    val formulas: List<String> = emptyList(),
    val keyConcepts: List<String> = emptyList(),
    val questionsAndAnswers: List<Pair<String, String>> = emptyList(),
    val revisionBulletPoints: List<String> = emptyList(),
    val sourceInfo: String = ""
)

data class ScheduleConflict(
    val existingClass: ClassScheduleEntity,
    val conflictingClass: ExtractedRoutineItem,
    val conflictReason: String
)

data class UniversalDocumentResult(
    val docType: DocumentType,
    val confidence: String = "HIGH", // HIGH, MEDIUM, LOW
    val title: String,
    val fileName: String,
    val fileSize: Long,
    val mimeType: String,
    val pageCount: Int = 1,
    val routineItems: List<ExtractedRoutineItem>? = null,
    val syllabusTopics: List<ExtractedSyllabusTopic>? = null,
    val noticeData: ExtractedNoticeData? = null,
    val notesData: ExtractedNotesData? = null,
    val rawText: String = "",
    val requiresUserTypeConfirmation: Boolean = false,
    val persistentFilePath: String? = null,
    val sourceUriString: String? = null,
    val detectedDuplicate: DocumentEntity? = null,
    val detectedConflicts: List<ScheduleConflict> = emptyList(),
    val crossDocumentRelations: List<String> = emptyList()
)

data class SearchResultData(
    val classes: List<ClassScheduleEntity> = emptyList(),
    val subjects: List<SubjectEntity> = emptyList(),
    val topics: List<SyllabusTopicEntity> = emptyList(),
    val notices: List<NoticeEntity> = emptyList(),
    val tasks: List<AcademicTaskEntity> = emptyList(),
    val documents: List<DocumentEntity> = emptyList(),
    val totalMatches: Int = 0
)
