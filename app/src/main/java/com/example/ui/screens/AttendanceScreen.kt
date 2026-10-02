package com.example.ui.screens

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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.entities.AttendanceRecordEntity
import com.example.data.local.entities.ClassScheduleEntity
import com.example.data.local.entities.SubjectEntity
import com.example.data.model.SubjectAttendanceSummary
import com.example.ui.components.EmptyStateCard
import com.example.ui.theme.AcademicBlue
import com.example.ui.theme.DangerRed
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.WarningOrange
import com.example.ui.viewmodel.StudyMateViewModel
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Three distinct Day Tabs for Attendance:
 * YESTERDAY | TODAY | TOMORROW
 * TODAY is the active/default tab on launch.
 */
enum class AttendanceDayTab {
    YESTERDAY,
    TODAY,
    TOMORROW
}

/**
 * Computed metadata for a day tab using actual device/calendar date and time.
 */
data class DayTabInfo(
    val tab: AttendanceDayTab,
    val title: String,        // "YESTERDAY", "TODAY", "TOMORROW"
    val dateLabel: String,    // e.g. "Tue, 29 Sep"
    val dateString: String,   // "YYYY-MM-DD" (used for database attendance records)
    val dateMillis: Long,     // calendar epoch millis
    val dayOfWeek: Int        // 1 = Monday, 2 = Tuesday, ..., 7 = Sunday
)

/**
 * Computes calendar information for the given tab using Asia-Kolkata / local device time.
 * Correctly handles weekend rollovers (e.g. Sunday -> Monday, Saturday -> Sunday).
 */
fun computeDayTabInfo(tab: AttendanceDayTab, baseTimeMillis: Long): DayTabInfo {
    val timeZone = TimeZone.getTimeZone("Asia/Kolkata")
    val cal = Calendar.getInstance(timeZone).apply {
        timeInMillis = baseTimeMillis
        when (tab) {
            AttendanceDayTab.YESTERDAY -> add(Calendar.DAY_OF_YEAR, -1)
            AttendanceDayTab.TODAY -> { /* today */ }
            AttendanceDayTab.TOMORROW -> add(Calendar.DAY_OF_YEAR, 1)
        }
    }

    // Java Calendar: SUNDAY=1, MONDAY=2, ..., SATURDAY=7
    // StudyMate Routine: 1=Monday, 2=Tuesday, ..., 7=Sunday
    val calDay = cal.get(Calendar.DAY_OF_WEEK)
    val studyMateDayOfWeek = if (calDay == Calendar.SUNDAY) 7 else calDay - 1

    val dateStringFormat = SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH).apply {
        this.timeZone = timeZone
    }
    val dateLabelFormat = SimpleDateFormat("EEE, d MMM", Locale.ENGLISH).apply {
        this.timeZone = timeZone
    }

    val dateString = dateStringFormat.format(cal.time)
    val dateLabel = dateLabelFormat.format(cal.time)

    val title = when (tab) {
        AttendanceDayTab.YESTERDAY -> "YESTERDAY"
        AttendanceDayTab.TODAY -> "TODAY"
        AttendanceDayTab.TOMORROW -> "TOMORROW"
    }

    return DayTabInfo(
        tab = tab,
        title = title,
        dateLabel = dateLabel,
        dateString = dateString,
        dateMillis = cal.timeInMillis,
        dayOfWeek = studyMateDayOfWeek
    )
}

/**
 * Converts 24-hour "HH:mm" time strings to clean 12-hour AM/PM format.
 * E.g. ("10:00", "11:00") -> "10:00 AM – 11:00 AM"
 */
fun formatClassTimeRange(startTime: String, endTime: String): String {
    fun to12Hour(t: String): String {
        val trimmed = t.trim()
        val parts = trimmed.split(":")
        if (parts.size >= 2) {
            val h = parts[0].toIntOrNull()
            val m = parts[1].toIntOrNull()
            if (h != null && m != null) {
                val ampm = if (h >= 12) "PM" else "AM"
                val h12 = when {
                    h == 0 -> 12
                    h > 12 -> h - 12
                    else -> h
                }
                return String.format(Locale.ENGLISH, "%d:%02d %s", h12, m, ampm)
            }
        }
        return trimmed
    }

    val s = to12Hour(startTime)
    val e = to12Hour(endTime)
    return if (e.isNotBlank() && s.isNotBlank()) "$s – $e" else if (s.isNotBlank()) s else "$startTime – $endTime"
}

