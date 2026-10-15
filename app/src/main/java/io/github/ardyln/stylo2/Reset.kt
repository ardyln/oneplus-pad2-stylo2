package io.github.ardyln.stylo2

import android.content.Context
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/** "Reset to defaults" for a page, or for everything: which settings belong where. */
enum class ResetScope(val title: Int, val message: Int, val matches: (String) -> Boolean) {
    SHORTCUTS(R.string.reset_shortcuts_title, R.string.reset_shortcuts_message, { key ->
        key == ShortcutStore.KEY || key == Settings.KEY_TAP_WINDOW || key == Settings.KEY_BROADCAST_ALL
    }),
    SABER(R.string.reset_saber_title, R.string.reset_saber_message, { key ->
        key == Settings.KEY_SABER_TEXTURE || (key.startsWith("saber_") && (key.endsWith("_texture") || key.endsWith("_level")))
    }),
    STYLUS_APPS(R.string.reset_stylus_apps_title, R.string.reset_stylus_apps_message, { key ->
        key == Settings.KEY_STYLUS_BUTTON_APPS
    }),
    PRESENTATION(R.string.reset_presentation_title, R.string.reset_presentation_message, { key ->
        key.startsWith("presentation")
    }),
    ALL(R.string.reset_all_title, R.string.reset_all_message, { true });

    /** Asks first, then removes the settings so their defaults apply again, and calls [after]. */
    fun confirm(context: Context, after: () -> Unit) {
        MaterialAlertDialogBuilder(context)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(R.string.reset_confirm) { _, _ ->
                val prefs = Settings(context).prefs
                val editor = prefs.edit()
                prefs.all.keys.filter(matches).forEach { editor.remove(it) }
                editor.apply()
                EventLog.add(EventLog.Source.APP, "reset ${name.lowercase()}", "Settings reset to defaults")
                after()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }
}
