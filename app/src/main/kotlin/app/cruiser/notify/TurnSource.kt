package app.cruiser.notify

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import app.cruiser.Session

/**
 * Android boundary only: watches nav-app notifications, captures their raw text
 * (debug capture lives in the Settings tab) and hands it to [NavTextParser]
 * (pure, unit-tested), which produces what Session posts.
 *
 * CoMaps is deliberately NOT here: its structured ContentProvider (ComapsSource)
 * is the primary source; text parsing is only the fallback we don't ship.
 */
class TurnSource : NotificationListenerService() {

    companion object {
        private val NAV_PACKAGES = setOf(
            "net.osmand", // OsmAnd / OsmAnd+
            "app.organicmaps",
            "com.google.android.apps.maps",
            "com.waze",
        )
    }

    override fun onListenerConnected() {
        if (app.cruiser.BuildConfig.DEBUG) {
            android.util.Log.i(
                "CruiserNav",
                "notification listener connected (${activeNotifications.size} active)",
            )
        }
        // A ride may already be underway when the service restarts — surface it now.
        activeNotifications.forEach(::tryParse)
    }

    override fun onListenerDisconnected() {
        Session.navActive.value = false
        Session.postTurn(null)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        tryParse(sbn)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        if (!isNavSource(sbn)) return
        // Another nav app may still be giving instructions — reparse the
        // remaining ones before declaring the route over.
        val next = activeNotifications.filter { it.key != sbn.key }.firstOrNull(::isNavSource)
        if (next != null) {
            tryParse(next)
        } else {
            Session.navActive.value = false
            Session.postTurn(null)
        }
    }

    private fun tryParse(sbn: StatusBarNotification) {
        if (!isNavSource(sbn)) return
        // Any ongoing nav notification means a route is live, even if its text
        // is still idle ("Guiding to…", "Starting navigation…").
        Session.navActive.value = true
        val e = sbn.notification.extras
        val title = e.getCharSequence("android.title")?.toString()?.trim().orEmpty()
        val bigText = e.getCharSequence("android.bigText")?.toString()?.trim().orEmpty()
        val text = e.getCharSequence("android.text")?.toString()?.trim().orEmpty()
        val subText = e.getCharSequence("android.subText")?.toString()?.trim().orEmpty()
        Session.captureNotif("${sbn.packageName}: title='$title' bigText='$bigText' text='$text' subText='$subText'")

        // Google Maps: instruction in title/bigText, bottom line in subText —
        // joining everything (deduplicated) is the only way to see all of it.
        val parts = listOf(bigText, text, subText).filter { it.isNotBlank() }.distinct()
        val bodyParts =
            if (title.isBlank() || parts.any { it.contains(title) }) parts else listOf(title) + parts
        val body = bodyParts.joinToString("\n").takeIf { it.isNotBlank() } ?: return

        when (val p = NavTextParser.parse(body)) {
            is NavTextParser.Parsed.Turn -> {
                Session.postRadar(null) // a fresh turn means the camera alert is stale
                if (p.update.ended) Session.navActive.value = false // arrived: route over
                Session.postTurn(p.update)
            }
            is NavTextParser.Parsed.Camera -> Session.postRadar(p.meters)
            NavTextParser.Parsed.Idle -> {}
        }
    }

    private fun isNavSource(sbn: StatusBarNotification): Boolean =
        sbn.isOngoing && NAV_PACKAGES.any(sbn.packageName::startsWith)
}
