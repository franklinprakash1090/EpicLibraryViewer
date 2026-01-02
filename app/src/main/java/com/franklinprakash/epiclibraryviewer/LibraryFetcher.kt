package com.franklinprakash.epiclibraryviewer

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

class LibraryFetcher(private val client: OkHttpClient, private val json: Json) {

    companion object {
        private const val TAG = "LibraryFetcher"
        private const val LAUNCHER_HOST = "launcher-public-service-prod06.ol.epicgames.com"
        private const val CATALOG_HOST = "catalog-public-service-prod06.ol.epicgames.com"
        private const val LIBRARY_HOST = "library-service.live.use1a.on.epicgames.com"
    }

    suspend fun fetchAssets(accessToken: String): List<GameAsset> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Fetching game assets...")

            val request = Request.Builder()
                .url("https://$LAUNCHER_HOST/launcher/api/public/assets/Windows?label=Live")
                .header("Authorization", "bearer $accessToken")
                .build()

            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: throw IOException("Empty response")

            if (!response.isSuccessful) {
                throw IOException("Failed to fetch assets: ${response.code}")
            }

            val jsonArray = json.parseToJsonElement(body).jsonArray

            val assets = jsonArray.mapNotNull { element ->
                try {
                    json.decodeFromJsonElement<GameAsset>(element)
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to parse asset", e)
                    null
                }
            }

            Log.d(TAG, "Fetched ${assets.size} assets")
            assets

        } catch (e: Exception) {
            Log.e(TAG, "Error fetching assets", e)
            emptyList()
        }
    }

    suspend fun fetchLibraryItems(accessToken: String): List<LibraryItem> = withContext(Dispatchers.IO) {
        val items = mutableListOf<LibraryItem>()
        var cursor: String? = null

        try {
            do {
                Log.d(TAG, "Fetching library page (cursor: ${cursor ?: "initial"})")

                val urlBuilder = HttpUrl.Builder()
                    .scheme("https")
                    .host(LIBRARY_HOST)
                    .addPathSegments("library/api/public/items")
                    .addQueryParameter("includeMetadata", "true")

                cursor?.let { urlBuilder.addQueryParameter("cursor", it) }

                val request = Request.Builder()
                    .url(urlBuilder.build())
                    .header("Authorization", "bearer $accessToken")
                    .build()

                val response = client.newCall(request).execute()
                val body = response.body?.string() ?: throw IOException("Empty response")

                if (!response.isSuccessful) {
                    throw IOException("Failed to fetch library: ${response.code}")
                }

                val jsonObj = json.parseToJsonElement(body).jsonObject
                val records = jsonObj["records"]?.jsonArray ?: break

                records.forEach { record ->
                    try {
                        items.add(json.decodeFromJsonElement(record))
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to parse library item", e)
                    }
                }

                cursor = jsonObj["responseMetadata"]?.jsonObject
                    ?.get("nextCursor")?.jsonPrimitive?.contentOrNull

            } while (cursor != null)

            Log.d(TAG, "Fetched ${items.size} library items")
            items

        } catch (e: Exception) {
            Log.e(TAG, "Error fetching library", e)
            items
        }
    }

    suspend fun fetchGameMetadata(
        accessToken: String,
        game: Game
    ): Game = withContext(Dispatchers.IO) {
        try {
            val url = "https://$CATALOG_HOST/catalog/api/shared/namespace/${game.namespace}/bulk/items" +
                    "?id=${game.catalogItemId}" +
                    "&includeDLCDetails=false" +
                    "&includeMainGameDetails=false" +
                    "&country=US" +
                    "&locale=en"

            val request = Request.Builder()
                .url(url)
                .header("Authorization", "bearer $accessToken")
                .build()

            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return@withContext game

            if (!response.isSuccessful) {
                Log.w(TAG, "Metadata fetch failed for ${game.catalogItemId}: ${response.code}")
                return@withContext game
            }

            val jsonObj = json.parseToJsonElement(body).jsonObject
            val itemData = jsonObj[game.catalogItemId]?.jsonObject ?: return@withContext game

            val description = itemData["description"]?.jsonPrimitive?.contentOrNull
            val developer = itemData["developer"]?.jsonPrimitive?.contentOrNull
            val keyImages = itemData["keyImages"]?.jsonArray?.mapNotNull { json.decodeFromJsonElement<KeyImage>(it) }

            val isDLC = itemData["categories"]?.jsonArray?.any {
                it.jsonObject["path"]?.jsonPrimitive?.content == "addons"
            } ?: false

            game.copy(
                description = description,
                developer = developer,
                keyImages = keyImages,
                isDLC = isDLC
            )

        } catch (e: Exception) {
            Log.w(TAG, "Error fetching metadata for ${game.catalogItemId}", e)
            game
        }
    }
}