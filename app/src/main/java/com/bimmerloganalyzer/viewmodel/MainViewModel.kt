package com.bimmerloganalyzer.viewmodel

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.bimmerloganalyzer.cloud.CloudFile
import com.bimmerloganalyzer.cloud.CloudFolder
import com.bimmerloganalyzer.cloud.CloudFolderContents
import com.bimmerloganalyzer.cloud.GoogleDriveHelper
import com.bimmerloganalyzer.cloud.OneDriveHelper
import com.bimmerloganalyzer.data.CsvParser
import com.bimmerloganalyzer.data.LogSession
import kotlinx.coroutines.Dispatchers
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
    /** Waiting for user to enter/confirm a path before browsing */
    data class PathInput(val source: CloudSource, val currentPath: String = "/") : FolderBrowseState()
    data class Browsing(val contents: CloudFolderContents, val source: CloudSource) : FolderBrowseState()
    data class LocalBrowsing(val contents: CloudFolderContents) : FolderBrowseState()
    data class Error(val message: String, val source: CloudSource) : FolderBrowseState()
}

enum class CloudSource { ONEDRIVE, GOOGLE_DRIVE }
enum class ChartType { SPEED_TIME, TORQUE_TIME, POWER_TIME, DYNO_CURVE, BOOST_TIME, TEMP_TIME }

class MainViewModel(app: Application) : AndroidViewModel(app) {

    val uiState: StateFlow<UiState> get() = _uiState
    private val _uiState = MutableStateFlow<UiState>(UiState.Idle)

    val folderBrowseState: StateFlow<FolderBrowseState> get() = _folderBrowseState
    private val _folderBrowseState = MutableStateFlow<FolderBrowseState>(FolderBrowseState.Idle)

    val selectedChartType: StateFlow<ChartType> get() = _selectedChartType
    private val _selectedChartType = MutableStateFlow(ChartType.SPEED_TIME)

    val oneDrive = OneDriveHelper(app)
    val googleDrive = GoogleDriveHelper(app)

    private var oneDriveToken: String? = null

    // ── Local folder browser ────────────────────────────────────────────────

    private val localNavStack = ArrayDeque<String>() // folder URI strings, bottom = root

    fun openLocalFolder(treeUri: Uri) {
        try {
            getApplication<Application>().contentResolver
                .takePersistableUriPermission(treeUri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
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

    fun loadLocalFileFromBrowser(file: CloudFile) {
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
                    _folderBrowseState.value = FolderBrowseState.LocalBrowsing(contents)
                }.onFailure {
                    _folderBrowseState.value = FolderBrowseState.Error(
                        it.message ?: "ไม่สามารถเปิด folder ได้", CloudSource.ONEDRIVE
                    )
                }
            }
        }
    }

    private fun buildLocalFolderContents(
        docFile: DocumentFile,
        parentDocFile: DocumentFile?,
    ): CloudFolderContents {
        val subFolders = mutableListOf<CloudFolder>()
        val csvFiles = mutableListOf<CloudFile>()
        docFile.listFiles().forEach { child ->
            val name = child.name ?: return@forEach
            when {
                child.isDirectory -> subFolders.add(
                    CloudFolder(id = child.uri.toString(), name = name, path = name)
                )
                child.isFile && name.endsWith(".csv", ignoreCase = true) -> csvFiles.add(
                    CloudFile(id = child.uri.toString(), name = name, size = child.length())
                )
            }
        }
        return CloudFolderContents(
            currentFolder = CloudFolder(
                id = docFile.uri.toString(),
                name = docFile.name ?: "/",
                path = docFile.name ?: "/",
            ),
            parentFolder = parentDocFile?.let {
                CloudFolder(id = it.uri.toString(), name = it.name ?: "..", path = it.name ?: "..")
            },
            subFolders = subFolders.sortedBy { it.name },
            csvFiles = csvFiles,
        )
    }

    // ── Local file ──────────────────────────────────────────────────────────

