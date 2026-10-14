package io.github.ardyln.stylo2

import android.accessibilityservice.AccessibilityService
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
        /** Package of the focused app window, or null if unknown. */
        @Volatile var focused: String? = null
            private set

        @Volatile var running = false
            private set

        /** Called on the main thread when the focused app changes. */
        @Volatile var listener: ((String) -> Unit)? = null
    }
}
