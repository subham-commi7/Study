package com.example.data.ai

import com.example.data.model.CourseSyllabusPackage
import com.example.data.model.ExtractedSubjectSyllabus
import com.example.data.model.ExtractedSyllabusTopic
import com.example.data.model.ExtractedTopicItem
import com.example.data.model.ExtractedUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import java.util.regex.Pattern

object CourseSyllabusExtractor {

    private val SUBJECT_CODE_REGEX = Pattern.compile(
        "\\b([A-Z]{2,5}\\s*[-_]?\\s*\\d{2,4}[A-Z]?)\\b",
        Pattern.CASE_INSENSITIVE
    )

    private val SEMESTER_REGEX = Pattern.compile(
        "\\b(Semester|Sem|Year)\\s*[-_:]?\\s*([0-9IVXLCDM]+)\\b",
        Pattern.CASE_INSENSITIVE
    )

    private val COURSE_REGEX = Pattern.compile(
        "\\b(B\\.Pharm|M\\.Pharm|Pharm\\.D|B\\.Tech|M\\.Tech|B\\.Sc|M\\.Sc|BCA|MCA|BBA|MBA|B\\.A|M\\.A|Bachelor\\s+of\\s+[A-Za-z\\s]+|Master\\s+of\\s+[A-Za-z\\s]+)\\b",
        Pattern.CASE_INSENSITIVE
    )

    private val UNIT_HEADER_REGEX = Pattern.compile(
        "^(Unit|Module|Part|Block)\\s*([0-9IVXLCDM]+)[:\\-\\s]*(.*)$",
        Pattern.CASE_INSENSITIVE
    )

    private val NON_TRACKABLE_HEADER_KEYWORDS = listOf(
        "recommended books", "reference books", "suggested readings", "text books",
        "textbooks", "references", "reading list", "course outcomes", "learning objectives",
        "course objectives", "teaching scheme", "examination scheme", "scheme of examination",
        "evaluation scheme", "credits", "lecture hours", "question paper pattern",
        "instructions for paper setters", "general regulations", "faculty notes"
    )

    /**
     * Extracts complete course syllabus with hierarchy:
     * COURSE -> SEMESTER -> SUBJECT -> UNIT/MODULE -> CHAPTER/TOPIC -> SUBTOPIC
     */
    suspend fun extractCompleteSyllabus(
        textSample: String,
        pages: List<Pair<Int, String>> = emptyList(),
        sourceFileName: String = ""
    ): CourseSyllabusPackage = withContext(Dispatchers.Default) {
        // 1. Attempt Gemini Structured Extraction if API key configured
        if (GeminiHelper.isApiKeyConfigured() && textSample.length > 50) {
            try {
                val prompt = buildSyllabusExtractionPrompt(textSample)
                val aiResult = GeminiHelper.callApiDirect(prompt, responseJson = true)
                val aiResponse = aiResult.getOrNull().orEmpty()
                if (aiResponse.isNotBlank()) {
                    val parsed = parseGeminiSyllabusJson(aiResponse, sourceFileName)
                    if (parsed != null && parsed.subjects.isNotEmpty()) {
                        return@withContext parsed
                    }
                }
            } catch (_: Exception) {
                // Fallback to deterministic parser
            }
        }

        // 2. Deterministic Structural Parser
        return@withContext parseSyllabusDeterministic(textSample, pages, sourceFileName)
    }

