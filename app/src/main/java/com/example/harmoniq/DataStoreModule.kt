package com.example.harmoniq

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey // ✅ Correct import
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

// ✅ Create a singleton instance of DataStore
val Context.dataStore by preferencesDataStore(name = "user_prefs")

object DataStoreModule {

    // ✅ Save API Key to DataStore
    suspend fun saveApiKey(context: Context, key: String, value: String) {
        val dataStoreKey = stringPreferencesKey(key) // ✅ Use correct function
        context.dataStore.edit { settings ->
            settings[dataStoreKey] = value
        }
    }

    // ✅ Read API Key from DataStore
    fun readApiKey(context: Context, key: String): Flow<String?> {
        val dataStoreKey = stringPreferencesKey(key) // ✅ Use correct function
        return context.dataStore.data.map { settings ->
            settings[dataStoreKey]
        }
    }
}
