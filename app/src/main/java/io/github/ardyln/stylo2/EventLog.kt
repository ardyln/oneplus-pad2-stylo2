package io.github.ardyln.stylo2

import android.os.Handler
import android.os.Looper

/**
 * In-memory log of what the app sees and does, for the debug screen: each entry has the raw value
 * and what it means. Keeps the last [MAX] entries; nothing is written to disk.
 */
object EventLog {
    enum class Source(val label: Int) {
        PEN(R.string.debug_source_pen),
        FOCUS(R.string.debug_source_focus),
        SABER(R.string.debug_source_saber),
        BUTTON(R.string.debug_source_button),
        INPUT(R.string.debug_source_input),
        APP(R.string.debug_source_app),
    }

    data class Entry(val time: Long, val source: Source, val raw: String, val meaning: String)

    private const val MAX = 500
    private val entries = ArrayDeque<Entry>()
    private val listeners = mutableListOf<(Entry) -> Unit>()
    private val main = Handler(Looper.getMainLooper())

    fun add(source: Source, raw: String, meaning: String) {
        val e = Entry(System.currentTimeMillis(), source, raw, meaning)
        synchronized(entries) {
            entries.addLast(e)
            if (entries.size > MAX) entries.removeFirst()
        }
        main.post { listeners.toList().forEach { it(e) } }
    }

    fun snapshot(): List<Entry> = synchronized(entries) { entries.toList() }

    fun clear() = synchronized(entries) { entries.clear() }

    /** Listeners are called on the main thread. */
    fun addListener(l: (Entry) -> Unit) = main.post { listeners += l }

    fun removeListener(l: (Entry) -> Unit) = main.post { listeners -= l }
}
