package com.bimmerloganalyzer.data

data class OBDDataPoint(
    val time: Float,
    val gear: Float,
    val speedKmh: Float,
    val rpm: Float,
    val torqueNm: Float,
    val clutchTorqueNm: Float,
    val boostBar: Float,
    val throttlePct: Float,
    val accelerationMs2: Float,
    val exhaustPressureBar: Float,
    val turboRpm: Float,
    val railPressureBar: Float,
    val ambientTempC: Float,
    val engineTempC: Float,
    val transmissionTempC: Float,
) {
    /** Metric horsepower (PS): Torque(Nm) × RPM / 9549.3 */
    val powerPs: Float get() = if (rpm > 0f && torqueNm > 0f) torqueNm * rpm / 9549.3f else 0f

    /** Imperial brake horsepower: Torque(Nm) × RPM / 7120.83 */
    val powerBhp: Float get() = if (rpm > 0f && torqueNm > 0f) torqueNm * rpm / 7120.83f else 0f
}
