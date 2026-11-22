# 20 · feat (experimental) · Speedometer idle screen

| | |
|---|---|
| Priority | P4 |
| Depends on | 16 (`IdleScreen` setting, idle GPS, idle branch in `tick()`) |
| Bench test | **B13** (is it readable?) |
| Status | **Blocked until B13 decides** |

## Why

The Meteor's speedo is analog; a big digital speed on the Pod is handy. There is no speed command, but
OpenTripper reuses the nav frame: straight icon + speed in the distance field (shown as "60 m") + trip
distance on the bottom line. A hack — only worth it if B13 looks acceptable.

## Decision rule (from B13)

| B13 | Do |
|---|---|
| "60" clearly readable, unit text tolerable | Build with the icon that looked best (B13-a `0x09` or B13-b `0xFF`) |
| Unreadable / confusing | Close |

## Plan

### 1. `Session.kt`

Append: `enum class IdleScreen { CLOCK, COMPASS, SPEEDOMETER }`. Settings chip `speed`.

### 2. `Constants.kt`

```kotlin
const val SPEEDO_ICON = 0x09          // or 0xFF per B13
const val MPS_TO_KMH = 3.6f
const val SPEEDO_MAX_KMH = 199
```

### 3. `proto/PodProtocol.kt` (pure)

```kotlin
/** Speed shown in the distance field (unit 1 = "m"), trip km on the bottom line. */
fun speedoFrame(kmh: Int, tripM: Int?, night: Boolean): ByteArray = withCrc(build18 {
    it[0] = 0x10; it[1] = 0x11; it[2] = Constants.SPEEDO_ICON.toByte()
    val v = kmh.coerceIn(0, Constants.SPEEDO_MAX_KMH)
    it[3] = (v shr 8).toByte(); it[4] = (v and 0xFF).toByte(); it[5] = 0x01
    it[6] = (if (night) 1 else 0).toByte()
    it[7] = 0xFF.toByte(); it[8] = 0xFF.toByte(); it[9] = 0xFF.toByte(); it[10] = 0x41
    val b = tripM?.let { m -> encodeBottom(Bottom.Total(m, false), 0) } ?: byteArrayOf(-1, -1, -1)
    it[11] = b[0]; it[12] = b[1]; it[13] = b[2]
})
```

Do **not** route this through `navFrame`/`encodeDistance`: those round to 10/50 m steps, which would
turn 63 km/h into "70".

### 4. `PodService.tick()` idle branch (added in 16)

```kotlin
Session.IdleScreen.SPEEDOMETER -> {
    val kmh = (Session.lastSpeedMps * Constants.MPS_TO_KMH).roundToInt()
    link.send(PodProtocol.speedoFrame(kmh, null, night))
    Session.podScreen.value = PodProtocol.PodScreen(Constants.SPEEDO_ICON, "$kmh km/h", null, night)
    lastFrameSentAt = now
    return
}
```

Bottom line stays blank: `RideLog.update()` returns early and resets when `navActive` is false
(`RideLog.kt:34`), so there is no idle trip distance. An idle odometer would be its own issue.
Speed comes from `Session.lastSpeedMps` (added in 16), not `RideLog.liveRide.currentSpeedKmh` (route-only).

### 5. Docs

`docs/PROTOCOL.md`: "Speedometer (app convention, not a Pod feature): nav frame with speed in `[3-4]`, B13".

## Tests (`PodProtocolTest`)

- `speedoFrame(63, null, false)`: `[2]=SPEEDO_ICON`, `[3..5] = 00 3F 01`, `[11..13] = FF FF FF`, valid CRC.
- `speedoFrame(250, …)` clamps to 199.

## Acceptance criteria

- [ ] B13 decision recorded.
- [ ] Riding without a route, Pod shows current speed within ±2 km/h of GPS, updated every second.
- [ ] Stopped → shows 0, link stays up.
