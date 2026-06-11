package com.bimmerloganalyzer.cloud

import android.app.Activity
import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import com.google.api.client.extensions.android.http.AndroidHttp
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream

class GoogleDriveHelper(private val context: Context) {

    companion object {
        const val REQUEST_SIGN_IN = 1001
    }

    private val signInClient: GoogleSignInClient by lazy {
        val options = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Scope(DriveScopes.DRIVE_READONLY))
            .build()
        GoogleSignIn.getClient(context, options)
    }

    fun getSignInIntent(): Intent = signInClient.signInIntent

    fun handleSignInResult(data: Intent?): Result<GoogleSignInAccount> {
        return try {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            Result.success(task.result)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun getDriveService(account: GoogleSignInAccount): Drive {
        val credential = GoogleAccountCredential.usingOAuth2(
            context, listOf(DriveScopes.DRIVE_READONLY)
        ).apply { selectedAccount = account.account }
        return Drive.Builder(
            AndroidHttp.newCompatibleTransport(),
            GsonFactory.getDefaultInstance(),
            credential
        ).setApplicationName("BimmerLogAnalyzer").build()
    }

    suspend fun listCsvFiles(): Result<List<CloudFile>> = withContext(Dispatchers.IO) {
        val account = GoogleSignIn.getLastSignedInAccount(context)
            ?: return@withContext Result.failure(Exception("Not signed in"))
        try {
            val drive = getDriveService(account)
            val result = drive.files().list()
                .setQ("name contains '.csv' and mimeType='text/csv' and trashed=false")
                .setFields("files(id, name, size)")
                .setPageSize(100)
                .execute()
            val files = result.files.map { f ->
                CloudFile(
                    id = f.id,
                    name = f.name,
                    size = f.getSize() ?: 0L,
                )
            }
            Result.success(files)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun downloadFile(fileId: String): Result<InputStream> = withContext(Dispatchers.IO) {
        val account = GoogleSignIn.getLastSignedInAccount(context)
            ?: return@withContext Result.failure(Exception("Not signed in"))
        try {
            val drive = getDriveService(account)
            val stream = drive.files().get(fileId).executeMediaAsInputStream()
            Result.success(stream)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun signOut(activity: Activity) {
        signInClient.signOut()
    }

    val isSignedIn: Boolean
        get() = GoogleSignIn.getLastSignedInAccount(context) != null

    val currentAccountEmail: String?
        get() = GoogleSignIn.getLastSignedInAccount(context)?.email
}
