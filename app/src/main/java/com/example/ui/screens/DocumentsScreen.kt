package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.EventNote
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Quiz
import androidx.compose.material.icons.filled.Summarize
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import com.example.ui.theme.AcademicBlue
import com.example.ui.theme.DangerRed
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.entities.DocumentEntity
import com.example.data.local.entities.NoticeEntity
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.RoutineChangeDetectedDialog
import com.example.ui.theme.AcademicAmber
import com.example.ui.theme.AcademicBlue
import com.example.ui.theme.AcademicTeal
import com.example.ui.theme.WarningOrange
import com.example.ui.viewmodel.StudyMateViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DocumentsScreen(
    viewModel: StudyMateViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val documents by viewModel.documents.collectAsStateWithLifecycle()
    val notices by viewModel.notices.collectAsStateWithLifecycle()
    val isExtracting by viewModel.isExtracting.collectAsStateWithLifecycle()
    val extractedNoticePreview by viewModel.extractedNoticePreview.collectAsStateWithLifecycle()
    val notesAnalysisResult by viewModel.notesAnalysisResult.collectAsStateWithLifecycle()

    var viewingDocument by remember { mutableStateOf<DocumentEntity?>(null) }
    var selectedTypeFilter by remember { mutableStateOf("All") }
    var selectedNoticeToInspect by remember { mutableStateOf<NoticeEntity?>(null) }
    var selectedDocForNotesAnalysis by remember { mutableStateOf<DocumentEntity?>(null) }
    var manualNotesInput by remember { mutableStateOf("") }
    var showManualNotesDialog by remember { mutableStateOf(false) }

    // If viewing a document, render the dedicated in-app PDF Viewer
    if (viewingDocument != null) {
        PdfViewerScreen(
            document = viewingDocument!!,
            onBack = { viewingDocument = null }
        )
        return
    }

    val categories = listOf("All", "Notice", "Notes", "Routine", "Syllabus", "Assignment")
    val filteredDocs = if (selectedTypeFilter == "All") documents else documents.filter { it.type.equals(selectedTypeFilter, ignoreCase = true) }

    // System File picker for any academic documents / notices / notes / routines / syllabus
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            val mime = context.contentResolver.getType(uri)
            val overrideType = when (selectedTypeFilter.lowercase()) {
                "routine" -> com.example.data.model.DocumentType.ROUTINE
                "syllabus" -> com.example.data.model.DocumentType.SYLLABUS
                "notice" -> com.example.data.model.DocumentType.NOTICE
                "notes" -> com.example.data.model.DocumentType.NOTES
                "assignment" -> com.example.data.model.DocumentType.ASSIGNMENT
                else -> null // Auto-detect with layout analysis
            }
            viewModel.processUniversalUpload(uri, mime, userOverrideType = overrideType)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
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
                    text = "Academic Documents",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Notices, Notes, Routine & Syllabus archives",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(
                    onClick = { showManualNotesDialog = true },
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Notes AI", fontSize = 12.sp)
                }

                Button(
                    onClick = {
                        try {
                            filePickerLauncher.launch(arrayOf("application/pdf", "image/*", "text/plain"))
                        } catch (_: Exception) {
                            viewModel.showMessage("Unable to launch file picker", isError = true)
                        }
                    },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.testTag("upload_document_button")
                ) {
                    Icon(Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Upload", fontSize = 12.sp)
                }
            }
        }

        // Category Filter
        ScrollableTabRow(
            selectedTabIndex = categories.indexOf(selectedTypeFilter).coerceAtLeast(0),
            edgePadding = 16.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            categories.forEach { cat ->
                Tab(
                    selected = selectedTypeFilter == cat,
                    onClick = { selectedTypeFilter = cat },
                    text = { Text(cat, fontWeight = if (selectedTypeFilter == cat) FontWeight.Bold else FontWeight.Normal) }
                )
            }
        }

        // Extraction loading
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
                    Text("Processing document with safe structured validation...", fontSize = 13.sp)
                }
            }
        }

        // Documents and Notices List
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Unapplied Notice Routine Changes Banner
            val pendingChanges = notices.filter { it.hasRoutineChange && !it.changeApplied }
            if (pendingChanges.isNotEmpty()) {
                item {
                    pendingChanges.forEach { notice ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = WarningOrange.copy(alpha = 0.12f)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Warning, contentDescription = null, tint = WarningOrange)
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Notice Schedule Change Detected", fontWeight = FontWeight.Bold, color = WarningOrange, fontSize = 13.sp)
                                    Text(notice.proposedChangeJson.ifEmpty { notice.explanation }, fontSize = 12.sp, maxLines = 2)
                                }
                                TextButton(onClick = { selectedNoticeToInspect = notice }) {
                                    Text("Review", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }

            if (filteredDocs.isEmpty() && notices.isEmpty()) {
                item {
                    EmptyStateCard(
                        title = "No documents in '$selectedTypeFilter'",
                        subtitle = "Upload college notices, class notes, routine, or syllabus files to access and analyze them anytime.",
                        actionButtonText = "Upload Document",
                        onActionClick = {
                            try {
                                filePickerLauncher.launch(arrayOf("application/pdf", "image/*", "text/plain"))
                            } catch (_: Exception) {
                                viewModel.showMessage("Unable to open picker", isError = true)
                            }
                        }
                    )
                }
            } else {
                // Saved Notices Section
                if (selectedTypeFilter == "All" || selectedTypeFilter == "Notice") {
                    if (notices.isNotEmpty()) {
                        item {
                            Text("Analyzed Notices (${notices.size})", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                        }
                        items(notices, key = { "notice_${it.id}" }) { notice ->
                            NoticeCard(
                                notice = notice,
                                onInspect = { selectedNoticeToInspect = notice },
                                onDelete = { viewModel.deleteNotice(notice) }
                            )
                        }
                    }
                }

                // Saved Files
                if (filteredDocs.isNotEmpty()) {
                    item {
                        Text("Uploaded Files (${filteredDocs.size})", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                    }
                    items(filteredDocs, key = { "doc_${it.id}" }) { doc ->
                        DocumentItemCard(
                            document = doc,
                            onOpen = { viewingDocument = doc },
                            onAnalyze = { selectedDocForNotesAnalysis = doc },
                            onDelete = { viewModel.deleteDocument(doc) }
                        )
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(80.dp))
            }
        }
    }

    // Notice Detail Dialog (Key Facts vs AI Interpretation)
    if (selectedNoticeToInspect != null) {
        val notice = selectedNoticeToInspect!!
        AlertDialog(
            onDismissRequest = { selectedNoticeToInspect = null },
            title = {
                Text(notice.title, fontWeight = FontWeight.Bold)
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    if (notice.issueDate.isNotBlank()) {
                        Text("Date: ${notice.issueDate}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(6.dp))
                    }

                    Text("SOURCE FACTS (Verbatim / Direct from Notice):", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = AcademicBlue)
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(notice.sourceFact, modifier = Modifier.padding(10.dp), fontSize = 12.sp)
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Text("STUDENT-FRIENDLY EXPLANATION:", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = AcademicTeal)
                    Surface(
                        color = AcademicTeal.copy(alpha = 0.1f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(notice.explanation, modifier = Modifier.padding(10.dp), fontSize = 12.sp)
                    }

                    if (notice.deadlineDate.isNotBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Key Deadline: ${notice.deadlineDate}", fontWeight = FontWeight.Bold, color = AcademicAmber, fontSize = 12.sp)
                    }
                }
            },
            confirmButton = {
                if (notice.hasRoutineChange && !notice.changeApplied) {
                    Button(
                        onClick = {
                            viewModel.applyNoticeRoutineChange(emptyList(), notice.id)
                            selectedNoticeToInspect = null
                        }
                    ) {
                        Text("Acknowledge / Apply")
                    }
                } else {
                    Button(onClick = { selectedNoticeToInspect = null }) {
                        Text("Close")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { selectedNoticeToInspect = null }) {
                    Text("Dismiss")
                }
            }
        )
    }

    // Routine Change Detected Confirmation Dialog (From fresh extraction)
    if (extractedNoticePreview?.routineChangeDetected == true) {
        RoutineChangeDetectedDialog(
            notice = extractedNoticePreview!!,
            onDismiss = { viewModel.dismissNoticePreview() },
            onApply = {
                viewModel.applyNoticeRoutineChange(extractedNoticePreview!!.proposedChanges)
            }
        )
    }

    // Notes AI Analysis Dialog (Summary, Questions, Definitions)
    if (showManualNotesDialog || selectedDocForNotesAnalysis != null) {
        AlertDialog(
            onDismissRequest = {
                showManualNotesDialog = false
                selectedDocForNotesAnalysis = null
                viewModel.clearNotesAnalysis()
            },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = AcademicBlue)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Notes AI Assistant", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Generate summaries, important exam questions, or formulas grounded strictly in your notes:",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = manualNotesInput,
                        onValueChange = { manualNotesInput = it },
                        label = { Text("Paste note excerpt or lecture text") },
                        modifier = Modifier.fillMaxWidth().height(120.dp)
                    )

                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                if (manualNotesInput.isNotBlank()) {
                                    viewModel.analyzeNotes(manualNotesInput, "SUMMARY")
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Summary", fontSize = 11.sp)
                        }

                        OutlinedButton(
                            onClick = {
                                if (manualNotesInput.isNotBlank()) {
                                    viewModel.analyzeNotes(manualNotesInput, "QUESTIONS")
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Questions", fontSize = 11.sp)
                        }

                        OutlinedButton(
                            onClick = {
                                if (manualNotesInput.isNotBlank()) {
                                    viewModel.analyzeNotes(manualNotesInput, "DEFINITIONS")
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Formulas", fontSize = 11.sp)
                        }
                    }

                    if (notesAnalysisResult != null) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text("AI Grounded Result:", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = AcademicBlue)
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth().height(160.dp)
                        ) {
                            LazyColumn(modifier = Modifier.padding(10.dp)) {
                                item {
                                    Text(notesAnalysisResult!!, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showManualNotesDialog = false
                        selectedDocForNotesAnalysis = null
                        viewModel.clearNotesAnalysis()
                    }
                ) {
                    Text("Done")
                }
            }
        )
    }
}

@Composable
fun NoticeCard(
    notice: NoticeEntity,
    onInspect: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth().clickable { onInspect() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = AcademicAmber.copy(alpha = 0.15f)
                    ) {
                        Text("NOTICE", modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), fontSize = 9.sp, fontWeight = FontWeight.Bold, color = AcademicAmber)
                    }
                    if (notice.hasRoutineChange) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = WarningOrange.copy(alpha = 0.15f)
                        ) {
                            Text("SCHEDULE CHANGE", modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), fontSize = 9.sp, fontWeight = FontWeight.Bold, color = WarningOrange)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(notice.title, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text(notice.explanation, maxLines = 2, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Delete, contentDescription = "Delete notice", modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
fun DocumentItemCard(
    document: DocumentEntity,
    onOpen: () -> Unit,
    onAnalyze: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dateStr = remember(document.createdAt) {
        SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(document.createdAt))
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onOpen() }
            .testTag("document_card_${document.id}"),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(AcademicBlue.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Description, contentDescription = null, tint = AcademicBlue, modifier = Modifier.size(22.dp))
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = document.title.ifBlank { document.fileName },
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    maxLines = 1
                )
                Text(
                    text = "${document.type} • $dateStr • ${maxOf(1L, document.fileSize / 1024)} KB",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                IconButton(onClick = onOpen, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Visibility, contentDescription = "View document", modifier = Modifier.size(18.dp), tint = AcademicBlue)
                }

                IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete document", modifier = Modifier.size(16.dp), tint = DangerRed)
                }
            }
        }
    }
}
