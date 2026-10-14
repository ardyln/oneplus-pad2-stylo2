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
class StylusBridge(context: Context, private val onSaberTool: (Int?) -> Unit) {
    private val fifo = File(context.filesDir, "stylus_button.fifo")
    private val toolFile = File(context.filesDir, "saber_tool")
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
        observer = object : FileObserver(toolFile, CLOSE_WRITE) {
            override fun onEvent(event: Int, path: String?) = onSaberTool(readTool())
        }.also { it.startWatching() }
        onSaberTool(readTool())
        io.execute { connect() }
    }

    fun stop() {
        stopped = true
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
        private const val BTN_STYLUS = 331
        private const val BTN_STYLUS2 = 332
        private const val REGISTER = """{"id":1,"command":"register","name":"Stylo 2 buttons",""" +
            """"vid":8921,"pid":14442,"bus":"bluetooth","configuration":[""" +
            """{"type":"UI_SET_EVBIT","data":[1]},{"type":"UI_SET_KEYBIT","data":[$BTN_STYLUS,$BTN_STYLUS2]}]}"""
    }
}
