package com.example.data.repository

import android.content.Context
import android.util.Log
import okhttp3.Headers
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.net.URLConnection
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

class GoogleDriveHelper(private val context: Context) {

    private val sharedPrefs = context.getSharedPreferences("google_drive_prefs", Context.MODE_PRIVATE)
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    // Configuration preferences
    fun getClientId(): String = sharedPrefs.getString("client_id", "") ?: ""
    fun setClientId(value: String) = sharedPrefs.edit().putString("client_id", value).apply()

    fun getClientSecret(): String = sharedPrefs.getString("client_secret", "") ?: ""
    fun setClientSecret(value: String) = sharedPrefs.edit().putString("client_secret", value).apply()

    fun getManualAccessToken(): String = sharedPrefs.getString("manual_access_token", "") ?: ""
    fun setManualAccessToken(value: String) = sharedPrefs.edit().putString("manual_access_token", value).apply()

    fun getRefreshToken(): String = sharedPrefs.getString("refresh_token", "") ?: ""
    fun setRefreshToken(value: String) = sharedPrefs.edit().putString("refresh_token", value).apply()

    fun getAccessTokenExpiry(): Long = sharedPrefs.getLong("access_token_expiry", 0L)
    fun setAccessTokenExpiry(value: Long) = sharedPrefs.edit().putLong("access_token_expiry", value).apply()

    fun getAccessToken(): String = sharedPrefs.getString("access_token", "") ?: ""
    fun setAccessToken(value: String) = sharedPrefs.edit().putString("access_token", value).apply()

    fun isLinked(): Boolean {
        return getManualAccessToken().isNotEmpty() || (getAccessToken().isNotEmpty() || getRefreshToken().isNotEmpty())
    }

    fun unlink() {
        sharedPrefs.edit()
            .remove("access_token")
            .remove("refresh_token")
            .remove("access_token_expiry")
            .remove("manual_access_token")
            .apply()
    }

    fun getAuthUrl(clientId: String): String {
        val redirectUri = URLEncoder.encode("aistudio-drive://oauth-callback", "UTF-8")
        val scope = URLEncoder.encode("https://www.googleapis.com/auth/drive.file", "UTF-8")
        return "https://accounts.google.com/o/oauth2/v2/auth?" +
                "client_id=$clientId&" +
                "redirect_uri=$redirectUri&" +
                "response_type=code&" +
                "scope=$scope&" +
                "access_type=offline&" +
                "prompt=consent"
    }

