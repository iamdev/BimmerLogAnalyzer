package com.bimmerdyno.viewmodel

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.bimmerdyno.data.CsvParser
import com.bimmerdyno.data.FieldMapping
import com.bimmerdyno.data.FolderContents
import com.bimmerdyno.data.LogField
import com.bimmerdyno.data.LogFile
import com.bimmerdyno.data.LogFolder
import com.bimmerdyno.data.LogSession
import com.bimmerdyno.data.SettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed class UiState {
    object Idle : UiState()
    object Loading : UiState()
    data class Success(val session: LogSession) : UiState()
    data class Error(val message: String) : UiState()
}

sealed class FolderBrowseState {
    object Idle : FolderBrowseState()
    object Loading : FolderBrowseState()
    data class Browsing(val contents: FolderContents) : FolderBrowseState()
    data class Error(val message: String) : FolderBrowseState()
}

enum class ChartType { SPEED_TIME, TORQUE_TIME, POWER_TIME, DYNO_CURVE, DYNO_ESTIMATE, BOOST_TIME, TEMP_TIME }

/** Power display unit. PS = metric horsepower, BHP = imperial brake horsepower. */
enum class PowerUnit(val label: String) { PS("PS"), BHP("HP") }

class MainViewModel(app: Application) : AndroidViewModel(app) {

    val uiState: StateFlow<UiState> get() = _uiState
    private val _uiState = MutableStateFlow<UiState>(UiState.Idle)

    val folderBrowseState: StateFlow<FolderBrowseState> get() = _folderBrowseState
    private val _folderBrowseState = MutableStateFlow<FolderBrowseState>(FolderBrowseState.Idle)

    val selectedChartType: StateFlow<ChartType> get() = _selectedChartType
    private val _selectedChartType = MutableStateFlow(ChartType.SPEED_TIME)

    val powerUnit: StateFlow<PowerUnit> get() = _powerUnit
    private val _powerUnit = MutableStateFlow(PowerUnit.PS)

    private val settings = SettingsStore(app)

    /** Column overrides applied to every parse. */
    val fieldMapping: StateFlow<FieldMapping> get() = _fieldMapping
    private val _fieldMapping = MutableStateFlow(settings.loadMapping())

    /**
     * Column names available to pick from in Settings: the header of the file
     * loaded in this session, falling back to the last one seen on a previous
     * run so the picker is useful straight after launch.
     */
    val availableColumns: StateFlow<List<String>> get() = _availableColumns
    private val _availableColumns = MutableStateFlow(settings.lastKnownColumns)

    /** Last opened file, so a mapping change can re-parse it in place. */
    private var lastLoaded: Pair<Uri, String>? = null

    private var parseJob: Job? = null

    // ── Local folder browser ────────────────────────────────────────────────

    private val localNavStack = ArrayDeque<String>() // folder URI strings, bottom = root

    /**
     * The previously-picked local folder tree URI, but only if its persisted
     * read permission is still granted. Returns null otherwise so the caller
     * re-prompts. The persisted URI permission survives reboots, so a saved
     * folder means we never have to ask again.
     */
    fun savedLocalFolderUri(): Uri? {
        val saved = settings.localFolderUri ?: return null
        val uri = Uri.parse(saved)
        val stillGranted = getApplication<Application>().contentResolver
            .persistedUriPermissions.any { it.uri == uri && it.isReadPermission }
        return if (stillGranted) uri else null
    }

    /** Display name of the remembered folder, or null if none. */
    fun savedLocalFolderName(): String? {
        val uri = savedLocalFolderUri() ?: return null
        return DocumentFile.fromTreeUri(getApplication(), uri)?.name
    }

    /** Re-open the remembered folder without showing the system picker. */
    fun openSavedLocalFolder() {
        val uri = savedLocalFolderUri() ?: return
        localNavStack.clear()
        localNavStack.addLast(uri.toString())
        browseLocalAtStack()
    }

