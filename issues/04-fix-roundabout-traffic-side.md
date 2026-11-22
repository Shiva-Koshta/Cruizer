# 04 · fix · Roundabout icons follow traffic side

| | |
|---|---|
| Priority | P1 (wrong arrow at every Indian roundabout) |
| Depends on | 03 |
| Bench test | B1 (confirm before merge) |

## Bug

| Case | Now | Should be (left-hand traffic) |
|---|---|---|
| Roundabout, exit known | `Icons.exitCode(n)` = `0x32 + n−1` (**counter-clockwise** family) | `0x0B + n−1` (clockwise) |
| Roundabout, exit unknown | `TurnKind.ROUNDABOUT.code()` = `0x0A` (clockwise base) | `0x0A` ✓ — but RHT users get the wrong base |

So in India the base icon is right but the numbered icon spins the wrong way; in right-hand countries
it is the opposite.

## Evidence

| Source | Clockwise (LHT) | Counter-clockwise (RHT) | Confidence |
|---|---|---|---|
| OpenTripper `TripperProtocol.java` / PROTOCOL.md | base `0x0A`, exits `0x0B–0x13` | base `0x31`, exits `0x32–0x3A` | hardware, Hunter 350 |
| wazetripper PROTOCOL.md §6 | `0x0B + (exit−1)` | `0x32 + (exit−1)` | rule stated; RHT implemented |
| Super Tripper (decompiled facts) | base `0x0A` + exit | base `0x31` + exit | code |
| spandan-kumar photos | exits 1–9 show the number | — | hardware |

## Implementation

### 1. `proto/Icons.kt`

Replace `ROUNDABOUT_EXIT_FIRST(0x32)` and `exitCode()`:

```kotlin
ROUNDABOUT_CW_EXIT_FIRST(0x0B),   // clockwise (left-hand traffic), exits 1..9 -> 0x0B..0x13
ROUNDABOUT_CCW_EXIT_FIRST(0x32),  // counter-clockwise (right-hand traffic), exits 1..9 -> 0x32..0x3A
...
companion object {
    private const val MAX_EXIT = 9

    fun roundaboutBase(side: TrafficSide): Int =
        if (side == TrafficSide.LEFT) ROUNDABOUT_CW.code else ROUNDABOUT_CCW.code

    /** Numbered roundabout icon for exits 1..9 (clamped). */
    fun exitCode(exit: Int, side: TrafficSide): Int =
        (if (side == TrafficSide.LEFT) ROUNDABOUT_CW_EXIT_FIRST else ROUNDABOUT_CCW_EXIT_FIRST).code +
            exit.coerceIn(1, MAX_EXIT) - 1

    /** Any roundabout glyph (base or numbered) — used by the dashboard mirror. */
    fun isRoundabout(code: Int): Boolean =
        code == ROUNDABOUT_CW.code || code == ROUNDABOUT_CCW.code ||
            code in ROUNDABOUT_CW_EXIT_FIRST.code until ROUNDABOUT_CW_EXIT_FIRST.code + MAX_EXIT ||
            code in ROUNDABOUT_CCW_EXIT_FIRST.code until ROUNDABOUT_CCW_EXIT_FIRST.code + MAX_EXIT
}
```

Fix the stale comment on `ROUNDABOUT_CW(0x0A)`.

### 2. `proto/TurnUpdate.kt`

`code()` must know the side. Change the signature (explicit, no default — a silent default is how this bug happened):

```kotlin
fun code(side: TrafficSide): Int = when (this) {
    ...
    ROUNDABOUT -> Icons.roundaboutBase(side)
    ...
}
fun nextCode(side: TrafficSide): Int = when (this) {
    KEEP_LEFT, OFF_RAMP_LEFT -> Icons.KEEP_LANE_LEFT.code
    KEEP_RIGHT, OFF_RAMP_RIGHT -> Icons.KEEP_LANE_RIGHT.code
    else -> code(side)
}
```

### 3. Call sites (all found by `grep -rn '\.code()\|nextCode()\|exitCode(' app/src`)

| File | Change |
|---|---|
| `PodService.kt:420-424` (`renderNavFrame`) | `val side = s.trafficSide`; `Icons.exitCode(turn.exit, side)`; `turn.kind.code(side)` |
| `PodService.kt:455` | `turn.next?.nextCode(side)` |
| `ui/PlaygroundScreen.kt:110-114` | use `s.trafficSide` the same way |
| `ui/DashboardCards.kt:272-275` | replace the inline roundabout range check with `PodIcons.isRoundabout(code)` |
| `proto/TurnUpdate.kt` `nextCode` | as above |

Also check `notify/ComapsSource.kt` `turnUpdate(...)` and any test that calls `code()`.

### 4. `docs/PROTOCOL.md` §5

Replace the roundabout row with both families, the side rule, and "Confirmed (B1)" once 02 is done.
Remove the "exit number never encoded" caveat for our app (RE app behaviour stays noted).

## Tests (`IconsTest.kt`, new; update `PodProtocolTest` if it uses `exitCode`)

| Call | Expected |
|---|---|
| `exitCode(1, LEFT)` | `0x0B` |
| `exitCode(9, LEFT)` | `0x13` |
| `exitCode(12, LEFT)` | `0x13` (clamped) |
| `exitCode(2, RIGHT)` | `0x33` |
| `roundaboutBase(LEFT)` / `(RIGHT)` | `0x0A` / `0x31` |
| `TurnKind.ROUNDABOUT.code(RIGHT)` | `0x31` |
| `isRoundabout(0x0F)`, `isRoundabout(0x35)` | true |
| `isRoundabout(0x14)` | false |

## Acceptance criteria

- [ ] B1 confirms the families on our Pod (if B1 shows otherwise, follow B1 and note it in PROTOCOL.md).
- [ ] Indian roundabout on a real Maps route ("take the 2nd exit") shows a clockwise arrow with "2".
- [ ] Setting traffic side to right shows the counter-clockwise family.
- [ ] App's Pod mirror on the Home tab still draws a roundabout for all 20 codes.
