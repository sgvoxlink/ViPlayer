package com.viplayer.playback

import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import androidx.core.content.IntentCompat
import androidx.media3.common.Player

/**
 * Считает нажатия кнопки гарнитуры:
 * 1 — пауза / продолжить, 2 — следующий файл, 3 и больше — назад на 30 секунд.
 */
class HeadsetClickHandler(private val player: Player) {
    private val handler = Handler(Looper.getMainLooper())
    private var clicks = 0

    private val fire = Runnable {
        val n = clicks
        clicks = 0
        when {
            n == 1 -> togglePlay()
            n == 2 -> if (player.hasNextMediaItem()) player.seekToNextMediaItem()
            n >= 3 -> player.seekBack()
        }
    }

    private fun togglePlay() {
        if (player.playWhenReady && player.playbackState != Player.STATE_ENDED) {
            player.pause()
        } else {
            if (player.playbackState == Player.STATE_IDLE) player.prepare()
            if (player.playbackState == Player.STATE_ENDED) player.seekToDefaultPosition()
            player.play()
        }
    }

    /** @return true, если событие обработано здесь. */
    fun handle(intent: Intent): Boolean {
        val event = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_KEY_EVENT, KeyEvent::class.java)
            ?: return false
        if (event.keyCode !in CLICK_KEYS) return false
        // Плейлист пуст — пусть Media3 сам попробует восстановить последнее воспроизведение.
        if (player.mediaItemCount == 0) return false
        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
            clicks++
            handler.removeCallbacks(fire)
            handler.postDelayed(fire, CLICK_WINDOW_MS)
        }
        return true
    }

    fun release() {
        handler.removeCallbacks(fire)
    }

    companion object {
        private const val CLICK_WINDOW_MS = 450L
        private val CLICK_KEYS = setOf(
            KeyEvent.KEYCODE_HEADSETHOOK,
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
            KeyEvent.KEYCODE_MEDIA_PLAY,
            KeyEvent.KEYCODE_MEDIA_PAUSE,
        )
    }
}
