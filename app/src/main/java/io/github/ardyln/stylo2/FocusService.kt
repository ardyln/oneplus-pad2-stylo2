package io.github.ardyln.stylo2

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityWindowInfo

/**
 * Tracks the focused app window, which in desktop windowing mode is where the pen writes even
 * when several apps are resumed. Only the package name of the focused window is read.
 * Enabled by the module (module/service.sh).
 */
class FocusService : AccessibilityService() {
    override fun onServiceConnected() {
        running = true
        update()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        update()
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        running = false
        super.onDestroy()
    }

    private fun update() {
        val window = windows.firstOrNull {
            it.isFocused && it.type == AccessibilityWindowInfo.TYPE_APPLICATION
        } ?: return
        val pkg = window.root?.packageName?.toString() ?: return
        if (pkg == focused) return
        focused = pkg
        listener?.invoke(pkg)
    }

    companion object {
        /**
         * Turns this service back on if it's off. Android switches an app's accessibility services
         * off whenever the app is force-stopped, which includes every update. Needs
         * WRITE_SECURE_SETTINGS, granted by the module (module/service.sh); other enabled services
         * are kept.
         */
        fun ensureEnabled(context: Context): Boolean {
            if (context.checkSelfPermission(android.Manifest.permission.WRITE_SECURE_SETTINGS) !=
                PackageManager.PERMISSION_GRANTED
            ) return false
            val me = ComponentName(context, FocusService::class.java)
            val resolver = context.contentResolver
            val current = Settings.Secure.getString(resolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
            val enabled = current.split(':').mapNotNull { ComponentName.unflattenFromString(it) }
            if (me !in enabled) {
                val updated = (current.split(':').filter { it.isNotEmpty() } + me.flattenToString()).joinToString(":")
                Settings.Secure.putString(resolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, updated)
                Settings.Secure.putInt(resolver, Settings.Secure.ACCESSIBILITY_ENABLED, 1)
                EventLog.add(EventLog.Source.FOCUS, "enabled_accessibility_services",
                    "Active app detection was off (the app was stopped or updated); turned it back on")
            }
            return true
        }

        /** Package of the focused app window, or null if unknown. */
        @Volatile var focused: String? = null
            private set

        @Volatile var running = false
            private set

        /** Called on the main thread when the focused app changes. */
        @Volatile var listener: ((String) -> Unit)? = null
    }
}
