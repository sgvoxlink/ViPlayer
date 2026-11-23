@file:OptIn(ExperimentalMaterial3Api::class)

package com.viplayer.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.viplayer.data.remainingMs
import com.viplayer.data.title
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@Composable
fun PlaylistScreen() {
    val app = app()
    val rows by remember { app.repo.queue() }.collectAsStateWithLifecycle(emptyList())
    val speed by app.settings.speed.collectAsStateWithLifecycle()
    val now by app.player.state.collectAsStateWithLifecycle()

    var list by remember { mutableStateOf(rows) }
    LaunchedEffect(rows) { list = rows }
    var menu by remember { mutableStateOf(false) }

    val lazyState = rememberLazyListState()
    val reorderState = rememberReorderableLazyListState(lazyState) { from, to ->
        list = list.toMutableList().apply { add(to.index, removeAt(from.index)) }
    }

    ScreenScaffold(topBar = {
        TopAppBar(
            title = { Text("Плейлист") },
            actions = {
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "Ещё") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(
                            text = { Text("Убрать прослушанные") },
                            onClick = {
                                menu = false
                                app.repo.clearPlayedFromQueue()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Очистить плейлист") },
                            onClick = {
                                menu = false
                                app.repo.clearQueue()
                            },
                        )
                    }
                }
            },
        )
    }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            if (list.isEmpty()) {
                EmptyPlaylist()
            } else {
                val left = list.filter { !it.media.played }
                val leftMs = left.sumOf { it.media.remainingMs }
                Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Осталось ${formatLong(leftMs)}", style = MaterialTheme.typography.titleMedium)
                            Text(
                                buildString {
                                    append("${left.size} из ${list.size} файлов")
                                    if (speed != 1f) {
                                        append(" · при ${formatSpeed(speed)} — ${formatLong((leftMs / speed).toLong())}")
                                    }
                                },
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        FilledIconButton(onClick = { app.player.playQueue() }, modifier = Modifier.size(56.dp)) {
                            Icon(Icons.Filled.PlayArrow, "Играть плейлист", Modifier.size(32.dp))
                        }
                    }
                }
                LazyColumn(state = lazyState, modifier = Modifier.weight(1f)) {
                    items(list, key = { it.queueId }) { row ->
                        ReorderableItem(reorderState, key = row.queueId) { isDragging ->
                            val m = row.media
                            val isCurrent = now.mediaId == m.id
                            val colors = MaterialTheme.colorScheme
                            Surface(shadowElevation = if (isDragging) 8.dp else 0.dp) {
                                ListItem(
                                    modifier = Modifier.clickable { app.player.playQueue(m.id) },
                                    leadingContent = {
                                        IconButton(
                                            onClick = {},
                                            modifier = Modifier.draggableHandle(
                                                onDragStopped = { app.repo.reorderQueue(list.map { it.queueId }) },
                                            ),
                                        ) { Icon(Icons.Filled.DragHandle, "Перетащить") }
                                    },
                                    headlineContent = {
                                        Text(
                                            m.title,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                            fontWeight = if (isCurrent) FontWeight.Bold else null,
                                            color = if (m.played && !isCurrent) colors.onSurfaceVariant else Color.Unspecified,
                                        )
                                    },
                                    supportingContent = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            if (isCurrent) {
                                                Icon(Icons.AutoMirrored.Filled.VolumeUp, null, Modifier.size(16.dp), tint = colors.primary)
                                            } else if (m.played) {
                                                Icon(Icons.Filled.Done, null, Modifier.size(16.dp), tint = colors.outline)
                                            }
                                            Text(
                                                buildString {
                                                    append(m.groupName.substringAfterLast(" / "))
                                                    if (m.durationMs > 0) {
                                                        append(" · ")
                                                        append(if (m.played) "прослушано" else formatClock(m.remainingMs))
                                                    }
                                                },
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.padding(start = if (isCurrent || m.played) 4.dp else 0.dp),
                                            )
                                        }
                                    },
                                    trailingContent = {
                                        IconButton(onClick = { app.repo.removeFromQueue(row.queueId) }) {
                                            Icon(Icons.Filled.Close, "Убрать из плейлиста")
                                        }
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyPlaylist() {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            Icons.AutoMirrored.Filled.QueueMusic, null,
            modifier = Modifier.size(72.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            "Плейлист пуст.\nОткройте папку в библиотеке и нажмите «+» у нужных файлов " +
                "или выделите несколько долгим нажатием.",
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}
