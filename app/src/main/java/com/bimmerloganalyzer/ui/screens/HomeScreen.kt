package com.bimmerloganalyzer.ui.screens

import android.app.Activity
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bimmerloganalyzer.viewmodel.FolderBrowseState
import com.bimmerloganalyzer.viewmodel.MainViewModel
import com.bimmerloganalyzer.viewmodel.UiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(viewModel: MainViewModel, onSessionLoaded: () -> Unit) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val folderState by viewModel.folderBrowseState.collectAsState()

    LaunchedEffect(uiState) {
        if (uiState is UiState.Success) onSessionLoaded()
    }

    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        uri?.let { viewModel.openLocalFolder(it) }
    }

    val googleSignInLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result -> viewModel.handleGoogleSignInResult(result.data) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("BimmerLog Analyzer", fontWeight = FontWeight.Bold) },
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
            Icon(
                Icons.Filled.Speed, null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(72.dp),
            )
            Spacer(Modifier.height(12.dp))
            Text("OBD Log Analyzer", fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text(
                "Speed · Torque · Horsepower",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(0.6f),
            )
            Spacer(Modifier.height(40.dp))

            // Local folder
            ImportButton(
                icon = Icons.Filled.FolderOpen,
                label = "เปิดไฟล์จากเครื่อง",
                subtitle = "เรียกดู CSV จาก storage",
                onClick = { folderPicker.launch(null) },
            )
            Spacer(Modifier.height(16.dp))

            // OneDrive
            ImportButton(
                icon = Icons.Filled.Cloud,
                label = "OneDrive",
                subtitle = if (viewModel.oneDrive.isSignedIn) "แตะเพื่อเลือก folder" else "Sign in ด้วย Microsoft account",
                badge = if (viewModel.oneDrive.isSignedIn) "Connected" else null,
                onClick = {
                    if (viewModel.oneDrive.isSignedIn) viewModel.openOneDriveBrowser()
                    else viewModel.signInOneDrive(context as Activity)
                },
            )
            Spacer(Modifier.height(16.dp))

            // Google Drive
            ImportButton(
                icon = Icons.Filled.CloudUpload,
                label = "Google Drive",
                subtitle = viewModel.googleDrive.currentAccountEmail ?: "Sign in ด้วย Google account",
                badge = if (viewModel.googleDrive.isSignedIn) "Connected" else null,
                onClick = {
                    if (viewModel.googleDrive.isSignedIn) viewModel.openGoogleDriveBrowser()
                    else googleSignInLauncher.launch(viewModel.googleDrive.getSignInIntent())
                },
            )

            Spacer(Modifier.height(40.dp))

            when (val s = uiState) {
                is UiState.Loading -> {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(8.dp))
                    Text("กำลังโหลดข้อมูล…")
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

    // Cloud folder browser overlays
    CloudFolderBrowserHost(viewModel = viewModel, state = folderState)
}

@Composable
private fun ImportButton(
    icon: ImageVector,
    label: String,
    subtitle: String,
    badge: String? = null,
    onClick: () -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(16.dp),
    ) {
        Icon(icon, null, modifier = Modifier.size(28.dp))
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(label, fontWeight = FontWeight.Medium)
                if (badge != null) {
                    Spacer(Modifier.width(6.dp))
                    Surface(
                        shape = MaterialTheme.shapes.extraSmall,
                        color = MaterialTheme.colorScheme.primaryContainer,
                    ) {
                        Text(
                            badge,
                            fontSize = 10.sp,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
            Text(subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(0.6f))
        }
        Icon(Icons.Filled.ChevronRight, null)
    }
}
