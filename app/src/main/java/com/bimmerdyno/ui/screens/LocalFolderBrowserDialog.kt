package com.bimmerdyno.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bimmerdyno.data.FolderContents
import com.bimmerdyno.data.LogFile
import com.bimmerdyno.data.LogFileName
import com.bimmerdyno.data.LogFolder
import com.bimmerdyno.viewmodel.FolderBrowseState
import com.bimmerdyno.viewmodel.MainViewModel

@Composable
fun LocalFolderBrowserHost(
    viewModel: MainViewModel,
    state: FolderBrowseState,
) {
    when (state) {
        is FolderBrowseState.Browsing -> {
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

// ── Folder Browser Dialog ────────────────────────────────────────────────────

@Composable
private fun LocalFolderBrowserDialog(
    contents: FolderContents,
    onNavigateInto: (String) -> Unit,
    onNavigateUp: () -> Unit,
    onSelectFile: (LogFile) -> Unit,
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
                                "ไม่พบไฟล์ CSV หรือ Folder ใน folder นี้",
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
private fun FolderRow(folder: LogFolder, onClick: () -> Unit) {
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
private fun LogFileRow(file: LogFile, onClick: () -> Unit) {
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

private fun formatSize(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "${bytes / 1024} KB"
    else -> "${"%.1f".format(bytes / 1024f / 1024f)} MB"
}
