package com.example.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.entities.AttendanceRecordEntity
import com.example.data.local.entities.SubjectEntity
import com.example.data.model.SubjectAttendanceSummary
import com.example.ui.components.EmptyStateCard
import com.example.ui.theme.AcademicAmber
import com.example.ui.theme.AcademicBlue
import com.example.ui.theme.DangerRed
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.WarningOrange
import com.example.ui.viewmodel.StudyMateViewModel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun AttendanceScreen(
    viewModel: StudyMateViewModel,
    modifier: Modifier = Modifier
) {
    val attendanceSummaries by viewModel.attendanceSummaries.collectAsStateWithLifecycle()
    val allAttendanceRecords by viewModel.allAttendanceRecords.collectAsStateWithLifecycle()
    val subjects by viewModel.subjects.collectAsStateWithLifecycle()

    var showAddSubjectDialog by remember { mutableStateOf(false) }
    var editingSubjectTarget by remember { mutableStateOf<SubjectEntity?>(null) }
    var selectedDateOffset by remember { mutableIntStateOf(0) } // 0 = Today, 1 = Yesterday

    val selectedDateMillis = remember(selectedDateOffset) {
        Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -selectedDateOffset) }.timeInMillis
    }
    val selectedDateStr = remember(selectedDateOffset) {
        SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH).format(Date(selectedDateMillis))
    }
    val selectedDateLabel = if (selectedDateOffset == 0) "Today" else "Yesterday"

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
                    text = "Subject-wise conducted, attended, and targets",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (attendanceSummaries.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp)
                ) {
                    EmptyStateCard(
                        title = "No subjects added yet",
                        subtitle = "Add your academic subjects to track attendance and get projection alerts.",
                        actionButtonText = "Add Subject",
                        onActionClick = { showAddSubjectDialog = true }
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Date Session Selector Chips
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilterChip(
                                selected = selectedDateOffset == 0,
                                onClick = { selectedDateOffset = 0 },
                                label = { Text("Today (Active)") },
                                leadingIcon = if (selectedDateOffset == 0) {
                                    { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                                } else null
                            )
                            FilterChip(
                                selected = selectedDateOffset == 1,
                                onClick = { selectedDateOffset = 1 },
                                label = { Text("Yesterday") },
                                leadingIcon = if (selectedDateOffset == 1) {
                                    { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                                } else null
                            )
                        }
                    }

                    // Overall Attendance Hero Card
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (overallPercentage >= 75.0) SuccessGreen.copy(alpha = 0.12f) else WarningOrange.copy(alpha = 0.12f)
                            )
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text("Overall Attendance", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                        Text(
                                            text = if (totalConducted > 0) "$totalAttended of $totalConducted total conducted classes attended" else "No classes conducted yet",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Text(
                                        text = "${String.format("%.1f", overallPercentage)}%",
                                        style = MaterialTheme.typography.headlineMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = if (overallPercentage >= 75.0) SuccessGreen else DangerRed
                                    )
                                }
                                Spacer(modifier = Modifier.height(10.dp))
                                LinearProgressIndicator(
                                    progress = { (overallPercentage / 100.0).toFloat().coerceIn(0f, 1f) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(8.dp)
                                        .clip(RoundedCornerShape(4.dp)),
                                    color = if (overallPercentage >= 75.0) SuccessGreen else DangerRed,
                                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                                )
                            }
                        }
                    }

                    // Subject Attendance Cards
                    items(attendanceSummaries, key = { it.subject.id }) { summary ->
                        val existingRec = allAttendanceRecords.firstOrNull {
                            it.subjectId == summary.subject.id &&
                            (it.dateString == selectedDateStr ||
                             SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH).format(Date(it.dateMillis)) == selectedDateStr)
                        }

                        SubjectAttendanceCard(
                            summary = summary,
                            existingRecord = existingRec,
                            selectedDateLabel = selectedDateLabel,
                            onMarkPresent = { viewModel.markAttendance(summary.subject.id, "PRESENT", selectedDateMillis) },
                            onMarkAbsent = { viewModel.markAttendance(summary.subject.id, "ABSENT", selectedDateMillis) },
                            onMarkCancelled = { viewModel.markAttendance(summary.subject.id, "CANCELLED", selectedDateMillis) },
                            onEditTarget = { editingSubjectTarget = summary.subject }
                        )
                    }

                    item {
                        Spacer(modifier = Modifier.height(80.dp))
                    }
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
                        label = { Text("Subject Name (e.g. Physics)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = subjectCode,
                        onValueChange = { subjectCode = it },
                        label = { Text("Course Code (optional)") },
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
                                dayOfWeek = 1,
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

@Composable
fun SubjectAttendanceCard(
    summary: SubjectAttendanceSummary,
    existingRecord: AttendanceRecordEntity? = null,
    selectedDateLabel: String = "Today",
    onMarkPresent: () -> Unit,
    onMarkAbsent: () -> Unit,
    onMarkCancelled: () -> Unit,
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
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Title & Percentage
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = summary.subject.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Target: $target%",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        IconButton(onClick = onEditTarget, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit target", modifier = Modifier.size(14.dp))
                        }
                    }
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = statusColor.copy(alpha = 0.15f)
                ) {
                    Text(
                        text = if (summary.conductedClasses > 0) "${String.format("%.1f", summary.percentage)}%" else "0.0%",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = statusColor
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Class Count Badges
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CountBadge("Attended", summary.attendedClasses, SuccessGreen, Modifier.weight(1f))
                CountBadge("Absent", summary.absentClasses, DangerRed, Modifier.weight(1f))
                CountBadge("Conducted", summary.conductedClasses, AcademicBlue, Modifier.weight(1f))
                CountBadge("Cancelled", summary.cancelledClasses, MaterialTheme.colorScheme.onSurfaceVariant, Modifier.weight(1f))
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Projection Calculation Notice
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = if (isMet) SuccessGreen.copy(alpha = 0.1f) else DangerRed.copy(alpha = 0.1f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (isMet) Icons.Default.Check else Icons.Default.Warning,
                        contentDescription = null,
                        tint = if (isMet) SuccessGreen else DangerRed,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = when {
                            summary.conductedClasses == 0 -> "No classes held yet. Ready to track!"
                            summary.isImpossibleToRecover -> "Target is 100%. Cannot reach 100% recovery after an absence."
                            isMet && summary.canMissClasses > 0 -> "You can safely miss ${summary.canMissClasses} more class(es) while maintaining $target%."
                            isMet -> "On track! Do not miss the next class to maintain $target%."
                            else -> "Attend next ${summary.requiredToReachTarget} class(es) consecutively to reach $target%."
                        },
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Attendance Status: Locked Record vs Action Buttons
            if (existingRecord != null && existingRecord.isLocked) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Attendance Locked",
                            tint = AcademicBlue,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Locked • Submitted as ${existingRecord.status} for $selectedDateLabel",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
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

@Composable
fun CountBadge(label: String, count: Int, color: Color, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = color.copy(alpha = 0.08f)
    ) {
        Column(
            modifier = Modifier.padding(vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = count.toString(), fontWeight = FontWeight.Bold, fontSize = 14.sp, color = color)
            Text(text = label, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
