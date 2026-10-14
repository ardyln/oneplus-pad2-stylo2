package io.github.ardyln.stylo2;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;
import java.util.ArrayDeque;
import java.util.UUID;

/**
 * Keeps an auto-reconnecting GATT connection to the bonded Stylo 2. On every connection it
 * sends the writing feedback setting (the pen forgets it when disconnected) and subscribes
 * to the thumb button.
 */
public final class PenService extends Service {
    static final String TAG = "Stylo2";
    static final String ACTION_APPLY = "io.github.ardyln.stylo2.APPLY";
    static final String ACTION_BUZZ = "io.github.ardyln.stylo2.BUZZ";

    private static final String CHANNEL = "status";
    private static final int NOTIFICATION_ID = 1;
    private static final long OP_TIMEOUT_MS = 5000;

    /** Last known state, shown by the settings screen. */
    static volatile String status = "not running";

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ArrayDeque<Runnable> ops = new ArrayDeque<>();
    private boolean busy;
    private Prefs prefs;
    private BluetoothGatt gatt;
    private BluetoothGattCharacteristic com;
    private boolean ready;
    private int battery = -1;
    private boolean charging;

    private final Runnable opTimeout = () -> {
        Log.w(TAG, "GATT operation timed out");
        done();
    };

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (BluetoothAdapter.ACTION_STATE_CHANGED.equals(action)) {
                int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, -1);
                if (state == BluetoothAdapter.STATE_OFF) {
                    disconnect();
                    setStatus("Bluetooth is off");
                } else if (state == BluetoothAdapter.STATE_ON) {
                    connect();
                }
            } else if (BluetoothDevice.ACTION_BOND_STATE_CHANGED.equals(action)) {
                if (gatt == null) connect();
            }
        }
    };

    @Override public void onCreate() {
        super.onCreate();
        prefs = new Prefs(this);
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel(
                CHANNEL, getString(R.string.channel_status), NotificationManager.IMPORTANCE_MIN));
        startForeground(NOTIFICATION_ID, notification(getString(R.string.status_waiting)));
        IntentFilter filter = new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED);
        filter.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
        registerReceiver(receiver, filter);
        connect();
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent != null ? intent.getAction() : null;
        if (ACTION_APPLY.equals(action)) {
            sendFeedbackSetting();
        } else if (ACTION_BUZZ.equals(action)) {
            write(PenProtocol.buzz());
        }
        return START_STICKY;
    }

    @Override public void onDestroy() {
        unregisterReceiver(receiver);
        disconnect();
        status = "not running";
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) {
        return null;
    }

    // --- connection ---

    private static BluetoothDevice findPen(BluetoothAdapter adapter) {
        for (BluetoothDevice d : adapter.getBondedDevices()) {
            String name = d.getName();
            if (name != null && name.startsWith(PenProtocol.NAME_PREFIX)) return d;
        }
        return null;
    }

    private void connect() {
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter == null || !adapter.isEnabled()) {
            setStatus("Bluetooth is off");
            return;
        }
        if (gatt != null) return;
        BluetoothDevice pen = findPen(adapter);
        if (pen == null) {
            setStatus(getString(R.string.status_not_paired));
            return;
        }
        Log.i(TAG, "connecting to the pen (autoConnect)");
        setStatus(getString(R.string.status_waiting));
        gatt = pen.connectGatt(this, true, callback, BluetoothDevice.TRANSPORT_LE);
    }

    private void disconnect() {
        ready = false;
        com = null;
        ops.clear();
        busy = false;
        handler.removeCallbacks(opTimeout);
        if (gatt != null) {
            gatt.close();
            gatt = null;
        }
    }

    private final BluetoothGattCallback callback = new BluetoothGattCallback() {
        @Override public void onConnectionStateChange(BluetoothGatt g, int st, int state) {
            handler.post(() -> {
                Log.i(TAG, "connection state=" + state + " status=" + st);
                if (state == BluetoothProfile.STATE_CONNECTED) {
                    setStatus(getString(R.string.status_connecting));
                    g.discoverServices();
                } else if (state == BluetoothProfile.STATE_DISCONNECTED) {
                    // autoConnect keeps trying in the background; just reset our state.
                    ready = false;
                    battery = -1;
                    com = null;
                    ops.clear();
                    busy = false;
                    handler.removeCallbacks(opTimeout);
                    setStatus(getString(R.string.status_waiting));
                }
            });
        }

        @Override public void onServicesDiscovered(BluetoothGatt g, int st) {
            handler.post(() -> onDiscovered(g, st));
        }

        @Override public void onDescriptorWrite(BluetoothGatt g, BluetoothGattDescriptor d, int st) {
            handler.post(() -> {
                if (st != BluetoothGatt.GATT_SUCCESS) {
                    Log.w(TAG, "subscribe " + d.getCharacteristic().getUuid() + " status=" + st);
                }
                done();
            });
        }

        @Override public void onCharacteristicWrite(BluetoothGatt g, BluetoothGattCharacteristic c, int st) {
            handler.post(() -> {
                if (st != BluetoothGatt.GATT_SUCCESS) Log.w(TAG, "write status=" + st);
                done();
            });
        }

        @Override public void onCharacteristicChanged(BluetoothGatt g, BluetoothGattCharacteristic c) {
            UUID u = c.getUuid();
            byte[] v = c.getValue();
            if (v == null || v.length == 0) return;
            if (PenProtocol.BUTTON.equals(u)) {
                handler.post(PenService.this::onButton);
            } else {
                handler.post(() -> onValue(u, v));
            }
        }

        @Override public void onCharacteristicRead(BluetoothGatt g, BluetoothGattCharacteristic c, int st) {
            UUID u = c.getUuid();
            byte[] v = c.getValue();
            handler.post(() -> {
                if (st == BluetoothGatt.GATT_SUCCESS && v != null && v.length > 0) onValue(u, v);
                done();
            });
        }
    };

    private void onDiscovered(BluetoothGatt g, int st) {
        if (g != gatt) return;
        BluetoothGattService comService = g.getService(PenProtocol.COM_SERVICE);
        BluetoothGattService statusService = g.getService(PenProtocol.STATUS_SERVICE);
        com = comService != null ? comService.getCharacteristic(PenProtocol.COM_WRITE) : null;
        BluetoothGattCharacteristic button =
                statusService != null ? statusService.getCharacteristic(PenProtocol.BUTTON) : null;
        if (st != BluetoothGatt.GATT_SUCCESS || com == null || button == null) {
            Log.e(TAG, "unexpected GATT layout (status=" + st + "); is this a Stylo 2?");
            setStatus(getString(R.string.status_unsupported));
            return;
        }
        ready = true;
        subscribe(g, com);
        subscribe(g, button);
        subscribe(g, statusService.getCharacteristic(PenProtocol.BATTERY));
        subscribe(g, statusService.getCharacteristic(PenProtocol.CHARGING));
        read(g, comService.getCharacteristic(PenProtocol.BATTERY_READ));
        read(g, comService.getCharacteristic(PenProtocol.CHARGING_READ));
        sendFeedbackSetting();
        setStatus(connectedText());
    }

    private void subscribe(BluetoothGatt g, BluetoothGattCharacteristic c) {
        BluetoothGattDescriptor ccc = c != null ? c.getDescriptor(PenProtocol.CCC) : null;
        if (ccc == null) return;
        queue(() -> {
            g.setCharacteristicNotification(c, true);
            ccc.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
            if (!g.writeDescriptor(ccc)) done();
        });
    }

    private void read(BluetoothGatt g, BluetoothGattCharacteristic c) {
        if (c == null) return;
        queue(() -> { if (!g.readCharacteristic(c)) done(); });
    }

    private void onValue(UUID u, byte[] v) {
        if (PenProtocol.BATTERY.equals(u) || PenProtocol.BATTERY_READ.equals(u)) {
            battery = v[0] & 0xff;
        } else if (PenProtocol.CHARGING.equals(u) || PenProtocol.CHARGING_READ.equals(u)) {
            charging = (v[0] & 0x01) != 0;
        } else {
            return;
        }
        setStatus(connectedText());
    }

    private String connectedText() {
        if (!ready) return status;
        if (battery < 0) return getString(R.string.status_connected);
        return getString(charging ? R.string.status_battery_charging : R.string.status_battery, battery);
    }

    // --- commands ---

    private void sendFeedbackSetting() {
        byte[] cmd = PenProtocol.texture(prefs.feedbackOn(), prefs.texture(), prefs.level());
        Log.i(TAG, "writing feedback: " + hex(cmd));
        write(cmd);
    }

    private void write(byte[] value) {
        if (!ready) return;
        queue(() -> {
            BluetoothGattCharacteristic c = com;
            BluetoothGatt g = gatt;
            if (c == null || g == null) { done(); return; }
            c.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
            c.setValue(value);
            if (!g.writeCharacteristic(c)) done();
        });
    }

    private void onButton() {
        Log.i(TAG, "thumb button");
        if (prefs.buttonBuzz()) write(PenProtocol.buzz());
        if (prefs.buttonAction() == Prefs.ACTION_OPEN_APP) {
            String pkg = prefs.buttonApp();
            Intent launch = pkg != null ? getPackageManager().getLaunchIntentForPackage(pkg) : null;
            if (launch == null) {
                Log.w(TAG, "no app to open: " + pkg);
                return;
            }
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try {
                startActivity(launch);
            } catch (RuntimeException e) {
                Log.e(TAG, "could not open " + pkg, e);
            }
        }
    }

    // --- one GATT operation at a time ---

    private void queue(Runnable op) {
        ops.addLast(op);
        next();
    }

    private void next() {
        if (busy || ops.isEmpty()) return;
        busy = true;
        handler.postDelayed(opTimeout, OP_TIMEOUT_MS);
        ops.removeFirst().run();
    }

    private void done() {
        handler.removeCallbacks(opTimeout);
        busy = false;
        next();
    }

    // --- status ---

    private void setStatus(String s) {
        status = s;
        getSystemService(NotificationManager.class).notify(NOTIFICATION_ID, notification(s));
    }

    private Notification notification(String text) {
        PendingIntent open = PendingIntent.getActivity(this, 0,
                new Intent(this, SettingsActivity.class), PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(this, CHANNEL)
                .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
                .setContentTitle(getString(R.string.app_name))
                .setContentText(text)
                .setContentIntent(open)
                .setOngoing(true)
                .build();
    }

    static String hex(byte[] v) {
        StringBuilder sb = new StringBuilder();
        for (byte b : v) sb.append(String.format("%02x", b));
        return sb.toString();
    }
}
