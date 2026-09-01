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
import androidx.core.content.ContextCompat

@Composable
fun SettingsScreen(
    settings: WorkSessionStore.AppSettings,
    onBreakMinutesChange: (first: Int, second: Int) -> Unit,
    onDailyTargetChange: (minutes: Int) -> Unit,
    onNotificationsEnabledChange: (Boolean) -> Unit,
    onNotificationOffsetChange: (Int) -> Unit
) {
    val context = LocalContext.current
    var permissionDenied by rememberSaveable { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        permissionDenied = !granted
        onNotificationsEnabledChange(granted)
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
                label = "Tagesziel",
                value = WorkTimeCalculator.formatDuration(settings.dailyTargetMinutes),
                canDecrease = settings.dailyTargetMinutes > 15,
                canIncrease = settings.dailyTargetMinutes < WorkTimeCalculator.MAX_NET_MINUTES,
                onDecrease = {
                    onDailyTargetChange((settings.dailyTargetMinutes - 15).coerceAtLeast(15))
                },
                onIncrease = {
                    onDailyTargetChange(
                        (settings.dailyTargetMinutes + 15)
                            .coerceAtMost(WorkTimeCalculator.MAX_NET_MINUTES)
                    )
                }
            )
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
                    text = "Beim Tagesziel erinnern",
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
                label = "Früher erinnern",
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