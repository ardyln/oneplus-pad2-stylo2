package io.github.ardyln.stylo2

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** A saved copy of all settings. */
data class Preset(val id: String, val name: String, val savedAt: Long, val values: JSONObject)

/**
 * Presets live in their own preferences file, so resetting or applying settings never touches them.
 * Each value keeps its type: b(oolean), i(nt), l(ong), f(loat), s(tring) or set (of strings).
 */
class PresetStore(context: Context) {
    private val settings = Settings(context)
    private val store: SharedPreferences = context.getSharedPreferences("presets", Context.MODE_PRIVATE)

    fun load(): List<Preset> {
        val a = runCatching { JSONArray(store.getString(KEY, "[]")) }.getOrDefault(JSONArray())
        return (0 until a.length()).mapNotNull { i ->
            runCatching {
                val o = a.getJSONObject(i)
                Preset(o.getString("id"), o.getString("name"), o.getLong("savedAt"), o.getJSONObject("values"))
            }.getOrNull()
        }
    }

    fun get(id: String?) = load().firstOrNull { it.id == id }

    private fun save(list: List<Preset>) {
        val a = JSONArray()
        for (p in list) {
            a.put(JSONObject().put("id", p.id).put("name", p.name).put("savedAt", p.savedAt).put("values", p.values))
        }
        store.edit().putString(KEY, a.toString()).apply()
    }

    /** Saves the current settings as a new preset, or into [existing]. */
    fun saveCurrent(name: String, existing: Preset? = null): Preset {
        val p = Preset(existing?.id ?: UUID.randomUUID().toString(), name, System.currentTimeMillis(), snapshot())
        save(load().filter { it.id != p.id } + p)
        return p
    }

    fun rename(p: Preset, name: String) = save(load().map { if (it.id == p.id) it.copy(name = name) else it })

    fun delete(p: Preset) = save(load().filter { it.id != p.id })

    /** Replaces all settings with the preset's; the pen service picks the changes up. */
    fun apply(p: Preset) {
        val editor = settings.prefs.edit().clear()
        for (key in p.values.keys()) {
            val o = p.values.getJSONObject(key)
            when (o.getString("t")) {
                "b" -> editor.putBoolean(key, o.getBoolean("v"))
                "i" -> editor.putInt(key, o.getInt("v"))
                "l" -> editor.putLong(key, o.getLong("v"))
                "f" -> editor.putFloat(key, o.getDouble("v").toFloat())
                "s" -> editor.putString(key, o.getString("v"))
                "set" -> {
                    val arr = o.getJSONArray("v")
                    editor.putStringSet(key, (0 until arr.length()).map { arr.getString(it) }.toSet())
                }
            }
        }
        editor.apply()
        EventLog.add(EventLog.Source.APP, "preset ${p.name}", "Preset applied")
    }

    private fun snapshot(): JSONObject {
        val values = JSONObject()
        for ((key, v) in settings.prefs.all) {
            val typed = when (v) {
                is Boolean -> JSONObject().put("t", "b").put("v", v)
                is Int -> JSONObject().put("t", "i").put("v", v)
                is Long -> JSONObject().put("t", "l").put("v", v)
                is Float -> JSONObject().put("t", "f").put("v", v.toDouble())
                is String -> JSONObject().put("t", "s").put("v", v)
                is Set<*> -> JSONObject().put("t", "set").put("v", JSONArray(v.map { it.toString() }))
                else -> null
            }
            if (typed != null) values.put(key, typed)
        }
        return values
    }

    companion object {
        private const val KEY = "presets"
    }
}
