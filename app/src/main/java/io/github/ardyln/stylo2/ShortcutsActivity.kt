package io.github.ardyln.stylo2

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.android.material.snackbar.Snackbar

/**
 * The ordered shortcut list: entry n runs on n double taps. Drag to reorder, swipe or tap the
 * delete icon to remove, tap an entry to change its action.
 */
class ShortcutsActivity : AppCompatActivity() {
    private lateinit var store: ShortcutStore
    private lateinit var editor: ShortcutEditor
    private lateinit var list: RecyclerView
    private lateinit var empty: TextView
    private lateinit var touchHelper: ItemTouchHelper
    private val items = mutableListOf<Shortcut>()
    private val adapter = Adapter()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.shortcuts_activity)
        setSupportActionBar(findViewById<MaterialToolbar>(R.id.toolbar))
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        store = Settings(this).shortcuts
        editor = ShortcutEditor(this)
        items += store.load()

        list = findViewById(R.id.list)
        empty = findViewById(R.id.empty)
        list.layoutManager = LinearLayoutManager(this)
        list.adapter = adapter
        touchHelper = ItemTouchHelper(TouchCallback())
        touchHelper.attachToRecyclerView(list)

        val add = findViewById<ExtendedFloatingActionButton>(R.id.add)
        add.setOnClickListener {
            editor.chooseAction(editor.tapsLabel(items.size + 1), null) { s ->
                items += s
                adapter.notifyItemInserted(items.size - 1)
                list.scrollToPosition(items.size - 1)
                saved()
            }
        }
        val density = resources.displayMetrics.density
        list.padForBottomBars(extra = (88 * density).toInt()) { bottom ->
            // Keep the add button above the bars too
            (add.layoutParams as ViewGroup.MarginLayoutParams).bottomMargin = bottom + (16 * density).toInt()
            add.requestLayout()
        }
        updateEmpty()
    }

    override fun onResume() {
        super.onResume()
        PenService.tapListener = { count -> highlight(count - 1) }
    }

    override fun onPause() {
        PenService.tapListener = null
        super.onPause()
    }

    /** Flashes the entry for the current tap count, like Settings does for a recognized fingerprint. */
    private fun highlight(position: Int) {
        if (position !in items.indices) return
        list.smoothScrollToPosition(position)
        list.post {
            val view = list.findViewHolderForAdapterPosition(position)?.itemView ?: return@post
            view.foreground?.setHotspot(view.width / 2f, view.height / 2f)
            view.isPressed = true
            view.postDelayed({ view.isPressed = false }, HIGHLIGHT_MS)
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun saved() {
        store.save(items)
        updateEmpty()
    }

    private fun updateEmpty() {
        empty.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
    }

    /** Positions from [from] on changed their tap count; refresh their titles. */
    private fun renumberFrom(from: Int) {
        if (from < items.size) adapter.notifyItemRangeChanged(from, items.size - from, PAYLOAD_RENUMBER)
    }

    private fun remove(position: Int) {
        val removed = items.removeAt(position)
        adapter.notifyItemRemoved(position)
        renumberFrom(position)
        saved()
        Snackbar.make(list, getString(R.string.shortcut_deleted, editor.actionLabel(removed)), Snackbar.LENGTH_LONG)
            .setAction(R.string.undo) {
                val at = position.coerceAtMost(items.size)
                items.add(at, removed)
                adapter.notifyItemInserted(at)
                renumberFrom(at + 1)
                saved()
            }
            .show()
    }

    private inner class Holder(view: View) : RecyclerView.ViewHolder(view) {
        val title: TextView = view.findViewById(android.R.id.title)
        val summary: TextView = view.findViewById(android.R.id.summary)
        val handle: ImageView = view.findViewById(R.id.drag_handle)
        val delete: ImageButton = view.findViewById(R.id.delete)
    }

    private inner class Adapter : RecyclerView.Adapter<Holder>() {
        override fun getItemCount() = items.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            Holder(LayoutInflater.from(parent.context).inflate(R.layout.item_shortcut, parent, false))

        @SuppressLint("ClickableViewAccessibility")
        override fun onBindViewHolder(h: Holder, position: Int) {
            val s = items[position]
            h.title.text = editor.tapsLabel(position + 1)
            h.summary.text = editor.actionLabel(s)
            h.itemView.setOnClickListener {
                val pos = h.bindingAdapterPosition
                if (pos == RecyclerView.NO_POSITION) return@setOnClickListener
                editor.chooseAction(editor.tapsLabel(pos + 1), items[pos]) { changed ->
                    items[pos] = changed
                    adapter.notifyItemChanged(pos)
                    saved()
                }
            }
            h.delete.setOnClickListener {
                val pos = h.bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION) remove(pos)
            }
            h.handle.setOnTouchListener { _, e ->
                if (e.actionMasked == MotionEvent.ACTION_DOWN) touchHelper.startDrag(h)
                false
            }
        }

        override fun onBindViewHolder(h: Holder, position: Int, payloads: MutableList<Any>) {
            if (payloads.contains(PAYLOAD_RENUMBER)) {
                h.title.text = editor.tapsLabel(position + 1)
            } else {
                onBindViewHolder(h, position)
            }
        }
    }

    private inner class TouchCallback : ItemTouchHelper.SimpleCallback(
        ItemTouchHelper.UP or ItemTouchHelper.DOWN, ItemTouchHelper.START or ItemTouchHelper.END,
    ) {
        private var dragFrom = -1

        // Dragging starts from the handle only
        override fun isLongPressDragEnabled() = false

        override fun onMove(rv: RecyclerView, from: RecyclerView.ViewHolder, to: RecyclerView.ViewHolder): Boolean {
            val a = from.bindingAdapterPosition
            val b = to.bindingAdapterPosition
            if (dragFrom < 0) dragFrom = a
            items.add(b, items.removeAt(a))
            adapter.notifyItemMoved(a, b)
            return true
        }

        override fun clearView(rv: RecyclerView, vh: RecyclerView.ViewHolder) {
            super.clearView(rv, vh)
            if (dragFrom >= 0) {
                adapter.notifyItemRangeChanged(0, items.size, PAYLOAD_RENUMBER)
                saved()
                dragFrom = -1
            }
        }

        override fun onSwiped(vh: RecyclerView.ViewHolder, direction: Int) {
            remove(vh.bindingAdapterPosition)
        }
    }

    companion object {
        private const val PAYLOAD_RENUMBER = "renumber"
        private const val HIGHLIGHT_MS = 400L
    }
}
