package com.bimmerloganalyzer.cloud

import android.app.Activity
import android.content.Context
import com.microsoft.identity.client.*
import com.microsoft.identity.client.exception.MsalException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class CloudFile(val id: String, val name: String, val size: Long, val downloadUrl: String = "")

class OneDriveHelper(private val context: Context) {

    companion object {
        private val SCOPES = arrayOf("Files.Read", "Files.Read.All")
        private const val GRAPH_BASE = "https://graph.microsoft.com/v1.0"
    }

    private var msalApp: ISingleAccountPublicClientApplication? = null

    suspend fun initialize(): Result<Unit> = withContext(Dispatchers.IO) {
        suspendCancellableCoroutine { cont ->
            PublicClientApplication.createSingleAccountPublicClientApplication(
                context,
                R.raw.msal_config,
                object : IPublicClientApplication.ISingleAccountApplicationCreatedListener {
                    override fun onCreated(application: ISingleAccountPublicClientApplication) {
                        msalApp = application
                        cont.resume(Result.success(Unit))
                    }
                    override fun onError(exception: MsalException) {
                        cont.resume(Result.failure(exception))
                    }
                }
            )
        }
    }

    suspend fun signIn(activity: Activity): Result<String> = withContext(Dispatchers.Main) {
        val app = msalApp ?: return@withContext Result.failure(Exception("MSAL not initialized"))
        suspendCancellableCoroutine { cont ->
            app.signIn(activity, null, SCOPES, object : AuthenticationCallback {
                override fun onSuccess(result: IAuthenticationResult) {
                    cont.resume(Result.success(result.accessToken))
                }
                override fun onError(exception: MsalException) {
                    cont.resume(Result.failure(exception))
                }
                override fun onCancel() {
                    cont.resume(Result.failure(Exception("Sign-in cancelled")))
                }
            })
        }
    }

    suspend fun getToken(): Result<String> = withContext(Dispatchers.IO) {
        val app = msalApp ?: return@withContext Result.failure(Exception("MSAL not initialized"))
        suspendCancellableCoroutine { cont ->
            app.acquireTokenSilentAsync(SCOPES, app.currentAccount.currentAccount?.authority ?: "",
                object : SilentAuthenticationCallback {
                    override fun onSuccess(result: IAuthenticationResult) {
                        cont.resume(Result.success(result.accessToken))
                    }
                    override fun onError(exception: MsalException) {
                        cont.resume(Result.failure(exception))
                    }
                })
        }
    }

    suspend fun listCsvFiles(token: String, folderId: String? = null): Result<List<CloudFile>> =
        withContext(Dispatchers.IO) {
            try {
                val endpoint = if (folderId != null)
                    "$GRAPH_BASE/me/drive/items/$folderId/children?\$filter=endswith(name,'.csv')"
                else
                    "$GRAPH_BASE/me/drive/root/search(q='.csv')?\$select=id,name,size"
                val json = graphGet(endpoint, token)
                val items = json.getJSONArray("value")
                val files = (0 until items.length()).map { i ->
                    val item = items.getJSONObject(i)
                    CloudFile(
                        id = item.getString("id"),
                        name = item.getString("name"),
                        size = item.optLong("size", 0),
                    )
                }.filter { it.name.endsWith(".csv", ignoreCase = true) }
                Result.success(files)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun downloadFile(token: String, fileId: String): Result<InputStream> =
        withContext(Dispatchers.IO) {
            try {
                val url = URL("$GRAPH_BASE/me/drive/items/$fileId/content")
                val conn = url.openConnection() as HttpURLConnection
                conn.setRequestProperty("Authorization", "Bearer $token")
                conn.instanceFollowRedirects = true
                conn.connect()
                if (conn.responseCode == 200) {
                    Result.success(conn.inputStream)
                } else {
                    Result.failure(Exception("HTTP ${conn.responseCode}"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    private fun graphGet(endpoint: String, token: String): JSONObject {
        val url = URL(endpoint)
        val conn = url.openConnection() as HttpURLConnection
        conn.setRequestProperty("Authorization", "Bearer $token")
        conn.setRequestProperty("Accept", "application/json")
        conn.connect()
        val body = conn.inputStream.bufferedReader().readText()
        return JSONObject(body)
    }

    fun signOut() {
        msalApp?.signOut(object : ISingleAccountPublicClientApplication.SignOutCallback {
            override fun onSignOut() {}
            override fun onError(exception: MsalException) {}
        })
    }

    val isSignedIn: Boolean
        get() = msalApp?.currentAccount?.currentAccount != null
}
