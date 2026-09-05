package de.worktime

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import de.worktime.data.WorkSessionStore
import de.worktime.domain.WorkTimeCalculator
import de.worktime.ui.MainActivity
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class TargetNotificationReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                deliverIfEligible(context)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun deliverIfEligible(context: Context) {
        val store = WorkSessionStore(context)
        val session = store.session.first()
        val settings = store.settings.first()
        val today = LocalDate.now()
        if (!session.isRunning || session.startTimeMillis <= 0 ||
            !settings.notificationsEnabled || settings.lastNotificationDate == today.toString()
        ) {
            cancelTargetNotification(context)
            return
        }

        val grossMinutes = (
            (System.currentTimeMillis() - session.startTimeMillis).coerceAtLeast(0) / 60_000
        ).toInt()
        val threshold =
            (settings.dailyTargetMinutes - settings.notificationOffsetMinutes).coerceAtLeast(0)
        val netMinutes = WorkTimeCalculator.calculateNetMinutes(grossMinutes, settings.breakConfig)
        if (netMinutes < threshold) {
            scheduleTargetNotification(context, session.startTimeMillis, settings)
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) return

        createNotificationChannel(context)
        val openAppIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.target_notification_title))
            .setContentText(context.getString(R.string.target_notification_text))
            .setContentIntent(openAppIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        } catch (_: SecurityException) {
            return
        }
        store.markNotificationShown(today)
    }

    private fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.target_notification_channel),
                NotificationManager.IMPORTANCE_DEFAULT
            )
        )
    }

    companion object {
        private const val CHANNEL_ID = "daily_target"
        private const val NOTIFICATION_ID = 1001
    }
}