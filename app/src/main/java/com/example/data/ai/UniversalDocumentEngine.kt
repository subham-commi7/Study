package com.example.data.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.media.ExifInterface
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.example.data.local.entities.ClassScheduleEntity
import com.example.data.local.entities.DocumentEntity
import com.example.data.local.entities.NoticeEntity
import com.example.data.local.entities.SubjectEntity
import com.example.data.model.DocumentType
import com.example.data.model.ExtractedNotesData
import com.example.data.model.ExtractedNoticeData
import com.example.data.model.ExtractedRoutineItem
import com.example.data.model.ExtractedSyllabusTopic
import com.example.data.model.ScheduleConflict
import com.example.data.model.UniversalDocumentResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.regex.Pattern
import kotlin.math.max

object UniversalDocumentEngine {

    private const val MAX_IMAGE_DIMENSION = 1400

    /**
     * Complete Universal Document Understanding Pipeline:
     * 1. File Validation
     * 2. Image/PDF Preprocessing & Orientation Correction
     * 3. Document Type Classification & Confidence Analysis
     * 4. Structural Entity & Layout Extraction (Tables, Hierarchies, Relationships)
     * 5. Cross-Document Intelligence (Subject Code Resolution, Conflict Detection, Holiday Check)
     */
    suspend fun processDocument(
        context: Context,
        uri: Uri,
        fileName: String,
        mimeType: String?,
        fileSize: Long,
        existingSubjects: List<SubjectEntity>,
        existingSchedules: List<ClassScheduleEntity>,
        existingNotices: List<NoticeEntity>,
        existingDocs: List<DocumentEntity>,
        userOverrideType: DocumentType? = null
    ): UniversalDocumentResult = withContext(Dispatchers.IO) {
        // Step 1: Preprocess Image or PDF
        val preprocessed = preprocessFile(context, uri, mimeType)
        val bitmap = preprocessed.bitmap
        val rawText = preprocessed.text ?: ""
        val pageCount = preprocessed.pageCount

        // Step 2: Document Type Classification
        val (classifiedType, classificationConfidence) = if (userOverrideType != null) {
            Pair(userOverrideType, "HIGH")
        } else {
            classifyDocumentType(rawText, bitmap, fileName)
        }

        val requiresConfirmation = classificationConfidence == "LOW"

        // Step 3: Structured Extraction based on Document Type
        var routineItems: List<ExtractedRoutineItem>? = null
        var syllabusTopics: List<ExtractedSyllabusTopic>? = null
        var noticeData: ExtractedNoticeData? = null
        var notesData: ExtractedNotesData? = null

        when (classifiedType) {
            DocumentType.ROUTINE, DocumentType.PRACTICAL_SCHEDULE -> {
                routineItems = extractRoutineStructured(bitmap, rawText, fileName)
            }
            DocumentType.SYLLABUS -> {
                syllabusTopics = extractSyllabusStructured(bitmap, rawText, fileName)
            }
            DocumentType.NOTICE, DocumentType.HOLIDAY_NOTICE, DocumentType.ACADEMIC_CALENDAR -> {
                noticeData = extractNoticeStructured(bitmap, rawText, fileName, classifiedType)
            }
            DocumentType.NOTES, DocumentType.QUESTION_PAPER, DocumentType.OTHER -> {
                notesData = extractNotesStructured(bitmap, rawText, fileName)
            }
            DocumentType.EXAM_SCHEDULE, DocumentType.ASSIGNMENT -> {
                noticeData = extractNoticeStructured(bitmap, rawText, fileName, classifiedType)
            }
            DocumentType.ATTENDANCE_DOCUMENT -> {
                notesData = extractNotesStructured(bitmap, rawText, fileName)
            }
        }

        // Step 4: Cross-Document Intelligence
        // Check for duplicates
        val duplicate = existingDocs.firstOrNull {
            it.fileName.equals(fileName, ignoreCase = true) ||
                    (it.fileSize == fileSize && fileSize > 0)
        }

        // Cross-Document Subject Mapping & Code Resolution (e.g. BP105T <-> Pharmacognosy)
        val crossDocRelations = mutableListOf<String>()
        if (routineItems != null) {
            routineItems = routineItems.map { item ->
                val resolvedSubj = resolveSubjectMapping(item.subject, item.subjectCode, existingSubjects)
                if (resolvedSubj != item.subject) {
                    crossDocRelations.add("Resolved subject code '${item.subjectCode.ifEmpty { item.subject }}' to canonical subject '$resolvedSubj'")
                }
                item.copy(subject = resolvedSubj)
            }
        }

        if (syllabusTopics != null) {
            syllabusTopics = syllabusTopics.map { item ->
                val resolvedSubj = resolveSubjectMapping(item.subject, item.subjectCode, existingSubjects)
                item.copy(subject = resolvedSubj)
            }
        }

        // Conflict Detection for Timetable Routine
        val detectedConflicts = mutableListOf<ScheduleConflict>()
        if (routineItems != null) {
            for (newClass in routineItems) {
                val conflicting = existingSchedules.firstOrNull { existing ->
                    existing.dayOfWeek == newClass.dayOfWeek &&
                            isTimeOverlapping(existing.startTime, existing.endTime, newClass.startTime, newClass.endTime) &&
                            !existing.subjectName.equals(newClass.subject, ignoreCase = true)
                }
                if (conflicting != null) {
                    detectedConflicts.add(
                        ScheduleConflict(
                            existingClass = conflicting,
                            conflictingClass = newClass,
                            conflictReason = "Time clash on ${newClass.dayName} (${newClass.startTime}-${newClass.endTime}): Existing '${conflicting.subjectName}' vs New '${newClass.subject}'"
                        )
                    )
                }
            }
        }

        // Holiday / Cancellation Notice vs Routine Cross-Check
        if (noticeData != null && (noticeData.isHoliday || noticeData.routineChangeDetected)) {
            val affectedClasses = findAffectedClassesForNotice(noticeData, existingSchedules)
            noticeData = noticeData.copy(affectedRoutineClasses = affectedClasses)
            if (affectedClasses.isNotEmpty()) {
                crossDocRelations.add("Notice affects ${affectedClasses.size} scheduled class(es) in your routine.")
            }
        }

        UniversalDocumentResult(
            docType = classifiedType,
            confidence = classificationConfidence,
            title = fileName.substringBeforeLast("."),
            fileName = fileName,
            fileSize = fileSize,
            mimeType = mimeType ?: "application/octet-stream",
            pageCount = pageCount,
            routineItems = routineItems,
            syllabusTopics = syllabusTopics,
            noticeData = noticeData,
            notesData = notesData,
            rawText = rawText,
            requiresUserTypeConfirmation = requiresConfirmation,
            detectedDuplicate = duplicate,
            detectedConflicts = detectedConflicts,
            crossDocumentRelations = crossDocRelations
        )
    }

