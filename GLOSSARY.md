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
- **Turn source** — wherever Cruiser gets its turn-by-turn updates: today the notification-listener parser (`TurnSource`), later a possible OsmAnd/CoMaps fork.
- **Frame playground** — debug-only screen that composes an arbitrary nav frame, shows its hex, and sends it live so the Pod's actual rendering can be verified against the icon table.
- **Night-mode policy** — day/night/auto cycle chosen in settings; AUTO estimates sunrise/sunset from a coarse last-known location instead of tracking position.
- **Route ownership** — the nav source that last posted live route data owns the ride; only that source may end it, so a browse screen in another app cannot tear down a live route.
- **Ride log** — the live trip clock and distance shown on the Home tab. The clock follows the route; GPS only enriches distance and speed.
- **Fill-up log** — phase-2 manual fuel entries (price per litre, km/L) with GPX export of the period's trips.
