@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package com.viplayer.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.PlaylistAddCheck
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RemoveDone
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.viplayer.data.MediaEntity
import com.viplayer.data.remainingMs
import com.viplayer.data.title
import kotlinx.coroutines.flow.map

@Composable
fun FolderScreen(group: String, onBack: () -> Unit) {
    val app = app()
    val files by remember(group) { app.repo.filesInGroup(group) }.collectAsStateWithLifecycle(emptyList())
    val queueIds by remember {
        app.repo.queue().map { rows -> rows.mapTo(HashSet()) { it.media.id } }
    }.collectAsStateWithLifecycle(emptySet())
    val now by app.player.state.collectAsStateWithLifecycle()

    var selected by remember { mutableStateOf(setOf<Long>()) }
    val selecting = selected.isNotEmpty()
    var menu by remember { mutableStateOf(false) }
    BackHandler(enabled = selecting) { selected = emptySet() }

    fun toggle(id: Long) {
        selected = if (id in selected) selected - id else selected + id
    }

    val leftCount = files.count { !it.played }
    val leftMs = files.sumOf { it.remainingMs }

    ScreenScaffold(topBar = {
        if (selecting) {
            TopAppBar(
                title = { Text("Выбрано: ${selected.size}") },
                navigationIcon = {
                    IconButton(onClick = { selected = emptySet() }) { Icon(Icons.Filled.Close, "Отменить выбор") }
                },
                actions = {
                    IconButton(onClick = { selected = files.mapTo(HashSet()) { it.id } }) {
                        Icon(Icons.Filled.SelectAll, "Выбрать все")
                    }
                    IconButton(onClick = {
                        app.repo.addToQueue(files.filter { it.id in selected }.map { it.id })
                        selected = emptySet()
                    }) { Icon(Icons.AutoMirrored.Filled.PlaylistAdd, "В плейлист") }
                    IconButton(onClick = {
                        app.repo.setPlayed(selected.toList(), true)
                        selected = emptySet()
                    }) { Icon(Icons.Filled.DoneAll, "Отметить прослушанными") }
                    IconButton(onClick = {
                        app.repo.setPlayed(selected.toList(), false)
                        selected = emptySet()
                    }) { Icon(Icons.Filled.RemoveDone, "Снять отметку") }
                },
            )
        } else {
            TopAppBar(
                title = {
                    Column {
                        Text(group.substringAfterLast(" / "), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            "Осталось $leftCount из ${files.size} · ${formatLong(leftMs)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад") }
                },
                actions = {
                    IconButton(onClick = { app.player.playFolder(group) }) {
                        Icon(Icons.Filled.PlayArrow, "Играть непрослушанные")
                    }
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "Ещё") }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(
                                text = { Text("Непрослушанные — в плейлист") },
                                onClick = {
                                    menu = false
                                    app.repo.addGroupToQueue(group)
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Отметить все прослушанными") },
                                onClick = {
                                    menu = false
                                    app.repo.setPlayed(files.map { it.id }, true)
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Сбросить все отметки") },
                                onClick = {
                                    menu = false
                                    app.repo.setPlayed(files.map { it.id }, false)
                                },
                            )
                        }
                    }
                },
            )
        }
    }) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize()) {
            items(files, key = { it.id }) { m ->
                FileRow(
                    m = m,
                    isCurrent = now.mediaId == m.id,
                    inQueue = m.id in queueIds,
                    selected = m.id in selected,
                    onClick = { if (selecting) toggle(m.id) else app.player.playFolder(group, m.id) },
                    onLongClick = { toggle(m.id) },
                    onQueue = { app.repo.addToQueue(listOf(m.id)) },
                )
            }
        }
    }
}

@Composable
private fun FileRow(
    m: MediaEntity,
    isCurrent: Boolean,
    inQueue: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onQueue: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val subtitle = when {
        m.durationMs <= 0 -> if (m.played) "прослушано" else "—"
        m.played -> "${formatClock(m.durationMs)} · прослушано"
        m.positionMs > 0 -> "${formatClock(m.durationMs)} · осталось ${formatClock(m.remainingMs)}"
        else -> formatClock(m.durationMs)
    }
    ListItem(
        modifier = Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick),
        colors = if (selected) ListItemDefaults.colors(containerColor = colors.secondaryContainer)
        else ListItemDefaults.colors(),
        leadingContent = {
            Icon(
                when {
                    selected -> Icons.Filled.CheckCircle
                    isCurrent -> Icons.AutoMirrored.Filled.VolumeUp
                    m.played -> Icons.Filled.Done
                    m.isVideo -> Icons.Filled.Movie
                    else -> Icons.Filled.MusicNote
                },
                null,
                tint = if (m.played && !isCurrent && !selected) colors.outline else colors.primary,
            )
        },
        headlineContent = {
            Text(
                m.title,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = if (m.played && !isCurrent) colors.onSurfaceVariant else Color.Unspecified,
                fontWeight = if (isCurrent) FontWeight.Bold else null,
            )
        },
        supportingContent = {
            Column {
                Text(subtitle)
                if (!m.played && m.positionMs > 0 && m.durationMs > 0) {
                    LinearProgressIndicator(
                        progress = { (m.positionMs.toFloat() / m.durationMs).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                    )
                }
            }
        },
        trailingContent = {
            IconButton(onClick = onQueue, enabled = !inQueue) {
                Icon(
                    if (inQueue) Icons.AutoMirrored.Filled.PlaylistAddCheck else Icons.AutoMirrored.Filled.PlaylistAdd,
                    if (inQueue) "Уже в плейлисте" else "В плейлист",
                )
            }
        },
    )
}
