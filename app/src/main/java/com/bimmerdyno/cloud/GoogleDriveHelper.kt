package com.bimmerdyno.cloud

import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream

class GoogleDriveHelper(private val context: Context) {

    companion object {
        const val REQUEST_SIGN_IN = 1001
        val ROOT_FOLDER = CloudFolder("root", "My Drive", "/")
        private const val MIME_FOLDER = "application/vnd.google-apps.folder"
    }

    private val signInClient: GoogleSignInClient by lazy {
        val options = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Scope(DriveScopes.DRIVE_READONLY))
            .build()
        GoogleSignIn.getClient(context, options)
    }

    fun getSignInIntent(): Intent = signInClient.signInIntent

    fun handleSignInResult(data: Intent?): Result<GoogleSignInAccount> = try {
        Result.success(GoogleSignIn.getSignedInAccountFromIntent(data).result)
    } catch (e: Exception) {
        Result.failure(e)
    }

    private fun driveService(account: GoogleSignInAccount): Drive {
        val credential = GoogleAccountCredential.usingOAuth2(
            context, listOf(DriveScopes.DRIVE_READONLY)
        ).apply { selectedAccount = account.account }
        return Drive.Builder(
            NetHttpTransport(),
            GsonFactory.getDefaultInstance(),
            credential
        ).setApplicationName("BimmerDyno").build()
    }

    // ── Folder navigation ───────────────────────────────────────────────────

    suspend fun listFolderContents(folder: CloudFolder): Result<CloudFolderContents> =
        withContext(Dispatchers.IO) {
            val account = GoogleSignIn.getLastSignedInAccount(context)
                ?: return@withContext Result.failure(Exception("Not signed in"))
            try {
                val drive = driveService(account)
                val folderId = folder.id

                // List subfolders
                val folderResult = drive.files().list()
                    .setQ("'$folderId' in parents and mimeType='$MIME_FOLDER' and trashed=false")
                    .setFields("files(id,name)")
                    .setPageSize(100)
                    .execute()
                val subFolders = folderResult.files.orEmpty().map { f ->
                    val childPath = if (folder.path == "/") "/${f.name}" else "${folder.path}/${f.name}"
                    CloudFolder(f.id, f.name, childPath)
                }

                // List CSV files
                val fileResult = drive.files().list()
                    .setQ("'$folderId' in parents and name contains '.csv' and trashed=false")
                    .setFields("files(id,name,size)")
                    .setPageSize(200)
                    .execute()
                val csvFiles = fileResult.files.orEmpty()
                    .filter { it.name.endsWith(".csv", ignoreCase = true) }
                    .map { CloudFile(it.id, it.name, it.getSize() ?: 0L) }

                // Get parent folder
                val parent: CloudFolder? = if (folderId == "root") null else {
                    val meta = drive.files().get(folderId)
                        .setFields("parents,name")
                        .execute()
                    val parentId = meta.parents?.firstOrNull() ?: "root"
                    val parentPath = folder.path.substringBeforeLast("/").ifEmpty { "/" }
                    val parentName = if (parentId == "root") "My Drive"
                    else parentPath.substringAfterLast("/")
                    CloudFolder(parentId, parentName, parentPath)
                }

                Result.success(CloudFolderContents(folder, parent, subFolders, csvFiles))
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    /** Navigate to folder by typed path, e.g. "/OBD Logs/BMW" */
    suspend fun folderByPath(path: String): Result<CloudFolder> = withContext(Dispatchers.IO) {
        val account = GoogleSignIn.getLastSignedInAccount(context)
            ?: return@withContext Result.failure(Exception("Not signed in"))
        try {
            val drive = driveService(account)
            val segments = path.trim().split("/").filter { it.isNotBlank() }
            var currentId = "root"
            var currentPath = "/"
            for (segment in segments) {
                val result = drive.files().list()
                    .setQ("'$currentId' in parents and name='$segment' and mimeType='$MIME_FOLDER' and trashed=false")
                    .setFields("files(id,name)")
                    .setPageSize(1)
                    .execute()
                val found = result.files.orEmpty().firstOrNull()
                    ?: return@withContext Result.failure(Exception("Folder not found: $segment"))
                currentId = found.id
                currentPath = if (currentPath == "/") "/$segment" else "$currentPath/$segment"
            }
            val name = if (segments.isEmpty()) "My Drive" else segments.last()
            Result.success(CloudFolder(currentId, name, currentPath))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ── File download ────────────────────────────────────────────────────────

    suspend fun downloadFile(fileId: String): Result<InputStream> = withContext(Dispatchers.IO) {
        val account = GoogleSignIn.getLastSignedInAccount(context)
            ?: return@withContext Result.failure(Exception("Not signed in"))
        try {
            val stream = driveService(account).files().get(fileId).executeMediaAsInputStream()
            Result.success(stream)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun signOut() { signInClient.signOut() }

    val isSignedIn: Boolean get() = GoogleSignIn.getLastSignedInAccount(context) != null
    val currentAccountEmail: String? get() = GoogleSignIn.getLastSignedInAccount(context)?.email
}
