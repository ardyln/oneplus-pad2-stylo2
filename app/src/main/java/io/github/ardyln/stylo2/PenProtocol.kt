package io.github.ardyln.stylo2

import java.util.UUID

/** OnePlus Stylo 2 GATT layout and commands. See docs/PROTOCOL.md. */
object PenProtocol {
    const val NAME_PREFIX = "OnePlus Stylo 2"

    val CCC: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    // Command channel: commands are written as 2c <cmd> <len> <payload...>
    val COM_SERVICE: UUID = UUID.fromString("18092dbc-2a69-11ec-8d3d-0242ac130003")
    val COM_WRITE: UUID = UUID.fromString("18093046-2a69-11ec-8d3d-0242ac130003")
    // Readable battery percent (the BATTERY notification only comes on change)
    val BATTERY_READ: UUID = UUID.fromString("1809314a-2a69-11ec-8d3d-0242ac130003")

    // Status service. The pen only reports the thumb button while other notifications are
    // subscribed too, so subscribe to the same set as the stock app: COM_WRITE, BUTTON, BATTERY, MAGNET.
    val STATUS_SERVICE: UUID = UUID.fromString("18093398-2a69-11ec-8d3d-0242ac130003")
    val BUTTON: UUID = UUID.fromString("180938ac-2a69-11ec-8d3d-0242ac130003") // "01" per touch
    val BATTERY: UUID = UUID.fromString("180937bc-2a69-11ec-8d3d-0242ac130003") // percent
    val MAGNET: UUID = UUID.fromString("1809396a-2a69-11ec-8d3d-0242ac130003") // 20 on the charger, 21 off

    const val TEXTURE_PENCIL = 0
    const val TEXTURE_ERASER = 1
    const val TEXTURE_BALLPEN = 2
    const val TEXTURE_PEN = 3
    const val LEVEL_MAX = 4

    /** Writing feedback: on with a texture (0-3) and strength (0-4), or off. */
    fun texture(on: Boolean, type: Int, level: Int): ByteArray {
        val t = type.coerceIn(TEXTURE_PENCIL, TEXTURE_PEN)
        val l = level.coerceIn(0, LEVEL_MAX)
        return byteArrayOf(0x2c, 0x94.toByte(), 0x02, t.toByte(), (if (on) l + 1 else 0).toByte())
    }

    /** A single short vibration. */
    fun buzz() = byteArrayOf(0x2c, 0x92.toByte(), 0x01, 0x02, 0xff.toByte(), 0x02)
}
