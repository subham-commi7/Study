package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.filled.DriveFileMove
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.model.CourseSyllabusPackage
import com.example.data.model.ExtractedSubjectSyllabus
import com.example.data.model.ExtractedTopicItem
import com.example.data.model.ExtractedUnit
import com.example.ui.theme.AcademicAmber
import com.example.ui.theme.AcademicBlue
import com.example.ui.theme.DangerRed
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.WarningOrange

@Composable
fun CourseSyllabusReviewDialog(
    syllabusPackage: CourseSyllabusPackage,
    onConfirm: (CourseSyllabusPackage) -> Unit,
    onDismiss: () -> Unit
) {
    var courseName by remember { mutableStateOf(syllabusPackage.course) }
    var semesterName by remember { mutableStateOf(syllabusPackage.semester) }
    var subjects by remember { mutableStateOf(syllabusPackage.subjects) }
    var unresolvedItems by remember { mutableStateOf(syllabusPackage.unresolvedItems) }

    // Dialog state for editing a topic
    var editingTopicSubjectIdx by remember { mutableStateOf(-1) }
    var editingTopicUnitIdx by remember { mutableStateOf(-1) }
    var editingTopicIdx by remember { mutableStateOf(-1) }
    var editingTopicText by remember { mutableStateOf("") }
    var showEditTopicDialog by remember { mutableStateOf(false) }

    // Total trackable count
    val totalTrackableTopics = subjects.sumOf { s ->
        s.units.sumOf { u -> u.topics.count { it.isTrackable && !it.isReferenceOnly } }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.92f),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(AcademicBlue.copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.School, contentDescription = null, tint = AcademicBlue, modifier = Modifier.size(18.dp))
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Course Syllabus Review",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            text = "Review course hierarchy & subject separation before saving",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Course & Semester Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = courseName,
                        onValueChange = { courseName = it },
                        label = { Text("Course / Degree") },
                        singleLine = true,
                        modifier = Modifier.weight(1.2f)
                    )
                    OutlinedTextField(
                        value = semesterName,
                        onValueChange = { semesterName = it },
                        label = { Text("Semester / Year") },
                        singleLine = true,
                        modifier = Modifier.weight(0.8f)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Unresolved Items Banner (if any)
                if (unresolvedItems.isNotEmpty()) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = WarningOrange.copy(alpha = 0.15f)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Warning, contentDescription = null, tint = WarningOrange, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "${unresolvedItems.size} Unassigned Items Detected",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = WarningOrange
                                )
                            }
                            Text(
                                text = "These topics could not be automatically assigned to a subject. Assign them below:",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            unresolvedItems.forEachIndexed { uIdx, item ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = item.topicName,
                                        fontSize = 12.sp,
                                        modifier = Modifier.weight(1f),
                                        maxLines = 1
                                    )
                                    var showSubjectDropdown by remember { mutableStateOf(false) }
                                    Box {
                                        OutlinedButton(
                                            onClick = { showSubjectDropdown = true },
                                            modifier = Modifier.height(32.dp)
                                        ) {
                                            Text("Assign to Subject", fontSize = 10.sp)
                                        }
                                        DropdownMenu(
                                            expanded = showSubjectDropdown,
                                            onDismissRequest = { showSubjectDropdown = false }
                                        ) {
                                            subjects.forEachIndexed { sIdx, subj ->
                                                DropdownMenuItem(
                                                    text = { Text(subj.subjectName, fontSize = 12.sp) },
                                                    onClick = {
                                                        showSubjectDropdown = false
                                                        // Move unresolved item to this subject's first unit
                                                        val targetUnits = subj.units.toMutableList()
                                                        if (targetUnits.isEmpty()) {
                                                            targetUnits.add(ExtractedUnit("Unit I", listOf(item.copy(isUnresolved = false))))
                                                        } else {
                                                            val u0 = targetUnits[0]
                                                            targetUnits[0] = u0.copy(topics = u0.topics + item.copy(isUnresolved = false))
                                                        }
                                                        val updatedSubjects = subjects.toMutableList()
                                                        updatedSubjects[sIdx] = subj.copy(units = targetUnits)
                                                        subjects = updatedSubjects
                                                        unresolvedItems = unresolvedItems.filterIndexed { index, _ -> index != uIdx }
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                }

                // Subjects & Units List
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(subjects.indices.toList(), key = { "subj_$it" }) { sIdx ->
                        val subject = subjects[sIdx]
                        var isExpanded by remember { mutableStateOf(true) }

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                // Subject Header Row
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { isExpanded = !isExpanded },
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            if (subject.subjectCode.isNotBlank()) {
                                                Surface(
                                                    shape = RoundedCornerShape(4.dp),
                                                    color = AcademicBlue.copy(alpha = 0.15f)
                                                ) {
                                                    Text(
                                                        text = subject.subjectCode,
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = AcademicBlue
                                                    )
                                                }
                                                Spacer(modifier = Modifier.width(8.dp))
                                            }
                                            Text(
                                                text = subject.subjectName,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 15.sp
                                            )
                                        }
                                        val topicCount = subject.units.sumOf { it.topics.count { t -> t.isTrackable } }
                                        Text(
                                            text = "${subject.units.size} Units • $topicCount trackable topics" +
                                                    if (subject.nonTrackableReferences.isNotEmpty()) " • ${subject.nonTrackableReferences.size} references (excluded)" else "",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    IconButton(onClick = { isExpanded = !isExpanded }) {
                                        Icon(
                                            imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                            contentDescription = null
                                        )
                                    }
                                }

                                AnimatedVisibility(visible = isExpanded) {
                                    Column(
                                        modifier = Modifier.padding(top = 10.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        subject.units.forEachIndexed { uIdx, unit ->
                                            Surface(
                                                shape = RoundedCornerShape(10.dp),
                                                color = MaterialTheme.colorScheme.surface,
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Column(modifier = Modifier.padding(10.dp)) {
                                                    Text(
                                                        text = unit.unitName,
                                                        fontWeight = FontWeight.SemiBold,
                                                        fontSize = 13.sp,
                                                        color = AcademicBlue
                                                    )
                                                    Spacer(modifier = Modifier.height(4.dp))

                                                    unit.topics.forEachIndexed { tIdx, topic ->
                                                        Row(
                                                            modifier = Modifier
                                                                .fillMaxWidth()
                                                                .padding(vertical = 4.dp),
                                                            verticalAlignment = Alignment.CenterVertically
                                                        ) {
                                                            Column(modifier = Modifier.weight(1f)) {
                                                                Text(
                                                                    text = topic.topicName,
                                                                    fontSize = 13.sp,
                                                                    fontWeight = FontWeight.Medium,
                                                                    color = if (topic.isTrackable) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                                                                )
                                                                if (topic.chapterName.isNotBlank() && topic.chapterName != unit.unitName) {
                                                                    Text(
                                                                        text = "Chapter: ${topic.chapterName}",
                                                                        fontSize = 11.sp,
                                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                                    )
                                                                }
                                                                if (topic.subtopics.isNotEmpty()) {
                                                                    Text(
                                                                        text = topic.subtopics.joinToString(" • "),
                                                                        fontSize = 10.sp,
                                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                                    )
                                                                }
                                                            }

                                                            // Quick Action: Edit Topic Name
                                                            IconButton(
                                                                onClick = {
                                                                    editingTopicSubjectIdx = sIdx
                                                                    editingTopicUnitIdx = uIdx
                                                                    editingTopicIdx = tIdx
                                                                    editingTopicText = topic.topicName
                                                                    showEditTopicDialog = true
                                                                },
                                                                modifier = Modifier.size(28.dp)
                                                            ) {
                                                                Icon(Icons.Default.Edit, contentDescription = "Edit topic", modifier = Modifier.size(15.dp))
                                                            }

                                                            // Quick Action: Move topic to another subject
                                                            var showMoveMenu by remember { mutableStateOf(false) }
                                                            Box {
                                                                IconButton(
                                                                    onClick = { showMoveMenu = true },
                                                                    modifier = Modifier.size(28.dp)
                                                                ) {
                                                                    @Suppress("DEPRECATION")
                                                                    Icon(Icons.Default.DriveFileMove, contentDescription = "Move topic", modifier = Modifier.size(15.dp), tint = AcademicAmber)
                                                                }
                                                                DropdownMenu(
                                                                    expanded = showMoveMenu,
                                                                    onDismissRequest = { showMoveMenu = false }
                                                                ) {
                                                                    subjects.forEachIndexed { targetSIdx, targetSubj ->
                                                                        if (targetSIdx != sIdx) {
                                                                            DropdownMenuItem(
                                                                                text = { Text("Move to ${targetSubj.subjectName}", fontSize = 12.sp) },
                                                                                onClick = {
                                                                                    showMoveMenu = false
                                                                                    // Remove from current unit
                                                                                    val curUnits = subject.units.toMutableList()
                                                                                    val curTops = unit.topics.toMutableList()
                                                                                    curTops.removeAt(tIdx)
                                                                                    curUnits[uIdx] = unit.copy(topics = curTops)

                                                                                    // Add to target subject
                                                                                    val targetUnits = targetSubj.units.toMutableList()
                                                                                    if (targetUnits.isEmpty()) {
                                                                                        targetUnits.add(ExtractedUnit("Unit I", listOf(topic)))
                                                                                    } else {
                                                                                        targetUnits[0] = targetUnits[0].copy(topics = targetUnits[0].topics + topic)
                                                                                    }

                                                                                    val updated = subjects.toMutableList()
                                                                                    updated[sIdx] = subject.copy(units = curUnits)
                                                                                    updated[targetSIdx] = targetSubj.copy(units = targetUnits)
                                                                                    subjects = updated
                                                                                }
                                                                            )
                                                                        }
                                                                    }
                                                                }
                                                            }

                                                            // Quick Action: Toggle Trackable vs Reference
                                                            FilterChip(
                                                                selected = topic.isTrackable && !topic.isReferenceOnly,
                                                                onClick = {
                                                                    val curUnits = subject.units.toMutableList()
                                                                    val curTops = unit.topics.toMutableList()
                                                                    val updatedTopic = topic.copy(
                                                                        isTrackable = !(topic.isTrackable && !topic.isReferenceOnly),
                                                                        isReferenceOnly = (topic.isTrackable && !topic.isReferenceOnly)
                                                                    )
                                                                    curTops[tIdx] = updatedTopic
                                                                    curUnits[uIdx] = unit.copy(topics = curTops)
                                                                    val updated = subjects.toMutableList()
                                                                    updated[sIdx] = subject.copy(units = curUnits)
                                                                    subjects = updated
                                                                },
                                                                label = {
                                                                    Text(if (topic.isTrackable && !topic.isReferenceOnly) "Trackable" else "Reference", fontSize = 10.sp)
                                                                },
                                                                modifier = Modifier.padding(horizontal = 4.dp)
                                                            )

                                                            // Quick Action: Delete Topic
                                                            IconButton(
                                                                onClick = {
                                                                    val curUnits = subject.units.toMutableList()
                                                                    val curTops = unit.topics.toMutableList()
                                                                    curTops.removeAt(tIdx)
                                                                    curUnits[uIdx] = unit.copy(topics = curTops)
                                                                    val updated = subjects.toMutableList()
                                                                    updated[sIdx] = subject.copy(units = curUnits)
                                                                    subjects = updated
                                                                },
                                                                modifier = Modifier.size(28.dp)
                                                            ) {
                                                                Icon(Icons.Default.Delete, contentDescription = "Delete topic", modifier = Modifier.size(15.dp), tint = DangerRed)
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

                Spacer(modifier = Modifier.height(14.dp))

                // Action Confirmation Footer
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }

                    Button(
                        onClick = {
                            val finalPackage = CourseSyllabusPackage(
                                course = courseName.ifBlank { "Academic Course" },
                                semester = semesterName.ifBlank { "Semester 1" },
                                subjects = subjects,
                                unresolvedItems = unresolvedItems,
                                sourceFileName = syllabusPackage.sourceFileName
                            )
                            onConfirm(finalPackage)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.testTag("confirm_syllabus_button")
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Save Syllabus ($totalTrackableTopics Topics)", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    // Edit Topic Name Dialog
    if (showEditTopicDialog && editingTopicSubjectIdx >= 0) {
        AlertDialog(
            onDismissRequest = { showEditTopicDialog = false },
            title = { Text("Edit Topic Name") },
            text = {
                OutlinedTextField(
                    value = editingTopicText,
                    onValueChange = { editingTopicText = it },
                    label = { Text("Topic Name") },
                    singleLine = false,
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val sIdx = editingTopicSubjectIdx
                        val uIdx = editingTopicUnitIdx
                        val tIdx = editingTopicIdx
                        if (sIdx in subjects.indices && uIdx in subjects[sIdx].units.indices && tIdx in subjects[sIdx].units[uIdx].topics.indices) {
                            val curSubj = subjects[sIdx]
                            val curUnits = curSubj.units.toMutableList()
                            val curUnit = curUnits[uIdx]
                            val curTopics = curUnit.topics.toMutableList()
                            curTopics[tIdx] = curTopics[tIdx].copy(topicName = editingTopicText.trim())
                            curUnits[uIdx] = curUnit.copy(topics = curTopics)
                            val updated = subjects.toMutableList()
                            updated[sIdx] = curSubj.copy(units = curUnits)
                            subjects = updated
                        }
                        showEditTopicDialog = false
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditTopicDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
