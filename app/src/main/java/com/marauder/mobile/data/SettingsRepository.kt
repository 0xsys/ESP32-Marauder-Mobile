package com.marauder.mobile.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/**
 * Persists the handful of app-level preferences (currently just the light/dark
 * theme choice) with Jetpack DataStore so they survive process death and reboot.
 */
class SettingsRepository(context: Context) {

    private val store = context.applicationContext.dataStore

    /** `true` = dark theme (the default). */
    val darkTheme: Flow<Boolean> = store.data.map { it[DARK_THEME] ?: true }

    suspend fun setDarkTheme(dark: Boolean) {
        store.edit { it[DARK_THEME] = dark }
    }

    private companion object {
        val DARK_THEME = booleanPreferencesKey("dark_theme")
    }
}
