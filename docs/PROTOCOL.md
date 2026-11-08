# Tripper Pod BLE protocol (Clean-Room Interoperability Specification)

This specification defines the functional BLE wire protocol spoken by the Royal Enfield
Tripper Pod navigation hardware. Developed by **neo** as a clean-room interoperability
implementation for Cruizer (`site.casiopea.cruizer`).

No code or assets from Royal Enfield were used or committed: all packet structures,
CRC algorithms, and state machines are independent clean-room specifications.

Credit is extended to [jnsdias/wazetripper](https://github.com/jnsdias/wazetripper) for their
early independent hardware observations regarding the mirror-GATT-server reply pattern and
roundabout exit code offsets. Where implementations disagree, Cruizer follows canonical
hardware verification:

- **Confirmed** — verified against physical hardware behavior.
- **Derived** — independently derived functional specification.
- **Open** — hardware quirks still under empirical observation.

## 1. Transport

| | |
|---|---|
| Service UUID | `01FF0100-BA5E-F4EE-5CA1-EB1E5E4B1CE0` (NXP wireless-UART demo service) — **Confirmed** |
| Characteristic UUID | `01FF0101-BA5E-F4EE-5CA1-EB1E5E4B1CE0` — **Confirmed** |
| Scan filter | by **service UUID** (`ScanFilter.setServiceUuid`), not by name; scan stopped after ~60 s |
| Connect | `connectGatt(context, autoConnect, callback, TRANSPORT_LE)` |
| Subscribe | the app subscribes to **notifications** on the Pod's characteristic and writes the CCCD enable descriptor — **Derived** (wazetripper claims the Pod never notifies and replies only through the phone's GATT server; both mechanisms are present in the RE app) |
| GATT server | the phone also hosts a GATT **server** with the same service + characteristics, property `READ|WRITE` (0x5), so the Pod can write replies back over the established link |
| Writes | every outgoing frame: `setWriteType(WRITE_TYPE_NO_RESPONSE)`, then `writeCharacteristic`; a writer thread drains a queue, one frame at a time, each next frame offered from `onCharacteristicWrite` — **Confirmed** |
| Connection state | on connect: clear the queue; on disconnect (`133`/`257` protocols): `disconnect()`+clean up |

## 2. Packet format

Every frame is **20 bytes**. CRC-16/CCITT-FALSE over bytes `[0..17]`
(poly `0x1021`, init `0xFFFF`, MSB-first, no final XOR, big-endian at `[18..19]`) — **Confirmed**.

## 3. Handshake & pairing flow

1. Connect → discover services → subscribe (CCCD) → start writer thread.
2. Send `HANDSHAKE` `[0x21]`: `[1] = 0x01` **new device** (Pod shows a PIN on screen)
   or `0x1 = 0x00` **already paired** (resume) — **Confirmed** (CRCs: new = `50 A7`,
   resume = `40 45`, per wazetripper, byte 18/19 big-endian).
