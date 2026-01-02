package com.franklinprakash.epiclibraryviewer

import kotlinx.serialization.Serializable

@Serializable
data class UserData(
    val access_token: String,
    val refresh_token: String,
    val expires_at: String,
    val account_id: String,
    val displayName: String
)

@Serializable
data class GameAsset(
    val appName: String,
    val labelName: String,
    val buildVersion: String,
    val namespace: String,
    val catalogItemId: String
)

@Serializable
data class Game(
    val appName: String,
    val title: String,
    val namespace: String,
    val catalogItemId: String,
    val buildVersion: String? = null,
    val description: String? = null,
    val developer: String? = null,
    val keyImages: List<KeyImage>? = null,
    val isDLC: Boolean = false,
    val platform: String = "Windows"
)

@Serializable
data class KeyImage(
    val type: String,
    val url: String
)

@Serializable
data class LibraryItem(
    val appName: String,
    val namespace: String,
    val catalogItemId: String,
    val sandboxType: String? = null
)

sealed class AuthResult {
    data class Success(val displayName: String) : AuthResult()
    data class Error(val message: String) : AuthResult()
}

sealed class LibraryResult {
    data class Success(val games: List<Game>) : LibraryResult()
    data class Error(val message: String) : LibraryResult()
    data class Cached(val games: List<Game>, val lastSync: java.util.Date) : LibraryResult()
}