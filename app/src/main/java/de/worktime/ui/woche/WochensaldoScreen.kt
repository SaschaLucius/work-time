package de.worktime.ui.woche

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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.worktime.domain.WorkTimeCalculator

private data class DayTime(val hour: Int, val minute: Int)

private data class DayState(
    val label: String,
    val start: DayTime?,
    val end: DayTime?
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WochensaldoScreen() {
    // Mo=0, Di=1, Mi=2, Do=3, Fr=4
    val dayLabels = listOf("Mo", "Di", "Mi", "Do", "Fr")

    var startTimes by rememberSaveable {
        mutableStateOf(List<DayTime?>(5) { null })
    }
    var endTimes by rememberSaveable {
        mutableStateOf(List<DayTime?>(5) { null })
    }

    // which picker is open: Pair(dayIndex, isStart)
    var activePicker by rememberSaveable { mutableStateOf<Pair<Int, Boolean>?>(null) }

    val netMinutesPerDay: List<Int?> = (0..4).map { i ->
        val s = startTimes[i]
        val e = endTimes[i]
        if (s != null && e != null) {
            val gross = (e.hour * 60 + e.minute) - (s.hour * 60 + s.minute)
            WorkTimeCalculator.calculateNetMinutes(gross.coerceAtLeast(0))
        } else null
    }

    val totalMinutes = netMinutesPerDay.filterNotNull().sum()
    val filledDays = netMinutesPerDay.count { it != null }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
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

        Spacer(Modifier.height(24.dp))

        (0..4).forEach { i ->
            TagZeile(
                label = dayLabels[i],
                start = startTimes[i],
                end = endTimes[i],
                netMinutes = netMinutesPerDay[i],
                onStartClick = { activePicker = Pair(i, true) },
                onEndClick = { activePicker = Pair(i, false) }
            )
            if (i < 4) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            }
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
                    text = if (filledDays > 0) "Gesamt ($filledDays Tag${if (filledDays == 1) "" else "e"})" else "Gesamt",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text = if (filledDays > 0) WorkTimeCalculator.formatDuration(totalMinutes) else "--:--",
                    fontSize = 48.sp,
                    fontWeight = FontWeight.Light,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    lineHeight = 52.sp
                )
            }
        }
    }

    // Time picker dialog
    activePicker?.let { (dayIndex, isStart) ->
        val current = if (isStart) startTimes[dayIndex] else endTimes[dayIndex]
        val pickerState = rememberTimePickerState(
            initialHour = current?.hour ?: 8,
            initialMinute = current?.minute ?: 0,
            is24Hour = true
        )
        val dayLabel = dayLabels[dayIndex]
        val pickerTitle = if (isStart) "Start $dayLabel" else "Ende $dayLabel"

        AlertDialog(
            onDismissRequest = { activePicker = null },
            title = { Text(pickerTitle) },
            text = { TimePicker(state = pickerState) },
            confirmButton = {
                TextButton(onClick = {
                    val newTime = DayTime(pickerState.hour, pickerState.minute)
                    if (isStart) {
                        startTimes = startTimes.toMutableList().also { it[dayIndex] = newTime }
                    } else {
                        endTimes = endTimes.toMutableList().also { it[dayIndex] = newTime }
                    }
                    activePicker = null
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { activePicker = null }) { Text("Abbrechen") }
            }
        )
    }
}

@Composable
private fun TagZeile(
    label: String,
    start: DayTime?,
    end: DayTime?,
    netMinutes: Int?,
    onStartClick: () -> Unit,
    onEndClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(0.6f),
            color = MaterialTheme.colorScheme.onSurface
        )
        OutlinedButton(
            onClick = onStartClick,
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = start?.let { "%02d:%02d".format(it.hour, it.minute) } ?: "--:--",
                style = MaterialTheme.typography.bodyMedium
            )
        }
        OutlinedButton(
            onClick = onEndClick,
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = end?.let { "%02d:%02d".format(it.hour, it.minute) } ?: "--:--",
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
    }
}
