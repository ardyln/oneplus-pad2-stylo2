package io.github.ardyln.stylo2

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import com.google.android.material.appbar.MaterialToolbar

class SettingsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.settings_activity)
        setSupportActionBar(findViewById<MaterialToolbar>(R.id.toolbar))
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.content_frame, SettingsFragment(), TAG)
                .commit()
        }
    }

    override fun onResume() {
        super.onResume()
        startForegroundService(Intent(this, PenService::class.java))
    }

    companion object {
        private const val TAG = "SettingsActivity"
    }
}
