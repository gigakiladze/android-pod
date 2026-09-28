package com.gigakiladze.pod

import android.app.Application
import com.gigakiladze.pod.media.MusicLibrary
import com.gigakiladze.pod.media.PlayerConnection
import com.gigakiladze.pod.media.VolumeController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

/**
 * Process-scoped state. The app is a single-Activity kiosk that is never really
 * dismissed, so holding the library and the player connection here keeps them
 * alive across Activity restarts without any ViewModel plumbing.
 */
class PodApplication : Application() {

    val scope = CoroutineScope(SupervisorJob())

    lateinit var settings: PodSettings
        private set
    lateinit var library: MusicLibrary
        private set
    lateinit var player: PlayerConnection
        private set
    lateinit var volume: VolumeController
        private set

    override fun onCreate() {
        super.onCreate()
        settings = PodSettings(this)
        library = MusicLibrary(scope)
        player = PlayerConnection(this, scope).also { it.connect() }
        volume = VolumeController(this)
    }

    override fun onTerminate() {
        player.release()
        scope.cancel()
        super.onTerminate()
    }
}
