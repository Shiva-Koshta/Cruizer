# 11 · fix · 200 ms pause after service discovery

| | |
|---|---|
| Priority | P3 (only if drops right after connect are seen; cheap insurance) |
| Depends on | 00 |
| Bench test | regression (pair + resume ×5) |

## Problem

`PodLink.onServicesDiscovered` sets `LinkState.CONNECTED` immediately; `PodService.observe()` reacts by
calling `handshake()`, so the first write goes out within milliseconds of discovery (only the optional
CCCD write sits in between).

Every other implementation waits: wazetripper §1 "Wait ~200 ms between service discovery and the first
write (writing too fast makes the Pod drop the link)" (observed); OpenTripper 200–300 ms; Super 200 ms.

## Implementation

### `Constants.kt`

```kotlin
const val POST_DISCOVERY_DELAY_MS = 200L
```

### `ble/PodLink.kt`

Delay the transition to `CONNECTED` (that is what triggers the handshake), not the queue:

```kotlin
override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
    if (client !== g) return
    val c = g.services.firstOrNull { it.uuid == serviceUuid.uuid }
        ?.characteristics?.firstOrNull { it.uuid == charUuid }
        ?: return disconnect()
    char = c
    // ...CCCD block unchanged...
    handler.postDelayed({ if (client === g) _state.value = LinkState.CONNECTED }, Constants.POST_DISCOVERY_DELAY_MS)
}
```

- Add `private val handler = android.os.Handler(android.os.Looper.getMainLooper())` to `PodLink`.
- In `reset()`: `handler.removeCallbacksAndMessages(null)` so a stale post cannot flip a dead link to CONNECTED.
- The `client === g` guard inside the runnable protects against a reconnect in the 200 ms window.

### Docs

`docs/PROTOCOL.md` §1: add "200 ms after discovery before the first write (wazetripper, observed)".

## Acceptance criteria

- [ ] 5 × resume (ignition off/on) and 1 × new pairing succeed.
- [ ] Debug log: `link state → CONNECTED` appears ~200 ms after discovery.