    private fun buildSyllabusExtractionPrompt(textSample: String): String {
        val snippet = if (textSample.length > 12000) textSample.take(12000) else textSample
        return """
            You are a university curriculum structuring engine. Analyze this course/semester syllabus document and produce a strict JSON output matching this schema:
            {
              "course": "Detected Degree/Course (e.g. B.Pharm, B.Tech)",
              "semester": "Detected Semester (e.g. Semester 1, Sem II)",
              "subjects": [
                {
                  "subjectName": "Full Subject Title",
                  "subjectCode": "Official Subject Code e.g. BP101T",
                  "units": [
                    {
                      "unitName": "Unit I or Module 1",
                      "topics": [
                        {
                          "chapterName": "Chapter or Broad Heading",
                          "topicName": "Core Topic Name",
                          "subtopics": ["Subtopic 1", "Subtopic 2"],
                          "sourcePage": 1,
                          "isTrackable": true,
                          "isReferenceOnly": false
                        }
                      ]
                    }
                  ],
                  "nonTrackableReferences": [
                    "Book references, readings, learning objectives (do NOT make these trackable chapters)"
                  ]
                }
              ],
              "unresolvedItems": []
            }

            STRICT EXTRACTION RULES:
            1. If the syllabus contains multiple subjects (e.g. BP101T, BP102T, BP103T), separate them into distinct Subject items. Never merge topics across different subjects.
            2. Distinguish trackable topics (units, chapters, topics, subtopics) from non-trackable content (book references, suggested readings, course outcomes, objectives, exam schemes). Put reference texts into "nonTrackableReferences" or set isTrackable: false.
            3. Do not invent fake subjects. If a section's subject cannot be determined, put it in "unresolvedItems".
            4. Output ONLY the JSON block.

            DOCUMENT TEXT:
            $snippet
        """.trimIndent()
    }

    private fun parseGeminiSyllabusJson(jsonStr: String, sourceFileName: String): CourseSyllabusPackage? {
        val clean = jsonStr.substringAfter("```json")
            .substringAfter("```")
            .substringBeforeLast("```")
            .trim()
        val jsonStart = clean.indexOf('{')
        val jsonEnd = clean.lastIndexOf('}')
        if (jsonStart < 0 || jsonEnd <= jsonStart) return null

        val root = JSONObject(clean.substring(jsonStart, jsonEnd + 1))
        val course = root.optString("course", "Academic Course").trim()
        val semester = root.optString("semester", "Semester 1").trim()

        val subjectsList = mutableListOf<ExtractedSubjectSyllabus>()
        val subjectsArray = root.optJSONArray("subjects") ?: JSONArray()
        for (i in 0 until subjectsArray.length()) {
            val subObj = subjectsArray.optJSONObject(i) ?: continue
            val subName = subObj.optString("subjectName", "").trim()
            if (subName.isBlank()) continue
            val subCode = subObj.optString("subjectCode", "").trim()

            val unitsList = mutableListOf<ExtractedUnit>()
            val unitsArr = subObj.optJSONArray("units") ?: JSONArray()
            for (j in 0 until unitsArr.length()) {
                val unitObj = unitsArr.optJSONObject(j) ?: continue
                val unitName = unitObj.optString("unitName", "Unit ${j + 1}").trim()

                val topicsList = mutableListOf<ExtractedTopicItem>()
                val topicsArr = unitObj.optJSONArray("topics") ?: JSONArray()
                for (k in 0 until topicsArr.length()) {
                    val topObj = topicsArr.optJSONObject(k) ?: continue
                    val tName = topObj.optString("topicName", "").trim()
                    if (tName.isBlank()) continue

                    val subtopicsList = mutableListOf<String>()
                    val subArr = topObj.optJSONArray("subtopics")
                    if (subArr != null) {
                        for (s in 0 until subArr.length()) {
                            val st = subArr.optString(s, "").trim()
                            if (st.isNotBlank()) subtopicsList.add(st)
                        }
                    }

                    topicsList.add(
                        ExtractedTopicItem(
                            chapterName = topObj.optString("chapterName", "").trim(),
                            topicName = tName,
                            subtopics = subtopicsList,
                            sourcePage = topObj.optInt("sourcePage", 1),
                            isTrackable = topObj.optBoolean("isTrackable", true),
                            isReferenceOnly = topObj.optBoolean("isReferenceOnly", false),
                            isUnresolved = topObj.optBoolean("isUnresolved", false)
                        )
                    )
                }

                if (topicsList.isNotEmpty()) {
                    unitsList.add(ExtractedUnit(unitName = unitName, topics = topicsList))
                }
            }

            val referencesList = mutableListOf<String>()
            val refsArr = subObj.optJSONArray("nonTrackableReferences")
            if (refsArr != null) {
                for (r in 0 until refsArr.length()) {
                    val ref = refsArr.optString(r, "").trim()
                    if (ref.isNotBlank()) referencesList.add(ref)
                }
            }

            subjectsList.add(
                ExtractedSubjectSyllabus(
                    subjectName = subName,
                    subjectCode = subCode,
                    units = unitsList,
                    nonTrackableReferences = referencesList
                )
            )
        }

        return CourseSyllabusPackage(
            course = course,
            semester = semester,
            subjects = subjectsList,
            sourceFileName = sourceFileName
        )
    }

