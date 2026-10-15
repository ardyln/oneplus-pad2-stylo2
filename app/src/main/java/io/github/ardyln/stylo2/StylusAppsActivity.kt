package io.github.ardyln.stylo2

import android.content.Intent
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
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton

/** The apps where a double tap holds or releases the virtual stylus button (Saber: eraser). */
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
        show()
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
        val chosen = settings.stylusButtonApps.sortedBy { label(it).lowercase() }
        for (pkg in chosen) {
            val row = LayoutInflater.from(this).inflate(R.layout.item_app, apps, false)
            row.findViewById<TextView>(android.R.id.title).text = label(pkg)
            row.findViewById<ImageView>(android.R.id.icon).setImageDrawable(
                try { pm.getApplicationIcon(pkg) } catch (e: PackageManager.NameNotFoundException) { null }
            )
            row.findViewById<View>(R.id.remove).setOnClickListener {
                settings.setStylusButtonApps(settings.stylusButtonApps - pkg)
                show()
            }
            apps.addView(row)
        }
        empty.visibility = if (chosen.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun pickApp() {
        val pm = packageManager
        val chosen = settings.stylusButtonApps
        val candidates = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
            .map { it.activityInfo.packageName to it.loadLabel(pm).toString() }
            .distinctBy { it.first }
            .filter { it.first !in chosen && it.first != packageName }
            .sortedBy { it.second.lowercase() }
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.stylus_apps_add)
            .setItems(candidates.map { it.second }.toTypedArray()) { _, which ->
                settings.setStylusButtonApps(settings.stylusButtonApps + candidates[which].first)
                show()
            }
            .show()
    }

    private fun label(pkg: String): String = try {
        packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
    } catch (e: PackageManager.NameNotFoundException) {
        pkg
    }
}
