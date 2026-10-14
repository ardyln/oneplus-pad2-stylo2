package io.github.ardyln.stylo2

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Starts the pen service after boot and after the app is updated. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        context.startForegroundService(Intent(context, PenService::class.java))
    }
}
