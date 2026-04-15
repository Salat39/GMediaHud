package com.salat.gmediahud.components

import android.net.Uri

fun Uri.extractUuidFromFilename(): String? {
    val lastSegment = lastPathSegment ?: return null
    return lastSegment.substringAfterLast("/")
}
