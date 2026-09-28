package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.local.entities.AcademicTaskEntity
import com.example.data.local.entities.AIActionEntity
import com.example.data.local.entities.AppSettingsEntity
import com.example.data.local.entities.AttendanceRecordEntity
import com.example.data.local.entities.ClassScheduleEntity
import com.example.data.local.entities.DocumentEntity
import com.example.data.local.entities.FriendChatMessageEntity
import com.example.data.local.entities.FriendshipEntity
import com.example.data.local.entities.HolidayExceptionEntity
import com.example.data.local.entities.NoticeEntity
import com.example.data.local.entities.RoutineVersionEntity
import com.example.data.local.entities.SharedDocumentEntity
import com.example.data.local.entities.SubjectEntity
import com.example.data.local.entities.SyllabusTopicEntity
import com.example.data.local.entities.UserEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SubjectDao {
    @Query("SELECT * FROM subjects ORDER BY name ASC")
    fun getAllSubjects(): Flow<List<SubjectEntity>>

    @Query("SELECT * FROM subjects WHERE id = :id LIMIT 1")
    suspend fun getSubjectById(id: Long): SubjectEntity?

    @Query("SELECT * FROM subjects WHERE LOWER(name) = LOWER(:name) LIMIT 1")
    suspend fun getSubjectByName(name: String): SubjectEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSubject(subject: SubjectEntity): Long

    @Update
    suspend fun updateSubject(subject: SubjectEntity)

    @Delete
    suspend fun deleteSubject(subject: SubjectEntity)
}

@Dao
interface RoutineDao {
    @Query("SELECT * FROM class_schedules ORDER BY dayOfWeek ASC, startTime ASC")
    fun getAllSchedules(): Flow<List<ClassScheduleEntity>>

    @Query("SELECT * FROM class_schedules WHERE dayOfWeek = :dayOfWeek ORDER BY startTime ASC")
    fun getSchedulesForDay(dayOfWeek: Int): Flow<List<ClassScheduleEntity>>

    @Query("SELECT * FROM class_schedules WHERE id = :id LIMIT 1")
    suspend fun getScheduleById(id: Long): ClassScheduleEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSchedule(schedule: ClassScheduleEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSchedules(schedules: List<ClassScheduleEntity>)

    @Update
    suspend fun updateSchedule(schedule: ClassScheduleEntity)

    @Delete
    suspend fun deleteSchedule(schedule: ClassScheduleEntity)

    @Query("DELETE FROM class_schedules WHERE id = :id")
    suspend fun deleteScheduleById(id: Long)

    @Query("DELETE FROM class_schedules")
    suspend fun clearAll()
}

@Dao
interface AttendanceDao {
    @Query("SELECT * FROM attendance_records ORDER BY dateMillis DESC")
    fun getAllRecords(): Flow<List<AttendanceRecordEntity>>

    @Query("SELECT * FROM attendance_records WHERE subjectId = :subjectId ORDER BY dateMillis DESC")
    fun getRecordsForSubject(subjectId: Long): Flow<List<AttendanceRecordEntity>>

    @Query("SELECT * FROM attendance_records WHERE id = :id LIMIT 1")
    suspend fun getRecordById(id: Long): AttendanceRecordEntity?

    @Query("SELECT * FROM attendance_records WHERE subjectId = :subjectId AND dateString = :dateString LIMIT 1")
    suspend fun getRecordForSubjectAndDate(subjectId: Long, dateString: String): AttendanceRecordEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecord(record: AttendanceRecordEntity): Long

    @Update
    suspend fun updateRecord(record: AttendanceRecordEntity)

    @Delete
    suspend fun deleteRecord(record: AttendanceRecordEntity)

    @Query("DELETE FROM attendance_records WHERE id = :id")
    suspend fun deleteRecordById(id: Long)

    @Query("DELETE FROM attendance_records WHERE subjectId = :subjectId")
    suspend fun clearRecordsForSubject(subjectId: Long)
}

@Dao
interface HolidayExceptionDao {
    @Query("SELECT * FROM holiday_exceptions ORDER BY dateString ASC")
    fun getAllExceptions(): Flow<List<HolidayExceptionEntity>>

    @Query("SELECT * FROM holiday_exceptions WHERE dateString = :dateString LIMIT 1")
    suspend fun getExceptionForDate(dateString: String): HolidayExceptionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertException(exception: HolidayExceptionEntity): Long

    @Delete
    suspend fun deleteException(exception: HolidayExceptionEntity)

    @Query("DELETE FROM holiday_exceptions WHERE id = :id")
    suspend fun deleteExceptionById(id: Long)

