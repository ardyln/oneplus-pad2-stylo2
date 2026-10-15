package io.github.ardyln.stylo2.helper;

import android.system.Os;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Root helper for presentation mode, run by module/stylus-bridge.sh through app_process.
 *
 * While the app asks for it (the file "pen_grab" in its data dir contains 1), this takes the pen's
 * HID mouse exclusively (EVIOCGRAB), so Android no longer sees it. The air-mouse movement and
 * buttons are passed on unchanged through a virtual mouse created with Android's uinput tool, and
 * the scroll steps (swipes on the touch area) go to the app as "REL_WHEEL UP" / "REL_WHEEL DOWN" on
 * pen_keys.fifo, so it can run the action the user chose. If this process dies, the kernel ends the
 * grab and the pen's mouse works directly again.
 */
public final class PenPointer {
    private static final String PEN_MOUSE = "OnePlus Stylo 2 Mouse";
    private static final int EVIOCGRAB = 0x40044590;
    private static final int EV_SYN = 0, EV_KEY = 1, EV_REL = 2;
    private static final int REL_X = 0, REL_Y = 1, REL_WHEEL = 8, REL_WHEEL_HI_RES = 11;
    private static final int EVENT_SIZE = 24; // struct input_event on 64-bit
    private static final String REGISTER = "{\"id\":1,\"command\":\"register\",\"name\":\"Stylo 2 pointer\","
            + "\"vid\":8921,\"pid\":14442,\"bus\":\"bluetooth\",\"configuration\":["
            + "{\"type\":\"UI_SET_EVBIT\",\"data\":[1,2]},{\"type\":\"UI_SET_KEYBIT\",\"data\":[272,273,274]},"
            + "{\"type\":\"UI_SET_RELBIT\",\"data\":[0,1]}]}";

    private final File grabFile;
    private final File keysFifo;
    private final LinkedBlockingQueue<String> keyLines = new LinkedBlockingQueue<>();
    private OutputStream uinput;

    private PenPointer(File files) {
        grabFile = new File(files, "pen_grab");
        keysFifo = new File(files, "pen_keys.fifo");
    }

    public static void main(String[] args) throws Exception {
        new PenPointer(new File(args[0])).run();
    }

    private void run() throws Exception {
        startKeyWriter();
        while (true) {
            String dev = findPenMouse();
            if (dev == null || !wantGrab()) {
                Thread.sleep(500);
                continue;
            }
            grabWhileWanted(dev);
        }
    }

    private boolean wantGrab() {
        try {
            byte[] b = java.nio.file.Files.readAllBytes(grabFile.toPath());
            return new String(b, StandardCharsets.UTF_8).trim().equals("1");
        } catch (IOException e) {
            return false;
        }
    }

    private static String findPenMouse() {
        File[] devices = new File("/sys/class/input").listFiles();
        if (devices == null) return null;
        for (File d : devices) {
            if (!d.getName().startsWith("event")) continue;
            try {
                String name = new String(java.nio.file.Files.readAllBytes(new File(d, "device/name").toPath()),
                        StandardCharsets.UTF_8).trim();
                if (name.equals(PEN_MOUSE)) return "/dev/input/" + d.getName();
            } catch (IOException ignored) {
            }
        }
        return null;
    }

    /** Grabs [dev] and forwards its events until the app no longer wants it or the pen disconnects. */
    private void grabWhileWanted(String dev) throws Exception {
        FileInputStream in = new FileInputStream(dev);
        FileDescriptor fd = in.getFD();
        Method ioctl = Os.class.getDeclaredMethod("ioctlInt", FileDescriptor.class, int.class);
        ioctl.setAccessible(true);
        ioctl.invoke(null, fd, EVIOCGRAB);
        log("grabbed " + dev);
        // No lambdas: the helper is compiled against android.jar, which lacks LambdaMetafactory
        Thread reader = new Thread(new Runnable() {
            @Override public void run() { forward(in); }
        }, "pen-mouse");
        reader.setDaemon(true);
        reader.start();
        while (reader.isAlive() && wantGrab()) Thread.sleep(300);
        in.close(); // ends the grab; the reader thread stops with an error
        log("released " + dev);
    }

    /** Reads input events and forwards each frame (up to SYN_REPORT). */
    private void forward(FileInputStream in) {
        byte[] raw = new byte[EVENT_SIZE];
        ByteBuffer ev = ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN);
        StringBuilder frame = new StringBuilder();
        try {
            while (true) {
                int n = 0;
                while (n < EVENT_SIZE) {
                    int r = in.read(raw, n, EVENT_SIZE - n);
                    if (r < 0) return;
                    n += r;
                }
                int type = ev.getShort(16) & 0xffff;
                int code = ev.getShort(18) & 0xffff;
                int value = ev.getInt(20);
                if (type == EV_REL && code == REL_WHEEL) {
                    keyLines.offer(value > 0 ? "REL_WHEEL UP" : "REL_WHEEL DOWN");
                } else if (type == EV_REL && (code == REL_X || code == REL_Y) || type == EV_KEY) {
                    if (frame.length() > 0) frame.append(',');
                    frame.append(type).append(',').append(code).append(',').append(value);
                } else if (type == EV_SYN && code == 0 && frame.length() > 0) {
                    send("{\"id\":1,\"command\":\"inject\",\"events\":[" + frame + ",0,0,0]}");
                    frame.setLength(0);
                }
                // REL_WHEEL_HI_RES and MSC events are dropped; the app sends its own scroll steps
            }
        } catch (IOException e) {
            // Device closed (released or pen disconnected)
        }
    }

    /** The virtual pointer, created on first use through Android's uinput tool. */
    private synchronized void send(String json) {
        try {
            if (uinput == null) {
                // Redirect.DISCARD (Java 9) doesn't exist on Android; /dev/null does the same
                Process p = new ProcessBuilder("uinput", "-").redirectErrorStream(true)
                        .redirectOutput(new File("/dev/null")).start();
                uinput = p.getOutputStream();
                uinput.write((REGISTER + "\n").getBytes(StandardCharsets.UTF_8));
            }
            uinput.write((json + "\n").getBytes(StandardCharsets.UTF_8));
            uinput.flush();
        } catch (IOException | RuntimeException | LinkageError e) {
            // Never let a forwarding error end the reader thread (and with it the pointer)
            log("uinput: " + e);
            uinput = null;
        }
    }

    /** Writes the scroll steps to the app; opening the pipe blocks until the app reads it. */
    private void startKeyWriter() {
        Thread t = new Thread(new Runnable() {
            @Override public void run() { writeKeys(); }
        }, "pen-keys-writer");
        t.setDaemon(true);
        t.start();
    }

    private void writeKeys() {
        while (true) {
            try (OutputStream out = new FileOutputStream(keysFifo)) {
                while (true) {
                    out.write((keyLines.take() + "\n").getBytes(StandardCharsets.UTF_8));
                    out.flush();
                }
            } catch (Exception e) {
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException ignored) {
                }
            }
        }
    }

    private static void log(String s) {
        android.util.Log.i("Stylo2Helper", s);
    }
}
