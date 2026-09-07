package com.bimmerdyno.data

import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.regex.Pattern

object LogFileName {

    // Log-2026-06-09--21-57-04.csv
    private val PATTERN = Pattern.compile(
        """Log-(\d{4})-(\d{2})-(\d{2})--(\d{2})-(\d{2})-(\d{2})\.csv""",
        Pattern.CASE_INSENSITIVE
    )

    private val DISPLAY_FORMATTER = DateTimeFormatter.ofPattern("EEE d MMM yyyy  HH:mm:ss")
    private val SHORT_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss")

    /** Parse start datetime from filename. Returns null if filename doesn't match pattern. */
    fun parseStartTime(fileName: String): LocalDateTime? {
        val m = PATTERN.matcher(fileName)
        if (!m.find()) return null
        return try {
            LocalDateTime.of(
                m.group(1).toInt(), m.group(2).toInt(), m.group(3).toInt(),
                m.group(4).toInt(), m.group(5).toInt(), m.group(6).toInt()
            )
        } catch (e: Exception) {
            null
        }
    }

    /** Human-readable label for file list: "Tue 9 Jun 2026  21:57:04" */
    fun displayLabel(fileName: String): String {
        val dt = parseStartTime(fileName) ?: return fileName
        return dt.format(DISPLAY_FORMATTER)
    }

    /** Short format for chart subtitle */
    fun shortLabel(fileName: String): String {
        val dt = parseStartTime(fileName) ?: return fileName
        return dt.format(SHORT_FORMATTER)
    }

    /** Absolute epoch millis for a given offset (seconds) from file start time */
    fun absoluteEpochMs(startTime: LocalDateTime, offsetSeconds: Float): Long {
        val startMs = startTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        return startMs + (offsetSeconds * 1000).toLong()
    }
}
