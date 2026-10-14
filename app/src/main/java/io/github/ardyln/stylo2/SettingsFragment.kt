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
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class SettingsFragment : PreferenceFragmentCompat(), Preference.OnPreferenceChangeListener {
    private lateinit var settings: Settings
    private lateinit var statusPreference: Preference
    private lateinit var texturePreference: ListPreference
    private lateinit var levelPreference: SeekBarPreference
    private lateinit var shortcutsPreference: Preference
    private lateinit var stylusAppsPreference: Preference
    private lateinit var bridgePreference: Preference

    private val handler = Handler(Looper.getMainLooper())
    private val refresh = object : Runnable {
        override fun run() {
            statusPreference.summary = PenService.status ?: getString(R.string.status_not_running)
            bridgePreference.summary = getString(
                when {
                    !PenService.stylusBridgeReady -> R.string.bridge_not_ready
                    !FocusService.running -> R.string.bridge_focus_off
                    else -> R.string.bridge_ready
                }
            )
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
        stylusAppsPreference = findPreference(Settings.KEY_STYLUS_BUTTON_APPS)!!
        bridgePreference = findPreference(Settings.KEY_BRIDGE_STATUS)!!
        findPreference<Preference>("saber_tools")!!.setOnPreferenceClickListener {
            startActivity(Intent(requireContext(), SaberToolsActivity::class.java))
            true
        }
        stylusAppsPreference.setOnPreferenceClickListener {
            pickStylusButtonApps()
            true
        }
        updateStylusAppsSummary()

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

    private fun launcherApps(): List<Pair<String, String>> {
        val pm = requireContext().packageManager
        return pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
            .map { it.activityInfo.packageName to it.loadLabel(pm).toString() }
            .distinctBy { it.first }
            .sortedBy { it.second.lowercase() }
    }

    private fun updateStylusAppsSummary() {
        val chosen = settings.stylusButtonApps
        val labels = launcherApps().filter { it.first in chosen }.map { it.second }
        stylusAppsPreference.summary = if (labels.isEmpty()) getString(R.string.stylus_button_apps_none)
            else getString(R.string.stylus_button_apps_summary, labels.joinToString(", "))
    }

    private fun pickStylusButtonApps() {
        val apps = launcherApps()
        val chosen = settings.stylusButtonApps.toMutableSet()
        val checked = apps.map { it.first in chosen }.toBooleanArray()
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.stylus_button_apps_dialog)
            .setMultiChoiceItems(apps.map { it.second }.toTypedArray(), checked) { _, which, isChecked ->
                if (isChecked) chosen += apps[which].first else chosen -= apps[which].first
            }
            .setPositiveButton(android.R.string.ok) { _, _ ->
                settings.setStylusButtonApps(chosen)
                updateStylusAppsSummary()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun updateFeedbackEnabled(on: Boolean) {
        texturePreference.isEnabled = on
        levelPreference.isEnabled = on
    }
}