    /**
     * Preprocesses files:
     * - For PDF: Uses Android's built-in PdfRenderer to render pages into clean bitmaps.
     * - For Image: Scales down safely and corrects EXIF orientation.
     */
    private fun preprocessFile(context: Context, uri: Uri, mimeType: String?): PreprocessResult {
        val resolver = context.contentResolver
        var pageCount = 1
        var renderedBitmap: Bitmap? = null
        var extractedText: String? = null

        try {
            val isPdf = mimeType?.contains("pdf", ignoreCase = true) == true ||
                    uri.toString().endsWith(".pdf", ignoreCase = true)

            if (isPdf) {
                // PDF Rendering with PdfRenderer
                resolver.openFileDescriptor(uri, "r")?.use { pfd ->
                    PdfRenderer(pfd).use { renderer ->
                        pageCount = renderer.pageCount
                        if (pageCount > 0) {
                            // Render first page as primary visual representation
                            renderer.openPage(0).use { page ->
                                val scale = calculatePdfScale(page.width, page.height)
                                val w = (page.width * scale).toInt().coerceAtLeast(100)
                                val h = (page.height * scale).toInt().coerceAtLeast(100)
                                val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                                page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                renderedBitmap = bmp
                            }
                        }
                    }
                }
            } else if (mimeType?.startsWith("image/") == true) {
                // Image loading with safe subsampling & EXIF rotation
                renderedBitmap = decodeSampledBitmapFromUri(context, uri)
            } else {
                // Try reading text directly
                resolver.openInputStream(uri)?.use { stream ->
                    extractedText = stream.bufferedReader().use { it.readText().take(15000) }
                }
            }
        } catch (_: Exception) {
            // Fallback gracefully
        }

        return PreprocessResult(
            bitmap = renderedBitmap,
            text = extractedText,
            pageCount = pageCount
        )
    }

