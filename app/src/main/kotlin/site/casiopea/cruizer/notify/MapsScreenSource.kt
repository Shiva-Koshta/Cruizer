package site.casiopea.cruizer.notify

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import site.casiopea.cruizer.Session

/**
 * Android boundary only: when the user enables Cruizer in Settings →
 * Accessibility, this service reads Google Maps' live navigation window into a
 * flat line list and hands it to [MapsScreenTextParser] (pure, unit-tested),
 * which produces what Session posts. "Latest update wins" — the ~1 Hz screen
 * feed dominates while Maps is on screen; notifications fill in when it's off.
 */
class MapsScreenSource : AccessibilityService() {

    companion object {
        private const val MAPS_PACKAGE = "com.google.android.apps.maps"
        private const val THROTTLE_MS = 250L

        fun start(context: Context) {
            context.startActivity(
                Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }

        /** Cruizer listed in the system's enabled accessibility services. */
        fun enabled(context: Context): Boolean {
            // applicationContext: the service context is created per-binding and
            // its package stays identical, but shops may wrap activities.
            val want = ComponentName(context.applicationContext, MapsScreenSource::class.java)
                .flattenToString()
            return Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            )?.split(':')?.any { it.equals(want, ignoreCase = true) } == true
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private var snapshotScheduled = false

    override fun onServiceConnected() {
        Session.mapsA11yConnected.value = true
        if (site.casiopea.cruizer.BuildConfig.DEBUG) {
            android.util.Log.i("CruizerNav", "maps a11y source connected")
        }
    }

    override fun onUnbind(intent: Intent?): Boolean {
        // Toggle flipped off (or ROM reset it): silently fall back to notifications.
        Session.mapsA11yConnected.value = false
        return super.onUnbind(intent)
    }

    override fun onInterrupt() {}

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        if (event.packageName != MAPS_PACKAGE) return
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            event.eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
        ) {
            return
        }
        // Content-change events fire constantly while navigating; coalesce to one
        // snapshot per throttle window.
        if (snapshotScheduled) return
        snapshotScheduled = true
        handler.postDelayed({ snapshotScheduled = false; snapshot() }, THROTTLE_MS)
    }

    private fun snapshot() {
        val root = rootInActiveWindow?.takeIf { it.packageName == MAPS_PACKAGE } ?: return
        val lines = ArrayList<String>()
        walk(root, lines)
        if (lines.isEmpty()) return
        // Debug-only capture, same convention as notifications: logcat + Settings card.
        Session.captureNotif("maps-a11y: ${lines.joinToString(" | ")}")
        val parsed = MapsScreenTextParser.extract(lines) ?: return
        Session.lastNavSource.value = MAPS_PACKAGE
        when (parsed) {
            is NavTextParser.Parsed.Turn -> {
                if (parsed.update.ended) Session.navActive.value = false // arrived
                else if (!Session.navActive.value) Session.navActive.value = true
                Session.postTurn(parsed.update)
            }
            // No actionable text yet (route just started): the screen IS a live
            // route, so nav liveness comes from the nav-mode gate alone.
            else -> Session.navActive.value = true
        }
    }

    private fun walk(node: AccessibilityNodeInfo, out: MutableList<String>) {
        node.text?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.let(out::add)
        node.contentDescription?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.let(out::add)
        for (i in 0 until node.childCount) node.getChild(i)?.let { walk(it, out) }
    }
}
