package com.bimmerloganalyzer.data

data class LogSession(
    val fileName: String,
    val points: List<OBDDataPoint>,
) {
    val maxSpeedKmh: Float get() = points.maxOfOrNull { it.speedKmh } ?: 0f
    val maxTorqueNm: Float get() = points.maxOfOrNull { it.torqueNm } ?: 0f
    val maxPowerPs: Float get() = points.maxOfOrNull { it.powerPs } ?: 0f
    val maxRpm: Float get() = points.maxOfOrNull { it.rpm } ?: 0f
    val durationSec: Float get() = points.lastOrNull()?.time ?: 0f

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
