@file:OptIn(ExperimentalMaterial3Api::class)

package com.viplayer.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.viplayer.playback.SleepMode
import java.time.LocalTime

@Composable
fun SleepTimerDialog(night: Boolean, onDismiss: () -> Unit) {
    val timer = app().sleepTimer
    val mode by timer.state.collectAsStateWithLifecycle()
    var pickTime by remember { mutableStateOf(false) }

    if (pickTime) {
        TimePickDialog(
            onDismiss = { pickTime = false },
            onPick = { h, m ->
                timer.setClock(h, m)
                onDismiss()
            },
        )
        return
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Filled.Bedtime, null) },
        title = { Text(if (night) "Поздно. Включить таймер сна?" else "Таймер сна") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                when (val m = mode) {
                    is SleepMode.At -> Text("Остановится в ${formatTimeOfDay(m.endAtMillis)}")
                    SleepMode.EndOfTrack -> Text("Остановится в конце текущего файла")
                    null -> Unit
                }
                listOf(15, 30, 45, 60, 90, 120).chunked(3).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { min ->
                            FilledTonalButton(
                                onClick = {
                                    timer.setMinutes(min)
                                    onDismiss()
                                },
                                modifier = Modifier.weight(1f),
                            ) { Text("$min мин") }
                        }
                    }
                }
                OutlinedButton(
                    onClick = {
                        timer.setEndOfTrack()
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("В конце текущего файла") }
                OutlinedButton(onClick = { pickTime = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("Выключить в указанное время…")
                }
                if (mode != null) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { timer.extend(15) }, modifier = Modifier.weight(1f)) { Text("+15 мин") }
                        TextButton(
                            onClick = {
                                timer.cancel()
                                onDismiss()
                            },
                            modifier = Modifier.weight(1f),
                        ) { Text("Отключить", color = MaterialTheme.colorScheme.error) }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(if (night && mode == null) "Не надо" else "Закрыть") }
        },
    )
}

@Composable
private fun TimePickDialog(onDismiss: () -> Unit, onPick: (Int, Int) -> Unit) {
    val initial = LocalTime.now().plusHours(1)
    val state = rememberTimePickerState(initial.hour, 0, is24Hour = true)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Остановить воспроизведение в") },
        text = { TimePicker(state = state) },
        confirmButton = { TextButton(onClick = { onPick(state.hour, state.minute) }) { Text("Готово") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}
