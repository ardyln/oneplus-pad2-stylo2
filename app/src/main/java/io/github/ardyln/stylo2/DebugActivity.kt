package io.github.ardyln.stylo2

import android.content.ClipData
import android.content.ClipboardManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Live log of the pen, the focused window, Saber's tool and the virtual stylus button, plus what
 * Android delivers when the pen is used over this screen (to see the merged stylus button).
 */
class DebugActivity : AppCompatActivity() {
    private val shown = mutableListOf<EventLog.Entry>()
    private val sources = EventLog.Source.entries.toMutableSet()
    private var paused = false
    private lateinit var list: RecyclerView
    private val adapter = Adapter()
    private val time = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
    private var lastInput: String? = null

    private val listener: (EventLog.Entry) -> Unit = { e ->
        if (!paused && e.source in sources) {
            shown += e
            adapter.notifyItemInserted(shown.size - 1)
            val lm = list.layoutManager as LinearLayoutManager
            if (lm.findLastVisibleItemPosition() >= shown.size - 3) list.scrollToPosition(shown.size - 1)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.debug_activity)
        setSupportActionBar(findViewById<MaterialToolbar>(R.id.toolbar))
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        title = getString(R.string.debug_title)

        list = findViewById(R.id.list)
        list.layoutManager = LinearLayoutManager(this)
        list.adapter = adapter
        list.padForBottomBars()

        val chips = findViewById<ChipGroup>(R.id.filters)
        for (source in EventLog.Source.entries) {
            chips.addView(Chip(this).apply {
                setText(source.label)
                isCheckable = true
                isChecked = true
                setOnCheckedChangeListener { _, on ->
                    if (on) sources += source else sources -= source
                    reload()
                }
            })
        }
        reload()
    }

    override fun onStart() {
        super.onStart()
        EventLog.addListener(listener)
    }

    override fun onStop() {
        EventLog.removeListener(listener)
        super.onStop()
    }

    private fun reload() {
        shown.clear()
        shown += EventLog.snapshot().filter { it.source in sources }
        adapter.notifyDataSetChanged()
        if (shown.isNotEmpty()) list.scrollToPosition(shown.size - 1)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.debug, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.pause -> {
                paused = !paused
                item.setTitle(if (paused) R.string.debug_resume else R.string.debug_pause)
                if (!paused) reload()
            }
            R.id.clear -> {
                EventLog.clear()
                reload()
            }
            R.id.copy -> {
                val text = shown.joinToString("\n") {
                    "${time.format(Date(it.time))} [${getString(it.source.label)}] ${it.raw} — ${it.meaning}"
                }
                getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Stylo 2 log", text))
                Toast.makeText(this, R.string.debug_copied, Toast.LENGTH_SHORT).show()
            }
            else -> return super.onOptionsItemSelected(item)
        }
        return true
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    // Pen input over this screen: logged when the tool, the buttons or hover/touch change.

    override fun dispatchGenericMotionEvent(ev: MotionEvent): Boolean {
        logInput(ev)
        return super.dispatchGenericMotionEvent(ev)
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        logInput(ev)
        return super.dispatchTouchEvent(ev)
    }

    private fun logInput(ev: MotionEvent) {
        val tool = ev.getToolType(0)
        if (tool != MotionEvent.TOOL_TYPE_STYLUS && tool != MotionEvent.TOOL_TYPE_ERASER) return
        val phase = when (ev.actionMasked) {
            MotionEvent.ACTION_HOVER_ENTER, MotionEvent.ACTION_HOVER_MOVE -> "hovering"
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> "touching"
            else -> "lifted"
        }
        val primary = ev.buttonState and MotionEvent.BUTTON_STYLUS_PRIMARY != 0
        val raw = "tool=${if (tool == MotionEvent.TOOL_TYPE_ERASER) "eraser" else "stylus"} " +
            "buttons=0x${Integer.toHexString(ev.buttonState)} $phase"
        if (raw == lastInput) return
        lastInput = raw
        EventLog.add(
            EventLog.Source.INPUT, raw,
            "Pen $phase over this screen" +
                if (primary) ", stylus button pressed (the virtual button, merged by Android)" else ", no button",
        )
    }

    private class Holder(view: View) : RecyclerView.ViewHolder(view) {
        val time: TextView = view.findViewById(R.id.time)
        val source: TextView = view.findViewById(R.id.source)
        val meaning: TextView = view.findViewById(R.id.meaning)
        val raw: TextView = view.findViewById(R.id.raw)
    }

    private inner class Adapter : RecyclerView.Adapter<Holder>() {
        override fun getItemCount() = shown.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            Holder(LayoutInflater.from(parent.context).inflate(R.layout.item_event, parent, false))

        override fun onBindViewHolder(h: Holder, position: Int) {
            val e = shown[position]
            h.time.text = time.format(Date(e.time))
            h.source.setText(e.source.label)
            h.meaning.text = e.meaning
            h.raw.text = e.raw
        }
    }
}
