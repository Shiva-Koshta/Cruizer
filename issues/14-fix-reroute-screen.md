# 14 · fix · Show "recalculating" icon on reroute (and stop the watchdog drop)

| | |
|---|---|
| Priority | P2 (real bug: link drops during every reroute) |
| Depends on | 00 |
| Bench test | B10-c (confirm glyph) |

## Bug (confirmed by reading the code)

1. Maps posts "Rerouting…" / "Recalculating…" → `NavTextParser` maps it to `TurnKind.RE_ROUTE`
   (`NavTextParser.kt:77`) with **`distanceM = null`** (no distance in that text).
2. `PodService.renderNavFrame` (line ~429):
   ```kotlin
   val distance = turn.distanceM ?: lastDistanceM?.takeIf { maneuver == lastManeuver } ?: return
   ```
   `maneuver` = `0x1C` ≠ `lastManeuver` → **returns without sending anything**.
3. The tick keeps calling it every second, nothing goes out, and after ~5 s the Pod's watchdog closes the
   link (`status=19`). The app reconnects a few seconds later. Net effect: blank/clock Pod and a
   reconnect during every reroute.

## Fix

Treat `RE_ROUTE` as a **status screen**, not a nav frame — the same as wazetripper
(`10 11 1C …`, "loading / recalculating", capped at 8 s).

### 1. `Constants.kt`

```kotlin
/** Max time the recalculating icon may hold the Pod before falling back to the last turn (wazetripper). */
const val REROUTE_MAX_MS = 8_000L
```

### 2. `PodService.kt`

Add `private var rerouteSince = 0L`.

In `renderNavFrame`, before the maneuver selection:

```kotlin
if (turn.kind == TurnKind.RE_ROUTE) {
    if (rerouteSince == 0L) rerouteSince = now
    if (now - rerouteSince <= Constants.REROUTE_MAX_MS) {
        link.send(PodProtocol.statusFrame(Icons.RE_ROUTE, night))
        Session.podScreen.value = PodProtocol.PodScreen(Icons.RE_ROUTE.code, "", null, night)
        lastFrameSentAt = now
        return
    }
    // Longer than the cap: keep re-sending the last real turn so the Pod is not left blank.
    lastManeuver?.let { m -> lastDistanceM?.let { d -> sendNav(m, d, turn, night, now) } }
    return
}
rerouteSince = 0L
```

`sendNav(...)` = extract the existing "build bottom + navFrame + send + mirror + log" tail of
`renderNavFrame` into a private function so both paths share it (no duplicated frame code).
Do **not** update `lastManeuver`/`lastDistanceM` with `0x1C`, so the next real instruction is
compared against the last real turn.

### 3. `statusFrame` sanity

`PodProtocol.statusFrame(Icons.RE_ROUTE, night)` already produces
`10 11 1C 00 00 01 0N FF 00 00 41 FF FF FF 00 00 00 00` (CRC day `ED E0`). No change.

### 4. Docs

`docs/PROTOCOL.md` §4 "Idle screens": note "sent on reroute, max 8 s (wazetripper behaviour)".

## Tests

`renderNavFrame` is Android-bound. Make the timing decision pure and test it:

```kotlin
// in PodProtocol or a small object
fun showReroute(since: Long, now: Long): Boolean = now - since <= Constants.REROUTE_MAX_MS
```
Test: `showReroute(0, 8_000) == true`, `showReroute(0, 8_001) == false`.

Add a `NavTextParserTest` fixture: Maps body `"Rerouting…"` → `kind == RE_ROUTE`, `distanceM == null`
(locks in the input this fix depends on).

## Acceptance criteria

- [ ] Miss a turn on purpose: the Pod shows the recalculating glyph, then the new turn. No disconnect in the debug log.
- [ ] If Maps stays "Rerouting" > 8 s (no data), the Pod shows the last turn instead of dropping.
