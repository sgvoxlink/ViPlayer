@file:OptIn(ExperimentalMaterial3Api::class)

package com.viplayer.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.viplayer.data.GroupSummary

@Composable
fun LibraryScreen(onOpenGroup: (String) -> Unit) {
    val app = app()
    val groups by remember { app.repo.groups() }.collectAsStateWithLifecycle(emptyList())
    val folders by remember { app.repo.folders() }.collectAsStateWithLifecycle(null)
    val scan by app.repo.scanState.collectAsStateWithLifecycle()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) app.repo.addFolder(uri)
    }

    ScreenScaffold(topBar = {
        TopAppBar(
            title = { Text("Библиотека") },
            actions = {
                IconButton(onClick = { app.repo.rescan() }, enabled = !scan.running) {
                    Icon(Icons.Filled.Refresh, "Пересканировать")
                }
                IconButton(onClick = { picker.launch(null) }) {
                    Icon(Icons.Filled.CreateNewFolder, "Добавить папку")
                }
            },
        )
    }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            if (scan.running) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Text(
                    scan.message,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
            val list = folders
            when {
                list == null -> Unit
                list.isEmpty() -> EmptyLibrary { picker.launch(null) }
                else -> LazyColumn(Modifier.fillMaxSize()) {
                    items(groups, key = { it.groupName }) { g ->
                        GroupRow(
                            g,
                            onClick = { onOpenGroup(g.groupName) },
                            onPlay = { app.player.playFolder(g.groupName) },
                            onQueue = { app.repo.addGroupToQueue(g.groupName) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GroupRow(g: GroupSummary, onClick: () -> Unit, onPlay: () -> Unit, onQueue: () -> Unit) {
    val parts = g.groupName.split(" / ")
    val left = g.total - g.playedCount
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        leadingContent = {
            Icon(
                Icons.Filled.Folder, null,
                tint = if (left == 0) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.primary,
            )
        },
        overlineContent = if (parts.size > 1) {
            { Text(parts.dropLast(1).joinToString(" / "), maxLines = 1, overflow = TextOverflow.Ellipsis) }
        } else {
            null
        },
        headlineContent = { Text(parts.last(), maxLines = 2, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            Column {
                Text(
                    if (left == 0) "Всё прослушано · ${g.total} файлов"
                    else "Осталось $left из ${g.total} · ${formatLong(g.remainingMs)}"
                )
                if (g.total > 0) {
                    LinearProgressIndicator(
                        progress = { g.playedCount.toFloat() / g.total },
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                    )
                }
            }
        },
        trailingContent = {
            Row {
                IconButton(onClick = onQueue) { Icon(Icons.AutoMirrored.Filled.PlaylistAdd, "Непрослушанные — в плейлист") }
                IconButton(onClick = onPlay) { Icon(Icons.Filled.PlayArrow, "Играть папку") }
            }
        },
    )
}

@Composable
private fun EmptyLibrary(onAdd: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            Icons.Filled.LibraryMusic, null,
            modifier = Modifier.size(72.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            "Выберите папку, где лежат лекции, подкасты или видео. Подпапки станут разделами библиотеки.",
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyLarge,
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onAdd) { Text("Выбрать папку") }
    }
}
