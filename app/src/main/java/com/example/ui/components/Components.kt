package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.entities.ClassScheduleEntity
import com.example.data.local.entities.SubjectEntity
import com.example.data.model.ExtractedNoticeData
import com.example.data.model.ExtractedRoutineItem
import com.example.data.model.ExtractedSyllabusTopic
import com.example.ui.theme.AcademicBlue
import com.example.ui.theme.DangerRed
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.WarningOrange

@Composable
fun EmptyStateCard(
    title: String,
    subtitle: String,
    actionButtonText: String? = null,
    onActionClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (actionButtonText != null && onActionClick != null) {
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = onActionClick,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(actionButtonText)
                }
            }
        }
    }
}

// Add/Edit Class Dialog
@Composable
fun AddEditClassDialog(
    initialSchedule: ClassScheduleEntity? = null,
    subjects: List<SubjectEntity>,
    currentDay: Int,
    onDismiss: () -> Unit,
    onConfirm: (
        id: Long,
        subjectName: String,
        dayOfWeek: Int,
        startTime: String,
        endTime: String,
        room: String,
        teacher: String,
        reminder: Boolean
    ) -> Unit
) {
    var subjectName by remember { mutableStateOf(initialSchedule?.subjectName ?: "") }
    var dayOfWeek by remember { mutableStateOf(initialSchedule?.dayOfWeek ?: currentDay) }
    var startTime by remember { mutableStateOf(initialSchedule?.startTime ?: "09:00") }
    var endTime by remember { mutableStateOf(initialSchedule?.endTime ?: "10:00") }
    var room by remember { mutableStateOf(initialSchedule?.room ?: "") }
    var teacher by remember { mutableStateOf(initialSchedule?.teacher ?: "") }
    var reminder by remember { mutableStateOf(initialSchedule?.reminderEnabled ?: true) }

    val days = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (initialSchedule == null) "Add Class" else "Edit Class",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = subjectName,
                    onValueChange = { subjectName = it },
                    label = { Text("Subject Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))

                // Day Selection row
                Text("Day of Week:", style = MaterialTheme.typography.labelMedium)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    days.forEachIndexed { index, name ->
                        val dayNum = index + 1
                        val isSelected = dayNum == dayOfWeek
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier
                                .weight(1f)
                                .clickable { dayOfWeek = dayNum }
                        ) {
                            Text(
                                text = name.take(2),
                                modifier = Modifier.padding(vertical = 6.dp),
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = startTime,
                        onValueChange = { startTime = it },
                        label = { Text("Start (HH:mm)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = endTime,
                        onValueChange = { endTime = it },
                        label = { Text("End (HH:mm)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = room,
                    onValueChange = { room = it },
                    label = { Text("Room / Hall (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = teacher,
                    onValueChange = { teacher = it },
                    label = { Text("Teacher (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (subjectName.isNotBlank()) {
                        onConfirm(
                            initialSchedule?.id ?: 0L,
                            subjectName,
                            dayOfWeek,
                            startTime,
                            endTime,
                            room,
                            teacher,
                            reminder
                        )
                        onDismiss()
                    }
                }
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

// Extracted Routine Confirmation Dialog (Safety pipeline: Extract -> Structure -> Validate -> Preview -> User confirms -> Save)
@Composable
fun ExtractedRoutinePreviewDialog(
    items: List<ExtractedRoutineItem>,
    onDismiss: () -> Unit,
    onConfirm: (List<ExtractedRoutineItem>) -> Unit
) {
    var editableItems by remember { mutableStateOf(items) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Info, contentDescription = null, tint = AcademicBlue)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Review Extracted Timetable", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Verify and edit extracted classes before saving to your timetable:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))

                LazyColumn(modifier = Modifier.height(300.dp)) {
                    items(editableItems) { item ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (item.needsReview) WarningOrange.copy(alpha = 0.1f) else MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                if (item.needsReview) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Warning, contentDescription = null, tint = WarningOrange, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Needs Review: ${item.reviewReason}", color = WarningOrange, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                }
                                Text(
                                    text = "${item.dayName} • ${item.startTime} - ${item.endTime}",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                                Text(text = item.subject, fontWeight = FontWeight.Medium, color = AcademicBlue)
                                if (item.room.isNotBlank() || item.teacher.isNotBlank()) {
                                    Text(
                                        text = listOfNotNull(
                                            item.room.takeIf { it.isNotBlank() }?.let { "Room: $it" },
                                            item.teacher.takeIf { it.isNotBlank() }?.let { "Teacher: $it" }
                                        ).joinToString(" | "),
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(editableItems) }) {
                Text("Confirm & Save (${editableItems.size} Classes)")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

// Extracted Syllabus Preview Dialog
@Composable
fun ExtractedSyllabusPreviewDialog(
    topics: List<ExtractedSyllabusTopic>,
    onDismiss: () -> Unit,
    onConfirm: (List<ExtractedSyllabusTopic>) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Review Extracted Syllabus", fontWeight = FontWeight.Bold)
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Found ${topics.size} syllabus topics. Verify before saving:",
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(modifier = Modifier.height(8.dp))
                LazyColumn(modifier = Modifier.height(300.dp)) {
                    items(topics) { topic ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(topic.subject, fontWeight = FontWeight.Bold, color = AcademicBlue, fontSize = 12.sp)
                                Text("${topic.unit} • ${topic.chapter}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(topic.topic, fontWeight = FontWeight.Medium, fontSize = 13.sp)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(topics) }) {
                Text("Save Topics (${topics.size})")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

// Routine Change Detected Confirmation Dialog (From Notice)
@Composable
fun RoutineChangeDetectedDialog(
    notice: ExtractedNoticeData,
    onDismiss: () -> Unit,
    onApply: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Warning, contentDescription = null, tint = WarningOrange)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Routine Change Detected", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "A class schedule change or cancellation was detected in the notice:\n\"${notice.title}\"",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    color = WarningOrange.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = notice.routineChangeSummary.ifEmpty { notice.simpleExplanation },
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Apply this change to your active timetable?",
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onApply,
                colors = ButtonDefaults.buttonColors(containerColor = AcademicBlue)
            ) {
                Text("Apply Change")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Keep Current Routine")
            }
        }
    )
}

@Composable
fun studyMateTextFieldColors() = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
    focusedTextColor = MaterialTheme.colorScheme.onSurface,
    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
    focusedContainerColor = MaterialTheme.colorScheme.surface,
    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
    errorContainerColor = MaterialTheme.colorScheme.surface,
    focusedBorderColor = MaterialTheme.colorScheme.primary,
    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
    focusedLabelColor = MaterialTheme.colorScheme.primary,
    unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
    focusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
    unfocusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
    focusedLeadingIconColor = MaterialTheme.colorScheme.primary,
    unfocusedLeadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
    focusedTrailingIconColor = MaterialTheme.colorScheme.primary,
    unfocusedTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
    cursorColor = MaterialTheme.colorScheme.primary,
    errorTextColor = MaterialTheme.colorScheme.error,
    errorBorderColor = MaterialTheme.colorScheme.error,
    errorLabelColor = MaterialTheme.colorScheme.error,
    errorLeadingIconColor = MaterialTheme.colorScheme.error,
    errorTrailingIconColor = MaterialTheme.colorScheme.error
)

