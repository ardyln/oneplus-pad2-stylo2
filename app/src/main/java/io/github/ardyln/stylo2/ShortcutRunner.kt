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
class ShortcutRunner(
    private val context: Context,
    private val settings: Settings,
    private val buzz: () -> Unit,
    /** Presses a key through the root helper's virtual keyboard. */
    private val key: (Int) -> Unit,
    /** Clicks, and scrolls by wheel steps (positive is up), through the virtual mouse. */
    private val click: (button: Int, holdMs: Long) -> Unit = { _, _ -> },
    private val scroll: (Int) -> Unit = {},
) {

    /** [gesture] names the trigger in webhooks and broadcasts ("double_tap", "pen_attached", …). */
    fun run(s: Shortcut, taps: Int, battery: Int, gesture: String = gestureName(taps)) {
        // Webhook URLs contain a secret id, so they're never logged.
        val arg = if (s.action == Shortcut.Action.WEBHOOK) "" else s.arg ?: ""
        Log.i(PenService.TAG, "shortcut: $gesture ${s.action} $arg")
        when (s.action) {
            Shortcut.Action.OPEN_APP -> openApp(s.arg)
            Shortcut.Action.GO_BACK -> goBack()
            Shortcut.Action.MEDIA_PLAY_PAUSE -> mediaKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
            Shortcut.Action.MEDIA_NEXT -> mediaKey(KeyEvent.KEYCODE_MEDIA_NEXT)
            Shortcut.Action.MEDIA_PREVIOUS -> mediaKey(KeyEvent.KEYCODE_MEDIA_PREVIOUS)
            Shortcut.Action.BROADCAST -> s.arg?.let { context.sendBroadcast(gestureIntent(it, taps, gesture)) }
            Shortcut.Action.WEBHOOK -> s.arg?.let { webhook(it, taps, battery, gesture) }
            Shortcut.Action.BUZZ -> buzz()
            // The pen service picks these setting changes up and updates the pen.
            Shortcut.Action.TOGGLE_PRESENTATION -> settings.setPresentation(!settings.presentation)
            Shortcut.Action.TOGGLE_POINTER -> settings.prefs.edit()
                .putString(Settings.KEY_PRESENTATION_MODE, if (settings.presentationMode == 1) "0" else "1").apply()
            Shortcut.Action.TOGGLE_FEEDBACK -> settings.prefs.edit()
                .putBoolean(Settings.KEY_FEEDBACK, !settings.feedbackOn).apply()
            Shortcut.Action.NEXT_TEXTURE -> settings.prefs.edit()
                .putString(Settings.KEY_TEXTURE, ((settings.texture + 1) % 4).toString()).apply()
            Shortcut.Action.APPLY_PRESET -> PresetStore(context).get(s.arg)?.let { PresetStore(context).apply(it) }
            Shortcut.Action.NEXT_PAGE -> key(StylusBridge.KEY_PAGEDOWN)
            Shortcut.Action.PREVIOUS_PAGE -> key(StylusBridge.KEY_PAGEUP)
            Shortcut.Action.SCROLL_UP -> scroll(steps(s))
            Shortcut.Action.SCROLL_DOWN -> scroll(-steps(s))
            Shortcut.Action.CLICK -> click(StylusBridge.BTN_LEFT, CLICK_MS)
            Shortcut.Action.RIGHT_CLICK -> click(StylusBridge.BTN_RIGHT, CLICK_MS)
            Shortcut.Action.MIDDLE_CLICK -> click(StylusBridge.BTN_MIDDLE, CLICK_MS)
            Shortcut.Action.NOTHING -> {}
        }
    }

    private fun steps(s: Shortcut) = s.arg?.toIntOrNull()?.coerceIn(1, MAX_SCROLL) ?: 1

    private fun openApp(pkg: String?) {
        val launch = pkg?.let { context.packageManager.getLaunchIntentForPackage(it) }
        if (launch == null) {
            Log.w(PenService.TAG, "no app to open: $pkg")
            return
        }
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        // Remember where we came from for "Go back"; nothing to return to if the app was already focused.
        val from = FocusService.focused
        returnTo = if (from != pkg) from else null
        opened = if (from != pkg) pkg else null
        try {
            context.startActivity(launch)
        } catch (e: RuntimeException) {
            Log.e(PenService.TAG, "could not open $pkg", e)
        }
    }

    /**
     * Brings back the app that was focused before the last "Open an app", the way a launcher
     * resumes it. Only while the opened app is still focused, so it never pulls you out of
     * something else you switched to.
     */
    private fun goBack() {
        val from = returnTo
        val app = opened
        val focused = FocusService.focused
        if (from == null || app == null || focused != app) {
            EventLog.add(EventLog.Source.APP, "go_back", "Go back: nothing to do (focused: ${focused ?: "?"})")
            return
        }
        returnTo = null
        opened = null
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val homePkg = context.packageManager.resolveActivity(home, 0)?.activityInfo?.packageName
        val intent = if (from == homePkg) home else context.packageManager.getLaunchIntentForPackage(from)
        if (intent == null) {
            Log.w(PenService.TAG, "go back: no way to open $from")
            return
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        EventLog.add(EventLog.Source.APP, "go_back", "Go back: $app → $from")
        try {
            context.startActivity(intent)
        } catch (e: RuntimeException) {
            Log.e(PenService.TAG, "could not go back to $from", e)
        }
    }

    private fun mediaKey(code: Int) {
        val am = context.getSystemService(AudioManager::class.java)
        val now = SystemClock.uptimeMillis()
        am.dispatchMediaKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, code, 0))
        am.dispatchMediaKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, code, 0))
    }

    private fun webhook(url: String, taps: Int, battery: Int, gesture: String) {
        val body = JSONObject()
            .put("gesture", gesture)
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
        /** The app focused before the last "Open an app", and the app it opened. */
        @Volatile private var returnTo: String? = null
        @Volatile private var opened: String? = null

        const val MAX_SCROLL = 5
        private const val CLICK_MS = 40L

        /** Broadcast sent for every gesture when enabled (for Tasker and similar apps). */
        const val ACTION_GESTURE = "io.github.ardyln.stylo2.action.DOUBLE_TAP"
        const val EXTRA_TAPS = "taps"
        const val EXTRA_GESTURE = "gesture"

        /** Broadcast for the charger, sent with "Send pen events to automation apps": extras attached and gesture. */
        const val ACTION_CHARGER = "io.github.ardyln.stylo2.action.CHARGER"
        const val EXTRA_ATTACHED = "attached"

        fun gestureName(taps: Int) = if (taps == 1) "double_tap" else "double_tap_$taps"

        fun gestureIntent(action: String, taps: Int, gesture: String = gestureName(taps)): Intent = Intent(action)
            .putExtra(EXTRA_TAPS, taps)
            .putExtra(EXTRA_GESTURE, gesture)
            .addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
    }
}
