package com.gigakiladze.pod.media

import android.content.ComponentName
import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Everything the UI needs to draw the Now Playing screen. */
data class PlaybackState(
    val track: Track? = null,
    val isPlaying: Boolean = false,
    val durationMs: Long = 0L,
    val queueSize: Int = 0,
    val queueIndex: Int = 0,
)

/**
 * Bridges Compose to [PodPlaybackService]. Lives for the life of the process so the
 * controller is connected once, not rebound every time the Activity restarts.
 *
 * Every [MediaController] call must happen on the main thread — the controller
 * throws otherwise — so all coroutines here are pinned to Dispatchers.Main, and the
 * synchronous methods are only ever called from Compose event handlers.
 */
class PlayerConnection(
    private val context: Context,
    private val scope: CoroutineScope,
) {
    private var controller: MediaController? = null

    /** The track list currently loaded into the player, parallel to its media items. */
    private var queue: List<Track> = emptyList()

    private val _state = MutableStateFlow(PlaybackState())
    val state: StateFlow<PlaybackState> = _state.asStateFlow()

    /**
     * Kept separate from [state] deliberately. It changes twice a second while a
     * track plays, and only the Now Playing screen cares; folding it into
     * [PlaybackState] made every menu recompose at 2 Hz for nothing.
     */
    private val _positionMs = MutableStateFlow(0L)
    val positionMs: StateFlow<Long> = _positionMs.asStateFlow()

    private var tickerJob: Job? = null

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = publish(player)
    }

    fun connect() {
        scope.launch(Dispatchers.Main.immediate) {
            val token = SessionToken(context, ComponentName(context, PodPlaybackService::class.java))
            val controller = MediaController.Builder(context, token).buildAsync().await()
            this@PlayerConnection.controller = controller
            controller.addListener(listener)
            publish(controller)
        }
    }

    fun release() {
        tickerJob?.cancel()
        controller?.removeListener(listener)
        controller?.release()
        controller = null
    }

    /** Replaces the queue and starts playing at [startIndex]. */
    fun play(tracks: List<Track>, startIndex: Int) {
        val controller = controller ?: return
        if (tracks.isEmpty()) return
        queue = tracks
        controller.setMediaItems(tracks.map(::toMediaItem), startIndex, 0L)
        controller.prepare()
        controller.play()
    }

    fun togglePlayPause() {
        val controller = controller ?: return
        if (controller.isPlaying) controller.pause() else controller.play()
    }

    fun next() {
        val controller = controller ?: return
        // Wrap around at the end of the queue the way the iPod does.
        if (controller.hasNextMediaItem()) {
            controller.seekToNextMediaItem()
        } else if (controller.mediaItemCount > 0) {
            controller.seekTo(0, 0L)
        }
    }

    /**
     * Mirrors the hardware behaviour: a press more than three seconds into a track
     * restarts it, otherwise it steps back to the previous track.
     */
    fun previous() {
        val controller = controller ?: return
        if (controller.currentPosition > RESTART_THRESHOLD_MS) {
            controller.seekTo(0L)
        } else if (controller.hasPreviousMediaItem()) {
            controller.seekToPreviousMediaItem()
        } else {
            controller.seekTo(0L)
        }
    }

    fun seekTo(positionMs: Long) {
        controller?.seekTo(positionMs.coerceAtLeast(0L))
    }

    private fun toMediaItem(track: Track): MediaItem = MediaItem.Builder()
        .setMediaId(track.id.toString())
        .setUri(track.uri)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(track.title)
                .setArtist(track.artist)
                .setAlbumTitle(track.album)
                .setIsBrowsable(false)
                .setIsPlayable(true)
                .build()
        )
        .build()

    /** Current position without going through a flow — used by the scrub handler. */
    fun currentPositionMs(): Long = controller?.currentPosition?.coerceAtLeast(0L) ?: 0L

    private fun publish(player: Player) {
        val index = player.currentMediaItemIndex
        _state.update {
            PlaybackState(
                track = queue.getOrNull(index),
                isPlaying = player.isPlaying,
                durationMs = player.duration.takeIf { it > 0L } ?: 0L,
                queueSize = player.mediaItemCount,
                queueIndex = index,
            )
        }
        _positionMs.value = player.currentPosition.coerceAtLeast(0L)
        if (player.isPlaying) startTicker() else stopTicker()
    }

    /** Playback position is not an event, so poll it while the music is actually moving. */
    private fun startTicker() {
        if (tickerJob?.isActive == true) return
        tickerJob = scope.launch(Dispatchers.Main.immediate) {
            while (isActive) {
                val player = controller
                if (player == null || !player.isPlaying) break
                _positionMs.value = player.currentPosition.coerceAtLeast(0L)
                delay(POSITION_POLL_MS)
            }
        }
    }

    private fun stopTicker() {
        tickerJob?.cancel()
        tickerJob = null
    }

    private companion object {
        const val RESTART_THRESHOLD_MS = 3_000L
        const val POSITION_POLL_MS = 500L
    }
}
