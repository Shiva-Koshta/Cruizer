# 02 · test · Bench-test session on the bike (B1–B13)

| | |
|---|---|
| Priority | P0 |
| Depends on | 01 (Frame lab) |
| Output | `docs/BENCH_RESULTS.md` (new) + updates to `docs/PROTOCOL.md` |
| Time | ~45 min, bike parked, ignition on, engine off |

## Why

Open-source apps disagree on several bytes. Cruizer's values partly come from the official app's
code; the others from watching real Pods. Only our Pod can decide. Each result unblocks an issue.

## Setup

1. Debug build of the fork installed (issue 00), upstream Cruizer service stopped.
2. Ignition on → Connect within ~30 s → paired.
3. Settings → Frame playground → Raw lab. Phone camera ready: **photo of the Pod for every step**.
4. For each row: pick the preset (or paste hex) → Hold (or Send once) → wait 3 s → photo → note result.

All hex below is the 18-byte payload (zeros omitted at the end); the lab adds the CRC.

## Tests

### B1 · Roundabout icon families → issue 04
| Preset | Hex | Expect / record |
|---|---|---|
| B1-a CW base | `10 11 0A 00 32 01 00 FF FF FF 41 FF FF FF` | Clockwise roundabout, no number? |
| B1-b CW exit 2 | `10 11 0C 00 32 01 00 FF FF FF 41 FF FF FF` | Clockwise + "2"? |
| B1-c CW exit 9 | `10 11 13 00 32 01 00 FF FF FF 41 FF FF FF` | Clockwise + "9"? |
| B1-d CCW base | `10 11 31 00 32 01 00 FF FF FF 41 FF FF FF` | Counter-clockwise |
| B1-e CCW exit 2 | `10 11 33 00 32 01 00 FF FF FF 41 FF FF FF` | Counter-clockwise + "2"? |

### B2 · Ring intensity `[6]` → issue 07
Right turn (`0x15`); distance in `[3-4]`.
| Preset | Hex | Record |
|---|---|---|
| B2-a 500 m, `[6]=00` | `10 11 15 01 F4 01 00 FF FF FF 41 FF FF FF` | ring state |
| B2-b 500 m, `[6]=50` | `10 11 15 01 F4 01 50 FF FF FF 41 FF FF FF` | ring state |
| B2-c 500 m, `[6]=60` | `10 11 15 01 F4 01 60 FF FF FF 41 FF FF FF` | ring state |
| B2-d 10 m, `[6]=50` | `10 11 15 00 0A 01 50 FF FF FF 41 FF FF FF` | ring state |
| B2-e 10 m, `[6]=40` | `10 11 15 00 0A 01 40 FF FF FF 41 FF FF FF` | ring state |
| B2-f 50 m, `[6]=20` / `30` | `10 11 15 00 32 01 20 FF FF FF 41 FF FF FF` | partial ring? |
Question: is `0x50` "full ring / imminent" or "far"? Does `0x60` render at all?

### B3 · Next-arrow bytes `[8..10]` → issue 08
Next arrow = turn left `0x14`.
| Preset | Hex | Record |
|---|---|---|
| B3-a others' frame | `10 11 15 00 32 01 40 14 FF FF 41 FF FF FF` | small arrow shown? |
| B3-b Cruizer style | `10 11 15 00 32 01 40 14 00 C8 11 FF FF FF` | small arrow? any "200 m" near it? |
| B3-c `[10]=F1` no next | `10 11 15 00 32 01 40 FF FF FF F1 FF FF FF` | any difference vs `41`? |
| B3-d `[10]=31` / `42` | `10 11 15 00 32 01 40 FF FF FF 31 FF FF FF` | any difference? |

### B4 · Keep icons (large) → issue 06
| Preset | Hex | Record |
|---|---|---|
| B4-a `0x28` | `10 11 28 00 32 01 00 FF FF FF 41 FF FF FF` | keep-left glyph? |
| B4-b `0x27` | `10 11 27 00 32 01 00 FF FF FF 41 FF FF FF` | keep-right glyph? |
| B4-c `0x2B` | `10 11 2B 00 32 01 00 FF FF FF 41 FF FF FF` | what draws as *large* icon? |
| B4-d `0x2C` | `10 11 2C 00 32 01 00 FF FF FF 41 FF FF FF` | |

