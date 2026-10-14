package io.github.ardyln.stylo2

import java.util.UUID

/** Plain-language meaning of the pen's notifications and our commands, for the debug log. */
object PenEvents {
    private fun texture(t: Int) = when (t) {
        PenProtocol.TEXTURE_PENCIL -> "pencil"
        PenProtocol.TEXTURE_ERASER -> "eraser"
        PenProtocol.TEXTURE_BALLPEN -> "ballpoint pen"
        PenProtocol.TEXTURE_PEN -> "pen"
        else -> "texture $t"
    }

    fun textureSetting(on: Boolean, t: Int, level: Int) =
        if (on) "${texture(t)}, strength $level" else "no vibration"

    /** A notification or read value from the pen. */
    fun incoming(u: UUID, v: ByteArray): String {
        val b = v[0].toInt() and 0xff
        return when (u) {
            PenProtocol.BUTTON -> "Double tap on the thumb button"
            PenProtocol.BATTERY, PenProtocol.BATTERY_READ -> "Battery $b%"
            PenProtocol.MAGNET -> if (b and 0x01 == 0) "Pen is on the magnetic charger" else "Pen is off the charger"
            PenProtocol.COM_WRITE ->
                if (v.size == 5 && b == 0x2c && v[1] == 0x90.toByte()) {
                    "Pen status: " + if (v[4].toInt() == 1) "active (off the charger)" else "idle (on the charger)"
                } else {
                    "Reply on the command channel (not decoded)"
                }
            else -> "Not decoded"
        }
    }

    /** A command we send to the pen. */
    fun outgoing(v: ByteArray): String = when {
        v.size == 5 && v[1] == 0x94.toByte() ->
            "Writing feedback: " + textureSetting(v[4].toInt() != 0, v[3].toInt(), v[4].toInt() - 1)
        v.contentEquals(PenProtocol.buzz()) -> "One short vibration"
        else -> "Command"
    }

    fun shortName(u: UUID) = u.toString().substring(0, 8)
}
