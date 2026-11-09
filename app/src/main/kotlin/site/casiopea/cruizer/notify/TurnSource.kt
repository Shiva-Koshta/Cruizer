package site.casiopea.cruizer.notify

import android.content.ComponentName
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import site.casiopea.cruizer.Session

/**
 * Android boundary only: watches nav-app notifications, captures their raw text
 * (debug capture lives in the Settings tab) and hands it to [NavTextParser]
 * (pure, unit-tested), which produces what Session posts.
 */
class TurnSource : NotificationListenerService() {

    companion object {
        private val NAV_PACKAGES = setOf(
            "net.osmand", // OsmAnd / OsmAnd+
            "app.organicmaps",
            "app.comaps", // CoMaps (Organic Maps fork — provider primary, text fallback)
            "com.google.android.apps.maps",
            "com.waze",
        )

        // Debug-only: dump the full notification payload for these packages.
        private val PROBE_PACKAGES = setOf(
            "net.osmand", "app.organicmaps", "app.comaps", "com.google.android.apps.maps",
        )

        fun rebind(context: Context) {
            runCatching {
                requestRebind(ComponentName(context, TurnSource::class.java))
            }
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private var pendingClear: Runnable? = null

    override fun onListenerConnected() {
        if (site.casiopea.cruizer.BuildConfig.DEBUG) {
            android.util.Log.i(
                "CruizerNav",
                "notification listener connected (${activeNotifications.size} active)",
            )
        }
        // A ride may already be underway when the service restarts — surface it now.
        activeNotifications.forEach(::tryParse)
    }

    override fun onListenerDisconnected() {
        pendingClear?.let { handler.removeCallbacks(it); pendingClear = null }
        Session.navActive.value = false
        Session.postTurn(null)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        pendingClear?.let { handler.removeCallbacks(it); pendingClear = null }
        tryParse(sbn)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        if (!isNavSource(sbn)) return
        // Another nav app may still be giving instructions — reparse the
        // remaining ones before declaring the route over.
        val next = activeNotifications.filter { it.key != sbn.key }.firstOrNull(::isNavSource)
        if (next != null) {
            pendingClear?.let { handler.removeCallbacks(it); pendingClear = null }
            tryParse(next)
        } else {
            // Debounce teardown: Google Maps rapidly removes and reposts notifications
            // during turn updates — avoid flashing back to the clock face.
            pendingClear?.let { handler.removeCallbacks(it) }
            val clear = Runnable {
                pendingClear = null
                val stillActive = activeNotifications.any(::isNavSource)
                if (!stillActive) {
                    Session.navActive.value = false
                    Session.postTurn(null)
                }
            }
            pendingClear = clear
            handler.postDelayed(clear, 2500)
        }
    }

    private fun tryParse(sbn: StatusBarNotification) {
        if (!isNavSource(sbn)) return
        if (site.casiopea.cruizer.BuildConfig.DEBUG && PROBE_PACKAGES.any(sbn.packageName::startsWith)) {
            dumpExtras(sbn)
        }
        // CoMaps publishes structured turn data via ContentProvider (ComapsSource);
        // ignore its notification so coarse text parsing cannot clobber it.
        if (sbn.packageName.startsWith("app.comaps") &&
            ComapsSource.installed(this).any { ComapsSource.granted(this, it) }
        ) {
            Session.navActive.value = true
            return
        }
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

        val iconKind = if (sbn.packageName == "com.google.android.apps.maps") {
            GoogleMapsIconMatcher.matchIcon(sbn.notification.getLargeIcon(), this)
        } else null

        when (val p = NavTextParser.parse(body)) {
            is NavTextParser.Parsed.Turn -> {
                if (p.update.ended) Session.navActive.value = false // arrived: route over
                val update = if (iconKind != null && iconKind != site.casiopea.cruizer.proto.TurnKind.UNKNOWN) {
                    p.update.copy(kind = iconKind)
                } else {
                    p.update
                }
                Session.postTurn(update)
            }
            is NavTextParser.Parsed.Camera -> {}
            NavTextParser.Parsed.Idle -> {}
        }
    }

    private fun dumpExtras(sbn: StatusBarNotification) {
        val extras = sbn.notification.extras
        val sb = StringBuilder("PROBE ${sbn.packageName} extras: ")
        for (k in extras.keySet().sorted()) {
            val v = extras.get(k)
            when (v) {
                is android.os.Bundle -> {
                    sb.append("$k=Bundle{")
                    for (kk in v.keySet().sorted()) sb.append("$kk=${v.get(kk)}; ")
                    sb.append("} ")
                }
                is java.util.ArrayList<*> -> {
                    sb.append("$k=List[${v.size}]{")
                    v.forEach { item ->
                        if (item is android.os.Bundle) {
                            for (kk in item.keySet().sorted()) sb.append("$kk=${item.get(kk)}; ")
                        } else sb.append("$item; ")
                    }
                    sb.append("} ")
                }
                else -> sb.append("$k=$v; ")
            }
        }
        Session.captureNotif(sb.toString())
        if (sbn.packageName == "com.google.android.apps.maps") {
            val matched = runCatching {
                GoogleMapsIconMatcher.matchIcon(sbn.notification.getLargeIcon(), this)
            }.getOrNull() ?: site.casiopea.cruizer.proto.TurnKind.UNKNOWN
            Session.captureNotif("PROBE maps icon=$matched")
        }
    }

    private fun isNavSource(sbn: StatusBarNotification): Boolean {
        val ongoing = sbn.isOngoing ||
            (sbn.notification.flags and android.app.Notification.FLAG_ONGOING_EVENT) != 0
        return ongoing && NAV_PACKAGES.any(sbn.packageName::startsWith)
    }
}
