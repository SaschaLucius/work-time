package de.worktime.ui.timer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.worktime.domain.WorkTimeCalculator
import de.worktime.ui.MainViewModel
import de.worktime.ui.TimerUiState
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimerScreen(viewModel: MainViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showStartPicker by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Status
        Text(
            text = if (state.isRunning)
                "Läuft seit ${formatStartTime(state.startTimeMillis)}"
            else if (state.startTimeMillis > 0)
                "Gestoppt · Start: ${formatStartTime(state.startTimeMillis)}"
            else
                "Bereit",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(12.dp))

        // Hauptanzeige: Netto-Arbeitszeit
        Text(
            text = if (state.startTimeMillis > 0)
                WorkTimeCalculator.formatDuration(state.netMinutes)
            else
                "--:--",
            fontSize = 72.sp,
            fontWeight = FontWeight.Light,
            color = MaterialTheme.colorScheme.onSurface,
            lineHeight = 72.sp
        )

        Text(
            text = "Netto-Arbeitszeit",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(12.dp))

        // Pauseninfo + Maximalzeit-Warnung
        val showChips = state.startTimeMillis > 0 &&
            (state.requiredBreakMinutes > 0 || WorkTimeCalculator.isOverMaximum(state.netMinutes))
        if (showChips) {
            Column(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (state.requiredBreakMinutes > 0) {
                    PausenChip(state)
                }
                if (WorkTimeCalculator.isOverMaximum(state.netMinutes)) {
                    MaxZeitChip()
                }
            }
        } else {
            Spacer(Modifier.height(32.dp))
        }

        Spacer(Modifier.height(40.dp))

        // Start
        Button(
            onClick = { viewModel.start() },
            enabled = !state.isRunning,
            modifier = Modifier
                .fillMaxWidth(0.55f)
                .height(52.dp)
        ) {
            Text(
                text = "Start",
                style = MaterialTheme.typography.titleMedium
            )
        }

        Spacer(Modifier.height(16.dp))

        // Sekundäre Aktionen
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
        ) {
            OutlinedButton(
                onClick = { showStartPicker = true },
                enabled = state.startTimeMillis > 0
            ) {
                Text("Startzeit")
            }
            OutlinedButton(
                onClick = { viewModel.reset() },
                enabled = state.startTimeMillis > 0 || state.isRunning
            ) {
                Text("Zurücksetzen")
            }
        }
    }

    // Zeitauswahl-Dialog für Startzeit
    if (showStartPicker) {
        val currentCal = remember(state.startTimeMillis) {
            Calendar.getInstance().apply {
                if (state.startTimeMillis > 0) timeInMillis = state.startTimeMillis
            }
        }
        val pickerState = rememberTimePickerState(
            initialHour = currentCal.get(Calendar.HOUR_OF_DAY),
            initialMinute = currentCal.get(Calendar.MINUTE),
            is24Hour = true
        )
        AlertDialog(
            onDismissRequest = { showStartPicker = false },
            title = { Text("Startzeit anpassen") },
            text = { TimePicker(state = pickerState) },
            confirmButton = {
                TextButton(onClick = {
                    showStartPicker = false
                    val cal = Calendar.getInstance().apply {
                        set(Calendar.HOUR_OF_DAY, pickerState.hour)
                        set(Calendar.MINUTE, pickerState.minute)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }
                    viewModel.adjustStartTime(cal.timeInMillis)
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showStartPicker = false }) { Text("Abbrechen") }
            }
        )
    }
}

@Composable
private fun MaxZeitChip() {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        shape = MaterialTheme.shapes.small
    ) {
        Text(
            text = "Gesetzl. Höchstarbeitszeit von 10 Std. überschritten (ArbZG §3)",
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onErrorContainer
        )
    }
}

@Composable
private fun PausenChip(state: TimerUiState) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.small
    ) {
        Text(
            text = "Brutto ${WorkTimeCalculator.formatDuration(state.grossMinutes)}" +
                    " · ${state.requiredBreakMinutes} Min. Pause (ArbZG §4)",
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun formatStartTime(startTimeMillis: Long): String {
    if (startTimeMillis <= 0) return "--:--"
    val cal = Calendar.getInstance().apply { timeInMillis = startTimeMillis }
    return "%02d:%02d".format(cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE))
}