    fun loadLocalFile(uri: Uri, fileName: String) {
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            withContext(Dispatchers.IO) {
                try {
                    val stream = getApplication<Application>().contentResolver.openInputStream(uri)
                        ?: throw Exception("Cannot open file")
                    val points = CsvParser.parse(stream)
                    if (points.isEmpty()) throw Exception("No valid data found in CSV")
                    _uiState.value = UiState.Success(LogSession(fileName, points))
                } catch (e: Exception) {
                    _uiState.value = UiState.Error(e.message ?: "Failed to load file")
                }
            }
        }
    }

    // ── OneDrive ────────────────────────────────────────────────────────────

    fun initOneDrive() {
        viewModelScope.launch { oneDrive.initialize() }
    }

    fun signInOneDrive(activity: Activity) {
        viewModelScope.launch {
            _folderBrowseState.value = FolderBrowseState.Loading
            oneDrive.signIn(activity).onSuccess { token ->
                oneDriveToken = token
                // After sign-in, show path input dialog
                _folderBrowseState.value = FolderBrowseState.PathInput(CloudSource.ONEDRIVE)
            }.onFailure {
                _folderBrowseState.value = FolderBrowseState.Error(
                    it.message ?: "OneDrive sign-in failed", CloudSource.ONEDRIVE
                )
            }
        }
    }

    fun openOneDriveBrowser() {
        if (oneDrive.isSignedIn) {
            _folderBrowseState.value = FolderBrowseState.PathInput(CloudSource.ONEDRIVE)
        }
    }

    fun navigateToOneDriveFolder(folder: CloudFolder) {
        val token = oneDriveToken ?: return
        viewModelScope.launch {
            _folderBrowseState.value = FolderBrowseState.Loading
            oneDrive.listFolderContents(token, folder).onSuccess { contents ->
                _folderBrowseState.value = FolderBrowseState.Browsing(contents, CloudSource.ONEDRIVE)
            }.onFailure {
                _folderBrowseState.value = FolderBrowseState.Error(
                    it.message ?: "Cannot open folder", CloudSource.ONEDRIVE
                )
            }
        }
    }

    fun navigateToOneDrivePath(path: String) {
        val token = oneDriveToken ?: return
        viewModelScope.launch {
            _folderBrowseState.value = FolderBrowseState.Loading
            oneDrive.folderByPath(token, path).onSuccess { folder ->
                oneDrive.listFolderContents(token, folder).onSuccess { contents ->
                    _folderBrowseState.value = FolderBrowseState.Browsing(contents, CloudSource.ONEDRIVE)
                }.onFailure {
                    _folderBrowseState.value = FolderBrowseState.Error(
                        it.message ?: "Cannot list folder", CloudSource.ONEDRIVE
                    )
                }
            }.onFailure {
                _folderBrowseState.value = FolderBrowseState.Error(
                    it.message ?: "Path not found", CloudSource.ONEDRIVE
                )
            }
        }
    }

    fun downloadOneDriveFile(file: CloudFile) {
        val token = oneDriveToken ?: return
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            _folderBrowseState.value = FolderBrowseState.Idle
            withContext(Dispatchers.IO) {
                oneDrive.downloadFile(token, file.id).onSuccess { stream ->
                    try {
                        val points = CsvParser.parse(stream)
                        if (points.isEmpty()) throw Exception("No valid data found")
                        _uiState.value = UiState.Success(LogSession(file.name, points))
                    } catch (e: Exception) {
                        _uiState.value = UiState.Error(e.message ?: "Parse error")
                    }
                }.onFailure {
                    _uiState.value = UiState.Error(it.message ?: "Download failed")
                }
            }
        }
    }

    // ── Google Drive ────────────────────────────────────────────────────────

    fun handleGoogleSignInResult(data: Intent?) {
        viewModelScope.launch {
            googleDrive.handleSignInResult(data).onSuccess {
                _folderBrowseState.value = FolderBrowseState.PathInput(CloudSource.GOOGLE_DRIVE)
            }.onFailure {
                _folderBrowseState.value = FolderBrowseState.Error(
                    it.message ?: "Google sign-in failed", CloudSource.GOOGLE_DRIVE
                )
            }
        }
    }

    fun openGoogleDriveBrowser() {
        if (googleDrive.isSignedIn) {
            _folderBrowseState.value = FolderBrowseState.PathInput(CloudSource.GOOGLE_DRIVE)
        }
    }

    fun navigateToGoogleDriveFolder(folder: CloudFolder) {
        viewModelScope.launch {
            _folderBrowseState.value = FolderBrowseState.Loading
            googleDrive.listFolderContents(folder).onSuccess { contents ->
                _folderBrowseState.value = FolderBrowseState.Browsing(contents, CloudSource.GOOGLE_DRIVE)
            }.onFailure {
                _folderBrowseState.value = FolderBrowseState.Error(
                    it.message ?: "Cannot open folder", CloudSource.GOOGLE_DRIVE
                )
            }
        }
    }

    fun navigateToGoogleDrivePath(path: String) {
        viewModelScope.launch {
            _folderBrowseState.value = FolderBrowseState.Loading
            googleDrive.folderByPath(path).onSuccess { folder ->
                googleDrive.listFolderContents(folder).onSuccess { contents ->
                    _folderBrowseState.value = FolderBrowseState.Browsing(contents, CloudSource.GOOGLE_DRIVE)
                }.onFailure {
                    _folderBrowseState.value = FolderBrowseState.Error(
                        it.message ?: "Cannot list folder", CloudSource.GOOGLE_DRIVE
                    )
                }
            }.onFailure {
                _folderBrowseState.value = FolderBrowseState.Error(
                    it.message ?: "Path not found", CloudSource.GOOGLE_DRIVE
                )
            }
        }
    }

    fun downloadGoogleDriveFile(file: CloudFile) {
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            _folderBrowseState.value = FolderBrowseState.Idle
            withContext(Dispatchers.IO) {
                googleDrive.downloadFile(file.id).onSuccess { stream ->
                    try {
                        val points = CsvParser.parse(stream)
                        if (points.isEmpty()) throw Exception("No valid data found")
                        _uiState.value = UiState.Success(LogSession(file.name, points))
                    } catch (e: Exception) {
                        _uiState.value = UiState.Error(e.message ?: "Parse error")
                    }
                }.onFailure {
                    _uiState.value = UiState.Error(it.message ?: "Download failed")
                }
            }
        }
    }

    // ── Common ───────────────────────────────────────────────────────────────

    fun dismissFolderBrowser() { _folderBrowseState.value = FolderBrowseState.Idle }

    fun selectChart(type: ChartType) { _selectedChartType.value = type }

    fun reset() { _uiState.value = UiState.Idle }
}
