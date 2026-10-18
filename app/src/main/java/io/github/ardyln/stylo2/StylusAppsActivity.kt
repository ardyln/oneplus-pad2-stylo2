package io.github.ardyln.stylo2

import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.widget.NestedScrollView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton

/** Apps other than Saber where a double tap holds or releases Android's primary stylus button. */
class StylusAppsActivity : AppCompatActivity() {
    private lateinit var settings: Settings
    private lateinit var apps: LinearLayout
    private lateinit var empty: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.stylus_apps_activity)
        setSupportActionBar(findViewById<MaterialToolbar>(R.id.toolbar))
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        title = getString(R.string.stylus_button_apps_title)
        settings = Settings(this)
        apps = findViewById(R.id.apps)
        empty = findViewById(R.id.empty)

        val density = resources.displayMetrics.density
        val add = findViewById<ExtendedFloatingActionButton>(R.id.add)
        add.setOnClickListener { pickApp() }
        findViewById<NestedScrollView>(R.id.scroll).padForBottomBars(extra = (88 * density).toInt()) { bottom ->
            (add.layoutParams as ViewGroup.MarginLayoutParams).bottomMargin = bottom + (16 * density).toInt()
            add.requestLayout()
        }
        bindFeel()
        bindBuzz()
        show()
    }

    /** Whether the pen vibrates when a double tap presses or releases the stylus button here. */
    private fun bindBuzz() {
        val row = findViewById<View>(R.id.buzz_switch)
        val toggle = row.findViewById<com.google.android.material.materialswitch.MaterialSwitch>(R.id.toggle)
        row.findViewById<TextView>(android.R.id.title).setText(R.string.stylus_apps_buzz_title)
        row.findViewById<TextView>(android.R.id.summary).setText(R.string.stylus_apps_buzz_summary)
        toggle.isChecked = settings.stylusAppsBuzz
        row.setOnClickListener {
            val on = !settings.stylusAppsBuzz
            settings.prefs.edit().putBoolean(Settings.KEY_STYLUS_APPS_BUZZ, on).apply()
            toggle.isChecked = on
        }
    }

    /** The optional writing feel for these apps: a switch, then texture and strength. */
    private fun bindFeel() {
        val row = findViewById<View>(R.id.feel_switch)
        val card = findViewById<View>(R.id.feel_card)
        val toggle = row.findViewById<com.google.android.material.materialswitch.MaterialSwitch>(R.id.toggle)
        row.findViewById<TextView>(android.R.id.title).setText(R.string.stylus_apps_feel_title)
        row.findViewById<TextView>(android.R.id.summary).setText(R.string.stylus_apps_feel_summary)
        toggle.isChecked = settings.stylusAppsFeel
        card.visibility = if (settings.stylusAppsFeel) View.VISIBLE else View.GONE
        row.setOnClickListener {
            val on = !settings.stylusAppsFeel
            settings.prefs.edit().putBoolean(Settings.KEY_STYLUS_APPS_FEEL, on).apply()
            toggle.isChecked = on
            card.visibility = if (on) View.VISIBLE else View.GONE
        }

        card.findViewById<ImageView>(android.R.id.icon).setImageResource(R.drawable.ic_action_texture)
        card.findViewById<TextView>(android.R.id.title).setText(R.string.stylus_apps_feel_card)
        val summary = card.findViewById<TextView>(android.R.id.summary)
        val textures = card.findViewById<com.google.android.material.button.MaterialButtonToggleGroup>(R.id.textures)
        val strengthLabel = card.findViewById<TextView>(R.id.strength_label)
        val strength = card.findViewById<com.google.android.material.slider.Slider>(R.id.strength)
        fun refresh() {
            val texture = settings.stylusAppsTexture
            val on = texture != Saber.TEXTURE_OFF
            summary.text = if (on) {
                getString(R.string.saber_tool_summary, SaberToolsActivity.textureName(this, texture), settings.stylusAppsLevel)
            } else {
                SaberToolsActivity.textureName(this, texture)
            }
            strength.isEnabled = on
            strengthLabel.isEnabled = on
        }
        textures.check(SaberToolsActivity.BUTTONS.getValue(settings.stylusAppsTexture))
        textures.addOnButtonCheckedListener { _, id, checked ->
            if (!checked) return@addOnButtonCheckedListener
            val texture = SaberToolsActivity.BUTTONS.entries.first { it.value == id }.key
            settings.prefs.edit().putString(Settings.KEY_STYLUS_APPS_TEXTURE, texture.toString()).apply()
            refresh()
        }
        strength.value = settings.stylusAppsLevel.coerceIn(0, PenProtocol.LEVEL_MAX).toFloat()
        strength.addOnChangeListener { _, value, fromUser ->
            if (!fromUser) return@addOnChangeListener
            settings.prefs.edit().putString(Settings.KEY_STYLUS_APPS_LEVEL, value.toInt().toString()).apply()
            refresh()
        }
        refresh()
    }

    override fun onCreateOptionsMenu(menu: android.view.Menu): Boolean {
        menuInflater.inflate(R.menu.reset, menu)
        return true
    }

    override fun onOptionsItemSelected(item: android.view.MenuItem): Boolean {
        if (item.itemId != R.id.reset) return super.onOptionsItemSelected(item)
        ResetScope.STYLUS_APPS.confirm(this) { recreate() }
        return true
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun show() {
        val pm = packageManager
        apps.removeAllViews()
        val chosen = settings.otherStylusApps.sortedBy { label(it).lowercase() }
        for (pkg in chosen) {
            val row = LayoutInflater.from(this).inflate(R.layout.item_app, apps, false)
            row.findViewById<TextView>(android.R.id.title).text = label(pkg)
            row.findViewById<ImageView>(android.R.id.icon).setImageDrawable(
                try { pm.getApplicationIcon(pkg) } catch (e: PackageManager.NameNotFoundException) { null }
            )
            row.findViewById<View>(R.id.remove).setOnClickListener {
                settings.setOtherStylusApps(settings.otherStylusApps - pkg)
                show()
            }
            apps.addView(row)
        }
        empty.visibility = if (chosen.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun pickApp() {
        val exclude = settings.otherStylusApps + packageName + Saber.PACKAGE
        AppPicker.show(this, R.string.stylus_apps_add, exclude) { pkg ->
            settings.setOtherStylusApps(settings.otherStylusApps + pkg)
            show()
        }
    }

    private fun label(pkg: String): String = try {
        packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
    } catch (e: PackageManager.NameNotFoundException) {
        pkg
    }
}
