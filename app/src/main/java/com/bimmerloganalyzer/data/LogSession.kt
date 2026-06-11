package com.bimmerloganalyzer.data

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

data class LogSession(
    val fileName: String,
    val points: List<OBDDataPoint>,
) {
    /** Start datetime parsed from filename — null if filename doesn't match pattern */
    val startTime: LocalDateTime? = LogFileName.parseStartTime(fileName)

    val maxSpeedKmh: Float get() = points.maxOfOrNull { it.speedKmh } ?: 0f
    val maxTorqueNm: Float get() = points.maxOfOrNull { it.torqueNm } ?: 0f
    val maxPowerPs: Float get() = points.maxOfOrNull { it.powerPs } ?: 0f
    val maxRpm: Float get() = points.maxOfOrNull { it.rpm } ?: 0f
    val durationSec: Float get() = points.lastOrNull()?.time ?: 0f

    /** Display label: "Tue 9 Jun 2026  21:57:04" or filename fallback */
    val displayLabel: String get() = LogFileName.displayLabel(fileName)

    /** Short subtitle: "09/06/2026 21:57:04" */
    val shortLabel: String get() = LogFileName.shortLabel(fileName)

    /** Downsample for chart rendering — keeps at most [maxPoints] evenly spaced points */
    fun sampledPoints(maxPoints: Int = 1000): List<OBDDataPoint> {
        if (points.size <= maxPoints) return points
        val step = points.size / maxPoints
        return points.filterIndexed { i, _ -> i % step == 0 }
    }

    /** Points during full-throttle pull (throttle >= 95%) for dyno-style curve */
    fun fullThrottlePoints(): List<OBDDataPoint> =
        points.filter { it.throttlePct >= 95f && it.rpm > 500f }
            .sortedBy { it.rpm }
}
