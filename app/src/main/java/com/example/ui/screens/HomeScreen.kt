package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.EventNote
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.entities.ClassScheduleEntity
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.studyMateTextFieldColors
import com.example.ui.theme.AcademicAmber
import com.example.ui.theme.AcademicBlue
import com.example.ui.theme.AcademicTeal
import com.example.ui.theme.DangerRed
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.WarningOrange
import com.example.ui.viewmodel.StudyMateViewModel

@Composable
fun HomeScreen(
    viewModel: StudyMateViewModel,
    onNavigateToTab: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val searchRes by viewModel.searchResults.collectAsStateWithLifecycle()

    val todayClasses by viewModel.todayClasses.collectAsStateWithLifecycle()
    val nextClassInfo by viewModel.nextClassInfo.collectAsStateWithLifecycle()
    val attendanceSummaries by viewModel.attendanceSummaries.collectAsStateWithLifecycle()
    val syllabusTopics by viewModel.syllabusTopics.collectAsStateWithLifecycle()
    val notices by viewModel.notices.collectAsStateWithLifecycle()
    val tasks by viewModel.tasks.collectAsStateWithLifecycle()
    val allSchedules by viewModel.schedules.collectAsStateWithLifecycle()
    val currentUser by viewModel.currentUser.collectAsStateWithLifecycle()
    val todayHoliday by viewModel.todayHolidayException.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            val mime = context.contentResolver.getType(uri)
            viewModel.processUniversalUpload(uri, mime)
        }
    }

    val lowAttendanceSubjects = attendanceSummaries.filter { !it.isTargetAchieved && it.conductedClasses > 0 }
    val totalTopics = syllabusTopics.size
    val completedTopics = syllabusTopics.count { it.status == "COMPLETED" }
    val syllabusProgress = if (totalTopics > 0) completedTopics.toFloat() / totalTopics.toFloat() else 0f

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // App Header & Global Search Bar
        item {
            Spacer(modifier = Modifier.height(8.dp))
            val cal = java.util.Calendar.getInstance()
            val hour = cal.get(java.util.Calendar.HOUR_OF_DAY)
            val greeting = when (hour) {
                in 5..11 -> "Good morning"
                in 12..16 -> "Good afternoon"
                else -> "Good evening"
            }
            val studentName = currentUser?.fullName?.trim()?.substringBefore(" ") ?: "Student"

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "$greeting, $studentName",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = if (!currentUser?.course.isNullOrBlank()) "${currentUser?.course} • ${currentUser?.semester?.ifBlank { "StudyMate" } ?: "StudyMate"}" else "Your Academic Companion",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Profile Avatar Button
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(AcademicBlue)
                        .clickable { viewModel.selectTab("PROFILE") }
                        .testTag("home_profile_avatar_button"),
                    contentAlignment = Alignment.Center
                ) {
                    val initials = (currentUser?.fullName?.take(2) ?: "SM").uppercase()
                    Text(
                        text = initials,
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))

            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.updateSearchQuery(it) },
                placeholder = { Text("Search classes, subjects, syllabus, notices...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.updateSearchQuery("") }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear search")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                colors = studyMateTextFieldColors(),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("home_search_bar")
            )
        }

        // Quick Action: Universal Academic Upload Banner
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { filePickerLauncher.launch(arrayOf("*/*")) }
                    .testTag("home_quick_upload_card")
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(AcademicBlue.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.UploadFile,
                            contentDescription = "Upload Document",
                            tint = AcademicBlue,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Universal Document Intelligence",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Upload Timetable, Syllabus, Notice, or Notes (PDF/Image)",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Button(
                        onClick = { filePickerLauncher.launch(arrayOf("*/*")) },
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = AcademicBlue),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Upload", fontSize = 12.sp)
                    }
                }
            }
        }

        // Global Search Results (if searching)
        if (searchRes != null && searchQuery.isNotBlank()) {
            item {
                Text(
                    text = "Search Results (${searchRes!!.totalMatches} matches)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            if (searchRes!!.totalMatches == 0) {
                item {
                    EmptyStateCard(
                        title = "No results found",
                        subtitle = "No matching subject, class, topic, or notice found for '$searchQuery'."
                    )
                }
            } else {
                items(searchRes!!.classes) { cls ->
                    Card(
                        modifier = Modifier.fillMaxWidth().clickable { onNavigateToTab("ROUTINE") },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.DateRange, contentDescription = null, tint = AcademicBlue)
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(cls.subjectName, fontWeight = FontWeight.Bold)
                                Text("Class: ${cls.startTime} - ${cls.endTime} • Day ${cls.dayOfWeek}", fontSize = 12.sp)
                            }
                        }
                    }
                }

                items(searchRes!!.topics) { top ->
                    Card(
                        modifier = Modifier.fillMaxWidth().clickable { onNavigateToTab("SYLLABUS") },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = AcademicTeal)
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(top.topicName, fontWeight = FontWeight.Bold)
                                Text("Syllabus: ${top.subjectName} • ${top.chapterName}", fontSize = 12.sp)
                            }
                        }
                    }
                }

                items(searchRes!!.notices) { not ->
                    Card(
                        modifier = Modifier.fillMaxWidth().clickable { onNavigateToTab("DOCUMENTS") },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Notifications, contentDescription = null, tint = AcademicAmber)
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(not.title, fontWeight = FontWeight.Bold)
                                Text("Notice: ${not.explanation}", maxLines = 1, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        } else {
            // Normal Home Overview

            // Holiday Alert Banner (if today has an official holiday exception)
            if (todayHoliday != null) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = WarningOrange.copy(alpha = 0.15f)),
                        shape = RoundedCornerShape(16.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, WarningOrange.copy(alpha = 0.5f))
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .background(WarningOrange.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Warning, contentDescription = null, tint = WarningOrange)
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Official Holiday Today",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = WarningOrange
                                )
                                Text(
                                    text = "${todayHoliday!!.reason}. Scheduled recurring classes are suspended for today.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }

            // 1. Next Class Highlight Card
            item {
                if (nextClassInfo != null) {
                    val info = nextClassInfo!!
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = if (info.isHappeningNow) SuccessGreen.copy(alpha = 0.15f) else AcademicBlue.copy(alpha = 0.12f)
                        ),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = if (info.isHappeningNow) SuccessGreen else AcademicBlue
                                ) {
                                    Text(
                                        text = if (info.isHappeningNow) "IN PROGRESS NOW" else "NEXT CLASS IN ${info.startsInMinutes} MIN",
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = info.schedule.subjectName,
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "${info.schedule.startTime} - ${info.schedule.endTime}" +
                                            if (info.schedule.room.isNotBlank()) " • Room ${info.schedule.room}" else "" +
                                            if (info.schedule.teacher.isNotBlank()) " • ${info.schedule.teacher}" else "",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Icon(
                                imageVector = Icons.Default.Alarm,
                                contentDescription = null,
                                tint = if (info.isHappeningNow) SuccessGreen else AcademicBlue,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                    }
                }
            }

            // 2. Attendance Warning Alert (if any subject below target)
            item {
                if (lowAttendanceSubjects.isNotEmpty()) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onNavigateToTab("ATTENDANCE") },
                        colors = CardDefaults.cardColors(containerColor = DangerRed.copy(alpha = 0.1f)),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(DangerRed.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Warning, contentDescription = null, tint = DangerRed)
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Attendance Alert (${lowAttendanceSubjects.size} Subjects Below Target)",
                                    fontWeight = FontWeight.Bold,
                                    color = DangerRed,
                                    fontSize = 14.sp
                                )
                                val topLow = lowAttendanceSubjects.first()
                                Text(
                                    text = "${topLow.subject.name}: ${String.format("%.1f", topLow.percentage)}% (Target: ${topLow.subject.targetAttendance}%). Attend next ${topLow.requiredToReachTarget} classes consecutively to recover.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = DangerRed)
                        }
                    }
                }
            }

            // 3. Today's Classes Section
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Today's Schedule",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "View All Routine",
                        style = MaterialTheme.typography.labelMedium,
                        color = AcademicBlue,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.clickable { onNavigateToTab("ROUTINE") }
                    )
                }
            }

            if (todayClasses.isEmpty()) {
                item {
                    if (allSchedules.isEmpty()) {
                        EmptyStateCard(
                            title = "No routine added yet",
                            subtitle = "Upload your routine to get started with automatic timetable and class reminders.",
                            actionButtonText = "Upload Routine",
                            onActionClick = { onNavigateToTab("ROUTINE") }
                        )
                    } else {
                        EmptyStateCard(
                            title = "No classes scheduled today",
                            subtitle = "Enjoy your free day or prepare ahead using your syllabus and notes."
                        )
                    }
                }
            } else {
                items(todayClasses) { cls ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = cls.subjectName,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "${cls.startTime} - ${cls.endTime}" +
                                            if (cls.room.isNotBlank()) " • Room ${cls.room}" else "" +
                                            if (cls.teacher.isNotBlank()) " • ${cls.teacher}" else "",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = AcademicBlue.copy(alpha = 0.15f)
                            ) {
                                Text(
                                    text = cls.startTime,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = AcademicBlue
                                )
                            }
                        }
                    }
                }
            }

            // 4. Syllabus Progress Card
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onNavigateToTab("SYLLABUS") },
                    colors = CardDefaults.cardColors(containerColor = AcademicTeal.copy(alpha = 0.08f)),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Syllabus Progress",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleSmall,
                                color = AcademicTeal
                            )
                            Text(
                                text = "$completedTopics / $totalTopics Topics (${(syllabusProgress * 100).toInt()}%)",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = AcademicTeal
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { syllabusProgress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = AcademicTeal,
                            trackColor = AcademicTeal.copy(alpha = 0.2f)
                        )
                        if (totalTopics == 0) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Tap to upload or add syllabus topics",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // 5. Important Notices / Deadlines
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Notices & Deadlines",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "View Documents",
                        style = MaterialTheme.typography.labelMedium,
                        color = AcademicBlue,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.clickable { onNavigateToTab("DOCUMENTS") }
                    )
                }
            }

            if (notices.isEmpty() && tasks.isEmpty()) {
                item {
                    EmptyStateCard(
                        title = "No notices or pending tasks",
                        subtitle = "Upload an academic circular or notice to extract deadlines and class changes automatically."
                    )
                }
            } else {
                items(tasks.take(3)) { task ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.AutoMirrored.Filled.EventNote, contentDescription = null, tint = AcademicAmber)
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(task.title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                Text("Due: ${task.dueDate} • ${task.subjectName}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}
