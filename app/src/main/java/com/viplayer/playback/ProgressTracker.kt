package com.viplayer.playback

import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import com.viplayer.data.Repository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Запоминает позицию в каждом файле, возобновляет с неё
 * и помечает файл прослушанным, когда дослушано ≥95% или файл закончился.
 */
class ProgressTracker(
    private val player: Player,
    private val repo: Repository,
    private val scope: CoroutineScope,
) : Player.Listener {

    private var currentId: Long? = null
    private var marked = false
    private var lastPos = 0L
    private var ticker: Job? = null

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        val prev = currentId
        if (prev != null && !marked) {
            if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) markPlayed(prev) else save(prev, lastPos)
        }
        currentId = mediaItem?.mediaId?.toLongOrNull()
        marked = false
        lastPos = player.currentPosition
        val id = currentId ?: return
        // Продолжаем с того места, где остановились в прошлый раз.
        scope.launch {
            val m = repo.media(id) ?: return@launch
            if (m.played || m.positionMs < 5_000) return@launch
            if (m.durationMs > 0 && m.positionMs > m.durationMs - 10_000) return@launch
            if (currentId == id && player.currentPosition < 3_000) {
                player.seekTo(m.positionMs - 3_000)
            }
        }
    }

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        ticker?.cancel()
        if (isPlaying) {
            ticker = scope.launch {
                var n = 0
                while (isActive) {
                    delay(1_000)
                    lastPos = player.currentPosition
                    checkThreshold()
                    if (++n % 5 == 0) saveCurrent()
                }
            }
        } else {
            saveCurrent()
        }
    }

    override fun onPlaybackStateChanged(playbackState: Int) {
        if (playbackState == Player.STATE_ENDED) {
            currentId?.let { if (!marked) markPlayed(it) }
        }
        if (playbackState == Player.STATE_READY) {
            val id = currentId
            val d = player.duration
            if (id != null && d > 0) scope.launch { repo.setDurationIfMissing(id, d) }
        }
    }

    private fun checkThreshold() {
        val id = currentId ?: return
        if (marked) return
        val d = player.duration
        if (d > 0 && player.currentPosition >= d * 95 / 100) markPlayed(id)
    }

    fun saveCurrent() {
        val id = currentId ?: return
        if (marked) return
        checkThreshold()
        if (!marked) {
            lastPos = player.currentPosition
            save(id, lastPos)
        }
    }

    private fun markPlayed(id: Long) {
        if (id == currentId) marked = true
        scope.launch { repo.markPlayed(id) }
    }

    private fun save(id: Long, pos: Long) {
        scope.launch { repo.savePosition(id, pos) }
    }

    fun release() {
        saveCurrent()
        ticker?.cancel()
    }
}
