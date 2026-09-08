package com.bimmerdyno.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bimmerdyno.data.FieldMapping
import com.bimmerdyno.data.LogField
import com.bimmerdyno.viewmodel.MainViewModel

/**
 * Field mapping settings: bind each [LogField] to a column of the log file.
 *
 * Every field defaults to auto-detection by keyword; an override is only needed
 * when a logger names its columns unusually. Changes are saved immediately and
 * re-parse the open log, so the effect is visible on the charts straight away.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: MainViewModel, onBack: () -> Unit) {
    val mapping by viewModel.fieldMapping.collectAsState()
    val columns by viewModel.availableColumns.collectAsState()
    val folderName = remember { viewModel.savedLocalFolderName() }

    // Which column each field currently reads, given the header we know about
    val resolved = remember(mapping, columns) { viewModel.resolvedColumns() }

    var showResetConfirm by remember { mutableStateOf(false) }

    val samplePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? -> uri?.let { viewModel.loadColumnsFrom(it) } }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, "กลับ") }
                },
                title = { Text("ตั้งค่า", fontWeight = FontWeight.Bold) },
                actions = {
                    TextButton(onClick = { showResetConfirm = true }) { Text("รีเซ็ต") }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                SectionHeader("แหล่งข้อมูล")
                Text(
                    folderName?.let { "Folder ที่บันทึกไว้: $it" } ?: "ยังไม่ได้เลือก folder",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(0.6f),
                )
                Spacer(Modifier.height(16.dp))

                SectionHeader("Mapping คอลัมน์")
                Text(
                    "ปกติแอปจะเดาคอลัมน์ให้อัตโนมัติจากชื่อหัวตาราง " +
                        "ตั้งค่าตรงนี้เมื่อ logger ตั้งชื่อคอลัมน์ไม่เหมือนใคร " +
                        "หรือต้องการปิดบางค่าไม่ให้อ่าน",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(0.6f),
                )
                Spacer(Modifier.height(8.dp))

                if (columns.isEmpty()) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Text(
                                "ยังไม่รู้จักคอลัมน์ของไฟล์",
                                fontWeight = FontWeight.Medium,
                                fontSize = 14.sp,
                            )
                            Text(
                                "เปิดไฟล์ CSV สักไฟล์ หรือเลือกไฟล์ตัวอย่าง " +
                                    "เพื่อให้แอปอ่านชื่อคอลัมน์มาให้เลือก",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(0.6f),
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }

                OutlinedButton(
                    onClick = { samplePicker.launch(CSV_MIME_TYPES) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Filled.UploadFile, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (columns.isEmpty()) "เลือกไฟล์ตัวอย่างเพื่ออ่านคอลัมน์"
                        else "อ่านคอลัมน์จากไฟล์อื่น (${columns.size} คอลัมน์)"
                    )
                }
                Spacer(Modifier.height(8.dp))
            }

            items(LogField.entries) { field ->
                FieldMappingRow(
                    field = field,
                    mapping = mapping,
                    columns = columns,
                    resolvedColumn = resolved[field],
                    onSelect = { column -> viewModel.setFieldMapping(field, column) },
                )
            }
        }
    }

    if (showResetConfirm) {
        AlertDialog(
            onDismissRequest = { showResetConfirm = false },
            title = { Text("รีเซ็ต mapping") },
            text = { Text("ล้างค่าที่ตั้งไว้ทั้งหมด แล้วกลับไปใช้การตรวจจับอัตโนมัติ") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.resetFieldMapping()
                    showResetConfirm = false
                }) { Text("รีเซ็ต") }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirm = false }) { Text("ยกเลิก") }
            },
        )
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text.uppercase(),
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(bottom = 4.dp),
    )
}

/**
 * One field, with a dropdown of the file's columns plus the two special
 * choices: auto-detect (the default) and off.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FieldMappingRow(
    field: LogField,
    mapping: FieldMapping,
    columns: List<String>,
    resolvedColumn: String?,
    onSelect: (String?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    val label = when {
        mapping.isDisabled(field) -> "ไม่ใช้"
        mapping.isAuto(field) -> "อัตโนมัติ"
        else -> mapping.columnFor(field).orEmpty()
    }

    // A saved override naming a column this file lacks silently falls back to
    // auto-detect at parse time — say so rather than letting it look bound.
    val savedColumn = mapping.columnFor(field)
    val overrideMissing = savedColumn != null &&
        savedColumn != FieldMapping.NONE &&
        columns.isNotEmpty() &&
        columns.none { it.equals(savedColumn.trim(), ignoreCase = true) }

    val helper = when {
        mapping.isDisabled(field) -> "ปิดอยู่ — อ่านเป็น 0"
        overrideMissing -> "ไฟล์นี้ไม่มีคอลัมน์นี้ — ใช้อัตโนมัติแทน: ${resolvedColumn ?: "ไม่พบ"}"
        columns.isEmpty() -> null
        resolvedColumn == null -> "ไม่พบคอลัมน์ที่ตรง — อ่านเป็น 0"
        mapping.isAuto(field) -> "ตรวจพบ: $resolvedColumn"
        else -> null
    }

    Column(Modifier.fillMaxWidth()) {
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = it },
        ) {
            OutlinedTextField(
                value = label,
                onValueChange = {},
                readOnly = true,
                label = {
                    Text(
                        if (field.unit.isEmpty()) field.displayName
                        else "${field.displayName} · ${field.unit}"
                    )
                },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                isError = overrideMissing,
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(),
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
            ) {
                DropdownMenuItem(
                    text = { Text("อัตโนมัติ") },
                    leadingIcon = { Icon(Icons.Filled.AutoAwesome, null) },
                    onClick = { onSelect(null); expanded = false },
                )
                DropdownMenuItem(
                    text = { Text("ไม่ใช้") },
                    leadingIcon = { Icon(Icons.Filled.Block, null) },
                    onClick = { onSelect(FieldMapping.NONE); expanded = false },
                )
                if (columns.isNotEmpty()) HorizontalDivider()
                columns.forEach { column ->
                    DropdownMenuItem(
                        text = { Text(column) },
                        onClick = { onSelect(column); expanded = false },
                    )
                }
            }
        }
        if (helper != null) {
            Text(
                helper,
                fontSize = 11.sp,
                color = if (overrideMissing) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurface.copy(0.5f),
                modifier = Modifier.padding(start = 16.dp, top = 2.dp),
            )
        }
    }
}

/**
 * File managers are inconsistent about which MIME type they report for a CSV,
 * so accept both spellings and fall back to any file type.
 */
private val CSV_MIME_TYPES =
    arrayOf("text/csv", "text/comma-separated-values", "text/plain", "*/*")
