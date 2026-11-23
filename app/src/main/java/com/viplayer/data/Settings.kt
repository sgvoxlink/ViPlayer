package com.viplayer.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalTime

class Settings(context: Context) {
    private val prefs = context.getSharedPreferences("viplayer", Context.MODE_PRIVATE)

    private val _speed = MutableStateFlow(prefs.getFloat("speed", 1f))
    val speed: StateFlow<Float> = _speed.asStateFlow()
    fun setSpeed(v: Float) {
        _speed.value = v
        prefs.edit().putFloat("speed", v).apply()
    }

    private val _videoMode = MutableStateFlow(prefs.getBoolean("videoMode", false))
    val videoMode: StateFlow<Boolean> = _videoMode.asStateFlow()
    fun setVideoMode(v: Boolean) {
        _videoMode.value = v
        prefs.edit().putBoolean("videoMode", v).apply()
    }

    private val _skipSilence = MutableStateFlow(prefs.getBoolean("skipSilence", false))
    val skipSilence: StateFlow<Boolean> = _skipSilence.asStateFlow()
    fun setSkipSilence(v: Boolean) {
        _skipSilence.value = v
        prefs.edit().putBoolean("skipSilence", v).apply()
    }

    private val _nightSuggest = MutableStateFlow(prefs.getBoolean("nightSuggest", true))
    val nightSuggest: StateFlow<Boolean> = _nightSuggest.asStateFlow()
    fun setNightSuggest(v: Boolean) {
        _nightSuggest.value = v
        prefs.edit().putBoolean("nightSuggest", v).apply()
    }

    private val _nightStart = MutableStateFlow(prefs.getInt("nightStart", 22))
    val nightStart: StateFlow<Int> = _nightStart.asStateFlow()
    fun setNightStart(v: Int) {
        _nightStart.value = v
        prefs.edit().putInt("nightStart", v).apply()
    }

    private val _nightEnd = MutableStateFlow(prefs.getInt("nightEnd", 6))
    val nightEnd: StateFlow<Int> = _nightEnd.asStateFlow()
    fun setNightEnd(v: Int) {
        _nightEnd.value = v
        prefs.edit().putInt("nightEnd", v).apply()
    }

    private val _fadeOut = MutableStateFlow(prefs.getBoolean("fadeOut", true))
    val fadeOut: StateFlow<Boolean> = _fadeOut.asStateFlow()
    fun setFadeOut(v: Boolean) {
        _fadeOut.value = v
        prefs.edit().putBoolean("fadeOut", v).apply()
    }

    /** Список файлов последнего воспроизведения — для продолжения кнопкой гарнитуры после выгрузки. */
    var resumeIds: List<Long>
        get() = prefs.getString("resumeIds", "").orEmpty().split(',').mapNotNull { it.toLongOrNull() }
        set(v) {
            prefs.edit().putString("resumeIds", v.joinToString(",")).apply()
        }

    fun isNightNow(): Boolean {
        val h = LocalTime.now().hour
        val start = nightStart.value
        val end = nightEnd.value
        return if (start > end) h >= start || h < end else h in start until end
    }
}
