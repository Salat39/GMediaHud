package com.salat.gmediahud.datastore

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey

object Prefs {
    val MEDIA_DATA_SYNC_ENABLED = booleanPreferencesKey("MEDIA_DATA_SYNC_ENABLED")
    val UPDATE_RATE = intPreferencesKey("UPDATE_RATE")
    val FORCE_SYNC = booleanPreferencesKey("FORCE_SYNC")
    val MEDIA_SOURCE = intPreferencesKey("MEDIA_SOURCE")
    val UNKNOWN_ARTIST_STUB = booleanPreferencesKey("UNKNOWN_ARTIST_STUB")
    val NOTIFICATION_VOLUME = intPreferencesKey("NOTIFICATION_VOLUME2")
    val FILTER_BY_AUDIO_SOURCE = booleanPreferencesKey("FILTER_BY_AUDIO_SOURCE")
}
