# Glossary

Decisions that crystallize live here. No implementation details.

- **Tripper Pod** — Royal Enfield's round Bluetooth navigation display (Meteor 350 etc.), BLE. The scope of this project. Seen on-air as a GATT server exposing an NXP wireless-UART service.
- **Tripper Dash** — newer instrument-cluster navigation device (Hunter 350 etc.). A different firmware/protocol generation. Explicitly out of scope for now.
- **Nav frame** (`CMD_NAVIGATE`) — the 20-byte CRC-16 packet that carries one screen of turn-by-turn state (maneuver icon, distances, next maneuver, bottom line) to the Pod.
- **GATT server mirror** — the phone also hosting a GATT server with the same service/characteristic UUIDs so the Pod can write replies over the established link.
- **CCCD** — the notification-enable descriptor; whether the Pod's own characteristic notifies is an open protocol question.
- **Watchdog** — the Pod drops the BLE link ~5 s after receiving nothing but keepalives; nav/idle screens must be refreshed ~1 Hz, or the link dies by design.
- **TBT feed** — turn-by-turn data (current step, remaining steps, distances, ETA). In the RE app it comes from a proprietary Google SDK over a Messenger; in our app it comes from our own OSM routing/notification parsing.
- **Maneuver icon byte** — the frame's `mgj`-style enum value naming which big arrow the Pod draws.
- **Bottom line** — the frame's `[11..13]` fields; either a time (ETA) or a distance.
