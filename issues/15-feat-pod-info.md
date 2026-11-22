# 15 · feat · Read Pod firmware version + serial number

| | |
|---|---|
| Priority | P3 |
| Depends on | 01 (reply log to confirm formats) |
| Bench test | **B11** (confirm reply layouts before building UI) |

## Why

Useful for support/debugging ("which firmware does my Pod run?") and for keying protocol quirks to a
firmware version later. The Pod already answers; `PodService.onPodCommand` ignores it
("0x03 = Pod firmware/version, 0x30 = serial — not needed").

## Protocol (facts from Quicker-pod notes; confirm with B11)

| Direction | Bytes | Meaning | Evidence |
|---|---|---|---|
| phone → Pod | `03 00…` (`PING_FW`, CRC `45 D9`) | already sent as keepalive | hardware |
| Pod → phone | `03 MM mm …` | firmware, BCD nibbles: `12 10` → "12.10" | official app logcat |
| phone → Pod | `30 00…` (CRC `42 8B`) | serial query (official app sends it ×2 after auth) | official app logcat |
| Pod → phone | `30 s1..s7 …` | ASCII serial in `[1..7]`, stops at `00` | decompiled Super (facts) |

## Implementation

### 1. `proto/PodProtocol.kt` (pure)

```kotlin
val SERIAL_QUERY = frame { it[0] = 0x30 }  // 30 … 42 8B

data class PodInfo(val firmware: String? = null, val serial: String? = null)

/** Fold one Pod reply into [info]; unknown replies leave it unchanged. */
fun parseInfo(info: PodInfo, r: ByteArray): PodInfo = when {
    r.size >= 3 && r[0] == 0x03.toByte() ->
        info.copy(firmware = "${bcd(r[1])}.${"%02d".format(bcd(r[2]))}")
    r.size >= 2 && r[0] == 0x30.toByte() ->
        info.copy(serial = r.drop(1).take(7).takeWhile { it != 0.toByte() }
            .map { it.toInt().toChar() }.filter { it in ' '..'~' }.joinToString("").ifEmpty { null })
    else -> info
}

private fun bcd(b: Byte) = ((b.toInt() shr 4) and 0x0F) * 10 + (b.toInt() and 0x0F)
```

(Adjust `"%02d"` vs plain minor once B11 shows a real reply.)

### 2. `Session.kt`

```kotlin
val podInfo = MutableStateFlow(PodProtocol.PodInfo())
```
Reset to `PodInfo()` when a *different* Pod MAC connects (in `PodService.observe` CONNECTED branch).

### 3. `PodService.kt`

- `onPodCommand`: first line `Session.podInfo.value = PodProtocol.parseInfo(Session.podInfo.value, cmd)`.
- After a successful handshake (resume branch after `sendSetTime()`, and in the `20 01` PIN-accepted branch):
  `link.send(PodProtocol.SERIAL_QUERY)` once per connection (guard with a `serialAsked` flag reset on DISCONNECTED).
  Firmware arrives on its own as the reply to the keepalive `PING_FW`.

### 4. UI

`ui/PodScreen.kt` (Home tab, connected card) or the saved-bike sheet: a small line
`Firmware 12.10 · Serial RE1234X`, hidden while both are null. Use existing typography (`bodySmall`,
`onSurfaceVariant`).

### 5. Docs

`docs/PROTOCOL.md`: new §"Pod replies" table (`02` NACK, `03` firmware, `10` nav ack, `20` PIN result,
`30` serial, `50` time ack) with confidence per row; close open question 1 (replies come via the GATT server).

## Tests (`PodProtocolTest`)

- `SERIAL_QUERY` CRC == `0x428B`.
- `parseInfo(PodInfo(), bytes("03 12 10"))` → firmware `"12.10"`.
- `parseInfo(PodInfo(), bytes("30 52 45 31 32 33 00"))` → serial `"RE123"`.
- Unknown `bytes("10 00")` → unchanged.

## Acceptance criteria

- [ ] B11 reply lines recorded; parser matches them.
- [ ] Home tab shows firmware + serial within ~3 s of connecting.
- [ ] No extra traffic after the first query (one `30` per connection).
