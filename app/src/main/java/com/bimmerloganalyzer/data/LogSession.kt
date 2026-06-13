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

    /**
     * Dyno-style power/torque envelope vs RPM, binned every [binRpm] and
     * **estimate-filled**. Each RPM bin keeps the **maximum torque** seen at that
     * RPM across the whole session (the achievable envelope) — this does not
     * require a full-throttle filter, so it works even when throttle isn't
     * logged as a percentage. Bins with no measured sample are linearly
     * interpolated from the nearest measured neighbours and flagged
     * `estimated = true` so the UI can render them as an approximation.
     */
    fun dynoCurve(binRpm: Float = 250f): List<DynoPoint> {
        // Use every point with a real RPM and positive torque (engine actually pulling)
        val src = points.filter { it.rpm > 500f && it.torqueNm > 0f }
        if (src.isEmpty()) return emptyList()

        // bin index → max measured torque in that bin (the envelope)
        val measured = sortedMapOf<Int, Float>()
        for (p in src) {
            val bin = (p.rpm / binRpm).toInt()
            measured[bin] = maxOf(measured[bin] ?: Float.NEGATIVE_INFINITY, p.torqueNm)
        }
        if (measured.isEmpty()) return emptyList()

        val firstBin = measured.firstKey()
        val lastBin = measured.lastKey()
        val result = ArrayList<DynoPoint>(lastBin - firstBin + 1)

        for (bin in firstBin..lastBin) {
            val rpm = bin * binRpm + binRpm / 2f
            val direct = measured[bin]
            val torque = direct ?: interpolateTorque(measured, bin)
            val ps = if (torque > 0f && rpm > 0f) torque * rpm / 9549.3f else 0f
            val bhp = if (torque > 0f && rpm > 0f) torque * rpm / 7120.83f else 0f
            result.add(DynoPoint(rpm, torque, ps, bhp, estimated = direct == null))
        }
        return result
    }

    /** Linear interpolation of torque for a gap [bin] between measured neighbours. */
    private fun interpolateTorque(measured: Map<Int, Float>, bin: Int): Float {
        val lower = measured.keys.filter { it < bin }.maxOrNull()
        val upper = measured.keys.filter { it > bin }.minOrNull()
        return when {
            lower != null && upper != null -> {
                val lo = measured.getValue(lower)
                val hi = measured.getValue(upper)
                val t = (bin - lower).toFloat() / (upper - lower)
                lo + (hi - lo) * t
            }
            lower != null -> measured.getValue(lower)
            upper != null -> measured.getValue(upper)
            else -> 0f
        }
    }
}

/** One point on the dyno envelope. [estimated] = interpolated, not measured. */
data class DynoPoint(
    val rpm: Float,
    val torqueNm: Float,
    val powerPs: Float,
    val powerBhp: Float,
    val estimated: Boolean,
)