    /**
     * Deterministic, multi-pass syllabus parser that runs locally without external dependencies.
     */
    fun parseSyllabusDeterministic(
        text: String,
        pages: List<Pair<Int, String>> = emptyList(),
        sourceFileName: String = ""
    ): CourseSyllabusPackage {
        val lines = text.lines()

        // Pass 1: Identify Course & Semester from header
        var detectedCourse = ""
        var detectedSemester = ""
        for (i in 0 until minOf(30, lines.size)) {
            val l = lines[i].trim()
            if (detectedCourse.isEmpty()) {
                val cMatcher = COURSE_REGEX.matcher(l)
                if (cMatcher.find()) detectedCourse = cMatcher.group(0) ?: ""
            }
            if (detectedSemester.isEmpty()) {
                val sMatcher = SEMESTER_REGEX.matcher(l)
                if (sMatcher.find()) detectedSemester = sMatcher.group(0) ?: ""
            }
        }
        if (detectedCourse.isEmpty()) detectedCourse = "Academic Course"
        if (detectedSemester.isEmpty()) detectedSemester = "Semester 1"

        // Pass 2: Partition text into Subject sections
        val subjectBlocks = partitionIntoSubjectBlocks(lines)

        val subjects = mutableListOf<ExtractedSubjectSyllabus>()
        val unresolved = mutableListOf<ExtractedTopicItem>()

        for (block in subjectBlocks) {
            val parsedSubject = parseSubjectBlock(block)
            if (parsedSubject.subjectName.equals("UNRESOLVED", ignoreCase = true) || parsedSubject.subjectName.isBlank()) {
                for (u in parsedSubject.units) {
                    unresolved.addAll(u.topics.map { it.copy(isUnresolved = true) })
                }
            } else {
                subjects.add(parsedSubject)
            }
        }

        // If no subjects partitioned, create default subject with detected units
        if (subjects.isEmpty() && unresolved.isEmpty()) {
            val defaultSubject = parseSubjectBlock(
                SubjectRawBlock(
                    subjectName = detectedCourse,
                    subjectCode = "",
                    lines = lines
                )
            )
            subjects.add(defaultSubject)
        }

        return CourseSyllabusPackage(
            course = detectedCourse,
            semester = detectedSemester,
            subjects = subjects,
            unresolvedItems = unresolved,
            sourceFileName = sourceFileName
        )
    }

    private data class SubjectRawBlock(
        val subjectName: String,
        val subjectCode: String,
        val lines: List<String>
    )

    private fun partitionIntoSubjectBlocks(lines: List<String>): List<SubjectRawBlock> {
        val blocks = mutableListOf<SubjectRawBlock>()
        var curName = ""
        var curCode = ""
        var curLines = mutableListOf<String>()

        for (i in lines.indices) {
            val line = lines[i].trim()
            if (line.isEmpty()) continue

            // Detect if this line starts a new Subject:
            // e.g. "BP101T. HUMAN ANATOMY AND PHYSIOLOGY-I", "CS101 - Introduction to Programming", "Subject: Mathematics"
            val subjectMatch = checkSubjectHeader(line, if (i + 1 < lines.size) lines[i + 1].trim() else "")
            if (subjectMatch != null) {
                if (curLines.isNotEmpty()) {
                    if (curName.isNotBlank() || curLines.any { UNIT_HEADER_REGEX.matcher(it).matches() }) {
                        blocks.add(SubjectRawBlock(subjectName = curName, subjectCode = curCode, lines = curLines))
                    }
                    curLines = mutableListOf()
                }
                curName = subjectMatch.first
                curCode = subjectMatch.second
            } else {
                curLines.add(line)
            }
        }

        if (curLines.isNotEmpty() && (curName.isNotBlank() || curLines.any { UNIT_HEADER_REGEX.matcher(it).matches() })) {
            blocks.add(SubjectRawBlock(subjectName = curName, subjectCode = curCode, lines = curLines))
        }

        return blocks
    }

