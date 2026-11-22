# 09 · fix · 12-hour SET_TIME / ETA encoding

| | |
|---|---|
| Priority | P2 (12 h is the app default: `twentyFourHourClock = false`) |
| Depends on | 01, **B5 result** |
| Status | **Blocked until B5 decides** |

## Problem

Three incompatible schemes for the Pod clock (`SET_TIME 0x50`) in 12 h mode:

| Scheme | `[1]` for 15:30 | `[1]` for 09:30 | `[3]` | Source |
|---|---|---|---|---|
| A — Cruizer (now) | `0x83` (3 \| PM `0x80`) | `0x49` (9 \| AM `0x40`) | 00 | RE app code |
| B — wazetripper | `0x03` (PM plain) | `0x49` | 00 | wazetripper, spandan |
| C — Super | `0x0F` (raw 24 h) | `0x09` | `01` = 12 h | Super decompile facts |

ETA bottom line `[11]` (both A and wazetripper use AM `0x40` / PM `0x80`) differs only at **noon/midnight**:
A sends hour 12 (`0x8C`), wazetripper sends 0 (`0x80`).

## Decision rule (from B5)

| B5 result | Do |
|---|---|
| B5-a shows 3:30 PM | Keep A, mark Confirmed (B5). Only fix noon if B5-e says so. |
| Only B5-b correct | Switch SET_TIME to B |
| Only B5-c correct | Switch SET_TIME to C |
| B5-e: only `0x80` shows 12:00 | ETA: send `h24 % 12` (0..11) instead of 1..12 |

## Plan

All in `proto/PodProtocol.kt` (pure).

**SET_TIME → B:**
```kotlin
val h12 = if (hour24 % 12 == 0) 12 else hour24 % 12
it[1] = (if (hour24 < 12) h12 or 0x40 else h12).toByte()
```
**SET_TIME → C:**
```kotlin
it[1] = hour24.toByte(); it[2] = minute.toByte(); it[3] = if (is12hFormat) 1 else 0
```
**ETA noon fix** (`encodeBottom`, `Bottom.Arrival`):
```kotlin
(if (h24 < 12) 0x01 else 0x02) to (h24 % 12)
```
and `bottomLabel` must then print `12` when the hour part is 0 (`if (h == 0) 12 else h`).

Name the AM/PM bits in `Constants.kt` (`TIME_AM_BIT = 0x40`, `TIME_PM_BIT = 0x80`) and use them in both encoders.

Update the KDoc on `setTimeFrame` (it currently says "wazetripper had PM wrong") and `docs/PROTOCOL.md` §3.6.

## Tests (`PodProtocolTest`)

- Rewrite `set time 12h packs AM-PM into bits 6-7` for the chosen scheme with 15:30, 09:30, 00:05, 12:00.
- If the ETA noon fix applies: `bottomLabel(Arrival at 12:10, 12h)` == `"12:10 PM"` and `[11] == 0x80`.

## Acceptance criteria

- [ ] B5 decision recorded.
- [ ] With 12 h setting, the Pod's own clock shows the phone's time after connect, morning and afternoon.
- [ ] ETA around noon shows 12:xx PM.