    fun openLocalFolder(treeUri: Uri) {
        try {
            getApplication<Application>().contentResolver
                .takePersistableUriPermission(treeUri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            // Remember the folder so we don't ask again next launch
            settings.localFolderUri = treeUri.toString()
        } catch (_: SecurityException) {}
        localNavStack.clear()
        localNavStack.addLast(treeUri.toString())
        browseLocalAtStack()
    }

    fun navigateLocalSubfolder(folderUri: String) {
        localNavStack.addLast(folderUri)
        browseLocalAtStack()
    }

    fun navigateLocalFolderUp() {
        if (localNavStack.size <= 1) return
        localNavStack.removeLast()
        browseLocalAtStack()
    }

    fun loadLocalFileFromBrowser(file: LogFile) {
        _folderBrowseState.value = FolderBrowseState.Idle
        loadLocalFile(Uri.parse(file.id), file.name)
    }

    private fun browseLocalAtStack() {
        val currentUri = localNavStack.last()
        val parentUri = if (localNavStack.size >= 2) localNavStack[localNavStack.size - 2] else null
        viewModelScope.launch {
            _folderBrowseState.value = FolderBrowseState.Loading
            withContext(Dispatchers.IO) {
                runCatching {
                    val app = getApplication<Application>()
                    val currentDoc = DocumentFile.fromTreeUri(app, Uri.parse(currentUri))
                        ?: throw Exception("ไม่สามารถเปิด folder ได้")
                    val parentDoc = parentUri?.let { DocumentFile.fromTreeUri(app, Uri.parse(it)) }
                    buildLocalFolderContents(currentDoc, parentDoc)
                }.onSuccess { contents ->
                    _folderBrowseState.value = FolderBrowseState.Browsing(contents)
                }.onFailure {
                    _folderBrowseState.value = FolderBrowseState.Error(
                        it.message ?: "ไม่สามารถเปิด folder ได้"
                    )
                }
            }
        }
    }

    private fun buildLocalFolderContents(
        docFile: DocumentFile,
        parentDocFile: DocumentFile?,
    ): FolderContents {
        val subFolders = mutableListOf<LogFolder>()
        val csvFiles = mutableListOf<LogFile>()
        docFile.listFiles().forEach { child ->
            val name = child.name ?: return@forEach
            when {
                child.isDirectory -> subFolders.add(
                    LogFolder(id = child.uri.toString(), name = name, path = name)
                )
                child.isFile && name.endsWith(".csv", ignoreCase = true) -> csvFiles.add(
                    LogFile(id = child.uri.toString(), name = name, size = child.length())
                )
            }
        }
        return FolderContents(
            currentFolder = LogFolder(
                id = docFile.uri.toString(),
                name = docFile.name ?: "/",
                path = docFile.name ?: "/",
            ),
            parentFolder = parentDocFile?.let {
                LogFolder(id = it.uri.toString(), name = it.name ?: "..", path = it.name ?: "..")
            },
            subFolders = subFolders.sortedBy { it.name },
            csvFiles = csvFiles,
        )
    }

    // ── Local file ──────────────────────────────────────────────────────────

    fun loadLocalFile(uri: Uri, fileName: String) {
        lastLoaded = uri to fileName
        // Changing several mappings in a row queues several re-parses; without
        // this the slowest one could land last and win.
        parseJob?.cancel()
        parseJob = viewModelScope.launch {
            _uiState.value = UiState.Loading
            withContext(Dispatchers.IO) {
                try {
                    val stream = getApplication<Application>().contentResolver.openInputStream(uri)
                        ?: throw Exception("Cannot open file")
                    val parsed = stream.use { CsvParser.parse(it, _fieldMapping.value) }
                    parsed.header?.columns?.takeIf { it.isNotEmpty() }?.let { rememberColumns(it) }
                    if (parsed.points.isEmpty()) throw Exception("No valid data found in CSV")
                    _uiState.value = UiState.Success(LogSession(fileName, parsed.points))
                } catch (e: Exception) {
                    _uiState.value = UiState.Error(e.message ?: "Failed to load file")
                }
            }
        }
    }

    private fun rememberColumns(columns: List<String>) {
        _availableColumns.value = columns
        settings.lastKnownColumns = columns
    }

    // ── Field mapping ───────────────────────────────────────────────────────

    /** Bind [field] to [column]; a null [column] restores auto-detection. */
    fun setFieldMapping(field: LogField, column: String?) {
        updateMapping(_fieldMapping.value.with(field, column))
    }

    /** Drop every override and go back to keyword auto-detection. */
    fun resetFieldMapping() = updateMapping(FieldMapping.AUTO)

    private fun updateMapping(mapping: FieldMapping) {
        _fieldMapping.value = mapping
        settings.saveMapping(mapping)
        // Re-parse the open log so the change is visible without reopening it.
        lastLoaded?.let { (uri, name) ->
            if (_uiState.value is UiState.Success) loadLocalFile(uri, name)
        }
    }

    /**
     * Read the header of [uri] so Settings can offer that file's real column
     * names. Errors are silent: the picker simply keeps the columns it had.
     */
    fun loadColumnsFrom(uri: Uri) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                runCatching {
                    getApplication<Application>().contentResolver.openInputStream(uri)
                        ?.use { CsvParser.readHeader(it) }
                }.getOrNull()?.columns?.takeIf { it.isNotEmpty() }?.let { rememberColumns(it) }
            }
        }
    }

    /** Which column each field actually resolves to for the current header. */
    fun resolvedColumns(): Map<LogField, String?> {
        val columns = _availableColumns.value
        if (columns.isEmpty()) return emptyMap()
        return CsvParser.resolveIndices(columns, _fieldMapping.value)
            .mapValues { (_, idx) -> columns.getOrNull(idx) }
    }

    // ── Common ───────────────────────────────────────────────────────────────

    fun dismissFolderBrowser() { _folderBrowseState.value = FolderBrowseState.Idle }

    fun selectChart(type: ChartType) { _selectedChartType.value = type }

    fun selectPowerUnit(unit: PowerUnit) { _powerUnit.value = unit }

    fun reset() { _uiState.value = UiState.Idle }
}
