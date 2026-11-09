package de.worktime.ui.settings

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import de.worktime.data.WorkSessionStore
import de.worktime.domain.WorkTimeCalculator
import de.worktime.ui.common.ZeitPickerDialog
import androidx.core.content.ContextCompat

@Composable
fun SettingsScreen(
    settings: WorkSessionStore.AppSettings,
    onBreakMinutesChange: (first: Int, second: Int) -> Unit,
    onDefaultStartChange: (minutes: Int) -> Unit,
    onWeeklyTargetChange: (minutes: Int) -> Unit,
    onWorkDaysPerWeekChange: (days: Int) -> Unit,
    onNotificationsEnabledChange: (Boolean) -> Unit,
    onNotificationOffsetChange: (Int) -> Unit
) {
    val context = LocalContext.current
    var permissionDenied by rememberSaveable { mutableStateOf(false) }
    var showStartPicker by rememberSaveable { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        permissionDenied = !granted
        onNotificationsEnabledChange(granted)
    }
    val weekly = settings.weeklyTargetMinutes
    val workDays = settings.workDaysPerWeek
    val firstBreak = settings.firstBreakMinutes
    val secondBreak = settings.secondBreakMinutes
    val offset = settings.notificationOffsetMinutes
    val isValidWeekly = { minutes: Int -> WorkSessionStore.isValidWeeklyTarget(minutes, workDays) }
    val isValidWorkDays = { days: Int -> WorkSessionStore.isValidWeeklyTarget(weekly, days) }
    val isValidOffset = { minutes: Int ->
        WorkSessionStore.isValidNotificationOffset(minutes, settings.dailyTargetMinutes)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Text(
            text = "Einstellungen",
            style = MaterialTheme.typography.headlineSmall
        )

        SettingsSection(title = "Arbeitszeit") {
            StepperRow(
                label = "Wochenziel",
                value = WorkTimeCalculator.formatDuration(weekly),
                onDecrease = stepIfValid(weekly - 60, isValidWeekly, onWeeklyTargetChange),
                onIncrease = stepIfValid(weekly + 60, isValidWeekly, onWeeklyTargetChange)
            )
            StepperRow(
                label = "Wochentage",
                value = workDays.toString(),
                onDecrease = stepIfValid(workDays - 1, isValidWorkDays, onWorkDaysPerWeekChange),
                onIncrease = stepIfValid(workDays + 1, isValidWorkDays, onWorkDaysPerWeekChange)
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Startzeit",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f)
                )
                OutlinedButton(onClick = { showStartPicker = true }) {
                    Text(
                        text = "%02d:%02d".format(
                            settings.defaultStartMinutes / 60,
                            settings.defaultStartMinutes % 60
                        ),
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
        }

        HorizontalDivider()

        SettingsSection(title = "Pausenabzug") {
            val isValidFirst = { minutes: Int -> WorkSessionStore.isValidBreaks(minutes, secondBreak) }
            val isValidSecond = { minutes: Int -> WorkSessionStore.isValidBreaks(firstBreak, minutes) }
            val changeFirst = { minutes: Int -> onBreakMinutesChange(minutes, secondBreak) }
            val changeSecond = { minutes: Int -> onBreakMinutesChange(firstBreak, minutes) }
            StepperRow(
                label = "Nach 6 Stunden",
                value = "$firstBreak Min.",
                onDecrease = stepIfValid(firstBreak - 5, isValidFirst, changeFirst),
                onIncrease = stepIfValid(firstBreak + 5, isValidFirst, changeFirst)
            )
            StepperRow(
                label = "Nach 9 Std. 30 Min.",
                value = "$secondBreak Min.",
                onDecrease = stepIfValid(secondBreak - 5, isValidSecond, changeSecond),
                onIncrease = stepIfValid(secondBreak + 5, isValidSecond, changeSecond)
            )
        }

        HorizontalDivider()

        SettingsSection(title = "Benachrichtigung") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Beim Tages- oder Wochenziel erinnern",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f)
                )
                Switch(
                    checked = settings.notificationsEnabled,
                    onCheckedChange = { enabled ->
                        when {
                            !enabled -> onNotificationsEnabledChange(false)
                            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ->
                                onNotificationsEnabledChange(true)
                            ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.POST_NOTIFICATIONS
                            ) == PackageManager.PERMISSION_GRANTED ->
                                onNotificationsEnabledChange(true)
                            else -> {
                                permissionDenied = false
                                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                        }
                    }
                )
            }
            StepperRow(
                label = "Tagesziel früher erinnern",
                value = "$offset Min.",
                onDecrease = stepIfValid(offset - 5, isValidOffset, onNotificationOffsetChange),
                onIncrease = stepIfValid(offset + 5, isValidOffset, onNotificationOffsetChange)
            )
            if (permissionDenied) {
                Text(
                    text = "Die Benachrichtigungsberechtigung wurde nicht erteilt.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }

    if (showStartPicker) {
        ZeitPickerDialog(
            title = "Startzeit",
            initialHour = settings.defaultStartMinutes / 60,
            initialMinute = settings.defaultStartMinutes % 60,
            onConfirm = { hour, minute ->
                showStartPicker = false
                onDefaultStartChange(hour * 60 + minute)
            },
            onDismiss = { showStartPicker = false }
        )
    }
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        content()
    }
}

/**
 * Schritt nur anbieten, wenn der neue Wert gültig ist. So prüfen Einstellungen und Store
 * dieselbe Regel, und ein Tipp kann keinen Wert senden, den der Store ablehnt.
 */
private fun stepIfValid(
    next: Int,
    isValid: (Int) -> Boolean,
    onChange: (Int) -> Unit
): (() -> Unit)? = if (isValid(next)) {
    { onChange(next) }
} else {
    null
}

/** Ein Knopf ist deaktiviert, wenn seine Aktion null ist. */
@Composable
private fun StepperRow(
    label: String,
    value: String,
    onDecrease: (() -> Unit)?,
    onIncrease: (() -> Unit)?
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f)
        )
        IconButton(onClick = { onDecrease?.invoke() }, enabled = onDecrease != null) {
            Icon(Icons.Default.Remove, contentDescription = "$label verringern")
        }
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 8.dp)
        )
        IconButton(onClick = { onIncrease?.invoke() }, enabled = onIncrease != null) {
            Icon(Icons.Default.Add, contentDescription = "$label erhöhen")
        }
    }
}
