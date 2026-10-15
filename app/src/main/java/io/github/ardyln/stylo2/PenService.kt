package io.github.ardyln.stylo2

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import java.util.UUID

/**
 * Keeps an auto-reconnecting GATT connection to the bonded Stylo 2. On every connection it
 * sends the writing feedback setting (the pen forgets it when disconnected) and subscribes
 * to the thumb button.
 */
// targetSdk 28: the legacy BLUETOOTH/BLUETOOTH_ADMIN permissions apply, not BLUETOOTH_CONNECT,
// and the pre-33 GATT API is the one that matches it.
@SuppressLint("MissingPermission")
@Suppress("DEPRECATION")
class PenService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private val ops = ArrayDeque<() -> Unit>()
    private var busy = false
    private lateinit var settings: Settings
    private var gatt: BluetoothGatt? = null
    private var com: BluetoothGattCharacteristic? = null
    private var ready = false
    private var battery = -1
    private var onCharger = false
    private var tapCount = 0
    private var surfaceDownAt = 0L
    private var lastFeedbackBuzz = 0L
    /** What the pen was last told about presentation mode; null after a reconnect. */
    private var sentPresentation: Boolean? = null
    private var sentPresentationMode = -1
    private val runner by lazy { ShortcutRunner(this, settings) { write(PenProtocol.buzz()) } }
    private lateinit var bridge: StylusBridge
    private lateinit var foreground: ForegroundApp
    private var saberTool: Int? = null
    private var lastFeedback: ByteArray? = null

    private fun bridgeReady() = this::bridge.isInitialized && bridge.ready

    /** The app the pen is working in: the focused window, or the last resumed app as a fallback. */
    private val activeApp get() = if (FocusService.running) FocusService.focused else foreground.current

    private val foregroundPoll = object : Runnable {
        override fun run() {
            // Fallback when the accessibility service is off (misses focus changes between windows)
            if (!FocusService.running && getSystemService(PowerManager::class.java).isInteractive &&
                foreground.update()
            ) {
                onForegroundChanged()
            }
            handler.postDelayed(this, FOREGROUND_POLL_MS)
        }
    }

    private val opTimeout = Runnable {
        Log.w(TAG, "GATT operation timed out")
        done()
    }

    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key in Settings.FEEDBACK_KEYS || key?.startsWith("saber_") == true) sendFeedbackSetting()
        if (key == Settings.KEY_PRESENTATION || key == Settings.KEY_PRESENTATION_MODE) sendPresentation()
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                BluetoothAdapter.ACTION_STATE_CHANGED ->
                    when (intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, -1)) {
                        BluetoothAdapter.STATE_OFF -> {
                            disconnect()
                            setStatus(getString(R.string.status_bluetooth_off))
                        }
                        BluetoothAdapter.STATE_ON -> connect()
                    }
                BluetoothDevice.ACTION_BOND_STATE_CHANGED -> if (gatt == null) connect()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        settings = Settings(this)
        instance = this
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, getString(R.string.channel_status), NotificationManager.IMPORTANCE_MIN)
        )
        startForeground(NOTIFICATION_ID, notification(getString(R.string.status_waiting)))
        registerReceiver(receiver, IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED).apply {
            addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
        })
        settings.prefs.registerOnSharedPreferenceChangeListener(prefsListener)
        foreground = ForegroundApp(this)
        bridge = StylusBridge(
            this,
            onSaberTool = { tool -> handler.post { onSaberTool(tool) } },
            onPenKey = { key, value -> handler.post { onPenKey(key, value) } },
        )
        bridge.start()
        FocusService.listener = { handler.post { onForegroundChanged() } }
        handler.post(foregroundPoll)
        connect()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_BUZZ) write(PenProtocol.buzz())
        return START_STICKY
    }

    override fun onDestroy() {
        settings.prefs.unregisterOnSharedPreferenceChangeListener(prefsListener)
        unregisterReceiver(receiver)
        handler.removeCallbacks(foregroundPoll)
        FocusService.listener = null
        bridge.stop()
        disconnect()
        status = null
        instance = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // --- connection ---

    private fun connect() {
        val adapter = BluetoothAdapter.getDefaultAdapter()
        if (adapter == null || !adapter.isEnabled) {
            setStatus(getString(R.string.status_bluetooth_off))
            return
        }
        if (gatt != null) return
        val pen = adapter.bondedDevices.firstOrNull { it.name?.startsWith(PenProtocol.NAME_PREFIX) == true }
        if (pen == null) {
            setStatus(getString(R.string.status_not_paired))
            return
        }
        Log.i(TAG, "connecting to the pen (autoConnect)")
        setStatus(getString(R.string.status_waiting))
        gatt = pen.connectGatt(this, true, callback, BluetoothDevice.TRANSPORT_LE)
    }

    private fun resetConnectionState() {
        ready = false
        sentPresentation = null
        lastFeedback = null
        com = null
        battery = -1
        onCharger = false
        ops.clear()
        busy = false
        handler.removeCallbacks(opTimeout)
    }

    private fun disconnect() {
        resetConnectionState()
        gatt?.close()
        gatt = null
    }

    private val callback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, st: Int, state: Int) {
            handler.post {
                Log.i(TAG, "connection state=$state status=$st")
                EventLog.add(
                    EventLog.Source.PEN, "state=$state status=$st",
                    if (state == BluetoothProfile.STATE_CONNECTED) "Pen connected" else "Pen disconnected",
                )
                if (state == BluetoothProfile.STATE_CONNECTED) {
                    setStatus(getString(R.string.status_connecting))
                    g.discoverServices()
                } else if (state == BluetoothProfile.STATE_DISCONNECTED) {
                    // autoConnect keeps trying in the background; just reset our state.
                    resetConnectionState()
                    setStatus(getString(R.string.status_waiting))
                }
            }
        }

        override fun onServicesDiscovered(g: BluetoothGatt, st: Int) {
            handler.post { onDiscovered(g, st) }
        }

        override fun onDescriptorWrite(g: BluetoothGatt, d: BluetoothGattDescriptor, st: Int) {
            handler.post {
                if (st != BluetoothGatt.GATT_SUCCESS) Log.w(TAG, "subscribe ${d.characteristic.uuid} status=$st")
                done()
            }
        }

        override fun onCharacteristicWrite(g: BluetoothGatt, c: BluetoothGattCharacteristic, st: Int) {
            handler.post {
                if (st != BluetoothGatt.GATT_SUCCESS) Log.w(TAG, "write status=$st")
                done()
            }
        }

        override fun onCharacteristicRead(g: BluetoothGatt, c: BluetoothGattCharacteristic, st: Int) {
            val u = c.uuid
            val v = c.value
            handler.post {
                if (st == BluetoothGatt.GATT_SUCCESS && v != null && v.isNotEmpty()) {
                    EventLog.add(EventLog.Source.PEN, "read ${PenEvents.shortName(u)} ${v.toHex()}", PenEvents.incoming(u, v))
                    onValue(u, v)
                }
                done()
            }
        }

        override fun onCharacteristicChanged(g: BluetoothGatt, c: BluetoothGattCharacteristic) {
            val u = c.uuid
            val v = c.value ?: return
            if (v.isEmpty()) return
            Log.d(TAG, "notify $u ${v.toHex()}")
            EventLog.add(EventLog.Source.PEN, "${PenEvents.shortName(u)} ${v.toHex()}", PenEvents.incoming(u, v))
            handler.post { if (u == PenProtocol.BUTTON) onButton() else onValue(u, v) }
        }
    }

    private fun onDiscovered(g: BluetoothGatt, st: Int) {
        if (g != gatt) return
        val comService = g.getService(PenProtocol.COM_SERVICE)
        val statusService = g.getService(PenProtocol.STATUS_SERVICE)
        com = comService?.getCharacteristic(PenProtocol.COM_WRITE)
        val button = statusService?.getCharacteristic(PenProtocol.BUTTON)
        if (st != BluetoothGatt.GATT_SUCCESS || com == null || button == null) {
            Log.e(TAG, "unexpected GATT layout (status=$st); is this a Stylo 2?")
            setStatus(getString(R.string.status_unsupported))
            return
        }
        ready = true
        subscribe(g, com)
        subscribe(g, button)
        subscribe(g, statusService.getCharacteristic(PenProtocol.BATTERY))
        subscribe(g, statusService.getCharacteristic(PenProtocol.MAGNET))
        read(g, comService.getCharacteristic(PenProtocol.BATTERY_READ))
        sendFeedbackSetting()
        sendPresentation(force = true)
        setStatus(connectedText())
    }

    /** Presentation mode state, sent on every connection like the stock app does. */
    private fun sendPresentation(force: Boolean = false) {
        val on = presentationActive()
        val mode = settings.presentationMode
        if (!force && on == sentPresentation && (!on || mode == sentPresentationMode)) return
        if (!ready) return
        sentPresentation = on
        sentPresentationMode = mode
        write(PenProtocol.presentation(on))
        if (on) write(PenProtocol.presentationMode(mode))
    }

    /**
     * Presentation mode as the pen should have it: the setting, paused while a stylus-button app
     * such as Saber is focused so that the double tap there toggles the eraser.
     */
    private fun presentationActive() = settings.presentation && activeApp !in settings.stylusButtonApps

    private fun subscribe(g: BluetoothGatt, c: BluetoothGattCharacteristic?) {
        val ccc = c?.getDescriptor(PenProtocol.CCC) ?: return
        queue {
            g.setCharacteristicNotification(c, true)
            ccc.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            if (!g.writeDescriptor(ccc)) done()
        }
    }

    private fun read(g: BluetoothGatt, c: BluetoothGattCharacteristic?) {
        c ?: return
        queue { if (!g.readCharacteristic(c)) done() }
    }

    private fun onValue(u: UUID, v: ByteArray) {
        when (u) {
            PenProtocol.BATTERY, PenProtocol.BATTERY_READ -> battery = v[0].toInt() and 0xff
            PenProtocol.MAGNET -> {
                onCharger = (v[0].toInt() and 0x01) == 0
                if (settings.presentation) {
                    if (onCharger && !settings.presentationStays) {
                        // Like OxygenOS: presentation mode lasts until the pen is attached again.
                        EventLog.add(EventLog.Source.APP, "presentation=false", "Pen attached: presentation mode turned off")
                        settings.setPresentation(false)
                    } else if (!onCharger) {
                        // Kept on: make sure the pen is in presentation mode again once it's picked up.
                        sendPresentation(force = true)
                    }
                }
            }
            else -> return
        }
        setStatus(connectedText())
    }

    private fun connectedText(): String = when {
        battery < 0 -> getString(R.string.status_connected)
        onCharger -> getString(R.string.status_battery_charger, battery)
        else -> getString(R.string.status_battery, battery)
    }

    // --- commands ---

    /**
     * Sends the writing feedback setting if it changed. In Saber the texture follows its tool, even
     * when the main switch is off (like OxygenOS, where notes apps have their own feedback).
     */
    private fun sendFeedbackSetting() {
        var on = settings.feedbackOn
        var texture = settings.texture
        var level = settings.level
        val tool = saberTool?.let { Saber.Tool.byId(it) }
        if (settings.saberTexture && activeApp == Saber.PACKAGE && tool != null) {
            val t = tool.texture(settings.prefs)
            on = t != Saber.TEXTURE_OFF
            if (on) texture = t
            tool.level(settings.prefs).let { if (it != Saber.LEVEL_MAIN) level = it }
        }
        val cmd = PenProtocol.texture(on, texture, level)
        if (cmd.contentEquals(lastFeedback) || !ready) return
        lastFeedback = cmd
        Log.i(TAG, "writing feedback: ${cmd.toHex()}")
        write(cmd)
    }

    private fun onSaberTool(tool: Int?) {
        saberTool = tool
        val t = tool?.let { Saber.Tool.byId(it) }
        if (t != null) {
            val texture = t.texture(settings.prefs)
            val level = t.level(settings.prefs).takeIf { it != Saber.LEVEL_MAIN } ?: settings.level
            EventLog.add(
                EventLog.Source.SABER, "lastTool=$tool",
                "Active tool: ${getString(t.label)} → " +
                    PenEvents.textureSetting(texture != Saber.TEXTURE_OFF, texture, level) +
                    if (activeApp == Saber.PACKAGE) "" else " (applies when Saber is focused)",
            )
        }
        sendFeedbackSetting()
    }

    private fun onForegroundChanged() {
        Log.i(TAG, "active app: $activeApp")
        EventLog.add(
            EventLog.Source.FOCUS, activeApp ?: "?",
            "Focused app" + when {
                activeApp == Saber.PACKAGE -> ": Saber. Its tool settings apply"
                activeApp in settings.stylusButtonApps -> ": double tap acts as the stylus button"
                else -> ": shortcuts and the main texture apply"
            } + if (FocusService.running) "" else " (usage stats fallback)",
        )
        if (bridge.pressed && activeApp !in settings.stylusButtonApps) bridge.setPressed(false)
        sendFeedbackSetting()
        val wasActive = sentPresentation
        sendPresentation()
        if (settings.presentation && wasActive != sentPresentation) {
            EventLog.add(
                EventLog.Source.APP, "presentation=${sentPresentation}",
                if (sentPresentation == true) "Presentation mode resumed"
                else "Presentation mode paused in this app: the double tap acts as the stylus button",
            )
        }
    }

    private fun write(value: ByteArray) {
        if (!ready) return
        queue {
            val c = com
            val g = gatt
            if (c == null || g == null) {
                done()
                return@queue
            }
            EventLog.add(EventLog.Source.PEN, "→ ${value.toHex()}", PenEvents.outgoing(value))
            c.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            c.value = value
            if (!g.writeCharacteristic(c)) done()
        }
    }

    /**
     * The pen reports each double tap on the thumb button (single taps, holds and slides are
     * filtered out by its firmware). Consecutive double taps within the tap window form one
     * gesture; it runs once no shortcut can use more taps, or when the window passes.
     */
    private fun onButton() {
        if (!FocusService.running) foreground.update()
        // In stylus-button apps (Saber) presentation mode pauses, so the double tap toggles the eraser.
        if (!presentationActive() && bridge.ready && activeApp in settings.stylusButtonApps) {
            // In notes apps the double tap holds or releases a virtual stylus button (Saber: eraser).
            bridge.setPressed(!bridge.pressed)
            Log.i(TAG, "stylus button ${if (bridge.pressed) "held" else "released"} in $activeApp")
            EventLog.add(
                EventLog.Source.BUTTON, "BTN_STYLUS=${if (bridge.pressed) 1 else 0}",
                if (bridge.pressed) "Virtual stylus button held: Android adds it to the pen's input; Saber erases"
                else "Virtual stylus button released: Saber goes back to the previous tool",
            )
            if (settings.buttonBuzz) write(PenProtocol.buzz())
            return
        }
        tapCount++
        Log.i(TAG, "double tap #$tapCount")
        tapListener?.invoke(tapCount)
        if (settings.buttonBuzz) write(PenProtocol.buzz())
        handler.removeCallbacks(fireGesture)
        // The broadcast reports any count, so then it always waits for the window to close.
        // In presentation mode one double tap clicks, so that counts as a gesture too.
        val last = maxOf(settings.shortcuts.load().size, if (clicksOnDoubleTap()) 1 else 0)
        if (!settings.broadcastAll && tapCount >= last) {
            fireGesture.run()
        } else {
            handler.postDelayed(fireGesture, settings.tapWindowMs)
        }
    }

    private val fireGesture = Runnable {
        val taps = tapCount
        tapCount = 0
        if (taps == 0) return@Runnable
        Log.i(TAG, "gesture: $taps double tap(s)")
        if (taps == 1 && clicksOnDoubleTap()) {
            // In presentation mode a single double tap clicks; chained ones still run shortcuts.
            bridge.click()
            EventLog.add(EventLog.Source.APP, "BTN_LEFT", "Double tap in presentation mode → click at the pointer")
            return@Runnable
        }
        val shortcut = settings.shortcuts.load().getOrNull(taps - 1)
        EventLog.add(
            EventLog.Source.APP, "gesture taps=$taps",
            "$taps double tap(s) → " + (shortcut?.action?.name?.lowercase()?.replace('_', ' ') ?: "no shortcut") +
                if (settings.broadcastAll) ", broadcast sent" else "",
        )
        if (settings.broadcastAll) sendBroadcast(ShortcutRunner.gestureIntent(ShortcutRunner.ACTION_GESTURE, taps))
        settings.shortcuts.load().getOrNull(taps - 1)?.let { runner.run(it, taps, battery) }
    }

    /**
     * The pen's presentation keys (forwarded by the root helper): F19 is held while a finger is on
     * the touch surface near the tip. A short touch counts as a tap and clicks at the pointer.
     */
    private fun onPenKey(key: String, value: String) {
        val now = SystemClock.uptimeMillis()
        val down = value == "DOWN"
        if (key == "REL_WHEEL") {
            EventLog.add(EventLog.Source.PEN, "REL_WHEEL $value", "Swipe on the touch area: scroll ${value.lowercase()}")
            if (presentationActive() && settings.vibrateScroll) feedbackBuzz(now)
            return
        }
        if (key == "KEY_F19") {
            if (down) {
                surfaceDownAt = now
                return
            }
            val held = now - surfaceDownAt
            val tap = held < SURFACE_TAP_MS
            EventLog.add(
                EventLog.Source.PEN, "KEY_F19 ${held}ms",
                if (tap) "Tap on the surface near the tip" + if (settings.clickSurfaceTap) " → click at the pointer" else ""
                else "Surface near the tip held (air mouse)",
            )
            if (tap && presentationActive() && settings.clickSurfaceTap) bridge.click()
            if (tap && presentationActive() && settings.vibrateTap) feedbackBuzz(now)
        } else if (down) {
            EventLog.add(EventLog.Source.PEN, key, "Presentation key (meaning unknown)")
        }
    }

    /** A short vibration for presentation gestures, at most one per [FEEDBACK_GAP_MS]. */
    private fun feedbackBuzz(now: Long) {
        if (now - lastFeedbackBuzz < FEEDBACK_GAP_MS) return
        lastFeedbackBuzz = now
        write(PenProtocol.buzz())
    }

    private fun clicksOnDoubleTap() = presentationActive() && settings.clickDoubleTap && bridge.ready

    // --- one GATT operation at a time ---

    private fun queue(op: () -> Unit) {
        ops.addLast(op)
        next()
    }

    private fun next() {
        if (busy || ops.isEmpty()) return
        busy = true
        handler.postDelayed(opTimeout, OP_TIMEOUT_MS)
        ops.removeFirst()()
    }

    private fun done() {
        handler.removeCallbacks(opTimeout)
        busy = false
        next()
    }

    // --- status ---

    private fun setStatus(s: String) {
        status = s
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification(s))
    }

    private fun notification(text: String): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, SettingsActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        return Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stylus)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(text)
            .setContentIntent(open)
            .setOngoing(true)
            .build()
    }

    companion object {
        const val TAG = "Stylo2"
        const val ACTION_BUZZ = "io.github.ardyln.stylo2.BUZZ"
        private const val CHANNEL = "status"
        private const val NOTIFICATION_ID = 1
        private const val OP_TIMEOUT_MS = 5000L
        private const val FOREGROUND_POLL_MS = 1000L
        private const val SURFACE_TAP_MS = 250L
        private const val FEEDBACK_GAP_MS = 150L

        /** Called on the main thread with the running count after each double tap (for the UI). */
        @Volatile var tapListener: ((Int) -> Unit)? = null

        /** Whether the root helper's virtual stylus button is available (for the settings screen). */
        val stylusBridgeReady get() = instance?.bridgeReady() == true
        private var instance: PenService? = null

        /** Last known state for the settings screen; null while the service isn't running. */
        @Volatile var status: String? = null
            private set

        private fun ByteArray.toHex() = joinToString("") { "%02x".format(it) }
    }
}
