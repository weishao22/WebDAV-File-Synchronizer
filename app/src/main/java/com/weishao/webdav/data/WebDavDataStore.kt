package com.weishao.webdav.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "webdav_settings")

class WebDavDataStore(private val context: Context) {
    companion object {
        private val URL = stringPreferencesKey("url")
        private val USERNAME = stringPreferencesKey("username")
        private val PASSWORD = stringPreferencesKey("password")
        private val SYNC_CONFIGS = stringPreferencesKey("sync_configs")
        private val AUTO_UPLOAD_ENABLED = booleanPreferencesKey("auto_upload_enabled")
        private val UPLOAD_CONCURRENCY = intPreferencesKey("upload_concurrency")
        private val DOWNLOAD_PATH = stringPreferencesKey("download_path")
    }

    val configFlow: Flow<WebDavConfig> = context.dataStore.data.map { preferences ->
        val syncConfigsJson = preferences[SYNC_CONFIGS]
        val syncConfigs = try {
            if (syncConfigsJson != null) Json.decodeFromString<List<SyncConfig>>(syncConfigsJson)
            else emptyList()
        } catch (e: Exception) {
            emptyList()
        }

        WebDavConfig(
            url = preferences[URL] ?: "",
            username = preferences[USERNAME] ?: "",
            password = preferences[PASSWORD] ?: "",
            syncConfigs = syncConfigs,
            autoUploadEnabled = preferences[AUTO_UPLOAD_ENABLED] ?: false,
            uploadConcurrency = preferences[UPLOAD_CONCURRENCY] ?: 3,
            downloadPath = preferences[DOWNLOAD_PATH] ?: ""
        )
    }

    suspend fun saveConfig(config: WebDavConfig) {
        context.dataStore.edit { preferences ->
            preferences[URL] = config.url
            preferences[USERNAME] = config.username
            preferences[PASSWORD] = config.password
            preferences[SYNC_CONFIGS] = Json.encodeToString(config.syncConfigs)
            preferences[AUTO_UPLOAD_ENABLED] = config.autoUploadEnabled
            preferences[UPLOAD_CONCURRENCY] = config.uploadConcurrency
            preferences[DOWNLOAD_PATH] = config.downloadPath
        }
    }
}
