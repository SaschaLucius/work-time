package de.worktime.ui.woche

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Share
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.worktime.data.WorkSessionStore
import de.worktime.domain.WorkTimeCalculator
import de.worktime.ui.common.ZeitPickerDialog
import java.time.DayOfWeek

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WochensaldoScreen(
    entries: Map<DayOfWeek, WorkSessionStore.WeekEntry>,
    runningDraft: RunningDayDraft?,
    breakConfig: WorkTimeCalculator.BreakConfig,
    showWeekends: Boolean,
    weeklyTargetMinutes: Int,
    onStartChange: (DayOfWeek, Int) -> Unit,
    onEndChange: (DayOfWeek, Int) -> Unit,
    onDraftStartChange: (Int) -> Unit,
    onResetDay: (DayOfWeek) -> Unit,
    onResetWeek: () -> Unit
) {
    val context = LocalContext.current
    val overview = buildWeekOverviewModel(
        entries = entries,
        runningDraft = runningDraft,
        breakConfig = breakConfig,
        showWeekends = showWeekends,
        weeklyTargetMinutes = weeklyTargetMinutes
    )
    val dayLabels = mapOf(
        DayOfWeek.MONDAY to "Mo",
        DayOfWeek.TUESDAY to "Di",
        DayOfWeek.WEDNESDAY to "Mi",
        DayOfWeek.THURSDAY to "Do",
        DayOfWeek.FRIDAY to "Fr",
        DayOfWeek.SATURDAY to "Sa",
        DayOfWeek.SUNDAY to "So"
    )

    // which picker is open: Pair(dayIndex, isStart)
    var activePicker by rememberSaveable { mutableStateOf<Pair<Int, Boolean>?>(null) }
    var showResetDialog by rememberSaveable { mutableStateOf(false) }
    var invalidTimeRange by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Wochensaldo",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Start- und Endzeit pro Tag eingeben",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(
                onClick = {
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_SUBJECT, buildWeekShareSubject())
                        putExtra(
                            Intent.EXTRA_TEXT,
                            buildWeekShareText(entries, breakConfig, weeklyTargetMinutes)
                        )
                    }
                    context.startActivity(
                        Intent.createChooser(shareIntent, "Wochenübersicht teilen")
                    )
                },
                enabled = entries.values.any { entry -> entry.hasValue }
            ) {
                Icon(Icons.Default.Share, contentDescription = "Wochenübersicht teilen")
            }
        }

        Spacer(Modifier.height(24.dp))

        overview.days.forEachIndexed { i, dayOverview ->
            val day = dayOverview.day
            TagZeile(
                label = dayLabels.getValue(day),
                startMinutes = dayOverview.startMinutes,
                endMinutes = dayOverview.endMinutes,
                netMinutes = dayOverview.netMinutes,
                isRunningDraft = dayOverview.isRunningDraft,
                onStartClick = { activePicker = Pair(i, true) },
                onEndClick = { activePicker = Pair(i, false) },
                onResetClick = { onResetDay(day) }
            )
            if (i < overview.days.lastIndex) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            }
        }

        Spacer(Modifier.height(16.dp))
        OutlinedButton(
            onClick = { showResetDialog = true },
            enabled = entries.values.any { entry -> entry.hasValue },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.RestartAlt, contentDescription = null)
            Text("Woche zurücksetzen", modifier = Modifier.padding(start = 8.dp))
        }

        Spacer(Modifier.height(24.dp))
        HorizontalDivider()
        Spacer(Modifier.height(16.dp))

        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.primaryContainer,
            shape = MaterialTheme.shapes.large
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = summaryLabel(overview.filledDays, overview.isProvisional),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text = if (overview.filledDays > 0) {
                        WorkTimeCalculator.formatDuration(overview.totalMinutes)
                    } else {
                        "--:--"
                    },
                    fontSize = 48.sp,
                    fontWeight = FontWeight.Light,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    lineHeight = 52.sp
                )
                Spacer(Modifier.height(8.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.2f))
                Spacer(Modifier.height(4.dp))
                SummaryRow(
                    label = "Wochenziel",
                    value = WorkTimeCalculator.formatDuration(weeklyTargetMinutes)
                )
                SummaryRow(
                    label = "Saldo",
                    value = if (overview.filledDays > 0) {
                        formatSignedDuration(overview.balanceMinutes)
                    } else {
                        "--:--"
                    }
                )
            }
        }
    }

    // Time picker dialog
    activePicker?.let { (dayIndex, isStart) ->
        val dayOverview = overview.days[dayIndex]
        val day = dayOverview.day
        val current = if (isStart) dayOverview.startMinutes else dayOverview.endMinutes
        val dayLabel = dayLabels.getValue(day)
        val pickerTitle = if (isStart) "Start $dayLabel" else "Ende $dayLabel"

        ZeitPickerDialog(
            title = pickerTitle,
            initialHour = current?.div(60) ?: 8,
            initialMinute = current?.rem(60) ?: 0,
            onConfirm = { hour, minute ->
                val newTime = hour * 60 + minute
                val isValid = if (isStart) {
                    dayOverview.endMinutes == null || newTime <= dayOverview.endMinutes
                } else {
                    dayOverview.startMinutes == null || dayOverview.startMinutes <= newTime
                }
                if (isValid) {
                    if (dayOverview.isRunningDraft) {
                        onDraftStartChange(newTime)
                    } else if (isStart) {
                        onStartChange(day, newTime)
                    } else {
                        onEndChange(day, newTime)
                    }
                } else {
                    invalidTimeRange = true
                }
                activePicker = null
            },
            onDismiss = { activePicker = null }
        )
    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text("Woche zurücksetzen?") },
            text = { Text("Alle Start- und Endzeiten dieser Woche werden gelöscht.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showResetDialog = false
                        onResetWeek()
                    }
                ) {
                    Text("Zurücksetzen")
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) {
                    Text("Abbrechen")
                }
            }
        )
    }

    if (invalidTimeRange) {
        AlertDialog(
            onDismissRequest = { invalidTimeRange = false },
            title = { Text("Ungültiger Zeitraum") },
            text = { Text("Die Startzeit darf nicht nach der Endzeit liegen.") },
            confirmButton = {
                TextButton(onClick = { invalidTimeRange = false }) {
                    Text("OK")
                }
            }
        )
    }
}

