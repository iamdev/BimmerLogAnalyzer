package com.bimmerloganalyzer.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.material3.LocalTextStyle
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bimmerloganalyzer.cloud.CloudFile
import com.bimmerloganalyzer.cloud.CloudFolder
import com.bimmerloganalyzer.cloud.CloudFolderContents
import com.bimmerloganalyzer.data.LogFileName
import com.bimmerloganalyzer.viewmodel.CloudSource
import com.bimmerloganalyzer.viewmodel.FolderBrowseState
import com.bimmerloganalyzer.viewmodel.MainViewModel

@Composable
fun CloudFolderBrowserHost(
    viewModel: MainViewModel,
    state: FolderBrowseState,
) {
    when (state) {
        is FolderBrowseState.PathInput -> {
            PathInputDialog(
                source = state.source,
                initialPath = state.currentPath,
                onNavigate = { path ->
                    when (state.source) {
                        CloudSource.ONEDRIVE -> viewModel.navigateToOneDrivePath(path)
                        CloudSource.GOOGLE_DRIVE -> viewModel.navigateToGoogleDrivePath(path)
                    }
                },
                onBrowseRoot = {
                    val root = when (state.source) {
                        CloudSource.ONEDRIVE -> com.bimmerloganalyzer.cloud.OneDriveHelper.ROOT_FOLDER
                        CloudSource.GOOGLE_DRIVE -> com.bimmerloganalyzer.cloud.GoogleDriveHelper.ROOT_FOLDER
                    }
                    when (state.source) {
                        CloudSource.ONEDRIVE -> viewModel.navigateToOneDriveFolder(root)
                        CloudSource.GOOGLE_DRIVE -> viewModel.navigateToGoogleDriveFolder(root)
                    }
                },
                onDismiss = viewModel::dismissFolderBrowser,
            )
        }

        is FolderBrowseState.Browsing -> {
            FolderBrowserDialog(
                contents = state.contents,
                source = state.source,
                onOpenFolder = { folder ->
                    when (state.source) {
                        CloudSource.ONEDRIVE -> viewModel.navigateToOneDriveFolder(folder)
                        CloudSource.GOOGLE_DRIVE -> viewModel.navigateToGoogleDriveFolder(folder)
                    }
                },
                onSelectFile = { file ->
                    when (state.source) {
                        CloudSource.ONEDRIVE -> viewModel.downloadOneDriveFile(file)
                        CloudSource.GOOGLE_DRIVE -> viewModel.downloadGoogleDriveFile(file)
                    }
                },
                onNavigateToPath = { path ->
                    when (state.source) {
                        CloudSource.ONEDRIVE -> viewModel.navigateToOneDrivePath(path)
                        CloudSource.GOOGLE_DRIVE -> viewModel.navigateToGoogleDrivePath(path)
                    }
                },
                onDismiss = viewModel::dismissFolderBrowser,
            )
        }

        is FolderBrowseState.LocalBrowsing -> {
            LocalFolderBrowserDialog(
                contents = state.contents,
                onNavigateInto = { viewModel.navigateLocalSubfolder(it) },
                onNavigateUp = { viewModel.navigateLocalFolderUp() },
                onSelectFile = { viewModel.loadLocalFileFromBrowser(it) },
                onDismiss = viewModel::dismissFolderBrowser,
            )
        }

        is FolderBrowseState.Loading -> {
            AlertDialog(
                onDismissRequest = {},
                confirmButton = {},
                title = { Text("Loading…") },
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(12.dp))
                        Text("กำลังโหลดรายการไฟล์…")
                    }
                },
            )
        }

        is FolderBrowseState.Error -> {
            AlertDialog(
                onDismissRequest = viewModel::dismissFolderBrowser,
                confirmButton = {
                    TextButton(onClick = viewModel::dismissFolderBrowser) { Text("ตกลง") }
                },
                title = { Text("เกิดข้อผิดพลาด") },
                text = { Text(state.message) },
            )
        }

        FolderBrowseState.Idle -> {}
    }
}

// ── Path Input Dialog ────────────────────────────────────────────────────────

