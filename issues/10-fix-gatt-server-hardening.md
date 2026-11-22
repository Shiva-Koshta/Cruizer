# 10 · fix · Mirror GATT server: properties, response, sender check

| | |
|---|---|
| Priority | P2 (stability + security finding #3) |
| Depends on | 00 |
| Bench test | regression only (pair + resume + 10 min ride) |

## Problems

All in `ble/PodLink.kt`.

| # | Where | Now | Fix |
|---|---|---|---|
| 1 | `openServer()` ~line 233 | `BluetoothGattCharacteristic(charUuid, 5, 16)`; comment says "READ\|WRITE" but `5` = `BROADCAST(0x01) \| WRITE_NO_RESPONSE(0x04)` | `PROPERTY_WRITE \| PROPERTY_WRITE_NO_RESPONSE` (`0x0C`), `PERMISSION_WRITE` — what the official app, Super, wazetripper and OpenTripper host |
| 2 | `serverCallback.onCharacteristicWriteRequest` ~line 141 | any BLE device that connects to the phone's server can inject a "PIN accepted/rejected" reply | accept writes only from the Pod we are connected to |
| 3 | same | response echoes `value` | send `GATT_SUCCESS` with no value (spec: write responses carry none) |

Evidence: Quicker-pod `startup-handshake.md` (official app logcat: server created before connect, char props
`0x0C`, perm `0x10`); wazetripper PROTOCOL.md §1 ("properties WRITE/WRITE_NO_RESPONSE"); OpenTripper §2.

## Implementation (`ble/PodLink.kt`)

### 1. Server characteristic

```kotlin
svc.addCharacteristic(
    BluetoothGattCharacteristic(
        charUuid,
        BluetoothGattCharacteristic.PROPERTY_WRITE or BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE,
        BluetoothGattCharacteristic.PERMISSION_WRITE,
    )
)
```
Fix the comment above it.

### 2. Sender check + response

```kotlin
override fun onCharacteristicWriteRequest(
    device: BluetoothDevice, requestId: Int, characteristic: BluetoothGattCharacteristic,
    preparedWrite: Boolean, responseNeeded: Boolean, offset: Int, value: ByteArray,
) {
    val fromPod = device.address.equals(client?.device?.address, ignoreCase = true)
    if (responseNeeded) server?.sendResponse(
        device, requestId,
        if (fromPod) BluetoothGatt.GATT_SUCCESS else BluetoothGatt.GATT_WRITE_NOT_PERMITTED,
        offset, null,
    )
    if (fromPod) onCommand?.invoke(value)
    else if (BuildConfig.DEBUG) Log.i("CruizerNav", "ignored server write from ${device.address}")
}
```

`client?.device` is the connected Pod (set in `connect()`), so no extra state is needed. During pairing
the Pod connects through our client first, so its address is known before its first reply.
Import `site.casiopea.cruizer.BuildConfig`.

### 3. Server-before-connect (verify only)

`PodService.onCreate` already calls `link.openServer()` before the first `scanOrConnect`, and
`stateWatch` re-opens it on `STATE_ON`. No change; add a one-line comment in `openServer()` that it
must run before `connectGatt` (Pod drops in 1–2 s without it — OpenTripper §2).

### 4. Docs

- `docs/PROTOCOL.md` §1 "GATT server" row: props `WRITE|WRITE_NO_RESPONSE (0x0C)`, perm `WRITE`; replies
  arrive only here (the Pod's own characteristic has no NOTIFY — answers open question 1).
- `docs/AUDIT.md`: add under "Fixed": server sender check.

## Tests

Hardware-only (AGENTS.md: BLE has no unit tests). Regression checklist:

- [ ] New pairing: PIN shown, typed, accepted (debug log shows the `20 01` reply via the server).
- [ ] Resume after ignition cycle works.
- [ ] 10-minute ride: no new disconnects compared with the upstream app.
- [ ] Pairing a second phone / BLE scanner app (nRF Connect) writing to the phone's service → ignored (debug log line).
