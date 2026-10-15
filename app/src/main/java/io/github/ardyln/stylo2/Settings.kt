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
    val stylusButtonApps: Set<String> get() = prefs.getStringSet(KEY_STYLUS_BUTTON_APPS, null) ?: setOf(Saber.PACKAGE)
    val saberTexture get() = prefs.getBoolean(KEY_SABER_TEXTURE, true)
    val presentation get() = prefs.getBoolean(KEY_PRESENTATION, false)
    val presentationMode get() = prefs.getString(KEY_PRESENTATION_MODE, "0")!!.toInt()

    val clickDoubleTap get() = prefs.getBoolean(KEY_CLICK_DOUBLE_TAP, true)
    val clickSurfaceTap get() = prefs.getBoolean(KEY_CLICK_SURFACE_TAP, false)

    fun setPresentation(on: Boolean) = prefs.edit().putBoolean(KEY_PRESENTATION, on).apply()

    fun setStylusButtonApps(apps: Set<String>) = prefs.edit().putStringSet(KEY_STYLUS_BUTTON_APPS, apps).apply()
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
        const val KEY_STYLUS_BUTTON_APPS = "stylus_button_apps"
        const val KEY_SABER_TEXTURE = "saber_texture"
        const val KEY_BRIDGE_STATUS = "bridge_status"
        const val KEY_PRESENTATION = "presentation"
        const val KEY_PRESENTATION_MODE = "presentation_mode"
        const val KEY_CLICK_DOUBLE_TAP = "presentation_click_double_tap"
        const val KEY_CLICK_SURFACE_TAP = "presentation_click_surface_tap"
        const val KEY_PRESENTATION_SCREEN = "presentation_screen"

        private const val OLD_KEY_BUTTON_ACTION = "button_action"
        private const val OLD_KEY_BUTTON_APP = "button_app"

        val FEEDBACK_KEYS = setOf(KEY_FEEDBACK, KEY_TEXTURE, KEY_LEVEL, KEY_SABER_TEXTURE)
    }
}
