package com.sidekeys.hibreak.core.data

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.sidekeys.hibreak.core.model.KeyMapping
import com.sidekeys.hibreak.core.model.KeySettings
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

interface MappingRepository {
    val mappings: Flow<List<KeyMapping>>
    val settings: Flow<KeySettings>
    suspend fun saveMapping(mapping: KeyMapping)
    suspend fun deleteMapping(keyCode: Int)
    suspend fun saveSettings(settings: KeySettings)
}

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(
    name = "sidekeys",
    // A corrupt preferences file (power loss mid-write) is replaced instead of
    // crash-looping the accessibility service.
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
)

class DataStoreMappingRepository(context: Context) : MappingRepository {

    private val store = context.applicationContext.dataStore
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val mappingListSerializer = ListSerializer(KeyMapping.serializer())

    private val mappingsKey = stringPreferencesKey("mappings")
    private val settingsKey = stringPreferencesKey("settings")

    private val safeData = store.data.catch { e ->
        if (e is IOException) {
            Log.e("SideKeys", "DataStore read failed", e)
            emit(emptyPreferences())
        } else {
            throw e
        }
    }

    override val mappings: Flow<List<KeyMapping>> = safeData.map { prefs ->
        prefs[mappingsKey]?.let { raw ->
            runCatching { json.decodeFromString(mappingListSerializer, raw) }.getOrDefault(emptyList())
        } ?: emptyList()
    }

    override val settings: Flow<KeySettings> = safeData.map { prefs ->
        prefs[settingsKey]?.let { raw ->
            runCatching { json.decodeFromString(KeySettings.serializer(), raw) }.getOrDefault(KeySettings())
        } ?: KeySettings()
    }

    override suspend fun saveMapping(mapping: KeyMapping) {
        safeEdit { prefs ->
            val current = prefs[mappingsKey]?.let { raw ->
                runCatching { json.decodeFromString(mappingListSerializer, raw) }.getOrDefault(emptyList())
            } ?: emptyList()
            val updated = current.filterNot { it.keyCode == mapping.keyCode } + mapping
            prefs[mappingsKey] = json.encodeToString(mappingListSerializer, updated.sortedBy { it.keyCode })
        }
    }

    override suspend fun deleteMapping(keyCode: Int) {
        safeEdit { prefs ->
            val current = prefs[mappingsKey]?.let { raw ->
                runCatching { json.decodeFromString(mappingListSerializer, raw) }.getOrDefault(emptyList())
            } ?: emptyList()
            prefs[mappingsKey] = json.encodeToString(mappingListSerializer, current.filterNot { it.keyCode == keyCode })
        }
    }

    override suspend fun saveSettings(settings: KeySettings) {
        safeEdit { prefs ->
            prefs[settingsKey] = json.encodeToString(KeySettings.serializer(), settings)
        }
    }

    private suspend fun safeEdit(transform: suspend (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        try {
            store.edit(transform)
        } catch (e: IOException) {
            Log.e("SideKeys", "DataStore write failed", e)
        }
    }
}

/** Tiny service locator — the app is single-module and too small for Hilt. */
object Graph {
    @Volatile
    private var repository: MappingRepository? = null

    fun mappingRepository(context: Context): MappingRepository =
        repository ?: synchronized(this) {
            repository ?: DataStoreMappingRepository(context.applicationContext).also { repository = it }
        }
}
