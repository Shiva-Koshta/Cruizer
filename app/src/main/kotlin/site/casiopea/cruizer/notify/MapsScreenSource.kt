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
import site.casiopea.cruizer.proto.TurnKind
import site.casiopea.cruizer.proto.TurnUpdate

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
        val screenH = resources.displayMetrics.heightPixels.toFloat().coerceAtLeast(1f)
        val lines = ArrayList<MapsScreenTextParser.ScreenLine>()
        walk(root, screenH, lines)
        if (lines.isEmpty()) return
        // Debug-only capture, same convention as notifications: logcat + Settings card.
        // y is included so the top/bottom split can be calibrated from real rides.
        Session.captureNotif(
            "maps-a11y: " + lines.joinToString(" | ") { "%.2f:${it.text}".format(it.y) },
        )
        val parsed = MapsScreenTextParser.extract(lines) ?: return
        Session.lastNavSource.value = MAPS_PACKAGE
        when (parsed) {
            is NavTextParser.Parsed.Turn -> {
                val update = merge(parsed.update)
                if (update.ended) Session.navActive.value = false // arrived
                else if (!Session.navActive.value) Session.navActive.value = true
                Session.postTurn(update)
            }
            // No actionable text yet (route just started): the screen IS a live
            // route, so nav liveness comes from the nav-mode gate alone.
            else -> Session.navActive.value = true
        }
    }

    /**
     * The bottom bar ticks ~1 Hz but the instruction card only changes at a turn.
     * When the card carried no maneuver, keep the previous kind/arrow and refresh
     * just the route summary, so the Pod's bottom line keeps updating.
     */
    private fun merge(next: TurnUpdate): TurnUpdate {
        val prev = Session.currentTurn.value ?: return next
        if (next.kind != TurnKind.UNKNOWN) return next
        return prev.copy(
            remainingSeconds = next.remainingSeconds ?: prev.remainingSeconds,
            routeRemainingM = next.routeRemainingM ?: prev.routeRemainingM,
            arrivalSecondsFromNow = next.arrivalSecondsFromNow ?: prev.arrivalSecondsFromNow,
        )
    }

    private fun walk(
        node: AccessibilityNodeInfo,
        screenH: Float,
        out: MutableList<MapsScreenTextParser.ScreenLine>,
    ) {
        val rect = android.graphics.Rect()
        node.getBoundsInScreen(rect)
        val y = (rect.top / screenH).coerceIn(0f, 1f)
        node.text?.toString()?.trim()?.takeIf { it.isNotEmpty() }
            ?.let { out.add(MapsScreenTextParser.ScreenLine(it, y)) }
        node.contentDescription?.toString()?.trim()?.takeIf { it.isNotEmpty() }
            ?.let { out.add(MapsScreenTextParser.ScreenLine(it, y)) }
        for (i in 0 until node.childCount) node.getChild(i)?.let { walk(it, screenH, out) }
    }
}
