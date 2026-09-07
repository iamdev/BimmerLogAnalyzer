package com.bimmerdyno.cloud

data class CloudFile(
    val id: String,
    val name: String,
    val size: Long,
)

data class CloudFolder(
    val id: String,
    val name: String,
    val path: String,       // display path e.g. "/OBD Logs/BMW"
)

data class CloudFolderContents(
    val currentFolder: CloudFolder,
    val parentFolder: CloudFolder?,  // null = already at root
    val subFolders: List<CloudFolder>,
    val csvFiles: List<CloudFile>,
)
