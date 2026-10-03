package de.worktime

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.glance.appwidget.updateAll
import de.worktime.data.WorkSessionStore
import de.worktime.data.totalNetMinutes
import de.worktime.widget.WorkTimeWidget
import de.worktime.widget.scheduleWidgetTick
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

private const val ACTION_MIDNIGHT_RESET = "de.worktime.MIDNIGHT_RESET"

fun midnightResetPendingIntent(context: Context): PendingIntent {
    val intent = Intent(context, MidnightResetReceiver::class.java)
        .setAction(ACTION_MIDNIGHT_RESET)
    return PendingIntent.getBroadcast(
        context, 0, intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
}

fun scheduleMidnightResetAlarm(context: Context) {
    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    val pi = midnightResetPendingIntent(context)
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

fun cancelMidnightResetAlarm(context: Context) {
    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    alarmManager.cancel(midnightResetPendingIntent(context))
}

class MidnightResetReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                when (intent.action) {
                    ACTION_MIDNIGHT_RESET -> handleMidnightReset(context)
                    Intent.ACTION_BOOT_COMPLETED,
                    Intent.ACTION_MY_PACKAGE_REPLACED -> handleBootCompleted(context)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun handleMidnightReset(context: Context) {
        WorkSessionStore(context).finalizeStaleSessionIfNeeded()
        cancelTargetNotification(context)
        WorkTimeWidget().updateAll(context)
    }

    private suspend fun handleBootCompleted(context: Context) {
        val store = WorkSessionStore(context)
        store.finalizeStaleSessionIfNeeded()
        val session = store.session.first()
        val settings = store.settings.first()
        when {
            // Heutige Session noch aktiv → Alarme neu planen (gehen bei
            // Reboot und App-Update verloren)
            session.isRunning && session.startTimeMillis > 0 -> {
                scheduleMidnightResetAlarm(context)
                scheduleWidgetTick(context, session.startTimeMillis)
                val completedWeekMinutes = store.weekEntries.first().totalNetMinutes(
                    settings.breakConfig,
                    excluding = LocalDate.now().dayOfWeek
                )
                scheduleTargetNotification(
                    context,
                    session.startTimeMillis,
                    settings,
                    completedWeekMinutes
                )
            }
            else -> cancelTargetNotification(context)
        }
        WorkTimeWidget().updateAll(context)
    }
}
