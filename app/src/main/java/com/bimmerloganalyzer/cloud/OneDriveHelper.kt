package com.bimmerloganalyzer.cloud

import android.app.Activity
import android.content.Context
import com.bimmerloganalyzer.R
import com.microsoft.identity.client.*
import com.microsoft.identity.client.exception.MsalException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.resume

class OneDriveHelper(private val context: Context) {

    companion object {
        private val SCOPES = arrayOf("Files.Read", "Files.Read.All")
        private const val GRAPH = "https://graph.microsoft.com/v1.0"
        val ROOT_FOLDER = CloudFolder("root", "My Drive", "/")
    }

    private var msalApp: ISingleAccountPublicClientApplication? = null

    suspend fun initialize(): Result<Unit> = withContext(Dispatchers.IO) {
        suspendCancellableCoroutine { cont ->
            PublicClientApplication.createSingleAccountPublicClientApplication(
                context,
                R.raw.msal_config,
                object : IPublicClientApplication.ISingleAccountApplicationCreatedListener {
                    override fun onCreated(app: ISingleAccountPublicClientApplication) {
                        msalApp = app; cont.resume(Result.success(Unit))
                    }
                    override fun onError(e: MsalException) { cont.resume(Result.failure(e)) }
                }
            )
        }
    }

    suspend fun signIn(activity: Activity): Result<String> = withContext(Dispatchers.Main) {
        val app = msalApp ?: return@withContext Result.failure(Exception("MSAL not initialized"))
        suspendCancellableCoroutine { cont ->
            app.signIn(activity, null, SCOPES, object : AuthenticationCallback {
                override fun onSuccess(r: IAuthenticationResult) = cont.resume(Result.success(r.accessToken))
                override fun onError(e: MsalException) = cont.resume(Result.failure(e))
                override fun onCancel() = cont.resume(Result.failure(Exception("Cancelled")))
            })
        }
    }

    suspend fun getToken(): Result<String> = withContext(Dispatchers.IO) {
        val app = msalApp ?: return@withContext Result.failure(Exception("MSAL not initialized"))
        suspendCancellableCoroutine { cont ->
            app.acquireTokenSilentAsync(
                SCOPES,
                app.currentAccount.currentAccount?.authority ?: "",
                object : SilentAuthenticationCallback {
                    override fun onSuccess(r: IAuthenticationResult) = cont.resume(Result.success(r.accessToken))
                    override fun onError(e: MsalException) = cont.resume(Result.failure(e))
                }
            )
        }
    }

    // ── Folder navigation ───────────────────────────────────────────────────

    /** List contents of a folder: subfolders + CSV files */
    suspend fun listFolderContents(token: String, folder: CloudFolder): Result<CloudFolderContents> =
        withContext(Dispatchers.IO) {
            try {
                // /me/drive/items/{id}/children  or  /me/drive/root/children  for root
                val childrenUrl = if (folder.id == "root")
                    "$GRAPH/me/drive/root/children?\$select=id,name,size,folder,file&\$top=200"
                else
                    "$GRAPH/me/drive/items/${folder.id}/children?\$select=id,name,size,folder,file,parentReference&\$top=200"

                val json = graphGet(childrenUrl, token)
                val items = json.getJSONArray("value")

                val subFolders = mutableListOf<CloudFolder>()
                val csvFiles = mutableListOf<CloudFile>()

                for (i in 0 until items.length()) {
                    val item = items.getJSONObject(i)
                    val id = item.getString("id")
                    val name = item.getString("name")
                    val childPath = if (folder.path == "/") "/$name" else "${folder.path}/$name"

                    when {
                        item.has("folder") -> subFolders.add(CloudFolder(id, name, childPath))
                        item.has("file") && name.endsWith(".csv", ignoreCase = true) ->
                            csvFiles.add(CloudFile(id, name, item.optLong("size", 0)))
                    }
                }

                // Get parent folder info
                val parent: CloudFolder? = if (folder.id == "root") null else {
                    val itemJson = graphGet("$GRAPH/me/drive/items/${folder.id}?\$select=parentReference", token)
                    val parentRef = itemJson.optJSONObject("parentReference")
                    val parentId = parentRef?.optString("id") ?: "root"
                    val parentPath = parentRef?.optString("path")
                        ?.removePrefix("/drive/root:")
                        ?.ifEmpty { "/" } ?: "/"
                    val parentName = if (parentId == "root" || parentPath == "/") "My Drive"
                    else parentPath.substringAfterLast("/")
                    CloudFolder(parentId, parentName, parentPath.ifEmpty { "/" })
                }

                Result.success(CloudFolderContents(folder, parent, subFolders, csvFiles))
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    /** Navigate to folder by typed path, e.g. "/OBD Logs/BMW" */
    suspend fun folderByPath(token: String, path: String): Result<CloudFolder> =
        withContext(Dispatchers.IO) {
            try {
                val cleanPath = path.trim().trimStart('/')
                val url = if (cleanPath.isEmpty()) {
                    "$GRAPH/me/drive/root?\$select=id,name"
                } else {
                    "$GRAPH/me/drive/root:/$cleanPath?\$select=id,name"
                }
                val json = graphGet(url, token)
                // Normalize the drive root to the literal "root" id so downstream
                // `folder.id == "root"` guards (parent detection) behave correctly.
                val id = if (cleanPath.isEmpty()) "root" else json.getString("id")
                val name = if (cleanPath.isEmpty()) "My Drive" else json.getString("name")
                Result.success(CloudFolder(id, name, "/$cleanPath"))
            } catch (e: Exception) {
                Result.failure(Exception("Folder not found: $path"))
            }
        }

    // ── File download ────────────────────────────────────────────────────────

    suspend fun downloadFile(token: String, fileId: String): Result<InputStream> =
        withContext(Dispatchers.IO) {
            try {
                val url = URL("$GRAPH/me/drive/items/$fileId/content")
                val conn = url.openConnection() as HttpURLConnection
                conn.setRequestProperty("Authorization", "Bearer $token")
                conn.instanceFollowRedirects = true
                conn.connect()
                if (conn.responseCode == 200) Result.success(conn.inputStream)
                else Result.failure(Exception("HTTP ${conn.responseCode}"))
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    // ── Auth helpers ────────────────────────────────────────────────────────

    private fun graphGet(endpoint: String, token: String): JSONObject {
        val conn = URL(endpoint).openConnection() as HttpURLConnection
        conn.setRequestProperty("Authorization", "Bearer $token")
        conn.setRequestProperty("Accept", "application/json")
        conn.connect()
        return JSONObject(conn.inputStream.bufferedReader().readText())
    }

    fun signOut() {
        msalApp?.signOut(object : ISingleAccountPublicClientApplication.SignOutCallback {
            override fun onSignOut() {}
            override fun onError(e: MsalException) {}
        })
    }

    val isSignedIn: Boolean get() = msalApp?.currentAccount?.currentAccount != null
}
