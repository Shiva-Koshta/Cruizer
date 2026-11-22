# 19 · feat (experimental) · Time-left bottom-line mode

| | |
|---|---|
| Priority | P4 |
| Depends on | 01, **B6 result** |
| Status | **Blocked until B6 decides; may be closed as "not supported"** |

## Why

Riders often want "35 min left" rather than an arrival clock. Cruizer dropped "time left" because
"the Pod's bottom line has no time-remaining format" (`Session.kt` comment on `KEY_BOTTOM`). Super's code
sends a duration with `[11]=hours, [12]=minutes, [13]=01`. Cruizer's encoder treats any non-zero `[13]` as
**distance mode**, so this may render as a distance (`0x011E` = 286 m). Only B6 can tell.

## Decision rule (from B6)

| B6 | Do |
|---|---|
| B6-a shows `1:30` (or `1h30`) | Build it (plan below) |
| Shows `286 m` / `28.6 km` / garbage | Close as not supported; add the result to PROTOCOL.md §4 |

## Plan

### 1. `Session.kt`

Append (never reorder — ordinals are stored under `bottom_mode_v2`):
```kotlin
enum class BottomMode { TOTAL, ARRIVAL, CYCLE, TIME_LEFT }
```
`ui/SettingsScreen.kt` `BOTTOM_OPTIONS`: add `Session.BottomMode.TIME_LEFT to "time left"`.

### 2. `proto/PodProtocol.kt`

```kotlin
sealed interface Bottom {
    ...
    data class Remaining(val seconds: Int) : Bottom
}
```
`encodeBottom`:
```kotlin
is Bottom.Remaining -> {
    val mins = (bottom.seconds + 59) / 60
    p[0] = (mins / 60).coerceAtMost(Constants.MAX_REMAINING_HOURS).toByte()
    p[1] = (mins % 60).toByte()
    p[2] = Constants.BOTTOM_DURATION_MODE.toByte()   // 0x01 per B6
}
```
`bottomLabel`: `is Bottom.Remaining -> "%d:%02d".format(hi, lo)`.

`Constants.kt`: `BOTTOM_DURATION_MODE = 0x01`, `MAX_REMAINING_HOURS = 99`.

### 3. `PodService.renderNavFrame`

```kotlin
Session.BottomMode.TIME_LEFT -> turn.remainingSeconds?.let { PodProtocol.Bottom.Remaining(it) }
```
and add it to the `CYCLE` candidates list.

### 4. Docs

`docs/PROTOCOL.md` §4 bottom line: add duration mode `[13]=01` "Confirmed (B6)"; update the comment in
`Session.kt` above `KEY_BOTTOM` and `PodProtocol.Bottom` KDoc.

## Tests (`PodProtocolTest`)

- `Remaining(90*60)` → `[11..13] = 01 1E 01`, label `"1:30"`.
- `Remaining(59)` → `00 01 01` (rounds up to 1 min).
- `Remaining(200*3600)` → hours clamped to 99.

## Acceptance criteria

- [ ] B6 decision recorded.
- [ ] With "time left", a 35-minute route shows `0:35` and counts down as Maps updates.
