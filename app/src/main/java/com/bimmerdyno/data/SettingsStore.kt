package com.bimmerdyno.data

import android.content.Context

/**
 * All persisted preferences, backed by SharedPreferences.
 *
 * Column overrides are stored one key per field (`map_<field key>`) rather than
 * as a serialised blob, so adding a [LogField] later cannot invalidate what is
 * already saved.
 */
class SettingsStore(context: Context) {

    private val prefs = context.getSharedPreferences("bimmerdyno_prefs", Context.MODE_PRIVATE)

    // ── Local folder ────────────────────────────────────────────────────────

    var localFolderUri: String?
        get() = prefs.getString(KEY_LOCAL_FOLDER, null)
        set(value) = prefs.edit().putString(KEY_LOCAL_FOLDER, value).apply()

    // ── Field mapping ───────────────────────────────────────────────────────

    fun loadMapping(): FieldMapping {
        val overrides = LogField.entries.mapNotNull { field ->
            prefs.getString(KEY_MAPPING_PREFIX + field.key, null)?.let { field to it }
        }.toMap()
        return FieldMapping(overrides)
    }

    fun saveMapping(mapping: FieldMapping) {
        val editor = prefs.edit()
        LogField.entries.forEach { field ->
            val key = KEY_MAPPING_PREFIX + field.key
            val column = mapping.columnFor(field)
            if (column == null) editor.remove(key) else editor.putString(key, column)
        }
        editor.apply()
    }

    // ── Last seen header ────────────────────────────────────────────────────

    /**
     * Column names of the most recently opened file, so Settings can offer the
     * real headers even before a log is loaded in the current session. Stored
     * newline-separated — a CSV header cell cannot contain a newline.
     */
    var lastKnownColumns: List<String>
        get() = prefs.getString(KEY_LAST_COLUMNS, null)
            ?.split('\n')
            ?.filter { it.isNotBlank() }
            .orEmpty()
        set(value) = prefs.edit()
            .putString(KEY_LAST_COLUMNS, value.joinToString("\n"))
            .apply()

    private companion object {
        const val KEY_LOCAL_FOLDER = "local_folder_uri"
        const val KEY_MAPPING_PREFIX = "map_"
        const val KEY_LAST_COLUMNS = "last_columns"
    }
}
