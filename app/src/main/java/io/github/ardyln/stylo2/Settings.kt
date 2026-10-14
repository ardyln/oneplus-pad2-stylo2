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
    val buttonAction get() = prefs.getString(KEY_BUTTON_ACTION, ACTION_NONE)!!
    val buttonApp get() = prefs.getString(KEY_BUTTON_APP, null)
    val buttonBuzz get() = prefs.getBoolean(KEY_BUTTON_BUZZ, false)

    fun setButtonApp(pkg: String) = prefs.edit().putString(KEY_BUTTON_APP, pkg).apply()

    companion object {
        const val KEY_STATUS = "status"
        const val KEY_FEEDBACK = "feedback_enable"
        const val KEY_TEXTURE = "feedback_texture"
        const val KEY_LEVEL = "feedback_level"
        const val KEY_BUTTON_ACTION = "button_action"
        const val KEY_BUTTON_APP = "button_app"
        const val KEY_BUTTON_BUZZ = "button_buzz"
        const val KEY_TEST_BUZZ = "test_buzz"

        const val ACTION_NONE = "none"
        const val ACTION_OPEN_APP = "open_app"

        val FEEDBACK_KEYS = setOf(KEY_FEEDBACK, KEY_TEXTURE, KEY_LEVEL)
    }
}
