package io.github.ardyln.stylo2

import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Toast
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreferenceCompat

class SettingsFragment : PreferenceFragmentCompat() {
    private lateinit var settings: Settings
    private lateinit var statusPreference: Preference
    private lateinit var texturePreference: TexturePreference
    private lateinit var focusPreference: Preference
    private lateinit var bridgePreference: Preference

    private val handler = Handler(Looper.getMainLooper())
    private val refresh = object : Runnable {
        override fun run() {
            statusPreference.summary = PenService.status ?: getString(R.string.status_not_running)
            focusPreference.summary = getString(if (FocusService.running) R.string.focus_on else R.string.focus_off)
            bridgePreference.summary = getString(
                if (PenService.stylusBridgeReady) R.string.bridge_ready else R.string.bridge_not_ready
            )
            handler.postDelayed(this, 1000)
        }
    }

    // Shortcuts, pages and the service can change settings; keep the summaries current.
    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        when (key) {
            Settings.KEY_FEEDBACK -> {
                findPreference<SwitchPreferenceCompat>(key)?.isChecked = settings.feedbackOn
                texturePreference.isEnabled = settings.feedbackOn
            }
            Settings.KEY_TEXTURE, Settings.KEY_LEVEL -> texturePreference.notifyChangedPublic()
            else -> updateSummaries()
        }
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.stylo2_settings, rootKey)
        settings = Settings(requireContext())

        statusPreference = findPreference(Settings.KEY_STATUS)!!
        texturePreference = findPreference("feedback_texture_widget")!!
        focusPreference = findPreference("focus_status")!!
        bridgePreference = findPreference(Settings.KEY_BRIDGE_STATUS)!!
        texturePreference.isEnabled = settings.feedbackOn

        open(Settings.KEY_SHORTCUTS_SCREEN, ShortcutsActivity::class.java)
        open("saber_screen", SaberToolsActivity::class.java)
        open("stylus_apps_screen", StylusAppsActivity::class.java)
        open(Settings.KEY_PRESENTATION_SCREEN, PresentationActivity::class.java)
        open("debug_log", DebugActivity::class.java)

        findPreference<Preference>(Settings.KEY_TEST_BUZZ)!!.setOnPreferenceClickListener {
            requireContext().startForegroundService(
                Intent(requireContext(), PenService::class.java).setAction(PenService.ACTION_BUZZ)
            )
            true
        }
        findPreference<Preference>("reset_all")!!.setOnPreferenceClickListener {
            ResetScope.ALL.confirm(requireContext()) { requireActivity().recreate() }
            true
        }
        focusPreference.setOnPreferenceClickListener {
            if (!FocusService.running && !FocusService.ensureEnabled(requireContext())) {
                Toast.makeText(requireContext(), R.string.focus_needs_module, Toast.LENGTH_LONG).show()
            }
            true
        }
    }

    private fun open(key: String, page: Class<*>) {
        findPreference<Preference>(key)!!.setOnPreferenceClickListener {
            startActivity(Intent(requireContext(), page))
            true
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // Like the system Settings: no lines between categories
        setDivider(null)
        listView.padForBottomBars()
    }

    override fun onResume() {
        super.onResume()
        settings.prefs.registerOnSharedPreferenceChangeListener(prefsListener)
        updateSummaries()
        texturePreference.notifyChangedPublic()
        handler.post(refresh)
    }

    override fun onPause() {
        settings.prefs.unregisterOnSharedPreferenceChangeListener(prefsListener)
        handler.removeCallbacks(refresh)
        super.onPause()
    }

    private fun updateSummaries() {
        val editor = ShortcutEditor(requireContext())
        val shortcuts = settings.shortcuts.load()
        findPreference<Preference>(Settings.KEY_SHORTCUTS_SCREEN)?.summary = when {
            shortcuts.isEmpty() -> getString(R.string.shortcuts_none)
            else -> {
                val shown = shortcuts.take(3).mapIndexed { i, s ->
                    getString(R.string.shortcut_pair, editor.tapsLabel(i + 1), editor.actionLabel(s))
                }
                val more = shortcuts.size - shown.size
                shown.joinToString(" · ") + if (more > 0) " " + getString(R.string.shortcuts_more, more) else ""
            }
        }
        findPreference<Preference>("saber_screen")?.summary = getString(
            when {
                !isInstalled(Saber.PACKAGE) -> R.string.saber_not_installed
                settings.saberTexture -> R.string.saber_summary_on
                else -> R.string.saber_summary_off
            }
        )
        val apps = settings.stylusButtonApps.map { appLabel(it) }.sortedBy { it.lowercase() }
        findPreference<Preference>("stylus_apps_screen")?.summary =
            if (apps.isEmpty()) getString(R.string.stylus_button_apps_none) else apps.joinToString(", ")
        findPreference<Preference>(Settings.KEY_PRESENTATION_SCREEN)?.summary = when {
            !settings.presentation -> getString(R.string.presentation_off)
            settings.presentationMode == 1 -> getString(R.string.presentation_on_always)
            else -> getString(R.string.presentation_on_touch)
        }
    }

    private fun isInstalled(pkg: String) = try {
        requireContext().packageManager.getApplicationInfo(pkg, 0)
        true
    } catch (e: PackageManager.NameNotFoundException) {
        false
    }

    private fun appLabel(pkg: String): String {
        val pm = requireContext().packageManager
        return try {
            pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
        } catch (e: PackageManager.NameNotFoundException) {
            pkg
        }
    }
}