@Composable
private fun PathInputDialog(
    source: CloudSource,
    initialPath: String,
    onNavigate: (String) -> Unit,
    onBrowseRoot: () -> Unit,
    onDismiss: () -> Unit,
) {
    var path by remember { mutableStateOf(initialPath) }
    val focusManager = LocalFocusManager.current
    val sourceName = if (source == CloudSource.ONEDRIVE) "OneDrive" else "Google Drive"

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (source == CloudSource.ONEDRIVE) Icons.Filled.Cloud else Icons.Filled.CloudUpload,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(8.dp))
                Text("$sourceName — เลือก Folder")
            }
        },
        text = {
            Column {
                Text(
                    "ระบุ path ของ folder ที่เก็บไฟล์ OBD log\nเช่น /OBD Logs/BMW หรือเว้นว่างสำหรับ root",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(0.6f),
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = path,
                    onValueChange = { path = it },
                    label = { Text("Folder Path") },
                    placeholder = { Text("/OBD Logs/BMW") },
                    leadingIcon = { Icon(Icons.Filled.FolderOpen, null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = {
                        focusManager.clearFocus()
                        onNavigate(path)
                    }),
                )
            }
        },
        confirmButton = {
            Button(onClick = { onNavigate(path) }) {
                Text("เปิด Folder")
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onBrowseRoot) {
                    Icon(Icons.Filled.Home, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Root")
                }
                TextButton(onClick = onDismiss) { Text("ยกเลิก") }
            }
        },
    )
}

// ── Folder Browser Dialog ────────────────────────────────────────────────────

@Composable
private fun FolderBrowserDialog(
    contents: CloudFolderContents,
    source: CloudSource,
    onOpenFolder: (CloudFolder) -> Unit,
    onSelectFile: (CloudFile) -> Unit,
    onNavigateToPath: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var showPathInput by remember { mutableStateOf(false) }
    var customPath by remember(contents.currentFolder.path) {
        mutableStateOf(contents.currentFolder.path)
    }
    val focusManager = LocalFocusManager.current
    val sourceName = if (source == CloudSource.ONEDRIVE) "OneDrive" else "Google Drive"
    val isEmpty = contents.subFolders.isEmpty() && contents.csvFiles.isEmpty()

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("ปิด") } },
        title = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.FolderOpen, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text(sourceName, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(4.dp))
                // Breadcrumb path + edit button
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (!showPathInput) {
                        Text(
                            contents.currentFolder.path,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = { showPathInput = true }, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Filled.Edit, "แก้ไข path", modifier = Modifier.size(16.dp))
                        }
                    } else {
                        OutlinedTextField(
                            value = customPath,
                            onValueChange = { customPath = it },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            textStyle = LocalTextStyle.current.copy(fontSize = 12.sp),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                            keyboardActions = KeyboardActions(onGo = {
                                focusManager.clearFocus()
                                showPathInput = false
                                onNavigateToPath(customPath)
                            }),
                            trailingIcon = {
                                IconButton(onClick = {
                                    showPathInput = false
                                    onNavigateToPath(customPath)
                                }) { Icon(Icons.Filled.Check, "ไปที่ path") }
                            }
                        )
                    }
                }
            }
        },
        text = {
            LazyColumn(modifier = Modifier.heightIn(min = 100.dp, max = 420.dp)) {

                // Back / Up button
                if (contents.parentFolder != null) {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onOpenFolder(contents.parentFolder) }
                                .padding(vertical = 10.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Filled.ArrowUpward, null,
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(20.dp),
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                ".. ขึ้นไปที่ ${contents.parentFolder.name}",
                                color = MaterialTheme.colorScheme.secondary,
                                fontSize = 14.sp,
                            )
                        }
                        HorizontalDivider(thickness = 0.5.dp)
                    }
                }

                // Subfolders
                if (contents.subFolders.isNotEmpty()) {
                    item {
                        Text(
                            "FOLDERS",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface.copy(0.4f),
                            modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
                        )
                    }
                    items(contents.subFolders) { folder ->
                        FolderRow(folder, onClick = { onOpenFolder(folder) })
                        HorizontalDivider(thickness = 0.5.dp)
                    }
                }

                // CSV files
                if (contents.csvFiles.isNotEmpty()) {
                    item {
                        Text(
                            "OBD LOG FILES  (${contents.csvFiles.size})",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface.copy(0.4f),
                            modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
                        )
                    }
                    // Sort by filename descending (newest first)
                    val sortedFiles = contents.csvFiles.sortedByDescending { it.name }
                    items(sortedFiles) { file ->
                        LogFileRow(file, onClick = { onSelectFile(file) })
                        HorizontalDivider(thickness = 0.5.dp)
                    }
                }

                if (isEmpty) {
                    item {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                "ไม่พบไฟล์ CSV หรือ Folder ใน path นี้",
                                color = MaterialTheme.colorScheme.onSurface.copy(0.4f),
                            )
                        }
                    }
                }
            }
        },
    )
}

