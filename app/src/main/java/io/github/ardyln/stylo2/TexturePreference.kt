package io.github.ardyln.stylo2

import android.content.Context
import android.util.AttributeSet
import android.widget.TextView
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.slider.Slider

/**
 * Texture and strength of the writing feedback, with the same icon buttons and slider as the
 * Saber tool page. Stores into the feedback_texture (string) and feedback_level (int) settings.
 */
class TexturePreference(context: Context, attrs: AttributeSet?) : Preference(context, attrs) {
    init {
        layoutResource = R.layout.preference_texture
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        val settings = Settings(context)
        val group = holder.findViewById(R.id.textures) as MaterialButtonToggleGroup
        val slider = holder.findViewById(R.id.strength) as Slider
        val textureLabel = holder.findViewById(R.id.texture_label) as TextView
        val strengthLabel = holder.findViewById(R.id.strength_label) as TextView

        fun labels() {
            textureLabel.text = context.getString(R.string.texture_label, textureName(settings.texture))
            strengthLabel.text = context.getString(R.string.strength_label, settings.level)
        }

        group.clearOnButtonCheckedListeners()
        group.check(BUTTONS.getValue(settings.texture.coerceIn(0, 3)))
        group.addOnButtonCheckedListener { _, id, checked ->
            if (!checked) return@addOnButtonCheckedListener
            val t = BUTTONS.entries.first { it.value == id }.key
            settings.prefs.edit().putString(Settings.KEY_TEXTURE, t.toString()).apply()
            labels()
        }
        slider.clearOnChangeListeners()
        slider.value = settings.level.coerceIn(0, PenProtocol.LEVEL_MAX).toFloat()
        slider.addOnChangeListener { _, value, fromUser ->
            if (!fromUser) return@addOnChangeListener
            settings.prefs.edit().putInt(Settings.KEY_LEVEL, value.toInt()).apply()
            labels()
        }
        // Disabled together with the "Vibrate while writing" switch
        for (i in 0 until group.childCount) group.getChildAt(i).isEnabled = isEnabled
        slider.isEnabled = isEnabled
        holder.itemView.alpha = if (isEnabled) 1f else 0.5f
        labels()
    }

    /** Redraws with the current settings, e.g. after a shortcut changed the texture. */
    fun notifyChangedPublic() = notifyChanged()

    private fun textureName(t: Int) = context.getString(
        when (t) {
            PenProtocol.TEXTURE_ERASER -> R.string.texture_eraser
            PenProtocol.TEXTURE_BALLPEN -> R.string.texture_ballpen
            PenProtocol.TEXTURE_PEN -> R.string.texture_pen
            else -> R.string.texture_pencil
        }
    )

    companion object {
        private val BUTTONS = mapOf(
            PenProtocol.TEXTURE_PENCIL to R.id.texture_pencil,
            PenProtocol.TEXTURE_ERASER to R.id.texture_eraser,
            PenProtocol.TEXTURE_BALLPEN to R.id.texture_ballpen,
            PenProtocol.TEXTURE_PEN to R.id.texture_pen,
        )
    }
}
