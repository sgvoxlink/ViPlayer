@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.viplayer.ui

import androidx.annotation.OptIn as AndroidOptIn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Forward30
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay30
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import com.viplayer.playback.SleepMode
import kotlinx.coroutines.delay
import kotlin.math.ceil

@Composable
fun PlayerScreen(onBack: () -> Unit, onSleep: () -> Unit) {
    val app = app()
    val conn = app.player
    val s by conn.state.collectAsStateWithLifecycle()
    val controller by conn.controller.collectAsStateWithLifecycle()
    val speed by app.settings.speed.collectAsStateWithLifecycle()
    val videoMode by app.settings.videoMode.collectAsStateWithLifecycle()
    val sleep by app.sleepTimer.state.collectAsStateWithLifecycle()
    val showVideo = videoMode && s.isVideo
    VideoTrackEffect(showVideo)

    var speedMenu by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableStateOf<Float?>(null) }
    val nowMs by produceState(System.currentTimeMillis()) {
        while (true) {
            value = System.currentTimeMillis()
            delay(1_000)
        }
    }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Сейчас играет") },
            navigationIcon = {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад") }
            },
            actions = {
                if (s.isVideo) {
                    IconButton(onClick = { app.settings.setVideoMode(!videoMode) }) {
                        Icon(
                            if (videoMode) Icons.Filled.Headphones else Icons.Filled.Videocam,
                            if (videoMode) "Только звук" else "Показать видео",
                        )
                    }
                }
            },
        )
    }) { padding ->
        if (s.mediaId == null) {
            Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Ничего не играет")
            }
            return@Scaffold
        }
        Column(
            Modifier.padding(padding).fillMaxSize().padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                val c = controller
                if (showVideo && c != null) {
                    VideoSurface(c)
                } else {
                    Surface(
                        shape = RoundedCornerShape(32.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(200.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                if (s.isVideo) Icons.Filled.Movie else Icons.Filled.Headphones,
                                null,
                                modifier = Modifier.size(96.dp),
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            Text(
                s.title,
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                s.group,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (s.count > 1) {
                Text(
                    "${s.index + 1} из ${s.count}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(8.dp))
            val dur = s.durationMs.coerceAtLeast(1)
            val fraction = dragFraction ?: (s.positionMs.toFloat() / dur).coerceIn(0f, 1f)
            Slider(
                value = fraction,
                onValueChange = { dragFraction = it },
                onValueChangeFinished = {
                    dragFraction?.let { conn.seekTo((it * dur).toLong()) }
                    dragFraction = null
                },
            )
            Row(Modifier.fillMaxWidth()) {
                val pos = (fraction * dur).toLong()
                Text(formatClock(pos), style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.weight(1f))
                Text(
                    "−" + formatClock(((s.durationMs - pos) / speed).toLong()),
                    style = MaterialTheme.typography.labelMedium,
                )
            }

            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { conn.previous() }) {
                    Icon(Icons.Filled.SkipPrevious, "Предыдущий", Modifier.size(32.dp))
                }
                IconButton(onClick = { conn.seekBack() }) {
                    Icon(Icons.Filled.Replay30, "Назад 30 секунд", Modifier.size(32.dp))
                }
                FilledIconButton(onClick = { conn.togglePlay() }, modifier = Modifier.size(72.dp)) {
                    Icon(
                        if (s.playWhenReady) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        "Пауза / продолжить",
                        Modifier.size(40.dp),
                    )
                }
                IconButton(onClick = { conn.seekForward() }) {
                    Icon(Icons.Filled.Forward30, "Вперёд 30 секунд", Modifier.size(32.dp))
                }
                IconButton(onClick = { conn.next() }, enabled = s.hasNext) {
                    Icon(Icons.Filled.SkipNext, "Следующий", Modifier.size(32.dp))
                }
            }

            Spacer(Modifier.height(8.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Box {
                    AssistChip(
                        onClick = { speedMenu = true },
                        label = { Text(formatSpeed(speed)) },
                        leadingIcon = { Icon(Icons.Filled.Speed, null) },
                    )
                    DropdownMenu(expanded = speedMenu, onDismissRequest = { speedMenu = false }) {
                        SPEEDS.forEach { v ->
                            DropdownMenuItem(
                                text = { Text(formatSpeed(v)) },
                                onClick = {
                                    app.settings.setSpeed(v)
                                    speedMenu = false
                                },
                                trailingIcon = { if (v == speed) Icon(Icons.Filled.Check, null) },
                            )
                        }
                    }
                }
                AssistChip(
                    onClick = onSleep,
                    label = { Text(sleepLabel(sleep, nowMs)) },
                    leadingIcon = { Icon(Icons.Filled.Bedtime, null) },
                )
                AssistChip(
                    onClick = { conn.markCurrentPlayedAndNext() },
                    label = { Text("Прослушано") },
                    leadingIcon = { Icon(Icons.Filled.Done, null) },
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

private fun sleepLabel(mode: SleepMode?, nowMs: Long): String = when (mode) {
    null -> "Сон"
    SleepMode.EndOfTrack -> "До конца файла"
    is SleepMode.At -> {
        val min = ceil((mode.endAtMillis - nowMs).coerceAtLeast(0) / 60_000.0).toInt()
        "Сон: $min мин"
    }
}

@AndroidOptIn(UnstableApi::class)
@Composable
private fun VideoSurface(player: androidx.media3.common.Player) {
    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                useController = false
                keepScreenOn = true
                this.player = player
            }
        },
        update = { it.player = player },
        onRelease = { it.player = null },
        modifier = Modifier.fillMaxSize(),
    )
}

/** Видео декодируется только пока экран плеера на виду и включён режим «Видео». */
@Composable
private fun VideoTrackEffect(enabled: Boolean) {
    val conn = app().player
    val owner = LocalLifecycleOwner.current
    DisposableEffect(enabled, owner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> conn.setVideoEnabled(enabled)
                Lifecycle.Event.ON_STOP -> conn.setVideoEnabled(false)
                else -> Unit
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose {
            owner.lifecycle.removeObserver(observer)
            conn.setVideoEnabled(false)
        }
    }
}
