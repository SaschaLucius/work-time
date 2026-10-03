package de.worktime.ui.calculator

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.worktime.domain.WorkTimeCalculator
import de.worktime.ui.common.ZeitPickerDialog

private data class BreakUiEntry(
    val id: Long,
    val startHour: Int,
    val startMinute: Int,
    val endHour: Int,
    val endMinute: Int
) {
    val startMinutes: Int get() = startHour * 60 + startMinute
    val endMinutes: Int get() = endHour * 60 + endMinute
}

private val BreakListSaver = listSaver<MutableList<BreakUiEntry>, List<Int>>(
    save = { breaks ->
        breaks.map { listOf(it.id.toInt(), it.startHour, it.startMinute, it.endHour, it.endMinute) }
    },
    restore = { saved ->
        saved.map { values ->
            BreakUiEntry(
                id = values[0].toLong(),
                startHour = values[1],
                startMinute = values[2],
                endHour = values[3],
                endMinute = values[4]
            )
        }.toMutableStateList()
    }
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RechnerScreen(breakConfig: WorkTimeCalculator.BreakConfig) {
    var startHour by rememberSaveable { mutableIntStateOf(8) }
    var startMinute by rememberSaveable { mutableIntStateOf(30) }
    var endHour by rememberSaveable { mutableIntStateOf(17) }
    var endMinute by rememberSaveable { mutableIntStateOf(0) }
    val breaks = rememberSaveable(saver = BreakListSaver) { mutableListOf<BreakUiEntry>().toMutableStateList() }
    var nextBreakId by rememberSaveable { mutableLongStateOf(1L) }

    val startMinutes = startHour * 60 + startMinute
    val endMinutes = endHour * 60 + endMinute
    val isValidRange = startMinutes <= endMinutes
    val result = WorkTimeCalculator.calculateWithManualBreaks(
        startMinutes = startMinutes,
        endMinutes = endMinutes,
        breaks = breaks.map {
            WorkTimeCalculator.ManualBreak(it.startMinutes, it.endMinutes)
        },
        breakConfig = breakConfig
    )
    val showAddedMandatory =
        isValidRange && result.addedMandatoryMinutes > 0
    val showMandatoryWarning =
        showAddedMandatory && result.manualBreakMinutes > 0

    var showStartPicker by rememberSaveable { mutableStateOf(false) }
    var showEndPicker by rememberSaveable { mutableStateOf(false) }
    var editingBreakId by rememberSaveable { mutableStateOf<Long?>(null) }
    var editingBreakField by rememberSaveable { mutableStateOf<BreakTimeField?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 32.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        Text(
            text = "Zeitrechner",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = "Start-, Pausen- und Endzeit eingeben",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(32.dp))

        ZeitZeile(
            label = "Startzeit",
            hour = startHour,
            minute = startMinute,
            onClick = { showStartPicker = true }
        )

        Spacer(Modifier.height(16.dp))

        if (showAddedMandatory) {
            PflichtpauseZeile(minutes = result.addedMandatoryMinutes)
            Spacer(Modifier.height(12.dp))
        }

        breaks.forEachIndexed { index, breakEntry ->
            PauseZeile(
                label = if (breaks.size == 1) "Pause" else "Pause ${index + 1}",
                startHour = breakEntry.startHour,
                startMinute = breakEntry.startMinute,
                endHour = breakEntry.endHour,
                endMinute = breakEntry.endMinute,
                onStartClick = {
                    editingBreakId = breakEntry.id
                    editingBreakField = BreakTimeField.Start
                },
                onEndClick = {
                    editingBreakId = breakEntry.id
                    editingBreakField = BreakTimeField.End
                },
                onDelete = { breaks.removeAll { it.id == breakEntry.id } }
            )
            if (breakEntry.endMinutes <= breakEntry.startMinutes) {
                Text(
                    text = "Pausenende muss nach Pausenbeginn liegen.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
                )
            } else if (
                breakEntry.startMinutes < startMinutes ||
                breakEntry.endMinutes > endMinutes
            ) {
                Text(
                    text = "Pause muss zwischen Start- und Endzeit liegen.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
                )
            } else {
                Spacer(Modifier.height(12.dp))
            }
        }

        TextButton(
            onClick = {
                val suggested = suggestedBreak(startMinutes, endMinutes, breaks)
                breaks.add(
                    BreakUiEntry(
                        id = nextBreakId,
                        startHour = suggested.first / 60,
                        startMinute = suggested.first % 60,
                        endHour = suggested.second / 60,
                        endMinute = suggested.second % 60
                    )
                )
                nextBreakId += 1
            },
            modifier = Modifier.padding(bottom = 8.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Pause hinzufügen")
        }

        ZeitZeile(
            label = "Endzeit",
            hour = endHour,
            minute = endMinute,
            onClick = { showEndPicker = true }
        )

        if (!isValidRange) {
            Text(
                text = "Die Endzeit darf nicht vor der Startzeit liegen.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        if (showMandatoryWarning) {
            Spacer(Modifier.height(16.dp))
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.errorContainer,
                shape = MaterialTheme.shapes.medium
            ) {
                Text(
                    text = "Pflichtpause nicht abgedeckt: " +
                        "${result.manualBreakMinutes} von ${result.requiredBreakMinutes} Min. " +
                        "– ${result.addedMandatoryMinutes} Min. werden zusätzlich abgezogen.",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }

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
                    text = if (isValidRange) {
                        WorkTimeCalculator.formatDuration(result.netMinutes)
                    } else {
                        "--:--"
                    },
                    fontSize = 48.sp,
                    fontWeight = FontWeight.Light,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    lineHeight = 52.sp
                )
                Text(
                    text = breakSummaryText(result),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
        if (result.netMinutes > WorkTimeCalculator.MAX_NET_MINUTES) {
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

    val editingBreak = breaks.firstOrNull { it.id == editingBreakId }
    val editingField = editingBreakField
    if (editingBreak != null && editingField != null) {
        val isStart = editingField == BreakTimeField.Start
        ZeitPickerDialog(
            title = if (isStart) "Pausenbeginn" else "Pausenende",
            initialHour = if (isStart) editingBreak.startHour else editingBreak.endHour,
            initialMinute = if (isStart) editingBreak.startMinute else editingBreak.endMinute,
            onConfirm = { hour, minute ->
                val index = breaks.indexOfFirst { it.id == editingBreak.id }
                if (index >= 0) {
                    breaks[index] = if (isStart) {
                        editingBreak.copy(startHour = hour, startMinute = minute)
                    } else {
                        editingBreak.copy(endHour = hour, endMinute = minute)
                    }
                }
                editingBreakId = null
                editingBreakField = null
            },
            onDismiss = {
                editingBreakId = null
                editingBreakField = null
            }
        )
    }
}

private enum class BreakTimeField { Start, End }

private fun breakSummaryText(result: WorkTimeCalculator.ManualBreakResult): String = when {
    result.effectiveBreakMinutes == 0 -> "Keine Pause erforderlich"
    result.manualBreakMinutes == 0 ->
        "${result.requiredBreakMinutes} Min. Pause abgezogen (ArbZG §4)"
    result.mandatoryBreakCovered && result.manualBreakMinutes > result.requiredBreakMinutes ->
        "${result.manualBreakMinutes} Min. Pause abgezogen " +
            "(Pflichtpause ${result.requiredBreakMinutes} Min. abgedeckt)"
    result.mandatoryBreakCovered ->
        "${result.manualBreakMinutes} Min. Pause abgezogen (Pflichtpause abgedeckt)"
    else ->
        "${result.effectiveBreakMinutes} Min. Pause abgezogen " +
            "(davon ${result.addedMandatoryMinutes} Min. Pflichtpause)"
}

private fun suggestedBreak(
    startMinutes: Int,
    endMinutes: Int,
    existing: List<BreakUiEntry>
): Pair<Int, Int> {
    val span = (endMinutes - startMinutes).coerceAtLeast(0)
    val defaultDuration = 30
    val preferredStart = when {
        span >= 5 * 60 -> startMinutes + 4 * 60
        span >= defaultDuration -> startMinutes + (span - defaultDuration) / 2
        else -> startMinutes
    }
    var breakStart = preferredStart.coerceIn(startMinutes, endMinutes)
    var breakEnd = (breakStart + defaultDuration).coerceAtMost(endMinutes)
    if (breakEnd <= breakStart && endMinutes > startMinutes) {
        breakStart = startMinutes
        breakEnd = minOf(startMinutes + defaultDuration, endMinutes)
    }
    // Shift later if it overlaps an existing break
    existing.sortedBy { it.startMinutes }.forEach { other ->
        if (breakStart < other.endMinutes && breakEnd > other.startMinutes) {
            breakStart = other.endMinutes
            breakEnd = (breakStart + defaultDuration).coerceAtMost(endMinutes)
        }
    }
    if (breakEnd <= breakStart) {
        breakStart = startMinutes
        breakEnd = minOf(startMinutes + defaultDuration, endMinutes).coerceAtLeast(breakStart)
    }
    return breakStart to breakEnd
}

@Composable
private fun PflichtpauseZeile(minutes: Int) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = MaterialTheme.shapes.medium
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Pflichtpause",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Text(
                    text = "automatisch ergänzt",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
            Text(
                text = "$minutes Min.",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
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

@Composable
private fun PauseZeile(
    label: String,
    startHour: Int,
    startMinute: Int,
    endHour: Int,
    endMinute: Int,
    onStartClick: () -> Unit,
    onEndClick: () -> Unit,
    onDelete: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
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
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "$label entfernen",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(
                onClick = onStartClick,
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = "%02d:%02d".format(startHour, startMinute),
                    style = MaterialTheme.typography.titleMedium
                )
            }
            Text(
                text = "–",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedButton(
                onClick = onEndClick,
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = "%02d:%02d".format(endHour, endMinute),
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }
    }
}