    @Query("DELETE FROM holiday_exceptions")
    suspend fun clearAllExceptions()
}

@Dao
interface SyllabusDao {
    @Query("SELECT * FROM syllabus_topics ORDER BY subjectName ASC, unitName ASC, chapterName ASC, topicName ASC")
    fun getAllTopics(): Flow<List<SyllabusTopicEntity>>

    @Query("SELECT * FROM syllabus_topics WHERE subjectId = :subjectId ORDER BY unitName ASC, chapterName ASC, topicName ASC")
    fun getTopicsForSubject(subjectId: Long): Flow<List<SyllabusTopicEntity>>

    @Query("SELECT * FROM syllabus_topics WHERE subjectName = :subjectName ORDER BY unitName ASC, chapterName ASC, topicName ASC")
    fun getTopicsForSubjectName(subjectName: String): Flow<List<SyllabusTopicEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTopic(topic: SyllabusTopicEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTopics(topics: List<SyllabusTopicEntity>)

    @Update
    suspend fun updateTopic(topic: SyllabusTopicEntity)

    @Delete
    suspend fun deleteTopic(topic: SyllabusTopicEntity)

    @Query("DELETE FROM syllabus_topics WHERE id = :id")
    suspend fun deleteTopicById(id: Long)

    @Query("UPDATE syllabus_topics SET status = :status WHERE id = :id")
    suspend fun updateTopicStatus(id: Long, status: String)
}

@Dao
interface DocumentDao {
    @Query("SELECT * FROM documents ORDER BY createdAt DESC")
    fun getAllDocuments(): Flow<List<DocumentEntity>>

    @Query("SELECT * FROM documents WHERE type = :type ORDER BY createdAt DESC")
    fun getDocumentsByType(type: String): Flow<List<DocumentEntity>>

    @Query("SELECT * FROM documents WHERE id = :id LIMIT 1")
    suspend fun getDocumentById(id: Long): DocumentEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDocument(document: DocumentEntity): Long

    @Delete
    suspend fun deleteDocument(document: DocumentEntity)

    @Query("DELETE FROM documents WHERE id = :id")
    suspend fun deleteDocumentById(id: Long)
}

@Dao
interface NoticeDao {
    @Query("SELECT * FROM notices ORDER BY createdAt DESC")
    fun getAllNotices(): Flow<List<NoticeEntity>>

    @Query("SELECT * FROM notices WHERE id = :id LIMIT 1")
    suspend fun getNoticeById(id: Long): NoticeEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNotice(notice: NoticeEntity): Long

    @Update
    suspend fun updateNotice(notice: NoticeEntity)

    @Delete
    suspend fun deleteNotice(notice: NoticeEntity)

    @Query("UPDATE notices SET changeApplied = :applied WHERE id = :id")
    suspend fun markChangeApplied(id: Long, applied: Boolean)
}

@Dao
interface TaskDao {
    @Query("SELECT * FROM academic_tasks ORDER BY dueDate ASC, dueTime ASC")
    fun getAllTasks(): Flow<List<AcademicTaskEntity>>

    @Query("SELECT * FROM academic_tasks WHERE isCompleted = 0 ORDER BY dueDate ASC, dueTime ASC")
    fun getPendingTasks(): Flow<List<AcademicTaskEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTask(task: AcademicTaskEntity): Long

    @Update
    suspend fun updateTask(task: AcademicTaskEntity)

    @Delete
    suspend fun deleteTask(task: AcademicTaskEntity)

    @Query("UPDATE academic_tasks SET isCompleted = :completed WHERE id = :id")
    suspend fun updateTaskCompletion(id: Long, completed: Boolean)
}

@Dao
interface SettingsDao {
    @Query("SELECT value FROM app_settings WHERE `key` = :key LIMIT 1")
    suspend fun getValue(key: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun setValue(setting: AppSettingsEntity)
}

@Dao
interface UserDao {
    @Query("SELECT * FROM users WHERE LOWER(email) = LOWER(:email) LIMIT 1")
    suspend fun getUserByEmail(email: String): UserEntity?

    @Query("SELECT * FROM users WHERE id = :id LIMIT 1")
    suspend fun getUserById(id: Long): UserEntity?

    @Query("SELECT * FROM users WHERE studentId = :studentId LIMIT 1")
    suspend fun getUserByStudentId(studentId: String): UserEntity?

    @Query("SELECT COUNT(*) FROM users WHERE studentId = :studentId")
    suspend fun countByStudentId(studentId: String): Int

    @Query("SELECT * FROM users WHERE studentId != :excludeStudentId AND (studentId LIKE '%' || :query || '%' OR LOWER(fullName) LIKE '%' || LOWER(:query) || '%')")
    suspend fun searchStudents(query: String, excludeStudentId: String): List<UserEntity>

