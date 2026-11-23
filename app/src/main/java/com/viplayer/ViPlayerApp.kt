package com.viplayer

import android.app.Application
import com.viplayer.data.AppDatabase
import com.viplayer.data.Repository
import com.viplayer.data.Settings
import com.viplayer.playback.PlayerConnection
import com.viplayer.playback.SleepTimer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

/** Простой контейнер зависимостей на всё приложение. */
class ViPlayerApp : Application() {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages: SharedFlow<String> = _messages

    lateinit var settings: Settings
        private set
    lateinit var repo: Repository
        private set
    lateinit var sleepTimer: SleepTimer
        private set
    lateinit var player: PlayerConnection
        private set

    override fun onCreate() {
        super.onCreate()
        settings = Settings(this)
        repo = Repository(this, AppDatabase.build(this), appScope, ::notify)
        sleepTimer = SleepTimer()
        player = PlayerConnection(this, appScope)
    }

    fun notify(message: String) {
        _messages.tryEmit(message)
    }
}
