# 17 · feat · Phone low-battery icon on the Pod

| | |
|---|---|
| Priority | P3 |
| Depends on | 14 (reuses the `sendNav` extraction and status-screen pattern) |
| Bench test | B10-a (confirm glyph) |

## Why

The Pod has a built-in "low battery" glyph (`0x44`, from the official app's icon enum). Riding with
navigation drains the phone; a glance at the Pod should warn before the phone dies. Cruizer defines
`Icons.LOW_BATTERY` and draws it in the mirror, but never sends it.

## Behaviour

```
battery ≤ 15 % and not charging
   navigating  → flash 0x44 for 2 s every 60 s, then back to the turn
   idle        → flash 0x44 for 2 s every 60 s, then back to clock/compass
charging or > 15 %  → never
```

A short flash, never a hold: turn info always wins.

## Implementation

### 1. `Constants.kt`

```kotlin
const val LOW_BATTERY_PCT = 15
const val ALERT_FLASH_MS = 2_000L
const val ALERT_REPEAT_MS = 60_000L
```

### 2. Pure scheduler (`proto/AlertSchedule.kt`, new — shared with issue 18)

```kotlin
package site.casiopea.cruizer.proto

/** Decides whether a status alert should be on screen at [now], given when it started being active. */
object AlertSchedule {
    fun showing(activeSince: Long?, now: Long, flashMs: Long, repeatMs: Long): Boolean =
        activeSince != null && (now - activeSince) % repeatMs < flashMs
}
```

### 3. Battery state (`PodService.kt`)

No permission needed: sticky broadcast.

```kotlin
private var lowBatterySince: Long? = null

private fun phoneBatteryLow(): Boolean {
    val i = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return false
    val pct = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) * 100 / i.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
    val charging = i.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0
    return pct in 0..Constants.LOW_BATTERY_PCT && !charging
}
```

Poll it at most every 30 s (cache `lastBatteryCheck`), not every tick.

### 4. `tick()` — one alert hook for both 17 and 18

Right after the lab/call branches:

```kotlin
val low = phoneBatteryLow()
lowBatterySince = if (low) lowBatterySince ?: now else null
if (AlertSchedule.showing(lowBatterySince, now, Constants.ALERT_FLASH_MS, Constants.ALERT_REPEAT_MS)) {
    link.send(PodProtocol.statusFrame(Icons.LOW_BATTERY, night))
    Session.podScreen.value = PodProtocol.PodScreen(Icons.LOW_BATTERY.code, "", null, night)
    lastFrameSentAt = now
    return
}
```

The normal branches re-send the turn/compass on the next tick, so no restore logic is needed.

### 5. Setting

`Settings.phoneAlerts: Boolean = true` (`KEY_PHONE_ALERTS`), `SettingSwitch("Phone alerts on the Pod")` in
`ui/SettingsScreen.kt` (existing helper at line ~310). Guard the hook with it. Issue 18 shares this switch.

### 6. Docs

`docs/PROTOCOL.md`: mark `0x44` "Confirmed (B10-a)" and note "phone battery, flashed".

## Tests (`AlertScheduleTest.kt`, new)

| activeSince | now | expect |
|---|---|---|
| null | 1000 | false |
| 0 | 0 | true |
| 0 | 1999 | true |
| 0 | 2000 | false |
| 0 | 60_500 | true |

## Acceptance criteria

- [ ] Phone at 14 %, unplugged, navigating: Pod flashes the battery glyph ~every minute for 2 s, turns otherwise.
- [ ] Plug in charger → flashing stops within 30 s.
- [ ] Switch off → never shown.
