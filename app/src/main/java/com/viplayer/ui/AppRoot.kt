package com.viplayer.ui

import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay30
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.viplayer.playback.NowPlaying

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val TABS = listOf(
    Tab("library", "Библиотека", Icons.Filled.LibraryMusic),
    Tab("playlist", "Плейлист", Icons.AutoMirrored.Filled.QueueMusic),
    Tab("settings", "Настройки", Icons.Filled.Settings),
)

@Composable
fun AppRoot() {
    val app = app()
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    val now by app.player.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    // null — диалог скрыт; true — ночное предложение; false — открыт вручную
    var sleepDialog by remember { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(Unit) { app.player.nightSuggestion.collect { sleepDialog = true } }
    LaunchedEffect(Unit) { app.messages.collect { snackbar.showSnackbar(it) } }

    val isPlayer = route == "player"
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (!isPlayer) {
                Column {
                    if (now.mediaId != null) MiniPlayer(now) { nav.navigate("player") }
                    NavigationBar {
                        TABS.forEach { tab ->
                            val selected = when (tab.route) {
                                "library" -> route == "library" || route?.startsWith("folder") == true
                                else -> route == tab.route
                            }
                            NavigationBarItem(
                                selected = selected,
                                onClick = {
                                    nav.navigate(tab.route) {
                                        popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = { Icon(tab.icon, null) },
                                label = { Text(tab.label) },
                            )
                        }
                    }
                }
            }
        },
    ) { padding ->
        NavHost(nav, startDestination = "library", modifier = Modifier.padding(padding)) {
            composable("library") {
                LibraryScreen(onOpenGroup = { nav.navigate("folder/" + Uri.encode(it)) })
            }
            composable(
                "folder/{group}",
                arguments = listOf(navArgument("group") { type = NavType.StringType }),
            ) { entry ->
                FolderScreen(
                    group = entry.arguments?.getString("group").orEmpty(),
                    onBack = { nav.popBackStack() },
                )
            }
            composable("playlist") { PlaylistScreen() }
            composable("settings") { SettingsScreen() }
            composable("player") {
                PlayerScreen(onBack = { nav.popBackStack() }, onSleep = { sleepDialog = false })
            }
        }
    }

    sleepDialog?.let { night -> SleepTimerDialog(night = night, onDismiss = { sleepDialog = null }) }
}

@Composable
private fun MiniPlayer(s: NowPlaying, onOpen: () -> Unit) {
    val conn = app().player
    Surface(
        tonalElevation = 3.dp,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen),
    ) {
        Column {
            LinearProgressIndicator(
                progress = { if (s.durationMs > 0) (s.positionMs.toFloat() / s.durationMs).coerceIn(0f, 1f) else 0f },
                modifier = Modifier.fillMaxWidth().height(2.dp),
            )
            Row(
                Modifier.padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(s.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        s.group,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = { conn.seekBack() }) { Icon(Icons.Filled.Replay30, "Назад 30 секунд") }
                IconButton(onClick = { conn.togglePlay() }) {
                    Icon(if (s.playWhenReady) Icons.Filled.Pause else Icons.Filled.PlayArrow, "Пауза / продолжить")
                }
                IconButton(onClick = { conn.next() }, enabled = s.hasNext) { Icon(Icons.Filled.SkipNext, "Следующий") }
            }
        }
    }
}
