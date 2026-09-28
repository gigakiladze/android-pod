package com.gigakiladze.pod.media

import android.content.Context
import android.media.AudioManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Hardware volume keys, handled by the app instead of the system.
 *
 * Passing flags = 0 to adjustStreamVolume is the part that matters: it changes the
 * volume without Android popping up its own slider, which would break the illusion
 * that this is an iPod rather than a Pixel.
 */
class VolumeController(context: Context) {

    private val audioManager = context.getSystemService(AudioManager::class.java)

    private val _level = MutableStateFlow(readLevel())
    val level: StateFlow<Float> = _level.asStateFlow()

    /** Bumped on every change so the UI knows to flash the volume bar. */
    private val _changes = MutableStateFlow(0)
    val changes: StateFlow<Int> = _changes.asStateFlow()

    fun raise() = adjust(AudioManager.ADJUST_RAISE)

    fun lower() = adjust(AudioManager.ADJUST_LOWER)

    /** Re-reads the system value, for changes that did not come from our key handler. */
    fun refresh() {
        _level.value = readLevel()
    }

    private fun adjust(direction: Int) {
        // flags = 0 -> adjust silently; no system volume panel.
        audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, 0)
        _level.value = readLevel()
        _changes.value = _changes.value + 1
    }

    private fun readLevel(): Float {
        val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        if (max <= 0) return 0f
        return audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / max
    }
}