@Composable
private fun SummaryRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
    }
}

@Composable
private fun TagZeile(
    label: String,
    startMinutes: Int?,
    endMinutes: Int?,
    netMinutes: Int?,
    isRunningDraft: Boolean,
    onStartClick: () -> Unit,
    onEndClick: () -> Unit,
    onResetClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(0.8f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (isRunningDraft) {
                Text(
                    text = "Laufend · nicht gespeichert",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.tertiary
                )
            }
        }
        OutlinedButton(
            onClick = onStartClick,
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = startMinutes?.let(::formatTime) ?: "--:--",
                style = MaterialTheme.typography.bodyMedium
            )
        }
        OutlinedButton(
            onClick = onEndClick,
            enabled = !isRunningDraft,
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = endMinutes?.let(::formatTime) ?: "--:--",
                style = MaterialTheme.typography.bodyMedium
            )
        }
        Text(
            text = netMinutes?.let { WorkTimeCalculator.formatDuration(it) } ?: "",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f),
            color = if (netMinutes != null) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
        )
        IconButton(
            onClick = onResetClick,
            enabled = !isRunningDraft && (startMinutes != null || endMinutes != null)
        ) {
            Icon(
                Icons.Default.RestartAlt,
                contentDescription = "$label zurücksetzen"
            )
        }
    }
}

private fun summaryLabel(filledDays: Int, isProvisional: Boolean): String {
    if (filledDays == 0) return "Gesamt"
    val days = "$filledDays Tag${if (filledDays == 1) "" else "e"}"
    return if (isProvisional) "Gesamt (vorläufig, $days)" else "Gesamt ($days)"
}

private fun formatTime(minutes: Int): String = "%02d:%02d".format(minutes / 60, minutes % 60)
