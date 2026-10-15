package io.github.ardyln.stylo2

import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * One thumb-button shortcut. Shortcuts form an ordered list: the first runs on a double tap,
 * the second on two double taps in a row, and so on.
 */
data class Shortcut(
    val id: String = UUID.randomUUID().toString(),
    val action: Action,
    /** Package (OPEN_APP), intent action (BROADCAST), webhook URL (WEBHOOK), else unused. */
    val arg: String? = null,
) {
    enum class Action {
        OPEN_APP, MEDIA_PLAY_PAUSE, MEDIA_NEXT, MEDIA_PREVIOUS, BROADCAST, WEBHOOK, BUZZ,
        TOGGLE_PRESENTATION, TOGGLE_POINTER, TOGGLE_FEEDBACK, NEXT_TEXTURE, NEXT_PAGE, PREVIOUS_PAGE,
        APPLY_PRESET, // arg: preset id

        // Only for presentation gestures. SCROLL_* take the number of wheel steps as arg.
        SCROLL_UP, SCROLL_DOWN, CLICK, LONG_CLICK, RIGHT_CLICK, MIDDLE_CLICK, NOTHING;

        val presentationOnly get() = this in PRESENTATION_ONLY
    }

    fun toJson(): JSONObject = JSONObject().put("id", id).put("action", action.name).put("arg", arg)

    companion object {
        private val PRESENTATION_ONLY = setOf(
            Action.SCROLL_UP, Action.SCROLL_DOWN, Action.CLICK, Action.LONG_CLICK, Action.RIGHT_CLICK,
            Action.MIDDLE_CLICK, Action.NOTHING,
        )

        fun fromJson(o: JSONObject) = Shortcut(
            id = o.getString("id"),
            action = Action.valueOf(o.getString("action")),
            arg = if (o.isNull("arg")) null else o.getString("arg"),
        )
    }
}

/** The ordered shortcut list, stored as JSON in the app's shared preferences. */
class ShortcutStore(private val prefs: SharedPreferences) {
    fun load(): List<Shortcut> {
        val json = prefs.getString(KEY, null) ?: return emptyList()
        return try {
            val a = JSONArray(json)
            (0 until a.length()).map { a.getJSONObject(it) }
                // v0.3 stored a tap count per shortcut; keep that order
                .sortedBy { it.optInt("taps", 0) }
                .mapNotNull { runCatching { Shortcut.fromJson(it) }.getOrNull() }
        } catch (e: org.json.JSONException) {
            emptyList()
        }
    }

    fun save(list: List<Shortcut>) {
        val a = JSONArray()
        list.forEach { a.put(it.toJson()) }
        prefs.edit().putString(KEY, a.toString()).apply()
    }

    companion object {
        const val KEY = "button_shortcuts"
    }
}
