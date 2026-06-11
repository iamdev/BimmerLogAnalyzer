package com.bimmerloganalyzer.viewmodel

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.bimmerloganalyzer.cloud.CloudFile
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

sealed class CloudBrowseState {
    object Idle : CloudBrowseState()
    object Loading : CloudBrowseState()
    data class FileList(val files: List<CloudFile>, val source: CloudSource) : CloudBrowseState()
    data class Error(val message: String) : CloudBrowseState()
}

enum class CloudSource { ONEDRIVE, GOOGLE_DRIVE }

enum class ChartType { SPEED_TIME, TORQUE_TIME, POWER_TIME, DYNO_CURVE, BOOST_TIME, TEMP_TIME }

class MainViewModel(app: Application) : AndroidViewModel(app) {

    val uiState: StateFlow<UiState> get() = _uiState
    private val _uiState = MutableStateFlow<UiState>(UiState.Idle)

    val cloudBrowseState: StateFlow<CloudBrowseState> get() = _cloudBrowseState
    private val _cloudBrowseState = MutableStateFlow<CloudBrowseState>(CloudBrowseState.Idle)

    val selectedChartType: StateFlow<ChartType> get() = _selectedChartType
    private val _selectedChartType = MutableStateFlow(ChartType.SPEED_TIME)

    val oneDrive = OneDriveHelper(app)
    val googleDrive = GoogleDriveHelper(app)

    private var oneDriveToken: String? = null

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
        viewModelScope.launch {
            oneDrive.initialize()
        }
    }

    fun signInOneDrive(activity: Activity) {
        viewModelScope.launch {
            _cloudBrowseState.value = CloudBrowseState.Loading
            val result = oneDrive.signIn(activity)
            result.onSuccess { token ->
                oneDriveToken = token
                listOneDriveFiles(token)
            }.onFailure {
                _cloudBrowseState.value = CloudBrowseState.Error(it.message ?: "OneDrive sign-in failed")
            }
        }
    }

    fun listOneDriveFiles(token: String? = oneDriveToken) {
        val t = token ?: return
        viewModelScope.launch {
            _cloudBrowseState.value = CloudBrowseState.Loading
            oneDrive.listCsvFiles(t).onSuccess { files ->
                _cloudBrowseState.value = CloudBrowseState.FileList(files, CloudSource.ONEDRIVE)
            }.onFailure {
                _cloudBrowseState.value = CloudBrowseState.Error(it.message ?: "Failed to list files")
            }
        }
    }

    fun downloadOneDriveFile(file: CloudFile) {
        val token = oneDriveToken ?: return
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            _cloudBrowseState.value = CloudBrowseState.Idle
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
                listGoogleDriveFiles()
            }.onFailure {
                _cloudBrowseState.value = CloudBrowseState.Error(it.message ?: "Google sign-in failed")
            }
        }
    }

    fun listGoogleDriveFiles() {
        viewModelScope.launch {
            _cloudBrowseState.value = CloudBrowseState.Loading
            googleDrive.listCsvFiles().onSuccess { files ->
                _cloudBrowseState.value = CloudBrowseState.FileList(files, CloudSource.GOOGLE_DRIVE)
            }.onFailure {
                _cloudBrowseState.value = CloudBrowseState.Error(it.message ?: "Failed to list files")
            }
        }
    }

    fun downloadGoogleDriveFile(file: CloudFile) {
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            _cloudBrowseState.value = CloudBrowseState.Idle
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

    fun dismissCloudBrowser() {
        _cloudBrowseState.value = CloudBrowseState.Idle
    }

    fun selectChart(type: ChartType) {
        _selectedChartType.value = type
    }

    fun reset() {
        _uiState.value = UiState.Idle
    }
}
