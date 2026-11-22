# 12 · fix (security) · Exact package match for nav notifications

| | |
|---|---|
| Priority | P2 |
| Depends on | 00 |
| Bench test | — |

## Problem

`notify/TurnSource.kt:189`:

```kotlin
return ongoing && NAV_PACKAGES.any(sbn.packageName::startsWith)
```

Prefix matching means any installed app whose package *starts with* a nav app's name
(`com.wazefake`, `net.osmand.evil`, `com.google.android.apps.mapsx`) can post an ongoing notification
and **drive the Pod with fake turn arrows** while riding. Same pattern at line 101 (`PROBE_PACKAGES`, debug only)
and line 108 (`startsWith("app.comaps")`).

## Fix

Exact match against an explicit set of every real package name.

### `notify/TurnSource.kt`

```kotlin
private val NAV_PACKAGES = setOf(
    "net.osmand", "net.osmand.plus", "net.osmand.dev",        // OsmAnd free / + / nightly
    "app.organicmaps", "app.organicmaps.debug",
    "app.comaps", "app.comaps.fdroid", "app.comaps.google",   // CoMaps flavours (same list as ComapsSource.PACKAGES)
    "com.google.android.apps.maps",
    "com.waze",
)
private val COMAPS_PACKAGES = setOf("app.comaps", "app.comaps.fdroid", "app.comaps.google", "app.organicmaps")
```

- `isNavSource`: `ongoing && sbn.packageName in NAV_PACKAGES`
- line 101: `sbn.packageName in PROBE_PACKAGES` (make `PROBE_PACKAGES` a subset set too)
- line 108: `if (sbn.packageName in COMAPS_PACKAGES)`

Better: expose `ComapsSource.PACKAGES` as `internal val` and reuse it instead of a second list.

Verify the real package names before merging:
```sh
adb shell pm list packages | grep -Ei 'osmand|organicmaps|comaps|apps.maps|waze'
```
(Package names are unique per device, so an exact match cannot be spoofed by another app.)

### Also check

- `notify/MapsScreenSource.kt:83,97` already use `==` ✓.
- `GoogleMapsIconMatcher` call site uses `==` ✓.

### Docs

`docs/AUDIT.md` → "Fixed": notification source spoofing via package prefix.

## Tests

`isNavSource` takes a `StatusBarNotification` (Android). Extract the pure part so it is testable:

```kotlin
companion object {
    internal fun isNavPackage(pkg: String) = pkg in NAV_PACKAGES
}
```

`TurnSourceTest.kt` (new): `isNavPackage("com.waze") == true`, `"com.wazefake" == false`,
`"net.osmand.plus" == true`, `"net.osmand.evil" == false`, `"com.google.android.apps.mapsx" == false`.

## Acceptance criteria

- [ ] Google Maps, OsmAnd(+), Waze turns still reach the Pod.
- [ ] Unit test passes.
