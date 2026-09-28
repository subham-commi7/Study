package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import com.example.data.auth.SecurityUtils
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.entities.AIActionEntity
import com.example.data.local.entities.DocumentEntity
import com.example.data.local.entities.FriendChatMessageEntity
import com.example.data.local.entities.FriendshipEntity
import com.example.data.local.entities.UserEntity
import com.example.ui.components.studyMateTextFieldColors
import com.example.ui.theme.AcademicBlue
import com.example.ui.theme.AcademicTeal
import com.example.ui.theme.DangerRed
import com.example.ui.theme.Slate100
import com.example.ui.theme.Slate200
import com.example.ui.theme.Slate50
import com.example.ui.theme.Slate700
import com.example.ui.theme.Slate800
import com.example.ui.theme.Slate900
import com.example.ui.theme.SuccessGreen
import com.example.ui.viewmodel.StudyMateViewModel
import kotlinx.coroutines.flow.Flow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ProfileScreen(
    viewModel: StudyMateViewModel,
    onBack: () -> Unit = { viewModel.selectTab("HOME") }
) {
    val currentUser by viewModel.currentUser.collectAsStateWithLifecycle()
    val subjects by viewModel.subjects.collectAsStateWithLifecycle()
    val schedules by viewModel.schedules.collectAsStateWithLifecycle()
    val documents by viewModel.documents.collectAsStateWithLifecycle()
    val topics by viewModel.syllabusTopics.collectAsStateWithLifecycle()
    val reminderMinutes by viewModel.reminderMinutes.collectAsStateWithLifecycle()
    val friends by viewModel.friends.collectAsStateWithLifecycle()
    val pendingRequests by viewModel.pendingFriendRequests.collectAsStateWithLifecycle()
    val aiActions by viewModel.recentAIActions.collectAsStateWithLifecycle()

    ProfileScreen(
        user = currentUser,
        subjectCount = subjects.size,
        classCount = schedules.size,
        documentCount = documents.size,
        syllabusCount = topics.size,
        reminderMinutes = reminderMinutes,
        friends = friends,
        pendingRequests = pendingRequests,
        aiActions = aiActions,
        allDocuments = documents,
        onUpdateReminderTiming = { viewModel.updateReminderTiming(it) },
        onUpdateProfile = { college, course, semester, year, group ->
            viewModel.saveAcademicProfile(college, course, semester, year, group)
        },
        onLogout = { viewModel.logout() },
        onSearchStudent = { query, callback -> viewModel.searchStudent(query, callback) },
        onSendFriendRequest = { targetId -> viewModel.sendFriendRequest(targetId) },
        onAcceptFriendRequest = { friendId -> viewModel.acceptFriendRequest(friendId) },
        onDeclineFriendRequest = { friendId -> viewModel.declineFriendRequest(friendId) },
        onRemoveFriend = { friendId -> viewModel.removeFriend(friendId) },
        getFriendChatMessages = { friendId -> viewModel.getFriendChatMessages(friendId) },
        onSendFriendChatMessage = { friendId, text, doc -> viewModel.sendFriendChatMessage(friendId, text, doc) },
        onBack = onBack
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    user: UserEntity?,
    subjectCount: Int,
    classCount: Int,
    documentCount: Int,
    syllabusCount: Int,
    reminderMinutes: Int,
    friends: List<FriendshipEntity> = emptyList(),
    pendingRequests: List<FriendshipEntity> = emptyList(),
    aiActions: List<AIActionEntity> = emptyList(),
    allDocuments: List<DocumentEntity> = emptyList(),
    onUpdateReminderTiming: (minutes: Int) -> Unit,
    onUpdateProfile: (college: String, course: String, semester: String, year: String, group: String) -> Unit,
    onLogout: () -> Unit,
    onSearchStudent: (query: String, (List<UserEntity>) -> Unit) -> Unit = { _, _ -> },
    onSendFriendRequest: (String) -> Unit = {},
    onAcceptFriendRequest: (String) -> Unit = {},
    onDeclineFriendRequest: (String) -> Unit = {},
    onRemoveFriend: (String) -> Unit = {},
    getFriendChatMessages: ((String) -> Flow<List<FriendChatMessageEntity>>)? = null,
    onSendFriendChatMessage: (String, String, DocumentEntity?) -> Unit = { _, _, _ -> },
    onBack: () -> Unit = {}
) {
    BackHandler { onBack() }

    val context = LocalContext.current
    val clipboardManager = remember { context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager }

    var showEditProfileDialog by remember { mutableStateOf(false) }
    var showLogoutDialog by remember { mutableStateOf(false) }
    var showFriendsDialog by remember { mutableStateOf(false) }
    var activeChatFriend by remember { mutableStateOf<FriendshipEntity?>(null) }
    var viewingDocument by remember { mutableStateOf<DocumentEntity?>(null) }

    // Edit Profile form fields
    var editCollege by remember(user) { mutableStateOf(user?.college ?: "") }
    var editCourse by remember(user) { mutableStateOf(user?.course ?: "") }
    var editSemester by remember(user) { mutableStateOf(user?.semester ?: "") }
    var editYear by remember(user) { mutableStateOf(user?.year ?: "") }
    var editGroup by remember(user) { mutableStateOf(user?.groupSection ?: "") }

    val scrollState = rememberScrollState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Academic Profile & Network",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = Slate900
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("profile_back_button")) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Slate900
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        },
        containerColor = Slate50
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // User Card
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF94A3B8)),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("user_profile_card")
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // User Avatar
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(AcademicBlue),
                            contentAlignment = Alignment.Center
                        ) {
                            if (!user?.photoUrl.isNullOrBlank()) {
                                AsyncImage(
                                    model = user?.photoUrl,
                                    contentDescription = "Profile Photo",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                val initials = SecurityUtils.computeInitials(user?.fullName)
                                Text(
                                    text = initials,
                                    color = Color.White,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = user?.fullName ?: "Student",
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                color = Slate900
                            )
                            Text(
                                text = user?.email ?: "student@university.edu",
                                fontSize = 13.sp,
                                color = Slate700
                            )
                            if (!user?.course.isNullOrBlank() || !user?.college.isNullOrBlank()) {
                                Text(
                                    text = listOfNotNull(
                                        user?.course?.takeIf { it.isNotBlank() },
                                        user?.college?.takeIf { it.isNotBlank() }
                                    ).joinToString(" • "),
                                    fontSize = 12.sp,
                                    color = AcademicBlue,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(top = 2.dp)
                                )
                            }
                        }

                        IconButton(
                            onClick = { showEditProfileDialog = true },
                            modifier = Modifier.testTag("edit_profile_button")
                        ) {
                            Icon(imageVector = Icons.Default.Edit, contentDescription = "Edit Profile", tint = AcademicBlue)
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 14.dp), color = Slate200)

                    // Academic Details Grid
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        ProfileInfoItem("Semester", user?.semester?.ifBlank { "Not set" } ?: "Not set")
                        ProfileInfoItem("Year", user?.year?.ifBlank { "Not set" } ?: "Not set")
                        ProfileInfoItem("Group", user?.groupSection?.ifBlank { "Not set" } ?: "Not set")
                    }
                }
            }

            // 8-Digit Official Student ID Card (Sections 5 & 6)
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, AcademicBlue.copy(alpha = 0.5f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("student_id_card")
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Default.Badge, contentDescription = null, tint = AcademicBlue, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = "Official Student ID", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Slate900)
                        }

                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = AcademicBlue.copy(alpha = 0.1f)
                        ) {
                            Text(
                                text = "Permanent ID",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = AcademicBlue,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    val studentIdStr = user?.studentId?.ifBlank { "--------" } ?: "--------"
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Slate100,
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF94A3B8)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                        ) {
                            Column {
                                Text(
                                    text = "STUDENT IDENTIFIER",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Slate700
                                )
                                Text(
                                    text = studentIdStr,
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontFamily = FontFamily.Monospace,
                                    color = AcademicBlue
                                )
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                IconButton(
                                    onClick = {
                                        if (user != null && user.studentId.isNotBlank()) {
                                            val clip = ClipData.newPlainText("StudyMate Student ID", user.studentId)
                                            clipboardManager.setPrimaryClip(clip)
                                            Toast.makeText(context, "Student ID copied to clipboard!", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    modifier = Modifier.testTag("copy_student_id_button")
                                ) {
                                    Icon(imageVector = Icons.Default.ContentCopy, contentDescription = "Copy ID", tint = AcademicBlue)
                                }

                                IconButton(
                                    onClick = {
                                        if (user != null && user.studentId.isNotBlank()) {
                                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                                type = "text/plain"
                                                putExtra(Intent.EXTRA_SUBJECT, "My StudyMate Student ID")
                                                putExtra(
                                                    Intent.EXTRA_TEXT,
                                                    "Connect with me on StudyMate! My Student ID is: ${user.studentId}\nSearch my ID to collaborate, study together, and share academic documents."
                                                )
                                            }
                                            context.startActivity(Intent.createChooser(shareIntent, "Share Student ID"))
                                        }
                                    },
                                    modifier = Modifier.testTag("share_student_id_button")
                                ) {
                                    Icon(imageVector = Icons.Default.Share, contentDescription = "Share ID", tint = AcademicBlue)
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Use this 8-digit numeric ID to connect with classmates. It is permanently linked to your account.",
                        fontSize = 11.sp,
                        color = Slate700
                    )
                }
            }

            // Study Network & Friends Card (Sections 7, 8, 40, 41)
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF94A3B8)),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("friends_network_card")
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Default.People, contentDescription = null, tint = AcademicBlue, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(text = "Study Network & Friends", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Slate900)
                                Text(text = "${friends.size} connected classmates", fontSize = 12.sp, color = Slate700)
                            }
                        }

                        Button(
                            onClick = { showFriendsDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = AcademicBlue),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.testTag("manage_friends_button")
                        ) {
                            Icon(imageVector = Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (pendingRequests.isNotEmpty()) "${pendingRequests.size} Pending" else "Collaborate",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    if (friends.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            friends.take(3).forEach { friend ->
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = Slate100,
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Slate200),
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable { activeChatFriend = friend }
                                ) {
                                    Column(
                                        modifier = Modifier.padding(10.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text(text = friend.friendName.take(12), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Slate900, maxLines = 1)
                                        Text(text = "Chat & Share", fontSize = 10.sp, color = AcademicBlue, fontWeight = FontWeight.Medium)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Academic Statistics
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF94A3B8)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.Storage, contentDescription = null, tint = AcademicBlue, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = "Academic Records Summary", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Slate900)
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        StatTile("Subjects", subjectCount.toString())
                        StatTile("Classes", classCount.toString())
                        StatTile("Documents", documentCount.toString())
                        StatTile("Syllabus", syllabusCount.toString())
                    }
                }
            }

            // Notification Reminders Timing Setting
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF94A3B8)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.Alarm, contentDescription = null, tint = AcademicBlue, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(text = "Class Reminder Timing", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Slate900)
                            Text(text = "Receive alerts before scheduled routine classes", fontSize = 12.sp, color = Slate700)
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    val timingOptions = listOf(5, 10, 15, 30)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        timingOptions.forEach { minutes ->
                            val isSelected = reminderMinutes == minutes
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (isSelected) AcademicBlue else Slate100,
                                border = if (isSelected) null else androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF94A3B8)),
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { onUpdateReminderTiming(minutes) }
                                    .testTag("reminder_option_$minutes")
                            ) {
                                Text(
                                    text = "$minutes min",
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) Color.White else Slate900,
                                    modifier = Modifier.padding(vertical = 10.dp),
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                            }
                        }
                    }
                }
            }

            // AI Action History Card (Section 30)
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF94A3B8)),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("ai_action_history_card")
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.AutoAwesome, contentDescription = null, tint = AcademicBlue, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(text = "AI Academic Action History", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Slate900)
                            Text(text = "Traceable record of automated syllabus & database modifications", fontSize = 12.sp, color = Slate700)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    if (aiActions.isEmpty()) {
                        Text(
                            text = "No AI actions recorded yet. When you update syllabus via AI Chat, actions will be logged here.",
                            fontSize = 12.sp,
                            color = Slate700,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    } else {
                        aiActions.take(5).forEach { action ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(SuccessGreen)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = action.summary,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Slate900
                                    )
                                    val dateStr = SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault()).format(Date(action.timestamp))
                                    Text(
                                        text = "$dateStr • ${action.actionType}",
                                        fontSize = 10.sp,
                                        color = Slate700
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // About StudyMate Card
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF94A3B8)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(text = "About StudyMate", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Slate900)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Production academic companion engineered for university students. Features automated timetable reading, attendance tracking, full-course syllabus breakdown, notice analysis, and 1-on-1 classmate collaboration.",
                        fontSize = 12.sp,
                        color = Slate700,
                        lineHeight = 18.sp
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    FeatureBullet("Routine Engine", "2D table parsing with effective date versioning")
                    FeatureBullet("Attendance Rule", "Strict daily locking with safe-cut forecast")
                    FeatureBullet("Course Syllabus", "Subject-specific hierarchy with non-trackable filter")
                    FeatureBullet("Student ID", "Permanent 8-digit identifier for classmate connections")
                }
            }

            // Logout Button
            Button(
                onClick = { showLogoutDialog = true },
                colors = ButtonDefaults.buttonColors(containerColor = DangerRed.copy(alpha = 0.1f)),
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, DangerRed.copy(alpha = 0.3f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("logout_button")
            ) {
                Icon(imageVector = Icons.Default.ExitToApp, contentDescription = null, tint = DangerRed, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = "Log Out of StudyMate", color = DangerRed, fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }

    // Edit Profile Dialog
    if (showEditProfileDialog) {
        AlertDialog(
            onDismissRequest = { showEditProfileDialog = false },
            title = {
                Text(text = "Edit Academic Profile", fontWeight = FontWeight.Bold, fontSize = 17.sp, color = MaterialTheme.colorScheme.onSurface)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = editCollege,
                        onValueChange = { editCollege = it },
                        label = { Text("College / University") },
                        singleLine = true,
                        colors = studyMateTextFieldColors(),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = editCourse,
                        onValueChange = { editCourse = it },
                        label = { Text("Course / Department") },
                        singleLine = true,
                        colors = studyMateTextFieldColors(),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = editSemester,
                            onValueChange = { editSemester = it },
                            label = { Text("Semester") },
                            singleLine = true,
                            colors = studyMateTextFieldColors(),
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        OutlinedTextField(
                            value = editYear,
                            onValueChange = { editYear = it },
                            label = { Text("Year") },
                            singleLine = true,
                            colors = studyMateTextFieldColors(),
                            modifier = Modifier.weight(1f)
                        )
                    }
                    OutlinedTextField(
                        value = editGroup,
                        onValueChange = { editGroup = it },
                        label = { Text("Group / Section") },
                        singleLine = true,
                        colors = studyMateTextFieldColors(),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onUpdateProfile(editCollege, editCourse, editSemester, editYear, editGroup)
                        showEditProfileDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AcademicBlue)
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditProfileDialog = false }) {
                    Text("Cancel", color = Slate700)
                }
            }
        )
    }

    // Logout Confirmation Dialog
    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = {
                Text(text = "Log Out?", fontWeight = FontWeight.Bold, fontSize = 17.sp, color = Slate900)
            },
            text = {
                Text(
                    text = "Are you sure you want to log out of StudyMate? Your academic data is safely stored on your device and will be ready when you log in again.",
                    fontSize = 13.sp,
                    color = Slate700
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showLogoutDialog = false
                        onLogout()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = DangerRed)
                ) {
                    Text("Log Out")
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) {
                    Text("Cancel", color = Slate700)
                }
            }
        )
    }

    // Study Network & Friends Dialog (Sections 7 & 8)
    if (showFriendsDialog) {
        FriendsManagementDialog(
            user = user,
            friends = friends,
            pendingRequests = pendingRequests,
            onClose = { showFriendsDialog = false },
            onSearchStudent = onSearchStudent,
            onSendFriendRequest = onSendFriendRequest,
            onAcceptFriendRequest = onAcceptFriendRequest,
            onDeclineFriendRequest = onDeclineFriendRequest,
            onRemoveFriend = onRemoveFriend,
            onStartChat = { friend ->
                showFriendsDialog = false
                activeChatFriend = friend
            }
        )
    }

    // Academic 1-on-1 Chat Dialog (Sections 40, 41, 42)
    val chatFlowProvider = getFriendChatMessages
    val currentChatFriend = activeChatFriend
    if (currentChatFriend != null && chatFlowProvider != null) {
        AcademicChatDialog(
            user = user,
            friend = currentChatFriend,
            allDocuments = allDocuments,
            messagesFlow = chatFlowProvider(currentChatFriend.friendStudentId),
            onSendMessage = { text, doc ->
                onSendFriendChatMessage(currentChatFriend.friendStudentId, text, doc)
            },
            onViewDocument = { docId ->
                val targetDoc = allDocuments.firstOrNull { it.id == docId }
                if (targetDoc != null) {
                    viewingDocument = targetDoc
                } else {
                    Toast.makeText(context, "Document not available locally", Toast.LENGTH_SHORT).show()
                }
            },
            onClose = { activeChatFriend = null }
        )
    }

    // In-App PDF Viewer Dialog
    if (viewingDocument != null) {
        PdfViewerScreen(
            document = viewingDocument!!,
            onBack = { viewingDocument = null }
        )
    }
}

