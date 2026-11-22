# 08 · fix · Next-arrow bytes `[8..10]` alignment

| | |
|---|---|
| Priority | P2 |
| Depends on | 07 (shares `hasNext`), **B3 result** |
| Status | **Blocked until B3 decides** |

## Problem

| Byte | Cruizer | All other apps (hardware-proven frames) |
|---|---|---|
| `[8-9]` | next-step distance when near, else `FF FF` | always `FF FF` |
| `[10]` | `(nextUnit << 4) \| (exit>0 ? 2 : 1)` → e.g. `0x11`, `0xF1`, `0x12` | always **`0x41`** |

Cruizer's own `statusFrame` already uses `0x41`, so the app sends two different `[10]` styles.
Nobody has seen the Pod render the `[8-9]` distance. Risk: the Pod might ignore or mis-render frames
whose `[10]` is not `0x41` (Super's code names it "road type": `31` highway, `41` street, `42` avenue — a guess).

## Decision rule (from B3)

| B3 outcome | Do |
|---|---|
| B3-b shows a small "200 m" next to the small arrow | Keep Cruizer's `[8-9]`; set `[10]` high nibble = unit, low = 1, but confirm B3-c `F1` = `41` visually. Document Confirmed. Close or partial. |
| B3-a and B3-b look identical (no extra distance) | **Adopt `[8-9] = FF FF`, `[10] = 0x41` always** (plan below) |
| B3-c/d (`F1`, `31`) render differently from `41` | Adopt `0x41`; record what `31`/`42` do in PROTOCOL.md |

## Plan (adopt `FF FF` + `0x41`)

### 1. `Constants.kt`

```kotlin
const val NAV_BYTE10 = 0x41   // constant in every hardware-proven frame (B3)
```

### 2. `proto/PodProtocol.kt` `navFrame`

```kotlin
it[7] = (if (hasNext) next else 0xFF).toByte()
it[8] = 0xFF.toByte(); it[9] = 0xFF.toByte()
it[10] = Constants.NAV_BYTE10.toByte()
```

- Remove the now-unused `nextDistanceM` and `exit` parameters from `navFrame` and their arguments in
  `PodService.renderNavFrame`, `PodService.tick` (arrival frame), `PlaygroundScreen`.
  `TurnUpdate.nextDistanceM` stays (UI may show it).
- `statusFrame` already uses `0x41`: switch it to the constant.

### 3. `docs/PROTOCOL.md` §4

`[8-9]`: "FF FF (B3: Pod does not draw a next distance)". `[10]`: "0x41 constant (B3)". Move the RE-derived
nibble layout to a "RE app, not used" note.

## Tests (`PodProtocolTest`)

- `nav frame packs icon distance and night flag`: replace the `[8-10]` asserts with
  `[8] == 0xFF, [9] == 0xFF, [10] == 0x41`.
- New: `navFrame(...)[10] == 0x41` for a roundabout with exit 3 and for no-next.

## Acceptance criteria

- [ ] B3 decision recorded.
- [ ] Debug log shows `… FF FF 41 …` in every nav frame.
- [ ] Next small arrow still appears within 100 m of a turn.
