package com.bimmerdyno.data

/** A CSV log file inside a browsed folder. [id] is the document tree URI string. */
data class LogFile(
    val id: String,
    val name: String,
    val size: Long,
)

/** A folder inside the picked document tree. [id] is the document tree URI string. */
data class LogFolder(
    val id: String,
    val name: String,
    val path: String,       // display path e.g. "/OBD Logs/BMW"
)

data class FolderContents(
    val currentFolder: LogFolder,
    val parentFolder: LogFolder?,   // null = already at the picked root
    val subFolders: List<LogFolder>,
    val csvFiles: List<LogFile>,
)