@Composable
private fun FolderRow(folder: CloudFolder, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Filled.Folder, null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.width(10.dp))
        Text(folder.name, modifier = Modifier.weight(1f))
        Icon(
            Icons.Filled.ChevronRight, null,
            tint = MaterialTheme.colorScheme.onSurface.copy(0.4f),
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun LogFileRow(file: CloudFile, onClick: () -> Unit) {
    val displayLabel = LogFileName.displayLabel(file.name)
    val isOBDFile = displayLabel != file.name // parsed successfully

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Filled.Speed, null,
            tint = if (isOBDFile) MaterialTheme.colorScheme.secondary
                   else MaterialTheme.colorScheme.onSurface.copy(0.4f),
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            // Show parsed datetime as primary label if matched
            Text(
                displayLabel,
                fontWeight = if (isOBDFile) FontWeight.Medium else FontWeight.Normal,
                fontSize = 14.sp,
            )
            if (isOBDFile) {
                Text(
                    file.name,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(0.4f),
                )
            }
            if (file.size > 0) {
                Text(
                    formatSize(file.size),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(0.4f),
                )
            }
        }
        Icon(
            Icons.Filled.PlayArrow, null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp),
        )
    }
}

// ── Local Folder Browser Dialog ──────────────────────────────────────────────

@Composable
private fun LocalFolderBrowserDialog(
    contents: CloudFolderContents,
    onNavigateInto: (String) -> Unit,
    onNavigateUp: () -> Unit,
    onSelectFile: (CloudFile) -> Unit,
    onDismiss: () -> Unit,
) {
    val isEmpty = contents.subFolders.isEmpty() && contents.csvFiles.isEmpty()

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("ปิด") } },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.FolderOpen, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text("เครื่อง", fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(6.dp))
                Text(
                    contents.currentFolder.name,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        },
        text = {
            LazyColumn(modifier = Modifier.heightIn(min = 100.dp, max = 420.dp)) {

                // Up button
                if (contents.parentFolder != null) {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onNavigateUp() }
                                .padding(vertical = 10.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Filled.ArrowUpward, null,
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(20.dp),
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                ".. ขึ้นไปที่ ${contents.parentFolder.name}",
                                color = MaterialTheme.colorScheme.secondary,
                                fontSize = 14.sp,
                            )
                        }
                        HorizontalDivider(thickness = 0.5.dp)
                    }
                }

                // Subfolders
                if (contents.subFolders.isNotEmpty()) {
                    item {
                        Text(
                            "FOLDERS",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface.copy(0.4f),
                            modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
                        )
                    }
                    items(contents.subFolders) { folder ->
                        FolderRow(folder, onClick = { onNavigateInto(folder.id) })
                        HorizontalDivider(thickness = 0.5.dp)
                    }
                }

                // CSV files
                if (contents.csvFiles.isNotEmpty()) {
                    item {
                        Text(
                            "OBD LOG FILES  (${contents.csvFiles.size})",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface.copy(0.4f),
                            modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
                        )
                    }
                    val sortedFiles = contents.csvFiles.sortedByDescending { it.name }
                    items(sortedFiles) { file ->
                        LogFileRow(file, onClick = { onSelectFile(file) })
                        HorizontalDivider(thickness = 0.5.dp)
                    }
                }

                if (isEmpty) {
                    item {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                "ไม่พบไฟล์ CSV หรือ Folder ใน path นี้",
                                color = MaterialTheme.colorScheme.onSurface.copy(0.4f),
                            )
                        }
                    }
                }
            }
        },
    )
}

private fun formatSize(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "${bytes / 1024} KB"
    else -> "${"%.1f".format(bytes / 1024f / 1024f)} MB"
}
