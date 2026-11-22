# 16 · feat · Compass screen when not navigating

| | |
|---|---|
| Priority | P2 (most visible new feature) |
| Depends on | 00 |
| Bench test | B9 (confirm direction codes, especially NE/NW naming) |

## Why

When no route is active the Pod shows only its clock. The Pod has a built-in **compass screen**
(8 directions) that wazetripper and OpenTripper use; Cruizer never sends it.

## Protocol

`10 11 41 00 00 00 [night] FF 00 00 00 FF FF FF [dir] 00 00 00` — wazetripper §4 (hardware-confirmed).

| Sector (bearing) | N 337.5–22.5 | NE | E | SE | S | SW | W | NW |
|---|---|---|---|---|---|---|---|---|
| `[14]` | `10` | `50` | `20` | `70` | `40` | `80` | `30` | `60` |
| CRC (day) | `94 76` | `FA EA` | `B8 9F` | `CD A4` | `E1 4D` | `52 E9` | `A3 38` | `D6 03` |

Night N: `… 01 FF … 10 …` → CRC `97 03`.
Watchdog: the compass **must be re-sent every ≤2 s** or the Pod drops the link (wazetripper §4).
Quicker-pod's notes disagree on NE/NW naming → B9 settles it.

## Design

```
Settings → "When not navigating, Pod shows"  [clock] [compass]      (IdleScreen enum)
compass + moving (GPS speed ≥ 2 m/s)  → compass frame every tick, sector from GPS bearing
compass + stopped / no fix            → hold last sector; no fix ever → clock (PING only)
```

GPS bearing, not magnetometer: steadier on a motorcycle (wazetripper).

## Implementation

### 1. `Constants.kt`

```kotlin
const val COMPASS_MIN_SPEED_MPS = 2f        // below this GPS bearing is noise
const val COMPASS_HYSTERESIS_DEG = 10f      // must pass this far into a new sector to switch
```

### 2. `proto/PodProtocol.kt` (pure)

```kotlin
/** Direction codes for sectors 0..7 clockwise from N. */
private val COMPASS_CODES = intArrayOf(0x10, 0x50, 0x20, 0x70, 0x40, 0x80, 0x30, 0x60)

fun compassFrame(sector: Int, night: Boolean): ByteArray = withCrc(build18 {
    it[0] = 0x10; it[1] = 0x11; it[2] = Icons.COMPASS.code.toByte()
    it[6] = (if (night) 1 else 0).toByte()
    it[7] = 0xFF.toByte()
    it[11] = 0xFF.toByte(); it[12] = 0xFF.toByte(); it[13] = 0xFF.toByte()
    it[14] = COMPASS_CODES[sector].toByte()
})

/** 45° sectors offset 22.5°, with hysteresis around the current sector. */
fun compassSector(bearing: Float, current: Int?): Int {
    val b = ((bearing % 360) + 360) % 360
    val raw = (((b + 22.5f) / 45f).toInt()) % 8
    if (current == null || raw == current) return raw
    val centre = current * 45f
    val off = Math.abs(((b - centre + 540) % 360) - 180)   // angular distance to current centre
    return if (off > 22.5f + Constants.COMPASS_HYSTERESIS_DEG) raw else current
}
```

`proto/Icons.kt`: add `COMPASS(0x41)` (screen id, not a maneuver; comment so).

### 3. `Session.kt`

- `enum class IdleScreen { CLOCK, COMPASS }` (issue 20 appends `SPEEDOMETER`).
- `Settings.idleScreen: IdleScreen = IdleScreen.CLOCK`, `KEY_IDLE_SCREEN = "idle_screen"`, load/save like `nightMode`.
- Expose live motion for the service: in `updateLiveLocation`, keep
  `@Volatile var lastBearing: Float? = null` and `@Volatile var lastSpeedMps: Float = 0f`
  (`if (location.hasBearing()) lastBearing = location.bearing`; `lastSpeedMps = location.speed`).

### 4. `PodService.kt`

- **GPS while idle:** line ~108 becomes
  ```kotlin
  val idleGps = Session.settings.value.idleScreen != Session.IdleScreen.CLOCK &&
      link.state.value == LinkState.CONNECTED
  if (NavRoute.navActive.value || idleGps) startLiveLocation() else stopLiveLocation()
  ```
  (GPS only while the Pod is connected — keeps the battery rule from the existing comment.)
- `private var compassSector: Int? = null` (reset on DISCONNECTED).
- `tick()`, in the `turn == null && !navActive` branch, before the PING fallback:
  ```kotlin
  if (s.idleScreen == Session.IdleScreen.COMPASS) {
      Session.lastBearing?.takeIf { Session.lastSpeedMps >= Constants.COMPASS_MIN_SPEED_MPS }
          ?.let { compassSector = PodProtocol.compassSector(it, compassSector) }
      compassSector?.let {
          link.send(PodProtocol.compassFrame(it, night))
          Session.podScreen.value = PodProtocol.PodScreen(Icons.COMPASS.code, COMPASS_LABELS[it], null, night)
          lastFrameSentAt = now
          return
      }
  }
  ```
  Send **every tick** (not only after 2 s) — compass watchdog.
- `resendScreen()` (night change): add the compass case.

### 5. UI

- `ui/SettingsScreen.kt`: chip row "When not navigating, Pod shows" → `clock` / `compass`, helper text
  "Compass uses GPS while the Pod is connected."
- `ui/DashboardCards.kt` `ManeuverGlyph`: `PodIcons.COMPASS.code` → draw the label text (N, NE…) or
  `Icons.Filled.Explore`; `COMPASS_LABELS = listOf("N","NE","E","SE","S","SW","W","NW")`.

### 6. Docs

`docs/PROTOCOL.md` §4: compass frame, codes, watchdog note, "Confirmed (B9)".

## Tests (`PodProtocolTest`)

- `compassFrame(0, false)` == exact bytes `10 11 41 00 00 00 00 FF 00 00 00 FF FF FF 10 00 00 00 94 76`.
- `compassFrame(0, true)` ends `97 03`; `compassFrame(1, false)` ends `FA EA`.
- `compassSector(0f, null) == 0`, `(44f, null) == 1`, `(359f, null) == 0`, `(-10f, null) == 0`.
- Hysteresis: `compassSector(25f, 0) == 0` (just over the edge, stays N), `compassSector(35f, 0) == 1`.

## Acceptance criteria

- [ ] B9 codes confirmed; table fixed if NE/NW swapped.
- [ ] Riding without a route: arrow follows heading, no flicker at sector edges, no link drops for 10 min.
- [ ] Setting "clock" → back to today's behaviour; GPS off when idle.
- [ ] Starting a Maps route switches from compass to turns; ending it returns to compass.
