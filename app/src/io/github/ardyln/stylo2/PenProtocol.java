package io.github.ardyln.stylo2;

import java.util.UUID;

/** OnePlus Stylo 2 GATT layout and commands. See docs/PROTOCOL.md. */
final class PenProtocol {
    private PenProtocol() {}

    static final String NAME_PREFIX = "OnePlus Stylo 2";

    static final UUID CCC = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

    // Command channel: commands are written as 2c <cmd> <len> <payload...>
    static final UUID COM_SERVICE = UUID.fromString("18092dbc-2a69-11ec-8d3d-0242ac130003");
    static final UUID COM_WRITE = UUID.fromString("18093046-2a69-11ec-8d3d-0242ac130003");
    // Readable battery percent (the BATTERY notification only comes on change)
    static final UUID BATTERY_READ = UUID.fromString("1809314a-2a69-11ec-8d3d-0242ac130003");

    // Status service. The pen only reports the thumb button while other notifications are
    // subscribed too, so subscribe to the same set as the stock app: COM_WRITE, BUTTON, BATTERY, CHARGING.
    static final UUID STATUS_SERVICE = UUID.fromString("18093398-2a69-11ec-8d3d-0242ac130003");
    static final UUID BUTTON = UUID.fromString("180938ac-2a69-11ec-8d3d-0242ac130003");   // "01" per touch
    static final UUID BATTERY = UUID.fromString("180937bc-2a69-11ec-8d3d-0242ac130003");  // percent
    static final UUID CHARGING = UUID.fromString("1809396a-2a69-11ec-8d3d-0242ac130003"); // 20 on the charger, 21 off

    static final int TEXTURE_PENCIL = 0;
    static final int TEXTURE_ERASER = 1;
    static final int TEXTURE_BALLPEN = 2;
    static final int TEXTURE_PEN = 3;
    static final int LEVEL_MAX = 4;

    /** Writing feedback: on with a texture (0-3) and strength (0-4), or off. */
    static byte[] texture(boolean on, int type, int level) {
        type = Math.max(TEXTURE_PENCIL, Math.min(TEXTURE_PEN, type));
        level = Math.max(0, Math.min(LEVEL_MAX, level));
        return new byte[] {0x2c, (byte) 0x94, 0x02, (byte) type, (byte) (on ? level + 1 : 0)};
    }

    /** A single short vibration. */
    static byte[] buzz() {
        return new byte[] {0x2c, (byte) 0x92, 0x01, 0x02, (byte) 0xff, 0x02};
    }
}
