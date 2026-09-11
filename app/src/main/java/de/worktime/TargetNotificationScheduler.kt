package de.worktime

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import de.worktime.data.WorkSessionStore
import de.worktime.domain.WorkTimeCalculator
import java.time.LocalDate

private const val ACTION_TARGET_NOTIFICATION = "de.worktime.TARGET_NOTIFICATION"
private const val TARGET_NOTIFICATION_REQUEST_CODE = 2

fun targetNotificationTriggerMillis(
    startTimeMillis: Long,
    settings: WorkSessionStore.AppSettings,
    today: LocalDate = LocalDate.now(),
    completedWeekMinutes: Int = 0
): Long? {
    if (!settings.notificationsEnabled) return null

    val dailyTrigger = if (settings.lastNotificationDate != today.toString()) {
        val targetNetMinutes =
            (settings.dailyTargetMinutes - settings.notificationOffsetMinutes).coerceAtLeast(0)
        val grossMinutes = WorkTimeCalculator.grossMinutesToReachNetTarget(
            targetNetMinutes,
            settings.breakConfig
        )
        startTimeMillis + grossMinutes * 60_000L
    } else null

    val weeklyTrigger = if (
        settings.lastWeeklyNotification != WorkSessionStore.weekId(today)
    ) {
        val remainingNetMinutes =
            (settings.weeklyTargetMinutes - completedWeekMinutes).coerceAtLeast(0)
        val grossMinutes = WorkTimeCalculator.grossMinutesToReachNetTarget(
            remainingNetMinutes,
            settings.breakConfig
        )
        startTimeMillis + grossMinutes * 60_000L
    } else null

    return listOfNotNull(dailyTrigger, weeklyTrigger).minOrNull()
}

fun scheduleTargetNotification(
    context: Context,
    startTimeMillis: Long,
    settings: WorkSessionStore.AppSettings,
    completedWeekMinutes: Int = 0
) {
    val triggerMillis = targetNotificationTriggerMillis(
        startTimeMillis,
        settings,
        completedWeekMinutes = completedWeekMinutes
    )
    if (triggerMillis == null || startTimeMillis <= 0) {
        cancelTargetNotification(context)
        return
    }
    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    val triggerAt = triggerMillis.coerceAtLeast(System.currentTimeMillis())
    try {
        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            triggerAt,
            targetNotificationPendingIntent(context)
        )
    } catch (_: SecurityException) {
        alarmManager.set(
            AlarmManager.RTC_WAKEUP,
            triggerAt,
            targetNotificationPendingIntent(context)
        )
    }
}

fun cancelTargetNotification(context: Context) {
    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    alarmManager.cancel(targetNotificationPendingIntent(context))
}

private fun targetNotificationPendingIntent(context: Context): PendingIntent {
    val intent = Intent(context, TargetNotificationReceiver::class.java)
        .setAction(ACTION_TARGET_NOTIFICATION)
    return PendingIntent.getBroadcast(
        context,
        TARGET_NOTIFICATION_REQUEST_CODE,
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
}