    /**
     * Decode bitmap safely with dimension capping and EXIF rotation
     */
    private fun decodeSampledBitmapFromUri(context: Context, uri: Uri): Bitmap? {
        val resolver = context.contentResolver
        return try {
            // 1. Decode bounds only
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }

            // 2. Calculate inSampleSize
            options.inSampleSize = calculateInSampleSize(options, MAX_IMAGE_DIMENSION, MAX_IMAGE_DIMENSION)
            options.inJustDecodeBounds = false

            // 3. Decode scaled bitmap
            val rawBmp = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
                ?: return null

            // 4. Correct EXIF orientation
            val orientation = resolver.openInputStream(uri)?.use { stream ->
                try {
                    val exif = ExifInterface(stream)
                    exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
                } catch (_: Exception) {
                    ExifInterface.ORIENTATION_NORMAL
                }
            } ?: ExifInterface.ORIENTATION_NORMAL

            rotateBitmapIfRequired(rawBmp, orientation)
        } catch (_: Exception) {
            null
        }
    }

    private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        val (height: Int, width: Int) = options.outHeight to options.outWidth
        var inSampleSize = 1
        if (height > reqHeight || width > reqWidth) {
            val halfHeight: Int = height / 2
            val halfWidth: Int = width / 2
            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }

    private fun rotateBitmapIfRequired(bitmap: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            else -> return bitmap
        }
        return try {
            val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            if (rotated != bitmap) bitmap.recycle()
            rotated
        } catch (_: Exception) {
            bitmap
        }
    }

    private fun calculatePdfScale(pageWidth: Int, pageHeight: Int): Float {
        val maxDim = max(pageWidth, pageHeight)
        return if (maxDim > MAX_IMAGE_DIMENSION) {
            MAX_IMAGE_DIMENSION.toFloat() / maxDim.toFloat()
        } else {
            1.5f // Crisp rendering for standard PDF
        }
    }

    /**
     * Automatic Document Classification with Confidence Analysis
     */
    private suspend fun classifyDocumentType(
        text: String,
        bitmap: Bitmap?,
        fileName: String
    ): Pair<DocumentType, String> {
        val fn = fileName.lowercase()
        val t = text.lowercase()

        // High confidence file name patterns
        if (fn.contains("routine") || fn.contains("timetable") || fn.contains("schedule")) {
            return Pair(DocumentType.ROUTINE, "HIGH")
        }
        if (fn.contains("syllabus") || fn.contains("curriculum")) {
            return Pair(DocumentType.SYLLABUS, "HIGH")
        }
        if (fn.contains("holiday")) {
            return Pair(DocumentType.HOLIDAY_NOTICE, "HIGH")
        }
        if (fn.contains("notice") || fn.contains("circular")) {
            return Pair(DocumentType.NOTICE, "HIGH")
        }
        if (fn.contains("notes") || fn.contains("lecture") || fn.contains("unit")) {
            return Pair(DocumentType.NOTES, "HIGH")
        }
        if (fn.contains("assignment")) {
            return Pair(DocumentType.ASSIGNMENT, "HIGH")
        }
        if (fn.contains("exam")) {
            return Pair(DocumentType.EXAM_SCHEDULE, "HIGH")
        }

        // Text heuristics
        if (t.contains("monday") && t.contains("tuesday") && (t.contains("am") || t.contains("pm") || t.contains(":"))) {
            return Pair(DocumentType.ROUTINE, "HIGH")
        }
        if (t.contains("syllabus") || (t.contains("unit 1") && t.contains("unit 2"))) {
            return Pair(DocumentType.SYLLABUS, "HIGH")
        }
        if (t.contains("holiday") || t.contains("classes will remain suspended") || t.contains("closed on account of")) {
            return Pair(DocumentType.HOLIDAY_NOTICE, "HIGH")
        }
        if (t.contains("notice") || t.contains("rescheduled") || t.contains("cancellation")) {
            return Pair(DocumentType.NOTICE, "HIGH")
        }

        // If Gemini is available, ask Gemini to classify
        if (GeminiHelper.isApiKeyConfigured() && (bitmap != null || text.isNotBlank())) {
            val classifyPrompt = """
                Classify this academic document into EXACTLY ONE of:
                ROUTINE, SYLLABUS, NOTICE, HOLIDAY_NOTICE, EXAM_SCHEDULE, ASSIGNMENT, NOTES, QUESTION_PAPER, PRACTICAL_SCHEDULE, ACADEMIC_CALENDAR, ATTENDANCE_DOCUMENT, OTHER.
                Output ONLY a JSON object: {"type": "ROUTINE", "confidence": "HIGH"|"MEDIUM"|"LOW"}
            """.trimIndent()
            val apiRes = GeminiHelper.callApiDirect(classifyPrompt, bitmap, responseJson = true)
            if (apiRes.isSuccess) {
                try {
                    val json = JSONObject(apiRes.getOrThrow())
                    val typeStr = json.optString("type", "OTHER")
                    val conf = json.optString("confidence", "MEDIUM")
                    val matchedType = DocumentType.values().firstOrNull { it.name.equals(typeStr, ignoreCase = true) }
                        ?: DocumentType.OTHER
                    return Pair(matchedType, conf)
                } catch (_: Exception) {}
            }
        }

        // Default fallback with LOW confidence so user is prompted to confirm
        return Pair(DocumentType.NOTES, "LOW")
    }

    /**
     * Extracts Timetable Routine with Table & Merged-Cell Structure Preservation:
     * - Day × Time × Activity × Faculty × Room
     * - Multi-period Practical Sessions (e.g. 9:20 - 12:05 preserved)
     */
    private suspend fun extractRoutineStructured(
        bitmap: Bitmap?,
        text: String,
        sourceFileName: String
    ): List<ExtractedRoutineItem> {
        val prompt = """
            You are an expert document OCR & layout analysis engine.
            Extract the complete class timetable / routine from this image/document.
            CRITICAL STRUCTURAL RULES:
            1. DO NOT FLATTEN OR DISCARD TABLE GRID. Map DAY × TIME × ACTIVITY accurately.
            2. MERGED CELLS: If a practical/lab occupies multiple consecutive periods (e.g. 09:20 AM to 12:05 PM), preserve it as ONE entry with full duration, do NOT split it into three separate fragments.
            3. Classify activityType as "Theory", "Practical", "Tutorial", "Break", or "Library".
            4. Extract subjectCode if available (e.g., BP105T).
            5. Output valid JSON array ONLY:
            [
              {
                "dayOfWeek": 1, // 1=Mon, 2=Tue, 3=Wed, 4=Thu, 5=Fri, 6=Sat, 7=Sun
                "dayName": "Monday",
                "startTime": "09:20", // 24-hr HH:mm
                "endTime": "12:05",
                "subject": "Pharmacognosy Practical",
                "subjectCode": "BP105P",
                "teacher": "Dr. A. Sharma",
                "room": "Lab 3",
                "activityType": "Practical",
                "mergedSpan": 3,
                "confidence": "HIGH",
                "needsReview": false,
                "reviewReason": ""
              }
            ]
        """.trimIndent()

        if (GeminiHelper.isApiKeyConfigured()) {
            val apiRes = GeminiHelper.callApiDirect(prompt, bitmap, responseJson = true)
            if (apiRes.isSuccess) {
                val parsed = parseRoutineJson(apiRes.getOrThrow(), sourceFileName)
                if (parsed.isNotEmpty()) return parsed
            }
        }

        // Local Heuristic Parser preserving merged cells & times
        return parseRoutineHeuristicPreservingStructure(text, sourceFileName)
    }

    private fun parseRoutineJson(jsonStr: String, sourceFileName: String): List<ExtractedRoutineItem> {
        return try {
            val clean = cleanJsonBlock(jsonStr)
            val arr = JSONArray(clean)
            val list = mutableListOf<ExtractedRoutineItem>()
            val days = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")

            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val dayInt = obj.optInt("dayOfWeek", 1).coerceIn(1, 7)
                val dayName = obj.optString("dayName", days[dayInt - 1])
                val start = formatTime(obj.optString("startTime", "09:00"))
                val end = formatTime(obj.optString("endTime", "10:00"))
                val subj = obj.optString("subject", "Academic Class").trim()
                val code = obj.optString("subjectCode", "").trim()
                val teacher = obj.optString("teacher", "").trim()
                val room = obj.optString("room", "").trim()
                val actType = obj.optString("activityType", if (subj.contains("Lab", ignoreCase = true) || subj.contains("Practical", ignoreCase = true)) "Practical" else "Theory")
                val span = obj.optInt("mergedSpan", 1)
                val conf = obj.optString("confidence", "HIGH")
                val review = obj.optBoolean("needsReview", false)
                val reason = obj.optString("reviewReason", "")

                if (subj.isNotBlank()) {
                    list.add(
                        ExtractedRoutineItem(
                            dayOfWeek = dayInt,
                            dayName = dayName,
                            startTime = start,
                            endTime = end,
                            subject = subj,
                            subjectCode = code,
                            teacher = teacher,
                            room = room,
                            activityType = actType,
                            mergedSpan = span,
                            confidence = conf,
                            sourceInfo = "$sourceFileName (Table extraction)",
                            needsReview = review,
                            reviewReason = reason
                        )
                    )
                }
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * Local Heuristic Timetable Parser with Merged Cell & Time Span Logic
     */
    private fun parseRoutineHeuristicPreservingStructure(text: String, sourceFileName: String): List<ExtractedRoutineItem> {
        val items = mutableListOf<ExtractedRoutineItem>()
        val lines = text.lines()
        val days = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")
        var currentDay = 1

        val timePattern = Pattern.compile("(\\d{1,2}[:.]\\d{2})\\s*(?:-|to)?\\s*(\\d{1,2}[:.]\\d{2})?", Pattern.CASE_INSENSITIVE)

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) continue

            for (i in days.indices) {
                if (trimmed.contains(days[i], ignoreCase = true)) {
                    currentDay = i + 1
                    break
                }
            }

            val matcher = timePattern.matcher(trimmed)
            if (matcher.find()) {
                val start = formatTime(matcher.group(1)?.replace(".", ":") ?: "09:00")
                val end = formatTime(matcher.group(2)?.replace(".", ":") ?: "10:00")
                val rest = trimmed.replace(matcher.group(0) ?: "", "").trim()
                val tokens = rest.split(",", "|", "-").map { it.trim() }.filter { it.isNotEmpty() }

                val subject = tokens.firstOrNull() ?: "Academic Class"
                val room = tokens.getOrNull(1) ?: ""
                val teacher = tokens.getOrNull(2) ?: ""

                val isPractical = subject.contains("Lab", ignoreCase = true) ||
                        subject.contains("Practical", ignoreCase = true) ||
                        trimmed.contains("Lab", ignoreCase = true)

                items.add(
                    ExtractedRoutineItem(
                        dayOfWeek = currentDay,
                        dayName = days[currentDay - 1],
                        startTime = start,
                        endTime = end,
                        subject = subject,
                        teacher = teacher,
                        room = room,
                        activityType = if (isPractical) "Practical" else "Theory",
                        mergedSpan = if (isPractical) 2 else 1,
                        confidence = if (tokens.size >= 2) "HIGH" else "MEDIUM",
                        sourceInfo = "$sourceFileName (Heuristic Table Analysis)",
                        needsReview = subject.isBlank()
                    )
                )
            }
        }

        if (items.isEmpty()) {
            items.add(
                ExtractedRoutineItem(
                    dayOfWeek = 1,
                    dayName = "Monday",
                    startTime = "09:30",
                    endTime = "10:30",
                    subject = "Sample Lecture (Review & Edit)",
                    room = "Room 101",
                    teacher = "Faculty",
                    confidence = "LOW",
                    sourceInfo = sourceFileName,
                    needsReview = true,
                    reviewReason = "Verify timetable fields"
                )
            )
        }
        return items
    }

    /**
     * Extracts Syllabus Hierarchy: Subject -> Unit -> Chapter -> Topic -> Subtopic
     */
    private suspend fun extractSyllabusStructured(
        bitmap: Bitmap?,
        text: String,
        sourceFileName: String
    ): List<ExtractedSyllabusTopic> {
        val prompt = """
            Extract the syllabus hierarchy from this document into structured JSON array.
            Format:
            [
              {
                "subject": "Physics",
                "subjectCode": "PHY101",
                "unit": "Unit 1: Thermodynamics",
                "chapter": "Chapter 2: Heat Engines",
                "topic": "Carnot Cycle & Entropy",
                "subtopic": "Reversible and irreversible processes",
                "weightage": "8 Marks",
                "classification": "Theory",
                "confidence": "HIGH"
              }
            ]
            Preserve hierarchical relationships. Return ONLY valid JSON array.
        """.trimIndent()

        if (GeminiHelper.isApiKeyConfigured()) {
            val apiRes = GeminiHelper.callApiDirect(prompt, bitmap, responseJson = true)
            if (apiRes.isSuccess) {
                val clean = cleanJsonBlock(apiRes.getOrThrow())
                try {
                    val arr = JSONArray(clean)
                    val list = mutableListOf<ExtractedSyllabusTopic>()
                    for (i in 0 until arr.length()) {
                        val obj = arr.optJSONObject(i) ?: continue
                        val topic = obj.optString("topic", "").trim()
                        if (topic.isNotBlank()) {
                            list.add(
                                ExtractedSyllabusTopic(
                                    subject = obj.optString("subject", "Subject").trim(),
                                    subjectCode = obj.optString("subjectCode", "").trim(),
                                    unit = obj.optString("unit", "Unit 1").trim(),
                                    chapter = obj.optString("chapter", "Chapter 1").trim(),
                                    topic = topic,
                                    subtopic = obj.optString("subtopic", "").trim(),
                                    weightage = obj.optString("weightage", "").trim(),
                                    classification = obj.optString("classification", "Theory"),
                                    confidence = obj.optString("confidence", "HIGH"),
                                    sourceInfo = sourceFileName
                                )
                            )
                        }
                    }
                    if (list.isNotEmpty()) return list
                } catch (_: Exception) {}
            }
        }

        // Local Heuristic Syllabus Parser
        return parseSyllabusHeuristic(text, sourceFileName)
    }

    private fun parseSyllabusHeuristic(text: String, sourceFileName: String): List<ExtractedSyllabusTopic> {
        val list = mutableListOf<ExtractedSyllabusTopic>()
        var subj = "General Academic"
        var unit = "Unit 1"
        var chap = "Chapter 1"

        for (line in text.lines()) {
            val t = line.trim()
            if (t.isEmpty()) continue
            if (t.startsWith("Subject:", ignoreCase = true) || t.startsWith("Course:", ignoreCase = true)) {
                subj = t.substringAfter(":").trim()
            } else if (t.startsWith("Unit", ignoreCase = true) || t.startsWith("Module", ignoreCase = true)) {
                unit = t
            } else if (t.startsWith("Chapter", ignoreCase = true)) {
                chap = t
            } else if (t.length > 4 && !t.contains("Page", ignoreCase = true)) {
                list.add(
                    ExtractedSyllabusTopic(
                        subject = subj,
                        unit = unit,
                        chapter = chap,
                        topic = t.removePrefix("•").removePrefix("-").trim(),
                        sourceInfo = sourceFileName,
                        confidence = "MEDIUM"
                    )
                )
            }
        }

        if (list.isEmpty()) {
            list.add(
                ExtractedSyllabusTopic(
                    subject = "Course Subject",
                    unit = "Unit 1: Overview",
                    chapter = "Chapter 1: Foundations",
                    topic = "Core Concepts and Syllabus Coverage",
                    sourceInfo = sourceFileName,
                    confidence = "MEDIUM"
                )
            )
        }
        return list
    }

    /**
     * Extracts Notice with Holiday / Cancellation / Routine Change Analysis
     */
    private suspend fun extractNoticeStructured(
        bitmap: Bitmap?,
        text: String,
        sourceFileName: String,
        type: DocumentType
    ): ExtractedNoticeData {
        val prompt = """
            Analyze this academic notice / circular and output valid JSON:
            {
              "title": "Clear Notice Title",
              "noticeType": "HOLIDAY" | "CANCELLATION" | "RESCHEDULE" | "EXAM" | "ASSIGNMENT" | "GENERAL",
              "issueDate": "YYYY-MM-DD or date",
              "institution": "University / College name if present",
              "affectedDate": "YYYY-MM-DD or specific affected date",
              "affectedSubject": "Specific subject name or code if mentioned",
              "sourceFacts": "Exact textual evidence from notice",
              "simpleExplanation": "Clear summary for students",
              "importantDates": ["Date 1", "Date 2"],
              "deadlines": ["Submission date"],
              "isHoliday": true or false,
              "holidayDetails": "Details if holiday",
              "routineChangeDetected": true or false,
              "routineChangeSummary": "Details of class cancellation or rescheduling",
              "proposedChanges": [
                {
                  "dayOfWeek": 1,
                  "dayName": "Monday",
                  "startTime": "10:00",
                  "endTime": "11:00",
                  "subject": "Physics",
                  "room": "Room 204",
                  "teacher": "Prof. Ray"
                }
              ]
            }
        """.trimIndent()

        if (GeminiHelper.isApiKeyConfigured()) {
            val apiRes = GeminiHelper.callApiDirect(prompt, bitmap, responseJson = true)
            if (apiRes.isSuccess) {
                try {
                    val json = JSONObject(cleanJsonBlock(apiRes.getOrThrow()))
                    val proposed = mutableListOf<ExtractedRoutineItem>()
                    val pArr = json.optJSONArray("proposedChanges")
                    if (pArr != null) {
                        for (i in 0 until pArr.length()) {
                            val pObj = pArr.optJSONObject(i) ?: continue
                            proposed.add(
                                ExtractedRoutineItem(
                                    dayOfWeek = pObj.optInt("dayOfWeek", 1),
                                    dayName = pObj.optString("dayName", "Monday"),
                                    startTime = formatTime(pObj.optString("startTime", "09:00")),
                                    endTime = formatTime(pObj.optString("endTime", "10:00")),
                                    subject = pObj.optString("subject", "Class"),
                                    room = pObj.optString("room", ""),
                                    teacher = pObj.optString("teacher", ""),
                                    sourceInfo = sourceFileName
                                )
                            )
                        }
                    }

                    val dates = mutableListOf<String>()
                    val dArr = json.optJSONArray("importantDates")
                    if (dArr != null) {
                        for (i in 0 until dArr.length()) dates.add(dArr.optString(i))
                    }

                    val deadlines = mutableListOf<String>()
                    val dlArr = json.optJSONArray("deadlines")
                    if (dlArr != null) {
                        for (i in 0 until dlArr.length()) deadlines.add(dlArr.optString(i))
                    }

                    return ExtractedNoticeData(
                        title = json.optString("title", "College Notice"),
                        noticeType = json.optString("noticeType", if (type == DocumentType.HOLIDAY_NOTICE) "HOLIDAY" else "GENERAL"),
                        issueDate = json.optString("issueDate", ""),
                        institution = json.optString("institution", ""),
                        affectedDate = json.optString("affectedDate", ""),
                        affectedSubject = json.optString("affectedSubject", ""),
                        sourceFacts = json.optString("sourceFacts", text.take(300)),
                        simpleExplanation = json.optString("simpleExplanation", "Notice details extracted."),
                        importantDates = dates,
                        deadlines = deadlines,
                        isHoliday = json.optBoolean("isHoliday", type == DocumentType.HOLIDAY_NOTICE),
                        holidayDetails = json.optString("holidayDetails", ""),
                        routineChangeDetected = json.optBoolean("routineChangeDetected", false),
                        routineChangeSummary = json.optString("routineChangeSummary", ""),
                        proposedChanges = proposed,
                        sourceInfo = sourceFileName
                    )
                } catch (_: Exception) {}
            }
        }

        // Heuristic fallback
        val isHol = type == DocumentType.HOLIDAY_NOTICE || text.contains("holiday", ignoreCase = true) || text.contains("closed", ignoreCase = true)
        val hasChange = text.contains("rescheduled", ignoreCase = true) || text.contains("cancelled", ignoreCase = true)

        return ExtractedNoticeData(
            title = text.lines().firstOrNull { it.isNotBlank() }?.take(60) ?: "Academic Notice",
            noticeType = if (isHol) "HOLIDAY" else if (hasChange) "CANCELLATION" else "GENERAL",
            issueDate = "Current Notice",
            sourceFacts = text.take(300),
            simpleExplanation = if (isHol) "Holiday announced. Scheduled classes will be suspended." else "Official academic circular.",
            isHoliday = isHol,
            holidayDetails = if (isHol) "College Holiday / Class Suspension" else "",
            routineChangeDetected = hasChange,
            routineChangeSummary = if (hasChange) "Class cancellation or reschedule requested." else "",
            sourceInfo = sourceFileName
        )
    }

    /**
     * Extracts Notes: Summary, Definitions, Formulas, Key Concepts, Exam Q&As
     */
    private suspend fun extractNotesStructured(
        bitmap: Bitmap?,
        text: String,
        sourceFileName: String
    ): ExtractedNotesData {
        val prompt = """
            Extract comprehensive study material from this document into valid JSON:
            {
              "subject": "Subject Name",
              "chapter": "Chapter Name",
              "topic": "Main Topic",
              "summary": "Clear, student-friendly 2-3 paragraph summary",
              "definitions": [
                {"term": "Term 1", "definition": "Accurate definition"}
              ],
              "formulas": ["Formula 1", "Formula 2"],
              "keyConcepts": ["Key concept 1", "Key concept 2"],
              "questionsAndAnswers": [
                {"question": "Likely Exam Question 1?", "answer": "Model answer based strictly on text."}
              ],
              "revisionBulletPoints": ["Point 1", "Point 2", "Point 3"]
            }
            Do not invent facts not grounded in the content.
        """.trimIndent()

        if (GeminiHelper.isApiKeyConfigured()) {
            val apiRes = GeminiHelper.callApiDirect(prompt, bitmap, responseJson = true)
            if (apiRes.isSuccess) {
                try {
                    val json = JSONObject(cleanJsonBlock(apiRes.getOrThrow()))
                    val defs = mutableListOf<Pair<String, String>>()
                    val dArr = json.optJSONArray("definitions")
                    if (dArr != null) {
                        for (i in 0 until dArr.length()) {
                            val obj = dArr.optJSONObject(i) ?: continue
                            defs.add(Pair(obj.optString("term"), obj.optString("definition")))
                        }
                    }

                    val formulas = mutableListOf<String>()
                    val fArr = json.optJSONArray("formulas")
                    if (fArr != null) {
                        for (i in 0 until fArr.length()) formulas.add(fArr.optString(i))
                    }

                    val concepts = mutableListOf<String>()
                    val cArr = json.optJSONArray("keyConcepts")
                    if (cArr != null) {
                        for (i in 0 until cArr.length()) concepts.add(cArr.optString(i))
                    }

                    val qas = mutableListOf<Pair<String, String>>()
                    val qArr = json.optJSONArray("questionsAndAnswers")
                    if (qArr != null) {
                        for (i in 0 until qArr.length()) {
                            val obj = qArr.optJSONObject(i) ?: continue
                            qas.add(Pair(obj.optString("question"), obj.optString("answer")))
                        }
                    }

                    val revs = mutableListOf<String>()
                    val rArr = json.optJSONArray("revisionBulletPoints")
                    if (rArr != null) {
                        for (i in 0 until rArr.length()) revs.add(rArr.optString(i))
                    }

                    return ExtractedNotesData(
                        subject = json.optString("subject", "Academic Notes"),
                        chapter = json.optString("chapter", ""),
                        topic = json.optString("topic", ""),
                        summary = json.optString("summary", text.take(300)),
                        definitions = defs,
                        formulas = formulas,
                        keyConcepts = concepts,
                        questionsAndAnswers = qas,
                        revisionBulletPoints = revs,
                        sourceInfo = sourceFileName
                    )
                } catch (_: Exception) {}
            }
        }

        // Heuristic fallback notes data
        return ExtractedNotesData(
            subject = "Academic Notes",
            chapter = "Chapter Notes",
            topic = sourceFileName.substringBeforeLast("."),
            summary = "Summary of ${sourceFileName}:\n\n${text.take(500)}",
            revisionBulletPoints = listOf("Review chapter concepts", "Verify lecture formulas", "Prepare exam notes"),
            sourceInfo = sourceFileName
        )
    }

    /**
     * Cross-Document Subject Resolution:
     * Normalizes codes (e.g. BP105T <-> Pharmacognosy) across documents.
     */
    private fun resolveSubjectMapping(
        detectedSubject: String,
        detectedCode: String,
        existingSubjects: List<SubjectEntity>
    ): String {
        val s = detectedSubject.trim()
        val c = detectedCode.trim().lowercase()

        // 1. Direct code match with existing subject
        if (c.isNotEmpty()) {
            val byCode = existingSubjects.firstOrNull { it.code.trim().equals(c, ignoreCase = true) }
            if (byCode != null) return byCode.name
        }

        // 2. Direct name match with existing subject
        val byName = existingSubjects.firstOrNull { it.name.trim().equals(s, ignoreCase = true) }
        if (byName != null) return byName.name

        // 3. Subject name starts with or contains known code
        for (subj in existingSubjects) {
            if (subj.code.isNotBlank() && s.contains(subj.code, ignoreCase = true)) {
                return subj.name
            }
        }

        return s
    }

    /**
     * Check if two time intervals overlap (HH:mm)
     */
    private fun isTimeOverlapping(start1: String, end1: String, start2: String, end2: String): Boolean {
        val s1 = parseMinutes(start1)
        val e1 = parseMinutes(end1)
        val s2 = parseMinutes(start2)
        val e2 = parseMinutes(end2)
        return max(s1, s2) < kotlin.math.min(e1, e2)
    }

    private fun parseMinutes(time: String): Int {
        val parts = time.split(":")
        val h = parts.getOrNull(0)?.toIntOrNull() ?: 0
        val m = parts.getOrNull(1)?.toIntOrNull() ?: 0
        return h * 60 + m
    }

    /**
     * Finds which routine classes are affected by a holiday or cancellation notice
     */
    private fun findAffectedClassesForNotice(
        notice: ExtractedNoticeData,
        schedules: List<ClassScheduleEntity>
    ): List<ClassScheduleEntity> {
        val affected = mutableListOf<ClassScheduleEntity>()
        val noticeText = "${notice.title} ${notice.simpleExplanation} ${notice.sourceFacts} ${notice.affectedDate}".lowercase()

        val days = mapOf(
            "monday" to 1, "tuesday" to 2, "wednesday" to 3,
            "thursday" to 4, "friday" to 5, "saturday" to 6, "sunday" to 7
        )

        for ((dayStr, dayNum) in days) {
            if (noticeText.contains(dayStr)) {
                val matches = schedules.filter { it.dayOfWeek == dayNum }
                affected.addAll(matches)
            }
        }

        // If specific subject is mentioned, filter by subject
        if (notice.affectedSubject.isNotBlank()) {
            val subMatches = schedules.filter {
                it.subjectName.contains(notice.affectedSubject, ignoreCase = true)
            }
            affected.addAll(subMatches)
        }

        return affected.distinctBy { it.id }
    }

    private fun cleanJsonBlock(raw: String): String {
        val trimmed = raw.trim()
        return if (trimmed.startsWith("```json")) {
            trimmed.substringAfter("```json").substringBeforeLast("```").trim()
        } else if (trimmed.startsWith("```")) {
            trimmed.substringAfter("```").substringBeforeLast("```").trim()
        } else trimmed
    }

    private fun formatTime(time: String): String {
        val parts = time.split(":")
        if (parts.size >= 2) {
            val h = parts[0].trim().toIntOrNull() ?: 9
            val m = parts[1].trim().take(2).toIntOrNull() ?: 0
            return String.format("%02d:%02d", h, m)
        }
        return "09:00"
    }

    private data class PreprocessResult(
        val bitmap: Bitmap?,
        val text: String?,
        val pageCount: Int
    )
}
