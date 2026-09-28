package com.gigakiladze.pod.kiosk

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.gigakiladze.pod.MainActivity

/**
 * Brings the player up after a reboot. Registering as HOME usually covers this,
 * but launching explicitly means the iPod is on screen even if the system would
 * otherwise have shown a chooser or a stale task.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action != Intent.ACTION_BOOT_COMPLETED && action != Intent.ACTION_LOCKED_BOOT_COMPLETED) return

        Log.i(TAG, "Boot completed, launching player")
        val launch = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        runCatching { context.startActivity(launch) }
            .onFailure { Log.w(TAG, "Could not launch on boot", it) }
    }

    private companion object {
        const val TAG = "PodBootReceiver"
    }
}
