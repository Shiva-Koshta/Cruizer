# Audit ledger

Independent third-party pass over the whole app (parsers, protocol, BLE,
services, state, UI), followed by a fresh re-read of every touched file.
Findings are grouped by area; each entry says what changed or why it was
deliberately left. Commits: `3e36912`, `60150d5`, `fdc34bd`, plus the
current pass.

## Fixed

### Parsing (`NavTextParser`, `MapsScreenTextParser`)
- Arrival ETA stays on one line: `"arrive at destination\n10:20"` keeps its
  destination maneuver instead of becoming a bare ETA.
- ETA clocks that have already passed roll to tomorrow and cap at 24 h
  (a 1200 km ride can legitimately be 14 h out).
- Bottom-line splice blanks only the matched range, so a maneuver after the
  summary still parses; `"52 km/h"` is a speed, not a turn distance.
- Route distance from the Maps bottom line can never become the distance to
  the turn.
- Summary region is found by shape (duration/clock anchor), not a fixed `y`,
  so landscape and split-screen layouts work.
- Nav gate accepts one control plus a maneuver card, rejects route previews.
- `MINUTES_ONLY` (`"24 min"`) only reads the last non-blank line, so a
  `"5 min"` note inside the instruction is not the route's time left.
- Case folding uses `Locale.ROOT` (a Turkish phone no longer turns `RIGHT`
  into `RıGHT` and loses the phrase).

### Route ownership (`NavRoute`)
- Last writer owns the route; only the owner may end it. Opening Maps for
  traffic no longer tears down OsmAnd's live ride.
- Arrival (`beginRoute(ended = true)`) ends the route and resets the ride.
- Notification removal only clears the same package's route.
- CoMaps: an empty provider row ends the route; an unmappable direction enum
  keeps the last turn. Provider ownership is tracked per package, the changed
  package is queried (CoMaps + Organic Maps both installed), overlapping
  queries are generation-gated, and a later install/grant is picked up.

### Ride log (`RideLog`)
- Extracted from `Session`; clock starts with the route (works without the
  Pod or GPS), distance/speed only from real fixes, `hasFix` resets with the
  ride, implausible jumps are dropped.

### Service / BLE (`PodService`, `PodLink`)
- `onStartCommand`/`onDestroy` guard the `lateinit` link, so a failed
  foreground start or a queued command cannot crash the service.
- `FORGET`/`STOP` are handled before any connect: forgetting a saved bike
  that is not the remembered Pod no longer resurrects it.
- `podForget` when disconnected just deletes the saved bike; it no longer
  clobbers the real link state or starts the service.
- Coarse-only location permission no longer throws; unreadable providers are
  skipped.
- Adapter-state receiver unregister is guarded.
- Earlier passes: scan callback cleared on failure, API 33 write overload,
  frame queue with retry under one lock, GATT notify/descriptor guards,
  remembered MAC only seeds an unchosen bike, FGS location type re-declared.

### State / prefs (`Session`)
- Stored-state load no longer overwrites edits made while loading.
- Removing a saved pod clears the pairing prefs; saved bike values are
  clamped (`cc 50..2000`, `kmpl 1.0..100.0`) at the keyboard trust boundary.
- Bottom-mode pref key versioned so old ordinals cannot alias.

### UI
- `MainActivity` split: `PodScreen.kt` (Home tab, Maps banner, PIN dialog) and
  `PodIntents.kt` (service/permission helpers); the activity is now the shell.
- Unused `shape` parameters removed from `liquidGlass`/`CruiserGlassCard`.
- Settings label is **"Glass effects"**.
- Disconnect-timeout field commits on focus loss/Done, clamped 0..240.
- Maps banner dismiss is a 48 dp labelled button (accessibility).

### Solar (`SunPhase`)
- Day angle corrected (`dayOfYear - 1`), equation of time added, polar cases
  covered by tests.

## Deliberately not changed

- **Google Maps largeIcon template (#30)** — two `MERGE_RIGHT` templates
  differ by 9 bits and are not mirror images; they are likely two real icon
  variants. Fixing the match needs a real captured icon from a Maps
  notification, which we do not have. `shortcut:` the matcher's fixtures are
  self-referential until such a capture exists.
- **Documented icon constants (#37)** — `FERRY_TRAIN`, `MOBILE_DATA` and
  `BLANK` stay because `docs/PROTOCOL.md` documents them; deleting them would
  hide protocol knowledge. They are not sent by the parser today.
- **`Session` full split (#35)** — route and ride state moved out, but prefs
  and settings stay in `Session`; splitting them would churn every settings
  call site for no user-visible gain.
- **Hardware behavior** — BLE/frame paths are hardware-verified; no test can
  cover them. Device smoke tests are the only check.

## Verification

- `./gradlew app:assembleRelease app:testDebugUnitTest --no-daemon` passes.
- Release builds are R8-minified and resource-shrunk (`isMinifyEnabled = true`,
  ~3.0 MB APK, one dex). AGP 9.5's compose-mapping task resolves
  `compose-group-mapping` against its unpublished embedded Kotlin, so the
  configuration is forced to the published 2.3.20.
- Device smoke test on the rider's phone: install, notification-listener
  grant, foreground-service start, connect/disconnect, nav frame stream
  (**R8-minified APK needs one re-check on the phone**).
- Known gap: no real Maps icon fixture and no automated BLE test.
