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
    private val editor by lazy { ShortcutEditor(this) }
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    // The explanations mention the gesture actions, so they're redrawn when those change
    private val sliderRefreshers = mutableListOf<() -> Unit>()
    private lateinit var vibrateScroll: View
    private lateinit var vibrateTap: View
    private lateinit var staysOn: View

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


        bindMsSlider(findViewById(R.id.tap_max), Settings.KEY_TAP_MAX, settings.tapMaxMs, 150, 800,
            R.string.tap_max_title, instantAtZero = false, ::tapMaxExplanation)
        bindMsSlider(findViewById(R.id.tap_wait), Settings.KEY_TAP_WAIT, settings.tapWaitMs, 0, 600,
            R.string.tap_wait_title, instantAtZero = true, ::tapWaitExplanation)
        staysOn = findViewById(R.id.stays_on)
        bindSwitchRow(staysOn, Settings.KEY_PRESENTATION_STAYS, R.string.presentation_stays_title,
            R.string.presentation_stays_summary) { settings.presentationStays }
        vibrateScroll = findViewById(R.id.vibrate_scroll)
        vibrateTap = findViewById(R.id.vibrate_tap)
        bindSwitchRow(vibrateScroll, Settings.KEY_VIBRATE_SCROLL, R.string.vibrate_scroll_title,
            R.string.vibrate_scroll_summary) { settings.vibrateScroll }
        bindSwitchRow(vibrateTap, Settings.KEY_VIBRATE_TAP, R.string.vibrate_tap_title,
            R.string.vibrate_tap_summary) { settings.vibrateTap }
    }

    override fun onResume() {
        super.onResume()
        handler.post(lastTouch)
        settings.prefs.registerOnSharedPreferenceChangeListener(prefsListener)
        refresh()
    }

    override fun onPause() {
        handler.removeCallbacks(lastTouch)
        settings.prefs.unregisterOnSharedPreferenceChangeListener(prefsListener)
        super.onPause()
    }

    override fun onCreateOptionsMenu(menu: android.view.Menu): Boolean {
        menuInflater.inflate(R.menu.reset, menu)
        return true
    }

    override fun onOptionsItemSelected(item: android.view.MenuItem): Boolean {
        if (item.itemId != R.id.reset) return super.onOptionsItemSelected(item)
        ResetScope.PRESENTATION.confirm(this) { recreate() }
        return true
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun actionName(g: PresentationGesture): String {
        val a = g.action(settings)
        return if (g == PresentationGesture.DOUBLE_TAP && a.action == Shortcut.Action.NOTHING) {
            getString(R.string.tap_runs_shortcuts)
        } else {
            editor.actionLabel(a)
        }
    }

    private fun seconds(ms: Long) = getString(R.string.seconds_value_2, ms / 1000f)

    /** What the "longest tap" value means, with the user's own actions. */
    private fun tapMaxExplanation(ms: Int) = getString(
        R.string.tap_max_explained, seconds(ms.toLong()), actionName(PresentationGesture.TAP),
    )

    /** What the "wait for a second tap" value means, with the user's own actions. */
    private fun tapWaitExplanation(ms: Int) = if (ms == 0) {
        getString(R.string.tap_wait_instant_explained, actionName(PresentationGesture.TAP), actionName(PresentationGesture.DOUBLE_TAP))
    } else {
        getString(
            R.string.tap_wait_explained, seconds(ms.toLong()), actionName(PresentationGesture.TAP),
            actionName(PresentationGesture.DOUBLE_TAP),
        )
    }

    /** Live feedback: how long the last touch on the touch area was, and what it counted as. */
    private val lastTouch = object : Runnable {
        override fun run() {
            findViewById<TapTimelineView>(R.id.timeline).tapMaxMs = settings.tapMaxMs
            val input = PenService.lastInput
            findViewById<TextView>(R.id.last_touch).text = when {
                !settings.presentation -> getString(R.string.last_touch_off)
                input == null -> getString(R.string.last_touch_none)
                else -> describe(input)
            }
            handler.postDelayed(this, 300)
        }
    }

    /** The live card's text: the key, what it counted as, and the action (or why it didn't run). */
    private fun describe(input: LastInput): String {
        val key = if (input.heldMs >= 0) getString(R.string.last_input_key_held, input.key, seconds(input.heldMs))
            else getString(R.string.last_input_key, input.key)
        val g = input.gesture
        val meaning = when (g) {
            null -> getString(R.string.last_input_hold, seconds(settings.tapMaxMs))
            PresentationGesture.TAP -> getString(R.string.last_input_tap, seconds(settings.tapMaxMs), actionName(g))
            PresentationGesture.DOUBLE_TAP -> getString(R.string.last_input_double, actionName(g))
            PresentationGesture.SWIPE_TOWARD_TIP -> getString(R.string.last_input_swipe_up, actionName(g))
            PresentationGesture.SWIPE_AWAY -> getString(R.string.last_input_swipe_down, actionName(g))
        }
        val notRun = if (g != null && !input.ran) "\n" + getString(R.string.last_input_not_run) else ""
        return "$key\n$meaning$notRun"
    }

    /** A slider in seconds for a setting stored in milliseconds; steps of 50 ms. */
    private fun bindMsSlider(
        row: View, key: String, current: Long, minMs: Int, maxMs: Int,
        title: Int, instantAtZero: Boolean, explain: (Int) -> String,
    ) {
        val titleView = row.findViewById<TextView>(android.R.id.title)
        val summaryView = row.findViewById<TextView>(android.R.id.summary)
        val slider = row.findViewById<com.google.android.material.slider.Slider>(R.id.slider)
        fun label(ms: Int) = if (instantAtZero && ms == 0) getString(R.string.tap_instant)
            else getString(R.string.seconds_value_2, ms / 1000f)
        slider.valueFrom = minMs.toFloat()
        slider.valueTo = maxMs.toFloat()
        slider.stepSize = 50f
        slider.value = current.coerceIn(minMs.toLong(), maxMs.toLong()).toFloat()
        slider.setLabelFormatter { label(it.toInt()) }
        fun show(ms: Int) {
            titleView.text = getString(R.string.slider_title_value, getString(title), label(ms))
            summaryView.text = explain(ms)
        }
        show(slider.value.toInt())
        slider.addOnChangeListener { _, v, fromUser ->
            show(v.toInt())
            if (fromUser) settings.prefs.edit().putInt(key, v.toInt()).apply()
        }
        sliderRefreshers += { show(slider.value.toInt()) }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun bindSwitchRow(row: View, key: String, title: Int, summary: Int, value: () -> Boolean) {
        row.findViewById<TextView>(android.R.id.title).setText(title)
        row.findViewById<TextView>(android.R.id.summary).setText(summary)
        row.setOnClickListener { settings.prefs.edit().putBoolean(key, !value()).apply() }
    }

    private fun refresh() {
        sliderRefreshers.forEach { it() }
        val on = settings.presentation
        val always = settings.presentationMode == 1
        mainSwitch.isChecked = on

        if (pointer.checkedButtonId != (if (always) R.id.pointer_always else R.id.pointer_touch)) {
            pointer.check(if (always) R.id.pointer_always else R.id.pointer_touch)
        }
        pointerDescription.setText(if (always) R.string.pointer_always_desc else R.string.pointer_touch_desc)
        staysOn.findViewById<MaterialSwitch>(R.id.toggle).isChecked = settings.presentationStays
        vibrateScroll.findViewById<MaterialSwitch>(R.id.toggle).isChecked = settings.vibrateScroll
        vibrateTap.findViewById<MaterialSwitch>(R.id.toggle).isChecked = settings.vibrateTap

        gestures.removeAllViews()
        // Assignable gestures first, then the ones with a fixed meaning
        for (g in PresentationGesture.entries) {
            val a = g.action(settings)
            val does = if (g == PresentationGesture.DOUBLE_TAP && a.action == Shortcut.Action.NOTHING) {
                getString(R.string.gesture_double_tap_nothing)
            } else {
                editor.actionLabel(a)
            }
            gesture(g.icon, getString(g.label), does) {
                editor.chooseAction(getString(g.label), a, presentation = true) { chosen ->
                    g.setAction(settings, chosen)
                }
            }
        }
        gesture(
            R.drawable.ic_gesture_hold,
            getString(if (always) R.string.gesture_point_always else R.string.gesture_point_touch),
            getString(R.string.gesture_point_does),
        )
        if (PresentationGesture.DOUBLE_TAP.action(settings).action != Shortcut.Action.NOTHING) {
            gesture(R.drawable.ic_gesture_click, getString(R.string.gesture_chained), getString(R.string.gesture_chained_does))
        }
        gesture(
            R.drawable.ic_gesture_attach, getString(R.string.gesture_attach),
            getString(if (settings.presentationStays) R.string.gesture_attach_stays else R.string.gesture_attach_does),
        )
        findViewById<TextView>(R.id.gestures_help).text = getString(R.string.gesture_actions_help) +
            if (PenService.stylusBridgeReady) "" else "\n" + getString(R.string.gesture_needs_helper)
        gestures.alpha = if (on) 1f else 0.6f
    }

    /** A gesture row; with [onClick] it can be tapped to change what the gesture does. */
    private fun gesture(icon: Int, what: String, does: String, onClick: (() -> Unit)? = null) {
        val row = LayoutInflater.from(this).inflate(R.layout.item_gesture, gestures, false)
        row.findViewById<ImageView>(android.R.id.icon).setImageResource(icon)
        row.findViewById<TextView>(android.R.id.title).text = what
        row.findViewById<TextView>(android.R.id.summary).text = does
        row.findViewById<View>(R.id.chevron).visibility = if (onClick != null) View.VISIBLE else View.GONE
        if (onClick != null) {
            val bg = android.util.TypedValue()
            theme.resolveAttribute(android.R.attr.selectableItemBackground, bg, true)
            row.setBackgroundResource(bg.resourceId)
            row.setOnClickListener { onClick() }
        }
        gestures.addView(row)
    }
}
