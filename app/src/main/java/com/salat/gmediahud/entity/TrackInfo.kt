package com.salat.gmediahud.entity

import android.net.Uri

data class TrackInfo(
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val cover: Uri? = null,
    val isPlaying: Boolean = false,
    val sourceType: Int = -1, // -1 = auto
    val progress: Long = -1L, // -1 = auto
    val maxProgress: Long = -1L, // -1 = auto
)
