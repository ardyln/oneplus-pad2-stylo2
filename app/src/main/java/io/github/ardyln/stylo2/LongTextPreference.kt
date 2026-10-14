package io.github.ardyln.stylo2

import android.content.Context
import android.util.AttributeSet
import android.widget.TextView
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder

/** A preference whose summary is shown in full, for explanatory text. */
class LongTextPreference(context: Context, attrs: AttributeSet?) : Preference(context, attrs) {
    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        (holder.findViewById(android.R.id.summary) as? TextView)?.maxLines = Int.MAX_VALUE
    }
}
