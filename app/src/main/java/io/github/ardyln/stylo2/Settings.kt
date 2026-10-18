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
    /** A texture of its own while one of the other apps (not Saber) is in front. */
    val stylusAppsFeel get() = prefs.getBoolean(KEY_STYLUS_APPS_FEEL, false)
    val stylusAppsTexture get() = prefs.getString(KEY_STYLUS_APPS_TEXTURE, PenProtocol.TEXTURE_PENCIL.toString())!!.toInt()
    val stylusAppsLevel get() = prefs.getString(KEY_STYLUS_APPS_LEVEL, null)?.toIntOrNull() ?: 4
    /** Whether the pen vibrates when a double tap presses or releases the stylus button in the other apps. */
    val stylusAppsBuzz get() = prefs.getBoolean(KEY_STYLUS_APPS_BUZZ, true)
    /** Apps other than Saber where a double tap holds Android's primary stylus button. */
    val otherStylusApps: Set<String> get() =
        (prefs.getStringSet(KEY_STYLUS_BUTTON_APPS, null) ?: emptySet()) - Saber.PACKAGE
    /** Whether a double tap toggles Saber's eraser (the primary stylus button). */
    val saberEraser get() = prefs.getBoolean(
        KEY_SABER_ERASER,
        // Settings from before v0.20 (e.g. an old preset): Saber was on or off the app list
        prefs.getStringSet(KEY_STYLUS_BUTTON_APPS, null)?.contains(Saber.PACKAGE) ?: true,
    )
    /** Whether the pen vibrates when a double tap toggles Saber's eraser. */
    val saberEraserBuzz get() = prefs.getBoolean(KEY_SABER_ERASER_BUZZ, true)
    /** Every app where a double tap holds the stylus button: Saber (if on) and the other apps. */
    val stylusButtonApps: Set<String> get() = otherStylusApps + if (saberEraser) setOf(Saber.PACKAGE) else emptySet()
    val saberTexture get() = prefs.getBoolean(KEY_SABER_TEXTURE, true)
    val presentation get() = prefs.getBoolean(KEY_PRESENTATION, false)
    val presentationMode get() = prefs.getString(KEY_PRESENTATION_MODE, "0")!!.toInt()

    val clickDoubleTap get() = prefs.getBoolean(KEY_CLICK_DOUBLE_TAP, true)
    val clickSurfaceTap get() = prefs.getBoolean(KEY_CLICK_SURFACE_TAP, false)
    val vibrateScroll get() = prefs.getBoolean(KEY_VIBRATE_SCROLL, true)
    val presentationStays get() = prefs.getBoolean(KEY_PRESENTATION_STAYS, false)
    /** Touches of the touch area up to this long are taps; longer ones are holds (pointer). */
    val tapMaxMs get() = prefs.getInt(KEY_TAP_MAX, 450).toLong()
    /**
     * How long a single tap waits before it runs; 0 runs it at once. The pen reports double taps on
     * its own (KEY_F20, no F19 presses), so this is only a safeguard.
     */
    val tapWaitMs get() = prefs.getInt(KEY_TAP_WAIT, 0).toLong()
    val vibrateTap get() = prefs.getBoolean(KEY_VIBRATE_TAP, true)

    fun setPresentation(on: Boolean) = prefs.edit().putBoolean(KEY_PRESENTATION, on).apply()

    fun setOtherStylusApps(apps: Set<String>) =
        prefs.edit().putStringSet(KEY_STYLUS_BUTTON_APPS, apps - Saber.PACKAGE).apply()
    val shortcuts = ShortcutStore(prefs)

    init {
        // Up to v0.19 Saber was on the stylus button app list; it has its own switch now.
        val oldList = prefs.getStringSet(KEY_STYLUS_BUTTON_APPS, null)
        if (oldList != null && Saber.PACKAGE in oldList) {
            prefs.edit().putBoolean(KEY_SABER_ERASER, true).putStringSet(KEY_STYLUS_BUTTON_APPS, oldList - Saber.PACKAGE).apply()
        } else if (oldList != null && !prefs.contains(KEY_SABER_ERASER)) {
            prefs.edit().putBoolean(KEY_SABER_ERASER, false).apply()
        }
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
        const val KEY_STYLUS_APPS_FEEL = "stylus_apps_feel"
        const val KEY_SABER_ERASER = "saber_double_tap_eraser"
        const val KEY_SABER_ERASER_BUZZ = "saber_eraser_vibrate"
        const val KEY_STYLUS_APPS_TEXTURE = "stylus_apps_texture"
        const val KEY_STYLUS_APPS_LEVEL = "stylus_apps_level"
        const val KEY_STYLUS_APPS_BUZZ = "stylus_apps_vibrate"
        const val KEY_SABER_TEXTURE = "saber_texture"
        const val KEY_BRIDGE_STATUS = "bridge_status"
        const val KEY_PRESENTATION = "presentation"
        const val KEY_PRESENTATION_MODE = "presentation_mode"
        const val KEY_CLICK_DOUBLE_TAP = "presentation_click_double_tap"
        const val KEY_CLICK_SURFACE_TAP = "presentation_click_surface_tap"
        const val KEY_PRESENTATION_SCREEN = "presentation_screen"
        const val KEY_VIBRATE_SCROLL = "presentation_vibrate_scroll"
        const val KEY_PRESENTATION_STAYS = "presentation_stays_on"
        const val KEY_TAP_MAX = "presentation_tap_max_ms"
        const val KEY_TAP_WAIT = "presentation_tap_wait_ms"
        const val KEY_VIBRATE_TAP = "presentation_vibrate_tap"

        private const val OLD_KEY_BUTTON_ACTION = "button_action"
        private const val OLD_KEY_BUTTON_APP = "button_app"

        val FEEDBACK_KEYS = setOf(KEY_FEEDBACK, KEY_TEXTURE, KEY_LEVEL, KEY_SABER_TEXTURE)
    }
}
