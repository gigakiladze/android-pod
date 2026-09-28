package com.gigakiladze.pod

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.view.KeyEvent
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.gigakiladze.pod.kiosk.KioskController
import com.gigakiladze.pod.media.LibraryState
import com.gigakiladze.pod.ui.PodNavState
import com.gigakiladze.pod.ui.PodRoot
import com.gigakiladze.pod.ui.PodTheme
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * The only Activity. It is the home launcher, so "closing" it is not a thing that
 * happens — it is simply always on screen until the display sleeps.
 */
class MainActivity : ComponentActivity() {

    private val app: PodApplication get() = application as PodApplication

    private lateinit var kiosk: KioskController
    private lateinit var dimmer: ScreenDimmer
    private val nav = PodNavState()

    /**
     * Waking the screen drops immersive mode, so re-assert the kiosk window state
     * as soon as the display comes back rather than waiting for a user interaction.
     */
    private val screenStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_ON, Intent.ACTION_USER_PRESENT -> kiosk.onResume()
                Intent.ACTION_SCREEN_OFF -> Log.d(TAG, "Screen off; playback continues in the service")
            }
        }
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { startScanIfPermitted() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        kiosk = KioskController(this)
        kiosk.onCreate()
        dimmer = ScreenDimmer(lifecycleScope).also { it.start() }
        kiosk.autoGrantPermissions(requiredPermissions())

        ContextCompat.registerReceiver(
            this,
            screenStateReceiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_USER_PRESENT)
            },
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )

        // With no navigation bar, a back gesture should behave like the MENU button.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                nav.pop()
            }
        })

        // Brightness is applied to this window, so it needs no system-settings
        // permission. The dimmer overrides it while the device sits idle.
        lifecycleScope.launch {
            combine(app.settings.brightness, dimmer.dimmed) { chosen, isDimmed ->
                if (isDimmed) ScreenDimmer.DIM_BRIGHTNESS else chosen
            }.collect { kiosk.applyBrightness(it) }
        }

        setContent {
            val darkTheme by app.settings.darkTheme.collectAsState()
            val isDimmed by dimmer.dimmed.collectAsState()

            PodTheme(darkTheme = darkTheme) {
                PodRoot(
                    library = app.library,
                    player = app.player,
                    settings = app.settings,
                    volume = app.volume,
                    nav = nav,
                    dimmed = isDimmed,
                    onInteraction = dimmer::noteInteraction,
                )
            }
        }

        requestPermissionsIfNeeded()
        handleExitIntent(intent)
    }

    /**
     * Swallows the hardware volume keys. [VolumeController] adjusts the stream with
     * no system UI, and the app draws its own iPod-style volume bar instead.
     */
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean = when (keyCode) {
        KeyEvent.KEYCODE_VOLUME_UP -> {
            dimmer.noteInteraction()
            app.volume.raise()
            true
        }

        KeyEvent.KEYCODE_VOLUME_DOWN -> {
            dimmer.noteInteraction()
            app.volume.lower()
            true
        }

        else -> super.onKeyDown(keyCode, event)
    }

    /** Also consume the release, or the system handles it and shows its panel. */
    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean =
        when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_VOLUME_DOWN -> true
            else -> super.onKeyUp(keyCode, event)
        }

    override fun onResume() {
        super.onResume()
        app.volume.refresh()
        dimmer.noteInteraction()
        kiosk.onResume()
        kiosk.applyBrightness(app.settings.brightness.value)
    }

    override fun onDestroy() {
        dimmer.stop()
        runCatching { unregisterReceiver(screenStateReceiver) }
        super.onDestroy()
    }

    /**
     * Home is already here, so a HOME intent means "go back to the top of the menus"
     * rather than restarting anything.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (handleExitIntent(intent)) return
        if (intent.hasCategory(Intent.CATEGORY_HOME)) {
            while (nav.canGoBack) nav.pop()
        }
    }

    /**
     * Recovery hatch reachable over adb:
     *
     *   adb shell am start -n com.gigakiladze.pod/.MainActivity \
     *       -a com.gigakiladze.pod.EXIT_KIOSK --ez clear_owner true
     *
     * This exists because `adb shell dpm remove-active-admin` refuses to touch a
     * production device owner — only the owning app can relinquish the role, so
     * without this the sole way out of a provisioned device would be the on-screen
     * menu or a factory reset.
     */
    private fun handleExitIntent(intent: Intent?): Boolean {
        if (intent?.action != ACTION_EXIT_KIOSK) return false
        val clearOwner = intent.getBooleanExtra(EXTRA_CLEAR_OWNER, false)
        Log.w(TAG, "Exit kiosk requested over intent (clearOwner=$clearOwner)")
        kiosk.exitKiosk(unprovision = clearOwner)
        return true
    }

    private fun requiredPermissions(): List<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            listOf(Manifest.permission.READ_MEDIA_AUDIO)
        } else {
            listOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

    private fun requestPermissionsIfNeeded() {
        if (hasStorageAccess()) {
            startScanIfPermitted()
        } else {
            permissionLauncher.launch(requiredPermissions().toTypedArray())
        }
    }

    private fun hasStorageAccess(): Boolean {
        // All-files access, normally granted over adb, covers the whole filesystem walk.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && Environment.isExternalStorageManager()) {
            return true
        }
        return requiredPermissions().all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun startScanIfPermitted() {
        if (!hasStorageAccess()) {
            Log.w(TAG, "Storage access denied; the library will stay empty")
            return
        }
        // Only kick off the initial scan; a rescan is an explicit menu action.
        if (app.library.state.value is LibraryState.Idle) {
            app.library.rescan()
        }
    }

    private companion object {
        const val TAG = "PodMainActivity"
        const val ACTION_EXIT_KIOSK = "com.gigakiladze.pod.EXIT_KIOSK"
        const val EXTRA_CLEAR_OWNER = "clear_owner"
    }
}
