@file:OptIn(ExperimentalMaterial3Api::class)

package com.viplayer.ui

import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings as AndroidSettings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.viplayer.data.FolderEntity
import java.util.Locale

@Composable
fun SettingsScreen() {
    val app = app()
    val s = app.settings
    val context = LocalContext.current
    val folders by remember { app.repo.folders() }.collectAsStateWithLifecycle(emptyList())
    val playedCount by remember { app.repo.playedCount() }.collectAsStateWithLifecycle(0)
    val skipSilence by s.skipSilence.collectAsStateWithLifecycle()
    val nightSuggest by s.nightSuggest.collectAsStateWithLifecycle()
    val nightStart by s.nightStart.collectAsStateWithLifecycle()
    val nightEnd by s.nightEnd.collectAsStateWithLifecycle()
    val fadeOut by s.fadeOut.collectAsStateWithLifecycle()
    var confirmRemove by remember { mutableStateOf<FolderEntity?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) app.repo.addFolder(uri)
    }

    ScreenScaffold(topBar = { TopAppBar(title = { Text("Настройки") }) }) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize()) {
            item { SectionTitle("Папки") }
            items(folders, key = { it.id }) { f ->
                ListItem(
                    leadingContent = { Icon(Icons.Filled.Folder, null) },
                    headlineContent = { Text(f.name) },
                    supportingContent = { Text(Uri.parse(f.treeUri).lastPathSegment.orEmpty()) },
                    trailingContent = {
                        IconButton(onClick = { confirmRemove = f }) { Icon(Icons.Filled.Delete, "Убрать папку") }
                    },
                )
            }
            item {
                Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { picker.launch(null) }) {
                        Icon(Icons.Filled.Add, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Добавить папку")
                    }
                    TextButton(onClick = { app.repo.rescan() }) { Text("Пересканировать") }
                }
            }

            item { SectionTitle("Воспроизведение") }
            item {
                SwitchRow(
                    "Пропускать тишину",
                    "Ускоряет длинные паузы в речи",
                    skipSilence,
                ) { s.setSkipSilence(it) }
            }

            item { SectionTitle("Таймер сна") }
            item {
                SwitchRow(
                    "Предлагать таймер ночью",
                    "При запуске воспроизведения в ночные часы",
                    nightSuggest,
                ) { s.setNightSuggest(it) }
            }
            item {
                Row(
                    Modifier.padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    HourPicker("Ночь с", nightStart) { s.setNightStart(it) }
                    HourPicker("до", nightEnd) { s.setNightEnd(it) }
                }
            }
            item {
                SwitchRow(
                    "Плавное затихание",
                    "Громкость снижается за 30 секунд до остановки",
                    fadeOut,
                ) { s.setFadeOut(it) }
            }

            item { SectionTitle("Кнопка гарнитуры") }
            item {
                ListItem(
                    leadingContent = { Icon(Icons.Filled.Headphones, null) },
                    headlineContent = { Text("1 нажатие — пауза / продолжить") },
                    supportingContent = {
                        Text("2 нажатия — следующий файл\n3 нажатия — назад на 30 секунд")
                    },
                )
            }

            item { SectionTitle("Работа в фоне") }
            item {
                val pm = context.getSystemService(PowerManager::class.java)
                val ignoring = pm?.isIgnoringBatteryOptimizations(context.packageName) == true
                ListItem(
                    modifier = Modifier.clickable {
                        context.startActivity(Intent(AndroidSettings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                    },
                    leadingContent = {
                        Icon(if (ignoring) Icons.Filled.BatteryFull else Icons.Filled.BatteryAlert, null)
                    },
                    headlineContent = { Text("Оптимизация батареи") },
                    supportingContent = {
                        Text(
                            if (ignoring) "Отключена — система не будет останавливать плеер"
                            else "Включена. Если воспроизведение обрывается в кармане, " +
                                "нажмите и выберите для ViPlayer «Не оптимизировать»."
                        )
                    },
                )
            }

            item { SectionTitle("Статистика") }
            item {
                ListItem(
                    headlineContent = { Text("Прослушано файлов: $playedCount") },
                    supportingContent = {
                        val version = try {
                            context.packageManager.getPackageInfo(context.packageName, 0).versionName
                        } catch (e: Exception) {
                            null
                        }
                        Text("ViPlayer ${version.orEmpty()}")
                    },
                )
            }
        }
    }

    confirmRemove?.let { f ->
        AlertDialog(
            onDismissRequest = { confirmRemove = null },
            title = { Text("Убрать папку «${f.name}»?") },
            text = {
                Text("Файлы на телефоне останутся. Из приложения пропадут её файлы, отметки о прослушивании и записи в плейлисте.")
            },
            confirmButton = {
                TextButton(onClick = {
                    app.repo.removeFolder(f)
                    confirmRemove = null
                }) { Text("Убрать") }
            },
            dismissButton = { TextButton(onClick = { confirmRemove = null }) { Text("Отмена") } },
        )
    }
}

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    ListItem(
        modifier = Modifier.clickable { onChange(!checked) },
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        trailingContent = { Switch(checked = checked, onCheckedChange = onChange) },
    )
}

@Composable
private fun HourPicker(label: String, value: Int, onChange: (Int) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { open = true }) {
            Text("$label ${String.format(Locale.US, "%02d:00", value)}")
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            (0..23).forEach { h ->
                DropdownMenuItem(
                    text = { Text(String.format(Locale.US, "%02d:00", h)) },
                    onClick = {
                        onChange(h)
                        open = false
                    },
                )
            }
        }
    }
}
