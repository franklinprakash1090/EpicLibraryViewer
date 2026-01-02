package com.franklinprakash.epiclibraryviewer

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.core.content.edit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import okhttp3.Credentials
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

class EpicApiClient(context: Context) {

    companion object {
        private const val TAG = "EpicApiClient"
        private const val PREFS_NAME = "epic_games_prefs"
        private const val KEY_USER_DATA = "user_data"
        private const val OAUTH_HOST = "account-public-service-prod03.ol.epicgames.com"
        private const val CLIENT_ID = "34a02cf8f4414e29b15921876da36f9a"
        private const val CLIENT_SECRET = "daafbccc737745039dffe53d94fc76cf"
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private var accessToken: String? = null
    private var userData: UserData? = null

    suspend fun initialize(): Boolean = withContext(Dispatchers.IO) {
        try {
            val savedData = prefs.getString(KEY_USER_DATA, null) ?: return@withContext false
            userData = json.decodeFromString<UserData>(savedData)

            if (isTokenExpired()) {
                Log.d(TAG, "Token expired, attempting refresh...")
                return@withContext refreshToken()
            }

            accessToken = userData?.access_token
            Log.d(TAG, "Session restored successfully")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to restore session", e)
            false
        }
    }

    fun getAuthorizationUrl(): String {
        return "https://www.epicgames.com/id/api/redirect?clientId=$CLIENT_ID&responseType=code"
    }

    suspend fun authenticateWithCode(authCode: String): AuthResult = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Exchanging auth code for tokens...")

            val formBody = FormBody.Builder()
                .add("grant_type", "authorization_code")
                .add("code", authCode)
                .add("token_type", "eg1")
                .build()

            val request = Request.Builder()
                .url("https://$OAUTH_HOST/account/api/oauth/token")
                .post(formBody)
                .header("Authorization", Credentials.basic(CLIENT_ID, CLIENT_SECRET))
                .build()

            val response = client.newCall(request).execute()
            val body = response.body.string()

            if (!response.isSuccessful) {
                Log.e(TAG, "Auth failed: ${response.code} - $body")
                val errorMsg = try {
                    val errorJson = Json.parseToJsonElement(body).jsonObject
                    errorJson["error_description"]?.toString()?.removeSurrounding("\"") ?: "Unknown error"
                } catch (_: Exception) {
                    "Authentication failed (${response.code})"
                }
                return@withContext AuthResult.Error(errorMsg)
            }

            val authData = json.decodeFromString<UserData>(body)
            userData = authData
            accessToken = authData.access_token

            prefs.edit {
                putString(KEY_USER_DATA, json.encodeToString(authData))
            }

            Log.d(TAG, "Authentication successful: ${authData.displayName}")
            AuthResult.Success(authData.displayName)

        } catch (e: Exception) {
            Log.e(TAG, "Authentication error", e)
            AuthResult.Error(e.message ?: "Unknown error")
        }
    }

    private suspend fun refreshToken(): Boolean = withContext(Dispatchers.IO) {
        try {
            val currentData = userData ?: return@withContext false
            Log.d(TAG, "Refreshing access token...")

            val formBody = FormBody.Builder()
                .add("grant_type", "refresh_token")
                .add("refresh_token", currentData.refresh_token)
                .add("token_type", "eg1")
                .build()

            val request = Request.Builder()
                .url("https://$OAUTH_HOST/account/api/oauth/token")
                .post(formBody)
                .header("Authorization", Credentials.basic(CLIENT_ID, CLIENT_SECRET))
                .build()

            val response = client.newCall(request).execute()
            val body = response.body.string()

            if (response.isSuccessful) {
                val authData = json.decodeFromString<UserData>(body)
                userData = authData
                accessToken = authData.access_token

                prefs.edit {
                    putString(KEY_USER_DATA, json.encodeToString(authData))
                }

                Log.d(TAG, "Token refreshed successfully")
                true
            } else {
                Log.e(TAG, "Token refresh failed: ${response.code}")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Token refresh error", e)
            false
        }
    }

    private fun isTokenExpired(): Boolean {
        val expiresAt = userData?.expires_at ?: return true
        try {
            val format = SimpleDateFormat(
                "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
                Locale.US
            )
            val expiryDate = format.parse(expiresAt) ?: return true
            val tenMinutes = 10 * 60 * 1000
            return System.currentTimeMillis() > (expiryDate.time - tenMinutes)
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing expiry date", e)
            return true
        }
    }

    fun isLoggedIn(): Boolean = userData != null && accessToken != null
    fun getDisplayName(): String? = userData?.displayName
    fun getAccessToken(): String? = accessToken

    fun logout() {
        prefs.edit { clear() }
        userData = null
        accessToken = null
        Log.d(TAG, "User logged out")
    }
}