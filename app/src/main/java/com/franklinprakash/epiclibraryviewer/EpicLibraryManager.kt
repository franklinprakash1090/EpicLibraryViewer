package com.franklinprakash.epiclibraryviewer

import android.content.Context
import android.util.Log
import androidx.core.content.edit
import java.util.Date
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

class EpicLibraryManager(context: Context) {

    companion object {
        private const val TAG = "EpicLibraryManager"
        private const val PREFS_NAME = "epic_games_prefs"
        private const val KEY_CACHED_GAMES = "cached_games"
        private const val KEY_LAST_SYNC = "last_sync"
        private const val CACHE_DURATION_HOURS = 6
    }

    private val apiClient = EpicApiClient(context)
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val libraryFetcher = LibraryFetcher(apiClient.client, json)

    suspend fun initialize(): Boolean {
        return apiClient.initialize()
    }

    fun getAuthorizationUrl(): String {
        return apiClient.getAuthorizationUrl()
    }

    suspend fun authenticateWithCode(code: String): AuthResult {
        return apiClient.authenticateWithCode(code)
    }

    fun isLoggedIn(): Boolean = apiClient.isLoggedIn()

    fun getDisplayName(): String? = apiClient.getDisplayName()

    suspend fun fetchLibrary(forceRefresh: Boolean = false): LibraryResult = withContext(Dispatchers.IO) {
        try {
            if (!forceRefresh) {
                val cached = getCachedLibrary()
                if (cached != null && isCacheValid()) {
                    val lastSync = Date(prefs.getLong(KEY_LAST_SYNC, 0))
                    Log.d(TAG, "Using cached library (${cached.size} games)")
                    return@withContext LibraryResult.Cached(cached, lastSync)
                }
            }

            val token = apiClient.getAccessToken()
                ?: return@withContext LibraryResult.Error("Not authenticated")

            Log.d(TAG, "Fetching library from Epic Games...")

            val assets = libraryFetcher.fetchAssets(token)
            val libraryItems = libraryFetcher.fetchLibraryItems(token)

            val combinedItems = (assets.map { it.toLibraryItem() } + libraryItems)
                .distinctBy { it.appName }

            val games = combinedItems.map {
                val game = Game(
                    appName = it.appName,
                    title = it.appName,
                    namespace = it.namespace,
                    catalogItemId = it.catalogItemId
                )
                libraryFetcher.fetchGameMetadata(token, game)
            }.filter { !it.isDLC }.sortedBy { it.title.lowercase() }

            Log.d(TAG, "Fetched ${games.size} games")

            cacheLibrary(games)

            LibraryResult.Success(games)

        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch library", e)

            getCachedLibrary()?.let { cached ->
                val lastSync = Date(prefs.getLong(KEY_LAST_SYNC, 0))
                LibraryResult.Cached(cached, lastSync)
            } ?: LibraryResult.Error(e.message ?: "Unknown error")
        }
    }

    private fun GameAsset.toLibraryItem() = LibraryItem(
        appName = appName,
        namespace = namespace,
        catalogItemId = catalogItemId
    )

    private fun cacheLibrary(games: List<Game>) {
        try {
            val serializer = ListSerializer(Game.serializer())
            val jsonString = json.encodeToString(serializer, games)
            prefs.edit {
                putString(KEY_CACHED_GAMES, jsonString)
                putLong(KEY_LAST_SYNC, System.currentTimeMillis())
            }
            Log.d(TAG, "Cached ${games.size} games")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to cache library", e)
        }
    }

    private fun getCachedLibrary(): List<Game>? {
        return try {
            val cached = prefs.getString(KEY_CACHED_GAMES, null) ?: return null
            val serializer = ListSerializer(Game.serializer())
            json.decodeFromString(serializer, cached)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load cached library", e)
            null
        }
    }

    private fun isCacheValid(): Boolean {
        val lastSync = prefs.getLong(KEY_LAST_SYNC, 0)
        if (lastSync == 0L) return false

        val hoursSinceSync = (System.currentTimeMillis() - lastSync) / (1000 * 60 * 60)
        return hoursSinceSync < CACHE_DURATION_HOURS
    }
}
