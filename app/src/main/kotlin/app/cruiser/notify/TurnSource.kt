package app.cruiser.notify

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import app.cruiser.Session

/**
 * Android boundary only: watches nav-app notifications and hands their text to
 * [NavTextParser] (pure, unit-tested) which produces what Session posts.
 */
class TurnSource : NotificationListenerService() {

    companion object {
        private val NAV_PACKAGES = setOf(
            "net.osmand", // OsmAnd / OsmAnd+
            "app.organicmaps",
            "app.comaps", // CoMaps (Organic Maps fork)
            "com.google.android.apps.maps",
            "com.waze",
        )
    }

    override fun onListenerConnected() {
        // A ride may already be underway when the service restarts — surface it now.
        activeNotifications.forEach(::tryParse)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        tryParse(sbn)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        if (isNavSource(sbn)) Session.postTurn(null)
    }

    private fun tryParse(sbn: StatusBarNotification) {
        if (!isNavSource(sbn)) return
        val body = text(sbn) ?: return
        when (val p = NavTextParser.parse(body)) {
            is NavTextParser.Parsed.Turn -> {
                Session.postRadar(null) // a fresh turn means the camera alert is stale
                Session.postTurn(p.update)
            }
            is NavTextParser.Parsed.Camera -> Session.postRadar(p.meters)
            NavTextParser.Parsed.Idle -> {}
        }
    }

    private fun isNavSource(sbn: StatusBarNotification): Boolean =
        sbn.isOngoing && NAV_PACKAGES.any(sbn.packageName::startsWith)

    private fun text(sbn: StatusBarNotification): String? {
        val extras = sbn.notification.extras
        val title = extras.getCharSequence("android.title")?.toString()?.trim().orEmpty()
        // Google Maps puts the live instruction in bigText; OsmAnd uses text/subText.
        val body = listOfNotNull(
            extras.getCharSequence("android.bigText")?.toString(),
            extras.getCharSequence("android.text")?.toString(),
            extras.getCharSequence("android.subText")?.toString(),
        ).firstOrNull { it.isNotBlank() }?.trim() ?: return null
        if (title.isBlank()) return body
        if (body.startsWith(title)) return body
        return "$title\n$body"
    }
}
