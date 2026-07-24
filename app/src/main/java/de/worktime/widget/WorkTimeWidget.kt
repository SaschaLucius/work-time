package de.worktime.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalSize
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.LocalContext
import androidx.glance.action.clickable
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import de.worktime.MidnightResetReceiver
import de.worktime.data.WorkSessionStore
import de.worktime.domain.WorkTimeCalculator
import de.worktime.ui.MainActivity
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private const val ACTION_TICK = "de.worktime.WIDGET_TICK"

private fun widgetTickPendingIntent(context: Context): PendingIntent {
    val intent = Intent(context, WorkTimeWidgetReceiver::class.java)
        .setAction(ACTION_TICK)
    return PendingIntent.getBroadcast(
        context, 1, intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
}

fun scheduleWidgetTick(context: Context, startTimeMillis: Long) {
    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    val now = System.currentTimeMillis()
    val elapsed = (now - startTimeMillis).coerceAtLeast(0)
    // Align to the next full minute boundary relative to startTimeMillis, so the
    // widget updates exactly when the displayed minute counter changes.
    val nextMinuteBoundary = startTimeMillis + ((elapsed / 60_000) + 1) * 60_000
    val pi = widgetTickPendingIntent(context)
    // One-shot EXACT alarm re-armed on every tick. Inexact repeating alarms get
    // batched/throttled by Doze and often never fire, which is why the widget
    // appeared frozen. RTC_WAKEUP + exact makes the minute tick reliable.
    try {
        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextMinuteBoundary, pi)
    } catch (_: SecurityException) {
        alarmManager.set(AlarmManager.RTC_WAKEUP, nextMinuteBoundary, pi)
    }
}

fun cancelWidgetTick(context: Context) {
    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    alarmManager.cancel(widgetTickPendingIntent(context))
}

/**
 * Re-arms the minute tick if a session is running. AlarmManager drops all of an
 * app's alarms on package update / force-stop, so this must be called from every
 * entry point (widget update, app start, boot, package replaced) to self-heal.
 */
suspend fun ensureWidgetTick(context: Context) {
    val session = WorkSessionStore(context).session.first()
    if (session.isRunning && session.startTimeMillis > 0) {
        scheduleWidgetTick(context, session.startTimeMillis)
    }
}

class WorkTimeWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val store = WorkSessionStore(context)
        provideContent {
            val session by store.session.collectAsState(
                initial = null
            )
            GlanceTheme {
                WidgetContent(session = session)
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun WidgetContent(session: WorkSessionStore.WorkSession?) {
    val size = LocalSize.current
    val widgetWidth = size.width.value
    val widgetHeight = size.height.value

    // null = still loading from DataStore; treat the same as running-but-unknown
    // to avoid flashing the Start button before the real state arrives
    val isRunning = session?.isRunning == true && (session.startTimeMillis) > 0
    val grossMinutes = if (isRunning && session != null) {
        ((System.currentTimeMillis() - session.startTimeMillis).coerceAtLeast(0) / 60_000).toInt()
    } else 0
    val netMinutes = WorkTimeCalculator.calculateNetMinutes(grossMinutes)
    val breakMinutes = WorkTimeCalculator.requiredBreakMinutes(grossMinutes)

    // Scale by width, but cap at a fraction of height so the text never
    // overflows a short widget (e.g. 4×1) and loses its vertical centering.
    // For tall widgets (4×2, 4×4) width is still the binding dimension and
    // the font grows large to fill the space.
    // When break text is shown in a single-row widget, use a smaller height
    // factor so both the time and break line remain visible.
    val timeHeightFactor = if (breakMinutes > 0) 0.42f else 0.65f
    val timeFontSize = minOf(widgetWidth * 0.22f, widgetHeight * timeHeightFactor)
        .coerceIn(20f, 96f).sp
    val breakFontSize = (widgetWidth * 0.07f).coerceIn(9f, 16f).sp

    val baseModifier = GlanceModifier
        .fillMaxSize()
        .background(GlanceTheme.colors.surface)
        .padding(horizontal = 14.dp, vertical = 8.dp)

    // Beim Tippen auf das laufende Widget → App öffnen
    val context = LocalContext.current
    val rowModifier = if (isRunning)
        baseModifier.clickable(actionStartActivity(
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        ))
    else
        baseModifier

    if (session == null) {
        // Still loading: show neutral placeholder so Start button never flashes
        Row(
            modifier = baseModifier,
            verticalAlignment = Alignment.CenterVertically,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "--:--",
                style = TextStyle(
                    color = GlanceTheme.colors.onSurfaceVariant,
                    fontSize = timeFontSize,
                    fontWeight = FontWeight.Bold
                )
            )
        }
    } else if (!isRunning) {
        // Nicht gestartet: nur Start-Button zentriert
        Row(
            modifier = baseModifier,
            verticalAlignment = Alignment.CenterVertically,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            androidx.glance.Button(
                text = "Start",
                onClick = actionRunCallback<StartSessionAction>()
            )
        }
    } else {
        // Läuft: nur Zeitanzeige, zentriert
        Column(
            modifier = rowModifier,
            verticalAlignment = Alignment.CenterVertically,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = WorkTimeCalculator.formatDuration(netMinutes),
                style = TextStyle(
                    color = GlanceTheme.colors.onSurface,
                    fontSize = timeFontSize,
                    fontWeight = FontWeight.Bold
                )
            )
            if (breakMinutes > 0) {
                Text(
                    text = "−$breakMinutes Min. Pause",
                    style = TextStyle(
                        color = GlanceTheme.colors.onSurfaceVariant,
                        fontSize = breakFontSize
                    )
                )
            }
        }
    }
}

/** Wird ausgeführt, wenn der Widget-Start-Button gedrückt wird. */
class StartSessionAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val startTimeMillis = (System.currentTimeMillis() / 60_000) * 60_000
        WorkSessionStore(context).startSession(startTimeMillis)
        scheduleMidnightAlarm(context)
        scheduleWidgetTick(context, startTimeMillis)
        WorkTimeWidget().updateAll(context)
    }

    private fun scheduleMidnightAlarm(context: Context) {
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

class WorkTimeWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = WorkTimeWidget()

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            ACTION_TICK -> {
                val result = goAsync()
                CoroutineScope(Dispatchers.Main).launch {
                    try {
                        val store = WorkSessionStore(context)
                        val session = store.session.first()
                        if (session.isRunning && session.startTimeMillis > 0) {
                            store.tickSession()
                            WorkTimeWidget().updateAll(context)
                            // Re-arm the next one-shot alarm for the following minute.
                            scheduleWidgetTick(context, session.startTimeMillis)
                        } else {
                            cancelWidgetTick(context)
                        }
                    } finally {
                        result.finish()
                    }
                }
            }
            // Fires on widget placement, reinstall/app update and every
            // updatePeriodMillis — perfect hook to self-heal a lost tick alarm.
            // Note: no goAsync() here, super.onReceive() already consumed it
            // for APPWIDGET_UPDATE (calling it twice returns null → NPE).
            "android.appwidget.action.APPWIDGET_UPDATE" -> {
                CoroutineScope(Dispatchers.IO).launch {
                    ensureWidgetTick(context)
                }
            }
        }
    }
}