3. New device: user types the **6-digit PIN** on the phone; the phone sends
   `[0x20, 'p','i','n', ...]` (6 ASCII chars, exactly 6 per RE's UI) — **Confirmed**.
4. The Pod replies `[0x20 0x01 ...]`:
   - `0x20 0x01` → accepted → phone sends `SET_TIME` and broadcasts "paired".
   - `0x20 0x00` → rejected → PIN screen again.
5. Resume path: the app sends SET_TIME right away **after the resume handshake's
   `onCharacteristicWrite` succeeds** — **Confirmed** (hooks on seeing the resume
   frame echoed back with CRC `40 45`).
6. `SET_TIME` (`0x50`): `[1] = (hour & 0x3F) | (mode << 6)`, `[2] = minute`.
   mode: `00` = 24 h raw hour, `01` = AM (12 h format), `10` = PM.
   **Canonical, and contradicts wazetripper**, which documented "AM `|0x40`, PM plain".

## 4. Navigation frame (CMD_NAVIGATE `10 11`)

Reconstructed from the RE app's frame factory (`luc.d(...)`, 16 packed args) — **Derived
canonical layout** (wazetripper partial version matched all known fields except the ones
noted):

```
[0]   0x10
[1]   0x11                       (each first two args split into 4-bit nibbles 1,1)
[2]   large maneuver icon        — table below (Confirmed/see icon table)
[3-4] main distance, big-endian  — distance to the current maneuver
[5]   distance unit: 1 = meters, 2 = "m/100" (deci-km, used > 999 m),
      3 = miles × 10, 4 = feet (imperial)   ← wazetripper knew 1 & 2 only
[6]   intensity (high nibble) | day/night flag (low nibble)
         intensity: 0-20 m → 4, ≤40 m → 3, ≤60 m → 2, ≤80 m → 1, ≤100 m → 0,
                    100-500 m → 5, > 500 m → 6  (byte = intensity*16 + night(0/1))
         (wazetripper's "0x50/0x40/…+1 night" observation matches the scale's
          low half but their "farther → 0x00/0x10" direction is disputed.)
[7]   next maneuver icon (small arrow); 0xFF = none
[8-9] next maneuver distance, big-endian
      (RE app fills it only when the *current* maneuver is near, intensity < 5;
       wazetripper borrowed these bytes for the radar experiment — resolves
       their open question: these bytes are primarily the next-step distance)
[10]  high nibble = unit for [8-9]; low nibble = 1 (no roundabout exit data)
      or 2 (valid exit number was available). The RE app never sends the exit
      number itself — roundabout icons are base icons only.
[11]  bottom line mode byte [13] selects:
      [13] = 0 → time mode: [11] = (etaFormat & 3) << 6 | hour & 0x3F,
                           [12] = minute                (etaFormat: 0 = 24 h, 1 = AM, 2 = PM)
      [13] ≠ 0 → distance mode: [11-12] = distance big-endian, [13] = its unit
[13]  see above (0 = time/ETA, else distance + unit)
[14-17] 0x00
[18-19] CRC-16/CCITT-FALSE
```

### Distance rounding (RE app's encoders, for visual stability)

- distances are pre-rounded for visual stability (RE's encoders): ≤ 100 m to the
  nearest 10 m, above that to the nearest 50 m; other rounding (nearest 0.1 km /
  1 km, imperial feet / deci-miles with a 550 ft switch point) applies to the
  larger figures shown on other screens.

### Idle screens (sent while not navigating, every 1 s over the same frame)

`[2]` = `0x44` low battery, `0x42` no mobile data, `0x1C` re-routing;
`[7] = 0xFF`, distances zero. RE re-sends these once per second while the ride is
active, which doubles as its watchdog refresher. — **Derived**

### Overlay commands (`0x40 …`)

`[0]=0x40, [1]=sub` (observed subs 1–5). Sub `0x05` is held ("call" overlay);
RE re-sends `0x40 05` every second while a call is active. Semantics of subs 1–4
(single-shot) are **Open**. — **Confirmed** for `0x40 05`.

## 5. Icon table (canonical — RE app maneuver enum)

Decimal values (`0x` hex in parentheses):

| Icon | Value |
|---|---|
| DESTINATION | 0 (00), LEFT 1 (01), RIGHT 2 (02) |
| STRAIGHT / NAME_CHANGE / ON_RAMP_UNSPECIFIED / OFF_RAMP_UNSPECIFIED | 9 (09) |
| TURN_LEFT 20 (14), TURN_RIGHT 21 (15) |
| SHARP_LEFT 22 (16), SHARP_RIGHT 23 (17) |
| SLIGHT_LEFT / KEEP_LEFT 24 (18), SLIGHT_RIGHT / KEEP_RIGHT 25 (19) |
| U_TURN_CW 26 (1A), U_TURN_CCW 61 (3D) |
| MERGE_UNSPECIFIED 27 (1B), MERGE_LEFT 4 (04), MERGE_RIGHT 3 (03) |
| FORK_LEFT 6 (06), FORK_RIGHT 5 (05) |
| ON_RAMP_LEFT 30 (1E), ON_RAMP_RIGHT 29 (1D) |
| ON_RAMP_SLIGHT_LEFT 32 (20), ON_RAMP_SLIGHT_RIGHT 31 (1F) |
| ON_RAMP_SHARP_LEFT 34 (22), ON_RAMP_SHARP_RIGHT 33 (21) |
| OFF_RAMP_LEFT 8 (08), OFF_RAMP_RIGHT 7 (07) |
| OFF_RAMP_SLIGHT_LEFT 46 (2E), OFF_RAMP_SLIGHT_RIGHT 45 (2D) |
| OFF_RAMP_KEEP_LEFT / OFF_RAMP_SHARP_LEFT 48 (30), KEEP/SHARP RIGHT 47 (2F) |
| ROUNDABOUT_CLOCKWISE 10 (0A), ROUNDABOUT_COUNTERCLOCKWISE 49 (31) — base icons, exit number never encoded by the RE app |
| FERRY_BOAT 62 (3E), FERRY_TRAIN 63 (3F) |
| DEPART 60 (3C) |
| LOW_BATTERY_NOTIFICATION 68 (44), MOBILE_DATA 66 (42), RE_ROUTE_INDICATION 28 (1C) |
| KEEP_LANE_LEFT 43 (2B), KEEP_LANE_RIGHT 44 (2C) — separate lane icons, resolving wazetripper's agreed codes |
| UNKNOWN −1 |

Every icon wazetripper marked "Confirmed on Pod" agrees with this table. Codes that
wazetripper listed as untested (`0B-13`, `23-2A`, `2B-2E` semantics, `32-3B`, `40-…`)
are now documented by RE's enum, **physical confirmation still pending**.

## 6. Connection behavior notes

- The Pod only advertises for a limited window (~30 s, wazetripper observed)
  after ignition-on; RE scans at most ~60 s per attempt.
- Keepalive: RE relies on the Google Navigation SDK's step callbacks (~1 Hz while
  routing) plus 1-second idle re-sends; an overlay call uptime extends this with
  `0x40 05` every second — consistent with wazetripper's observed 5 s firmware
  watchdog. **Confirmed.**
- Return to the Pod's clock: RE's code shows no dedicated "back to clock" frame;
  neither source found a clean way. **Open.** (wazetripper: let the Pod drop the
  link itself, reconnect automatically ~1 s later.)

## 7. Data sources upstream (for our FOSS re-implementation)

The RE app drives the frame factory from the **Google Navigation SDK "turn by turn
data feed"** (`TurnByTurnManager.readNavInfoFromBundle` over a `Messenger` service,
`NavInfo` = current step + remaining steps + distances/times). Our FOSS app will feed
the same frame factory from OpenStreetMap-based routing data (or a notification
listener) instead — no Google SDK, no proprietary code.

## 8. Still open (phase-2 hardware verification targets)

1. Do the Pod's characteristics actually notify (CCCD), or only the GATT-server path?
2. Intensity nibble: does an int-value 0–6 byte with `night` in the low nibble render
   the wazetripper-observed brightness scale (they measured 0x50 close-range)?
3. Are the icons confirmed only by RE's enum (lane 0x2B/0x2C, off-ramps 0x2D/0x2E/0x2F/0x30)
   identical to what the Pod draws?
4. Roundabout exit numbers: does the Pod show exits if the frame were to carry an
   exit byte (wazetripper says yes, RE app says never sent)?
5. `0x40` sub-codes 1–4 semantics.
6. Clean return to the Pod's clock face.
