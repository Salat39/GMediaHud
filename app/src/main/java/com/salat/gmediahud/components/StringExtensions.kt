package com.salat.gmediahud.components

import java.security.MessageDigest
import java.util.UUID

fun String.generateFileId(): String {
    val digest = MessageDigest.getInstance("MD5") // Or "SHA-256" if needed
    val hashBytes = digest.digest(this.toByteArray())
    return hashBytes.joinToString("") { "%02x".format(it) } //.take(16) // Truncate to 16 characters
}

fun String.generateUuid(): String {
    return UUID.nameUUIDFromBytes(this.toByteArray(Charsets.UTF_8)).toString()
}
