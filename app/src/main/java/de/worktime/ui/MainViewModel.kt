package de.worktime.ui

import android.app.AlarmManager
import android.app.Application
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.glance.appwidget.updateAll
import de.worktime.MidnightResetReceiver
import de.worktime.cancelTargetNotification
import de.worktime.scheduleTargetNotification
import de.worktime.data.WorkSessionStore
import de.worktime.domain.WorkTimeCalculator
import de.worktime.widget.WorkTimeWidget
import de.worktime.widget.cancelWidgetTick
import de.worktime.widget.scheduleWidgetTick
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId

data class TimerUiState(
    val isRunning: Boolean = false,
    val startTimeMillis: Long = -1L,
    val netMinutes: Int = 0,
    val grossMinutes: Int = 0,
    val requiredBreakMinutes: Int = 0,
    val endDayError: String? = null,
    val settings: WorkSessionStore.AppSettings = WorkSessionStore.AppSettings(),
    val weekEntries: Map<DayOfWeek, WorkSessionStore.WeekEntry> =
        WorkSessionStore.WORK_DAYS.associateWith { WorkSessionStore.WeekEntry() }
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val store = WorkSessionStore(application)
    private val _state = MutableStateFlow(TimerUiState())
    val state: StateFlow<TimerUiState> = _state.asStateFlow()
    private var tickJob: Job? = null

    init {
        viewModelScope.launch {
            store.session.collect { session ->
                // Prüfen ob das gespeicherte Datum noch heute ist
                if (session.isRunning && session.sessionDate != LocalDate.now().toString()) {
                    store.resetSession()
                    cancelTargetNotification(getApplication())
                    return@collect
                }
                // Extern gestartete Session aufnehmen (z. B. vom Widget)
                if (session.isRunning && session.startTimeMillis > 0 && !_state.value.isRunning) {
                    _state.update { it.copy(isRunning = true, startTimeMillis = session.startTimeMillis) }
                    startTicker(session.startTimeMillis)
                    // Alarme können durch App-Update/Force-Stop verloren gehen → neu planen
                    scheduleWidgetTick(getApplication(), session.startTimeMillis)
                    scheduleMidnightReset()
                    scheduleTargetNotification(
                        getApplication(),
                        session.startTimeMillis,
                        _state.value.settings
                    )
                }
                // Gestoppte Session beim App-Start wiederherstellen
                if (!session.isRunning && session.startTimeMillis > 0 && _state.value.startTimeMillis <= 0) {
                    recalculate(session.startTimeMillis)
                    _state.update { it.copy(startTimeMillis = session.startTimeMillis) }
                }
            }
        }
        viewModelScope.launch {
            store.settings.collect { settings ->
                _state.update { it.copy(settings = settings) }
                val startTimeMillis = _state.value.startTimeMillis
                if (startTimeMillis > 0) recalculate(startTimeMillis)
                if (_state.value.isRunning) {
                    scheduleTargetNotification(getApplication(), startTimeMillis, settings)
                } else {
                    cancelTargetNotification(getApplication())
                }
                WorkTimeWidget().updateAll(getApplication())
            }
        }
        viewModelScope.launch {
            store.weekEntries.collect { entries ->
                _state.update { it.copy(weekEntries = entries) }
            }
        }
    }

    fun start() {
        val now = (System.currentTimeMillis() / 60_000) * 60_000
        viewModelScope.launch {
            store.startSession(now)
            _state.update { it.copy(isRunning = true, startTimeMillis = now) }
            startTicker(now)
            scheduleMidnightReset()
            scheduleWidgetTick(getApplication(), now)
            scheduleTargetNotification(getApplication(), now, _state.value.settings)
            WorkTimeWidget().updateAll(getApplication())
        }
    }

    fun stop() {
        viewModelScope.launch {
            store.stopSession()
            tickJob?.cancel()
            cancelMidnightReset()
            cancelWidgetTick(getApplication())
            cancelTargetNotification(getApplication())
            _state.update { it.copy(isRunning = false) }
            WorkTimeWidget().updateAll(getApplication())
        }
    }

    fun reset() {
        viewModelScope.launch {
            store.resetSession()
            tickJob?.cancel()
            cancelMidnightReset()
            cancelWidgetTick(getApplication())
            cancelTargetNotification(getApplication())
            _state.update {
                TimerUiState(
                    settings = it.settings,
                    weekEntries = it.weekEntries
                )
            }
            WorkTimeWidget().updateAll(getApplication())
        }
    }

    fun adjustStartTime(newStartMillis: Long) {
        viewModelScope.launch {
            store.adjustStartTime(newStartMillis)
            _state.update { it.copy(startTimeMillis = newStartMillis) }
            recalculate(newStartMillis)
            if (_state.value.isRunning) {
                startTicker(newStartMillis)
                scheduleTargetNotification(
                    getApplication(),
                    newStartMillis,
                    _state.value.settings
                )
            }
        }
    }

    fun updateBreakMinutes(firstBreakMinutes: Int, secondBreakMinutes: Int) {
        viewModelScope.launch {
            store.updateBreakMinutes(firstBreakMinutes, secondBreakMinutes)
        }
    }

    fun updateDailyTarget(minutes: Int) {
        viewModelScope.launch {
            store.updateDailyTarget(minutes)
        }
    }

    fun updateNotificationsEnabled(enabled: Boolean) {
        viewModelScope.launch { store.updateNotificationsEnabled(enabled) }
    }

    fun updateNotificationOffset(minutes: Int) {
        viewModelScope.launch { store.updateNotificationOffset(minutes) }
    }

    fun updateWeekStart(day: DayOfWeek, minutes: Int) {
        viewModelScope.launch { store.updateWeekStart(day, minutes) }
    }

    fun updateWeekEnd(day: DayOfWeek, minutes: Int) {
        viewModelScope.launch { store.updateWeekEnd(day, minutes) }
    }

    fun resetWeek() {
        viewModelScope.launch { store.resetWeek() }
    }

    fun resetWeekDay(day: DayOfWeek) {
        viewModelScope.launch { store.resetWeekDay(day) }
    }

    fun endDay() {
        val currentState = _state.value
        val day = LocalDate.now().dayOfWeek
        if (!currentState.isRunning || currentState.startTimeMillis <= 0 ||
            day !in WorkSessionStore.WORK_DAYS
        ) return

        val endTimeMillis = (System.currentTimeMillis() / 60_000) * 60_000
        val startTime = Instant.ofEpochMilli(currentState.startTimeMillis)
            .atZone(ZoneId.systemDefault())
            .toLocalTime()
        val endTime = Instant.ofEpochMilli(endTimeMillis)
            .atZone(ZoneId.systemDefault())
            .toLocalTime()
        val startMinutes = startTime.hour * 60 + startTime.minute
        val endMinutes = endTime.hour * 60 + endTime.minute

        viewModelScope.launch {
            try {
                store.saveDayAndResetSession(day, startMinutes, endMinutes)
                tickJob?.cancel()
                cancelMidnightReset()
                cancelWidgetTick(getApplication())
                cancelTargetNotification(getApplication())
                _state.update {
                    TimerUiState(
                        settings = it.settings,
                        weekEntries = it.weekEntries + (
                            day to WorkSessionStore.WeekEntry(startMinutes, endMinutes)
                        )
                    )
                }
                WorkTimeWidget().updateAll(getApplication())
            } catch (exception: CancellationException) {
                throw exception
            } catch (_: Exception) {
                _state.update {
                    it.copy(endDayError = "Der Arbeitstag konnte nicht gespeichert werden.")
                }
            }
        }
    }

    fun clearEndDayError() {
        _state.update { it.copy(endDayError = null) }
    }

    private fun startTicker(startTimeMillis: Long) {
        tickJob?.cancel()
        tickJob = viewModelScope.launch {
            while (true) {
                recalculate(startTimeMillis)
                delay(1_000L)
            }
        }
    }

    private fun recalculate(startTimeMillis: Long) {
        val grossMs = (System.currentTimeMillis() - startTimeMillis).coerceAtLeast(0)
        val grossMinutes = (grossMs / 60_000).toInt()
        val breakConfig = _state.value.settings.breakConfig
        val netMinutes = WorkTimeCalculator.calculateNetMinutes(grossMinutes, breakConfig)
        val breakMinutes = WorkTimeCalculator.requiredBreakMinutes(grossMinutes, breakConfig)
        _state.update {
            it.copy(
                grossMinutes = grossMinutes,
                netMinutes = netMinutes,
                requiredBreakMinutes = breakMinutes
            )
        }
    }

    fun scheduleMidnightReset() {
        val context = getApplication<Application>()
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = midnightPendingIntent(context)
        val midnight = LocalDate.now().plusDays(1)
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
        try {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, midnight, pi)
        } catch (_: SecurityException) {
            alarmManager.set(AlarmManager.RTC_WAKEUP, midnight, pi)
        }
    }

    private fun cancelMidnightReset() {
        val context = getApplication<Application>()
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(midnightPendingIntent(context))
    }

    private fun midnightPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, MidnightResetReceiver::class.java)
            .setAction("de.worktime.MIDNIGHT_RESET")
        return PendingIntent.getBroadcast(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
