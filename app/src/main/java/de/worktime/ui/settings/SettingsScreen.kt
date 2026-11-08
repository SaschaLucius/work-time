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
    val maxWeekly = WorkSessionStore.maxWeeklyTargetMinutes(settings.workDaysPerWeek)
    val canIncreaseWorkDays = settings.workDaysPerWeek < WorkSessionStore.MAX_WORK_DAYS_PER_WEEK &&
        settings.weeklyTargetMinutes <=
        WorkSessionStore.maxWeeklyTargetMinutes(settings.workDaysPerWeek + 1) &&
        WorkSessionStore.dailyTargetMinutes(
            settings.weeklyTargetMinutes,
            settings.workDaysPerWeek + 1
        ) in 1..WorkTimeCalculator.MAX_NET_MINUTES

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
                value = WorkTimeCalculator.formatDuration(settings.weeklyTargetMinutes),
                canDecrease = settings.weeklyTargetMinutes > WorkSessionStore.MIN_WEEKLY_TARGET_MINUTES &&
                    WorkSessionStore.dailyTargetMinutes(
                        settings.weeklyTargetMinutes - 60,
                        settings.workDaysPerWeek
                    ) >= 1,
                canIncrease = settings.weeklyTargetMinutes + 60 <= maxWeekly,
                onDecrease = {
                    onWeeklyTargetChange(
                        (settings.weeklyTargetMinutes - 60)
                            .coerceAtLeast(WorkSessionStore.MIN_WEEKLY_TARGET_MINUTES)
                    )
                },
                onIncrease = {
                    onWeeklyTargetChange(
                        (settings.weeklyTargetMinutes + 60).coerceAtMost(maxWeekly)
                    )
                }
            )
            StepperRow(
                label = "Wochentage",
                value = settings.workDaysPerWeek.toString(),
                canDecrease = settings.workDaysPerWeek > 1 &&
                    WorkSessionStore.dailyTargetMinutes(
                        settings.weeklyTargetMinutes,
                        settings.workDaysPerWeek - 1
                    ) <= WorkTimeCalculator.MAX_NET_MINUTES,
                canIncrease = canIncreaseWorkDays,
                onDecrease = {
                    onWorkDaysPerWeekChange((settings.workDaysPerWeek - 1).coerceAtLeast(1))
                },
                onIncrease = {
                    onWorkDaysPerWeekChange(
                        (settings.workDaysPerWeek + 1)
                            .coerceAtMost(WorkSessionStore.MAX_WORK_DAYS_PER_WEEK)
                    )
                }
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
            StepperRow(
                label = "Nach 6 Stunden",
                value = "${settings.firstBreakMinutes} Min.",
                canDecrease = settings.firstBreakMinutes > 0,
                canIncrease = settings.firstBreakMinutes + 5 <= settings.secondBreakMinutes,
                onDecrease = {
                    onBreakMinutesChange(
                        (settings.firstBreakMinutes - 5).coerceAtLeast(0),
                        settings.secondBreakMinutes
                    )
                },
                onIncrease = {
                    onBreakMinutesChange(
                        settings.firstBreakMinutes + 5,
                        settings.secondBreakMinutes
                    )
                }
            )
            StepperRow(
                label = "Nach 9 Std. 30 Min.",
                value = "${settings.secondBreakMinutes} Min.",
                canDecrease = settings.secondBreakMinutes - 5 >= settings.firstBreakMinutes,
                canIncrease = settings.secondBreakMinutes < 180,
                onDecrease = {
                    onBreakMinutesChange(
                        settings.firstBreakMinutes,
                        settings.secondBreakMinutes - 5
                    )
                },
                onIncrease = {
                    onBreakMinutesChange(
                        settings.firstBreakMinutes,
                        (settings.secondBreakMinutes + 5).coerceAtMost(180)
                    )
                }
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
                value = "${settings.notificationOffsetMinutes} Min.",
                canDecrease = settings.notificationOffsetMinutes > 0,
                canIncrease = settings.notificationOffsetMinutes + 5 <=
                    settings.dailyTargetMinutes,
                onDecrease = {
                    onNotificationOffsetChange(
                        (settings.notificationOffsetMinutes - 5).coerceAtLeast(0)
                    )
                },
                onIncrease = {
                    onNotificationOffsetChange(
                        (settings.notificationOffsetMinutes + 5)
                            .coerceAtMost(settings.dailyTargetMinutes)
                    )
                }
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

@Composable
private fun StepperRow(
    label: String,
    value: String,
    canDecrease: Boolean,
    canIncrease: Boolean,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit
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
        IconButton(onClick = onDecrease, enabled = canDecrease) {
            Icon(Icons.Default.Remove, contentDescription = "$label verringern")
        }
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 8.dp)
        )
        IconButton(onClick = onIncrease, enabled = canIncrease) {
            Icon(Icons.Default.Add, contentDescription = "$label erhöhen")
        }
    }
}
