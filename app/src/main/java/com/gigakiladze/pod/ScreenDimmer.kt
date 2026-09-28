package com.gigakiladze.pod

import android.os.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * The appliance never sleeps. Instead of letting the display time out, the screen
 * stays on and simply fades to its dimmest setting after a spell of inactivity,
 * coming straight back on the next touch.
 *
 * This is why the window carries FLAG_KEEP_SCREEN_ON: without it Android would
 * still blank the display on its own schedule regardless of the brightness.
 */
class ScreenDimmer(
    private val scope: CoroutineScope,
    private val idleTimeoutMs: Long = DEFAULT_IDLE_MS,
) {
    private val _dimmed = MutableStateFlow(false)
    val dimmed: StateFlow<Boolean> = _dimmed.asStateFlow()

    @Volatile
    private var lastInteractionAt = SystemClock.elapsedRealtime()

    private var job: Job? = null

    fun start() {
        if (job?.isActive == true) return
        job = scope.launch {
            while (isActive) {
                val idleFor = SystemClock.elapsedRealtime() - lastInteractionAt
                _dimmed.value = idleFor >= idleTimeoutMs
                delay(POLL_MS)
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    /** Any touch anywhere brings the screen back to the user's chosen brightness. */
    fun noteInteraction() {
        lastInteractionAt = SystemClock.elapsedRealtime()
        if (_dimmed.value) _dimmed.value = false
    }

    companion object {
        const val DEFAULT_IDLE_MS = 45_000L
        private const val POLL_MS = 1_000L

        /** Dim, not off — low enough to save power, high enough to still read. */
        const val DIM_BRIGHTNESS = 0.01f
    }
}
