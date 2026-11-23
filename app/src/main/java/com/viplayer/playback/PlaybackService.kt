package com.viplayer.playback

import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.viplayer.R
import com.viplayer.ViPlayerApp
import com.viplayer.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.guava.future
import kotlinx.coroutines.launch

/**
 * Фоновый сервис воспроизведения. Media3 сам держит уведомление, экран блокировки,
 * аудиофокус и паузу при отключении наушников.
 */
@OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {

    private var session: MediaSession? = null
    private lateinit var player: ExoPlayer
    private lateinit var tracker: ProgressTracker
    private lateinit var headset: HeadsetClickHandler
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val app get() = application as ViPlayerApp

    override fun onCreate() {
        super.onCreate()
        player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
                    .build(),
                /* handleAudioFocus = */ true,
            )
            .setHandleAudioBecomingNoisy(true) // пауза при отключении наушников
            .setWakeMode(C.WAKE_MODE_LOCAL) // не засыпать с выключенным экраном
            .setSeekBackIncrementMs(30_000)
            .setSeekForwardIncrementMs(30_000)
            .build()
        // По умолчанию видео не декодируем — экономим батарею. Экран плеера включает его сам.
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, true)
            .build()

        tracker = ProgressTracker(player, app.repo, app.appScope)
        player.addListener(tracker)
        player.addListener(serviceListener)
        headset = HeadsetClickHandler(player)

        val openApp = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        session = MediaSession.Builder(this, player)
            .setCallback(SessionCallback())
            .setSessionActivity(openApp)
            .setCustomLayout(
                listOf(
                    CommandButton.Builder()
                        .setDisplayName("Назад 30 с")
                        .setIconResId(R.drawable.ic_replay_30)
                        .setSessionCommand(SessionCommand(ACTION_REWIND, Bundle.EMPTY))
                        .build(),
                    CommandButton.Builder()
                        .setDisplayName("Вперёд 30 с")
                        .setIconResId(R.drawable.ic_forward_30)
                        .setSessionCommand(SessionCommand(ACTION_FORWARD, Bundle.EMPTY))
                        .build(),
                )
            )
            .build()

        scope.launch { app.settings.speed.collect { player.playbackParameters = PlaybackParameters(it) } }
        scope.launch { app.settings.skipSilence.collect { player.skipSilenceEnabled = it } }
        scope.launch { runSleepTimer() }
    }

    private suspend fun runSleepTimer() {
        app.sleepTimer.state.collectLatest { mode ->
            player.volume = 1f
            player.pauseAtEndOfMediaItems = mode is SleepMode.EndOfTrack
            if (mode is SleepMode.At) {
                while (true) {
                    val left = mode.endAtMillis - System.currentTimeMillis()
                    if (left <= 0) {
                        player.pause()
                        app.sleepTimer.cancel()
                        break
                    }
                    player.volume = if (app.settings.fadeOut.value && left < FADE_MS) {
                        (left.toFloat() / FADE_MS).coerceAtLeast(0.05f)
                    } else {
                        1f
                    }
                    delay(if (left < FADE_MS) 250 else 1_000)
                }
            }
        }
    }

    private val serviceListener = object : Player.Listener {
        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            if (reason == Player.PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM &&
                app.sleepTimer.state.value is SleepMode.EndOfTrack
            ) {
                app.sleepTimer.cancel()
            }
        }

        override fun onTimelineChanged(timeline: Timeline, reason: Int) {
            if (reason == Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED && player.mediaItemCount > 0) {
                app.settings.resumeIds = (0 until player.mediaItemCount).mapNotNull {
                    player.getMediaItemAt(it).mediaId.toLongOrNull()
                }
            }
        }
    }

    private inner class SessionCallback : MediaSession.Callback {

        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): MediaSession.ConnectionResult {
            val commands = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                .add(SessionCommand(ACTION_REWIND, Bundle.EMPTY))
                .add(SessionCommand(ACTION_FORWARD, Bundle.EMPTY))
                .build()
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(commands)
                .build()
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle,
        ): ListenableFuture<SessionResult> {
            when (customCommand.customAction) {
                ACTION_REWIND -> player.seekBack()
                ACTION_FORWARD -> player.seekForward()
            }
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        }

        /** Контроллер передаёт только mediaId — восстанавливаем полный MediaItem из базы. */
        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
        ): ListenableFuture<MutableList<MediaItem>> = scope.future {
            mediaItems.map { item ->
                if (item.localConfiguration != null) {
                    item
                } else {
                    item.mediaId.toLongOrNull()?.let { app.repo.media(it) }?.let(MediaItems::from) ?: item
                }
            }.toMutableList()
        }

        /** Продолжение воспроизведения кнопкой гарнитуры, когда приложение было выгружено. */
        override fun onPlaybackResumption(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> = scope.future {
            var list = app.repo.mediaByIds(app.settings.resumeIds).filter { !it.played }
            if (list.isEmpty()) list = app.repo.queueNow().map { it.media }.filter { !it.played }
            check(list.isNotEmpty()) { "Нечего продолжать" }
            val first = list.first()
            val start = if (first.positionMs > 5_000) first.positionMs - 3_000 else 0L
            MediaSession.MediaItemsWithStartPosition(list.map(MediaItems::from), 0, start)
        }

        override fun onMediaButtonEvent(
            session: MediaSession,
            controllerInfo: MediaSession.ControllerInfo,
            intent: Intent,
        ): Boolean = headset.handle(intent)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        val p = session?.player
        if (p == null || !p.playWhenReady || p.mediaItemCount == 0 || p.playbackState == Player.STATE_ENDED) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        tracker.release()
        headset.release()
        scope.cancel()
        session?.run {
            player.release()
            release()
        }
        session = null
        super.onDestroy()
    }

    companion object {
        const val ACTION_REWIND = "viplayer.REWIND"
        const val ACTION_FORWARD = "viplayer.FORWARD"
        private const val FADE_MS = 30_000L
    }
}
