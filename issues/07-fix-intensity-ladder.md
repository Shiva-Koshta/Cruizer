# 07 · fix · Ring intensity byte `[6]` ladder

| | |
|---|---|
| Priority | P2 |
| Depends on | 01 (lab), **B2 result** |
| Status | **Blocked until B2 decides** |

## Problem

Byte `[6]` = `intensity << 4 | night`. Two ladders exist:

| Distance to turn | Cruizer (from RE app code) | wazetripper / Super / OpenTripper / spandan |
|---|---|---|
| ≤ 10 m | 4 → `0x40` | 5 → `0x50` |
| ≤ 20 m | 4 → `0x40` | 4 → `0x40` |
| ≤ 40–45 m | 3 | 3 |
| ≤ 60–70 m | 2 | 2 |
| ≤ 80–95 m | 1 | 1 |
| ≤ 100 m | 0 | 0 |
| 100–500 m | **5 → `0x50`** | **0 → `0x00`** |
| > 500 m | **6 → `0x60`** | **0 → `0x00`** |

If `0x50` means "imminent / full ring" (OpenTripper: "outer ring fully lit at 0x50"), Cruizer lights
the full ring when the turn is still 300 m away. spandan's Hunter photos show "staged approach emphasis".

## Decision rule (from B2)

| B2 outcome | Do |
|---|---|
| 500 m with `0x00` = no ring, `0x50` = full ring | **Adopt the others' ladder** (plan below) |
| `0x50`/`0x60` at 500 m look like "far" / dim | Keep Cruizer's ladder, mark it Confirmed (B2) in PROTOCOL.md, close |
| Unclear | Keep Cruizer's ladder; note in BENCH_RESULTS |

## Plan (adopt the others' ladder)

### 1. `Constants.kt`

```kotlin
/** Ring intensity ladder: (max distance m, level). Hardware-observed (wazetripper, B2). */
val INTENSITY_LADDER = listOf(10 to 5, 20 to 4, 45 to 3, 70 to 2, 95 to 1)
/** RE behaviour: next-arrow data only while the current turn is this close. */
const val NEXT_ARROW_MAX_M = 100
```

### 2. `proto/PodProtocol.kt`

```kotlin
fun intensity(distanceM: Int): Int =
    Constants.INTENSITY_LADDER.firstOrNull { distanceM <= it.first }?.second ?: 0
```

- Drop the `maneuverCode` parameter and the `isKeep` special case (no other app uses it; RE-only). Update
  the only caller in `navFrame` and `intensityOverride` stays.
- **Important coupling:** `navFrame` uses `hasNext = next != null && intensity < 5` to mean "within 100 m".
  With the new ladder `< 5` would mean "not within 10 m" — wrong. Replace with distance:
  ```kotlin
  val hasNext = next != null && distanceM <= Constants.NEXT_ARROW_MAX_M
  ```
  (Issue 08 may change next-arrow behaviour further.)

### 3. `docs/PROTOCOL.md` §4 `[6]`

Replace the RE ladder with the confirmed one; keep the RE ladder as a note ("RE app code, not used").

## Tests (`PodProtocolTest`)

Rewrite `intensity ladder follows the RE buckets`:

| distance | expected |
|---|---|
| 5, 10 | 5 |
| 15, 20 | 4 |
| 30, 45 | 3 |
| 60, 70 | 2 |
| 90, 95 | 1 |
| 96, 300, 900 | 0 |

Update `nav frame packs icon distance and night flag` (55 m → level 2 → `[6] = 0x21` stays the same ✓)
and `next arrow suppressed above intensity 5` → rename to `…beyond 100 m`, still 900 m → `[7] = FF`.

## Acceptance criteria

- [ ] B2 decision recorded.
- [ ] On a real route the ring stays off until ~95 m and fills as the turn approaches.
