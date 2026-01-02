package com.franklinprakash.epiclibraryviewer.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class EpicGame(
    val title: String,
    val id: String,
    val namespace: String,
    @SerialName("keyImages") val keyImages: List<KeyImage>
)

@Serializable
data class KeyImage(
    val type: String,
    val url: String
)
