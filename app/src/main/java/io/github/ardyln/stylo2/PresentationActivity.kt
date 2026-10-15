package io.github.ardyln.stylo2

import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.widget.NestedScrollView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.materialswitch.MaterialSwitch

/** Presentation mode: what each gesture does, and its options. */
class PresentationActivity : AppCompatActivity() {
    private lateinit var settings: Settings
    private lateinit var mainSwitch: MaterialSwitch
    private lateinit var gestures: LinearLayout
    private lateinit var pointer: MaterialButtonToggleGroup
    private lateinit var pointerDescription: TextView
    private lateinit var clickDoubleTap: View
    private lateinit var clickSurfaceTap: View

    // The service turns presentation mode off when the pen is attached.
    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> refresh() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.presentation_activity)
        setSupportActionBar(findViewById<MaterialToolbar>(R.id.toolbar))
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        title = getString(R.string.presentation_title)
        settings = Settings(this)
        findViewById<NestedScrollView>(R.id.scroll).padForBottomBars(extra = dp(16))

        mainSwitch = findViewById(R.id.main_switch_toggle)
        findViewById<View>(R.id.main_switch).setOnClickListener {
            settings.setPresentation(!settings.presentation)
        }

        gestures = findViewById(R.id.gestures)

        pointer = findViewById(R.id.pointer)
        pointerDescription = findViewById(R.id.pointer_description)
        pointer.addOnButtonCheckedListener { _, id, checked ->
            if (!checked) return@addOnButtonCheckedListener
            val mode = if (id == R.id.pointer_always) "1" else "0"
            if (settings.prefs.getString(Settings.KEY_PRESENTATION_MODE, "0") != mode) {
                settings.prefs.edit().putString(Settings.KEY_PRESENTATION_MODE, mode).apply()
            }
        }

        clickDoubleTap = findViewById(R.id.click_double_tap)
        clickSurfaceTap = findViewById(R.id.click_surface_tap)
        bindSwitchRow(clickDoubleTap, Settings.KEY_CLICK_DOUBLE_TAP, R.string.click_double_tap_title,
            R.string.click_double_tap_summary) { settings.clickDoubleTap }
        bindSwitchRow(clickSurfaceTap, Settings.KEY_CLICK_SURFACE_TAP, R.string.click_surface_title,
            R.string.click_surface_summary) { settings.clickSurfaceTap }
    }

    override fun onResume() {
        super.onResume()
        settings.prefs.registerOnSharedPreferenceChangeListener(prefsListener)
        refresh()
    }

    override fun onPause() {
        settings.prefs.unregisterOnSharedPreferenceChangeListener(prefsListener)
        super.onPause()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun bindSwitchRow(row: View, key: String, title: Int, summary: Int, value: () -> Boolean) {
        row.findViewById<TextView>(android.R.id.title).setText(title)
        row.findViewById<TextView>(android.R.id.summary).setText(summary)
        row.setOnClickListener { settings.prefs.edit().putBoolean(key, !value()).apply() }
    }

    private fun refresh() {
        val on = settings.presentation
        val always = settings.presentationMode == 1
        mainSwitch.isChecked = on

        if (pointer.checkedButtonId != (if (always) R.id.pointer_always else R.id.pointer_touch)) {
            pointer.check(if (always) R.id.pointer_always else R.id.pointer_touch)
        }
        pointerDescription.setText(if (always) R.string.pointer_always_desc else R.string.pointer_touch_desc)
        clickDoubleTap.findViewById<MaterialSwitch>(R.id.toggle).isChecked = settings.clickDoubleTap
        clickSurfaceTap.findViewById<MaterialSwitch>(R.id.toggle).isChecked = settings.clickSurfaceTap

        gestures.removeAllViews()
        gesture(R.drawable.ic_gesture_swipe, R.string.gesture_swipe, R.string.gesture_swipe_does)
        gesture(
            R.drawable.ic_gesture_hold,
            if (always) R.string.gesture_point_always else R.string.gesture_point_touch,
            R.string.gesture_point_does,
        )
        gesture(
            R.drawable.ic_gesture_click, R.string.gesture_double_tap,
            if (settings.clickDoubleTap) R.string.gesture_clicks else R.string.gesture_double_tap_shortcuts,
        )
        gesture(
            R.drawable.ic_gesture_click, R.string.gesture_surface_tap,
            if (settings.clickSurfaceTap) R.string.gesture_clicks else R.string.gesture_not_used,
        )
        gesture(R.drawable.ic_gesture_attach, R.string.gesture_attach, R.string.gesture_attach_does)
        gestures.alpha = if (on) 1f else 0.6f
    }

    private fun gesture(icon: Int, what: Int, does: Int) {
        val row = LayoutInflater.from(this).inflate(R.layout.item_gesture, gestures, false)
        row.findViewById<ImageView>(android.R.id.icon).setImageResource(icon)
        row.findViewById<TextView>(android.R.id.title).setText(what)
        row.findViewById<TextView>(android.R.id.summary).setText(does)
        gestures.addView(row)
    }
}
