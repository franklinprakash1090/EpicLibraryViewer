package com.franklinprakash.epiclibraryviewer

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import org.json.JSONObject
import java.util.Base64

object EpicAuthService {

    private const val TOKEN_URL =
        "https://account-public-service-prod.ol.epicgames.com/account/api/oauth/token"

    private const val CLIENT_ID = "34a02cf8f4414e29b15921876da36f9a"
    private const val CLIENT_SECRET = "secret"

    private val client = OkHttpClient()

    suspend fun exchangeCode(code: String): JSONObject? =
        withContext(Dispatchers.IO) {

            val auth = Base64.getEncoder()
                .encodeToString("$CLIENT_ID:$CLIENT_SECRET".toByteArray())

            val body = FormBody.Builder()
                .add("grant_type", "authorization_code")
                .add("code", code)
                .add("token_type", "eg1")
                .build()

            val request = Request.Builder()
                .url(TOKEN_URL)
                .post(body)
                .header("Authorization", "Basic $auth")
                .build()

            client.newCall(request).execute().use {
                if (!it.isSuccessful) return@withContext null
                JSONObject(it.body!!.string())
            }
        }
}
