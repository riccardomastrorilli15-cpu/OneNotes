package com.onenotes.app.data.export

import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import okio.BufferedSink
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

/**
 * Backup on Google Drive, implemented with the Drive REST API v3 directly.
 *
 * Why not the Drive SDK: the old "Drive Android API" has been shut down by Google, while the
 * full Java client library would add several megabytes of dependencies. A couple of OkHttp
 * calls against `appDataFolder` do exactly what we need:
 * the backup files are hidden from the user's Drive and are removed when the app is uninstalled.
 */
class DriveBackupClient(private val context: Context) {

    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .build()

    private val options: GoogleSignInOptions = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
        .requestEmail()
        .requestScopes(Scope(DRIVE_SCOPE))
        .build()

    private val signInClient: GoogleSignInClient get() = GoogleSignIn.getClient(context, options)

    fun isSignedIn(): Boolean = GoogleSignIn.getLastSignedInAccount(context) != null

    fun accountEmail(): String? = GoogleSignIn.getLastSignedInAccount(context)?.email

    fun signInIntent(): Intent = signInClient.signInIntent

    /** Call it with the data of the ActivityResult of [signInIntent]. */
    fun handleSignInResult(success: Boolean): Boolean = success && isSignedIn()

    suspend fun signOut() = withContext(Dispatchers.IO) {
        runCatching { signInClient.signOut() }
        runCatching { signInClient.revokeAccess() }
    }

    /** Uploads [file] in the hidden app folder of Drive. Returns the id of the file. */
    suspend fun upload(file: File): String? = withContext(Dispatchers.IO) {
        val token = token() ?: return@withContext null
        val metadata = JSONObject().apply {
            put("name", file.name)
            put("parents", JSONArray().put("appDataFolder"))
            put("mimeType", "application/json")
        }.toString()

        val body = MultipartUploadBody(metadata, file)
        val request = Request.Builder()
            .url(UPLOAD_URL)
            .addHeader("Authorization", "Bearer $token")
            .post(body)
            .build()

        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@use null
            val json = JSONObject(response.body?.string().orEmpty())
            json.optString("id").takeIf { it.isNotBlank() }
        }
    }

    /** Metadata (id, name, timestamp) of the most recent backup. */
    suspend fun latestBackup(): DriveFile? = withContext(Dispatchers.IO) {
        val token = token() ?: return@withContext null
        val request = Request.Builder()
            .url(LIST_URL)
            .addHeader("Authorization", "Bearer $token")
            .get()
            .build()

        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@use null
            val files = JSONObject(response.body?.string().orEmpty()).optJSONArray("files")
            val first = files?.optJSONObject(0) ?: return@use null
            DriveFile(
                id = first.optString("id"),
                name = first.optString("name"),
                modifiedTime = first.optString("modifiedTime")
            )
        }
    }

    /** Downloads the most recent backup into the cache of the app. */
    suspend fun download(file: DriveFile): File? = withContext(Dispatchers.IO) {
        val token = token() ?: return@withContext null
        val request = Request.Builder()
            .url("$FILES_URL/${file.id}?alt=media")
            .addHeader("Authorization", "Bearer $token")
            .get()
            .build()

        http.newCall(request).execute().use { response: Response ->
            if (!response.isSuccessful) return@use null
            val target = File(context.cacheDir, "drive-${file.name}")
            response.body?.byteStream()?.use { input ->
                FileOutputStream(target).use { output -> input.copyTo(output) }
            } ?: return@use null
            target
        }
    }

    /** Access token of the signed in account, or null when the user has to sign in again. */
    private suspend fun token(): String? = withContext(Dispatchers.IO) {
        val account = GoogleSignIn.getLastSignedInAccount(context) ?: return@withContext null
        val email = account.email ?: return@withContext null
        runCatching {
            GoogleAuthUtil.getToken(context, email, "oauth2:$DRIVE_SCOPE")
        }.getOrNull()
    }

    /** Hand written multipart/related body: exactly what the Drive upload endpoint expects. */
    private class MultipartUploadBody(
        private val metadata: String,
        private val file: File
    ) : RequestBody() {

        private val boundary = "onenotes-boundary-${System.currentTimeMillis()}"

        override fun contentType() = "multipart/related; boundary=$boundary".toMediaType()

        override fun contentLength(): Long = -1L

        override fun writeTo(sink: BufferedSink) {
            sink.writeUtf8("--$boundary\r\n")
            sink.writeUtf8("Content-Type: application/json; charset=UTF-8\r\n\r\n")
            sink.writeUtf8(metadata)
            sink.writeUtf8("\r\n--$boundary\r\n")
            sink.writeUtf8("Content-Type: application/json; charset=UTF-8\r\n\r\n")
            file.inputStream().use { input ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    sink.write(buffer, 0, read)
                }
            }
            sink.writeUtf8("\r\n--$boundary--\r\n")
        }
    }

    data class DriveFile(
        val id: String,
        val name: String,
        val modifiedTime: String
    )

    companion object {
        const val DRIVE_SCOPE = "https://www.googleapis.com/auth/drive.appdata"
        const val BACKUP_PREFIX = "onenotes-backup"

        private const val FILES_URL = "https://www.googleapis.com/drive/v3/files"
        private const val UPLOAD_URL = "https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart&fields=id"
        private const val LIST_URL =
            "https://www.googleapis.com/drive/v3/files?spaces=appDataFolder" +
                "&fields=files(id,name,modifiedTime)&orderBy=modifiedTime%20desc&pageSize=5"
    }
}
