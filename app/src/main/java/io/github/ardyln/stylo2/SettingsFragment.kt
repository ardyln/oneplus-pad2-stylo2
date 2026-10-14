package io.github.ardyln.stylo2

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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
    private lateinit var appPreference: Preference

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
        appPreference = findPreference(Settings.KEY_BUTTON_APP)!!

        findPreference<SwitchPreferenceCompat>(Settings.KEY_FEEDBACK)!!.onPreferenceChangeListener = this
        findPreference<ListPreference>(Settings.KEY_BUTTON_ACTION)!!.onPreferenceChangeListener = this
        updateFeedbackEnabled(settings.feedbackOn)
        updateAppPreference(settings.buttonAction)

        appPreference.setOnPreferenceClickListener {
            pickApp()
            true
        }
        findPreference<Preference>(Settings.KEY_TEST_BUZZ)!!.setOnPreferenceClickListener {
            requireContext().startForegroundService(
                Intent(requireContext(), PenService::class.java).setAction(PenService.ACTION_BUZZ)
            )
            true
        }
    }

    override fun onViewCreated(view: android.view.View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // Like the system Settings: no lines between categories
        setDivider(null)
    }

    override fun onResume() {
        super.onResume()
        handler.post(refresh)
    }

    override fun onPause() {
        handler.removeCallbacks(refresh)
        super.onPause()
    }

    override fun onPreferenceChange(preference: Preference, newValue: Any?): Boolean {
        when (preference.key) {
            Settings.KEY_FEEDBACK -> updateFeedbackEnabled(newValue as Boolean)
            Settings.KEY_BUTTON_ACTION -> {
                updateAppPreference(newValue as String)
                if (newValue == Settings.ACTION_OPEN_APP && settings.buttonApp == null) pickApp()
            }
        }
        return true
    }

    private fun updateFeedbackEnabled(on: Boolean) {
        texturePreference.isEnabled = on
        levelPreference.isEnabled = on
    }

    private fun updateAppPreference(action: String) {
        appPreference.isVisible = action == Settings.ACTION_OPEN_APP
        val pkg = settings.buttonApp
        appPreference.summary = pkg?.let { appLabel(it) } ?: getString(R.string.button_app_none)
    }

    private fun appLabel(pkg: String): String {
        val pm = requireContext().packageManager
        return try {
            pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
        } catch (e: PackageManager.NameNotFoundException) {
            pkg
        }
    }

    private fun pickApp() {
        val pm = requireContext().packageManager
        val apps = pm.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0
        )
            .map { it.activityInfo.packageName to it.loadLabel(pm).toString() }
            .distinctBy { it.first }
            .sortedBy { it.second.lowercase() }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.button_app_title)
            .setItems(apps.map { it.second }.toTypedArray()) { _, which ->
                settings.setButtonApp(apps[which].first)
                updateAppPreference(Settings.ACTION_OPEN_APP)
            }
            .show()
    }
}