    private fun checkSubjectHeader(line: String, nextLine: String): Pair<String, String>? {
        // Pattern 1: Starts with explicit "Subject:" or "Course:"
        if (line.startsWith("Subject:", ignoreCase = true) || line.startsWith("Course Title:", ignoreCase = true)) {
            val clean = line.substringAfter(":").trim()
            val codeMatcher = SUBJECT_CODE_REGEX.matcher(clean)
            val code = if (codeMatcher.find()) codeMatcher.group(0) ?: "" else ""
            val title = clean.replace(code, "").trim('-', ' ', ':')
            return Pair(title.ifBlank { clean }, code)
        }

        // Pattern 2: Starts with Subject Code e.g. "BP101T. HUMAN ANATOMY AND PHYSIOLOGY - I (Theory)"
        val codeMatcher = SUBJECT_CODE_REGEX.matcher(line)
        if (codeMatcher.find() && codeMatcher.start() <= 2) {
            val code = codeMatcher.group(0) ?: ""
            val rest = line.substring(codeMatcher.end()).trim('.', '-', ':', ' ', '–')
            val title = if (rest.length >= 3) {
                rest.substringBefore("(Theory)").substringBefore("(Practical)").trim()
            } else if (nextLine.isNotBlank() && !nextLine.startsWith("Unit", true)) {
                nextLine
            } else {
                code
            }
            return Pair(title, code)
        }

        // Pattern 3: Explicit "PAPER I - ...", "MODULE 1 - ..."
        if (line.matches(Regex("^(Paper|Course)\\s+[0-9IVXLCDM]+[:\\-\\s]+[A-Za-z\\s]+.*", RegexOption.IGNORE_CASE))) {
            val title = line.substringAfter("-").substringAfter(":").trim()
            return Pair(title.ifBlank { line }, "")
        }

        return null
    }

    private fun parseSubjectBlock(block: SubjectRawBlock): ExtractedSubjectSyllabus {
        val units = mutableListOf<ExtractedUnit>()
        val references = mutableListOf<String>()

        var curUnitName = "Unit I"
        var curTopics = mutableListOf<ExtractedTopicItem>()
        var curChapter = ""
        var isInsideReferenceSection = false

        for (line in block.lines) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) continue

            // 1. Check if entering a Non-Trackable Section (References, Learning Objectives, etc.)
            val lower = trimmed.lowercase(Locale.ENGLISH)
            if (NON_TRACKABLE_HEADER_KEYWORDS.any { lower.contains(it) && trimmed.length < 50 }) {
                isInsideReferenceSection = true
                continue
            }

            // 2. Check if encountering a new Unit/Module
            val unitMatcher = UNIT_HEADER_REGEX.matcher(trimmed)
            if (unitMatcher.matches()) {
                isInsideReferenceSection = false
                if (curTopics.isNotEmpty()) {
                    units.add(ExtractedUnit(unitName = curUnitName, topics = curTopics))
                    curTopics = mutableListOf()
                }
                curUnitName = trimmed.substringBefore("-").substringBefore("–").trim()
                val unitTitle = trimmed.substringAfter("-", "").substringAfter("–", "").trim()
                if (unitTitle.isNotBlank()) curChapter = unitTitle
                continue
            }

            // 3. Handle lines inside Reference/Non-Trackable section
            if (isInsideReferenceSection) {
                references.add(trimmed)
                continue
            }

            // 4. Chapter Header detection
            if (trimmed.startsWith("Chapter", ignoreCase = true) ||
                (trimmed.endsWith(":") && trimmed.length < 40 && !trimmed.contains(','))
            ) {
                curChapter = trimmed.trimEnd(':')
                continue
            }

