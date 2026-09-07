package com.bimmerdyno.data

/**
 * Every value [OBDDataPoint] can hold, together with how it is found in a CSV.
 *
 * [autoKeywords] drives auto-detection: a header matches when it *contains* one
 * of the keywords (case-insensitive). The first matching header in file order
 * wins, so keywords are deliberately specific ("engine torque" rather than
 * "torque") to avoid stealing a neighbouring column.
 *
 * [key] is the stable identifier used for persistence — never rename it, or
 * saved mappings from an older install stop resolving.
 */
enum class LogField(
    val key: String,
    val displayName: String,
    val unit: String,
    val autoKeywords: List<String>,
) {
    TIME("time", "เวลา (Time)", "s", listOf("time")),
    GEAR("gear", "เกียร์ (Gear)", "", listOf("gear")),
    SPEED("speed", "ความเร็ว (Speed)", "km/h", listOf("vehicle speed", "speed km")),
    RPM("rpm", "รอบเครื่อง (RPM)", "rpm", listOf("engine speed", "rpm")),
    TORQUE("torque", "แรงบิด (Torque)", "Nm", listOf("current engine torque", "engine torque")),
    CLUTCH_TORQUE("clutch_torque", "แรงบิดคลัตช์", "Nm", listOf("clutch torque")),
    BOOST("boost", "บูสต์ (Boost)", "bar", listOf("boost pressure")),
    THROTTLE("throttle", "คันเร่ง (Throttle)", "%", listOf("accelerator pedal", "throttle")),
    ACCELERATION("acceleration", "อัตราเร่ง", "m/s2", listOf("vehicle acceleration", "acceleration m")),
    EXHAUST_PRESSURE("exhaust_pressure", "แรงดันไอเสีย", "bar", listOf("exhaust pressure")),
    TURBO_RPM("turbo_rpm", "รอบเทอร์โบ", "rpm", listOf("turbocharger rpm", "turbo")),
    RAIL_PRESSURE("rail_pressure", "แรงดันราง (Rail)", "bar", listOf("rail pressure")),
    AMBIENT_TEMP("ambient_temp", "อุณหภูมิภายนอก", "C", listOf("ambient temperature", "ambient temp")),
    ENGINE_TEMP("engine_temp", "อุณหภูมิเครื่องยนต์", "C", listOf("engine temperature", "engine temp")),
    TRANS_TEMP("trans_temp", "อุณหภูมิเกียร์", "C", listOf("transmission oil", "trans")),
    ;

    companion object {
        fun byKey(key: String): LogField? = entries.firstOrNull { it.key == key }
    }
}

/**
 * User overrides for how CSV columns bind to [LogField]s.
 *
 * A field absent from [overrides] is auto-detected from [LogField.autoKeywords].
 * A field mapped to [NONE] is explicitly switched off and always reads `0`.
 * Any other value is a column header name, matched case-insensitively against
 * the file's header row.
 */
data class FieldMapping(val overrides: Map<LogField, String> = emptyMap()) {

    fun columnFor(field: LogField): String? = overrides[field]

    fun isAuto(field: LogField): Boolean = !overrides.containsKey(field)

    fun isDisabled(field: LogField): Boolean = overrides[field] == NONE

    /** Returns a copy with [field] bound to [column]; a null [column] restores auto-detect. */
    fun with(field: LogField, column: String?): FieldMapping = FieldMapping(
        if (column == null) overrides - field else overrides + (field to column)
    )

    companion object {
        /**
         * Sentinel meaning "do not read this field at all". The leading space
         * keeps it distinct from any real header, which is always trimmed.
         */
        const val NONE = " none"

        val AUTO = FieldMapping()
    }
}
