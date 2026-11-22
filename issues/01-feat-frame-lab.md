# 01 · feat · Frame lab: raw frames, icon sweep, Pod reply log

| | |
|---|---|
| Priority | P0 (tooling for 02 and every protocol fix; **runs the colour test that gates owner priority #2**) |
| Depends on | 00 |
| Bench test | enables B1–B13 |
| Build type | debug only (Playground is already gated by `BuildConfig.DEBUG`) |

## Why

The existing Frame Playground (`ui/PlaygroundScreen.kt`) can only post a `TurnUpdate` through
`NavRoute.postTurn()`, so every frame goes through *our current* encoder. To settle the protocol
conflicts (intensity, byte 10, icon families, 12 h time …) we must send **arbitrary bytes**, keep
them on screen past the 5 s watchdog, and see what the Pod writes back.

**First job: the colour test.** Near a turn Cruizer already sends the same `[6] = 0x40` as Super Pod, yet the
owner's Pod stays black and white with Cruizer and turns red with the RE app and Super Pod. So a byte other than `[6]`
decides whether colour shows. The existing Playground can't find out which: every frame goes through Cruizer's
encoder (`[10] = F1/11`, next distance in `[8-9]`, ETA bottom line). The Raw lab can, by changing one byte at a time.

## What to build

```
Playground
 ├─ (existing) maneuver / distance / exit / night chips + preview
 └─ NEW "Raw lab" card
     ├─ hex field (up to 18 bytes, CRC added automatically, short input zero-padded)
     ├─ [Hold] re-send every tick (watchdog)   [Send once]   [Release]
     ├─ Preset dropdown: "Colour test" group first (C1–C9, below), then bench tests B1–B13 (issue 02)
     ├─ Icon sweep: [◀] 0x2A [▶]  builds a nav template with that icon, auto-Hold
     └─ Pod replies: last 30 lines "HH:mm:ss.SSS  30 52 45 …"  [Copy] [Clear]
```

## Implementation

### 1. Session bus (`Session.kt`)

```kotlin
/** Debug lab: 18-byte payload re-sent by PodService every tick instead of normal frames. */
val labHold = MutableStateFlow<ByteArray?>(null)
/** Debug lab: one-shot payloads (e.g. SET_TIME then let the Pod show its clock). */
val labOnce = MutableSharedFlow<ByteArray>(extraBufferCapacity = 8)
/** Debug lab: raw Pod -> phone writes, newest first. */
val podReplies = MutableStateFlow<List<String>>(emptyList())

fun logPodReply(bytes: ByteArray) {
    if (!BuildConfig.DEBUG) return
    val t = java.text.SimpleDateFormat("HH:mm:ss.SSS", java.util.Locale.US).format(java.util.Date())
    podReplies.value = (listOf("$t  ${PodProtocol.hex(bytes)}") + podReplies.value).take(Constants.LAB_REPLY_LINES)
}
```

### 2. Protocol helpers (`proto/PodProtocol.kt`, pure → unit-tested)

```kotlin
fun hex(b: ByteArray): String = b.joinToString(" ") { "%02X".format(it) }

/** "10 11 15" / "101115" -> 18-byte payload, zero-padded; null if invalid or > 18 bytes. */
fun parsePayloadHex(s: String): ByteArray? {
    val clean = s.filterNot(Char::isWhitespace)
    if (clean.isEmpty() || clean.length % 2 != 0 || clean.length > 36) return null
    val bytes = clean.chunked(2).map { it.toIntOrNull(16) ?: return null }
    return ByteArray(18).also { out -> bytes.forEachIndexed { i, v -> out[i] = v.toByte() } }
}

/** Known-good nav template (wazetripper layout) with only the icon varied: 50 m, day, no next, blank bottom. */
fun sweepPayload(icon: Int): ByteArray =
    parsePayloadHex("10 11 %02X 00 32 01 00 FF FF FF 41 FF FF FF".format(icon))!!
```

Replace the three existing `joinToString(" ") { "%02X".format(it) }` call sites (PodService debug log,
PlaygroundScreen preview) with `PodProtocol.hex()`.

### 3. PodService

- `onCreate`, next to the `pinInput` collector:
  ```kotlin
  scope.launch { Session.labOnce.collect { link.send(PodProtocol.withCrc(it)) } }
  ```
- `tick()`, **first lines** (before the call-overlay branch):
  ```kotlin
  Session.labHold.value?.let {
      link.send(PodProtocol.withCrc(it))
      lastFrameSentAt = now
      return
  }
  ```
- `onPodCommand(cmd)`: first line `Session.logPodReply(cmd)` (before the size check).

### 4. Presets (`ui/LabPresets.kt`, debug data only)

```kotlin
data class LabPreset(val id: String, val label: String, val hex: String, val hold: Boolean = true)
val LAB_PRESETS: List<LabPreset> = listOf(/* copy the table from issue 02, one entry per row */)
```

### 4b. Colour test presets (first group in `LAB_PRESETS`, all `hold = true`)

Right turn `0x15`, day. Each preset changes **one thing** compared with C1 (the Super Pod style frame).
Hold each for ~5 s, photograph the Pod, note what it shows.

| ID | Changes vs C1 | Hex (18-byte payload, CRC added by the lab) | Expect if `[10]` is the colour switch |
|---|---|---|---|
| C1 | baseline, Super Pod style, 20 m | `10 11 15 00 14 01 40 FF FF FF 41 FF FF FF` | **red** arrow |
| C2 | `[10] = F1` (Cruizer, no next turn) | `10 11 15 00 14 01 40 FF FF FF F1 FF FF FF` | black and white |
| C3 | `[10] = 11` (Cruizer, next in metres) | `10 11 15 00 14 01 40 FF FF FF 11 FF FF FF` | black and white |
| C4 | `[7-9]` = next straight `09` at 200 m (`00 C8`) | `10 11 15 00 14 01 40 09 00 C8 41 FF FF FF` | red (isolates `[8-9]`) |
| C5 | full Cruizer frame: C3 + C4 + ETA 14:30 bottom | `10 11 15 00 14 01 40 09 00 C8 11 0E 1E 00` | black and white (what you see today) |
| C6 | bottom line = ETA 14:30 (`[13] = 00`) only | `10 11 15 00 14 01 40 FF FF FF 41 0E 1E 00` | red (isolates the bottom line) |
| C7 | `[10] = 31` ("highway" in Super's code) | `10 11 15 00 14 01 40 FF FF FF 31 FF FF FF` | ? (curiosity) |
| C8 | 10 m, `[6] = 50` | `10 11 15 00 0A 01 50 FF FF FF 41 FF FF FF` | **yellow** arrow |
| C9 | 60 m, `[6] = 20` | `10 11 15 00 3C 01 20 FF FF FF 41 FF FF FF` | partial red shaft |

Read the result:

| Photos show | Meaning | Next |
|---|---|---|
| C1 red, C2/C3 B&W, C4/C6 red | `[10]` is the colour switch | Build **08** (`[10] = 0x41`), then **07** |
| C1 red, C4 B&W | the next-distance bytes `[8-9]` block it | Build **08** (`[8-9] = FF FF`), then 07 |
| C1 red, C6 B&W | the ETA bottom line blocks it | New issue: send ETA the Super Pod way, or `FF FF FF` |
| C1 B&W too | colour needs something these frames don't have | Compare with what Super Pod sends (its notes in Quicker-pod); stop and re-plan |
| C8 yellow, C9 partial red | the `[6]` ladder works on our Meteor Pod | **07** as written |

Write the photos and the result table into `docs/BENCH_RESULTS.md` (issue 02, section B0).

### 5. UI (`ui/PlaygroundScreen.kt`)

- New `RawLabCard()` composable below the existing preview card (keep the file's FilterChip/Card style).
- State: `var hexText by remember { mutableStateOf("") }`, `val payload = PodProtocol.parsePayloadHex(hexText)`;
  show the full 20-byte hex with CRC under the field, or "invalid hex" in `colorScheme.error`.
- Buttons: Hold → `Session.labHold.value = payload`; Send once → `Session.labOnce.tryEmit(payload)`;
  Release → `Session.labHold.value = null`. Disable Hold/Send when `payload == null` or Pod not connected
  (`Session.linkState`).
- Sweep row: `var sweep by remember { mutableIntStateOf(0x00) }`; ◀/▶ change it (wrap 0x00..0xFF) and set
  `Session.labHold.value = PodProtocol.sweepPayload(sweep)`.
- Replies list from `Session.podReplies.collectAsState()`, Copy uses `LocalClipboardManager` (same as SettingsScreen).
- Leaving the screen must release the Pod:
  ```kotlin
  DisposableEffect(Unit) { onDispose { Session.labHold.value = null } }
  ```

### 6. Constants (`Constants.kt`, create)

```kotlin
object Constants {
    const val LAB_REPLY_LINES = 30
}
```

## Tests (`PodProtocolTest.kt`)

- `parsePayloadHex("10 11 15")` → 18 bytes, `[0..2] = 10 11 15`, rest 0.
- `parsePayloadHex("1")`, `"zz"`, 19 bytes → `null`.
- `PodProtocol.withCrc(parsePayloadHex("30")!!)` ends with `42 8B` (also a fixture for issue 15).
- `sweepPayload(0x2A)[2] == 0x2A` and `[10] == 0x41`.

## Acceptance criteria

- [ ] Holding `10 11 41 00 00 00 00 FF 00 00 00 FF FF FF 10` keeps the compass "N" on the Pod for > 30 s (no watchdog drop).
- [ ] Colour test C1–C9 run, photos taken, result row picked and written to `docs/BENCH_RESULTS.md`.
- [ ] Release → normal behaviour resumes (clock or nav) within ~5 s.
- [ ] Leaving the Playground releases the hold.
- [ ] Pairing/resume shows Pod replies in the log (at least the `20 01` PIN-accept or `03 …` after PING_FW).
- [ ] Release build has no lab UI (Playground still gated).

## Notes

- Only one app may talk to the Pod: stop the upstream Cruizer service (its notification → Stop) before testing.
- The Pod may NACK (`02 …`) bad frames; that is useful data, log it in issue 02's results.
