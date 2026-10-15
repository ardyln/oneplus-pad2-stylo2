package io.github.ardyln.stylo2

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.slider.Slider

/** A card per Saber tool: texture (icon buttons) and strength (slider). */
class SaberToolsActivity : AppCompatActivity() {
    private lateinit var settings: Settings

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.saber_tools_activity)
        setSupportActionBar(findViewById<MaterialToolbar>(R.id.toolbar))
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        title = getString(R.string.saber_tools_title)
        settings = Settings(this)

        val list = findViewById<RecyclerView>(R.id.list)
        list.layoutManager = LinearLayoutManager(this)
        list.adapter = Adapter()
        list.padForBottomBars(extra = (8 * resources.displayMetrics.density).toInt())
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun textureName(texture: Int): String = getString(
        when (texture) {
            PenProtocol.TEXTURE_PENCIL -> R.string.texture_pencil
            PenProtocol.TEXTURE_ERASER -> R.string.texture_eraser
            PenProtocol.TEXTURE_BALLPEN -> R.string.texture_ballpen
            PenProtocol.TEXTURE_PEN -> R.string.texture_pen
            else -> R.string.texture_off
        }
    )

    /** Fills one tool's controls (view_tool_settings); [compact] for the pen types inside the pen card. */
    private fun bindTool(view: View, tool: Saber.Tool, compact: Boolean) {
        val prefs = settings.prefs
        val icon: ImageView = view.findViewById(android.R.id.icon)
        val title: TextView = view.findViewById(android.R.id.title)
        val summary: TextView = view.findViewById(android.R.id.summary)
        val textures: MaterialButtonToggleGroup = view.findViewById(R.id.textures)
        val strengthLabel: TextView = view.findViewById(R.id.strength_label)
        val strength: Slider = view.findViewById(R.id.strength)

        title.setText(tool.label)
        icon.setImageResource(tool.icon)
        if (compact) {
            val px = (32 * resources.displayMetrics.density).toInt()
            icon.layoutParams = icon.layoutParams.apply { width = px; height = px }
            icon.setPadding(px / 5, px / 5, px / 5, px / 5)
            title.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleSmall)
            // In the single-row layout, keep the controls aligned with the other cards despite the indent
            val header: View = view.findViewById(R.id.tool_header)
            if (header.layoutParams.width > 0) {
                header.layoutParams = header.layoutParams.apply {
                    width -= resources.getDimensionPixelSize(R.dimen.pen_group_indent)
                }
            }
        }

        fun refresh() {
            val texture = tool.texture(prefs)
            val level = tool.level(prefs).takeIf { it != Saber.LEVEL_MAIN } ?: settings.level
            val on = texture != Saber.TEXTURE_OFF
            summary.text = if (on) getString(R.string.saber_tool_summary, textureName(texture), level)
                else textureName(texture)
            strength.isEnabled = on
            strengthLabel.isEnabled = on
        }

        textures.clearOnButtonCheckedListeners()
        textures.check(BUTTONS.getValue(tool.texture(prefs)))
        textures.addOnButtonCheckedListener { _, id, checked ->
            if (!checked) return@addOnButtonCheckedListener
            val texture = BUTTONS.entries.first { it.value == id }.key
            prefs.edit().putString(tool.textureKey, texture.toString()).apply()
            refresh()
        }

        strength.clearOnChangeListeners()
        strength.value = (tool.level(prefs).takeIf { it != Saber.LEVEL_MAIN } ?: settings.level)
            .coerceIn(0, PenProtocol.LEVEL_MAX).toFloat()
        strength.addOnChangeListener { _, value, fromUser ->
            if (!fromUser) return@addOnChangeListener
            prefs.edit().putString(tool.levelKey, value.toInt().toString()).apply()
            refresh()
        }
        refresh()
    }

    private inner class Adapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        private val cards: List<List<Saber.Tool>> = Saber.Tool.toolbarCards

        override fun getItemCount() = cards.size

        override fun getItemViewType(position: Int) = if (cards[position].size > 1) TYPE_GROUP else TYPE_TOOL

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val inflater = LayoutInflater.from(parent.context)
            val layout = if (viewType == TYPE_GROUP) R.layout.item_saber_pen else R.layout.item_saber_tool
            return object : RecyclerView.ViewHolder(inflater.inflate(layout, parent, false)) {}
        }

        override fun onBindViewHolder(h: RecyclerView.ViewHolder, position: Int) {
            val tools = cards[position]
            if (tools.size == 1) {
                bindTool(h.itemView, tools[0], compact = false)
                return
            }
            val pens: LinearLayout = h.itemView.findViewById(R.id.pens)
            pens.removeAllViews()
            val gap = (20 * resources.displayMetrics.density).toInt()
            for (tool in tools) {
                val block = LayoutInflater.from(pens.context).inflate(R.layout.view_tool_settings, pens, false)
                (block.layoutParams as ViewGroup.MarginLayoutParams).topMargin = gap
                pens.addView(block)
                bindTool(block, tool, compact = true)
            }
        }
    }

    companion object {
        private const val TYPE_TOOL = 0
        private const val TYPE_GROUP = 1
        private val BUTTONS = mapOf(
            Saber.TEXTURE_OFF to R.id.texture_off,
            PenProtocol.TEXTURE_PENCIL to R.id.texture_pencil,
            PenProtocol.TEXTURE_ERASER to R.id.texture_eraser,
            PenProtocol.TEXTURE_BALLPEN to R.id.texture_ballpen,
            PenProtocol.TEXTURE_PEN to R.id.texture_pen,
        )
    }
}
