# 18 · feat · "No mobile data" icon on the Pod

| | |
|---|---|
| Priority | P3 |
| Depends on | 17 (alert scheduler, setting switch, tick hook) |
| Bench test | B10-b (confirm glyph) |

## Why

Google Maps cannot reroute or refresh traffic without data (common on Indian highways/ghats).
The Pod has a built-in glyph `0x42` (official icon enum "MOBILE_DATA"; Super calls the same frame its
"locked" screen). Warn the rider while navigating.

## Behaviour

Only **while navigating** (`NavRoute.navActive`), when the phone has no validated internet for ≥ 30 s:
flash `0x42` for 2 s every 60 s (same scheduler as 17). Battery alert wins if both are due.

## Implementation

### 1. Permission (normal, auto-granted, **not** internet access)

`app/src/main/AndroidManifest.xml`:
```xml
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
```
The app still has no `INTERNET` permission — note this in the PR so the privacy review stays true.

### 2. `Constants.kt`

```kotlin
const val NO_DATA_GRACE_MS = 30_000L
```

### 3. `PodService.kt`

```kotlin
private var hasInternet = true
private var noDataSince: Long? = null

private val netCallback = object : ConnectivityManager.NetworkCallback() {
    override fun onCapabilitiesChanged(n: Network, c: NetworkCapabilities) {
        hasInternet = c.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
    override fun onLost(n: Network) { hasInternet = false }
}
```

- `onCreate`: `getSystemService(ConnectivityManager::class.java).registerDefaultNetworkCallback(netCallback)` (wrap in `runCatching`).
- `onDestroy`: `unregisterNetworkCallback(netCallback)` (`runCatching`).
- In the alert hook added by issue 17 (after the battery check):

```kotlin
val offline = NavRoute.navActive.value && !hasInternet
noDataSince = if (offline) noDataSince ?: now else null
// Alert "starts" once the grace period has passed; before that it is not active.
val alertSince = noDataSince?.plus(Constants.NO_DATA_GRACE_MS)?.takeIf { now >= it }
if (AlertSchedule.showing(alertSince, now, Constants.ALERT_FLASH_MS, Constants.ALERT_REPEAT_MS)) {
    link.send(PodProtocol.statusFrame(Icons.MOBILE_DATA, night))
    Session.podScreen.value = PodProtocol.PodScreen(Icons.MOBILE_DATA.code, "", null, night)
    lastFrameSentAt = now
    return
}
```

Clean up: extract `showStatus(icon, night, now)` (send + mirror + timestamp) the first time this pattern appears
three times (issues 14, 17, 18).

### 4. Mirror

`ui/DashboardCards.kt` `ManeuverGlyph`: map `PodIcons.MOBILE_DATA.code` → `Icons.Filled.SignalCellularOff`
(material-icons-core has `Warning`/`Info`; if `SignalCellularOff` is not in core, use `Icons.Filled.Warning`).

### 5. Docs

`docs/PROTOCOL.md`: `0x42` "Confirmed (B10-b)".

## Tests

Logic reuses `AlertSchedule` (tested in 17). Add one case to `AlertScheduleTest`:
`showing(30_000, 30_000, 2000, 60000) == true` (first flash right after the grace period).

## Acceptance criteria

- [ ] Navigating, airplane mode on (keep Bluetooth on): after ~30 s the Pod flashes the no-data glyph.
- [ ] Data back → stops.
- [ ] Not navigating → never shown.
- [ ] Merged manifest still has no `android.permission.INTERNET`.
