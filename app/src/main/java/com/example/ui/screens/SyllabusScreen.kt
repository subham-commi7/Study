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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import com.example.ui.components.studyMateTextFieldColors
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.entities.SyllabusTopicEntity
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.ExtractedSyllabusPreviewDialog
import com.example.ui.theme.AcademicBlue
import com.example.ui.theme.AcademicTeal
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.WarningOrange
import com.example.ui.viewmodel.StudyMateViewModel

@Composable
fun SyllabusScreen(
    viewModel: StudyMateViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val allTopics by viewModel.syllabusTopics.collectAsStateWithLifecycle()
    val isExtracting by viewModel.isExtracting.collectAsStateWithLifecycle()
    val extractedPreview by viewModel.extractedSyllabusPreview.collectAsStateWithLifecycle()

    var showAddDialog by remember { mutableStateOf(false) }
    var selectedSubjectFilter by remember { mutableStateOf("All") }

    val subjects = listOf("All") + allTopics.map { it.subjectName }.distinct()
    val filteredTopics = if (selectedSubjectFilter == "All") allTopics else allTopics.filter { it.subjectName == selectedSubjectFilter }

    val totalCount = filteredTopics.size
    val completedCount = filteredTopics.count { it.status == "COMPLETED" }
    val progress = if (totalCount > 0) completedCount.toFloat() / totalCount.toFloat() else 0f

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            val mime = context.contentResolver.getType(uri)
            viewModel.processUniversalUpload(uri, mime, userOverrideType = com.example.data.model.DocumentType.SYLLABUS)
        }
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = AcademicTeal,
                contentColor = Color.White,
                modifier = Modifier.testTag("add_topic_fab")
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Topic")
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
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Syllabus Progress",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Track units, chapters, and topics",
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
                    modifier = Modifier.testTag("upload_syllabus_button")
                ) {
                    Icon(Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Upload", fontSize = 13.sp)
                }
            }

            // Extracting banner
            if (isExtracting) {
                Surface(
                    color = AcademicTeal.copy(alpha = 0.1f),
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
                        Text("Extracting syllabus units & topics...", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }

            // Subject Filter Tabs
            if (subjects.size > 1) {
                ScrollableTabRow(
                    selectedTabIndex = subjects.indexOf(selectedSubjectFilter).coerceAtLeast(0),
                    edgePadding = 16.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    subjects.forEach { subj ->
                        Tab(
                            selected = selectedSubjectFilter == subj,
                            onClick = { selectedSubjectFilter = subj },
                            text = { Text(subj, fontWeight = if (selectedSubjectFilter == subj) FontWeight.Bold else FontWeight.Normal) }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            // Overall / Subject Progress Header Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = AcademicTeal.copy(alpha = 0.1f))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (selectedSubjectFilter == "All") "Overall Completion" else "$selectedSubjectFilter Progress",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        Text(
                            text = "$completedCount of $totalCount Completed (${(progress * 100).toInt()}%)",
                            fontWeight = FontWeight.Bold,
                            color = AcademicTeal,
                            fontSize = 13.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = AcademicTeal,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (filteredTopics.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                    EmptyStateCard(
                        title = "No syllabus topics found",
                        subtitle = "Upload your syllabus document (PDF/Image) or add topics manually to monitor exam readiness.",
                        actionButtonText = "Add Topic",
                        onActionClick = { showAddDialog = true }
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredTopics, key = { it.id }) { topic ->
                        SyllabusTopicCard(
                            topic = topic,
                            onToggle = { viewModel.toggleTopicStatus(topic) },
                            onDelete = { viewModel.deleteTopic(topic) }
                        )
                    }
                    item {
                        Spacer(modifier = Modifier.height(80.dp))
                    }
                }
            }
        }
    }

    // Add Topic Dialog
    if (showAddDialog) {
        var subjectName by remember { mutableStateOf(if (selectedSubjectFilter != "All") selectedSubjectFilter else "") }
        var unitName by remember { mutableStateOf("Unit 1") }
        var chapterName by remember { mutableStateOf("Chapter 1") }
        var topicName by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("Add Syllabus Topic", fontWeight = FontWeight.Bold) },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = subjectName,
                        onValueChange = { subjectName = it },
                        label = { Text("Subject (e.g. Physics)") },
                        singleLine = true,
                        colors = studyMateTextFieldColors(),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = unitName,
                        onValueChange = { unitName = it },
                        label = { Text("Unit / Module Name") },
                        singleLine = true,
                        colors = studyMateTextFieldColors(),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = chapterName,
                        onValueChange = { chapterName = it },
                        label = { Text("Chapter Name") },
                        singleLine = true,
                        colors = studyMateTextFieldColors(),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = topicName,
                        onValueChange = { topicName = it },
                        label = { Text("Topic Title") },
                        singleLine = true,
                        colors = studyMateTextFieldColors(),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (topicName.isNotBlank() && subjectName.isNotBlank()) {
                            viewModel.addSyllabusTopic(subjectName, unitName, chapterName, topicName)
                            showAddDialog = false
                        }
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Extracted Preview Dialog
    if (extractedPreview != null) {
        ExtractedSyllabusPreviewDialog(
            topics = extractedPreview!!,
            onDismiss = { viewModel.dismissSyllabusPreview() },
            onConfirm = { confirmed ->
                viewModel.confirmExtractedSyllabus(confirmed)
            }
        )
    }
}

@Composable
fun SyllabusTopicCard(
    topic: SyllabusTopicEntity,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val statusColor = when (topic.status) {
        "COMPLETED" -> SuccessGreen
        "IN_PROGRESS" -> WarningOrange
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onToggle() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(onClick = onToggle, modifier = Modifier.size(36.dp)) {
                Icon(
                    imageVector = when (topic.status) {
                        "COMPLETED" -> Icons.Default.CheckCircle
                        "IN_PROGRESS" -> Icons.Default.HourglassTop
                        else -> Icons.Default.RadioButtonUnchecked
                    },
                    contentDescription = "Topic status: ${topic.status}",
                    tint = statusColor
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = topic.topicName,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp
                )
                Text(
                    text = "${topic.subjectName} • ${topic.unitName} - ${topic.chapterName}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Surface(
                shape = RoundedCornerShape(6.dp),
                color = statusColor.copy(alpha = 0.12f)
            ) {
                Text(
                    text = when (topic.status) {
                        "COMPLETED" -> "Completed"
                        "IN_PROGRESS" -> "In Progress"
                        else -> "Not Started"
                    },
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = statusColor
                )
            }

            IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Delete, contentDescription = "Delete topic", modifier = Modifier.size(16.dp))
            }
        }
    }
}
