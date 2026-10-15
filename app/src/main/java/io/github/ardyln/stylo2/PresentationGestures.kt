package io.github.ardyln.stylo2

import org.json.JSONObject

/**
 * What each presentation gesture does: an action like a shortcut, plus scroll, click and nothing.
 * Swipes reach the app only while the root pointer helper holds the pen's mouse.
 */
enum class PresentationGesture(val key: String, val label: Int, val icon: Int) {
    // The pen sends a wheel step up when swiping toward the tip, down when swiping away from it
    SWIPE_TOWARD_TIP("presentation_swipe_up", R.string.gesture_swipe_up, R.drawable.ic_gesture_swipe),
    SWIPE_AWAY("presentation_swipe_down", R.string.gesture_swipe_down, R.drawable.ic_gesture_swipe),
    DOUBLE_TAP("presentation_double_tap", R.string.gesture_double_tap, R.drawable.ic_gesture_click),
    TAP("presentation_tap", R.string.gesture_surface_tap, R.drawable.ic_gesture_click);

    fun action(settings: Settings): Shortcut {
        settings.prefs.getString(key, null)?.let { json ->
            runCatching { return Shortcut.fromJson(JSONObject(json)) }
        }
        return when (this) {
            SWIPE_TOWARD_TIP -> Shortcut(id = key, action = Shortcut.Action.SCROLL_UP, arg = "1")
            SWIPE_AWAY -> Shortcut(id = key, action = Shortcut.Action.SCROLL_DOWN, arg = "1")
            // Defaults follow the click switches of earlier versions
            DOUBLE_TAP -> Shortcut(id = key, action = if (settings.clickDoubleTap) Shortcut.Action.CLICK else Shortcut.Action.NOTHING)
            TAP -> Shortcut(id = key, action = if (settings.clickSurfaceTap) Shortcut.Action.CLICK else Shortcut.Action.NOTHING)
        }
    }

    fun setAction(settings: Settings, s: Shortcut) =
        settings.prefs.edit().putString(key, s.copy(id = key).toJson().toString()).apply()

    companion object {
        val KEYS = entries.map { it.key }.toSet()
    }
}
