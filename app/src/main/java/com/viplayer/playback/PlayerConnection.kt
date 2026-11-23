package com.viplayer.playback

import android.content.ComponentName
import androidx.core.content.ContextCompat
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.viplayer.ViPlayerApp
import com.viplayer.data.MediaEntity
import com.viplayer.data.QueueRow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Что именно сейчас воспроизводится. */
enum class PlayContext { NONE, QUEUE, FOLDER, SINGLE }

data class NowPlaying(
    val mediaId: Long? = null,
    val title: String = "",
    val group: String = "",
    val isVideo: Boolean = false,
    val isPlaying: Boolean = false,
    val playWhenReady: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val index: Int = 0,
    val count: Int = 0,
    val hasNext: Boolean = false,
)

/** Мост между UI и PlaybackService (через MediaController). */
class PlayerConnection(private val app: ViPlayerApp, private val scope: CoroutineScope) {

    private var future: ListenableFuture<MediaController>? = null
    private val _controller = MutableStateFlow<MediaController?>(null)
    val controller: StateFlow<MediaController?> = _controller.asStateFlow()

    private val _state = MutableStateFlow(NowPlaying())
    val state: StateFlow<NowPlaying> = _state.asStateFlow()

    private val _context = MutableStateFlow(PlayContext.NONE)
    val playContext: StateFlow<PlayContext> = _context.asStateFlow()

    private val _nightSuggestion = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val nightSuggestion: SharedFlow<Unit> = _nightSuggestion

