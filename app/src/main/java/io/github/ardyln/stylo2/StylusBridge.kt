package io.github.ardyln.stylo2

import android.content.Context
import android.os.FileObserver
import android.system.Os
import android.system.OsConstants
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.Executors

/**
 * Talks to the module's root helper (module/stylus-bridge.sh) through two files in our data dir:
 * - stylus_button.fifo: we write uinput JSON commands; the helper pipes them into Android's
 *   `uinput` tool, which creates a virtual "external stylus" with a stylus button. Android
 *   merges that button into the pen's touchscreen events. If our process dies the pipe
 *   closes, `uinput` exits and the device (and any held button) goes away.
 * - saber_tool: the helper writes Saber's active tool id (its ToolId index) here.
 */
class StylusBridge(
    context: Context,
    private val onSaberTool: (Int?) -> Unit,
    /**
     * Called on a background thread for the pen's presentation input: ("KEY_F19", "DOWN"/"UP") or
     * ("REL_WHEEL", "UP"/"DOWN").
     */
    private val onPenKey: (String, String) -> Unit,
) {
    private val fifo = File(context.filesDir, "stylus_button.fifo")
    private val keysFifo = File(context.filesDir, "pen_keys.fifo")
    private val keysThread = Thread(::readKeys, "pen-keys").apply { isDaemon = true }
    private val toolFile = File(context.filesDir, "saber_tool")
    private val grabFile = File(context.filesDir, "pen_grab")
    private val io = Executors.newSingleThreadExecutor()
    private var out: FileOutputStream? = null
    private var observer: FileObserver? = null

    /** True once the root helper has opened the pipe and the virtual device exists. */
    @Volatile var ready = false
        private set

    /** Whether the virtual stylus button is currently held. */
    @Volatile var pressed = false
        private set

    @Volatile private var stopped = false

    fun start() {
        if (!fifo.exists()) {
            try {
                Os.mkfifo(fifo.path, 0x180) // 0600
            } catch (e: Exception) {
                Log.w(PenService.TAG, "could not create the stylus button pipe: $e")
            }
        }
        if (!toolFile.exists()) toolFile.writeText("")
        if (!keysFifo.exists()) {
            try {
                Os.mkfifo(keysFifo.path, 0x180) // 0600
            } catch (e: Exception) {
                Log.w(PenService.TAG, "could not create the pen keys pipe: $e")
            }
        }
        keysThread.start()
        observer = object : FileObserver(toolFile, CLOSE_WRITE) {
            override fun onEvent(event: Int, path: String?) = onSaberTool(readTool())
        }.also { it.startWatching() }
        onSaberTool(readTool())
        io.execute { connect() }
    }

    fun stop() {
        stopped = true
        setGrab(false)
        observer?.stopWatching()
        // If connect() is still waiting for the helper, open the reading end ourselves so it returns.
        if (!ready) {
            try {
                Os.close(Os.open(fifo.path, OsConstants.O_RDONLY or OsConstants.O_NONBLOCK, 0))
            } catch (_: Exception) {
            }
        }
        io.execute {
            try {
                out?.close()
            } catch (_: IOException) {
            }
            out = null
            ready = false
            pressed = false
        }
        io.shutdown()
    }

    /**
     * A click at the mouse pointer through the virtual "Stylo 2 click" mouse: [button] is pressed for
     * [holdMs] (long enough, and the left button becomes a long press).
     */
    fun click(button: Int = BTN_LEFT, holdMs: Long = CLICK_MS.toLong()) {
        if (!ready) return
        io.execute {
            send("""{"id":2,"command":"inject","events":[1,$button,1,0,0,0]}""")
            send("""{"id":2,"command":"delay","duration":$holdMs}""")
            send("""{"id":2,"command":"inject","events":[1,$button,0,0,0,0]}""")
        }
    }

    /**
     * Asks the root pointer helper to take over the pen's HID mouse (presentation mode), so swipes
     * reach the app instead of scrolling directly. See helper/.../PenPointer.java.
     */
    fun setGrab(on: Boolean) {
        try {
            grabFile.writeText(if (on) "1" else "0")
        } catch (e: IOException) {
            Log.w(PenService.TAG, "pen_grab: $e")
        }
    }

    /** Scrolls [steps] wheel steps (positive is up) through the virtual "Stylo 2 click" mouse. */
    fun scroll(steps: Int) {
        if (!ready || steps == 0) return
        io.execute { send("""{"id":2,"command":"inject","events":[2,8,$steps,2,11,${steps * 120},0,0,0]}""") }
    }

    /** A key press (down and up) through the virtual "Stylo 2 keys" keyboard, e.g. [KEY_PAGEDOWN]. */
    fun key(code: Int) {
        if (!ready) return
        io.execute {
            send("""{"id":3,"command":"inject","events":[1,$code,1,0,0,0]}""")
            send("""{"id":3,"command":"delay","duration":$CLICK_MS}""")
            send("""{"id":3,"command":"inject","events":[1,$code,0,0,0,0]}""")
        }
    }

    /**
     * Reads the pen's presentation keys, which the root helper forwards from the pen's HID keyboard
     * as lines like "KEY_F19 DOWN". Blocks until the helper opens the pipe; reopens when it closes.
     */
    private fun readKeys() {
        while (!stopped) {
            try {
                keysFifo.bufferedReader().use { r ->
                    while (true) {
                        val parts = (r.readLine() ?: break).trim().split(Regex("\\s+"))
                        if (parts.size == 2) onPenKey(parts[0], parts[1])
                    }
                }
            } catch (e: IOException) {
                Log.w(PenService.TAG, "pen keys pipe: $e")
                Thread.sleep(5000)
            }
        }
    }

    fun setPressed(down: Boolean) {
        if (down == pressed) return
        pressed = down
        io.execute { send("""{"id":1,"command":"inject","events":[1,$BTN_STYLUS,${if (down) 1 else 0},0,0,0]}""") }
    }

    private fun readTool(): Int? = try {
        toolFile.readText().trim().toIntOrNull()
    } catch (_: IOException) {
        null
    }

    /** Blocks until the helper opens the reading end, then registers the virtual device. */
    private fun connect() {
        if (stopped || !fifo.exists()) return
        try {
            out = FileOutputStream(fifo)
            if (stopped) {
                out?.close()
                out = null
                return
            }
            send(REGISTER)
            send(REGISTER_CLICK)
            send(REGISTER_KEYS)
            ready = out != null
            Log.i(PenService.TAG, "virtual stylus button ready")
            EventLog.add(
                EventLog.Source.BUTTON, "uinput register",
                "Root helper connected: virtual \"Stylo 2 buttons\" device created (external stylus)",
            )
        } catch (e: IOException) {
            Log.w(PenService.TAG, "stylus button pipe: $e")
        }
    }

    private fun send(json: String) {
        val o = out ?: return
        try {
            o.write((json + "\n").toByteArray())
            o.flush()
        } catch (e: IOException) {
            // The helper went away; wait for it to come back.
            Log.w(PenService.TAG, "stylus button pipe closed: $e")
            EventLog.add(EventLog.Source.BUTTON, "pipe closed", "Root helper went away; waiting for it")
            out = null
            ready = false
            pressed = false
            connect()
        }
    }

    companion object {
        const val KEY_PAGEUP = 104
        const val KEY_PAGEDOWN = 109
        const val BTN_LEFT = 272
        const val BTN_RIGHT = 273
        const val BTN_MIDDLE = 274
        private const val CLICK_MS = 40
        private const val BTN_STYLUS = 331
        private const val BTN_STYLUS2 = 332
        private const val REGISTER = """{"id":1,"command":"register","name":"Stylo 2 buttons",""" +
            """"vid":8921,"pid":14442,"bus":"bluetooth","configuration":[""" +
            """{"type":"UI_SET_EVBIT","data":[1]},{"type":"UI_SET_KEYBIT","data":[$BTN_STYLUS,$BTN_STYLUS2]}]}"""

        /** A mouse with left, right and middle buttons; Android shares one pointer between all mice. */
        private const val REGISTER_CLICK = """{"id":2,"command":"register","name":"Stylo 2 click",""" +
            """"vid":8921,"pid":14442,"bus":"bluetooth","configuration":[""" +
            """{"type":"UI_SET_EVBIT","data":[1,2]},{"type":"UI_SET_KEYBIT","data":[$BTN_LEFT,$BTN_RIGHT,$BTN_MIDDLE]},""" +
            """{"type":"UI_SET_RELBIT","data":[0,1,8,11]}]}"""

        /** A keyboard with only the page keys, for the next/previous page shortcuts. */
        private const val REGISTER_KEYS = """{"id":3,"command":"register","name":"Stylo 2 keys",""" +
            """"vid":8921,"pid":14442,"bus":"bluetooth","configuration":[""" +
            """{"type":"UI_SET_EVBIT","data":[1]},{"type":"UI_SET_KEYBIT","data":[$KEY_PAGEUP,$KEY_PAGEDOWN]}]}"""
    }
}
