package io.github.ardyln.stylo2

import android.content.ComponentName
import android.content.Context
import android.graphics.drawable.Icon
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

/**
 * Quick Settings tiles for settings that are handy to change on the go. They are active tiles:
 * [requestUpdate] asks Android to refresh them when a setting changes, e.g. when presentation mode
 * turns off because the pen was attached.
 */
abstract class SettingTile : TileService() {
    protected val settings by lazy { Settings(this) }

    override fun onStartListening() = refresh()

    override fun onClick() {
        toggle()
        refresh()
    }

    protected abstract fun toggle()

    /** State, label, subtitle and icon for the current setting. */
    protected abstract fun describe(tile: Tile)

    private fun refresh() {
        val tile = qsTile ?: return
        describe(tile)
        tile.updateTile()
    }

    companion object {
        private val TILES = listOf(PresentationTile::class.java, FeedbackTile::class.java, TextureTile::class.java)

        fun requestUpdate(context: Context) {
            for (t in TILES) {
                try {
                    requestListeningState(context, ComponentName(context, t))
                } catch (_: RuntimeException) {
                    // The tile isn't added to Quick Settings
                }
            }
        }
    }
}

/** Presentation mode on/off. */
class PresentationTile : SettingTile() {
    override fun toggle() = settings.setPresentation(!settings.presentation)

    override fun describe(tile: Tile) {
        tile.state = if (settings.presentation) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = getString(R.string.tile_presentation)
        tile.subtitle = getString(
            when {
                !settings.presentation -> R.string.tile_off
                settings.presentationMode == 1 -> R.string.pointer_always_short
                else -> R.string.pointer_touch_short
            }
        )
        tile.icon = Icon.createWithResource(this, R.drawable.ic_presentation)
    }
}

/** Vibrate while writing on/off. */
class FeedbackTile : SettingTile() {
    override fun toggle() =
        settings.prefs.edit().putBoolean(Settings.KEY_FEEDBACK, !settings.feedbackOn).apply()

    override fun describe(tile: Tile) {
        tile.state = if (settings.feedbackOn) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = getString(R.string.tile_feedback)
        tile.subtitle = getString(if (settings.feedbackOn) R.string.tile_on else R.string.tile_off)
        tile.icon = Icon.createWithResource(this, R.drawable.ic_action_texture)
    }
}

/** Steps through the writing textures; unavailable while writing feedback is off. */
class TextureTile : SettingTile() {
    override fun toggle() {
        if (!settings.feedbackOn) return
        settings.prefs.edit().putString(Settings.KEY_TEXTURE, ((settings.texture + 1) % 4).toString()).apply()
    }

    override fun describe(tile: Tile) {
        val (name, icon) = when (settings.texture) {
            PenProtocol.TEXTURE_ERASER -> R.string.texture_eraser to R.drawable.ic_texture_eraser
            PenProtocol.TEXTURE_BALLPEN -> R.string.texture_ballpen to R.drawable.ic_texture_ballpen
            PenProtocol.TEXTURE_PEN -> R.string.texture_pen to R.drawable.ic_texture_pen
            else -> R.string.texture_pencil to R.drawable.ic_texture_pencil
        }
        tile.state = if (settings.feedbackOn) Tile.STATE_ACTIVE else Tile.STATE_UNAVAILABLE
        tile.label = getString(R.string.tile_texture)
        tile.subtitle = getString(name)
        tile.icon = Icon.createWithResource(this, icon)
    }
}