    private val pending = ArrayDeque<(MediaController) -> Unit>()
    private var lastSuggestAt = 0L

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = refresh()
    }

    init {
        scope.launch {
            while (isActive) {
                delay(500)
                if (_controller.value?.isPlaying == true) refresh()
            }
        }
        // Плейлист изменили во время его воспроизведения — синхронизируем очередь плеера.
        scope.launch {
            app.repo.queue().collect { rows ->
                if (_context.value == PlayContext.QUEUE) syncQueue(rows)
            }
        }
    }

    fun connect() {
        if (future != null) return
        val token = SessionToken(app, ComponentName(app, PlaybackService::class.java))
        val f = MediaController.Builder(app, token)
            .setListener(object : MediaController.Listener {
                override fun onDisconnected(controller: MediaController) {
                    _controller.value = null
                    future = null
                    refresh()
                }
            })
            .buildAsync()
        future = f
        f.addListener({
            val c = try {
                f.get()
            } catch (e: Exception) {
                future = null
                return@addListener
            }
            c.addListener(listener)
            _controller.value = c
            refresh()
            while (pending.isNotEmpty()) pending.removeFirst().invoke(c)
        }, ContextCompat.getMainExecutor(app))
    }

    private fun withController(block: (MediaController) -> Unit) {
        val c = _controller.value
        if (c != null && c.isConnected) {
            block(c)
        } else {
            pending.addLast(block)
            connect()
        }
    }

    private fun refresh() {
        val c = _controller.value
        if (c == null) {
            _state.value = NowPlaying()
            return
        }
        val item = c.currentMediaItem
        val md = item?.mediaMetadata
        _state.value = NowPlaying(
            mediaId = item?.mediaId?.toLongOrNull(),
            title = md?.title?.toString().orEmpty(),
            group = md?.albumTitle?.toString().orEmpty(),
            isVideo = md?.extras?.getBoolean(MediaItems.EXTRA_VIDEO) == true,
            isPlaying = c.isPlaying,
            playWhenReady = c.playWhenReady && c.playbackState != Player.STATE_ENDED,
            positionMs = c.currentPosition.coerceAtLeast(0),
            durationMs = c.duration.takeIf { it != C.TIME_UNSET }?.coerceAtLeast(0) ?: 0,
            index = c.currentMediaItemIndex,
            count = c.mediaItemCount,
            hasNext = c.hasNextMediaItem(),
        )
    }

    // ---------- Запуск воспроизведения ----------

    /** Играть плейлист: непрослушанные по порядку (или начиная с выбранного файла). */
    fun playQueue(startMediaId: Long? = null) = scope.launch {
        val list = pickFrom(app.repo.queueNow().map { it.media }, startMediaId)
        if (list.isEmpty()) {
            app.notify("В плейлисте нет непрослушанных файлов")
            return@launch
        }
        start(list, PlayContext.QUEUE)
    }

    /** Играть папку: непрослушанные по порядку (или начиная с выбранного файла). */
    fun playFolder(group: String, startMediaId: Long? = null) = scope.launch {
        val list = pickFrom(app.repo.filesInGroupNow(group), startMediaId)
        if (list.isEmpty()) {
            app.notify("Всё в этой папке уже прослушано")
            return@launch
        }
        start(list, PlayContext.FOLDER)
    }

    fun playSingle(media: MediaEntity) = start(listOf(media), PlayContext.SINGLE)

    private fun pickFrom(all: List<MediaEntity>, startId: Long?): List<MediaEntity> {
        val idx = if (startId == null) -1 else all.indexOfFirst { it.id == startId }
        if (idx < 0) return all.filter { !it.played }
        return listOf(all[idx]) + all.drop(idx + 1).filter { !it.played }
    }

    private fun start(items: List<MediaEntity>, ctx: PlayContext) {
        _context.value = ctx
        val first = items.first()
        val startPos = if (!first.played && first.positionMs > 5_000) first.positionMs - 3_000 else 0L
        withController { c ->
            c.setMediaItems(items.map(MediaItems::from), 0, startPos)
            c.prepare()
            c.play()
        }
        maybeSuggestSleep()
    }

    private fun syncQueue(rows: List<QueueRow>) {
        val c = _controller.value ?: return
        if (c.mediaItemCount == 0) return
        val curId = c.currentMediaItem?.mediaId?.toLongOrNull() ?: return
        val curIdx = c.currentMediaItemIndex
        val pos = rows.indexOfFirst { it.media.id == curId }
        val desired = (if (pos < 0) rows else rows.drop(pos + 1))
            .map { it.media }
            .filter { !it.played && it.id != curId }
        val upcoming = (curIdx + 1 until c.mediaItemCount).map { c.getMediaItemAt(it).mediaId }
        if (upcoming != desired.map { it.id.toString() }) {
            c.replaceMediaItems(curIdx + 1, c.mediaItemCount, desired.map(MediaItems::from))
        }
    }

    // ---------- Управление ----------

    fun togglePlay() = withController { c ->
        if (c.mediaItemCount == 0) {
            playQueue()
            return@withController
        }
        if (c.playWhenReady && c.playbackState != Player.STATE_ENDED) {
            c.pause()
        } else {
            if (c.playbackState == Player.STATE_IDLE) c.prepare()
            if (c.playbackState == Player.STATE_ENDED) c.seekToDefaultPosition()
            c.play()
            maybeSuggestSleep()
        }
    }

    fun next() = withController { it.seekToNextMediaItem() }
    fun previous() = withController { it.seekToPrevious() }
    fun seekBack() = withController { it.seekBack() }
    fun seekForward() = withController { it.seekForward() }
    fun seekTo(positionMs: Long) = withController { it.seekTo(positionMs) }

    /** Отметить текущий файл прослушанным и перейти к следующему. */
    fun markCurrentPlayedAndNext() = withController { c ->
        val id = c.currentMediaItem?.mediaId?.toLongOrNull() ?: return@withController
        app.repo.setPlayed(listOf(id), true)
        if (c.hasNextMediaItem()) c.seekToNextMediaItem() else c.pause()
    }

    fun setVideoEnabled(enabled: Boolean) {
        val c = _controller.value ?: return
        c.trackSelectionParameters = c.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, !enabled)
            .build()
    }

    private fun maybeSuggestSleep() {
        val now = System.currentTimeMillis()
        if (app.settings.nightSuggest.value &&
            app.settings.isNightNow() &&
            app.sleepTimer.state.value == null &&
            now - lastSuggestAt > 30 * 60_000L
        ) {
            lastSuggestAt = now
            _nightSuggestion.tryEmit(Unit)
        }
    }
}
