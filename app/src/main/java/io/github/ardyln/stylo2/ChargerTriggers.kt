package io.github.ardyln.stylo2

import org.json.JSONObject

/** Actions for the pen going onto or coming off the magnetic charger, e.g. for home automation. */
enum class ChargerTrigger(val key: String, val label: Int, val icon: Int, val gesture: String, val attached: Boolean) {
    ATTACHED("charger_attached", R.string.charger_attached, R.drawable.ic_charger_attached, "pen_attached", true),
    DETACHED("charger_detached", R.string.charger_detached, R.drawable.ic_charger_detached, "pen_detached", false);

    fun action(settings: Settings): Shortcut {
        settings.prefs.getString(key, null)?.let { json ->
            runCatching { return Shortcut.fromJson(JSONObject(json)) }
        }
        return Shortcut(id = key, action = Shortcut.Action.NOTHING)
    }

    fun setAction(settings: Settings, s: Shortcut) =
        settings.prefs.edit().putString(key, s.copy(id = key).toJson().toString()).apply()

    companion object {
        fun of(attached: Boolean) = if (attached) ATTACHED else DETACHED
    }
}
