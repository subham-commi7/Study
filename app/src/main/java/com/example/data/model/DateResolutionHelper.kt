package com.example.data.model

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.regex.Pattern

data class ResolvedDateInfo(
    val dateString: String, // "YYYY-MM-DD"
    val dayOfWeek: Int, // 1 = Monday, 2 = Tuesday, ..., 7 = Sunday
    val formattedDisplay: String,
    val matchedPhrase: String,
    val isHoliday: Boolean = true
)

object DateResolutionHelper {

    private val ISO_FORMAT = SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH)
    private val DISPLAY_FORMAT = SimpleDateFormat("EEEE, d MMMM yyyy", Locale.ENGLISH)

    private val WEEKDAY_PATTERNS = mapOf(
        Pattern.compile("\\b(next|upcoming|this)?\\s*(monday|সোম|সোমবার)\\b", Pattern.CASE_INSENSITIVE) to Calendar.MONDAY,
        Pattern.compile("\\b(next|upcoming|this)?\\s*(tuesday|মঙ্গল|মঙ্গলবার)\\b", Pattern.CASE_INSENSITIVE) to Calendar.TUESDAY,
        Pattern.compile("\\b(next|upcoming|this)?\\s*(wednesday|বুধ|বুধবার)\\b", Pattern.CASE_INSENSITIVE) to Calendar.WEDNESDAY,
        Pattern.compile("\\b(next|upcoming|this)?\\s*(thursday|বৃহস্পতি|বৃহস্পতিবার)\\b", Pattern.CASE_INSENSITIVE) to Calendar.THURSDAY,
        Pattern.compile("\\b(next|upcoming|this)?\\s*(friday|শুক্র|শুক্রবার)\\b", Pattern.CASE_INSENSITIVE) to Calendar.FRIDAY,
        Pattern.compile("\\b(next|upcoming|this)?\\s*(saturday|শনি|শনিবার)\\b", Pattern.CASE_INSENSITIVE) to Calendar.SATURDAY,
        Pattern.compile("\\b(next|upcoming|this)?\\s*(sunday|রবি|রবিবার)\\b", Pattern.CASE_INSENSITIVE) to Calendar.SUNDAY
    )

    private val DATE_FORMATS = listOf(
        SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH),
        SimpleDateFormat("dd-MM-yyyy", Locale.ENGLISH),
        SimpleDateFormat("dd/MM/yyyy", Locale.ENGLISH),
        SimpleDateFormat("d MMMM yyyy", Locale.ENGLISH),
        SimpleDateFormat("MMMM d, yyyy", Locale.ENGLISH),
        SimpleDateFormat("d MMM yyyy", Locale.ENGLISH),
        SimpleDateFormat("d MMMM", Locale.ENGLISH),
        SimpleDateFormat("d MMM", Locale.ENGLISH)
    )

    /**
     * Resolves natural language or explicit date expressions into an exact calendar date.
     * E.g. "Next Monday is a holiday" -> resolves next Monday's exact date string (e.g. 2026-09-28)
     */
    fun resolveDateExpression(
        text: String,
        referenceDateMillis: Long = System.currentTimeMillis()
    ): ResolvedDateInfo? {
        val lower = text.lowercase(Locale.ENGLISH)

        // 1. Check relative days: Today, Tomorrow
        val baseCal = Calendar.getInstance().apply {
            timeInMillis = referenceDateMillis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        if (lower.contains("tomorrow") || lower.contains("কাল") || lower.contains("আগামীকাল")) {
            val cal = (baseCal.clone() as Calendar).apply {
                add(Calendar.DAY_OF_YEAR, 1)
            }
            val dateStr = ISO_FORMAT.format(cal.time)
            val dayOfWeek = convertCalendarDayToAppDay(cal.get(Calendar.DAY_OF_WEEK))
            return ResolvedDateInfo(
                dateString = dateStr,
                dayOfWeek = dayOfWeek,
                formattedDisplay = DISPLAY_FORMAT.format(cal.time),
                matchedPhrase = "Tomorrow"
            )
        }

        if (lower.contains("today") || lower.contains("আজ") || lower.contains("আজকে")) {
            val dateStr = ISO_FORMAT.format(baseCal.time)
            val dayOfWeek = convertCalendarDayToAppDay(baseCal.get(Calendar.DAY_OF_WEEK))
            return ResolvedDateInfo(
                dateString = dateStr,
                dayOfWeek = dayOfWeek,
                formattedDisplay = DISPLAY_FORMAT.format(baseCal.time),
                matchedPhrase = "Today"
            )
        }

        // 2. Check explicit date patterns like 28/09/2026 or 28 September
        val explicitDatePattern = Pattern.compile("(\\d{1,2}[/-]\\d{1,2}[/-]\\d{2,4}|\\d{1,2}\\s+(?:January|February|March|April|May|June|July|August|September|October|November|December|Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)(?:\\s+\\d{4})?)", Pattern.CASE_INSENSITIVE)
        val explicitMatcher = explicitDatePattern.matcher(text)
        if (explicitMatcher.find()) {
            val dateSub = explicitMatcher.group(1) ?: ""
            if (dateSub.isNotBlank()) {
                for (fmt in DATE_FORMATS) {
                    try {
                        val parsed = fmt.parse(dateSub)
                        if (parsed != null) {
                            val cal = Calendar.getInstance().apply {
                                time = parsed
                                // If year was not specified, default to current year
                                if (get(Calendar.YEAR) < 2000) {
                                    set(Calendar.YEAR, baseCal.get(Calendar.YEAR))
                                }
                            }
                            val dateStr = ISO_FORMAT.format(cal.time)
                            val dayOfWeek = convertCalendarDayToAppDay(cal.get(Calendar.DAY_OF_WEEK))
                            return ResolvedDateInfo(
                                dateString = dateStr,
                                dayOfWeek = dayOfWeek,
                                formattedDisplay = DISPLAY_FORMAT.format(cal.time),
                                matchedPhrase = dateSub
                            )
                        }
                    } catch (_: Exception) {}
                }
            }
        }

        // 3. Check weekday expressions: "Next Monday", "This Friday", etc.
        for ((pattern, targetDay) in WEEKDAY_PATTERNS) {
            val matcher = pattern.matcher(text)
            if (matcher.find()) {
                val matchedText = matcher.group(0) ?: ""
                val cal = (baseCal.clone() as Calendar)
                val currentDay = cal.get(Calendar.DAY_OF_WEEK)

                var daysToAdd = (targetDay - currentDay + 7) % 7
                if (daysToAdd == 0) {
                    // If user said "next Monday" and today is Monday, move to next week (+7 days)
                    daysToAdd = 7
                }

                cal.add(Calendar.DAY_OF_YEAR, daysToAdd)
                val dateStr = ISO_FORMAT.format(cal.time)
                val dayOfWeek = convertCalendarDayToAppDay(targetDay)

                return ResolvedDateInfo(
                    dateString = dateStr,
                    dayOfWeek = dayOfWeek,
                    formattedDisplay = DISPLAY_FORMAT.format(cal.time),
                    matchedPhrase = matchedText
                )
            }
        }

        return null
    }

    /**
     * Converts java.util.Calendar day of week (Sunday = 1, Monday = 2, ...)
     * to StudyMate day of week (Monday = 1, Tuesday = 2, ..., Sunday = 7).
     */
    fun convertCalendarDayToAppDay(calendarDay: Int): Int {
        return when (calendarDay) {
            Calendar.MONDAY -> 1
            Calendar.TUESDAY -> 2
            Calendar.WEDNESDAY -> 3
            Calendar.THURSDAY -> 4
            Calendar.FRIDAY -> 5
            Calendar.SATURDAY -> 6
            Calendar.SUNDAY -> 7
            else -> 1
        }
    }

    /**
     * Validates that the weekday and date string agree with the calendar.
     */
    fun verifyWeekdayAndDate(dateString: String, dayOfWeek: Int): Boolean {
        return try {
            val date = ISO_FORMAT.parse(dateString) ?: return false
            val cal = Calendar.getInstance().apply { time = date }
            val computedDay = convertCalendarDayToAppDay(cal.get(Calendar.DAY_OF_WEEK))
            computedDay == dayOfWeek
        } catch (_: Exception) {
            false
        }
    }

    fun getTodayDateString(): String {
        return ISO_FORMAT.format(Date())
    }

    fun getDayName(dayOfWeek: Int): String {
        return when (dayOfWeek) {
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
