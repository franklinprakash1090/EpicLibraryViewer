package com.franklinprakash.epiclibraryviewer

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import org.json.JSONArray

object EpicLibraryService {

    private val client = OkHttpClient()

    suspend fun fetchLibrary(token: String): JSONArray? =
        withContext(Dispatchers.IO) {

            val request = Request.Builder()
                .url("https://library-service.live.use1a.on.epicgames.com/library/api/public/items")
                .header("Authorization", "Bearer $token")
                .build()

            client.newCall(request).execute().use {
                if (!it.isSuccessful) return@withContext null
                JSONArray(it.body!!.string())
            }
        }
}
