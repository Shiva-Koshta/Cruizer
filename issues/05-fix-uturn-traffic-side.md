# 05 · fix · U-turn icon follows traffic side

| | |
|---|---|
| Priority | P1 |
| Depends on | 03 (and 04 if done first — it changes `code()` to take a `TrafficSide`) |
| Bench test | B12 (confirm) |

## Bug

`proto/TurnUpdate.kt:39` always sends `U_TURN_CCW (0x3D)` ("CCW is the common case").
In left-hand traffic (India) a U-turn sweeps to the **right**, i.e. clockwise → `U_TURN_CW (0x1A)`.

## Evidence

| Source | Mapping |
|---|---|
| spandan-kumar `TripperProtocol.kt` | traffic-side aware: LHT → `0x1A`, RHT → `0x3D` |
| Super Tripper facts | U-turn CW `0x1A`, CCW `0x3D` |
| wazetripper §6 | `1A` = U-turn clockwise, `3D` = U-turn counter-clockwise (codes confirmed) |

## Implementation

### `proto/Icons.kt` companion

```kotlin
fun uTurn(side: TrafficSide): Int =
    if (side == TrafficSide.LEFT) U_TURN_CW.code else U_TURN_CCW.code
```

### `proto/TurnUpdate.kt`

```kotlin
U_TURN -> Icons.uTurn(side)
```

(If 04 is not merged yet, add the `side: TrafficSide` parameter to `code()`/`nextCode()` here and update the
call sites listed in issue 04 §3.)

The dashboard mirror already draws both codes as a U (`DashboardCards.turnAngle`: both → 180°). Optional
polish: mirror the drawn U for `U_TURN_CW` (flip x) so the phone preview matches the Pod.

### `docs/PROTOCOL.md` §5

Add: "U-turn: `0x1A` for left-hand traffic, `0x3D` for right-hand traffic (B12)."

## Tests (`IconsTest.kt`)

- `Icons.uTurn(LEFT) == 0x1A`, `Icons.uTurn(RIGHT) == 0x3D`
- `TurnKind.U_TURN.code(LEFT) == 0x1A`

## Acceptance criteria

- [ ] B12: `0x1A` draws a clockwise (right-hand) U-turn on our Pod.
- [ ] A Maps "Make a U-turn" in India shows the clockwise U.
