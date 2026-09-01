package de.worktime.ui.common

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Zeitauswahl-Dialog mit umschaltbarer Eingabeart:
 * Standardmäßig Zahleneingabe (einfach), optional die klassische Uhr (Wählscheibe).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ZeitPickerDialog(
    title: String,
    initialHour: Int,
    initialMinute: Int,
    onConfirm: (hour: Int, minute: Int) -> Unit,
    onDismiss: () -> Unit
) {
    val pickerState = rememberTimePickerState(
        initialHour = initialHour,
        initialMinute = initialMinute,
        is24Hour = true
    )
    var useTextInput by rememberSaveable { mutableStateOf(true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                if (useTextInput) {
                    TimeInput(state = pickerState)
                } else {
                    TimePicker(state = pickerState)
                }
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = { useTextInput = !useTextInput }) {
                    Icon(
                        imageVector = if (useTextInput) Icons.Filled.Schedule else Icons.Filled.Keyboard,
                        contentDescription = null
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(if (useTextInput) "Uhr anzeigen" else "Zahleneingabe")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(pickerState.hour, pickerState.minute) }) {
                Text("OK")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Abbrechen") }
        }
    )
}
