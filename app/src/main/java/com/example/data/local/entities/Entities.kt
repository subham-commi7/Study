package com.example.data.local.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "subjects")
data class SubjectEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val code: String = "",
    val colorHex: String = "#1E40AF",
    val targetAttendance: Int = 75
)

@Entity(
    tableName = "class_schedules",
    indices = [Index(value = ["dayOfWeek", "startTime"])]
)
data class ClassScheduleEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val subjectId: Long,
    val subjectName: String,
    val dayOfWeek: Int, // 1 = Monday, 2 = Tuesday, ..., 7 = Sunday
    val startTime: String, // HH:mm format e.g. "09:30"
    val endTime: String,   // HH:mm format e.g. "10:30"
    val room: String = "",
    val teacher: String = "",
    val groupSection: String = "",
    val routineVersionId: Long = 1L,
    val reminderEnabled: Boolean = true
)

@Entity(
    tableName = "attendance_records",
    indices = [
        Index(value = ["subjectId", "dateMillis"]),
        Index(value = ["subjectId", "dateString"])
    ]
)
data class AttendanceRecordEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val subjectId: Long,
    val dateMillis: Long,
    val dateString: String = "", // "YYYY-MM-DD"
    val status: String, // "PRESENT", "ABSENT", "CANCELLED"
    val notes: String = "",
    val isLocked: Boolean = true
)

@Entity(
    tableName = "holiday_exceptions",
    indices = [Index(value = ["dateString"])]
)
data class HolidayExceptionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val dateString: String, // "YYYY-MM-DD"
    val dayOfWeek: Int, // 1 = Monday, 2 = Tuesday, ..., 7 = Sunday
    val reason: String,
    val isFullDay: Boolean = true,
    val affectedSubjectId: Long? = null,
    val noticeId: Long? = null,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "syllabus_topics",
    indices = [
        Index(value = ["subjectId", "unitName"]),
        Index(value = ["course", "semester"]),
        Index(value = ["subjectName", "topicName"])
    ]
)
data class SyllabusTopicEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val subjectId: Long,
    val subjectName: String,
    val subjectCode: String = "",
    val course: String = "",
    val semester: String = "",
    val unitName: String,
    val chapterName: String = "",
    val topicName: String,
    val subtopic: String = "",
    val isTrackable: Boolean = true,
    val isReferenceOnly: Boolean = false,
    val sourceDocumentId: Long? = null,
    val sourcePage: Int = 1,
    val status: String = "NOT_STARTED" // "NOT_STARTED", "IN_PROGRESS", "COMPLETED"
)

@Entity(tableName = "documents")
data class DocumentEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val type: String, // "ROUTINE", "SYLLABUS", "NOTICE", "NOTES", "ASSIGNMENT", "OTHER"
    val subjectName: String = "",
    val filePath: String,
    val fileName: String,
    val mimeType: String,
    val fileSize: Long,
    val createdAt: Long = System.currentTimeMillis(),
    val contentSummary: String = ""
)

@Entity(tableName = "notices")
data class NoticeEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val issueDate: String = "",
    val sourceFact: String,
    val explanation: String,
    val hasRoutineChange: Boolean = false,
    val proposedChangeJson: String = "",
    val changeApplied: Boolean = false,
    val deadlineDate: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "academic_tasks")
data class AcademicTaskEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val subjectName: String,
    val title: String,
    val type: String, // "ASSIGNMENT", "EXAM", "DEADLINE"
    val dueDate: String, // YYYY-MM-DD
    val dueTime: String = "",
    val instructions: String = "",
    val isCompleted: Boolean = false
)

@Entity(tableName = "app_settings")
data class AppSettingsEntity(
    @PrimaryKey
    val key: String,
    val value: String
)

@Entity(
    tableName = "users",
    indices = [
        Index(value = ["email"], unique = true),
        Index(value = ["studentId"], unique = true)
    ]
)
data class UserEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val studentId: String = "", // 8-digit unique ID e.g. "48271635"
    val fullName: String,
    val email: String,
    val passwordHash: String,
    val salt: String,
    val college: String = "",
    val course: String = "",
    val semester: String = "",
    val year: String = "",
    val groupSection: String = "",
    val reminderMinutesBefore: Int = 10,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "friendships",
    indices = [
        Index(value = ["userStudentId", "friendStudentId"], unique = true),
        Index(value = ["friendStudentId"])
    ]
)
data class FriendshipEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val userStudentId: String,
    val friendStudentId: String,
    val friendName: String,
    val friendCollege: String = "",
    val friendCourse: String = "",
    val status: String = "PENDING", // "PENDING", "ACCEPTED", "DECLINED", "BLOCKED"
    val isInitiator: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "friend_chat_messages",
    indices = [
        Index(value = ["conversationKey", "timestamp"]),
        Index(value = ["receiverStudentId", "isRead"])
    ]
)
data class FriendChatMessageEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val conversationKey: String, // sorted "id1_id2" to identify thread
    val senderStudentId: String,
    val receiverStudentId: String,
    val senderName: String = "",
    val messageText: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isRead: Boolean = false,
    val attachmentDocumentId: Long? = null,
    val attachmentFileName: String = "",
    val attachmentMimeType: String = "",
    val attachmentFilePath: String = ""
)

@Entity(
    tableName = "shared_documents",
    indices = [
        Index(value = ["recipientStudentId"]),
        Index(value = ["senderStudentId"])
    ]
)
data class SharedDocumentEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val documentId: Long,
    val senderStudentId: String,
    val senderName: String,
    val recipientStudentId: String,
    val documentTitle: String,
    val documentType: String,
    val fileName: String,
    val filePath: String,
    val mimeType: String,
    val fileSize: Long,
    val sharedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "ai_actions",
    indices = [Index(value = ["timestamp"])]
)
data class AIActionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val actionType: String, // "MARK_SYLLABUS_TOPIC_COMPLETED", "CREATE_ATTENDANCE_ENTRY", etc.
    val subjectName: String = "",
    val topicName: String = "",
    val summary: String,
    val source: String = "AI Assistant Chat",
    val timestamp: Long = System.currentTimeMillis(),
    val details: String = ""
)

@Entity(
    tableName = "routine_versions",
    indices = [Index(value = ["versionNumber"])]
)
data class RoutineVersionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val versionNumber: Int = 1,
    val versionName: String = "Version 1",
    val effectiveDate: String = "", // "YYYY-MM-DD"
    val groupSection: String = "",
    val isActive: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)
