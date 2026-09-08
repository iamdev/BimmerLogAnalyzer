package com.bimmerdyno.data

import java.io.InputStream

/** Header row of a log file: where it sits and what the raw column names are. */
data class CsvHeader(val rowIndex: Int, val columns: List<String>)

object CsvParser {

    /** Parse result: the samples plus the header the columns were bound against. */
    data class Parsed(val points: List<OBDDataPoint>, val header: CsvHeader?)

    fun parse(stream: InputStream, mapping: FieldMapping = FieldMapping.AUTO): Parsed {
        val lines = stream.bufferedReader(Charsets.UTF_8).readLines()
        if (lines.size < 2) return Parsed(emptyList(), null)

        val header = findHeader(lines) ?: return Parsed(emptyList(), null)
        val colIndex = resolveIndices(header.columns, mapping)

        val raw = lines.drop(header.rowIndex + 1)
            .filter { it.isNotBlank() }
            .mapNotNull { line -> parseLine(line, colIndex) }

        return Parsed(compact(raw, colIndex), header)
    }

    /**
     * Read only the header row — used by Settings to offer the real column
     * names of a file without paying for a full parse.
     */
    fun readHeader(stream: InputStream): CsvHeader? =
        stream.bufferedReader(Charsets.UTF_8).useLines { seq ->
            findHeader(seq.take(MAX_HEADER_SCAN_LINES).toList())
        }

    /** First row that looks like a header (contains "Time" or "speed"). */
    private fun findHeader(lines: List<String>): CsvHeader? {
        val idx = lines.indexOfFirst { line ->
            line.contains("Time", ignoreCase = true) ||
            line.contains("speed", ignoreCase = true)
        }
        if (idx < 0) return null
        return CsvHeader(idx, splitRow(lines[idx]).map { it.trim() })
    }

    /**
     * Bind every [LogField] to a column index in [headers].
     *
     * An explicit override wins: it is matched case-insensitively against the
     * trimmed header name, and [FieldMapping.NONE] switches the field off. A
     * field with no override — or one naming a column this file does not have —
     * falls back to keyword auto-detection, so a mapping saved for one logger
     * still does something sensible on a file from another.
     */
    fun resolveIndices(headers: List<String>, mapping: FieldMapping): Map<LogField, Int> {
        val lower = headers.map { it.lowercase() }
        return LogField.entries.associateWith { field ->
            val override = mapping.columnFor(field)
            when {
                override == FieldMapping.NONE -> NOT_FOUND
                override != null -> {
                    val exact = lower.indexOf(override.trim().lowercase())
                    if (exact >= 0) exact else autoDetect(lower, field)
                }
                else -> autoDetect(lower, field)
            }
        }
    }

    private fun autoDetect(lowerHeaders: List<String>, field: LogField): Int =
        lowerHeaders.indexOfFirst { h -> field.autoKeywords.any { h.contains(it) } }

    /**
     * The logger streams **one value per row**, carrying every previously-set
     * value forward, e.g.
     * ```
     * 1,0,0   1,2,0   1,2,3   4,2,3   4,5,3   4,5,6
     * ```
     * forms only two complete samples: `1,2,3` and `4,5,6`. A complete sample
     * is the fully-accumulated row right before the `Time` column advances, so
     * we keep the **last** row of each consecutive equal-time run. When the time
     * column is unmapped or constant we cannot detect cycle boundaries, so the
     * raw rows are returned unchanged.
     */
    private fun compact(points: List<OBDDataPoint>, colIndex: Map<LogField, Int>): List<OBDDataPoint> {
        if (points.size < 2) return points
        if ((colIndex[LogField.TIME] ?: NOT_FOUND) < 0) return points
        val distinctTimes = points.mapTo(HashSet()) { it.time }.size
        if (distinctTimes < 2) return points

        val out = ArrayList<OBDDataPoint>(distinctTimes)
        for (i in points.indices) {
            val cur = points[i]
            val next = points.getOrNull(i + 1)
            if (next == null || next.time != cur.time) out.add(cur)
        }
        return out
    }

    private fun parseLine(line: String, c: Map<LogField, Int>): OBDDataPoint? {
        val cols = splitRow(line)
        fun col(field: LogField): Float {
            val idx = c[field] ?: return 0f
            if (idx < 0 || idx >= cols.size) return 0f
            return cols[idx].trim().toFloatOrNull() ?: 0f
        }
        return try {
            OBDDataPoint(
                time = col(LogField.TIME),
                gear = col(LogField.GEAR),
                speedKmh = col(LogField.SPEED),
                rpm = col(LogField.RPM),
                torqueNm = col(LogField.TORQUE),
                clutchTorqueNm = col(LogField.CLUTCH_TORQUE),
                boostBar = col(LogField.BOOST),
                throttlePct = col(LogField.THROTTLE),
                accelerationMs2 = col(LogField.ACCELERATION),
                exhaustPressureBar = col(LogField.EXHAUST_PRESSURE),
                turboRpm = col(LogField.TURBO_RPM),
                railPressureBar = col(LogField.RAIL_PRESSURE),
                ambientTempC = col(LogField.AMBIENT_TEMP),
                engineTempC = col(LogField.ENGINE_TEMP),
                transmissionTempC = col(LogField.TRANS_TEMP),
            )
        } catch (e: Exception) {
            null
        }
    }

    /** Split on commas, honouring double-quoted fields that contain commas. */
    private fun splitRow(line: String): List<String> {
        if (!line.contains('"')) return line.split(",")
        val out = mutableListOf<String>()
        val cell = StringBuilder()
        var inQuotes = false
        for (ch in line) {
            when {
                ch == '"' -> inQuotes = !inQuotes
                ch == ',' && !inQuotes -> { out.add(cell.toString()); cell.setLength(0) }
                else -> cell.append(ch)
            }
        }
        out.add(cell.toString())
        return out
    }

    private const val NOT_FOUND = -1

    /** Loggers put at most a handful of preamble rows above the header. */
    private const val MAX_HEADER_SCAN_LINES = 50
}
