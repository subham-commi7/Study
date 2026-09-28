package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.AlarmOff
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.entities.ClassScheduleEntity
import com.example.ui.components.AddEditClassDialog
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.ExtractedRoutinePreviewDialog
import com.example.ui.theme.AcademicBlue
import com.example.ui.viewmodel.StudyMateViewModel

@Composable
fun RoutineScreen(
    viewModel: StudyMateViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val schedules by viewModel.schedules.collectAsStateWithLifecycle()
    val subjects by viewModel.subjects.collectAsStateWithLifecycle()
    val selectedDay by viewModel.selectedRoutineDay.collectAsStateWithLifecycle()
    val isExtracting by viewModel.isExtracting.collectAsStateWithLifecycle()
    val extractedPreview by viewModel.extractedRoutinePreview.collectAsStateWithLifecycle()

    var showAddDialog by remember { mutableStateOf(false) }
    var editingSchedule by remember { mutableStateOf<ClassScheduleEntity?>(null) }

    val days = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
    val dayClasses = schedules.filter { it.dayOfWeek == selectedDay }.sortedBy { it.startTime }

    // System File/Image picker for Routine
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            val mimeType = context.contentResolver.getType(uri)
            viewModel.processUniversalUpload(uri, mimeType, userOverrideType = com.example.data.model.DocumentType.ROUTINE)
        }
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = AcademicBlue,
                contentColor = Color.White,
                modifier = Modifier.testTag("add_class_fab")
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Class")
            }
        },
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Header with Upload routine option
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Class Routine",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Weekly Timetable & Reminders",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                OutlinedButton(
                    onClick = {
                        try {
                            filePickerLauncher.launch(arrayOf("application/pdf", "image/*", "text/plain"))
                        } catch (_: Exception) {
                            viewModel.showMessage("Unable to open file picker", isError = true)
                        }
                    },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.testTag("upload_routine_button")
                ) {
                    Icon(Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Upload", fontSize = 13.sp)
                }
            }

            // Extraction Loading Indicator
            if (isExtracting) {
                Surface(
                    color = AcademicBlue.copy(alpha = 0.1f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("Extracting timetable structure...", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }

            // Week Day Tabs (Mon - Sun)
            ScrollableTabRow(
                selectedTabIndex = selectedDay - 1,
                edgePadding = 16.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                days.forEachIndexed { index, name ->
                    val dayNum = index + 1
                    val count = schedules.count { it.dayOfWeek == dayNum }
                    Tab(
                        selected = selectedDay == dayNum,
                        onClick = { viewModel.selectRoutineDay(dayNum) },
                        text = {
                            Text(
                                text = if (count > 0) "$name ($count)" else name,
                                fontWeight = if (selectedDay == dayNum) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Class Schedule List for the Day
            if (dayClasses.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp)
                ) {
                    EmptyStateCard(
                        title = "No classes for ${days[selectedDay - 1]}",
                        subtitle = "Add classes manually or upload an image/PDF timetable to automatically structure your routine.",
                        actionButtonText = "Add Class",
                        onActionClick = { showAddDialog = true }
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(dayClasses, key = { it.id }) { schedule ->
                        ClassItemCard(
                            schedule = schedule,
                            onEdit = { editingSchedule = schedule },
                            onDelete = { viewModel.deleteClass(schedule) }
                        )
                    }
                    item {
                        Spacer(modifier = Modifier.height(80.dp))
                    }
                }
            }
        }
    }

    // Add / Edit Class Dialog
    if (showAddDialog || editingSchedule != null) {
        AddEditClassDialog(
            initialSchedule = editingSchedule,
            subjects = subjects,
            currentDay = selectedDay,
            onDismiss = {
                showAddDialog = false
                editingSchedule = null
            },
            onConfirm = { id, subj, day, start, end, room, teacher, reminder ->
                viewModel.addOrUpdateClass(id, subj, day, start, end, room, teacher, reminder)
            }
        )
    }

    // Extracted Preview Dialog (User confirmation pipeline)
    if (extractedPreview != null) {
        ExtractedRoutinePreviewDialog(
            items = extractedPreview!!,
            onDismiss = { viewModel.dismissRoutinePreview() },
            onConfirm = { confirmed ->
                viewModel.confirmExtractedRoutine(confirmed)
            }
        )
    }
}

@Composable
fun ClassItemCard(
    schedule: ClassScheduleEntity,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = schedule.subjectName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    if (schedule.reminderEnabled) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.Default.Alarm,
                            contentDescription = "Reminder enabled",
                            tint = AcademicBlue,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${schedule.startTime} - ${schedule.endTime}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = AcademicBlue
                )
                if (schedule.room.isNotBlank() || schedule.teacher.isNotBlank()) {
                    Text(
                        text = listOfNotNull(
                            schedule.room.takeIf { it.isNotBlank() }?.let { "Room $it" },
                            schedule.teacher.takeIf { it.isNotBlank() }?.let { "Teacher: $it" }
                        ).joinToString(" • "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Class options")
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Edit") },
                        leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                        onClick = {
                            menuExpanded = false
                            onEdit()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Delete") },
                        leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                        onClick = {
                            menuExpanded = false
                            onDelete()
                        }
                    )
                }
            }
        }
    }
}
