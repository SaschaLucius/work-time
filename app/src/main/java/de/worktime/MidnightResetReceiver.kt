package de.worktime

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.glance.appwidget.updateAll
import de.worktime.data.WorkSessionStore
import de.worktime.widget.WorkTimeWidget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

class MidnightResetReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                when (intent.action) {
                    "de.worktime.MIDNIGHT_RESET" -> handleMidnightReset(context)
                    Intent.ACTION_BOOT_COMPLETED -> handleBootCompleted(context)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun handleMidnightReset(context: Context) {
        WorkSessionStore(context).resetSession()
        WorkTimeWidget().updateAll(context)
    }

    private suspend fun handleBootCompleted(context: Context) {
        val store = WorkSessionStore(context)
        val session = store.session.first()
        when {
            // Alte Session von gestern → löschen
            session.isRunning && session.sessionDate != LocalDate.now().toString() -> {
                store.resetSession()
            }
            // Heutige Session noch aktiv → Mitternachts-Alarm neu planen
            session.isRunning -> {
                scheduleNextMidnightAlarm(context)
            }
        }
        WorkTimeWidget().updateAll(context)
    }

    private fun scheduleNextMidnightAlarm(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, MidnightResetReceiver::class.java)
            .setAction("de.worktime.MIDNIGHT_RESET")
        val pi = PendingIntent.getBroadcast(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
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
}
