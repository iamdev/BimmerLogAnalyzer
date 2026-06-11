package com.bimmerloganalyzer.data

import java.io.InputStream

object CsvParser {

    fun parse(stream: InputStream): List<OBDDataPoint> {
        val lines = stream.bufferedReader(Charsets.UTF_8).readLines()
        if (lines.size < 2) return emptyList()

        // Find header row (first row that contains "Time" or "speed")
        val headerIndex = lines.indexOfFirst { line ->
            line.contains("Time", ignoreCase = true) ||
            line.contains("speed", ignoreCase = true)
        }
        if (headerIndex < 0) return emptyList()

        val headers = lines[headerIndex].split(",").map { it.trim().lowercase() }
        val colIndex = buildColumnIndex(headers)

        return lines.drop(headerIndex + 1)
            .filter { it.isNotBlank() }
            .mapNotNull { line -> parseLine(line, colIndex) }
    }

    private data class ColIndex(
        val time: Int = -1,
        val gear: Int = -1,
        val speed: Int = -1,
        val rpm: Int = -1,
        val torque: Int = -1,
        val clutchTorque: Int = -1,
        val boost: Int = -1,
        val throttle: Int = -1,
        val accel: Int = -1,
        val exhaustPressure: Int = -1,
        val turboRpm: Int = -1,
        val railPressure: Int = -1,
        val ambientTemp: Int = -1,
        val engineTemp: Int = -1,
        val transTemp: Int = -1,
    )

    private fun buildColumnIndex(headers: List<String>): ColIndex {
        fun find(vararg keywords: String) =
            headers.indexOfFirst { h -> keywords.any { h.contains(it, ignoreCase = true) } }

        return ColIndex(
            time = find("time"),
            gear = find("gear"),
            speed = find("vehicle speed", "speed km"),
            rpm = find("engine speed", "rpm"),
            torque = find("current engine torque", "engine torque"),
            clutchTorque = find("clutch torque"),
            boost = find("boost pressure"),
            throttle = find("accelerator pedal", "throttle"),
            accel = find("vehicle acceleration", "acceleration m"),
            exhaustPressure = find("exhaust pressure"),
            turboRpm = find("turbocharger rpm", "turbo"),
            railPressure = find("rail pressure"),
            ambientTemp = find("ambient temperature", "ambient temp"),
            engineTemp = find("engine temperature", "engine temp"),
            transTemp = find("transmission oil", "trans"),
        )
    }

    private fun parseLine(line: String, c: ColIndex): OBDDataPoint? {
        val cols = line.split(",")
        fun col(idx: Int) = if (idx >= 0 && idx < cols.size) cols[idx].trim().toFloatOrNull() ?: 0f else 0f
        return try {
            OBDDataPoint(
                time = col(c.time),
                gear = col(c.gear),
                speedKmh = col(c.speed),
                rpm = col(c.rpm),
                torqueNm = col(c.torque),
                clutchTorqueNm = col(c.clutchTorque),
                boostBar = col(c.boost),
                throttlePct = col(c.throttle),
                accelerationMs2 = col(c.accel),
                exhaustPressureBar = col(c.exhaustPressure),
                turboRpm = col(c.turboRpm),
                railPressureBar = col(c.railPressure),
                ambientTempC = col(c.ambientTemp),
                engineTempC = col(c.engineTemp),
                transmissionTempC = col(c.transTemp),
            )
        } catch (e: Exception) {
            null
        }
    }
}
