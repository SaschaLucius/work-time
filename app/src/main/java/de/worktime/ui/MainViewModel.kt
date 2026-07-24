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
import de.worktime.data.WorkSessionStore
import de.worktime.domain.WorkTimeCalculator
import de.worktime.widget.WorkTimeWidget
import de.worktime.widget.cancelWidgetTick
import de.worktime.widget.scheduleWidgetTick
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

data class TimerUiState(
    val isRunning: Boolean = false,
    val startTimeMillis: Long = -1L,
    val netMinutes: Int = 0,
    val grossMinutes: Int = 0,
    val requiredBreakMinutes: Int = 0
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
                    return@collect
                }
                // Extern gestartete Session aufnehmen (z. B. vom Widget)
                if (session.isRunning && session.startTimeMillis > 0 && !_state.value.isRunning) {
                    _state.update { it.copy(isRunning = true, startTimeMillis = session.startTimeMillis) }
                    startTicker(session.startTimeMillis)
                }
                // Gestoppte Session beim App-Start wiederherstellen
                if (!session.isRunning && session.startTimeMillis > 0 && _state.value.startTimeMillis <= 0) {
                    recalculate(session.startTimeMillis)
                    _state.update { it.copy(startTimeMillis = session.startTimeMillis) }
                }
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
            WorkTimeWidget().updateAll(getApplication())
        }
    }

    fun stop() {
        viewModelScope.launch {
            store.stopSession()
            tickJob?.cancel()
            cancelMidnightReset()
            cancelWidgetTick(getApplication())
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
            _state.update { TimerUiState() }
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
            }
        }
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
        val netMinutes = WorkTimeCalculator.calculateNetMinutes(grossMinutes)
        val breakMinutes = WorkTimeCalculator.requiredBreakMinutes(grossMinutes)
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
