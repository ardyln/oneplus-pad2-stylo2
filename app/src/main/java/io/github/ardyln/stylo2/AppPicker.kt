package io.github.ardyln.stylo2

import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ImageView
import android.widget.ListView
import android.widget.TextView
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/** A dialog that lists the launchable apps with their icons, with a search field on top. */
object AppPicker {
    private data class App(val pkg: String, val label: String)

    fun show(context: Context, title: Int, exclude: Set<String> = emptySet(), onPicked: (String) -> Unit) {
        val pm = context.packageManager
        val all = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
            .map { App(it.activityInfo.packageName, it.loadLabel(pm).toString()) }
            .distinctBy { it.pkg }
            .filter { it.pkg !in exclude }
            .sortedBy { it.label.lowercase() }
        var shown = all
        val icons = HashMap<String, Drawable?>()

        val view = LayoutInflater.from(context).inflate(R.layout.dialog_app_picker, null)
        val list = view.findViewById<ListView>(R.id.apps)
        val noMatch = view.findViewById<TextView>(R.id.no_match)
        val adapter = object : BaseAdapter() {
            override fun getCount() = shown.size
            override fun getItem(position: Int) = shown[position]
            override fun getItemId(position: Int) = position.toLong()
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val row = convertView ?: LayoutInflater.from(context).inflate(R.layout.item_app_choice, parent, false)
                val app = shown[position]
                row.findViewById<TextView>(android.R.id.title).text = app.label
                // Icons are loaded on first sight only
                val icon = icons.getOrPut(app.pkg) { runCatching { pm.getApplicationIcon(app.pkg) }.getOrNull() }
                row.findViewById<ImageView>(android.R.id.icon).setImageDrawable(icon)
                return row
            }
        }
        list.adapter = adapter

        val dialog = MaterialAlertDialogBuilder(context)
            .setTitle(title)
            .setView(view)
            .setNegativeButton(android.R.string.cancel, null)
            .show()
        list.setOnItemClickListener { _, _, position, _ ->
            dialog.dismiss()
            onPicked(shown[position].pkg)
        }
        view.findViewById<TextView>(R.id.search).addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val q = s?.toString()?.trim()?.lowercase().orEmpty()
                shown = if (q.isEmpty()) all else all.filter { q in it.label.lowercase() || q in it.pkg.lowercase() }
                adapter.notifyDataSetChanged()
                noMatch.visibility = if (shown.isEmpty()) View.VISIBLE else View.GONE
            }
        })
    }
}
