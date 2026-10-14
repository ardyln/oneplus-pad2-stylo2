package io.github.ardyln.stylo2

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Starts the pen service after boot and after the app is updated. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // No action: the module's explicit start (module/service.sh)
        if (intent.action != null && intent.action !in STARTS) return
        context.startForegroundService(Intent(context, PenService::class.java))
    }

    companion object {
        private val STARTS = setOf(Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED)
    }
}
