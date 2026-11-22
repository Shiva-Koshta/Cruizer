package site.casiopea.cruizer.ui

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import site.casiopea.cruizer.Perms
import site.casiopea.cruizer.Perms.granted
import site.casiopea.cruizer.PodService
import site.casiopea.cruizer.Session
import site.casiopea.cruizer.ble.LinkState

// Location stays out on Android 12+: it is optional there, so gating Connect on it
// made the button silently dead for riders who denied it (Android 11 needs it to scan).
// Notifications stay in: without them the foreground-service notification is
// invisible on Android 13+.
internal fun Context.hasRequiredPermissions(): Boolean =
    granted(Perms.required()) && listenerGranted(this)

internal fun Context.podStart(mac: String? = null) {
    if (!hasRequiredPermissions()) {
        MainActivity.gate.value = MainActivity.gate.value + 1
        return
    }
    val intent = Intent(this, PodService::class.java).setAction(PodService.ACTION_START)
    if (mac != null) intent.putExtra(PodService.EXTRA_MAC, mac)
    try {
        ContextCompat.startForegroundService(this, intent)
        // Instant press feedback; the service overwrites this with real states.
        Session.linkState.value = LinkState.CONNECTING
    } catch (_: Exception) {
        MainActivity.gate.value = MainActivity.gate.value + 1
    }
}

internal fun Context.podScanNew() {
    if (!hasRequiredPermissions()) {
        MainActivity.gate.value = MainActivity.gate.value + 1
        return
    }
    val intent = Intent(this, PodService::class.java).setAction(PodService.ACTION_START)
    intent.putExtra(PodService.EXTRA_SCAN_NEW, true)
    try {
        ContextCompat.startForegroundService(this, intent)
        Session.linkState.value = LinkState.SCANNING
    } catch (_: Exception) {
        MainActivity.gate.value = MainActivity.gate.value + 1
    }
}

internal fun Context.podStop() {
    Session.linkState.value = LinkState.DISCONNECTED
    startService(Intent(this, PodService::class.java).setAction(PodService.ACTION_STOP))
}

internal fun Context.podForget(mac: String? = null) {
    // The service may not be running (nothing to disconnect) and starting it just
    // to delete a saved bike made it scan for that bike and re-add it.
    if (Session.linkState.value == LinkState.DISCONNECTED) {
        mac?.let(Session::removeSavedPod)
        return
    }
    val intent = Intent(this, PodService::class.java).setAction(PodService.ACTION_FORGET)
    if (mac != null) intent.putExtra(PodService.EXTRA_MAC, mac)
    startService(intent)
}
