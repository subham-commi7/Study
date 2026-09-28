package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import com.example.data.model.DocumentType
import com.example.data.model.ExtractedRoutineItem
import com.example.data.model.UniversalDocumentResult
import com.example.ui.theme.AcademicAmber
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
import com.example.ui.theme.WarningOrange

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun UniversalDocumentReviewDialog(
    result: UniversalDocumentResult,
    onConfirm: (confirmedType: DocumentType) -> Unit,
    onDismiss: () -> Unit,
    onResolveConflict: (applyNew: Boolean, conflict: com.example.data.model.ScheduleConflict) -> Unit,
    onChangeTypeRequested: () -> Unit
) {
    var selectedType by remember { mutableStateOf(result.docType) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .heightIn(max = 680.dp)
                .padding(vertical = 16.dp),
            shape = RoundedCornerShape(24.dp),
            color = Color.White,
            tonalElevation = 4.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                // Header: Title & Badges
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = AcademicBlue,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Document Intelligence Review",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = Slate900
                            )
                        }
                        Text(
                            text = result.fileName,
                            fontSize = 13.sp,
                            color = Slate700
                        )
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.testTag("dismiss_review_button")) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Close", tint = Slate700)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Type & Confidence Badges
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Document Type Chip
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = AcademicBlue.copy(alpha = 0.1f),
                        modifier = Modifier.clickable { onChangeTypeRequested() }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = selectedType.displayName,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = AcademicBlue
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Change Type",
                                tint = AcademicBlue,
                                modifier = Modifier.size(12.dp)
                            )
                        }
                    }

                    // Confidence Chip
                    val (confBg, confFg, confText) = when (result.confidence) {
                        "HIGH" -> Triple(SuccessGreen.copy(alpha = 0.12f), SuccessGreen, "High Confidence")
                        "MEDIUM" -> Triple(AcademicAmber.copy(alpha = 0.15f), AcademicAmber, "Medium Confidence")
                        else -> Triple(WarningOrange.copy(alpha = 0.15f), WarningOrange, "Needs Review")
                    }

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = confBg
                    ) {
                        Text(
                            text = confText,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = confFg,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }

                // Cross Document Relations Banner
                if (result.crossDocumentRelations.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Slate100),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    tint = AcademicTeal,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Cross-Document Context Found",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = AcademicTeal
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            for (rel in result.crossDocumentRelations) {
                                Text(text = "• $rel", fontSize = 12.sp, color = Slate800)
                            }
                        }
                    }
                }

                // Conflict Banner
                if (result.detectedConflicts.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    for (conflict in result.detectedConflicts) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = DangerRed.copy(alpha = 0.08f)),
                            border = androidx.compose.foundation.BorderStroke(1.dp, DangerRed.copy(alpha = 0.3f)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Warning, contentDescription = null, tint = DangerRed, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(text = "Schedule Conflict Detected", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = DangerRed)
                                }
                                Text(text = conflict.conflictReason, fontSize = 12.sp, color = Slate800, modifier = Modifier.padding(top = 4.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                                    horizontalArrangement = Arrangement.End
                                ) {
                                    TextButton(onClick = { onResolveConflict(false, conflict) }) {
                                        Text("Keep Existing", fontSize = 12.sp)
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Button(
                                        onClick = { onResolveConflict(true, conflict) },
                                        colors = ButtonDefaults.buttonColors(containerColor = DangerRed)
                                    ) {
                                        Text("Apply New", fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = Slate200)

                // Body: Structured Preview based on Document Type
                Box(modifier = Modifier.weight(1f)) {
                    when (selectedType) {
                        DocumentType.ROUTINE, DocumentType.PRACTICAL_SCHEDULE -> {
                            val items = result.routineItems ?: emptyList()
                            if (items.isEmpty()) {
                                Text("No routine entries found in document.", color = Slate700)
                            } else {
                                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    items(items) { item ->
                                        RoutineReviewItemCard(item)
                                    }
                                }
                            }
                        }

                        DocumentType.SYLLABUS -> {
                            val topics = result.syllabusTopics ?: emptyList()
                            if (topics.isEmpty()) {
                                Text("No syllabus topics extracted.", color = Slate700)
                            } else {
                                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    items(topics) { topic ->
                                        Card(
                                            colors = CardDefaults.cardColors(containerColor = Slate50),
                                            shape = RoundedCornerShape(10.dp),
                                            border = androidx.compose.foundation.BorderStroke(1.dp, Slate200),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Column(modifier = Modifier.padding(12.dp)) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween
                                                ) {
                                                    Text(text = topic.subject, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = AcademicBlue)
                                                    Text(text = topic.unit, fontSize = 12.sp, color = Slate700)
                                                }
                                                Text(text = "${topic.chapter}: ${topic.topic}", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Slate900, modifier = Modifier.padding(top = 4.dp))
                                                if (topic.subtopic.isNotBlank()) {
                                                    Text(text = topic.subtopic, fontSize = 12.sp, color = Slate700)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        DocumentType.NOTICE, DocumentType.HOLIDAY_NOTICE, DocumentType.ACADEMIC_CALENDAR, DocumentType.EXAM_SCHEDULE, DocumentType.ASSIGNMENT -> {
                            val n = result.noticeData
                            if (n != null) {
                                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    item {
                                        Card(
                                            colors = CardDefaults.cardColors(containerColor = Slate50),
                                            shape = RoundedCornerShape(12.dp),
                                            border = androidx.compose.foundation.BorderStroke(1.dp, Slate200),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Column(modifier = Modifier.padding(14.dp)) {
                                                Text(text = n.title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Slate900)
                                                if (n.issueDate.isNotBlank()) {
                                                    Text(text = "Date: ${n.issueDate}", fontSize = 12.sp, color = Slate700, modifier = Modifier.padding(top = 2.dp))
                                                }
                                                Spacer(modifier = Modifier.height(8.dp))
                                                Text(text = n.simpleExplanation, fontSize = 13.sp, color = Slate800)

                                                if (n.isHoliday) {
                                                    Spacer(modifier = Modifier.height(8.dp))
                                                    Surface(
                                                        color = WarningOrange.copy(alpha = 0.12f),
                                                        shape = RoundedCornerShape(8.dp)
                                                    ) {
                                                        Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                                            Icon(Icons.Default.EventBusy, contentDescription = null, tint = WarningOrange, modifier = Modifier.size(16.dp))
                                                            Spacer(modifier = Modifier.width(6.dp))
                                                            Text(text = "Holiday: Classes suspended on affected dates.", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = WarningOrange)
                                                        }
                                                    }
                                                }

                                                if (n.affectedRoutineClasses.isNotEmpty()) {
                                                    Spacer(modifier = Modifier.height(8.dp))
                                                    Text(text = "Affected Routine Classes (${n.affectedRoutineClasses.size}):", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Slate900)
                                                    for (cls in n.affectedRoutineClasses) {
                                                        Text(text = "• ${cls.subjectName} (${cls.startTime} - ${cls.endTime}) Room: ${cls.room}", fontSize = 12.sp, color = Slate800)
                                                    }
                                                }

                                                if (n.deadlines.isNotEmpty()) {
                                                    Spacer(modifier = Modifier.height(8.dp))
                                                    Text(text = "Detected Deadlines / Exam Dates:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = AcademicBlue)
                                                    for (dl in n.deadlines) {
                                                        Text(text = "• $dl", fontSize = 12.sp, color = Slate800)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            } else {
                                Text("No structured notice data found.", color = Slate700)
                            }
                        }

                        DocumentType.NOTES, DocumentType.QUESTION_PAPER, DocumentType.ATTENDANCE_DOCUMENT, DocumentType.OTHER -> {
                            val notes = result.notesData
                            if (notes != null) {
                                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    item {
                                        Card(
                                            colors = CardDefaults.cardColors(containerColor = Slate50),
                                            shape = RoundedCornerShape(12.dp),
                                            border = androidx.compose.foundation.BorderStroke(1.dp, Slate200),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Column(modifier = Modifier.padding(14.dp)) {
                                                Text(text = notes.subject, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = AcademicBlue)
                                                if (notes.chapter.isNotBlank()) {
                                                    Text(text = notes.chapter, fontSize = 13.sp, color = Slate700)
                                                }
                                                Spacer(modifier = Modifier.height(8.dp))
                                                Text(text = "Executive Summary:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Slate900)
                                                Text(text = notes.summary, fontSize = 13.sp, color = Slate800, modifier = Modifier.padding(top = 2.dp))

                                                if (notes.definitions.isNotEmpty()) {
                                                    Spacer(modifier = Modifier.height(10.dp))
                                                    Text(text = "Key Definitions & Formulas:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = AcademicTeal)
                                                    for ((term, def) in notes.definitions.take(4)) {
                                                        Text(text = "• $term: $def", fontSize = 12.sp, color = Slate800)
                                                    }
                                                }

                                                if (notes.questionsAndAnswers.isNotEmpty()) {
                                                    Spacer(modifier = Modifier.height(10.dp))
                                                    Text(text = "Exam Practice Questions:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = AcademicAmber)
                                                    for ((q, a) in notes.questionsAndAnswers.take(3)) {
                                                        Text(text = "Q: $q", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Slate900)
                                                        Text(text = "A: $a", fontSize = 12.sp, color = Slate700, modifier = Modifier.padding(bottom = 4.dp))
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            } else {
                                Text("Document parsed. Ready to index.", color = Slate700)
                            }
                        }
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = Slate200)

                // Bottom Confirmation Actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("discard_document_button")
                    ) {
                        Text("Discard", color = DangerRed)
                    }

                    Button(
                        onClick = { onConfirm(selectedType) },
                        colors = ButtonDefaults.buttonColors(containerColor = AcademicBlue),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.testTag("confirm_document_button")
                    ) {
                        Icon(imageVector = Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Confirm & Apply to App")
                    }
                }
            }
        }
    }
}

@Composable
fun RoutineReviewItemCard(item: ExtractedRoutineItem) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Slate50),
        shape = RoundedCornerShape(10.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Slate200),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        color = AcademicBlue.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = item.dayName,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = AcademicBlue,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "${item.startTime} - ${item.endTime}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Slate900
                    )
                }

                Surface(
                    color = if (item.activityType.equals("Practical", ignoreCase = true)) AcademicTeal.copy(alpha = 0.15f) else Slate200,
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = item.activityType,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (item.activityType.equals("Practical", ignoreCase = true)) AcademicTeal else Slate800,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = item.subject,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = Slate900
            )

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                if (item.room.isNotBlank()) {
                    Text(text = "Room: ${item.room}", fontSize = 12.sp, color = Slate700)
                }
                if (item.teacher.isNotBlank()) {
                    Text(text = "Teacher: ${item.teacher}", fontSize = 12.sp, color = Slate700)
                }
            }
        }
    }
}

@Composable
fun DocumentTypeSelectionDialog(
    onSelectType: (DocumentType) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("What type of document is this?", fontSize = 17.sp, fontWeight = FontWeight.Bold)
        },
        text = {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(DocumentType.values()) { type ->
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelectType(type) },
                        shape = RoundedCornerShape(8.dp),
                        color = Slate50,
                        border = androidx.compose.foundation.BorderStroke(1.dp, Slate200)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = when (type) {
                                    DocumentType.ROUTINE, DocumentType.PRACTICAL_SCHEDULE -> Icons.Default.Schedule
                                    DocumentType.SYLLABUS -> Icons.AutoMirrored.Filled.MenuBook
                                    DocumentType.NOTICE, DocumentType.HOLIDAY_NOTICE -> Icons.Default.Notifications
                                    DocumentType.EXAM_SCHEDULE, DocumentType.ACADEMIC_CALENDAR -> Icons.Default.CalendarMonth
                                    else -> Icons.Default.Description
                                },
                                contentDescription = null,
                                tint = AcademicBlue,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(text = type.displayName, fontSize = 14.sp, color = Slate900, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
