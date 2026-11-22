# 06 · fix · Keep left/right use the soft-keep icons

| | |
|---|---|
| Priority | P1 (highway forks, common on Indian expressways) |
| Depends on | 00 (touches `TurnUpdate.code()`; rebase on 04 if merged) |
| Bench test | B4 (confirm) |

## Bug

`TurnKind.KEEP_LEFT/RIGHT.code()` sends `KEEP_LANE_LEFT/RIGHT (0x2B/0x2C)` as the **large** icon.
All three other apps send `0x28` (keep left) / `0x27` (keep right) as the large icon and use
`0x2B/0x2C` only for the **small next arrow** `[7]`.

## Evidence

| Source | Large icon | Small arrow | Confidence |
|---|---|---|---|
| wazetripper §6/§7 | `28`/`27` ("exit/keep soft") | `2B`/`2C` | `27/28` hardware-checked |
| OpenTripper | `28`/`27` | `2B`/`2C` | code |
| spandan-kumar | `28`/`27` | `2B`/`2C` | code |
| Cruizer (RE enum) | `2B`/`2C` | `2B`/`2C` | derived, `2B/2C` as large never hardware-checked |

## Implementation

### 1. `proto/Icons.kt` — add the codes (missing from the enum)

```kotlin
KEEP_RIGHT_SOFT(0x27),  // exit/keep right, soft (wazetripper: confirmed)
KEEP_LEFT_SOFT(0x28),   // exit/keep left, soft (wazetripper: confirmed)
```

### 2. `proto/TurnUpdate.kt`

```kotlin
KEEP_LEFT -> Icons.KEEP_LEFT_SOFT.code
KEEP_RIGHT -> Icons.KEEP_RIGHT_SOFT.code
```

`nextCode()` stays: `KEEP_*`/`OFF_RAMP_*` → `KEEP_LANE_*` (0x2B/0x2C). Update its KDoc and the
`code()` KDoc ("for keep we send the dedicated lane icons as the main icon" is no longer true).

### 3. `proto/PodProtocol.kt` — intensity special case

`isKeep()` (line ~110) keys the "hold intensity 4" rule on `0x2B/0x2C`. Extend it so behaviour is unchanged:

```kotlin
private val KEEP_CODES = setOf(Icons.KEEP_LANE_LEFT.code, Icons.KEEP_LANE_RIGHT.code,
                               Icons.KEEP_LEFT_SOFT.code, Icons.KEEP_RIGHT_SOFT.code)
private fun isKeep(code: Int) = code in KEEP_CODES
```

(Issue 07 may delete this rule entirely; whichever lands second resolves the conflict.)

### 4. `ui/DashboardCards.kt` `turnAngle()`

Add `KEEP_LEFT_SOFT` to the −45° group and `KEEP_RIGHT_SOFT` to the +45° group (a keep is a slight
bend, not a 90° turn), so the phone mirror matches.

### 5. `docs/PROTOCOL.md` §5

Add `KEEP_RIGHT_SOFT 39 (27)`, `KEEP_LEFT_SOFT 40 (28)` — Confirmed (wazetripper, B4); note large vs small usage.

## Tests

- `TurnKind.KEEP_LEFT.code(side) == 0x28`, `KEEP_RIGHT → 0x27`.
- `TurnKind.KEEP_LEFT.nextCode(side) == 0x2B` (unchanged).
- Update `PodProtocolTest.intensity ladder…` keep asserts to use `KEEP_LEFT_SOFT` too.

## Acceptance criteria

- [ ] B4-a/b draw keep-left / keep-right glyphs; B4-c/d photographed for the record.
- [ ] Maps "Keep left at the fork" shows `0x28` on the Pod (check debug log `nav frame: 10 11 28 …`).
