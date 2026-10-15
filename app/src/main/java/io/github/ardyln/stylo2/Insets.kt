package io.github.ardyln.stylo2

import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding

/**
 * Keeps a scrolling view's bottom padding clear of the navigation bar or a docked taskbar, plus
 * [extra]. Reads the window's own insets because CoordinatorLayout doesn't pass them to its
 * scrolling child. Content still scrolls under the bars ([ViewGroup.setClipToPadding] false).
 * [onBottomInset] gets the bar height whenever it changes.
 */
fun View.padForBottomBars(extra: Int = 0, onBottomInset: ((Int) -> Unit)? = null) {
    val base = paddingBottom
    var last = -1
    if (this is ViewGroup) clipToPadding = false
    viewTreeObserver.addOnGlobalLayoutListener {
        val bottom = ViewCompat.getRootWindowInsets(this)
            ?.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            ?.bottom ?: 0
        if (bottom != last) {
            last = bottom
            updatePadding(bottom = base + extra + bottom)
            onBottomInset?.invoke(bottom)
        }
    }
}
