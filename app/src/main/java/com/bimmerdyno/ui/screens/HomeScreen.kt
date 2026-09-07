package com.bimmerdyno.ui.screens

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bimmerdyno.viewmodel.MainViewModel
import com.bimmerdyno.viewmodel.UiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: MainViewModel,
    onSessionLoaded: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState()
    val folderState by viewModel.folderBrowseState.collectAsState()

    LaunchedEffect(uiState) {
        if (uiState is UiState.Success) onSessionLoaded()
    }

    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        uri?.let { viewModel.openLocalFolder(it) }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("BimmerDyno", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Filled.Settings, "ตั้งค่า")
                    }
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

            // Local folder — remembers the picked folder across launches
            val savedFolderName = remember(folderState, uiState) { viewModel.savedLocalFolderName() }
            ImportButton(
                icon = Icons.Filled.FolderOpen,
                label = if (savedFolderName != null) "เปิดจาก: $savedFolderName" else "เปิดไฟล์จากเครื่อง",
                subtitle = if (savedFolderName != null) "จำ folder ไว้แล้ว · แตะเพื่อเรียกดู"
                           else "เลือก folder ที่เก็บ CSV",
                badge = if (savedFolderName != null) "Saved" else null,
                onClick = {
                    if (viewModel.savedLocalFolderUri() != null) viewModel.openSavedLocalFolder()
                    else folderPicker.launch(null)
                },
            )
            if (savedFolderName != null) {
                Spacer(Modifier.height(4.dp))
                TextButton(
                    onClick = { folderPicker.launch(null) },
                    modifier = Modifier.align(Alignment.End),
                ) {
                    Icon(Icons.Filled.FolderOpen, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("เปลี่ยน folder", fontSize = 12.sp)
                }
            }

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

    // Folder browser overlay
    LocalFolderBrowserHost(viewModel = viewModel, state = folderState)
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
