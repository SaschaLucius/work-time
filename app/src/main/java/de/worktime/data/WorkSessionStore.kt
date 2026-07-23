package de.worktime.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "work_session")

class WorkSessionStore(private val context: Context) {

    companion object {
        val KEY_START_TIME = longPreferencesKey("start_time_millis")
        val KEY_IS_RUNNING = booleanPreferencesKey("is_running")
        val KEY_SESSION_DATE = stringPreferencesKey("session_date")
    }

    data class WorkSession(
        val startTimeMillis: Long = -1L,
        val isRunning: Boolean = false,
        val sessionDate: String = ""
    )

    val session: Flow<WorkSession> = context.dataStore.data.map { prefs ->
        WorkSession(
            startTimeMillis = prefs[KEY_START_TIME] ?: -1L,
            isRunning = prefs[KEY_IS_RUNNING] ?: false,
            sessionDate = prefs[KEY_SESSION_DATE] ?: ""
        )
    }

    suspend fun startSession(startTimeMillis: Long) {
        context.dataStore.edit { prefs ->
            prefs[KEY_START_TIME] = startTimeMillis
            prefs[KEY_IS_RUNNING] = true
            prefs[KEY_SESSION_DATE] = LocalDate.now().toString()
        }
    }

    suspend fun stopSession() {
        context.dataStore.edit { prefs ->
            prefs[KEY_IS_RUNNING] = false
        }
    }

    suspend fun adjustStartTime(newStartTimeMillis: Long) {
        context.dataStore.edit { prefs ->
            prefs[KEY_START_TIME] = newStartTimeMillis
        }
    }

    suspend fun resetSession() {
        context.dataStore.edit { prefs ->
            prefs[KEY_START_TIME] = -1L
            prefs[KEY_IS_RUNNING] = false
            prefs[KEY_SESSION_DATE] = ""
        }
    }
}
