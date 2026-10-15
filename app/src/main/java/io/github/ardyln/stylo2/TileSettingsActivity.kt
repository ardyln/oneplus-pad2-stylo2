package io.github.ardyln.stylo2

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle

/**
 * Opened by Android when a Quick Settings tile is long-pressed; shows the page for that tile.
 * Has no UI of its own.
 */
class TileSettingsActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        @Suppress("DEPRECATION")
        val tile = intent.getParcelableExtra<ComponentName>(Intent.EXTRA_COMPONENT_NAME)?.className
        val page = when (tile) {
            PresentationTile::class.java.name -> PresentationActivity::class.java
            else -> SettingsActivity::class.java
        }
        startActivity(Intent(this, page).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        finish()
    }
}
