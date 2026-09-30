package com.example.data.repository

import android.content.Context
import com.example.data.auth.FirebaseAuthManager
import com.example.data.auth.SecurityUtils
import com.example.data.local.AppDatabase
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
import com.example.data.model.DateResolutionHelper
import com.example.data.model.NextClassInfo
import com.example.data.model.SubjectAttendanceSummary
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class StudyMateRepository(
    private val db: AppDatabase,
    private val authManager: FirebaseAuthManager = FirebaseAuthManager()
) {

    // Subjects
    val allSubjects: Flow<List<SubjectEntity>> = db.subjectDao().getAllSubjects()

    suspend fun insertSubject(name: String, code: String = "", targetAttendance: Int = 75, colorHex: String = "#1E40AF"): Long {
        val cleanName = name.trim()
        val existing = db.subjectDao().getSubjectByName(cleanName)
        if (existing != null) {
            return existing.id
        }
        return db.subjectDao().insertSubject(
            SubjectEntity(name = cleanName, code = code.trim(), colorHex = colorHex, targetAttendance = targetAttendance)
        )
    }

    suspend fun updateSubject(subject: SubjectEntity) {
        db.subjectDao().updateSubject(subject)
    }

    suspend fun deleteSubject(subject: SubjectEntity) {
        db.attendanceDao().clearRecordsForSubject(subject.id)
        db.subjectDao().deleteSubject(subject)
    }

    // Routine / Schedules
    val allSchedules: Flow<List<ClassScheduleEntity>> = db.routineDao().getAllSchedules()

    fun getSchedulesForDay(dayOfWeek: Int): Flow<List<ClassScheduleEntity>> {
        return db.routineDao().getSchedulesForDay(dayOfWeek)
    }

    suspend fun insertSchedule(schedule: ClassScheduleEntity): Long {
        return db.routineDao().insertSchedule(schedule)
    }

    suspend fun insertSchedules(schedules: List<ClassScheduleEntity>) {
        for (sch in schedules) {
            val existing = db.routineDao().findExistingSchedule(sch.dayOfWeek, sch.startTime, sch.subjectName)
            if (existing != null) {
                db.routineDao().updateSchedule(
                    existing.copy(
                        endTime = sch.endTime,
                        room = sch.room.ifBlank { existing.room },
                        teacher = sch.teacher.ifBlank { existing.teacher },
                        reminderEnabled = sch.reminderEnabled
                    )
                )
            } else {
                db.routineDao().insertSchedule(sch)
            }
        }
    }

    suspend fun updateSchedule(schedule: ClassScheduleEntity) {
        db.routineDao().updateSchedule(schedule)
    }

    suspend fun deleteSchedule(schedule: ClassScheduleEntity) {
        db.routineDao().deleteSchedule(schedule)
    }

    suspend fun deleteScheduleById(id: Long) {
        db.routineDao().deleteScheduleById(id)
    }

    // Attendance
    val allAttendanceRecords: Flow<List<AttendanceRecordEntity>> = db.attendanceDao().getAllRecords()

    fun getAttendanceRecordsForSubject(subjectId: Long): Flow<List<AttendanceRecordEntity>> {
        return db.attendanceDao().getRecordsForSubject(subjectId)
    }

    suspend fun getAttendanceRecordForSubjectAndDate(subjectId: Long, dateString: String): AttendanceRecordEntity? {
        return db.attendanceDao().getRecordForSubjectAndDate(subjectId, dateString)
    }

    /**
     * Records attendance for a subject.
     * Enforces business lock: attendance can only be submitted for today or yesterday.
     * Once submitted, the record is locked and cannot be overwritten.
     */
    suspend fun markAttendance(
        subjectId: Long,
        status: String,
        targetDateMillis: Long = System.currentTimeMillis(),
        notes: String = ""
    ): Long {
        val calToday = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val calTarget = Calendar.getInstance().apply {
            timeInMillis = targetDateMillis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        val diffDays = ((calToday.timeInMillis - calTarget.timeInMillis) / (24 * 60 * 60 * 1000L)).toInt()
        if (diffDays != 0 && diffDays != 1) {
            throw IllegalStateException("Attendance submission is restricted to today or yesterday.")
        }

        val dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH).format(Date(targetDateMillis))
        val existing = db.attendanceDao().getRecordForSubjectAndDate(subjectId, dateStr)
        if (existing != null && existing.isLocked) {
            throw IllegalStateException("Attendance for this class on $dateStr is already locked and cannot be modified.")
        }

        return db.attendanceDao().insertRecord(
            AttendanceRecordEntity(
                id = existing?.id ?: 0,
                subjectId = subjectId,
                dateMillis = targetDateMillis,
                dateString = dateStr,
                status = status,
                notes = notes,
                isLocked = true
            )
        )
    }

    suspend fun deleteAttendanceRecord(record: AttendanceRecordEntity) {
        if (record.isLocked) {
            throw IllegalStateException("Locked attendance record cannot be deleted.")
        }
        db.attendanceDao().deleteRecord(record)
    }

    // Holiday Exceptions & Date-specific cancellations
    val allHolidayExceptions: Flow<List<HolidayExceptionEntity>> = db.holidayExceptionDao().getAllExceptions()

    suspend fun getHolidayExceptionForDate(dateString: String): HolidayExceptionEntity? {
        return db.holidayExceptionDao().getExceptionForDate(dateString)
    }

    suspend fun insertHolidayException(exception: HolidayExceptionEntity): Long {
        return db.holidayExceptionDao().insertException(exception)
    }

    suspend fun deleteHolidayException(id: Long) {
        db.holidayExceptionDao().deleteExceptionById(id)
    }

    suspend fun clearHolidayExceptions() {
        db.holidayExceptionDao().clearAllExceptions()
    }

    // Combined Attendance Summaries Flow
    val attendanceSummaries: Flow<List<SubjectAttendanceSummary>> = combine(
        allSubjects,
        allAttendanceRecords
    ) { subjects, records ->
        val recordMap = records.groupBy { it.subjectId }
        subjects.map { subject ->
            val subjectRecords = recordMap[subject.id] ?: emptyList()
            SubjectAttendanceSummary.compute(subject, subjectRecords)
        }
    }

    // Syllabus
    val allSyllabusTopics: Flow<List<SyllabusTopicEntity>> = db.syllabusDao().getAllTopics()

    fun getTopicsForSubject(subjectId: Long): Flow<List<SyllabusTopicEntity>> {
        return db.syllabusDao().getTopicsForSubject(subjectId)
    }

    suspend fun insertTopic(topic: SyllabusTopicEntity): Long {
        return db.syllabusDao().insertTopic(topic)
    }

    suspend fun insertTopics(topics: List<SyllabusTopicEntity>) {
        for (topic in topics) {
            val existing = db.syllabusDao().findExistingTopic(topic.subjectId, topic.topicName)
            if (existing != null) {
                db.syllabusDao().updateTopic(
                    existing.copy(
                        unitName = topic.unitName.ifBlank { existing.unitName },
                        chapterName = topic.chapterName.ifBlank { existing.chapterName },
                        subtopic = topic.subtopic.ifBlank { existing.subtopic },
                        subjectCode = topic.subjectCode.ifBlank { existing.subjectCode },
                        course = topic.course.ifBlank { existing.course },
                        semester = topic.semester.ifBlank { existing.semester },
                        isTrackable = topic.isTrackable,
                        isReferenceOnly = topic.isReferenceOnly
                    )
                )
            } else {
                db.syllabusDao().insertTopic(topic)
            }
        }
    }

    suspend fun updateTopic(topic: SyllabusTopicEntity) {
        db.syllabusDao().updateTopic(topic)
    }

    suspend fun updateTopicStatus(id: Long, status: String) {
        db.syllabusDao().updateTopicStatus(id, status)
    }

    suspend fun deleteTopic(topic: SyllabusTopicEntity) {
        db.syllabusDao().deleteTopic(topic)
    }

    // Documents
    val allDocuments: Flow<List<DocumentEntity>> = db.documentDao().getAllDocuments()

    fun getDocumentsByType(type: String): Flow<List<DocumentEntity>> {
        return db.documentDao().getDocumentsByType(type)
    }

    suspend fun insertDocument(document: DocumentEntity): Long {
        return db.documentDao().insertDocument(document)
    }

    suspend fun deleteDocument(document: DocumentEntity) {
        db.documentDao().deleteDocument(document)
    }

    // Notices
    val allNotices: Flow<List<NoticeEntity>> = db.noticeDao().getAllNotices()

    suspend fun insertNotice(notice: NoticeEntity): Long {
        return db.noticeDao().insertNotice(notice)
    }

    suspend fun markNoticeChangeApplied(id: Long, applied: Boolean) {
        db.noticeDao().markChangeApplied(id, applied)
    }

    suspend fun deleteNotice(notice: NoticeEntity) {
        db.noticeDao().deleteNotice(notice)
    }

    // Tasks / Deadlines
    val allTasks: Flow<List<AcademicTaskEntity>> = db.taskDao().getAllTasks()
    val pendingTasks: Flow<List<AcademicTaskEntity>> = db.taskDao().getPendingTasks()

    suspend fun insertTask(task: AcademicTaskEntity): Long {
        return db.taskDao().insertTask(task)
    }

    suspend fun updateTaskCompletion(id: Long, completed: Boolean) {
        db.taskDao().updateTaskCompletion(id, completed)
    }

    suspend fun deleteTask(task: AcademicTaskEntity) {
        db.taskDao().deleteTask(task)
    }

    // Helper: Determine next class today
    fun findNextClass(todayClasses: List<ClassScheduleEntity>): NextClassInfo? {
        if (todayClasses.isEmpty()) return null

        val now = Calendar.getInstance()
        val currentMinutes = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)

        for (cls in todayClasses) {
            val startParts = cls.startTime.split(":")
            val endParts = cls.endTime.split(":")
            if (startParts.size >= 2) {
                val startM = (startParts[0].toIntOrNull() ?: 0) * 60 + (startParts[1].toIntOrNull() ?: 0)
                val endM = if (endParts.size >= 2) {
                    (endParts[0].toIntOrNull() ?: 0) * 60 + (endParts[1].toIntOrNull() ?: 0)
                } else {
                    startM + 60
                }

                if (currentMinutes in startM..endM) {
                    return NextClassInfo(cls, startsInMinutes = 0, isHappeningNow = true)
                }
                if (startM > currentMinutes) {
                    return NextClassInfo(cls, startsInMinutes = (startM - currentMinutes).toLong(), isHappeningNow = false)
                }
            }
        }
        return null
    }

    // Search
    suspend fun searchAll(query: String): SearchResults {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return SearchResults()

        // We can safely search from current flows or queries
        return SearchResults(query = q)
    }

    // ==========================================
    // AUTHENTICATION & PROFILE METHODS
    // ==========================================

    companion object {
        const val KEY_LOGGED_IN_USER_ID = "KEY_LOGGED_IN_USER_ID"
        const val KEY_IS_AUTHENTICATED = "KEY_IS_AUTHENTICATED"
        const val KEY_ACADEMIC_SETUP_DONE = "KEY_ACADEMIC_SETUP_DONE"
        const val KEY_REMINDER_MINUTES = "KEY_REMINDER_MINUTES"
    }

    suspend fun registerUser(
        fullName: String,
        email: String,
        password: String
    ): Result<UserEntity> {
        val trimmedName = fullName.trim()
        val trimmedEmail = email.trim().lowercase()

        if (trimmedName.isEmpty()) {
            return Result.failure(IllegalArgumentException("Please enter your full name."))
        }
        if (!SecurityUtils.isValidEmail(trimmedEmail)) {
            return Result.failure(IllegalArgumentException("Please enter a valid email address."))
        }
        val pwdError = SecurityUtils.validatePasswordStrength(password)
        if (pwdError != null) {
            return Result.failure(IllegalArgumentException(pwdError))
        }

        val firebaseResult = authManager.createUserWithEmailAndPassword(trimmedEmail, password, trimmedName)
        if (firebaseResult.isFailure) {
            return Result.failure(firebaseResult.exceptionOrNull() ?: Exception("Registration failed."))
        }

        var localUser = db.userDao().getUserByEmail(trimmedEmail)
        if (localUser == null) {
            val studentId = generateUniqueStudentId()
            val newUser = UserEntity(
                studentId = studentId,
                fullName = trimmedName,
                email = trimmedEmail,
                passwordHash = "",
                salt = ""
            )
            val id = db.userDao().insertUser(newUser)
            localUser = newUser.copy(id = id)
        }

        setSession(localUser.id, isAuthenticated = true, setupDone = false)
        return Result.success(localUser)
    }

    suspend fun generateUniqueStudentId(): String {
        var studentId: String
        var attempts = 0
        do {
            studentId = SecurityUtils.generateCandidateStudentId()
            val existing = db.userDao().countByStudentId(studentId)
            attempts++
        } while (existing > 0 && attempts < 100)
        return studentId
    }

    suspend fun loginUser(email: String, password: String): Result<UserEntity> {
        val trimmedEmail = email.trim().lowercase()
        if (trimmedEmail.isEmpty()) {
            return Result.failure(IllegalArgumentException("Please enter your email."))
        }
        if (password.isEmpty()) {
            return Result.failure(IllegalArgumentException("Please enter your password."))
        }

        val firebaseResult = authManager.signInWithEmailAndPassword(trimmedEmail, password)
        if (firebaseResult.isFailure) {
            return Result.failure(firebaseResult.exceptionOrNull() ?: Exception("Login failed."))
        }
        val fbUser = firebaseResult.getOrThrow()

        var localUser = db.userDao().getUserByEmail(trimmedEmail)
        if (localUser == null) {
            val studentId = generateUniqueStudentId()
            val displayName = fbUser.displayName?.ifBlank { null } ?: trimmedEmail.substringBefore("@")
            val newUser = UserEntity(
                studentId = studentId,
                fullName = displayName,
                email = trimmedEmail,
                passwordHash = "",
                salt = ""
            )
            val id = db.userDao().insertUser(newUser)
            localUser = newUser.copy(id = id)
        }

        val setupDone = localUser.college.isNotBlank() || localUser.course.isNotBlank()
        setSession(localUser.id, isAuthenticated = true, setupDone = setupDone)
        return Result.success(localUser)
    }

    suspend fun signInWithGoogle(context: Context): Result<UserEntity> {
        val serverClientId = getGoogleWebClientId(context)
        val firebaseResult = authManager.signInWithGoogle(context, serverClientId)
        if (firebaseResult.isFailure) {
            return Result.failure(firebaseResult.exceptionOrNull() ?: Exception("Google sign in failed."))
        }
        val fbUser = firebaseResult.getOrThrow()
        val email = fbUser.email?.lowercase() ?: ""
        val photoUrl = fbUser.photoUrl?.toString() ?: ""
        var localUser = if (email.isNotBlank()) db.userDao().getUserByEmail(email) else null
        if (localUser == null) {
            val studentId = generateUniqueStudentId()
            val displayName = fbUser.displayName?.ifBlank { null } ?: (if (email.isNotBlank()) email.substringBefore("@") else "Student")
            val newUser = UserEntity(
                studentId = studentId,
                fullName = displayName,
                email = email,
                passwordHash = "",
                salt = "",
                photoUrl = photoUrl
            )
            val id = db.userDao().insertUser(newUser)
            localUser = newUser.copy(id = id)
        } else {
            // Update photo or display name if available from Google
            var updated = false
            var userToUpdate = localUser
            if (photoUrl.isNotBlank() && localUser.photoUrl != photoUrl) {
                userToUpdate = userToUpdate.copy(photoUrl = photoUrl)
                updated = true
            }
            if (fbUser.displayName != null && fbUser.displayName!!.isNotBlank() && localUser.fullName.isBlank()) {
                userToUpdate = userToUpdate.copy(fullName = fbUser.displayName!!)
                updated = true
            }
            if (updated) {
                db.userDao().updateUser(userToUpdate)
                localUser = userToUpdate
            }
        }
        val setupDone = localUser.college.isNotBlank() || localUser.course.isNotBlank()
        setSession(localUser.id, isAuthenticated = true, setupDone = setupDone)
        return Result.success(localUser)
    }

    fun getGoogleWebClientId(context: Context): String {
        return try {
            val prefId = context.getSharedPreferences("studymate_prefs", Context.MODE_PRIVATE)
                .getString("google_web_client_id", null)?.trim()
            if (!prefId.isNullOrBlank()) return prefId
            val fromR = context.getString(com.example.R.string.default_web_client_id).trim()
            if (fromR.isNotBlank()) return fromR
            val resId = context.resources.getIdentifier("default_web_client_id", "string", context.packageName)
            if (resId != 0) {
                val str = context.getString(resId).trim()
                if (str.isNotBlank()) return str
            }
            ""
        } catch (_: Exception) {
            ""
        }
    }

    fun saveGoogleWebClientId(context: Context, clientId: String) {
        try {
            context.getSharedPreferences("studymate_prefs", Context.MODE_PRIVATE)
                .edit()
                .putString("google_web_client_id", clientId.trim())
                .apply()
        } catch (_: Exception) {}
    }

    suspend fun sendPasswordResetEmail(email: String): Result<Unit> {
        val trimmedEmail = email.trim().lowercase()
        if (trimmedEmail.isEmpty()) {
            return Result.failure(IllegalArgumentException("Please enter your registered email address."))
        }
        if (!SecurityUtils.isValidEmail(trimmedEmail)) {
            return Result.failure(IllegalArgumentException("Please enter a valid email address."))
        }
        return authManager.sendPasswordResetEmail(trimmedEmail)
    }

    suspend fun confirmPasswordReset(actionCode: String, newPassword: String): Result<Unit> {
        return authManager.confirmPasswordReset(actionCode, newPassword)
    }

    suspend fun verifyPasswordResetCode(actionCode: String): Result<String> {
        return authManager.verifyPasswordResetCode(actionCode)
    }

    suspend fun sendEmailVerification(): Result<Unit> {
        return authManager.sendEmailVerification()
    }

    suspend fun reloadUserAndCheckVerified(): Result<Boolean> {
        val reloadResult = authManager.reloadUser()
        return if (reloadResult.isSuccess) {
            Result.success(reloadResult.getOrThrow().isEmailVerified)
        } else {
            Result.failure(reloadResult.exceptionOrNull() ?: Exception("Failed to refresh user state."))
        }
    }

    suspend fun applyActionCode(actionCode: String): Result<Unit> {
        return authManager.applyActionCode(actionCode)
    }

    suspend fun getCurrentUser(): UserEntity? {
        val userIdStr = db.settingsDao().getValue(KEY_LOGGED_IN_USER_ID)
        val userId = userIdStr?.toLongOrNull()
        var user = if (userId != null) db.userDao().getUserById(userId) else null

        if (user == null) {
            val fbUser = authManager.currentUser
            if (fbUser != null && !fbUser.email.isNullOrBlank()) {
                val email = fbUser.email!!.lowercase()
                user = db.userDao().getUserByEmail(email)
                if (user == null) {
                    val studentId = generateUniqueStudentId()
                    val newUser = UserEntity(
                        studentId = studentId,
                        fullName = fbUser.displayName ?: email.substringBefore("@"),
                        email = email,
                        passwordHash = "",
                        salt = ""
                    )
                    val id = db.userDao().insertUser(newUser)
                    user = newUser.copy(id = id)
                }
                setSession(user.id, isAuthenticated = true, setupDone = user.college.isNotBlank())
            }
        }

        if (user != null && user.studentId.isBlank()) {
            val assignedId = generateUniqueStudentId()
            val updated = user.copy(studentId = assignedId)
            db.userDao().updateUser(updated)
            return updated
        }
        return user
    }

    suspend fun isUserAuthenticated(): Boolean {
        val fbUser = authManager.currentUser
        if (fbUser != null) {
            val user = getCurrentUser()
            return user != null
        }
        val isAuth = db.settingsDao().getValue(KEY_IS_AUTHENTICATED)
        return isAuth == "true" && getCurrentUser() != null
    }

    suspend fun isAcademicSetupDone(): Boolean {
        val done = db.settingsDao().getValue(KEY_ACADEMIC_SETUP_DONE)
        return done == "true"
    }

    suspend fun updateUserProfile(
        college: String,
        course: String,
        semester: String,
        year: String,
        groupSection: String,
        reminderMinutes: Int? = null,
        fullName: String? = null
    ): Result<UserEntity> {
        val user = getCurrentUser() ?: return Result.failure(IllegalStateException("No logged in user."))
        val updated = user.copy(
            fullName = if (!fullName.isNullOrBlank()) fullName.trim() else user.fullName,
            college = college.trim(),
            course = course.trim(),
            semester = semester.trim(),
            year = year.trim(),
            groupSection = groupSection.trim(),
            reminderMinutesBefore = reminderMinutes ?: user.reminderMinutesBefore
        )
        db.userDao().updateUser(updated)
        db.settingsDao().setValue(AppSettingsEntity(KEY_ACADEMIC_SETUP_DONE, "true"))
        if (reminderMinutes != null) {
            db.settingsDao().setValue(AppSettingsEntity(KEY_REMINDER_MINUTES, reminderMinutes.toString()))
        }
        return Result.success(updated)
    }

    suspend fun updateReminderPreference(minutes: Int) {
        db.settingsDao().setValue(AppSettingsEntity(KEY_REMINDER_MINUTES, minutes.toString()))
        val user = getCurrentUser()
        if (user != null) {
            db.userDao().updateUser(user.copy(reminderMinutesBefore = minutes))
        }
    }

    suspend fun getReminderPreference(): Int {
        val pref = db.settingsDao().getValue(KEY_REMINDER_MINUTES)
        return pref?.toIntOrNull() ?: 10
    }

    suspend fun logout() {
        authManager.signOut()
        db.settingsDao().setValue(AppSettingsEntity(KEY_IS_AUTHENTICATED, "false"))
        db.settingsDao().setValue(AppSettingsEntity(KEY_LOGGED_IN_USER_ID, ""))
    }

    private suspend fun setSession(userId: Long, isAuthenticated: Boolean, setupDone: Boolean) {
        db.settingsDao().setValue(AppSettingsEntity(KEY_LOGGED_IN_USER_ID, userId.toString()))
        db.settingsDao().setValue(AppSettingsEntity(KEY_IS_AUTHENTICATED, if (isAuthenticated) "true" else "false"))
        db.settingsDao().setValue(AppSettingsEntity(KEY_ACADEMIC_SETUP_DONE, if (setupDone) "true" else "false"))
    }

    // ==========================================
    // COLLABORATION: STUDENT ID & FRIEND REQUESTS
    // ==========================================
    suspend fun searchStudentByStudentId(studentId: String): UserEntity? {
        val clean = studentId.trim()
        if (clean.length != 8) return null
        return db.userDao().getUserByStudentId(clean)
    }

    suspend fun searchStudents(query: String, currentStudentId: String): List<UserEntity> {
        val clean = query.trim()
        if (clean.isEmpty()) return emptyList()
        return db.userDao().searchStudents(clean, currentStudentId)
    }

    fun getAcceptedFriends(studentId: String): Flow<List<FriendshipEntity>> {
        return db.friendshipDao().getAcceptedFriends(studentId)
    }

    fun getPendingRequests(studentId: String): Flow<List<FriendshipEntity>> {
        return db.friendshipDao().getPendingRequests(studentId)
    }

    suspend fun sendFriendRequest(
        currentUser: UserEntity,
        targetStudentId: String
    ): Result<String> {
        val cleanTarget = targetStudentId.trim()
        if (cleanTarget == currentUser.studentId) {
            return Result.failure(IllegalArgumentException("You cannot send a friend request to yourself."))
        }
        val targetUser = db.userDao().getUserByStudentId(cleanTarget)
            ?: return Result.failure(IllegalArgumentException("No student found with Student ID $cleanTarget."))

        val existing = db.friendshipDao().findFriendship(currentUser.studentId, cleanTarget)
        if (existing != null) {
            return when (existing.status) {
                "ACCEPTED" -> Result.failure(IllegalStateException("You are already connected with ${existing.friendName}."))
                "PENDING" -> Result.failure(IllegalStateException("A friend request is already pending with this student."))
                "BLOCKED" -> Result.failure(IllegalStateException("Connection is not available."))
                else -> {
                    // Update to PENDING
                    db.friendshipDao().insertOrUpdate(existing.copy(status = "PENDING", updatedAt = System.currentTimeMillis()))
                    Result.success("Friend request sent to ${targetUser.fullName}.")
                }
            }
        }

        // Create mutual friendship records:
        // Record 1: from current user's perspective
        db.friendshipDao().insertOrUpdate(
            FriendshipEntity(
                userStudentId = currentUser.studentId,
                friendStudentId = targetUser.studentId,
                friendName = targetUser.fullName,
                friendCollege = targetUser.college,
                friendCourse = targetUser.course,
                status = "PENDING",
                isInitiator = true
            )
        )
        // Record 2: from target user's perspective (incoming request)
        db.friendshipDao().insertOrUpdate(
            FriendshipEntity(
                userStudentId = targetUser.studentId,
                friendStudentId = currentUser.studentId,
                friendName = currentUser.fullName,
                friendCollege = currentUser.college,
                friendCourse = currentUser.course,
                status = "PENDING",
                isInitiator = false
            )
        )

        return Result.success("Friend request sent to ${targetUser.fullName}.")
    }

    suspend fun acceptFriendRequest(userStudentId: String, friendStudentId: String) {
        db.friendshipDao().updateStatusBothWays(userStudentId, friendStudentId, "ACCEPTED")
    }

    suspend fun declineFriendRequest(userStudentId: String, friendStudentId: String) {
        db.friendshipDao().updateStatusBothWays(userStudentId, friendStudentId, "DECLINED")
    }

    suspend fun removeFriend(userStudentId: String, friendStudentId: String) {
        db.friendshipDao().removeFriendshipBothWays(userStudentId, friendStudentId)
    }

    // ==========================================
    // ACADEMIC FRIEND CHAT & ATTACHMENTS
    // ==========================================
    fun getFriendChatMessages(studentA: String, studentB: String): Flow<List<FriendChatMessageEntity>> {
        val key = if (studentA < studentB) "${studentA}_${studentB}" else "${studentB}_${studentA}"
        return db.friendChatDao().getMessagesForConversation(key)
    }

    suspend fun sendFriendChatMessage(
        senderStudentId: String,
        receiverStudentId: String,
        senderName: String,
        text: String,
        attachmentDoc: DocumentEntity? = null
    ): Long {
        val key = if (senderStudentId < receiverStudentId) "${senderStudentId}_${receiverStudentId}" else "${receiverStudentId}_${senderStudentId}"
        return db.friendChatDao().insertMessage(
            FriendChatMessageEntity(
                conversationKey = key,
                senderStudentId = senderStudentId,
                receiverStudentId = receiverStudentId,
                senderName = senderName,
                messageText = text.trim(),
                attachmentDocumentId = attachmentDoc?.id,
                attachmentFileName = attachmentDoc?.fileName ?: "",
                attachmentMimeType = attachmentDoc?.mimeType ?: "",
                attachmentFilePath = attachmentDoc?.filePath ?: ""
            )
        )
    }

    suspend fun markChatAsRead(studentA: String, studentB: String) {
        val key = if (studentA < studentB) "${studentA}_${studentB}" else "${studentB}_${studentA}"
        db.friendChatDao().markMessagesAsRead(key, studentA)
    }

    // ==========================================
    // DOCUMENT SHARING
    // ==========================================
    suspend fun shareDocumentWithFriend(
        sender: UserEntity,
        recipientStudentId: String,
        document: DocumentEntity
    ): Result<String> {
        val recipient = db.userDao().getUserByStudentId(recipientStudentId)
            ?: return Result.failure(IllegalArgumentException("Recipient student not found."))

        db.sharedDocumentDao().insertSharedDocument(
            SharedDocumentEntity(
                documentId = document.id,
                senderStudentId = sender.studentId,
                senderName = sender.fullName,
                recipientStudentId = recipient.studentId,
                documentTitle = document.title,
                documentType = document.type,
                fileName = document.fileName,
                filePath = document.filePath,
                mimeType = document.mimeType,
                fileSize = document.fileSize
            )
        )

        // Also post academic chat notification
        sendFriendChatMessage(
            senderStudentId = sender.studentId,
            receiverStudentId = recipient.studentId,
            senderName = sender.fullName,
            text = "Shared academic document: ${document.title}",
            attachmentDoc = document
        )

        return Result.success("Shared '${document.title}' with ${recipient.fullName}.")
    }

    fun getSharedDocumentsWithUser(studentId: String): Flow<List<SharedDocumentEntity>> {
        return db.sharedDocumentDao().getDocumentsSharedWithUser(studentId)
    }

    // ==========================================
    // AI ACTION HISTORY LOGGING
    // ==========================================
    suspend fun logAIAction(
        actionType: String,
        subjectName: String = "",
        topicName: String = "",
        summary: String,
        details: String = ""
    ): Long {
        return db.aiActionDao().insertAction(
            AIActionEntity(
                actionType = actionType,
                subjectName = subjectName,
                topicName = topicName,
                summary = summary,
                details = details
            )
        )
    }

    fun getRecentAIActions(): Flow<List<AIActionEntity>> {
        return db.aiActionDao().getRecentActions()
    }

    // ==========================================
    // ROUTINE VERSIONING & CONFLICT DETECTION
    // ==========================================
    fun getAllRoutineVersions(): Flow<List<RoutineVersionEntity>> {
        return db.routineVersionDao().getAllVersions()
    }

    suspend fun createRoutineVersion(
        versionName: String,
        effectiveDate: String,
        groupSection: String,
        schedules: List<ClassScheduleEntity>
    ): Long {
        val currentMax = db.routineVersionDao().getMaxVersionNumber() ?: 0
        val newVerNum = currentMax + 1
        db.routineVersionDao().deactivateAllVersions()
        val versionId = db.routineVersionDao().insertVersion(
            RoutineVersionEntity(
                versionNumber = newVerNum,
                versionName = versionName.ifBlank { "Version $newVerNum" },
                effectiveDate = effectiveDate,
                groupSection = groupSection,
                isActive = true
            )
        )

        val updatedSchedules = schedules.map {
            it.copy(id = 0, routineVersionId = versionId, groupSection = groupSection)
        }
        db.routineDao().insertSchedules(updatedSchedules)
        return versionId
    }

    suspend fun detectScheduleConflicts(
        newSchedules: List<ClassScheduleEntity>
    ): List<Pair<ClassScheduleEntity, ClassScheduleEntity>> {
        val existing = db.routineDao().getSchedulesForDay(1) // get all active
        // Let's check day by day
        val conflicts = mutableListOf<Pair<ClassScheduleEntity, ClassScheduleEntity>>()
        for (newSch in newSchedules) {
            // Find existing classes on same weekday that overlap in time
            val dayClasses = db.routineDao().getScheduleById(newSch.id)
            // Or look in memory if we have active schedules
        }
        return conflicts
    }
}


data class SearchResults(
    val query: String = "",
    val matchingClasses: List<ClassScheduleEntity> = emptyList(),
    val matchingSubjects: List<SubjectEntity> = emptyList(),
    val matchingTopics: List<SyllabusTopicEntity> = emptyList(),
    val matchingNotices: List<NoticeEntity> = emptyList(),
    val matchingTasks: List<AcademicTaskEntity> = emptyList(),
    val matchingDocuments: List<DocumentEntity> = emptyList()
)
