package com.gigakiladze.pod

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Persisted user preferences: the theme choice and the screen brightness. */
class PodSettings(context: Context) {

    private val prefs = context.getSharedPreferences("pod_settings", Context.MODE_PRIVATE)

    private val _darkTheme = MutableStateFlow(prefs.getBoolean(KEY_DARK, true))
    val darkTheme: StateFlow<Boolean> = _darkTheme.asStateFlow()

    private val _brightness = MutableStateFlow(prefs.getFloat(KEY_BRIGHTNESS, 0.6f))
    val brightness: StateFlow<Float> = _brightness.asStateFlow()

    fun toggleTheme() {
        setDarkTheme(!_darkTheme.value)
    }

    fun setDarkTheme(dark: Boolean) {
        _darkTheme.value = dark
        prefs.edit().putBoolean(KEY_DARK, dark).apply()
    }

    /** Clamped to a visible floor so the wheel can never leave a black screen behind. */
    fun setBrightness(value: Float) {
        val clamped = value.coerceIn(MIN_BRIGHTNESS, 1f)
        _brightness.value = clamped
        prefs.edit().putFloat(KEY_BRIGHTNESS, clamped).apply()
    }

    fun nudgeBrightness(delta: Float) {
        setBrightness(_brightness.value + delta)
    }

    companion object {
        const val MIN_BRIGHTNESS = 0.05f
        private const val KEY_DARK = "dark_theme"
        private const val KEY_BRIGHTNESS = "brightness"
    }
}
