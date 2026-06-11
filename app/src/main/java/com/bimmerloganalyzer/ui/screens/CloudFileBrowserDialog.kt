package com.bimmerloganalyzer.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bimmerloganalyzer.cloud.CloudFile
import com.bimmerloganalyzer.viewmodel.CloudBrowseState
import com.bimmerloganalyzer.viewmodel.CloudSource

@Composable
fun CloudFileBrowserDialog(
    state: CloudBrowseState.FileList,
    onSelect: (CloudFile) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        title = {
            Text(
                if (state.source == CloudSource.ONEDRIVE) "OneDrive — CSV Files"
                else "Google Drive — CSV Files"
            )
        },
        text = {
            if (state.files.isEmpty()) {
                Text("No CSV files found.", color = MaterialTheme.colorScheme.onSurface.copy(0.6f))
            } else {
                LazyColumn {
                    items(state.files) { file ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelect(file) }
                                .padding(vertical = 10.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Filled.InsertDriveFile,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp),
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(file.name, fontWeight = FontWeight.Medium)
                                if (file.size > 0) {
                                    Text(
                                        formatSize(file.size),
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurface.copy(0.5f),
                                    )
                                }
                            }
                        }
                        HorizontalDivider(thickness = 0.5.dp)
                    }
                }
            }
        }
    )
}

private fun formatSize(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "${bytes / 1024} KB"
    else -> "${"%.1f".format(bytes / 1024f / 1024f)} MB"
}
