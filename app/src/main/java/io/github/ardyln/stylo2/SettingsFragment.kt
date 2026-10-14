package io.github.ardyln.stylo2

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SeekBarPreference
import androidx.preference.SwitchPreferenceCompat

class SettingsFragment : PreferenceFragmentCompat(), Preference.OnPreferenceChangeListener {
    private lateinit var settings: Settings
    private lateinit var statusPreference: Preference
    private lateinit var texturePreference: ListPreference
    private lateinit var levelPreference: SeekBarPreference
    private lateinit var shortcutsPreference: Preference

    private val handler = Handler(Looper.getMainLooper())
    private val refresh = object : Runnable {
        override fun run() {
            statusPreference.summary = PenService.status ?: getString(R.string.status_not_running)
            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.stylo2_settings, rootKey)
        settings = Settings(requireContext())

        statusPreference = findPreference(Settings.KEY_STATUS)!!
        texturePreference = findPreference(Settings.KEY_TEXTURE)!!
        levelPreference = findPreference(Settings.KEY_LEVEL)!!
        shortcutsPreference = findPreference(Settings.KEY_SHORTCUTS_SCREEN)!!

        findPreference<SwitchPreferenceCompat>(Settings.KEY_FEEDBACK)!!.onPreferenceChangeListener = this
        updateFeedbackEnabled(settings.feedbackOn)

        shortcutsPreference.setOnPreferenceClickListener {
            startActivity(Intent(requireContext(), ShortcutsActivity::class.java))
            true
        }
        findPreference<Preference>(Settings.KEY_TEST_BUZZ)!!.setOnPreferenceClickListener {
            requireContext().startForegroundService(
                Intent(requireContext(), PenService::class.java).setAction(PenService.ACTION_BUZZ)
            )
            true
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // Like the system Settings: no lines between categories
        setDivider(null)
    }

    override fun onResume() {
        super.onResume()
        val count = settings.shortcuts.load().size
        shortcutsPreference.summary = if (count == 0) getString(R.string.shortcuts_none)
            else resources.getQuantityString(R.plurals.shortcuts_summary, count, count)
        handler.post(refresh)
    }

    override fun onPause() {
        handler.removeCallbacks(refresh)
        super.onPause()
    }

    override fun onPreferenceChange(preference: Preference, newValue: Any?): Boolean {
        if (preference.key == Settings.KEY_FEEDBACK) updateFeedbackEnabled(newValue as Boolean)
        return true
    }

    private fun updateFeedbackEnabled(on: Boolean) {
        texturePreference.isEnabled = on
        levelPreference.isEnabled = on
    }
}
