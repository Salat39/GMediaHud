package com.salat.gmediahud.entity

import android.graphics.Bitmap
import android.net.Uri

data class CoverBuffer(
    val id: String,
    val mediaUri: String,
    val fileUri: Uri?,
    val bitmap: Bitmap?,
    val status: LoadCoverStatus
)
