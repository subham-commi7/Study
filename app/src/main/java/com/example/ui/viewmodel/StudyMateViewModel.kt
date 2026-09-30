package com.example.ui.viewmodel

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import android.provider.OpenableColumns
import com.example.data.ai.GeminiHelper
import com.example.data.ai.UniversalDocumentEngine
import com.example.data.local.AppDatabase
import com.example.data.auth.ForgotPasswordUiState
import com.example.data.auth.SecurityUtils
import com.example.data.local.entities.AcademicTaskEntity
import com.example.data.local.entities.AIActionEntity
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
import com.example.data.model.DocumentType
import com.example.data.model.ExtractedNoticeData
import com.example.data.model.ExtractedRoutineItem
import com.example.data.model.ExtractedSyllabusTopic
import com.example.data.model.NextClassInfo
import com.example.data.model.ScheduleConflict
import com.example.data.model.SubjectAttendanceSummary
import com.example.data.model.UniversalDocumentResult
import com.example.data.repository.StudyMateRepository
import com.example.notification.NotificationHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

enum class AppScreenState {
    SPLASH,
    AUTH,
    ONBOARDING,
    MAIN
}


data class ChatMessage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val sender: String, // "USER" or "AI"
    val text: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isError: Boolean = false
)

data class UiNotification(
    val message: String,
    val isError: Boolean = false
)

class StudyMateViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: StudyMateRepository by lazy {
        val db = AppDatabase.getInstance(application)
        StudyMateRepository(db)
    }

    // App Navigation Flow State: SPLASH, AUTH, ONBOARDING, MAIN
    private val _appScreenState = MutableStateFlow(AppScreenState.SPLASH)
    val appScreenState: StateFlow<AppScreenState> = _appScreenState.asStateFlow()

    private val _currentUser = MutableStateFlow<UserEntity?>(null)
    val currentUser: StateFlow<UserEntity?> = _currentUser.asStateFlow()

    private val _authError = MutableStateFlow<String?>(null)
    val authError: StateFlow<String?> = _authError.asStateFlow()

    private val _isAuthLoading = MutableStateFlow(false)
    val isAuthLoading: StateFlow<Boolean> = _isAuthLoading.asStateFlow()

    // Native Firebase Password Reset State
    private val _forgotPasswordState = MutableStateFlow(ForgotPasswordUiState())
    val forgotPasswordState: StateFlow<ForgotPasswordUiState> = _forgotPasswordState.asStateFlow()

    private val _showForgotPasswordDialog = MutableStateFlow(false)
    val showForgotPasswordDialog: StateFlow<Boolean> = _showForgotPasswordDialog.asStateFlow()

    private val _reminderMinutes = MutableStateFlow(10)
    val reminderMinutes: StateFlow<Int> = _reminderMinutes.asStateFlow()

    // Navigation State: "HOME", "ROUTINE", "ATTENDANCE", "SYLLABUS", "DOCUMENTS", "AI", "PROFILE"
    private val _currentTab = MutableStateFlow("HOME")
    val currentTab: StateFlow<String> = _currentTab.asStateFlow()

    fun selectTab(tab: String) {
        _currentTab.value = tab
    }

    // UI Feedback Banner/Snackbar
    private val _userMessage = MutableStateFlow<UiNotification?>(null)
    val userMessage: StateFlow<UiNotification?> = _userMessage.asStateFlow()

    fun dismissUserMessage() {
        _userMessage.value = null
    }

    fun showMessage(message: String, isError: Boolean = false) {
        _userMessage.value = UiNotification(message, isError)
    }

    // Search Query
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    // DB Data Flows
    val subjects: StateFlow<List<SubjectEntity>> = repository.allSubjects
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val schedules: StateFlow<List<ClassScheduleEntity>> = repository.allSchedules
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val attendanceSummaries: StateFlow<List<SubjectAttendanceSummary>> = repository.attendanceSummaries
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allAttendanceRecords: StateFlow<List<AttendanceRecordEntity>> = repository.allAttendanceRecords
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val syllabusTopics: StateFlow<List<SyllabusTopicEntity>> = repository.allSyllabusTopics
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val documents: StateFlow<List<DocumentEntity>> = repository.allDocuments
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val notices: StateFlow<List<NoticeEntity>> = repository.allNotices
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val tasks: StateFlow<List<AcademicTaskEntity>> = repository.allTasks
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val friends: StateFlow<List<FriendshipEntity>> = currentUser.flatMapLatest { user ->
        if (user != null && user.studentId.isNotBlank()) repository.getAcceptedFriends(user.studentId)
        else flowOf(emptyList())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val pendingFriendRequests: StateFlow<List<FriendshipEntity>> = currentUser.flatMapLatest { user ->
        if (user != null && user.studentId.isNotBlank()) repository.getPendingRequests(user.studentId)
        else flowOf(emptyList())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val sharedDocuments: StateFlow<List<SharedDocumentEntity>> = currentUser.flatMapLatest { user ->
        if (user != null && user.studentId.isNotBlank()) repository.getSharedDocumentsWithUser(user.studentId)
        else flowOf(emptyList())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recentAIActions: StateFlow<List<AIActionEntity>> = repository.getRecentAIActions()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val routineVersions: StateFlow<List<RoutineVersionEntity>> = repository.getAllRoutineVersions()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Today's Day of Week (1 = Mon ... 7 = Sun)
    val currentDayOfWeek: Int = when (Calendar.getInstance().get(Calendar.DAY_OF_WEEK)) {
        Calendar.MONDAY -> 1
        Calendar.TUESDAY -> 2
        Calendar.WEDNESDAY -> 3
        Calendar.THURSDAY -> 4
        Calendar.FRIDAY -> 5
        Calendar.SATURDAY -> 6
        Calendar.SUNDAY -> 7
        else -> 1
    }

    // Selected Routine Day Tab
    private val _selectedRoutineDay = MutableStateFlow(currentDayOfWeek)
    val selectedRoutineDay: StateFlow<Int> = _selectedRoutineDay.asStateFlow()

    fun selectRoutineDay(day: Int) {
        _selectedRoutineDay.value = day
    }

    // Holiday Exceptions
    val holidayExceptions: StateFlow<List<HolidayExceptionEntity>> = repository.allHolidayExceptions
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val todayDateString: String = SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH).format(Date())

    val todayHolidayException: StateFlow<HolidayExceptionEntity?> = holidayExceptions.map { list ->
        list.firstOrNull { it.dateString == todayDateString }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // Today's Classes & Next Class (respects date-specific holiday exceptions)
    val todayClasses: StateFlow<List<ClassScheduleEntity>> = combine(schedules, todayHolidayException) { allSchedules, holiday ->
        if (holiday != null && holiday.isFullDay) {
            emptyList()
        } else {
            allSchedules.filter { it.dayOfWeek == currentDayOfWeek }.sortedBy { it.startTime }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val nextClassInfo: StateFlow<NextClassInfo?> = combine(todayClasses, todayHolidayException) { todayList, holiday ->
        if (holiday != null && holiday.isFullDay) {
            null
        } else {
            repository.findNextClass(todayList)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // Global Search Combined Results
    val searchResults: StateFlow<com.example.data.model.SearchResultData?> = combine(
        _searchQuery,
        schedules,
        syllabusTopics,
        notices
    ) { query, schList, topList, notList ->
        val q = query.trim().lowercase()
        if (q.isEmpty()) return@combine null

        val filteredClasses = schList.filter {
            it.subjectName.lowercase().contains(q) || it.room.lowercase().contains(q) || it.teacher.lowercase().contains(q)
        }
        val filteredTopics = topList.filter {
            it.topicName.lowercase().contains(q) || it.chapterName.lowercase().contains(q) || it.subjectName.lowercase().contains(q)
        }
        val filteredNotices = notList.filter {
            it.title.lowercase().contains(q) || it.sourceFact.lowercase().contains(q) || it.explanation.lowercase().contains(q)
        }

        com.example.data.model.SearchResultData(
            classes = filteredClasses,
            topics = filteredTopics,
            notices = filteredNotices,
            totalMatches = filteredClasses.size + filteredTopics.size + filteredNotices.size
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // Extraction & Confirmation State
    private val _isExtracting = MutableStateFlow(false)
    val isExtracting: StateFlow<Boolean> = _isExtracting.asStateFlow()

    private val _extractedRoutinePreview = MutableStateFlow<List<ExtractedRoutineItem>?>(null)
    val extractedRoutinePreview: StateFlow<List<ExtractedRoutineItem>?> = _extractedRoutinePreview.asStateFlow()

    private val _extractedSyllabusPreview = MutableStateFlow<List<ExtractedSyllabusTopic>?>(null)
    val extractedSyllabusPreview: StateFlow<List<ExtractedSyllabusTopic>?> = _extractedSyllabusPreview.asStateFlow()

    private val _extractedNoticePreview = MutableStateFlow<ExtractedNoticeData?>(null)
    val extractedNoticePreview: StateFlow<ExtractedNoticeData?> = _extractedNoticePreview.asStateFlow()

    // Universal Document Intelligence Pipeline State
    private val _universalDocumentResult = MutableStateFlow<UniversalDocumentResult?>(null)
    val universalDocumentResult: StateFlow<UniversalDocumentResult?> = _universalDocumentResult.asStateFlow()

    private val _showTypeSelectionDialog = MutableStateFlow(false)
    val showTypeSelectionDialog: StateFlow<Boolean> = _showTypeSelectionDialog.asStateFlow()

    private val _pendingUploadUri = MutableStateFlow<Pair<Uri, String?>?>(null)
    val pendingUploadUri: StateFlow<Pair<Uri, String?>?> = _pendingUploadUri.asStateFlow()

    // Notes Q&A / Summary State
    private val _notesAnalysisResult = MutableStateFlow<String?>(null)
    val notesAnalysisResult: StateFlow<String?> = _notesAnalysisResult.asStateFlow()

    // AI Chat Messages
    private val _chatMessages = MutableStateFlow<List<ChatMessage>>(
        listOf(
            ChatMessage(
                sender = "AI",
                text = "Hello! I am StudyMate AI. Ask me about your routine, attendance, remaining syllabus, notices, or upload documents to get summaries and exam questions."
            )
        )
    )
    val chatMessages: StateFlow<List<ChatMessage>> = _chatMessages.asStateFlow()

    private val _isChatLoading = MutableStateFlow(false)
    val isChatLoading: StateFlow<Boolean> = _isChatLoading.asStateFlow()

    init {
        NotificationHelper.createNotificationChannels(application)
        checkInitialAuth()
    }

    private fun checkInitialAuth() {
        viewModelScope.launch {
            delay(900) // Brief smooth splash experience
            val isAuth = repository.isUserAuthenticated()
            if (isAuth) {
                val user = repository.getCurrentUser()
                _currentUser.value = user
                _reminderMinutes.value = user?.reminderMinutesBefore ?: repository.getReminderPreference()
                val setupDone = repository.isAcademicSetupDone()
                if (setupDone) {
                    _appScreenState.value = AppScreenState.MAIN
                } else {
                    _appScreenState.value = AppScreenState.ONBOARDING
                }
            } else {
                _appScreenState.value = AppScreenState.AUTH
            }
        }
    }

    // ==========================================
    // AUTHENTICATION & PROFILE ACTIONS
    // ==========================================

    fun login(email: String, pass: String) {
        viewModelScope.launch {
            _isAuthLoading.value = true
            _authError.value = null
            val result = repository.loginUser(email, pass)
            _isAuthLoading.value = false
            if (result.isSuccess) {
                val user = result.getOrThrow()
                _currentUser.value = user
                _reminderMinutes.value = user.reminderMinutesBefore
                val setupDone = repository.isAcademicSetupDone()
                if (setupDone) {
                    _appScreenState.value = AppScreenState.MAIN
                } else {
                    _appScreenState.value = AppScreenState.ONBOARDING
                }
                showMessage("Welcome back, ${user.fullName}!")
            } else {
                _authError.value = result.exceptionOrNull()?.message ?: "Login failed"
            }
        }
    }

    fun loginWithGoogle(context: Context) {
        viewModelScope.launch {
            _isAuthLoading.value = true
            _authError.value = null
            val result = repository.signInWithGoogle(context)
            _isAuthLoading.value = false
            if (result.isSuccess) {
                val user = result.getOrThrow()
                _currentUser.value = user
                _reminderMinutes.value = user.reminderMinutesBefore
                val setupDone = repository.isAcademicSetupDone()
                if (setupDone) {
                    _appScreenState.value = AppScreenState.MAIN
                } else {
                    _appScreenState.value = AppScreenState.ONBOARDING
                }
                showMessage("Welcome, ${user.fullName}!")
            } else {
                val err = result.exceptionOrNull()?.message ?: "Google sign-in failed."
                _authError.value = err
            }
        }
    }

    fun register(name: String, email: String, pass: String, confirmPass: String) {
        val trimmedName = name.trim()
        val trimmedEmail = email.trim().lowercase()

        if (trimmedName.isEmpty()) {
            _authError.value = "Please enter your full name."
            return
        }
        if (!SecurityUtils.isValidEmail(trimmedEmail)) {
            _authError.value = "Please enter a valid email address."
            return
        }
        if (pass != confirmPass) {
            _authError.value = "Passwords do not match."
            return
        }
        val pwdError = SecurityUtils.validatePasswordStrength(pass)
        if (pwdError != null) {
            _authError.value = pwdError
            return
        }

        viewModelScope.launch {
            _isAuthLoading.value = true
            _authError.value = null
            val result = repository.registerUser(trimmedName, trimmedEmail, pass)
            _isAuthLoading.value = false

            if (result.isSuccess) {
                val user = result.getOrThrow()
                _currentUser.value = user
                _reminderMinutes.value = user.reminderMinutesBefore
                _appScreenState.value = AppScreenState.ONBOARDING
                showMessage("Account created! A verification link has been sent to your email. Welcome, ${user.fullName}!")
            } else {
                _authError.value = result.exceptionOrNull()?.message ?: "Registration failed. Please try again."
            }
        }
    }

    fun clearAuthError() {
        _authError.value = null
    }

    // ==========================================
    // FIREBASE NATIVE FORGOT PASSWORD & RESET FLOW
    // ==========================================

    fun openForgotPasswordDialog(initialEmail: String = "") {
        _forgotPasswordState.value = ForgotPasswordUiState(
            isOpen = true,
            email = initialEmail.trim(),
            isSent = false,
            isLoading = false,
            error = null,
            successMessage = null,
            actionCode = null,
            isResettingWithCode = false
        )
        _showForgotPasswordDialog.value = true
    }

    fun dismissForgotPasswordDialog() {
        _showForgotPasswordDialog.value = false
        _forgotPasswordState.value = ForgotPasswordUiState()
    }

    fun sendPasswordResetLink(email: String) {
        val trimmedEmail = email.trim().lowercase()
        if (!SecurityUtils.isValidEmail(trimmedEmail)) {
            _forgotPasswordState.value = _forgotPasswordState.value.copy(
                error = "Please enter a valid email address.",
                successMessage = null
            )
            return
        }

        viewModelScope.launch {
            _forgotPasswordState.value = _forgotPasswordState.value.copy(
                isLoading = true,
                error = null,
                successMessage = null
            )
            val result = repository.sendPasswordResetEmail(trimmedEmail)
            if (result.isSuccess) {
                _forgotPasswordState.value = _forgotPasswordState.value.copy(
                    isSent = true,
                    isLoading = false,
                    email = trimmedEmail,
                    error = null,
                    successMessage = "A secure password reset link has been sent to $trimmedEmail. Please open the link in your email to choose a new password."
                )
            } else {
                _forgotPasswordState.value = _forgotPasswordState.value.copy(
                    isLoading = false,
                    error = result.exceptionOrNull()?.message ?: "Failed to send reset email. Please try again."
                )
            }
        }
    }

    fun openResetPasswordWithCode(actionCode: String) {
        _forgotPasswordState.value = ForgotPasswordUiState(
            isOpen = true,
            actionCode = actionCode.trim(),
            isResettingWithCode = true,
            isSent = false,
            isLoading = false
        )
        _showForgotPasswordDialog.value = true
    }

    fun confirmPasswordResetWithCode(actionCode: String, newPass: String, confirmPass: String) {
        val cleanCode = actionCode.trim()
        if (cleanCode.isBlank()) {
            _forgotPasswordState.value = _forgotPasswordState.value.copy(
                error = "Password reset code or link is missing."
            )
            return
        }
        if (newPass.isEmpty()) {
            _forgotPasswordState.value = _forgotPasswordState.value.copy(
                error = "Please enter your new password."
            )
            return
        }
        if (confirmPass.isEmpty()) {
            _forgotPasswordState.value = _forgotPasswordState.value.copy(
                error = "Please confirm your new password."
            )
            return
        }
        if (newPass != confirmPass) {
            _forgotPasswordState.value = _forgotPasswordState.value.copy(
                error = "Passwords do not match."
            )
            return
        }
        val pwdError = SecurityUtils.validatePasswordStrength(newPass)
        if (pwdError != null) {
            _forgotPasswordState.value = _forgotPasswordState.value.copy(
                error = pwdError
            )
            return
        }

        viewModelScope.launch {
            _forgotPasswordState.value = _forgotPasswordState.value.copy(
                isLoading = true,
                error = null,
                successMessage = null
            )
            val result = repository.confirmPasswordReset(cleanCode, newPass)
            if (result.isSuccess) {
                _showForgotPasswordDialog.value = false
                _forgotPasswordState.value = ForgotPasswordUiState()
                showMessage("Password updated successfully! Please log in with your new password.")
            } else {
                _forgotPasswordState.value = _forgotPasswordState.value.copy(
                    isLoading = false,
                    error = result.exceptionOrNull()?.message ?: "Failed to reset password. The link may have expired or is invalid."
                )
            }
        }
    }

    fun resendEmailVerification() {
        viewModelScope.launch {
            val result = repository.sendEmailVerification()
            if (result.isSuccess) {
                showMessage("Verification email sent! Please check your inbox.")
            } else {
                showMessage(result.exceptionOrNull()?.message ?: "Failed to send verification email.", isError = true)
            }
        }
    }

    fun handleAuthActionUri(uri: Uri) {
        val mode = uri.getQueryParameter("mode")
        val oobCode = uri.getQueryParameter("oobCode")
        if (oobCode.isNullOrBlank()) return

        when (mode) {
            "resetPassword" -> {
                openResetPasswordWithCode(oobCode)
            }
            "verifyEmail" -> {
                viewModelScope.launch {
                    val result = repository.applyActionCode(oobCode)
                    if (result.isSuccess) {
                        showMessage("Email verified successfully! You can now log in.")
                    } else {
                        showMessage(result.exceptionOrNull()?.message ?: "Email verification link expired or invalid.", isError = true)
                    }
                }
            }
        }
    }

    fun saveAcademicProfile(college: String, course: String, semester: String, year: String, group: String, fullName: String? = null) {
        viewModelScope.launch {
            val result = repository.updateUserProfile(college, course, semester, year, group, fullName = fullName)
            if (result.isSuccess) {
                _currentUser.value = result.getOrThrow()
                _appScreenState.value = AppScreenState.MAIN
                showMessage("Academic profile saved successfully!")
            } else {
                showMessage("Failed to save profile: ${result.exceptionOrNull()?.message}", isError = true)
            }
        }
    }

    fun saveGoogleWebClientId(clientId: String) {
        val context = getApplication<Application>()
        repository.saveGoogleWebClientId(context, clientId)
        showMessage("Google Web Client ID saved!")
    }

    fun skipOnboarding() {
        viewModelScope.launch {
            repository.updateUserProfile("", "", "", "", "")
            _currentUser.value = repository.getCurrentUser()
            _appScreenState.value = AppScreenState.MAIN
            showMessage("Profile setup skipped. You can update details in Settings anytime.")
        }
    }

    fun logout() {
        viewModelScope.launch {
            repository.logout()
            _currentUser.value = null
            _appScreenState.value = AppScreenState.AUTH
            _currentTab.value = "HOME"
            showMessage("Logged out successfully.")
        }
    }

    fun updateReminderTiming(minutes: Int) {
        viewModelScope.launch {
            repository.updateReminderPreference(minutes)
            _reminderMinutes.value = minutes
            val user = _currentUser.value
            if (user != null) {
                _currentUser.value = user.copy(reminderMinutesBefore = minutes)
            }
            // Reschedule active class reminders with the new preference
            val curSchedules = schedules.value
            val context = getApplication<Application>()
            for (sch in curSchedules) {
                if (sch.reminderEnabled) {
                    NotificationHelper.scheduleClassReminder(
                        context = context,
                        scheduleId = sch.id,
                        dayOfWeek = sch.dayOfWeek,
                        startTime = sch.startTime,
                        subject = sch.subjectName,
                        room = sch.room,
                        minutesBefore = minutes
                    )
                }
            }
            showMessage("Class reminder alerts set to $minutes minutes before class.")
        }
    }


    // Collaboration Actions (Student ID, Friends, Chat, Document Sharing)
    fun searchStudent(query: String, onResult: (List<UserEntity>) -> Unit) {
        val user = _currentUser.value ?: return
        viewModelScope.launch {
            val list = repository.searchStudents(query, user.studentId)
            onResult(list)
        }
    }

    fun sendFriendRequest(targetStudentId: String) {
        val user = _currentUser.value ?: return
        viewModelScope.launch {
            val result = repository.sendFriendRequest(user, targetStudentId)
            if (result.isSuccess) {
                showMessage(result.getOrThrow())
            } else {
                showMessage(result.exceptionOrNull()?.message ?: "Failed to send request", isError = true)
            }
        }
    }

    fun acceptFriendRequest(friendStudentId: String) {
        val user = _currentUser.value ?: return
        viewModelScope.launch {
            repository.acceptFriendRequest(user.studentId, friendStudentId)
            showMessage("Friend request accepted! You can now collaborate and chat.")
        }
    }

    fun declineFriendRequest(friendStudentId: String) {
        val user = _currentUser.value ?: return
        viewModelScope.launch {
            repository.declineFriendRequest(user.studentId, friendStudentId)
            showMessage("Friend request declined.")
        }
    }

    fun removeFriend(friendStudentId: String) {
        val user = _currentUser.value ?: return
        viewModelScope.launch {
            repository.removeFriend(user.studentId, friendStudentId)
            showMessage("Friend removed.")
        }
    }

    fun getFriendChatMessages(friendStudentId: String): Flow<List<FriendChatMessageEntity>> {
        val user = _currentUser.value ?: return flowOf(emptyList<FriendChatMessageEntity>())
        return repository.getFriendChatMessages(user.studentId, friendStudentId)
    }

    fun sendFriendChatMessage(friendStudentId: String, text: String, attachmentDoc: DocumentEntity? = null) {
        val user = _currentUser.value ?: return
        viewModelScope.launch {
            repository.sendFriendChatMessage(
                senderStudentId = user.studentId,
                receiverStudentId = friendStudentId,
                senderName = user.fullName,
                text = text,
                attachmentDoc = attachmentDoc
            )
        }
    }

    fun shareDocumentWithFriend(recipientStudentId: String, document: DocumentEntity) {
        val user = _currentUser.value ?: return
        viewModelScope.launch {
            val result = repository.shareDocumentWithFriend(user, recipientStudentId, document)
            if (result.isSuccess) {
                showMessage(result.getOrThrow())
            } else {
                showMessage(result.exceptionOrNull()?.message ?: "Failed to share document", isError = true)
            }
        }
    }

    // Routine Actions
    fun addOrUpdateClass(
        id: Long = 0,
        subjectName: String,
        dayOfWeek: Int,
        startTime: String,
        endTime: String,
        room: String = "",
        teacher: String = "",
        reminderEnabled: Boolean = true
    ) {
        viewModelScope.launch {
            try {
                val cleanSubj = subjectName.trim().ifEmpty { "General Class" }
                val subjId = repository.insertSubject(cleanSubj)

                val entity = ClassScheduleEntity(
                    id = id,
                    subjectId = subjId,
                    subjectName = cleanSubj,
                    dayOfWeek = dayOfWeek.coerceIn(1, 7),
                    startTime = startTime.trim().ifEmpty { "09:00" },
                    endTime = endTime.trim().ifEmpty { "10:00" },
                    room = room.trim(),
                    teacher = teacher.trim(),
                    reminderEnabled = reminderEnabled
                )

                if (id == 0L) {
                    val newId = repository.insertSchedule(entity)
                    if (reminderEnabled) {
                        NotificationHelper.scheduleClassReminder(
                            context = getApplication(),
                            scheduleId = newId,
                            dayOfWeek = entity.dayOfWeek,
                            startTime = entity.startTime,
                            subject = entity.subjectName,
                            room = entity.room
                        )
                    }
                    showMessage("Class added successfully")
                } else {
                    repository.updateSchedule(entity)
                    if (reminderEnabled) {
                        NotificationHelper.scheduleClassReminder(
                            context = getApplication(),
                            scheduleId = id,
                            dayOfWeek = entity.dayOfWeek,
                            startTime = entity.startTime,
                            subject = entity.subjectName,
                            room = entity.room
                        )
                    } else {
                        NotificationHelper.cancelClassReminder(getApplication(), id)
                    }
                    showMessage("Class updated")
                }
            } catch (e: Exception) {
                showMessage("Failed to save class: ${e.message}", isError = true)
            }
        }
    }

    fun deleteClass(schedule: ClassScheduleEntity) {
        viewModelScope.launch {
            try {
                repository.deleteSchedule(schedule)
                NotificationHelper.cancelClassReminder(getApplication(), schedule.id)
                showMessage("Class removed")
            } catch (e: Exception) {
                showMessage("Error removing class: ${e.message}", isError = true)
            }
        }
    }

    // Attendance Actions
    fun markAttendance(subjectId: Long, status: String, targetDateMillis: Long = System.currentTimeMillis(), notes: String = "") {
        viewModelScope.launch {
            try {
                repository.markAttendance(subjectId, status, targetDateMillis, notes)
                val statusText = when (status) {
                    "PRESENT" -> "Present marked (locked)"
                    "ABSENT" -> "Absent marked (locked)"
                    "CANCELLED" -> "Class cancelled marked (locked)"
                    else -> "Attendance updated"
                }
                showMessage(statusText)
            } catch (e: Exception) {
                showMessage("${e.message}", isError = true)
            }
        }
    }

    fun updateSubjectTarget(subject: SubjectEntity, newTarget: Int) {
        viewModelScope.launch {
            try {
                repository.updateSubject(subject.copy(targetAttendance = newTarget.coerceIn(1, 100)))
                showMessage("Target updated to $newTarget%")
            } catch (e: Exception) {
                showMessage("Failed to update target: ${e.message}", isError = true)
            }
        }
    }

    // Syllabus Actions
    fun addSyllabusTopic(
        subjectName: String,
        unitName: String,
        chapterName: String,
        topicName: String
    ) {
        viewModelScope.launch {
            try {
                val cleanSubj = subjectName.trim().ifEmpty { "General Subject" }
                val subjId = repository.insertSubject(cleanSubj)
                val topic = SyllabusTopicEntity(
                    subjectId = subjId,
                    subjectName = cleanSubj,
                    unitName = unitName.trim().ifEmpty { "Unit 1" },
                    chapterName = chapterName.trim().ifEmpty { "Chapter 1" },
                    topicName = topicName.trim().ifEmpty { "Topic" }
                )
                repository.insertTopic(topic)
                showMessage("Topic added to syllabus")
            } catch (e: Exception) {
                showMessage("Failed to add topic: ${e.message}", isError = true)
            }
        }
    }

    fun toggleTopicStatus(topic: SyllabusTopicEntity) {
        viewModelScope.launch {
            try {
                val nextStatus = when (topic.status) {
                    "NOT_STARTED" -> "IN_PROGRESS"
                    "IN_PROGRESS" -> "COMPLETED"
                    else -> "NOT_STARTED"
                }
                repository.updateTopicStatus(topic.id, nextStatus)
            } catch (e: Exception) {
                showMessage("Failed to update topic status: ${e.message}", isError = true)
            }
        }
    }

    fun deleteTopic(topic: SyllabusTopicEntity) {
        viewModelScope.launch {
            try {
                repository.deleteTopic(topic)
                showMessage("Topic deleted")
            } catch (e: Exception) {
                showMessage("Failed to delete topic: ${e.message}", isError = true)
            }
        }
    }

    // Document & Routine Extraction Pipeline
    fun processUploadedRoutine(uri: Uri, mimeType: String?, textSample: String? = null) {
        viewModelScope.launch {
            _isExtracting.value = true
            try {
                val (extractedText, bitmap) = if (textSample == null) {
                    GeminiHelper.extractTextOrImageFromUri(getApplication(), uri, mimeType)
                } else {
                    Pair(textSample, null)
                }

                saveDocumentLocally(uri, "Routine Document", "ROUTINE", mimeType)
                val items = GeminiHelper.extractRoutine(extractedText ?: textSample, bitmap)
                _extractedRoutinePreview.value = items
            } catch (e: Exception) {
                showMessage("Extraction failed: ${e.message}", isError = true)
            } finally {
                _isExtracting.value = false
            }
        }
    }

    fun confirmExtractedRoutine(confirmedItems: List<ExtractedRoutineItem>) {
        viewModelScope.launch {
            try {
                val schedulesToSave = confirmedItems.map { item ->
                    val subjId = repository.insertSubject(item.subject)
                    ClassScheduleEntity(
                        subjectId = subjId,
                        subjectName = item.subject,
                        dayOfWeek = item.dayOfWeek,
                        startTime = item.startTime,
                        endTime = item.endTime,
                        room = item.room,
                        teacher = item.teacher,
                        reminderEnabled = true
                    )
                }
                repository.insertSchedules(schedulesToSave)
                _extractedRoutinePreview.value = null
                showMessage("Extracted routine saved to timetable (${confirmedItems.size} classes)")
            } catch (e: Exception) {
                showMessage("Failed to save confirmed routine: ${e.message}", isError = true)
            }
        }
    }

    fun dismissRoutinePreview() {
        _extractedRoutinePreview.value = null
    }

    // Syllabus Extraction Pipeline
    fun processUploadedSyllabus(uri: Uri, mimeType: String?, textSample: String? = null) {
        viewModelScope.launch {
            _isExtracting.value = true
            try {
                val (extractedText, bitmap) = if (textSample == null) {
                    GeminiHelper.extractTextOrImageFromUri(getApplication(), uri, mimeType)
                } else {
                    Pair(textSample, null)
                }

                saveDocumentLocally(uri, "Syllabus Document", "SYLLABUS", mimeType)
                val topics = GeminiHelper.extractSyllabus(extractedText ?: textSample, bitmap)
                _extractedSyllabusPreview.value = topics
            } catch (e: Exception) {
                showMessage("Syllabus extraction failed: ${e.message}", isError = true)
            } finally {
                _isExtracting.value = false
            }
        }
    }

    fun confirmExtractedSyllabus(confirmedTopics: List<ExtractedSyllabusTopic>) {
        viewModelScope.launch {
            try {
                val entities = confirmedTopics.map { item ->
                    val subjId = repository.insertSubject(item.subject)
                    SyllabusTopicEntity(
                        subjectId = subjId,
                        subjectName = item.subject,
                        unitName = item.unit,
                        chapterName = item.chapter,
                        topicName = item.topic
                    )
                }
                repository.insertTopics(entities)
                _extractedSyllabusPreview.value = null
                showMessage("Saved ${confirmedTopics.size} syllabus topics")
            } catch (e: Exception) {
                showMessage("Failed to save syllabus: ${e.message}", isError = true)
            }
        }
    }

    fun dismissSyllabusPreview() {
        _extractedSyllabusPreview.value = null
    }

    // Notice Extraction Pipeline & Routine Change Confirmation
    fun processUploadedNotice(uri: Uri, mimeType: String?, textSample: String? = null) {
        viewModelScope.launch {
            _isExtracting.value = true
            try {
                val (extractedText, bitmap) = if (textSample == null) {
                    GeminiHelper.extractTextOrImageFromUri(getApplication(), uri, mimeType)
                } else {
                    Pair(textSample, null)
                }

                saveDocumentLocally(uri, "Notice Document", "NOTICE", mimeType)
                val noticeData = GeminiHelper.extractNotice(extractedText ?: textSample, bitmap)
                _extractedNoticePreview.value = noticeData

                // Save notice entity to DB
                repository.insertNotice(
                    NoticeEntity(
                        title = noticeData.title,
                        issueDate = noticeData.issueDate,
                        sourceFact = noticeData.sourceFacts,
                        explanation = noticeData.simpleExplanation,
                        hasRoutineChange = noticeData.routineChangeDetected,
                        proposedChangeJson = noticeData.routineChangeSummary,
                        deadlineDate = noticeData.deadlines.firstOrNull() ?: ""
                    )
                )

                // If notice contains assignments or exam deadlines, add task
                for (dl in noticeData.deadlines) {
                    repository.insertTask(
                        AcademicTaskEntity(
                            subjectName = "Notice Deadline",
                            title = noticeData.title,
                            type = if (noticeData.title.contains("Exam", ignoreCase = true)) "EXAM" else "DEADLINE",
                            dueDate = dl,
                            instructions = noticeData.simpleExplanation
                        )
                    )
                }
            } catch (e: Exception) {
                showMessage("Notice processing error: ${e.message}", isError = true)
            } finally {
                _isExtracting.value = false
            }
        }
    }

    fun applyNoticeRoutineChange(proposedChanges: List<ExtractedRoutineItem>, noticeId: Long? = null) {
        viewModelScope.launch {
            try {
                val newSchedules = proposedChanges.map { item ->
                    val subjId = repository.insertSubject(item.subject)
                    ClassScheduleEntity(
                        subjectId = subjId,
                        subjectName = item.subject,
                        dayOfWeek = item.dayOfWeek,
                        startTime = item.startTime,
                        endTime = item.endTime,
                        room = item.room,
                        teacher = item.teacher
                    )
                }
                repository.insertSchedules(newSchedules)
                if (noticeId != null) {
                    repository.markNoticeChangeApplied(noticeId, true)
                }
                _extractedNoticePreview.value = null
                showMessage("Routine schedule updated according to notice!")
            } catch (e: Exception) {
                showMessage("Failed to update routine from notice: ${e.message}", isError = true)
            }
        }
    }

    fun dismissNoticePreview() {
        _extractedNoticePreview.value = null
    }

    // ==========================================
    // UNIVERSAL DOCUMENT INTELLIGENCE PIPELINE
    // ==========================================

    fun processUniversalUpload(uri: Uri, mimeType: String?, userOverrideType: DocumentType? = null) {
        viewModelScope.launch {
            _isExtracting.value = true
            try {
                val fileName = getFileNameFromUri(uri)
                val fileSize = getFileSizeFromUri(uri)

                val result = UniversalDocumentEngine.processDocument(
                    context = getApplication(),
                    uri = uri,
                    fileName = fileName,
                    mimeType = mimeType,
                    fileSize = fileSize,
                    existingSubjects = subjects.value,
                    existingSchedules = schedules.value,
                    existingNotices = notices.value,
                    existingDocs = documents.value,
                    userOverrideType = userOverrideType
                )

                if (result.requiresUserTypeConfirmation && userOverrideType == null) {
                    _pendingUploadUri.value = Pair(uri, mimeType)
                    _showTypeSelectionDialog.value = true
                } else {
                    _universalDocumentResult.value = result
                }
            } catch (e: Exception) {
                showMessage("Document processing error: ${e.message}", isError = true)
            } finally {
                _isExtracting.value = false
            }
        }
    }

    fun overrideDocumentTypeAndReprocess(chosenType: DocumentType) {
        val pending = _pendingUploadUri.value ?: return
        _showTypeSelectionDialog.value = false
        processUniversalUpload(pending.first, pending.second, userOverrideType = chosenType)
    }

    fun dismissTypeSelectionDialog() {
        _showTypeSelectionDialog.value = false
        _pendingUploadUri.value = null
    }

    fun confirmUniversalDocument(
        result: UniversalDocumentResult,
        confirmedType: DocumentType = result.docType
    ) {
        viewModelScope.launch {
            try {
                val context = getApplication<Application>()
                val docsDir = File(context.filesDir, "academic_docs").apply { mkdirs() }

                // Check if already persistently saved by UniversalDocumentEngine
                val persistentPath = result.persistentFilePath
                val finalFile = if (!persistentPath.isNullOrBlank() && File(persistentPath).exists() && File(persistentPath).length() > 0L) {
                    File(persistentPath)
                } else {
                    val safeName = result.fileName.replace(Regex("[^a-zA-Z0-9._-]"), "_").ifBlank { "doc_${System.currentTimeMillis()}.pdf" }
                    val copyFile = File(docsDir, "${System.currentTimeMillis()}_$safeName")
                    if (result.sourceUriString != null) {
                        try {
                            val uri = Uri.parse(result.sourceUriString)
                            context.contentResolver.openInputStream(uri)?.use { input ->
                                copyFile.outputStream().use { output -> input.copyTo(output) }
                            }
                        } catch (_: Exception) {}
                    }
                    copyFile
                }

                val doc = DocumentEntity(
                    title = result.title,
                    type = confirmedType.name,
                    filePath = finalFile.absolutePath,
                    fileName = result.fileName,
                    mimeType = result.mimeType,
                    fileSize = if (finalFile.exists()) finalFile.length() else result.fileSize,
                    contentSummary = result.notesData?.summary ?: result.noticeData?.simpleExplanation ?: "Processed ${confirmedType.displayName}"
                )
                repository.insertDocument(doc)

                // Apply to correct feature
                when (confirmedType) {
                    DocumentType.ROUTINE, DocumentType.PRACTICAL_SCHEDULE -> {
                        val items = result.routineItems ?: emptyList()
                        val newSchedules = items.map { item ->
                            val subjId = repository.insertSubject(item.subject, code = item.subjectCode)
                            ClassScheduleEntity(
                                subjectId = subjId,
                                subjectName = item.subject,
                                dayOfWeek = item.dayOfWeek,
                                startTime = item.startTime,
                                endTime = item.endTime,
                                room = item.room,
                                teacher = item.teacher,
                                reminderEnabled = true
                            )
                        }
                        repository.insertSchedules(newSchedules)
                        for (sch in newSchedules) {
                            NotificationHelper.scheduleClassReminder(
                                context = context,
                                scheduleId = sch.id,
                                dayOfWeek = sch.dayOfWeek,
                                startTime = sch.startTime,
                                subject = sch.subjectName,
                                room = sch.room
                            )
                        }
                        showMessage("Saved routine timetable (${items.size} classes added)")
                    }

                    DocumentType.SYLLABUS -> {
                        val topics = result.syllabusTopics ?: emptyList()
                        val entities = topics.map { topic ->
                            val subjId = repository.insertSubject(topic.subject, code = topic.subjectCode)
                            SyllabusTopicEntity(
                                subjectId = subjId,
                                subjectName = topic.subject,
                                subjectCode = topic.subjectCode,
                                course = topic.course,
                                semester = topic.semester,
                                unitName = topic.unit,
                                chapterName = topic.chapter,
                                topicName = topic.topic,
                                subtopic = topic.subtopic,
                                isTrackable = topic.isTrackable,
                                isReferenceOnly = topic.isReferenceOnly,
                                status = "NOT_STARTED"
                            )
                        }
                        repository.insertTopics(entities)
                        showMessage("Saved syllabus (${topics.size} topics across subjects added)")
                    }

                    DocumentType.NOTICE, DocumentType.HOLIDAY_NOTICE, DocumentType.ACADEMIC_CALENDAR -> {
                        val n = result.noticeData
                        if (n != null) {
                            val noticeId = repository.insertNotice(
                                NoticeEntity(
                                    title = n.title,
                                    issueDate = n.issueDate,
                                    sourceFact = n.sourceFacts,
                                    explanation = n.simpleExplanation,
                                    hasRoutineChange = n.routineChangeDetected || n.isHoliday,
                                    proposedChangeJson = n.routineChangeSummary,
                                    deadlineDate = n.deadlines.firstOrNull() ?: ""
                                )
                            )

                            // Resolve calendar date for holiday / cancellation notices
                            val resolvedDate = DateResolutionHelper.resolveDateExpression(
                                "${n.title} ${n.simpleExplanation} ${n.sourceFacts} ${n.affectedDate}"
                            )
                            val isHol = n.isHoliday || confirmedType == DocumentType.HOLIDAY_NOTICE ||
                                    n.title.contains("holiday", ignoreCase = true) ||
                                    n.simpleExplanation.contains("holiday", ignoreCase = true) ||
                                    n.simpleExplanation.contains("suspended", ignoreCase = true)

                            if (isHol) {
                                val targetDateStr = resolvedDate?.dateString ?: n.affectedDate.ifBlank { todayDateString }
                                val targetDayOfWeek = resolvedDate?.dayOfWeek ?: currentDayOfWeek
                                repository.insertHolidayException(
                                    HolidayExceptionEntity(
                                        dateString = targetDateStr,
                                        dayOfWeek = targetDayOfWeek,
                                        reason = n.title.ifBlank { "Holiday Announcement" },
                                        isFullDay = true,
                                        noticeId = noticeId
                                    )
                                )
                                val displayDate = resolvedDate?.formattedDisplay ?: targetDateStr
                                showMessage("Holiday registered for $displayDate. Classes on that date are cancelled; regular recurring timetable is preserved.")
                            } else {
                                showMessage("Notice registered. Check reminders & tasks.")
                            }

                            for (dl in n.deadlines) {
                                repository.insertTask(
                                    AcademicTaskEntity(
                                        subjectName = n.institution.ifBlank { "Notice Deadline" },
                                        title = n.title,
                                        type = if (n.title.contains("Exam", ignoreCase = true)) "EXAM" else "DEADLINE",
                                        dueDate = dl,
                                        instructions = n.simpleExplanation
                                    )
                                )
                            }
                        }
                    }

                    DocumentType.EXAM_SCHEDULE -> {
                        val n = result.noticeData
                        if (n != null) {
                            for (dl in n.deadlines) {
                                repository.insertTask(
                                    AcademicTaskEntity(
                                        subjectName = "Exam Schedule",
                                        title = n.title,
                                        type = "EXAM",
                                        dueDate = dl,
                                        instructions = n.simpleExplanation
                                    )
                                )
                            }
                        }
                        showMessage("Exam schedule saved to academic calendar.")
                    }

                    DocumentType.ASSIGNMENT -> {
                        val n = result.noticeData
                        if (n != null) {
                            for (dl in n.deadlines) {
                                repository.insertTask(
                                    AcademicTaskEntity(
                                        subjectName = "Assignment",
                                        title = n.title,
                                        type = "ASSIGNMENT",
                                        dueDate = dl,
                                        instructions = n.simpleExplanation
                                    )
                                )
                            }
                        }
                        showMessage("Assignment task saved with deadline reminder.")
                    }

                    DocumentType.NOTES, DocumentType.QUESTION_PAPER, DocumentType.ATTENDANCE_DOCUMENT, DocumentType.OTHER -> {
                        showMessage("Document notes indexed for AI assistant and search.")
                    }
                }

                _universalDocumentResult.value = null
            } catch (e: Exception) {
                showMessage("Failed to save document: ${e.message}", isError = true)
            }
        }
    }

    fun dismissUniversalDocumentResult() {
        _universalDocumentResult.value = null
    }

    fun resolveRoutineConflict(applyNew: Boolean, conflict: ScheduleConflict) {
        viewModelScope.launch {
            try {
                if (applyNew) {
                    val subjId = repository.insertSubject(conflict.conflictingClass.subject, code = conflict.conflictingClass.subjectCode)
                    val updated = conflict.existingClass.copy(
                        subjectId = subjId,
                        subjectName = conflict.conflictingClass.subject,
                        startTime = conflict.conflictingClass.startTime,
                        endTime = conflict.conflictingClass.endTime,
                        room = conflict.conflictingClass.room,
                        teacher = conflict.conflictingClass.teacher
                    )
                    repository.updateSchedule(updated)
                    showMessage("Applied new schedule for ${conflict.conflictingClass.subject}")
                } else {
                    showMessage("Kept existing schedule for ${conflict.existingClass.subjectName}")
                }
            } catch (e: Exception) {
                showMessage("Conflict resolution error: ${e.message}", isError = true)
            }
        }
    }

    private fun getFileNameFromUri(uri: Uri): String {
        return try {
            val cursor = getApplication<Application>().contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val nameIdx = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIdx != -1) return it.getString(nameIdx)
                }
            }
            uri.lastPathSegment ?: "academic_document"
        } catch (_: Exception) {
            "academic_document"
        }
    }

    private fun getFileSizeFromUri(uri: Uri): Long {
        return try {
            val cursor = getApplication<Application>().contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val sizeIdx = it.getColumnIndex(OpenableColumns.SIZE)
                    if (sizeIdx != -1) return it.getLong(sizeIdx)
                }
            }
            0L
        } catch (_: Exception) {
            0L
        }
    }

    // Document Storage Helper
    private suspend fun saveDocumentLocally(
        uri: Uri,
        title: String,
        type: String,
        mimeType: String?
    ): DocumentEntity? = withContext(Dispatchers.IO) {
        try {
            val context = getApplication<Application>()
            val docsDir = File(context.filesDir, "academic_docs").apply { mkdirs() }
            val fileName = "doc_${System.currentTimeMillis()}.${if (mimeType?.contains("pdf") == true) "pdf" else "dat"}"
            val destFile = File(docsDir, fileName)

            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            }

            val doc = DocumentEntity(
                title = title,
                type = type,
                filePath = destFile.absolutePath,
                fileName = fileName,
                mimeType = mimeType ?: "application/octet-stream",
                fileSize = destFile.length()
            )
            repository.insertDocument(doc)
            doc
        } catch (_: Exception) {
            null
        }
    }

    fun deleteDocument(doc: DocumentEntity) {
        viewModelScope.launch {
            try {
                File(doc.filePath).delete()
                repository.deleteDocument(doc)
                showMessage("Document deleted")
            } catch (e: Exception) {
                showMessage("Failed to delete document: ${e.message}", isError = true)
            }
        }
    }

    fun deleteNotice(notice: NoticeEntity) {
        viewModelScope.launch {
            try {
                repository.deleteNotice(notice)
                showMessage("Notice deleted")
            } catch (e: Exception) {
                showMessage("Failed to delete notice: ${e.message}", isError = true)
            }
        }
    }

    // Notes Analysis
    fun analyzeNotes(content: String, requestType: String) {
        viewModelScope.launch {
            _isExtracting.value = true
            try {
                val res = GeminiHelper.analyzeNotes(content, requestType)
                _notesAnalysisResult.value = res
            } catch (e: Exception) {
                _notesAnalysisResult.value = "Unable to analyze notes: ${e.message}"
            } finally {
                _isExtracting.value = false
            }
        }
    }

    fun clearNotesAnalysis() {
        _notesAnalysisResult.value = null
    }

    // AI Academic Assistant
    fun sendChatMessage(userText: String) {
        val q = userText.trim()
        if (q.isEmpty()) return

        val userMsg = ChatMessage(sender = "USER", text = q)
        _chatMessages.value = _chatMessages.value + userMsg
        _isChatLoading.value = true

        viewModelScope.launch {
            try {
                // 1. First check if message is a database action (e.g. syllabus topic completion/progress)
                val actionResult = tryExecuteAiSyllabusAction(q)
                if (actionResult != null) {
                    _chatMessages.value = _chatMessages.value + ChatMessage(sender = "AI", text = actionResult)
                    return@launch
                }

                // 2. Otherwise build real grounding context from actual DB records
                val contextBuilder = StringBuilder()

                val user = currentUser.value
                if (user != null) {
                    contextBuilder.append("STUDENT ACADEMIC PROFILE:\n")
                    contextBuilder.append("Student Name: ${user.fullName}\n")
                    if (user.college.isNotBlank()) contextBuilder.append("Institution/College: ${user.college}\n")
                    if (user.course.isNotBlank()) contextBuilder.append("Course: ${user.course}\n")
                    if (user.semester.isNotBlank()) contextBuilder.append("Semester: ${user.semester}\n")
                    if (user.year.isNotBlank()) contextBuilder.append("Year: ${user.year}\n")
                    if (user.groupSection.isNotBlank()) contextBuilder.append("Group/Batch: ${user.groupSection}\n")
                    contextBuilder.append("\n")
                }

                contextBuilder.append("ROUTINE (Classes):\n")
                val curSchedules = schedules.value
                if (curSchedules.isEmpty()) {
                    contextBuilder.append("No classes scheduled yet.\n")
                } else {
                    curSchedules.forEach {
                        contextBuilder.append("- Day ${it.dayOfWeek}: ${it.subjectName} (${it.startTime} - ${it.endTime}) in ${it.room}, Teacher: ${it.teacher}\n")
                    }
                }

                contextBuilder.append("\nATTENDANCE SUMMARY:\n")
                val curAtt = attendanceSummaries.value
                if (curAtt.isEmpty()) {
                    contextBuilder.append("No attendance records yet.\n")
                } else {
                    curAtt.forEach {
                        contextBuilder.append("- ${it.subject.name}: ${String.format("%.1f", it.percentage)}% (${it.attendedClasses}/${it.conductedClasses} attended, Target: ${it.subject.targetAttendance}%). Safe to miss: ${it.canMissClasses} classes, Required to reach target: ${it.requiredToReachTarget} classes\n")
                    }
                }

                contextBuilder.append("\nSYLLABUS STATUS:\n")
                val curTopics = syllabusTopics.value
                if (curTopics.isEmpty()) {
                    contextBuilder.append("No syllabus topics added yet.\n")
                } else {
                    val grouped = curTopics.groupBy { it.subjectName }
                    grouped.forEach { (subj, topics) ->
                        val completed = topics.count { it.status == "COMPLETED" }
                        val inProgress = topics.count { it.status == "IN_PROGRESS" }
                        val notStarted = topics.count { it.status == "NOT_STARTED" }
                        contextBuilder.append("- $subj: $completed/${topics.size} completed ($inProgress in progress, $notStarted pending)\n")
                        topics.take(4).forEach {
                            contextBuilder.append("  * [${it.status}] ${it.unitName} - ${it.topicName}\n")
                        }
                    }
                }

                contextBuilder.append("\nNOTICES & DEADLINES:\n")
                val curNotices = notices.value
                if (curNotices.isEmpty()) {
                    contextBuilder.append("No notices stored.\n")
                } else {
                    curNotices.take(5).forEach {
                        contextBuilder.append("- ${it.title} (Deadline: ${it.deadlineDate}): ${it.explanation}\n")
                    }
                }

                val aiResponseText = GeminiHelper.askAssistant(q, contextBuilder.toString())
                _chatMessages.value = _chatMessages.value + ChatMessage(sender = "AI", text = aiResponseText)
            } catch (e: Exception) {
                _chatMessages.value = _chatMessages.value + ChatMessage(
                    sender = "AI",
                    text = "AI service unavailable. Please check your internet or configuration.\n${e.message}",
                    isError = true
                )
            } finally {
                _isChatLoading.value = false
            }
        }
    }

    /**
     * Understands natural language statements (English & Bengali) to modify syllabus topics directly in SQLite Room.
     * E.g. "আজকে আমার Human Anatomy-এর Tissue chapterটা শেষ হয়ে গেছে"
     * -> Updates 'Tissue' under 'Human Anatomy' to COMPLETED, recalculates progress, and returns factual confirmation.
     * If ambiguous, asks for clarification instead of guessing.
     */
    private suspend fun tryExecuteAiSyllabusAction(userText: String): String? {
        val lower = userText.lowercase(Locale.ENGLISH)

        // Detect intent
        val isCompletion = lower.contains("শেষ") || lower.contains("পড়া শেষ") ||
                lower.contains("সম্পূর্ণ") || lower.contains("complete") ||
                lower.contains("finish") || lower.contains("finished") ||
                lower.contains("done") || lower.contains("studied")

        val isProgress = lower.contains("শুরু") || lower.contains("চলছে") ||
                lower.contains("start") || lower.contains("in progress")

        val targetStatus = when {
            isCompletion -> "COMPLETED"
            isProgress -> "IN_PROGRESS"
            else -> return null
        }

        val allTopics = syllabusTopics.value
        if (allTopics.isEmpty()) return null

        // 1. Identify candidate subject in user message
        val allSubjectNames = allTopics.map { it.subjectName }.distinct()
        val matchedSubjects = allSubjectNames.filter { subjName ->
            val cleanSubj = subjName.lowercase(Locale.ENGLISH)
            lower.contains(cleanSubj) || cleanSubj.split(" ").any { word -> word.length >= 4 && lower.contains(word) }
        }

        val candidateTopics = if (matchedSubjects.isNotEmpty()) {
            allTopics.filter { topic -> matchedSubjects.any { it.equals(topic.subjectName, ignoreCase = true) } }
        } else {
            allTopics
        }

        // 2. Search topic / chapter in candidateTopics
        val matchingTopics = candidateTopics.filter { topic ->
            val tName = topic.topicName.lowercase(Locale.ENGLISH)
            val cName = topic.chapterName.lowercase(Locale.ENGLISH)

            (tName.isNotBlank() && lower.contains(tName)) ||
            (cName.isNotBlank() && lower.contains(cName)) ||
            tName.split(" ", "-", "_", "/").any { word -> word.length >= 4 && lower.contains(word) } ||
            cName.split(" ", "-", "_", "/").any { word -> word.length >= 4 && lower.contains(word) }
        }

        if (matchingTopics.isEmpty()) {
            if (matchedSubjects.isNotEmpty()) {
                val sName = matchedSubjects.first()
                val sampleTopics = candidateTopics.take(4).joinToString("\n") { "• [${it.unitName}] ${it.topicName}" }
                return "Found subject '$sName', but could not identify the specific chapter or topic name from your message.\nAvailable topics under $sName:\n$sampleTopics\nPlease mention the exact topic name to update your syllabus."
            }
            return null
        }

        if (matchingTopics.size > 1) {
            // Ambiguous match: ask for clarification instead of guessing
            val topicOptions = matchingTopics.take(5).mapIndexed { idx, t ->
                "${idx + 1}. [${t.subjectName} • ${t.unitName}] ${t.topicName} (Status: ${t.status})"
            }.joinToString("\n")
            return "Found ${matchingTopics.size} matching topics for your request:\n$topicOptions\n\nPlease clarify which exact topic you would like to mark as $targetStatus."
        }

        // 3. Unique match! Execute real database action
        val matchedTopic = matchingTopics.first()
        repository.updateTopicStatus(matchedTopic.id, targetStatus)
        repository.logAIAction(
            actionType = "MARK_SYLLABUS_TOPIC_$targetStatus",
            subjectName = matchedTopic.subjectName,
            topicName = matchedTopic.topicName,
            summary = "Topic '${matchedTopic.topicName}' marked as $targetStatus via AI Assistant chat",
            details = "Unit: ${matchedTopic.unitName}, Subject: ${matchedTopic.subjectName}"
        )

        // Recalculate progress for that subject
        val subjectTopics = allTopics.filter { it.subjectName.equals(matchedTopic.subjectName, ignoreCase = true) }
        val newCompleted = subjectTopics.count {
            if (it.id == matchedTopic.id) targetStatus == "COMPLETED" else it.status == "COMPLETED"
        }
        val totalCount = subjectTopics.size
        val pct = if (totalCount > 0) ((newCompleted.toDouble() / totalCount.toDouble()) * 100).toInt() else 100

        showMessage("Syllabus updated: ${matchedTopic.topicName} -> $targetStatus")

        return "✓ [Database Updated] Topic '${matchedTopic.topicName}' under '${matchedTopic.subjectName}' has been marked as $targetStatus in your syllabus.\n\n" +
                "• Unit: ${matchedTopic.unitName}\n" +
                "• Chapter: ${matchedTopic.chapterName.ifBlank { "General" }}\n" +
                "• Subject Progress: $pct% ($newCompleted/$totalCount topics completed)\n" +
                "The syllabus tracker and home dashboard have been automatically updated."
    }
}