    suspend fun exchangeCodeForTokens(code: String, clientId: String, clientSecret: String): Boolean {
        try {
            val bodyContent = "code=${URLEncoder.encode(code, "UTF-8")}&" +
                    "client_id=${URLEncoder.encode(clientId, "UTF-8")}&" +
                    "client_secret=${URLEncoder.encode(clientSecret, "UTF-8")}&" +
                    "redirect_uri=${URLEncoder.encode("aistudio-drive://oauth-callback", "UTF-8")}&" +
                    "grant_type=authorization_code"

            val request = Request.Builder()
                .url("https://oauth2.googleapis.com/token")
                .post(bodyContent.toRequestBody("application/x-www-form-urlencoded".toMediaTypeOrNull()))
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e("GoogleDriveHelper", "Code exchange failed: ${response.code} ${response.message}")
                    return false
                }
                val bodyStr = response.body?.string() ?: return false
                val json = JSONObject(bodyStr)
                val accessToken = json.optString("access_token", "")
                val refreshToken = json.optString("refresh_token", "")
                val expiresIn = json.optLong("expires_in", 3600L)

                if (accessToken.isNotEmpty()) {
                    setAccessToken(accessToken)
                    if (refreshToken.isNotEmpty()) {
                        setRefreshToken(refreshToken)
                    }
                    setAccessTokenExpiry(System.currentTimeMillis() + (expiresIn * 1000))
                    setClientId(clientId)
                    setClientSecret(clientSecret)
                    return true
                }
            }
        } catch (e: Exception) {
            Log.e("GoogleDriveHelper", "Error exchanging code", e)
        }
        return false
    }

    suspend fun ensureValidAccessToken(): String? {
        // If manual token is supplied, use it directly (bypass expiration calculations)
        val manualToken = getManualAccessToken().trim()
        if (manualToken.isNotEmpty()) {
            return manualToken
        }

        val refreshToken = getRefreshToken()
        val clientId = getClientId()
        val clientSecret = getClientSecret()

        if (refreshToken.isEmpty() || clientId.isEmpty() || clientSecret.isEmpty()) {
            val curToken = getAccessToken()
            return if (curToken.isNotEmpty()) curToken else null
        }

        val expiry = getAccessTokenExpiry()
        // If token expires in less than 5 minutes (300 seconds), refresh now
        if (System.currentTimeMillis() + 300_000 >= expiry) {
            Log.d("GoogleDriveHelper", "Access token expired or close to expiry, refreshing...")
            try {
                val bodyContent = "refresh_token=${URLEncoder.encode(refreshToken, "UTF-8")}&" +
                        "client_id=${URLEncoder.encode(clientId, "UTF-8")}&" +
                        "client_secret=${URLEncoder.encode(clientSecret, "UTF-8")}&" +
                        "grant_type=refresh_token"

                val request = Request.Builder()
                    .url("https://oauth2.googleapis.com/token")
                    .post(bodyContent.toRequestBody("application/x-www-form-urlencoded".toMediaTypeOrNull()))
                    .build()

                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val bodyStr = response.body?.string() ?: return null
                        val json = JSONObject(bodyStr)
                        val newAccessToken = json.optString("access_token", "")
                        val expiresIn = json.optLong("expires_in", 3600L)
                        if (newAccessToken.isNotEmpty()) {
                            setAccessToken(newAccessToken)
                            setAccessTokenExpiry(System.currentTimeMillis() + (expiresIn * 1000))
                            Log.d("GoogleDriveHelper", "Successfully refreshed Access Token.")
                            return newAccessToken
                        }
                    } else {
                        Log.e("GoogleDriveHelper", "Token refresh failed: ${response.code} ${response.message}")
                    }
                }
            } catch (e: Exception) {
                Log.e("GoogleDriveHelper", "Exception during token refresh", e)
            }
        }

        val curToken = getAccessToken()
        return if (curToken.isNotEmpty()) curToken else null
    }

    suspend fun searchFile(name: String, accessToken: String): String? {
        try {
            val query = URLEncoder.encode("name = '$name' and trashed = false", "UTF-8")
            val url = "https://www.googleapis.com/drive/v3/files?q=$query&fields=files(id,name)"

            val request = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $accessToken")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e("GoogleDriveHelper", "Search failed: ${response.code} ${response.message}")
                    return null
                }
                val bodyStr = response.body?.string() ?: return null
                val json = JSONObject(bodyStr)
                val files = json.optJSONArray("files")
                if (files != null && files.length() > 0) {
                    return files.getJSONObject(0).optString("id", null)
                }
            }
        } catch (e: Exception) {
            Log.e("GoogleDriveHelper", "Search exception", e)
        }
        return null
    }

    suspend fun downloadFileContent(fileId: String, accessToken: String): String? {
        try {
            val url = "https://www.googleapis.com/drive/v3/files/$fileId?alt=media"

            val request = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $accessToken")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    return response.body?.string()
                } else {
                    Log.e("GoogleDriveHelper", "Download failed: ${response.code} ${response.message}")
                }
            }
        } catch (e: Exception) {
            Log.e("GoogleDriveHelper", "Download exception", e)
        }
        return null
    }

    suspend fun createNewFile(name: String, content: String, accessToken: String): Boolean {
        try {
            val metadata = JSONObject()
            metadata.put("name", name)

            val mimeType = URLConnection.guessContentTypeFromName(name) ?: "text/plain"

            // Construct multipart/related manually for perfect reliability and zero dependency bugs
            val boundary = "==AISTUDIO_DRIVE_BOUNDARY=="
            val metadataPart = "--$boundary\r\n" +
                    "Content-Type: application/json; charset=UTF-8\r\n\r\n" +
                    metadata.toString() + "\r\n"

            val mediaPart = "--$boundary\r\n" +
                    "Content-Type: $mimeType\r\n\r\n" +
                    content + "\r\n" +
                    "--$boundary--"

            val payload = metadataPart + mediaPart

            val request = Request.Builder()
                .url("https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart")
                .header("Authorization", "Bearer $accessToken")
                .header("Content-Type", "multipart/related; boundary=$boundary")
                .post(payload.toRequestBody("multipart/related; boundary=$boundary".toMediaTypeOrNull()))
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    Log.d("GoogleDriveHelper", "Successfully created file '$name' on Google Drive.")
                    return true
                } else {
                    Log.e("GoogleDriveHelper", "Create file failed: ${response.code} ${response.body?.string()}")
                }
            }
        } catch (e: Exception) {
            Log.e("GoogleDriveHelper", "Create file exception", e)
        }
        return false
    }

    suspend fun updateExistingFile(fileId: String, content: String, accessToken: String): Boolean {
        try {
            val request = Request.Builder()
                .url("https://www.googleapis.com/upload/drive/v3/files/$fileId?uploadType=media")
                .header("Authorization", "Bearer $accessToken")
                .put(content.toRequestBody("text/plain".toMediaTypeOrNull()))
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    Log.d("GoogleDriveHelper", "Successfully updated file with ID '$fileId' on Google Drive.")
                    return true
                } else {
                    Log.e("GoogleDriveHelper", "Update file failed: ${response.code} ${response.body?.string()}")
                }
            }
        } catch (e: Exception) {
            Log.e("GoogleDriveHelper", "Update file exception", e)
        }
        return false
    }

    suspend fun syncFileToDrive(name: String, content: String): Boolean {
        val accessToken = ensureValidAccessToken() ?: return false
        val existingId = searchFile(name, accessToken)
        return if (existingId != null) {
            updateExistingFile(existingId, content, accessToken)
        } else {
            createNewFile(name, content, accessToken)
        }
    }

    suspend fun fetchFileFromDrive(name: String): String? {
        val accessToken = ensureValidAccessToken() ?: return null
        val existingId = searchFile(name, accessToken) ?: return null
        return downloadFileContent(existingId, accessToken)
    }
}
