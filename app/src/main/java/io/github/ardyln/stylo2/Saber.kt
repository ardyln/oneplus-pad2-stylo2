package io.github.ardyln.stylo2

import android.content.SharedPreferences

/** Integration with the Saber notes app (https://github.com/saber-notes/saber). */
object Saber {
    const val PACKAGE = "com.adilhanney.saber"

    /** Texture value for "no writing feedback" in the per-tool settings. */
    const val TEXTURE_OFF = -1

    /** Level value meaning "use the main strength setting". */
    const val LEVEL_MAIN = -1

    /**
     * Saber's tools in the order of its ToolId enum (packages/sbn/lib/tool_id.dart); the index is
     * what Saber stores in its "lastTool" pref. Each has a default texture and strength.
     */
    enum class Tool(
        val key: String,
        val label: Int,
        /** The same icon Saber shows for the tool. */
        val icon: Int,
        val defaultTexture: Int,
        val defaultLevel: Int = LEVEL_MAIN,
    ) {
        HIGHLIGHTER("highlighter", R.string.saber_tool_highlighter, R.drawable.ic_tool_highlighter, PenProtocol.TEXTURE_PEN, 0),
        FOUNTAIN_PEN("fountain_pen", R.string.saber_tool_fountain_pen, R.drawable.ic_texture_pen, PenProtocol.TEXTURE_PEN),
        BALLPOINT_PEN("ballpoint_pen", R.string.saber_tool_ballpoint_pen, R.drawable.ic_texture_ballpen, PenProtocol.TEXTURE_BALLPEN),
        PENCIL("pencil", R.string.saber_tool_pencil, R.drawable.ic_texture_pencil, PenProtocol.TEXTURE_PENCIL),
        SHAPE_PEN("shape_pen", R.string.saber_tool_shape_pen, R.drawable.ic_tool_shape_pen, PenProtocol.TEXTURE_PEN),
        ERASER("eraser", R.string.saber_tool_eraser, R.drawable.ic_texture_eraser, PenProtocol.TEXTURE_ERASER),
        SELECT("select", R.string.saber_tool_select, R.drawable.ic_tool_select, TEXTURE_OFF),
        TEXT("text", R.string.saber_tool_text, R.drawable.ic_tool_text, TEXTURE_OFF),
        LASER_POINTER("laser_pointer", R.string.saber_tool_laser_pointer, R.drawable.ic_tool_laser_pointer, TEXTURE_OFF);

        val textureKey get() = "saber_${key}_texture"
        val levelKey get() = "saber_${key}_level"

        fun texture(prefs: SharedPreferences) =
            prefs.getString(textureKey, defaultTexture.toString())!!.toInt()

        fun level(prefs: SharedPreferences) =
            prefs.getString(levelKey, defaultLevel.toString())!!.toInt()

        companion object {
            fun byId(id: Int): Tool? = entries.getOrNull(id)

            /**
             * The drawing tools as cards, in the order of Saber's toolbar. The first card is the pen
             * button, whose menu offers the fountain pen, ballpoint pen and shape pen.
             */
            val toolbarCards = listOf(
                listOf(FOUNTAIN_PEN, BALLPOINT_PEN, SHAPE_PEN),
                listOf(PENCIL), listOf(HIGHLIGHTER), listOf(SELECT), listOf(LASER_POINTER), listOf(ERASER), listOf(TEXT),
            )
        }
    }
}
