package io.github.ardyln.stylo2

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.SystemClock
import android.util.Log
import android.view.KeyEvent
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

/** Carries out shortcut actions. [buzz] makes the pen vibrate once. */
class ShortcutRunner(private val context: Context, private val buzz: () -> Unit) {

    fun run(s: Shortcut, taps: Int, battery: Int) {
        // Webhook URLs contain a secret id, so they're never logged.
        val arg = if (s.action == Shortcut.Action.WEBHOOK) "" else s.arg ?: ""
        Log.i(PenService.TAG, "shortcut: ${taps}x ${s.action} $arg")
        when (s.action) {
            Shortcut.Action.OPEN_APP -> openApp(s.arg)
            Shortcut.Action.MEDIA_PLAY_PAUSE -> mediaKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
            Shortcut.Action.MEDIA_NEXT -> mediaKey(KeyEvent.KEYCODE_MEDIA_NEXT)
            Shortcut.Action.MEDIA_PREVIOUS -> mediaKey(KeyEvent.KEYCODE_MEDIA_PREVIOUS)
            Shortcut.Action.BROADCAST -> s.arg?.let { context.sendBroadcast(gestureIntent(it, taps)) }
            Shortcut.Action.WEBHOOK -> s.arg?.let { webhook(it, taps, battery) }
            Shortcut.Action.BUZZ -> buzz()
        }
    }

    private fun openApp(pkg: String?) {
        val launch = pkg?.let { context.packageManager.getLaunchIntentForPackage(it) }
        if (launch == null) {
            Log.w(PenService.TAG, "no app to open: $pkg")
            return
        }
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(launch)
        } catch (e: RuntimeException) {
            Log.e(PenService.TAG, "could not open $pkg", e)
        }
    }

    private fun mediaKey(code: Int) {
        val am = context.getSystemService(AudioManager::class.java)
        val now = SystemClock.uptimeMillis()
        am.dispatchMediaKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, code, 0))
        am.dispatchMediaKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, code, 0))
    }

    private fun webhook(url: String, taps: Int, battery: Int) {
        val body = JSONObject()
            .put("gesture", gestureName(taps))
            .put("taps", taps)
            .apply { if (battery >= 0) put("battery", battery) }
            .toString()
        thread(name = "webhook") {
            try {
                val c = URL(url).openConnection() as HttpURLConnection
                c.requestMethod = "POST"
                c.connectTimeout = 5000
                c.readTimeout = 5000
                c.doOutput = true
                c.setRequestProperty("Content-Type", "application/json")
                c.outputStream.use { it.write(body.toByteArray()) }
                Log.i(PenService.TAG, "webhook: HTTP ${c.responseCode}")
                c.disconnect()
            } catch (e: Exception) {
                Log.w(PenService.TAG, "webhook failed: $e")
            }
        }
    }

    companion object {
        /** Broadcast sent for every gesture when enabled (for Tasker and similar apps). */
        const val ACTION_GESTURE = "io.github.ardyln.stylo2.action.DOUBLE_TAP"
        const val EXTRA_TAPS = "taps"
        const val EXTRA_GESTURE = "gesture"

        fun gestureName(taps: Int) = if (taps == 1) "double_tap" else "double_tap_$taps"

        fun gestureIntent(action: String, taps: Int): Intent = Intent(action)
            .putExtra(EXTRA_TAPS, taps)
            .putExtra(EXTRA_GESTURE, gestureName(taps))
            .addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
    }
}