### B5 · 12-hour time → issue 09
Use **Send once**, then Release and let the Pod fall back to its clock (it may need the watchdog
drop + reconnect, ~10 s). Do each line separately.
| Preset | Hex | Meaning | Clock should read |
|---|---|---|---|
| B5-a | `50 83 1E` | Cruizer: PM = `0x80|3` | 3:30 PM |
| B5-b | `50 03 1E` | wazetripper: PM = plain | 3:30 PM |
| B5-c | `50 0F 1E 01` | Super: raw 24 h + `[3]=01` | 3:30 PM |
| B5-d | `50 49 1E` | AM = `0x40|9` (all agree) | 9:30 AM |
| B5-e | `50 8C 00` vs `50 80 00` | noon: 12 vs 0 | 12:00 PM |
ETA bottom line at noon (Hold): `10 11 15 00 32 01 00 FF FF FF 41 8C 1E 00` vs `… 41 80 1E 00`.

### B6 · Duration bottom line → issue 19
| Preset | Hex | Record |
|---|---|---|
| B6-a `[13]=01` | `10 11 15 00 32 01 00 FF FF FF 41 01 1E 01` | "1:30"? or a distance "286 m"? |
| B6-b `[13]=03` | `10 11 15 00 32 01 00 FF FF FF 41 01 1E 03` | |

### B7 · Icon sweep (untested ranges) → may create new issues
Use the sweep row. Photograph every code in `0x23–0x2A`, `0x40`, `0x43`, `0x46–0x60`.
Record "blank" / glyph description. Stop if the Pod disconnects repeatedly; note the code.

### B8 · Return to clock (no lab) → baseline behaviour
Start a 2-minute Maps route, arrive (or stop navigation). Record: seconds until the Pod shows its clock,
and whether it reconnects by itself.

### B9 · Compass → issue 16
| Preset | Hex | Expect |
|---|---|---|
| B9-N | `10 11 41 00 00 00 00 FF 00 00 00 FF FF FF 10` | N |
| B9-E | `10 11 41 00 00 00 00 FF 00 00 00 FF FF FF 20` | E |
| B9-NE | `10 11 41 00 00 00 00 FF 00 00 00 FF FF FF 50` | NE (naming disputed: NE vs NW) |
| B9-NW | `10 11 41 00 00 00 00 FF 00 00 00 FF FF FF 60` | NW |
| B9-night | `10 11 41 00 00 00 01 FF 00 00 00 FF FF FF 10` | N, night palette |

### B10 · Status icons → issues 14, 17, 18
| Preset | Hex | Expect |
|---|---|---|
| B10-a low battery | `10 11 44 00 00 01 00 FF 00 00 41 FF FF FF` | battery glyph |
| B10-b no data | `10 11 42 00 00 01 00 FF 00 00 41 FF FF FF` | no-data glyph |
| B10-c rerouting | `10 11 1C 00 00 01 00 FF 00 00 41 FF FF FF` | recalculating glyph |

### B11 · Pod info replies → issue 15
1. Send once `03` (PING_FW). Reply log: expect `03 xx yy …` (firmware, BCD).
2. Send once `30`. Reply log: expect `30` + ASCII serial.
Copy both reply lines into the results.

### B12 · U-turn direction → issue 05
| Preset | Hex | Record |
|---|---|---|
| B12-a `0x1A` | `10 11 1A 00 32 01 00 FF FF FF 41 FF FF FF` | turns right-around (clockwise)? |
| B12-b `0x3D` | `10 11 3D 00 32 01 00 FF FF FF 41 FF FF FF` | turns left-around? |

### B13 · Speedometer hack → issue 20
| Preset | Hex | Record |
|---|---|---|
| B13-a | `10 11 09 00 3C 01 00 FF FF FF 41 FF FF FF` | "60 m" with straight arrow — readable as speed? |
| B13-b | `10 11 FF 00 3C 01 00 FF FF FF 41 FF FF FF` | blank icon + "60"? |

## Recording results

Create `docs/BENCH_RESULTS.md`:

```markdown
# Bench results — Pod firmware <from B11>, <bike>, <date>
| Test | Result | Photo | Decision |
|---|---|---|---|
| B1-a | clockwise, no number | img/b1a.jpg | issue 04: use 0x0A for LHT base |
```

Then for each decision update `docs/PROTOCOL.md` (mark **Confirmed (B-id)**) and unblock the issue.

## Acceptance criteria

- [ ] Every row has a result + photo.
- [ ] `docs/BENCH_RESULTS.md` committed with the decisions.
- [ ] Issues 07, 08, 09, 19, 20 updated with "Decision: …" at the top.