@Composable
fun FriendsManagementDialog(
    user: UserEntity?,
    friends: List<FriendshipEntity>,
    pendingRequests: List<FriendshipEntity>,
    onClose: () -> Unit,
    onSearchStudent: (query: String, (List<UserEntity>) -> Unit) -> Unit,
    onSendFriendRequest: (String) -> Unit,
    onAcceptFriendRequest: (String) -> Unit,
    onDeclineFriendRequest: (String) -> Unit,
    onRemoveFriend: (String) -> Unit,
    onStartChat: (FriendshipEntity) -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(if (pendingRequests.isNotEmpty()) 1 else 0) }
    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<UserEntity>>(emptyList()) }
    var hasSearched by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            color = Color.White
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "Study Network & Friends", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Slate900)
                    IconButton(onClick = onClose) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Close", tint = Slate700)
                    }
                }

                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = Slate100,
                    contentColor = AcademicBlue
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("Friends (${friends.size})", fontWeight = FontWeight.Bold) }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("Requests (${pendingRequests.size})", fontWeight = FontWeight.Bold) }
                    )
                    Tab(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        text = { Text("Find by ID", fontWeight = FontWeight.Bold) }
                    )
                }

                when (selectedTab) {
                    0 -> {
                        // Friends List
                        if (friends.isEmpty()) {
                            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(imageVector = Icons.Default.People, contentDescription = null, tint = Color(0xFF94A3B8), modifier = Modifier.size(48.dp))
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Text("No connected friends yet.", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Slate900)
                                    Text("Search by 8-digit Student ID to add classmates.", fontSize = 12.sp, color = Slate700)
                                }
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.weight(1f).fillMaxWidth().padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                items(friends) { friend ->
                                    Card(
                                        colors = CardDefaults.cardColors(containerColor = Slate50),
                                        shape = RoundedCornerShape(12.dp),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, Slate200),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(text = friend.friendName, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Slate900)
                                                Text(text = "ID: ${friend.friendStudentId}", fontSize = 12.sp, fontFamily = FontFamily.Monospace, color = AcademicBlue, fontWeight = FontWeight.SemiBold)
                                                if (friend.friendCourse.isNotBlank() || friend.friendCollege.isNotBlank()) {
                                                    Text(text = "${friend.friendCourse} • ${friend.friendCollege}", fontSize = 11.sp, color = Slate700)
                                                }
                                            }

                                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                Button(
                                                    onClick = { onStartChat(friend) },
                                                    colors = ButtonDefaults.buttonColors(containerColor = AcademicBlue),
                                                    shape = RoundedCornerShape(8.dp),
                                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                                ) {
                                                    Icon(imageVector = Icons.Default.Chat, contentDescription = null, modifier = Modifier.size(14.dp))
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text("Chat", fontSize = 12.sp)
                                                }

                                                IconButton(onClick = { onRemoveFriend(friend.friendStudentId) }) {
                                                    Icon(imageVector = Icons.Default.Close, contentDescription = "Remove", tint = DangerRed, modifier = Modifier.size(18.dp))
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    1 -> {
                        // Requests
                        if (pendingRequests.isEmpty()) {
                            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                                Text("No pending friend requests.", fontSize = 13.sp, color = Slate700)
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.weight(1f).fillMaxWidth().padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                items(pendingRequests) { req ->
                                    Card(
                                        colors = CardDefaults.cardColors(containerColor = Slate50),
                                        shape = RoundedCornerShape(12.dp),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, Slate200),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                                            Text(text = req.friendName, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Slate900)
                                            Text(text = "Student ID: ${req.friendStudentId}", fontSize = 12.sp, fontFamily = FontFamily.Monospace, color = AcademicBlue)
                                            if (req.friendCourse.isNotBlank()) {
                                                Text(text = "${req.friendCourse} • ${req.friendCollege}", fontSize = 11.sp, color = Slate700)
                                            }

                                            Spacer(modifier = Modifier.height(10.dp))

                                            if (req.isInitiator) {
                                                Surface(
                                                    shape = RoundedCornerShape(6.dp),
                                                    color = Slate200
                                                ) {
                                                    Text(text = "Outgoing Request Sent (Pending Approval)", fontSize = 11.sp, color = Slate700, modifier = Modifier.padding(6.dp))
                                                }
                                            } else {
                                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                    Button(
                                                        onClick = { onAcceptFriendRequest(req.friendStudentId) },
                                                        colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen),
                                                        shape = RoundedCornerShape(8.dp),
                                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                                    ) {
                                                        Icon(imageVector = Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp))
                                                        Spacer(modifier = Modifier.width(4.dp))
                                                        Text("Accept", fontSize = 12.sp)
                                                    }

                                                    OutlinedButton(
                                                        onClick = { onDeclineFriendRequest(req.friendStudentId) },
                                                        shape = RoundedCornerShape(8.dp),
                                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                                    ) {
                                                        Text("Decline", fontSize = 12.sp, color = Slate700)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    2 -> {
                        // Find by Student ID
                        Column(modifier = Modifier.weight(1f).fillMaxWidth().padding(16.dp)) {
                            Text(text = "Search Classmate by 8-Digit Student ID", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Slate900)
                            Spacer(modifier = Modifier.height(8.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedTextField(
                                    value = searchQuery,
                                    onValueChange = { searchQuery = it.take(8) },
                                    placeholder = { Text("e.g. 48271635") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Search),
                                    keyboardActions = KeyboardActions(onSearch = {
                                        if (searchQuery.isNotBlank()) {
                                            hasSearched = true
                                            onSearchStudent(searchQuery) { searchResults = it }
                                        }
                                    }),
                                    colors = studyMateTextFieldColors(),
                                    singleLine = true,
                                    modifier = Modifier.weight(1f).testTag("search_student_id_input")
                                )

                                Spacer(modifier = Modifier.width(8.dp))

                                Button(
                                    onClick = {
                                        if (searchQuery.isNotBlank()) {
                                            hasSearched = true
                                            onSearchStudent(searchQuery) { searchResults = it }
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = AcademicBlue),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.testTag("search_student_id_button")
                                ) {
                                    Icon(imageVector = Icons.Default.Search, contentDescription = "Search")
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            if (hasSearched && searchResults.isEmpty()) {
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = Slate100,
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)
                                ) {
                                    Text(
                                        text = "No student found matching '$searchQuery'. Please verify the 8-digit Student ID.",
                                        fontSize = 12.sp,
                                        color = Slate700,
                                        modifier = Modifier.padding(14.dp)
                                    )
                                }
                            } else {
                                LazyColumn(
                                    verticalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    items(searchResults) { target ->
                                        Card(
                                            colors = CardDefaults.cardColors(containerColor = Slate50),
                                            shape = RoundedCornerShape(12.dp),
                                            border = androidx.compose.foundation.BorderStroke(1.dp, Slate200),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth().padding(14.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(text = target.fullName, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Slate900)
                                                    Text(text = "ID: ${target.studentId}", fontSize = 12.sp, fontFamily = FontFamily.Monospace, color = AcademicBlue, fontWeight = FontWeight.Bold)
                                                    if (target.course.isNotBlank() || target.college.isNotBlank()) {
                                                        Text(text = "${target.course} • ${target.college}", fontSize = 11.sp, color = Slate700)
                                                    }
                                                }

                                                Button(
                                                    onClick = { onSendFriendRequest(target.studentId) },
                                                    colors = ButtonDefaults.buttonColors(containerColor = AcademicBlue),
                                                    shape = RoundedCornerShape(8.dp),
                                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                                    modifier = Modifier.testTag("send_friend_request_btn")
                                                ) {
                                                    Icon(imageVector = Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(14.dp))
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text("Add", fontSize = 12.sp)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AcademicChatDialog(
    user: UserEntity?,
    friend: FriendshipEntity,
    allDocuments: List<DocumentEntity>,
    messagesFlow: Flow<List<FriendChatMessageEntity>>,
    onSendMessage: (text: String, doc: DocumentEntity?) -> Unit,
    onViewDocument: (Long) -> Unit,
    onClose: () -> Unit
) {
    val messages by messagesFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    var inputText by remember { mutableStateOf("") }
    var showAttachmentPicker by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            shape = RoundedCornerShape(16.dp),
            color = Color.White
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Chat Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Slate100)
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(text = friend.friendName, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Slate900)
                        Text(text = "Student ID: ${friend.friendStudentId}", fontSize = 11.sp, color = AcademicBlue, fontFamily = FontFamily.Monospace)
                    }

                    IconButton(onClick = onClose) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Close", tint = Slate700)
                    }
                }

                // Chat Messages
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (messages.isEmpty()) {
                        item {
                            Box(modifier = Modifier.fillMaxWidth().padding(top = 40.dp), contentAlignment = Alignment.Center) {
                                Text(
                                    text = "Start academic collaboration with ${friend.friendName}!\nYou can exchange study questions and share lecture PDFs.",
                                    fontSize = 12.sp,
                                    color = Slate700,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                            }
                        }
                    }

                    items(messages) { msg ->
                        val isMe = msg.senderStudentId == (user?.studentId ?: "")
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = if (isMe) Alignment.End else Alignment.Start
                        ) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (isMe) AcademicBlue else Slate100,
                                modifier = Modifier.fillMaxWidth(0.85f)
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    if (msg.messageText.isNotBlank()) {
                                        Text(
                                            text = msg.messageText,
                                            fontSize = 13.sp,
                                            color = if (isMe) Color.White else Slate900
                                        )
                                    }

                                    // Attachment
                                    if (msg.attachmentDocumentId != null && msg.attachmentFileName.isNotBlank()) {
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = if (isMe) Color.White.copy(alpha = 0.15f) else Color.White,
                                            border = androidx.compose.foundation.BorderStroke(1.dp, if (isMe) Color.White.copy(alpha = 0.3f) else Slate200),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable { onViewDocument(msg.attachmentDocumentId) }
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(8.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Description,
                                                    contentDescription = null,
                                                    tint = if (isMe) Color.White else AcademicBlue,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    text = msg.attachmentFileName,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (isMe) Color.White else Slate900,
                                                    maxLines = 1,
                                                    modifier = Modifier.weight(1f)
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(
                                                    text = "[Open]",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (isMe) Color.White else AcademicBlue
                                                )
                                            }
                                        }
                                    }

                                    val timeStr = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(msg.timestamp))
                                    Text(
                                        text = timeStr,
                                        fontSize = 10.sp,
                                        color = if (isMe) Color.White.copy(alpha = 0.7f) else Slate700,
                                        modifier = Modifier.align(Alignment.End).padding(top = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // Chat Input Row
                Surface(
                    color = Slate50,
                    border = androidx.compose.foundation.BorderStroke(1.dp, Slate200),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { showAttachmentPicker = true }) {
                            Icon(imageVector = Icons.Default.AttachFile, contentDescription = "Attach Document", tint = AcademicBlue)
                        }

                        OutlinedTextField(
                            value = inputText,
                            onValueChange = { inputText = it },
                            placeholder = { Text("Type academic message...", fontSize = 13.sp) },
                            colors = studyMateTextFieldColors(),
                            shape = RoundedCornerShape(20.dp),
                            modifier = Modifier.weight(1f),
                            maxLines = 3
                        )

                        Spacer(modifier = Modifier.width(6.dp))

                        IconButton(
                            onClick = {
                                if (inputText.isNotBlank()) {
                                    val text = inputText
                                    inputText = ""
                                    onSendMessage(text, null)
                                }
                            },
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(if (inputText.isNotBlank()) AcademicBlue else Slate200)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Send,
                                contentDescription = "Send",
                                tint = if (inputText.isNotBlank()) Color.White else Slate700,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    // Attachment Picker Dialog
    if (showAttachmentPicker) {
        AlertDialog(
            onDismissRequest = { showAttachmentPicker = false },
            title = {
                Text(text = "Attach Academic Document", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Slate900)
            },
            text = {
                if (allDocuments.isEmpty()) {
                    Text("No documents stored in your library yet. Upload a syllabus or routine first to share it with classmates.", fontSize = 12.sp, color = Slate700)
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(allDocuments) { doc ->
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Slate50,
                                border = androidx.compose.foundation.BorderStroke(1.dp, Slate200),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        showAttachmentPicker = false
                                        onSendMessage("Sharing document: ${doc.title.ifBlank { doc.fileName }}", doc)
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(imageVector = Icons.Default.Description, contentDescription = null, tint = AcademicBlue, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(text = doc.title.ifBlank { doc.fileName }, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Slate900, maxLines = 1)
                                        Text(text = "${doc.type} • ${maxOf(1L, doc.fileSize / 1024)} KB", fontSize = 10.sp, color = Slate700)
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAttachmentPicker = false }) {
                    Text("Cancel", color = Slate700)
                }
            }
        )
    }
}

@Composable
private fun ProfileInfoItem(label: String, value: String) {
    Column {
        Text(text = label, fontSize = 11.sp, color = Slate700)
        Text(text = value, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Slate900, modifier = Modifier.padding(top = 2.dp))
    }
}

@Composable
private fun StatTile(label: String, count: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = count, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = AcademicBlue)
        Text(text = label, fontSize = 11.sp, color = Slate700, modifier = Modifier.padding(top = 2.dp))
    }
}

@Composable
private fun FeatureBullet(title: String, desc: String) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(text = "• $title", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Slate900)
        Text(text = "   $desc", fontSize = 12.sp, color = Slate700)
    }
}
