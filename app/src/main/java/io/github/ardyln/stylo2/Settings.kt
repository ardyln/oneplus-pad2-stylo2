package io.github.ardyln.stylo2

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager

/** Preference keys (shared with res/xml/stylo2_settings.xml) and typed access. */
class Settings(context: Context) {
    val prefs: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)

    val feedbackOn get() = prefs.getBoolean(KEY_FEEDBACK, true)
    val texture get() = prefs.getString(KEY_TEXTURE, "0")!!.toInt()
    val level get() = prefs.getInt(KEY_LEVEL, 2)
    val buttonBuzz get() = prefs.getBoolean(KEY_BUTTON_BUZZ, false)
    val tapWindowMs get() = prefs.getInt(KEY_TAP_WINDOW, 1000).toLong()
    val broadcastAll get() = prefs.getBoolean(KEY_BROADCAST_ALL, false)
    val shortcuts = ShortcutStore(prefs)

    init {
        // v0.2 had a single "open an app" action; turn it into a shortcut once.
        val oldApp = prefs.getString(OLD_KEY_BUTTON_APP, null)
        if (prefs.getString(OLD_KEY_BUTTON_ACTION, null) == "open_app" && oldApp != null) {
            shortcuts.save(listOf(Shortcut(action = Shortcut.Action.OPEN_APP, arg = oldApp)) + shortcuts.load())
        }
        if (prefs.contains(OLD_KEY_BUTTON_ACTION) || prefs.contains(OLD_KEY_BUTTON_APP)) {
            prefs.edit().remove(OLD_KEY_BUTTON_ACTION).remove(OLD_KEY_BUTTON_APP).apply()
        }
    }

    companion object {
        const val KEY_STATUS = "status"
        const val KEY_FEEDBACK = "feedback_enable"
        const val KEY_TEXTURE = "feedback_texture"
        const val KEY_LEVEL = "feedback_level"
        const val KEY_BUTTON_BUZZ = "button_buzz"
        const val KEY_TAP_WINDOW = "button_tap_window"
        const val KEY_BROADCAST_ALL = "button_broadcast_all"
        const val KEY_SHORTCUTS_SCREEN = "shortcuts_screen"
        const val KEY_TEST_BUZZ = "test_buzz"

        private const val OLD_KEY_BUTTON_ACTION = "button_action"
        private const val OLD_KEY_BUTTON_APP = "button_app"

        val FEEDBACK_KEYS = setOf(KEY_FEEDBACK, KEY_TEXTURE, KEY_LEVEL)
    }
}
