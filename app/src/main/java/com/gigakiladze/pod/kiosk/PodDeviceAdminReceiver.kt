package com.gigakiladze.pod.kiosk

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Required for the app to hold device-owner privileges. It carries no logic of its
 * own — its existence is what lets `dpm set-device-owner` target this package.
 */
class PodDeviceAdminReceiver : DeviceAdminReceiver() {

    override fun onEnabled(context: Context, intent: Intent) {
        Log.i(TAG, "Device admin enabled")
    }

    override fun onDisabled(context: Context, intent: Intent) {
        Log.i(TAG, "Device admin disabled; kiosk privileges are gone")
    }

    override fun onLockTaskModeEntering(context: Context, intent: Intent, pkg: String) {
        Log.i(TAG, "Lock task entered for $pkg")
    }

    override fun onLockTaskModeExiting(context: Context, intent: Intent) {
        Log.i(TAG, "Lock task exited")
    }

    private companion object {
        const val TAG = "PodDeviceAdmin"
    }
}
