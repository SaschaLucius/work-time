package de.worktime.ui.calculator

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.worktime.domain.WorkTimeCalculator
import de.worktime.ui.common.ZeitPickerDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RechnerScreen(breakConfig: WorkTimeCalculator.BreakConfig) {
    var startHour by rememberSaveable { mutableIntStateOf(8) }
    var startMinute by rememberSaveable { mutableIntStateOf(30) }
    var endHour by rememberSaveable { mutableIntStateOf(17) }
    var endMinute by rememberSaveable { mutableIntStateOf(0) }

    val gross = ((endHour * 60 + endMinute) - (startHour * 60 + startMinute)).coerceAtLeast(0)
    val net = WorkTimeCalculator.calculateNetMinutes(gross, breakConfig)
    val pause = WorkTimeCalculator.requiredBreakMinutes(gross, breakConfig)

    var showStartPicker by rememberSaveable { mutableStateOf(false) }
    var showEndPicker by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        Text(
            text = "Zeitrechner",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = "Start- und Endzeit eingeben",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(32.dp))

        // Startzeit
        ZeitZeile(
            label = "Startzeit",
            hour = startHour,
            minute = startMinute,
            onClick = { showStartPicker = true }
        )

        Spacer(Modifier.height(16.dp))

        // Endzeit
        ZeitZeile(
            label = "Endzeit",
            hour = endHour,
            minute = endMinute,
            onClick = { showEndPicker = true }
        )

        Spacer(Modifier.height(24.dp))

        HorizontalDivider()

        Spacer(Modifier.height(24.dp))

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
                        text = "Netto-Arbeitszeit",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = WorkTimeCalculator.formatDuration(net),
                        fontSize = 48.sp,
                        fontWeight = FontWeight.Light,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        lineHeight = 52.sp
                    )
                    if (pause > 0) {
                        Text(
                            text = "$pause Min. Pause abgezogen (ArbZG §4)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    } else {
                        Text(
                            text = "Keine Pause erforderlich",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
            if (net > WorkTimeCalculator.MAX_NET_MINUTES) {
                Spacer(Modifier.height(8.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = MaterialTheme.shapes.medium
                ) {
                    Text(
                        text = "Gesetzl. Höchstarbeitszeit von 10 Std. überschritten (ArbZG §3)",
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
    }

    // Start-Zeitpicker
    if (showStartPicker) {
        ZeitPickerDialog(
            title = "Startzeit",
            initialHour = startHour,
            initialMinute = startMinute,
            onConfirm = { hour, minute ->
                startHour = hour
                startMinute = minute
                showStartPicker = false
            },
            onDismiss = { showStartPicker = false }
        )
    }

    // End-Zeitpicker
    if (showEndPicker) {
        ZeitPickerDialog(
            title = "Endzeit",
            initialHour = endHour,
            initialMinute = endMinute,
            onConfirm = { hour, minute ->
                endHour = hour
                endMinute = minute
                showEndPicker = false
            },
            onDismiss = { showEndPicker = false }
        )
    }
}

@Composable
private fun ZeitZeile(
    label: String,
    hour: Int,
    minute: Int,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
        OutlinedButton(onClick = onClick) {
            Text(
                text = "%02d:%02d".format(hour, minute),
                style = MaterialTheme.typography.titleMedium
            )
        }
    }
}