    @Query("SELECT * FROM users ORDER BY id ASC LIMIT 1")
    suspend fun getFirstUser(): UserEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: UserEntity): Long

    @Update
    suspend fun updateUser(user: UserEntity)

    @Query("DELETE FROM users")
    suspend fun clearUsers()
}

@Dao
interface FriendshipDao {
    @Query("SELECT * FROM friendships WHERE userStudentId = :studentId ORDER BY updatedAt DESC")
    fun getFriendshipsForUser(studentId: String): Flow<List<FriendshipEntity>>

    @Query("SELECT * FROM friendships WHERE userStudentId = :studentId AND status = 'ACCEPTED' ORDER BY friendName ASC")
    fun getAcceptedFriends(studentId: String): Flow<List<FriendshipEntity>>

    @Query("SELECT * FROM friendships WHERE userStudentId = :studentId AND status = 'PENDING' ORDER BY createdAt DESC")
    fun getPendingRequests(studentId: String): Flow<List<FriendshipEntity>>

    @Query("SELECT * FROM friendships WHERE (userStudentId = :userStudentId AND friendStudentId = :friendStudentId) LIMIT 1")
    suspend fun findFriendship(userStudentId: String, friendStudentId: String): FriendshipEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(friendship: FriendshipEntity): Long

    @Query("UPDATE friendships SET status = :status, updatedAt = :updatedAt WHERE (userStudentId = :userStudentId AND friendStudentId = :friendStudentId) OR (userStudentId = :friendStudentId AND friendStudentId = :userStudentId)")
    suspend fun updateStatusBothWays(userStudentId: String, friendStudentId: String, status: String, updatedAt: Long = System.currentTimeMillis())

    @Query("DELETE FROM friendships WHERE (userStudentId = :userStudentId AND friendStudentId = :friendStudentId) OR (userStudentId = :friendStudentId AND friendStudentId = :userStudentId)")
    suspend fun removeFriendshipBothWays(userStudentId: String, friendStudentId: String)
}

@Dao
interface FriendChatDao {
    @Query("SELECT * FROM friend_chat_messages WHERE conversationKey = :conversationKey ORDER BY timestamp ASC")
    fun getMessagesForConversation(conversationKey: String): Flow<List<FriendChatMessageEntity>>

    @Query("SELECT * FROM friend_chat_messages WHERE receiverStudentId = :studentId AND isRead = 0")
    fun getUnreadMessages(studentId: String): Flow<List<FriendChatMessageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: FriendChatMessageEntity): Long

    @Query("UPDATE friend_chat_messages SET isRead = 1 WHERE conversationKey = :conversationKey AND receiverStudentId = :studentId")
    suspend fun markMessagesAsRead(conversationKey: String, studentId: String)
}

@Dao
interface SharedDocumentDao {
    @Query("SELECT * FROM shared_documents WHERE recipientStudentId = :studentId ORDER BY sharedAt DESC")
    fun getDocumentsSharedWithUser(studentId: String): Flow<List<SharedDocumentEntity>>

    @Query("SELECT * FROM shared_documents WHERE senderStudentId = :studentId ORDER BY sharedAt DESC")
    fun getDocumentsSharedByUser(studentId: String): Flow<List<SharedDocumentEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSharedDocument(shared: SharedDocumentEntity): Long

    @Query("DELETE FROM shared_documents WHERE id = :id")
    suspend fun deleteSharedDocument(id: Long)
}

@Dao
interface AIActionDao {
    @Query("SELECT * FROM ai_actions ORDER BY timestamp DESC LIMIT 50")
    fun getRecentActions(): Flow<List<AIActionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAction(action: AIActionEntity): Long

    @Query("DELETE FROM ai_actions")
    suspend fun clearHistory()
}

@Dao
interface RoutineVersionDao {
    @Query("SELECT * FROM routine_versions ORDER BY versionNumber DESC")
    fun getAllVersions(): Flow<List<RoutineVersionEntity>>

    @Query("SELECT * FROM routine_versions WHERE isActive = 1 LIMIT 1")
    suspend fun getActiveVersion(): RoutineVersionEntity?

    @Query("SELECT MAX(versionNumber) FROM routine_versions")
    suspend fun getMaxVersionNumber(): Int?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVersion(version: RoutineVersionEntity): Long

    @Query("UPDATE routine_versions SET isActive = 0")
    suspend fun deactivateAllVersions()

    @Query("UPDATE routine_versions SET isActive = 1 WHERE id = :id")
    suspend fun setActiveVersion(id: Long)
}