@Composable
fun AttendanceScreen(
    viewModel: StudyMateViewModel,
    modifier: Modifier = Modifier
) {
    // Collect existing structured routine data from repository
    val schedules by viewModel.schedules.collectAsStateWithLifecycle()
    val subjects by viewModel.subjects.collectAsStateWithLifecycle()
    val attendanceSummaries by viewModel.attendanceSummaries.collectAsStateWithLifecycle()
    val allAttendanceRecords by viewModel.allAttendanceRecords.collectAsStateWithLifecycle()

    // TODAY is the active/default tab when Attendance opens
    var selectedTab by remember { mutableStateOf(AttendanceDayTab.TODAY) }
    var showAddSubjectDialog by remember { mutableStateOf(false) }
    var editingSubjectTarget by remember { mutableStateOf<SubjectEntity?>(null) }

    // Live clock ticker to detect day/midnight changes and foreground resume
    var currentTimeMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                currentTimeMillis = System.currentTimeMillis()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Ticker that checks every 30 seconds for midnight date rollover
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000L)
            currentTimeMillis = System.currentTimeMillis()
        }
    }

    // Compute day info for all 3 tabs using actual device calendar date
    val yesterdayInfo = remember(currentTimeMillis) { computeDayTabInfo(AttendanceDayTab.YESTERDAY, currentTimeMillis) }
    val todayInfo = remember(currentTimeMillis) { computeDayTabInfo(AttendanceDayTab.TODAY, currentTimeMillis) }
    val tomorrowInfo = remember(currentTimeMillis) { computeDayTabInfo(AttendanceDayTab.TOMORROW, currentTimeMillis) }

    val currentTabInfo = when (selectedTab) {
        AttendanceDayTab.YESTERDAY -> yesterdayInfo
        AttendanceDayTab.TODAY -> todayInfo
        AttendanceDayTab.TOMORROW -> tomorrowInfo
    }

    // Filter existing routine data by the selected day's dayOfWeek (1=Mon ... 7=Sun)
    // and sort chronologically by startTime. Never mix multiple days!
    val dayClasses = remember(schedules, currentTabInfo.dayOfWeek) {
        schedules
            .filter { it.dayOfWeek == currentTabInfo.dayOfWeek }
            .sortedWith(compareBy({ it.startTime }, { it.id }))
    }

    // Overall Attendance metrics
    val totalConducted = attendanceSummaries.sumOf { it.conductedClasses }
    val totalAttended = attendanceSummaries.sumOf { it.attendedClasses }
    val overallPercentage = if (totalConducted > 0) (totalAttended.toDouble() / totalConducted.toDouble()) * 100.0 else 0.0

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddSubjectDialog = true },
                containerColor = AcademicBlue,
                contentColor = Color.White,
                modifier = Modifier.testTag("add_subject_fab")
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Subject")
            }
        },
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Header
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text(
                    text = "Attendance Tracker",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Track your attendance class-by-class for each day",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Overall Attendance Summary Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (overallPercentage >= 75.0) SuccessGreen.copy(alpha = 0.12f) else WarningOrange.copy(alpha = 0.12f)
                )
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Overall Attendance", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(
                                text = if (totalConducted > 0) "$totalAttended of $totalConducted total classes attended" else "No classes conducted yet",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = "${String.format(Locale.ENGLISH, "%.1f", overallPercentage)}%",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (overallPercentage >= 75.0) SuccessGreen else DangerRed
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { (overallPercentage / 100.0).toFloat().coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = if (overallPercentage >= 75.0) SuccessGreen else DangerRed,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // ========================================================
            // DAY TABS: YESTERDAY | TODAY | TOMORROW
            // TODAY is active/default on open
            // ========================================================
            val tabs = listOf(yesterdayInfo, todayInfo, tomorrowInfo)
            val selectedTabIndex = selectedTab.ordinal

            TabRow(
                selectedTabIndex = selectedTabIndex,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        Modifier.tabIndicatorOffset(tabPositions[selectedTabIndex]),
                        color = AcademicBlue,
                        height = 3.dp
                    )
                }
            ) {
                tabs.forEach { tabInfo ->
                    val isSelected = selectedTab == tabInfo.tab
                    Tab(
                        selected = isSelected,
                        onClick = { selectedTab = tabInfo.tab },
                        modifier = Modifier.testTag("tab_${tabInfo.tab.name.lowercase()}"),
                        text = {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(vertical = 6.dp)
                            ) {
                                Text(
                                    text = tabInfo.title,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 13.sp,
                                    color = if (isSelected) AcademicBlue else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = tabInfo.dateLabel,
                                    fontSize = 10.sp,
                                    color = if (isSelected) AcademicBlue.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                )
                            }
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Class Schedule List for the Active Tab (Filtered strictly to that day)
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (dayClasses.isEmpty()) {
                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 16.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = Icons.Default.EventBusy,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(40.dp)
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = "No classes scheduled.",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "No classes found for ${currentTabInfo.title.lowercase().replaceFirstChar { it.uppercase() }} (${currentTabInfo.dateLabel}).",
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                } else {
                    items(dayClasses, key = { it.id }) { schedule ->
                        // Match existing attendance record for this exact subject & date
                        val existingRecord = allAttendanceRecords.firstOrNull {
                            it.subjectId == schedule.subjectId &&
                            (it.dateString == currentTabInfo.dateString ||
                             SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH).format(Date(it.dateMillis)) == currentTabInfo.dateString)
                        }

                        val subject = subjects.find { it.id == schedule.subjectId || it.name.equals(schedule.subjectName, ignoreCase = true) }
                        val subjectCode = subject?.code ?: ""

                        DayClassAttendanceCard(
                            schedule = schedule,
                            subjectCode = subjectCode,
                            existingRecord = existingRecord,
                            dateLabel = currentTabInfo.title,
                            onMarkPresent = {
                                viewModel.markAttendance(schedule.subjectId, "PRESENT", currentTabInfo.dateMillis)
                            },
                            onMarkAbsent = {
                                viewModel.markAttendance(schedule.subjectId, "ABSENT", currentTabInfo.dateMillis)
                            },
                            onMarkCancelled = {
                                viewModel.markAttendance(schedule.subjectId, "CANCELLED", currentTabInfo.dateMillis)
                            },
                            onClearStatus = {
                                viewModel.markAttendance(schedule.subjectId, "NOT MARKED", currentTabInfo.dateMillis)
                            }
                        )
                    }
                }

                // Subject Attendance Targets & Breakdown Section
                if (attendanceSummaries.isNotEmpty()) {
                    item {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Subject-wise Attendance & Targets",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    items(attendanceSummaries, key = { "summary_${it.subject.id}" }) { summary ->
                        SubjectSummaryMiniCard(
                            summary = summary,
                            onEditTarget = { editingSubjectTarget = summary.subject }
                        )
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(80.dp))
                }
            }
        }
    }

    // Add Subject Dialog
    if (showAddSubjectDialog) {
        var subjectName by remember { mutableStateOf("") }
        var subjectCode by remember { mutableStateOf("") }
        var targetText by remember { mutableStateOf("75") }

        AlertDialog(
            onDismissRequest = { showAddSubjectDialog = false },
            title = { Text("Add Subject", fontWeight = FontWeight.Bold) },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = subjectName,
                        onValueChange = { subjectName = it },
                        label = { Text("Subject Name (e.g. Pharmaceutics)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = subjectCode,
                        onValueChange = { subjectCode = it },
                        label = { Text("Subject Code (optional e.g. BP101T)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = targetText,
                        onValueChange = { targetText = it.filter { ch -> ch.isDigit() } },
                        label = { Text("Target Attendance %") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (subjectName.isNotBlank()) {
                            val target = targetText.toIntOrNull()?.coerceIn(1, 100) ?: 75
                            viewModel.addOrUpdateClass(
                                subjectName = subjectName,
                                dayOfWeek = currentTabInfo.dayOfWeek,
                                startTime = "09:00",
                                endTime = "10:00"
                            )
                            showAddSubjectDialog = false
                        }
                    }
                ) {
                    Text("Add")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddSubjectDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Edit Target Dialog
    if (editingSubjectTarget != null) {
        var targetInput by remember { mutableStateOf(editingSubjectTarget!!.targetAttendance.toString()) }

        AlertDialog(
            onDismissRequest = { editingSubjectTarget = null },
            title = { Text("Change Target Attendance", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("Set desired target percentage for ${editingSubjectTarget!!.name}:")
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = targetInput,
                        onValueChange = { targetInput = it.filter { ch -> ch.isDigit() } },
                        label = { Text("Target %") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val num = targetInput.toIntOrNull()?.coerceIn(1, 100) ?: 75
                        viewModel.updateSubjectTarget(editingSubjectTarget!!, num)
                        editingSubjectTarget = null
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { editingSubjectTarget = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

/**
 * Individual Class Card showing:
 * - Subject Name
 * - Exact class time (e.g. 10:00 AM – 11:00 AM)
 * - Subject code (if available)
 * - Teacher (if available)
 * - Room (if available)
 * - Theory/Practical (if available)
 * - Action buttons for Present, Absent, Cancel
 */
@Composable
fun DayClassAttendanceCard(
    schedule: ClassScheduleEntity,
    subjectCode: String,
    existingRecord: AttendanceRecordEntity?,
    dateLabel: String,
    onMarkPresent: () -> Unit,
    onMarkAbsent: () -> Unit,
    onMarkCancelled: () -> Unit,
    onClearStatus: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val formattedTime = remember(schedule.startTime, schedule.endTime) {
        formatClassTimeRange(schedule.startTime, schedule.endTime)
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Class Time & Theory/Practical Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Schedule,
                        contentDescription = null,
                        tint = AcademicBlue,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = formattedTime,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = AcademicBlue
                    )
                }

                val typeOrSection = schedule.groupSection.trim()
                if (typeOrSection.isNotBlank()) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = AcademicBlue.copy(alpha = 0.12f)
                    ) {
                        Text(
                            text = typeOrSection.uppercase(),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = AcademicBlue,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Subject Name & Code
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = schedule.subjectName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (subjectCode.isNotBlank()) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Text(
                            text = subjectCode,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            // Teacher & Room details
            if (schedule.teacher.isNotBlank() || schedule.room.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (schedule.teacher.isNotBlank()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = schedule.teacher,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    if (schedule.room.isNotBlank()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.LocationOn,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = schedule.room,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Status Badge & Action Buttons
            if (existingRecord != null) {
                val statusColor = when (existingRecord.status) {
                    "PRESENT" -> SuccessGreen
                    "ABSENT" -> DangerRed
                    else -> WarningOrange
                }
                val statusIcon = when (existingRecord.status) {
                    "PRESENT" -> Icons.Default.Check
                    "ABSENT" -> Icons.Default.Close
                    else -> Icons.Default.EventBusy
                }

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = statusColor.copy(alpha = 0.12f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = statusIcon,
                                contentDescription = null,
                                tint = statusColor,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Marked as ${existingRecord.status} for $dateLabel",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = statusColor
                            )
                        }

                        // Option to toggle / change
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            if (existingRecord.status != "PRESENT") {
                                IconButton(onClick = onMarkPresent, modifier = Modifier.size(28.dp)) {
                                    Icon(Icons.Default.Check, contentDescription = "Change to Present", tint = SuccessGreen, modifier = Modifier.size(16.dp))
                                }
                            }
                            if (existingRecord.status != "ABSENT") {
                                IconButton(onClick = onMarkAbsent, modifier = Modifier.size(28.dp)) {
                                    Icon(Icons.Default.Close, contentDescription = "Change to Absent", tint = DangerRed, modifier = Modifier.size(16.dp))
                                }
                            }
                            if (existingRecord.status != "CANCELLED") {
                                IconButton(onClick = onMarkCancelled, modifier = Modifier.size(28.dp)) {
                                    Icon(Icons.Default.EventBusy, contentDescription = "Change to Cancelled", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                                }
                            }
                            IconButton(onClick = onClearStatus, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Default.Delete, contentDescription = "Reset to Not Marked", tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f), modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onMarkPresent,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Present", fontSize = 12.sp)
                    }

                    Button(
                        onClick = onMarkAbsent,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = DangerRed),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Absent", fontSize = 12.sp)
                    }

                    OutlinedButton(
                        onClick = onMarkCancelled,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.EventBusy, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Cancel", fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

/**
 * Subject Breakdown Card showing cumulative stats and target editor
 */
@Composable
fun SubjectSummaryMiniCard(
    summary: SubjectAttendanceSummary,
    onEditTarget: () -> Unit,
    modifier: Modifier = Modifier
) {
    val target = summary.subject.targetAttendance
    val isMet = summary.isTargetAchieved
    val statusColor = when {
        summary.conductedClasses == 0 -> MaterialTheme.colorScheme.onSurfaceVariant
        summary.percentage >= target -> SuccessGreen
        summary.percentage >= target - 10 -> WarningOrange
        else -> DangerRed
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = summary.subject.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${summary.attendedClasses} attended / ${summary.conductedClasses} conducted • Target: $target%",
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = statusColor.copy(alpha = 0.12f)
                ) {
                    Text(
                        text = if (summary.conductedClasses > 0) "${String.format(Locale.ENGLISH, "%.1f", summary.percentage)}%" else "0.0%",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = statusColor
                    )
                }
                IconButton(onClick = onEditTarget, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit target", modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