            // 5. Clean Topic Extraction
            val cleanedTopic = cleanTopicLine(trimmed)
            if (cleanedTopic != null && cleanedTopic.topicName.length >= 3) {
                curTopics.add(
                    cleanedTopic.copy(
                        chapterName = curChapter.ifBlank { curUnitName }
                    )
                )
            }
        }

        if (curTopics.isNotEmpty()) {
            units.add(ExtractedUnit(unitName = curUnitName, topics = curTopics))
        }

        return ExtractedSubjectSyllabus(
            subjectName = block.subjectName.ifBlank { "General Subject" },
            subjectCode = block.subjectCode,
            units = units,
            nonTrackableReferences = references
        )
    }

    private fun cleanTopicLine(line: String): ExtractedTopicItem? {
        val trimmed = line.trim()

        // Exclude obvious page headers/footers and numbering
        if (trimmed.matches(Regex("^(Page|Page\\s*\\d+|\\d+\\s*/\\s*\\d+|\\d+)$", RegexOption.IGNORE_CASE))) {
            return null
        }
        if (trimmed.matches(Regex("^(Hours|Marks|Credits|Lecture|Tutorial|Practical)\\s*[:=\\d\\s]+$", RegexOption.IGNORE_CASE))) {
            return null
        }

        // Split main topic from subtopic listing if present
        // e.g. "Tissue level of organization: Classification of tissues, structure, location of epithelial tissue"
        val hasColon = trimmed.contains(':')
        val mainTopic: String
        val subtopics = mutableListOf<String>()

        if (hasColon && trimmed.indexOf(':') in 4..40) {
            mainTopic = trimmed.substringBefore(':').trim().removePrefix("•").removePrefix("-").trim()
            val subStr = trimmed.substringAfter(':').trim()
            val parts = subStr.split(',', ';')
            for (p in parts) {
                val c = p.trim()
                if (c.isNotBlank()) subtopics.add(c)
            }
        } else {
            mainTopic = trimmed.removePrefix("•").removePrefix("-").removePrefix("*").trim()
            if (mainTopic.contains(';') || (mainTopic.count { it == ',' } >= 2)) {
                val parts = mainTopic.split(';', ',')
                if (parts.isNotEmpty()) {
                    for (i in 1 until parts.size) {
                        val c = parts[i].trim()
                        if (c.isNotBlank()) subtopics.add(c)
                    }
                }
            }
        }

        // Check if this line looks like an orphan bibliography entry (e.g. "1. Tortora, G.J. ...")
        if (trimmed.matches(Regex("^\\d+\\.\\s*[A-Z][a-z]+,\\s+[A-Z].*"))) {
            return ExtractedTopicItem(
                topicName = mainTopic,
                isTrackable = false,
                isReferenceOnly = true
            )
        }

        return ExtractedTopicItem(
            topicName = mainTopic,
            subtopics = subtopics,
            isTrackable = true,
            isReferenceOnly = false
        )
    }

    /**
     * Converts a CourseSyllabusPackage into flat ExtractedSyllabusTopic list
     * for seamless persistence into Room SyllabusTopicEntity.
     */
    fun flattenToExtractedTopics(pkg: CourseSyllabusPackage): List<ExtractedSyllabusTopic> {
        val result = mutableListOf<ExtractedSyllabusTopic>()
        for (sub in pkg.subjects) {
            for (unit in sub.units) {
                for (topic in unit.topics) {
                    if (topic.isTrackable && !topic.isReferenceOnly) {
                        result.add(
                            ExtractedSyllabusTopic(
                                subject = sub.subjectName,
                                subjectCode = sub.subjectCode,
                                course = pkg.course,
                                semester = pkg.semester,
                                unit = unit.unitName,
                                chapter = topic.chapterName,
                                topic = topic.topicName,
                                subtopic = topic.subtopics.joinToString(", "),
                                sourceInfo = pkg.sourceFileName,
                                sourcePage = topic.sourcePage,
                                isTrackable = true,
                                isReferenceOnly = false,
                                isUnresolved = topic.isUnresolved
                            )
                        )
                    }
                }
            }
        }
        return result
    }
}
