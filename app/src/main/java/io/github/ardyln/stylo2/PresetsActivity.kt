package io.github.ardyln.stylo2

import android.os.Bundle
import android.text.InputType
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.widget.NestedScrollView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton

/** Saved copies of all settings, to switch between setups. */
class PresetsActivity : AppCompatActivity() {
    private lateinit var store: PresetStore
    private lateinit var list: LinearLayout
    private lateinit var empty: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.presets_activity)
        setSupportActionBar(findViewById<MaterialToolbar>(R.id.toolbar))
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        title = getString(R.string.presets_title)
        store = PresetStore(this)
        list = findViewById(R.id.presets)
        empty = findViewById(R.id.empty)

        val density = resources.displayMetrics.density
        val add = findViewById<ExtendedFloatingActionButton>(R.id.add)
        add.setOnClickListener {
            askName(R.string.presets_save, "") { name ->
                store.saveCurrent(name)
                show()
            }
        }
        findViewById<NestedScrollView>(R.id.scroll).padForBottomBars(extra = (88 * density).toInt()) { bottom ->
            (add.layoutParams as ViewGroup.MarginLayoutParams).bottomMargin = bottom + (16 * density).toInt()
            add.requestLayout()
        }
        show()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun show() {
        list.removeAllViews()
        val presets = store.load().sortedBy { it.name.lowercase() }
        for (p in presets) {
            val row = LayoutInflater.from(this).inflate(R.layout.item_gesture, list, false)
            row.findViewById<ImageView>(android.R.id.icon).setImageResource(R.drawable.ic_presets)
            row.findViewById<TextView>(android.R.id.title).text = p.name
            row.findViewById<TextView>(android.R.id.summary).text = getString(
                R.string.presets_saved_at,
                DateUtils.formatDateTime(this, p.savedAt, DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME),
            )
            row.findViewById<View>(R.id.chevron).visibility = View.VISIBLE
            val bg = android.util.TypedValue()
            theme.resolveAttribute(android.R.attr.selectableItemBackground, bg, true)
            row.setBackgroundResource(bg.resourceId)
            row.setOnClickListener { options(p) }
            list.addView(row)
        }
        empty.visibility = if (presets.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun options(p: Preset) {
        val choices = arrayOf(
            getString(R.string.presets_apply),
            getString(R.string.presets_update),
            getString(R.string.presets_rename),
            getString(R.string.presets_delete),
        )
        MaterialAlertDialogBuilder(this)
            .setTitle(p.name)
            .setItems(choices) { _, which ->
                when (which) {
                    0 -> confirm(R.string.presets_apply_title, R.string.presets_apply_message, p.name) {
                        store.apply(p)
                        Toast.makeText(this, getString(R.string.presets_applied, p.name), Toast.LENGTH_SHORT).show()
                    }
                    1 -> confirm(R.string.presets_update_title, R.string.presets_update_message, p.name) {
                        store.saveCurrent(p.name, p)
                        show()
                    }
                    2 -> askName(R.string.presets_rename, p.name) { name ->
                        store.rename(p, name)
                        show()
                    }
                    3 -> confirm(R.string.presets_delete_title, R.string.presets_delete_message, p.name) {
                        store.delete(p)
                        show()
                    }
                }
            }
            .show()
    }

    private fun confirm(title: Int, message: Int, name: String, onYes: () -> Unit) {
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(title, name))
            .setMessage(message)
            .setPositiveButton(android.R.string.ok) { _, _ -> onYes() }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun askName(title: Int, initial: String, onDone: (String) -> Unit) {
        val pad = (24 * resources.displayMetrics.density).toInt()
        val input = EditText(this).apply {
            setText(initial)
            setHint(R.string.presets_name_hint)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            setSingleLine()
        }
        val box = LinearLayout(this).apply {
            setPadding(pad, pad / 2, pad, 0)
            addView(input, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        }
        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(title)
            .setView(box)
            .setPositiveButton(android.R.string.ok, null)
            .setNegativeButton(android.R.string.cancel, null)
            .show()
        dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE).setOnClickListener {
            val name = input.text.toString().trim()
            if (name.isEmpty()) {
                input.error = getString(R.string.invalid_value)
            } else {
                dialog.dismiss()
                onDone(name)
            }
        }
    }
}
