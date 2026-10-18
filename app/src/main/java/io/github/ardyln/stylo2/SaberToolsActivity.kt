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
        title = getString(R.string.saber_title)
        settings = Settings(this)

        val list = findViewById<RecyclerView>(R.id.list)
        list.layoutManager = LinearLayoutManager(this)
        list.adapter = Adapter()
        list.padForBottomBars(extra = (8 * resources.displayMetrics.density).toInt())
    }

    override fun onCreateOptionsMenu(menu: android.view.Menu): Boolean {
        menuInflater.inflate(R.menu.reset, menu)
        return true
    }

    override fun onOptionsItemSelected(item: android.view.MenuItem): Boolean {
        if (item.itemId != R.id.reset) return super.onOptionsItemSelected(item)
        ResetScope.SABER.confirm(this) { recreate() }
        return true
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun textureName(texture: Int) = textureName(this, texture)

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

        // Header (intro and switch), the tool cards, then the note about the integration
        override fun getItemCount() = cards.size + 2

        override fun getItemViewType(position: Int) = when {
            position == 0 -> TYPE_HEADER
            position == cards.size + 1 -> TYPE_FOOTER
            cards[position - 1].size > 1 -> TYPE_GROUP
            else -> TYPE_TOOL
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val inflater = LayoutInflater.from(parent.context)
            val layout = when (viewType) {
                TYPE_HEADER -> R.layout.item_saber_header
                TYPE_FOOTER -> R.layout.item_saber_footer
                TYPE_GROUP -> R.layout.item_saber_pen
                else -> R.layout.item_saber_tool
            }
            return object : RecyclerView.ViewHolder(inflater.inflate(layout, parent, false)) {}
        }

        override fun onBindViewHolder(h: RecyclerView.ViewHolder, position: Int) {
            when (getItemViewType(position)) {
                TYPE_FOOTER -> return
                TYPE_HEADER -> {
                    val toggle = h.itemView.findViewById<com.google.android.material.materialswitch.MaterialSwitch>(
                        R.id.main_switch_toggle,
                    )
                    toggle.isChecked = settings.saberTexture
                    h.itemView.findViewById<View>(R.id.main_switch).setOnClickListener {
                        settings.prefs.edit().putBoolean(Settings.KEY_SABER_TEXTURE, !settings.saberTexture).apply()
                        notifyDataSetChanged()
                    }
                    val eraser = h.itemView.findViewById<View>(R.id.eraser_row)
                    eraser.findViewById<TextView>(android.R.id.title).setText(R.string.saber_eraser_title)
                    eraser.findViewById<TextView>(android.R.id.summary).setText(R.string.saber_eraser_summary)
                    eraser.findViewById<com.google.android.material.materialswitch.MaterialSwitch>(R.id.toggle)
                        .isChecked = settings.saberEraser
                    eraser.setOnClickListener {
                        settings.prefs.edit().putBoolean(Settings.KEY_SABER_ERASER, !settings.saberEraser).apply()
                        notifyItemChanged(0)
                    }
                    // The vibration only applies while the double tap toggles the eraser
                    val buzz = h.itemView.findViewById<View>(R.id.eraser_buzz_row)
                    buzz.findViewById<TextView>(android.R.id.title).setText(R.string.saber_eraser_buzz_title)
                    buzz.findViewById<TextView>(android.R.id.summary).setText(R.string.saber_eraser_buzz_summary)
                    buzz.findViewById<com.google.android.material.materialswitch.MaterialSwitch>(R.id.toggle)
                        .isChecked = settings.saberEraserBuzz
                    buzz.isEnabled = settings.saberEraser
                    buzz.alpha = if (settings.saberEraser) 1f else 0.5f
                    buzz.setOnClickListener {
                        settings.prefs.edit().putBoolean(Settings.KEY_SABER_ERASER_BUZZ, !settings.saberEraserBuzz).apply()
                        notifyItemChanged(0)
                    }
                    return
                }
            }
            // The tool settings only apply while the switch is on
            h.itemView.alpha = if (settings.saberTexture) 1f else 0.5f
            val tools = cards[position - 1]
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
        private const val TYPE_HEADER = 2
        private const val TYPE_FOOTER = 3
        fun textureName(context: android.content.Context, texture: Int): String = context.getString(
            when (texture) {
                PenProtocol.TEXTURE_PENCIL -> R.string.texture_pencil
                PenProtocol.TEXTURE_ERASER -> R.string.texture_eraser
                PenProtocol.TEXTURE_BALLPEN -> R.string.texture_ballpen
                PenProtocol.TEXTURE_PEN -> R.string.texture_pen
                else -> R.string.texture_off
            }
        )

        /** The texture toggle buttons in view_tool_settings, by texture. */
        val BUTTONS = mapOf(
            Saber.TEXTURE_OFF to R.id.texture_off,
            PenProtocol.TEXTURE_PENCIL to R.id.texture_pencil,
            PenProtocol.TEXTURE_ERASER to R.id.texture_eraser,
            PenProtocol.TEXTURE_BALLPEN to R.id.texture_ballpen,
            PenProtocol.TEXTURE_PEN to R.id.texture_pen,
        )
    }
}
