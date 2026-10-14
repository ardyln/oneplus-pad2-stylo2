package io.github.ardyln.stylo2

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context

/**
 * Tracks the app in the foreground from usage events. Needs the GET_USAGE_STATS app-op,
 * which the module grants (module/service.sh).
 */
class ForegroundApp(context: Context) {
    private val usm = context.getSystemService(UsageStatsManager::class.java)
    private var since = System.currentTimeMillis() - LOOKBACK_MS

    /** Package of the last resumed activity, or null if unknown. */
    var current: String? = null
        private set

    /** Reads new usage events; returns true if the foreground app changed. */
    fun update(): Boolean {
        val now = System.currentTimeMillis()
        val events = usm.queryEvents(since, now) ?: return false
        since = now
        val e = UsageEvents.Event()
        var latest = current
        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            if (e.eventType == UsageEvents.Event.ACTIVITY_RESUMED) latest = e.packageName
        }
        if (latest == current) return false
        current = latest
        return true
    }

    companion object {
        private const val LOOKBACK_MS = 10 * 60 * 1000L
    }
}
