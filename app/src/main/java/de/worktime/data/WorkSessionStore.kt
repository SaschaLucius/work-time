package de.worktime.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import de.worktime.domain.WorkTimeCalculator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.DayOfWeek
import java.time.LocalDate

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "work_session")

class WorkSessionStore internal constructor(
    private val dataStore: DataStore<Preferences>
) {

    constructor(context: Context) : this(context.dataStore)

    companion object {
        val KEY_START_TIME = longPreferencesKey("start_time_millis")
        val KEY_IS_RUNNING = booleanPreferencesKey("is_running")
        val KEY_SESSION_DATE = stringPreferencesKey("session_date")
        val KEY_TICK_COUNT = longPreferencesKey("tick_count")

        private val KEY_FIRST_BREAK_MINUTES = intPreferencesKey("first_break_minutes")
        private val KEY_SECOND_BREAK_MINUTES = intPreferencesKey("second_break_minutes")
        private val KEY_DAILY_TARGET_MINUTES = intPreferencesKey("daily_target_minutes")
        private val KEY_NOTIFICATIONS_ENABLED = booleanPreferencesKey("notifications_enabled")
        private val KEY_NOTIFICATION_OFFSET_MINUTES = intPreferencesKey("notification_offset_minutes")
        private val KEY_LAST_NOTIFICATION_DATE = stringPreferencesKey("last_notification_date")

        val WORK_DAYS = DayOfWeek.entries.filter { day ->
            day.value in DayOfWeek.MONDAY.value..DayOfWeek.FRIDAY.value
        }

        private fun startKey(day: DayOfWeek) =
            intPreferencesKey("${day.name.lowercase()}_start_minutes")

        private fun endKey(day: DayOfWeek) =
            intPreferencesKey("${day.name.lowercase()}_end_minutes")
    }

    data class WorkSession(
        val startTimeMillis: Long = -1L,
        val isRunning: Boolean = false,
        val sessionDate: String = "",
        val tickCount: Long = 0L
    )

    data class AppSettings(
        val firstBreakMinutes: Int = 30,
        val secondBreakMinutes: Int = 45,
        val dailyTargetMinutes: Int = 8 * 60,
        val notificationsEnabled: Boolean = false,
        val notificationOffsetMinutes: Int = 0,
        val lastNotificationDate: String = ""
    ) {
        val breakConfig: WorkTimeCalculator.BreakConfig
            get() = WorkTimeCalculator.BreakConfig(firstBreakMinutes, secondBreakMinutes)
    }

    data class WeekEntry(
        val startMinutes: Int? = null,
        val endMinutes: Int? = null
    ) {
        val hasValue: Boolean get() = startMinutes != null || endMinutes != null
    }

    val session: Flow<WorkSession> = dataStore.data.map { prefs ->
        WorkSession(
            startTimeMillis = prefs[KEY_START_TIME] ?: -1L,
            isRunning = prefs[KEY_IS_RUNNING] ?: false,
            sessionDate = prefs[KEY_SESSION_DATE] ?: "",
            tickCount = prefs[KEY_TICK_COUNT] ?: 0L
        )
    }

    val settings: Flow<AppSettings> = dataStore.data.map { prefs ->
        AppSettings(
            firstBreakMinutes = prefs[KEY_FIRST_BREAK_MINUTES] ?: 30,
            secondBreakMinutes = prefs[KEY_SECOND_BREAK_MINUTES] ?: 45,
            dailyTargetMinutes = prefs[KEY_DAILY_TARGET_MINUTES] ?: 8 * 60,
            notificationsEnabled = prefs[KEY_NOTIFICATIONS_ENABLED] ?: false,
            notificationOffsetMinutes = prefs[KEY_NOTIFICATION_OFFSET_MINUTES] ?: 0,
            lastNotificationDate = prefs[KEY_LAST_NOTIFICATION_DATE] ?: ""
        )
    }

    val weekEntries: Flow<Map<DayOfWeek, WeekEntry>> = dataStore.data.map { prefs ->
        WORK_DAYS.associateWith { day ->
            WeekEntry(
                startMinutes = prefs[startKey(day)],
                endMinutes = prefs[endKey(day)]
            )
        }
    }

    suspend fun startSession(startTimeMillis: Long) {
        dataStore.edit { prefs ->
            prefs[KEY_START_TIME] = startTimeMillis
            prefs[KEY_IS_RUNNING] = true
            prefs[KEY_SESSION_DATE] = LocalDate.now().toString()
        }
    }

    suspend fun stopSession() {
        dataStore.edit { prefs ->
            prefs[KEY_IS_RUNNING] = false
        }
    }

    suspend fun adjustStartTime(newStartTimeMillis: Long) {
        dataStore.edit { prefs ->
            prefs[KEY_START_TIME] = newStartTimeMillis
        }
    }

    suspend fun resetSession() {
        dataStore.edit { prefs ->
            prefs[KEY_START_TIME] = -1L
            prefs[KEY_IS_RUNNING] = false
            prefs[KEY_SESSION_DATE] = ""
            prefs[KEY_TICK_COUNT] = 0L
        }
    }

    /** Increments the tick counter so Glance recomposes and picks up the new current time. */
    suspend fun tickSession() {
        dataStore.edit { prefs ->
            prefs[KEY_TICK_COUNT] = (prefs[KEY_TICK_COUNT] ?: 0L) + 1L
        }
    }

    suspend fun updateBreakMinutes(firstBreakMinutes: Int, secondBreakMinutes: Int) {
        require(firstBreakMinutes in 0..180) { "First break must be between 0 and 180 minutes" }
        require(secondBreakMinutes in firstBreakMinutes..180) {
            "Second break must be between the first break and 180 minutes"
        }
        dataStore.edit { prefs ->
            prefs[KEY_FIRST_BREAK_MINUTES] = firstBreakMinutes
            prefs[KEY_SECOND_BREAK_MINUTES] = secondBreakMinutes
        }
    }

    suspend fun updateDailyTarget(minutes: Int) {
        require(minutes in 1..WorkTimeCalculator.MAX_NET_MINUTES) {
            "Daily target must be between 1 and ${WorkTimeCalculator.MAX_NET_MINUTES} minutes"
        }
        dataStore.edit { prefs ->
            prefs[KEY_DAILY_TARGET_MINUTES] = minutes
            val currentOffset = prefs[KEY_NOTIFICATION_OFFSET_MINUTES] ?: 0
            prefs[KEY_NOTIFICATION_OFFSET_MINUTES] = currentOffset.coerceAtMost(minutes)
        }
    }

    suspend fun updateNotificationsEnabled(enabled: Boolean) {
        dataStore.edit { prefs -> prefs[KEY_NOTIFICATIONS_ENABLED] = enabled }
    }

    suspend fun updateNotificationOffset(minutes: Int) {
        require(minutes >= 0) { "Notification offset must not be negative" }
        dataStore.edit { prefs ->
            val target = prefs[KEY_DAILY_TARGET_MINUTES] ?: 8 * 60
            require(minutes <= target) { "Notification offset must not exceed the daily target" }
            prefs[KEY_NOTIFICATION_OFFSET_MINUTES] = minutes
        }
    }

    suspend fun markNotificationShown(date: LocalDate) {
        dataStore.edit { prefs -> prefs[KEY_LAST_NOTIFICATION_DATE] = date.toString() }
    }

    suspend fun updateWeekStart(day: DayOfWeek, minutes: Int?) {
        updateWeekValue(day, minutes, isStart = true)
    }

    suspend fun updateWeekEnd(day: DayOfWeek, minutes: Int?) {
        updateWeekValue(day, minutes, isStart = false)
    }

    suspend fun resetWeek() {
        dataStore.edit { prefs ->
            WORK_DAYS.forEach { day ->
                prefs.remove(startKey(day))
                prefs.remove(endKey(day))
            }
        }
    }

    suspend fun resetWeekDay(day: DayOfWeek) {
        requireWorkDay(day)
        dataStore.edit { prefs ->
            prefs.remove(startKey(day))
            prefs.remove(endKey(day))
        }
    }

    suspend fun saveDayAndResetSession(
        day: DayOfWeek,
        startMinutes: Int,
        endMinutes: Int
    ) {
        requireWorkDay(day)
        requireMinutesSinceMidnight(startMinutes)
        requireMinutesSinceMidnight(endMinutes)
        dataStore.edit { prefs ->
            prefs[startKey(day)] = startMinutes
            prefs[endKey(day)] = endMinutes
            prefs[KEY_START_TIME] = -1L
            prefs[KEY_IS_RUNNING] = false
            prefs[KEY_SESSION_DATE] = ""
            prefs[KEY_TICK_COUNT] = 0L
        }
    }

    private suspend fun updateWeekValue(day: DayOfWeek, minutes: Int?, isStart: Boolean) {
        requireWorkDay(day)
        minutes?.let(::requireMinutesSinceMidnight)
        dataStore.edit { prefs ->
            val key = if (isStart) startKey(day) else endKey(day)
            if (minutes == null) prefs.remove(key) else prefs[key] = minutes
        }
    }

    private fun requireWorkDay(day: DayOfWeek) {
        require(day in WORK_DAYS) { "Weekly calculator supports Monday through Friday" }
    }

    private fun requireMinutesSinceMidnight(minutes: Int) {
        require(minutes in 0 until 24 * 60) { "Time must be within one day" }
    }
}
