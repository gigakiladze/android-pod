package com.gigakiladze.pod.kiosk

import android.app.Activity
import android.app.ActivityManager
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.view.WindowManager
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.gigakiladze.pod.MainActivity

/**
 * Turns the phone into the appliance.
 *
 * Two tiers, chosen automatically:
 *  - **Device owner** (provisioned with `adb shell dpm set-device-owner`): real lock
 *    task mode. Status bar, nav bar, recents and the power-button menu are all gone,
 *    and the keyguard is disabled so waking the screen lands straight on the player.
 *  - **Plain install**: immersive mode plus the HOME intent filter. The bars hide but
 *    a swipe can still reveal them transiently. Useful for developing on a phone you
 *    have not wiped yet.
 */
class KioskController(private val activity: Activity) {

    private val dpm = activity.getSystemService(DevicePolicyManager::class.java)
    private val admin = ComponentName(activity, PodDeviceAdminReceiver::class.java)
    private val activityManager = activity.getSystemService(ActivityManager::class.java)

    val isDeviceOwner: Boolean
        get() = runCatching { dpm.isDeviceOwnerApp(activity.packageName) }.getOrDefault(false)

    /**
     * Set once the user deliberately leaves kiosk mode. Without it, the next
     * onResume — which a single screen sleep/wake is enough to trigger — would
     * silently re-lock the device and make the escape hatch useless.
     *
     * Intentionally not persisted: a restart should come back up as an iPod.
     */
    private var suspended = false

    private val isInLockTask: Boolean
        get() = activityManager.lockTaskModeState != ActivityManager.LOCK_TASK_MODE_NONE

    /** One-time policy setup, plus the window flags that let the screen wake into the app. */
    fun onCreate() {
        allowShowingOverKeyguard()
        keepScreenOn()
        if (isDeviceOwner) applyDeviceOwnerPolicies()
    }

    /**
     * The display never blanks on its own; [com.gigakiladze.pod.ScreenDimmer] fades
     * it to minimum brightness instead. Without this flag Android would still turn
     * the panel off on its own timeout no matter how we set brightness.
     */
    private fun keepScreenOn() {
        activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    /** Re-asserted on every resume — the system drops immersive mode after dialogs and wake-ups. */
    fun onResume() {
        if (suspended) return
        hideSystemBars()
        enterLockTaskIfPossible()
    }

    /**
     * Grants the runtime permissions the scanner needs without prompting, which a
     * kiosk with no visible system UI could not otherwise satisfy. Device owner only.
     */
    fun autoGrantPermissions(permissions: List<String>) {
        if (!isDeviceOwner) return
        for (permission in permissions) {
            runCatching {
                dpm.setPermissionGrantState(
                    admin,
                    activity.packageName,
                    permission,
                    DevicePolicyManager.PERMISSION_GRANT_STATE_GRANTED,
                )
            }.onFailure { Log.w(TAG, "Could not auto-grant $permission", it) }
        }
    }

    private fun applyDeviceOwnerPolicies() {
        runCatching {
            dpm.setLockTaskPackages(admin, arrayOf(activity.packageName))

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                // NONE removes the status bar, nav bar, recents, notifications, and
                // critically the power-button global actions menu.
                dpm.setLockTaskFeatures(admin, DevicePolicyManager.LOCK_TASK_FEATURE_NONE)
            }

            // Waking the screen should show the player, not a lock screen.
            dpm.setKeyguardDisabled(admin, true)
            dpm.setStatusBarDisabled(admin, true)

            // Make this the unconditional home app so no launcher chooser ever appears.
            dpm.addPersistentPreferredActivity(
                admin,
                IntentFilter(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_HOME)
                    addCategory(Intent.CATEGORY_DEFAULT)
                },
                ComponentName(activity, MainActivity::class.java),
            )

            // An iPod on a dock should not go to sleep and drop off the wheel mid-album.
            dpm.setGlobalSetting(
                admin,
                Settings.Global.STAY_ON_WHILE_PLUGGED_IN,
                STAY_ON_ALL_PLUG_TYPES.toString(),
            )

            // Belt and braces alongside FLAG_KEEP_SCREEN_ON: push the system's own
            // display timeout as far out as it will go.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                dpm.setSystemSetting(
                    admin,
                    Settings.System.SCREEN_OFF_TIMEOUT,
                    Int.MAX_VALUE.toString(),
                )
            }
        }.onFailure { Log.e(TAG, "Failed to apply device owner policies", it) }
    }

    private fun enterLockTaskIfPossible() {
        if (!isDeviceOwner || isInLockTask) return
        runCatching { activity.startLockTask() }
            .onFailure { Log.w(TAG, "startLockTask rejected", it) }
    }

    private fun hideSystemBars() {
        val window = activity.window
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    /**
     * Lets the Activity be shown over (and dismiss) the keyguard, so a power-button
     * wake goes straight back to Now Playing.
     */
    private fun allowShowingOverKeyguard() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            activity.setShowWhenLocked(true)
            activity.setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            activity.window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        @Suppress("DEPRECATION")
        activity.window.addFlags(WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD)
    }

    /**
     * The escape hatch. Without this a fully provisioned device is very hard to get
     * out of, so it is wired to a deliberate, hard-to-hit action in Settings.
     *
     * @param unprovision also relinquishes device ownership, which is irreversible
     *                    without another factory reset.
     */
    fun exitKiosk(unprovision: Boolean) {
        suspended = true
        showSystemBars()
        runCatching { if (isInLockTask) activity.stopLockTask() }
            .onFailure { Log.w(TAG, "stopLockTask failed", it) }

        if (!isDeviceOwner) return
        runCatching {
            dpm.setStatusBarDisabled(admin, false)
            dpm.setKeyguardDisabled(admin, false)
            dpm.clearPackagePersistentPreferredActivities(admin, activity.packageName)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                dpm.setLockTaskFeatures(admin, DevicePolicyManager.LOCK_TASK_FEATURE_HOME)
            }
        }.onFailure { Log.w(TAG, "Could not relax policies", it) }

        if (unprovision) {
            // Deprecated, but it remains the only way for an app to relinquish its own
            // device ownership. The replacement APIs are for work profiles, not this.
            @Suppress("DEPRECATION")
            runCatching { dpm.clearDeviceOwnerApp(activity.packageName) }
                .onFailure { Log.e(TAG, "clearDeviceOwnerApp failed", it) }
        }
    }

    /** Gives the system bars back so the user can actually reach Android's settings. */
    private fun showSystemBars() {
        val window = activity.window
        WindowCompat.setDecorFitsSystemWindows(window, true)
        WindowInsetsControllerCompat(window, window.decorView)
            .show(WindowInsetsCompat.Type.systemBars())
    }

    /** Applies the user's brightness to this window only — needs no system permission. */
    fun applyBrightness(value: Float) {
        val window = activity.window
        window.attributes = window.attributes.apply { screenBrightness = value.coerceIn(0.01f, 1f) }
    }

    private companion object {
        const val TAG = "KioskController"

        /** AC | USB | wireless — the bitmask Settings.Global.STAY_ON_WHILE_PLUGGED_IN expects. */
        const val STAY_ON_ALL_PLUG_TYPES = 7
    }
}
