package com.example.data.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import com.example.BuildConfig
import com.example.data.model.ExtractedNoticeData
import com.example.data.model.ExtractedRoutineItem
import com.example.data.model.ExtractedSyllabusTopic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

object GeminiHelper {
    private const val MODEL_NAME = "gemini-2.5-flash"
    private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models/$MODEL_NAME:generateContent"

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    fun isApiKeyConfigured(): Boolean {
        return try {
            val key = BuildConfig.GEMINI_API_KEY
            key.isNotBlank() && key != "MY_GEMINI_API_KEY" && !key.startsWith("YOUR_")
        } catch (_: Throwable) {
            false
        }
    }

    private fun getApiKey(): String {
        return try {
            BuildConfig.GEMINI_API_KEY
        } catch (_: Throwable) {
            ""
        }
    }

    // Safe File/URI Text Reader
    suspend fun extractTextOrImageFromUri(
        context: Context,
        uri: Uri,
        mimeType: String?
    ): Pair<String?, Bitmap?> = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        try {
            if (mimeType?.startsWith("image/") == true) {
                resolver.openInputStream(uri)?.use { stream ->
                    val bitmap = BitmapFactory.decodeStream(stream)
                    return@withContext Pair(null, bitmap)
                }
            } else {
                // Try reading text / PDF / document stream
                resolver.openInputStream(uri)?.use { stream ->
                    val text = stream.bufferedReader().use { it.readText() }
                    if (text.isNotBlank()) {
                        return@withContext Pair(text.take(15000), null)
                    }
                }
            }
        } catch (_: Exception) {
            // Safe fallback
        }
        Pair(null, null)
    }

    // Direct Gemini REST API caller
    suspend fun callApiDirect(
        prompt: String,
        bitmap: Bitmap? = null,
        responseJson: Boolean = false
    ): Result<String> = callGeminiApi(prompt, bitmap, responseJson)

    private suspend fun callGeminiApi(
        prompt: String,
        bitmap: Bitmap? = null,
        responseJson: Boolean = false
    ): Result<String> = withContext(Dispatchers.IO) {
        if (!isApiKeyConfigured()) {
            return@withContext Result.failure(
                IllegalStateException("Gemini API key is not configured. Using offline parser.")
            )
        }

        try {
            val partsArray = JSONArray()
            partsArray.put(JSONObject().put("text", prompt))

            if (bitmap != null) {
                val outputStream = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, 80, outputStream)
                val base64Image = Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)
                val inlineData = JSONObject().apply {
                    put("mimeType", "image/jpeg")
                    put("data", base64Image)
                }
                partsArray.put(JSONObject().put("inlineData", inlineData))
            }

            val contentObj = JSONObject().put("parts", partsArray)
            val contentsArray = JSONArray().put(contentObj)

            val rootJson = JSONObject().put("contents", contentsArray)

            if (responseJson) {
                val genConfig = JSONObject().put("responseMimeType", "application/json")
                rootJson.put("generationConfig", genConfig)
            }

            val requestBody = rootJson.toString().toRequestBody("application/json".toMediaType())
            val url = "$BASE_URL?key=${getApiKey()}"

            val request = Request.Builder()
                .url(url)
                .post(requestBody)
                .build()

            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                val errorBody = response.body?.string() ?: ""
                return@withContext Result.failure(Exception("API Error ${response.code}: $errorBody"))
            }

            val resBody = response.body?.string() ?: ""
            val jsonRes = JSONObject(resBody)
            val candidates = jsonRes.optJSONArray("candidates")
            val firstCandidate = candidates?.optJSONObject(0)
            val content = firstCandidate?.optJSONObject("content")
            val parts = content?.optJSONArray("parts")
            val text = parts?.optJSONObject(0)?.optString("text")

            if (text != null && text.isNotBlank()) {
                Result.success(text)
            } else {
                Result.failure(Exception("Empty response from AI"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Extract Routine
    suspend fun extractRoutine(
        textSample: String?,
        bitmap: Bitmap?
    ): List<ExtractedRoutineItem> = withContext(Dispatchers.Default) {
        val prompt = """
            Extract weekly class timetable into structured JSON array.
            Format:
            [
              {
                "dayOfWeek": 1, // 1 for Monday, 2 for Tuesday, ..., 7 for Sunday
                "dayName": "Monday",
                "startTime": "09:00", // 24-hr HH:mm format
                "endTime": "10:00",
                "subject": "Mathematics",
                "teacher": "Dr. Smith",
                "room": "Room 101",
                "needsReview": false,
                "reviewReason": ""
              }
            ]
            If ambiguous, set needsReview: true. Return ONLY valid JSON array.
            Input content:
            ${textSample ?: "Attached image timetable"}
        """.trimIndent()

        val apiResult = callGeminiApi(prompt, bitmap, responseJson = true)
        if (apiResult.isSuccess) {
            try {
                val raw = apiResult.getOrThrow().trim()
                val cleanJson = if (raw.startsWith("```json")) {
                    raw.substringAfter("```json").substringBeforeLast("```").trim()
                } else if (raw.startsWith("```")) {
                    raw.substringAfter("```").substringBeforeLast("```").trim()
                } else raw

                val jsonArray = JSONArray(cleanJson)
                val list = mutableListOf<ExtractedRoutineItem>()
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.optJSONObject(i) ?: continue
                    val day = obj.optInt("dayOfWeek", 1).coerceIn(1, 7)
                    val dayName = obj.optString("dayName", getDayName(day))
                    val start = obj.optString("startTime", "09:00")
                    val end = obj.optString("endTime", "10:00")
                    val subject = obj.optString("subject", "General Class")
                    val teacher = obj.optString("teacher", "")
                    val room = obj.optString("room", "")
                    val review = obj.optBoolean("needsReview", false)
                    val reason = obj.optString("reviewReason", "")

                    if (subject.isNotBlank()) {
                        list.add(
                            ExtractedRoutineItem(
                                dayOfWeek = day,
                                dayName = dayName,
                                startTime = formatTime(start),
                                endTime = formatTime(end),
                                subject = subject.trim(),
                                teacher = teacher.trim(),
                                room = room.trim(),
                                needsReview = review,
                                reviewReason = reason
                            )
                        )
                    }
                }
                if (list.isNotEmpty()) return@withContext list
            } catch (_: Exception) {
                // Malformed response, fallback safely
            }
        }

        // Local robust heuristic fallback
        return@withContext parseRoutineHeuristic(textSample ?: "")
    }

    // Extract Syllabus
    suspend fun extractSyllabus(
        textSample: String?,
        bitmap: Bitmap?
    ): List<ExtractedSyllabusTopic> = withContext(Dispatchers.Default) {
        val prompt = """
            Extract academic syllabus into structured JSON array.
            Format:
            [
              {
                "subject": "Physics",
                "unit": "Unit 1: Thermodynamics",
                "chapter": "Chapter 2: Laws of Heat",
                "topic": "Carnot Engine & Efficiency",
                "needsReview": false
              }
            ]
            Never invent topics. Return ONLY valid JSON array.
            Input content:
            ${textSample ?: "Attached syllabus image"}
        """.trimIndent()

        val apiResult = callGeminiApi(prompt, bitmap, responseJson = true)
        if (apiResult.isSuccess) {
            try {
                val raw = apiResult.getOrThrow().trim()
                val cleanJson = if (raw.startsWith("```json")) {
                    raw.substringAfter("```json").substringBeforeLast("```").trim()
                } else if (raw.startsWith("```")) {
                    raw.substringAfter("```").substringBeforeLast("```").trim()
                } else raw

                val jsonArray = JSONArray(cleanJson)
                val list = mutableListOf<ExtractedSyllabusTopic>()
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.optJSONObject(i) ?: continue
                    val subject = obj.optString("subject", "Academic Subject")
                    val unit = obj.optString("unit", "Unit 1")
                    val chapter = obj.optString("chapter", "Chapter 1")
                    val topic = obj.optString("topic", "")
                    val review = obj.optBoolean("needsReview", false)

                    if (topic.isNotBlank()) {
                        list.add(
                            ExtractedSyllabusTopic(
                                subject = subject.trim(),
                                unit = unit.trim(),
                                chapter = chapter.trim(),
                                topic = topic.trim(),
                                needsReview = review
                            )
                        )
                    }
                }
                if (list.isNotEmpty()) return@withContext list
            } catch (_: Exception) {
                // Fallback
            }
        }

        return@withContext parseSyllabusHeuristic(textSample ?: "")
    }

    // Extract Notice with holiday/class change detection
    suspend fun extractNotice(
        textSample: String?,
        bitmap: Bitmap?
    ): ExtractedNoticeData = withContext(Dispatchers.Default) {
        val prompt = """
            Analyze this academic notice and provide structured JSON:
            {
              "title": "Title of notice",
              "issueDate": "YYYY-MM-DD or date string",
              "sourceFacts": "Exact key facts directly from notice",
              "simpleExplanation": "Clear, student-friendly explanation",
              "importantDates": ["Date 1", "Date 2"],
              "deadlines": ["Submission date"],
              "isHoliday": true or false,
              "holidayDetails": "Details if holiday",
              "routineChangeDetected": true or false,
              "routineChangeSummary": "Details of class cancellation/room change/reschedule",
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
            Do not invent unstated facts. Return ONLY JSON.
            Input content:
            ${textSample ?: "Attached notice image"}
        """.trimIndent()

        val apiResult = callGeminiApi(prompt, bitmap, responseJson = true)
        if (apiResult.isSuccess) {
            try {
                val raw = apiResult.getOrThrow().trim()
                val cleanJson = if (raw.startsWith("```json")) {
                    raw.substringAfter("```json").substringBeforeLast("```").trim()
                } else if (raw.startsWith("```")) {
                    raw.substringAfter("```").substringBeforeLast("```").trim()
                } else raw

                val obj = JSONObject(cleanJson)
                val title = obj.optString("title", "Academic Notice")
                val issueDate = obj.optString("issueDate", "")
                val facts = obj.optString("sourceFacts", textSample?.take(300) ?: "Notice content")
                val explanation = obj.optString("simpleExplanation", "Notice details extracted.")
                val isHoliday = obj.optBoolean("isHoliday", false)
                val holidayDetails = obj.optString("holidayDetails", "")
                val routineChange = obj.optBoolean("routineChangeDetected", false)
                val routineSummary = obj.optString("routineChangeSummary", "")

                val datesList = mutableListOf<String>()
                val dArr = obj.optJSONArray("importantDates")
                if (dArr != null) {
                    for (i in 0 until dArr.length()) {
                        datesList.add(dArr.optString(i))
                    }
                }

                val deadlinesList = mutableListOf<String>()
                val dlArr = obj.optJSONArray("deadlines")
                if (dlArr != null) {
                    for (i in 0 until dlArr.length()) {
                        deadlinesList.add(dlArr.optString(i))
                    }
                }

                val proposed = mutableListOf<ExtractedRoutineItem>()
                val pArr = obj.optJSONArray("proposedChanges")
                if (pArr != null) {
                    for (i in 0 until pArr.length()) {
                        val item = pArr.optJSONObject(i) ?: continue
                        proposed.add(
                            ExtractedRoutineItem(
                                dayOfWeek = item.optInt("dayOfWeek", 1),
                                dayName = item.optString("dayName", "Monday"),
                                startTime = item.optString("startTime", "09:00"),
                                endTime = item.optString("endTime", "10:00"),
                                subject = item.optString("subject", "Class"),
                                room = item.optString("room", ""),
                                teacher = item.optString("teacher", "")
                            )
                        )
                    }
                }

                return@withContext ExtractedNoticeData(
                    title = title,
                    issueDate = issueDate,
                    sourceFacts = facts,
                    simpleExplanation = explanation,
                    importantDates = datesList,
                    deadlines = deadlinesList,
                    isHoliday = isHoliday,
                    holidayDetails = holidayDetails,
                    routineChangeDetected = routineChange,
                    routineChangeSummary = routineSummary,
                    proposedChanges = proposed
                )
            } catch (_: Exception) {
                // Fallback
            }
        }

        return@withContext parseNoticeHeuristic(textSample ?: "")
    }

    // Notes Analysis
    suspend fun analyzeNotes(
        content: String,
        requestType: String // "SUMMARY", "QUESTIONS", "DEFINITIONS"
    ): String = withContext(Dispatchers.Default) {
        val prompt = when (requestType) {
            "QUESTIONS" -> "Based STRICTLY on these notes, generate important exam questions with concise answers. If notes don't have enough info, say so clearly:\n\n$content"
            "DEFINITIONS" -> "Based STRICTLY on these notes, list key definitions and formulas. If not found, say so:\n\n$content"
            else -> "Provide a clear summary, core principles, and bullet-point revision notes strictly from this document:\n\n$content"
        }

        val result = callGeminiApi(prompt)
        if (result.isSuccess) {
            result.getOrThrow()
        } else {
            "AI Service Note: ${result.exceptionOrNull()?.message ?: "Unable to connect."}\n\nLocal Notes Preview:\n${content.take(400)}..."
        }
    }

    // Academic Assistant Q&A
    suspend fun askAssistant(
        question: String,
        contextSummary: String
    ): String = withContext(Dispatchers.Default) {
        val prompt = """
            You are StudyMate, an intelligent, grounded academic assistant for students.
            Support English and Bengali (বাংলা).
            
            MANDATORY ANTI-HALLUCINATION RULES:
            1. STRICT GROUNDING: Answer using ONLY the verified stored academic records below.
            2. NEVER INVENT: Do not fabricate classes, attendance %, syllabus topics, deadlines, notices, faculty names, or exam dates.
            3. SOURCE ATTRIBUTION: State which document/record the fact came from whenever applicable (e.g. "According to Routine...", "From Notice...").
            4. MISSING INFO: If the information is not in the stored records, explicitly state:
               "Not found in your uploaded documents or stored records."
               Do not guess or assume.

            STORED ACADEMIC RECORDS:
            $contextSummary

            STUDENT QUESTION:
            $question
        """.trimIndent()

        val result = callGeminiApi(prompt)
        if (result.isSuccess) {
            result.getOrThrow()
        } else {
            // Intelligent rule-based offline answer generator!
            generateOfflineAssistantAnswer(question, contextSummary)
        }
    }

    // Offline rule-based Assistant generator that extracts only the section requested
    private fun generateOfflineAssistantAnswer(question: String, context: String): String {
        val q = question.lowercase()

        fun extractSection(header: String, nextHeader: String?): String {
            val lowerContext = context.lowercase()
            val lowerHeader = header.lowercase()
            var startIdx = lowerContext.indexOf(lowerHeader)
            var headerLen = header.length

            if (startIdx == -1) {
                // Secondary keyword fallback
                val keyword = when {
                    lowerHeader.contains("routine") -> "routine"
                    lowerHeader.contains("attendance") -> "attendance"
                    lowerHeader.contains("syllabus") -> "syllabus"
                    lowerHeader.contains("notice") -> "notice"
                    else -> lowerHeader
                }
                startIdx = lowerContext.indexOf(keyword)
                headerLen = keyword.length
            }

            if (startIdx == -1) return "No records found under $header."
            val contentStart = startIdx + headerLen
            val endIdx = if (nextHeader != null) lowerContext.indexOf(nextHeader.lowercase(), contentStart) else -1
            val section = if (endIdx != -1) context.substring(contentStart, endIdx) else context.substring(contentStart)
            return section.trim()
        }

        return when {
            q.contains("routine") || q.contains("class") || q.contains("আজ") || q.contains("কাল") || q.contains("আজকে") || q.contains("today") || q.contains("tomorrow") || q.contains("schedule") -> {
                val routineData = extractSection("ROUTINE (Classes):", "ATTENDANCE SUMMARY:")
                "Classes & Routine from your saved records:\n\n$routineData"
            }
            q.contains("attendance") || q.contains("হাজিরা") || q.contains("percentage") || q.contains("miss") || q.contains("bunk") -> {
                val attData = extractSection("ATTENDANCE SUMMARY:", "SYLLABUS STATUS:")
                "Attendance summary from your saved records:\n\n$attData"
            }
            q.contains("syllabus") || q.contains("chapter") || q.contains("বাকি") || q.contains("pending") || q.contains("topic") -> {
                val syllabusData = extractSection("SYLLABUS STATUS:", "NOTICES & DEADLINES:")
                "Syllabus status from your saved records:\n\n$syllabusData"
            }
            q.contains("notice") || q.contains("exam") || q.contains("নোটিশ") || q.contains("deadline") || q.contains("holiday") -> {
                val noticeData = extractSection("NOTICES & DEADLINES:", null)
                "Notices & Deadlines from your saved records:\n\n$noticeData"
            }
            else -> {
                "I could not find information regarding '$question' in your stored records.\n" +
                "You can ask me about your routine, attendance percentage, syllabus progress, or notices."
            }
        }
    }

    // Helper: Local heuristic routine parser
    private fun parseRoutineHeuristic(text: String): List<ExtractedRoutineItem> {
        val items = mutableListOf<ExtractedRoutineItem>()
        val lines = text.lines()
        val days = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")

        var currentDay = 1
        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) continue

            for (i in days.indices) {
                if (trimmed.contains(days[i], ignoreCase = true)) {
                    currentDay = i + 1
                    break
                }
            }

            val timePattern = Pattern.compile("(\\d{1,2}[:.]\\d{2})\\s*(?:-|to)?\\s*(\\d{1,2}[:.]\\d{2})?", Pattern.CASE_INSENSITIVE)
            val matcher = timePattern.matcher(trimmed)
            if (matcher.find()) {
                val start = matcher.group(1)?.replace(".", ":") ?: "09:00"
                val end = matcher.group(2)?.replace(".", ":") ?: "10:00"
                val remaining = trimmed.replace(matcher.group(0) ?: "", "").trim()
                val parts = remaining.split(",", "-", "|")
                val subj = parts.getOrNull(0)?.trim() ?: "Class"
                val room = if (parts.size > 1) parts[1].trim() else ""

                items.add(
                    ExtractedRoutineItem(
                        dayOfWeek = currentDay,
                        dayName = days[currentDay - 1],
                        startTime = formatTime(start),
                        endTime = formatTime(end),
                        subject = if (subj.isNotBlank()) subj else "General Lecture",
                        room = room,
                        needsReview = subj.isBlank(),
                        reviewReason = if (subj.isBlank()) "Subject name unclear" else ""
                    )
                )
            }
        }

        if (items.isEmpty()) {
            // Provide template item for user review
            items.add(
                ExtractedRoutineItem(
                    dayOfWeek = 1,
                    dayName = "Monday",
                    startTime = "09:00",
                    endTime = "10:00",
                    subject = "Sample Subject (Tap to edit)",
                    room = "Room 101",
                    needsReview = true,
                    reviewReason = "Please verify or edit class details"
                )
            )
        }
        return items
    }

    private fun parseSyllabusHeuristic(text: String): List<ExtractedSyllabusTopic> {
        val topics = mutableListOf<ExtractedSyllabusTopic>()
        val lines = text.lines()
        var currentSubject = "General"
        var currentUnit = "Unit 1"
        var currentChapter = "Chapter 1"

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) continue

            if (trimmed.startsWith("Subject:", ignoreCase = true) || trimmed.startsWith("Course:", ignoreCase = true)) {
                currentSubject = trimmed.substringAfter(":").trim()
            } else if (trimmed.startsWith("Unit", ignoreCase = true) || trimmed.startsWith("Module", ignoreCase = true)) {
                currentUnit = trimmed
            } else if (trimmed.startsWith("Chapter", ignoreCase = true)) {
                currentChapter = trimmed
            } else if (trimmed.length > 3) {
                topics.add(
                    ExtractedSyllabusTopic(
                        subject = currentSubject,
                        unit = currentUnit,
                        chapter = currentChapter,
                        topic = trimmed.removePrefix("-").removePrefix("•").trim(),
                        needsReview = false
                    )
                )
            }
        }

        if (topics.isEmpty()) {
            topics.add(
                ExtractedSyllabusTopic(
                    subject = "Subject",
                    unit = "Unit 1: Fundamentals",
                    chapter = "Chapter 1: Overview",
                    topic = "Introduction & Core Concepts",
                    needsReview = true
                )
            )
        }
        return topics
    }

    private fun parseNoticeHeuristic(text: String): ExtractedNoticeData {
        val isHol = text.contains("holiday", ignoreCase = true) || text.contains("closed", ignoreCase = true) || text.contains("বন্ধ", ignoreCase = true)
        val hasChange = text.contains("rescheduled", ignoreCase = true) || text.contains("cancelled", ignoreCase = true) || text.contains("postponed", ignoreCase = true) || text.contains("room change", ignoreCase = true)

        return ExtractedNoticeData(
            title = text.lines().firstOrNull { it.isNotBlank() }?.take(60) ?: "College Notice",
            issueDate = "Today",
            sourceFacts = text.take(300),
            simpleExplanation = if (isHol) "The institution has announced a holiday. Classes will remain suspended." else "Important academic announcement from department.",
            importantDates = listOf("See notice details"),
            deadlines = emptyList(),
            isHoliday = isHol,
            holidayDetails = if (isHol) "Official Holiday announced" else "",
            routineChangeDetected = hasChange,
            routineChangeSummary = if (hasChange) "Routine change detected in notice: classes rescheduled or cancelled." else "",
            proposedChanges = emptyList()
        )
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

    private fun getDayName(day: Int): String {
        return when (day) {
            1 -> "Monday"
            2 -> "Tuesday"
            3 -> "Wednesday"
            4 -> "Thursday"
            5 -> "Friday"
            6 -> "Saturday"
            7 -> "Sunday"
            else -> "Monday"
        }
    }
}
