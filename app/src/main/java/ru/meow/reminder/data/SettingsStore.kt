package ru.meow.reminder.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.Json

private val Context.settingsStore by preferencesDataStore("settings")
val appJson = Json { ignoreUnknownKeys = true; encodeDefaults = true; prettyPrint = true }

class SettingsStore(private val context: Context) {
    private val key = stringPreferencesKey("app_settings")
    val flow: Flow<AppSettings> = context.settingsStore.data.map { prefs ->
        prefs[key]?.let { appJson.decodeFromString<AppSettings>(it) } ?: AppSettings()
    }
    suspend fun get() = flow.first()
    suspend fun update(change: (AppSettings) -> AppSettings) {
        context.settingsStore.edit { prefs ->
            val old = prefs[key]?.let { appJson.decodeFromString<AppSettings>(it) } ?: AppSettings()
            prefs[key] = appJson.encodeToString(change(old))
        }
    }
}
