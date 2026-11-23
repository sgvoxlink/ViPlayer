package com.viplayer.playback

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDateTime
import java.time.ZoneId

sealed interface SleepMode {
    /** Остановиться в момент [endAtMillis] (System.currentTimeMillis). */
    data class At(val endAtMillis: Long) : SleepMode

    /** Остановиться, когда закончится текущий файл. */
    data object EndOfTrack : SleepMode
}

/** Хранит состояние таймера сна. Исполняет его PlaybackService. */
class SleepTimer {
    private val _state = MutableStateFlow<SleepMode?>(null)
    val state: StateFlow<SleepMode?> = _state.asStateFlow()

    fun setMinutes(minutes: Int) {
        _state.value = SleepMode.At(System.currentTimeMillis() + minutes * 60_000L)
    }

    /** Остановиться в указанное время (ближайшее наступление). */
    fun setClock(hour: Int, minute: Int) {
        val now = LocalDateTime.now()
        var t = now.toLocalDate().atTime(hour, minute)
        if (!t.isAfter(now)) t = t.plusDays(1)
        _state.value = SleepMode.At(t.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli())
    }

    fun setEndOfTrack() {
        _state.value = SleepMode.EndOfTrack
    }

    fun extend(minutes: Int) {
        val current = _state.value
        val base = if (current is SleepMode.At) maxOf(current.endAtMillis, System.currentTimeMillis())
        else System.currentTimeMillis()
        _state.value = SleepMode.At(base + minutes * 60_000L)
    }

    fun cancel() {
        _state.value = null
    }
}
