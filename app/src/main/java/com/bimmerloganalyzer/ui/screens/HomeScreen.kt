package com.bimmerloganalyzer.ui.screens

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bimmerloganalyzer.viewmodel.CloudBrowseState
import com.bimmerloganalyzer.viewmodel.CloudSource
import com.bimmerloganalyzer.viewmodel.MainViewModel
import com.bimmerloganalyzer.viewmodel.UiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(viewModel: MainViewModel, onSessionLoaded: () -> Unit) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val cloudState by viewModel.cloudBrowseState.collectAsState()

    // Navigate when session is ready
    LaunchedEffect(uiState) {
        if (uiState is UiState.Success) onSessionLoaded()
    }

    // File picker for local CSV
    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            val name = context.contentResolver.query(it, null, null, null, null)?.use { c ->
                val idx = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                c.moveToFirst(); if (idx >= 0) c.getString(idx) else "log.csv"
            } ?: "log.csv"
            viewModel.loadLocalFile(it, name)
        }
    }

    // Google Sign-In launcher
    val googleSignInLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        viewModel.handleGoogleSignInResult(result.data)
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text("BimmerLog Analyzer", fontWeight = FontWeight.Bold)
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.primary,
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {

            // Logo / title area
            Icon(
                imageVector = Icons.Filled.Speed,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(72.dp)
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "OBD Log Analyzer",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                "Speed · Torque · Horsepower",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            )

            Spacer(Modifier.height(40.dp))

            // Import from device
            ImportButton(
                icon = Icons.Filled.FolderOpen,
                label = "Open Local CSV",
                subtitle = "Pick file from device storage",
                onClick = { filePicker.launch("*/*") }
            )

            Spacer(Modifier.height(16.dp))

            // Import from OneDrive
            ImportButton(
                icon = Icons.Filled.Cloud,
                label = "Open from OneDrive",
                subtitle = "Sign in with Microsoft account",
                onClick = {
                    if (viewModel.oneDrive.isSignedIn) {
                        viewModel.listOneDriveFiles()
                    } else {
                        viewModel.signInOneDrive(context as Activity)
                    }
                }
            )

            Spacer(Modifier.height(16.dp))

            // Import from Google Drive
            ImportButton(
                icon = Icons.Filled.CloudUpload,
                label = "Open from Google Drive",
                subtitle = viewModel.googleDrive.currentAccountEmail ?: "Sign in with Google account",
                onClick = {
                    if (viewModel.googleDrive.isSignedIn) {
                        viewModel.listGoogleDriveFiles()
                    } else {
                        googleSignInLauncher.launch(viewModel.googleDrive.getSignInIntent())
                    }
                }
            )

            Spacer(Modifier.height(40.dp))

            // Status
            when (val s = uiState) {
                is UiState.Loading -> {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(8.dp))
                    Text("Loading…", color = MaterialTheme.colorScheme.onSurface)
                }
                is UiState.Error -> {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Error, null, tint = MaterialTheme.colorScheme.error)
                            Spacer(Modifier.width(8.dp))
                            Text(s.message, color = MaterialTheme.colorScheme.onErrorContainer)
                        }
                    }
                }
                else -> {}
            }
        }
    }

    // Cloud file browser dialog
    if (cloudState is CloudBrowseState.FileList) {
        CloudFileBrowserDialog(
            state = cloudState as CloudBrowseState.FileList,
            onSelect = { file ->
                when ((cloudState as CloudBrowseState.FileList).source) {
                    CloudSource.ONEDRIVE -> viewModel.downloadOneDriveFile(file)
                    CloudSource.GOOGLE_DRIVE -> viewModel.downloadGoogleDriveFile(file)
                }
            },
            onDismiss = { viewModel.dismissCloudBrowser() }
        )
    }
    if (cloudState is CloudBrowseState.Loading) {
        AlertDialog(
            onDismissRequest = {},
            confirmButton = {},
            title = { Text("Connecting…") },
            text = { CircularProgressIndicator() },
        )
    }
    if (cloudState is CloudBrowseState.Error) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissCloudBrowser() },
            confirmButton = { TextButton(onClick = { viewModel.dismissCloudBrowser() }) { Text("OK") } },
            title = { Text("Error") },
            text = { Text((cloudState as CloudBrowseState.Error).message) },
        )
    }
}

@Composable
private fun ImportButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(16.dp),
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(28.dp))
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(label, fontWeight = FontWeight.Medium)
            Text(subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(0.6f))
        }
        Icon(Icons.Filled.ChevronRight, contentDescription = null)
    }
}
