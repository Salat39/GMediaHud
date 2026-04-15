package com.salat.gmediahud.datastore

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

class DataStoreManager(private val context: Context) {

    suspend fun <T> saveValue(key: Preferences.Key<T>, value: T) {
        context.dataStore.edit { preferences ->
            preferences[key] = value
        }
    }

    fun <T> getValueFlow(key: Preferences.Key<T>): Flow<T?> =
        context.dataStore.data
            .map { preferences -> preferences[key] }
            .distinctUntilChanged()

    fun <T> getValueFlow(key: Preferences.Key<T>, defaultValue: T): Flow<T> =
        context.dataStore.data
            .map { preferences -> preferences[key] ?: defaultValue }
            .distinctUntilChanged()

    suspend fun <T> removeValue(key: Preferences.Key<T>) {
        context.dataStore.edit { preferences ->
            preferences.remove(key)
        }
    }
}
