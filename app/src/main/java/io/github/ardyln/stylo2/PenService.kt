package io.github.ardyln.stylo2

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
import android.util.Log
import java.util.UUID

/**
 * Keeps an auto-reconnecting GATT connection to the bonded Stylo 2. On every connection it
 * sends the writing feedback setting (the pen forgets it when disconnected) and subscribes
 * to the thumb button.
 */
@Suppress("DEPRECATION") // the pre-33 GATT API, matching targetSdk 28
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
    private val runner by lazy { ShortcutRunner(this) { write(PenProtocol.buzz()) } }

    private val opTimeout = Runnable {
        Log.w(TAG, "GATT operation timed out")
        done()
    }

    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key in Settings.FEEDBACK_KEYS) sendFeedbackSetting()
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
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, getString(R.string.channel_status), NotificationManager.IMPORTANCE_MIN)
        )
        startForeground(NOTIFICATION_ID, notification(getString(R.string.status_waiting)))
        registerReceiver(receiver, IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED).apply {
            addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
        })
        settings.prefs.registerOnSharedPreferenceChangeListener(prefsListener)
        connect()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_BUZZ) write(PenProtocol.buzz())
        return START_STICKY
    }

    override fun onDestroy() {
        settings.prefs.unregisterOnSharedPreferenceChangeListener(prefsListener)
        unregisterReceiver(receiver)
        disconnect()
        status = null
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
                if (st == BluetoothGatt.GATT_SUCCESS && v != null && v.isNotEmpty()) onValue(u, v)
                done()
            }
        }

        override fun onCharacteristicChanged(g: BluetoothGatt, c: BluetoothGattCharacteristic) {
            val u = c.uuid
            val v = c.value ?: return
            if (v.isEmpty()) return
            Log.d(TAG, "notify $u ${v.toHex()}")
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
        setStatus(connectedText())
    }

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
            PenProtocol.MAGNET -> onCharger = (v[0].toInt() and 0x01) == 0
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

    private fun sendFeedbackSetting() {
        val cmd = PenProtocol.texture(settings.feedbackOn, settings.texture, settings.level)
        Log.i(TAG, "writing feedback: ${cmd.toHex()}")
        write(cmd)
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
        tapCount++
        Log.i(TAG, "double tap #$tapCount")
        tapListener?.invoke(tapCount)
        if (settings.buttonBuzz) write(PenProtocol.buzz())
        handler.removeCallbacks(fireGesture)
        // The broadcast reports any count, so then it always waits for the window to close.
        val last = settings.shortcuts.load().size
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
        if (settings.broadcastAll) sendBroadcast(ShortcutRunner.gestureIntent(ShortcutRunner.ACTION_GESTURE, taps))
        settings.shortcuts.load().getOrNull(taps - 1)?.let { runner.run(it, taps, battery) }
    }

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

        /** Called on the main thread with the running count after each double tap (for the UI). */
        @Volatile var tapListener: ((Int) -> Unit)? = null

        /** Last known state for the settings screen; null while the service isn't running. */
        @Volatile var status: String? = null
            private set

        private fun ByteArray.toHex() = joinToString("") { "%02x".format(it) }
    }
}
