# Cruiser

A FOSS dashboard for the Royal Enfield **Tripper Pod**: pairs your phone to the
round motorcycle display over BLE and drives it with turn-by-turn navigation
from your installed nav apps (OsmAnd, CoMaps, Organic Maps, Google Maps, Waze).
No cloud, no account, no Royal Enfield code.

## How it works

- A foreground service (`app.cruiser.PodService`) stays connected to the Pod,
  mirrors the RE-app BLE behavior (client + mirror GATT server, serialized
  20-byte CRC-16/CCITT-FALSE frames), and re-renders the screen at 1 Hz — that
  refresh doubles as the Pod's watchdog feed.
- A notification listener (`app.cruiser.notify.TurnSource`) reads the
  "navigating" notifications of the selected nav apps and parses turn kind,
  distance, roundabout exits, ETA and speed cameras from the text.
- The byte-level protocol is documented in
  [`docs/PROTOCOL.md`](docs/PROTOCOL.md); it was derived independently from the
  RE app and cross-checked against [wazetripper](https://github.com/jnsdias/wazetripper)'s
  notes — see [NOTICE.md](NOTICE.md) for credits.

## Status

First working ride target: connect, pair (PIN), navigate with OsmAnd-style
notifications or CoMaps' structured provider, call overlay (optional
`READ_PHONE_STATE`), radar (best effort). Settings: night mode (day/night/auto
by sunrise–sunset), bottom-line content, 12/24 h clock, metric/imperial. The
Trips tab records a ride (GPX track + distance) and exports it to Downloads.
Debug builds add a **frame playground** screen.

Planned: manual fill-up fuel log; possible OsmAnd fork for a structured TBT
feed if notification text proves too coarse.

## Building

- Android Studio Ladybug+ or any Gradle 9.6-compatible setup; `./gradlew app:assembleDebug`.
- minSdk 31 (Android 12), Compose Material 3 with Material You dynamic theming.

## Pairing

Turn the ignition on, press *Connect*, pick the PIN the Pod shows. The Pod is
remembered; after that it reconnects silently each ride. Only one phone at a
time — the phone hosts a mirror GATT server so the Pod can reply on the
established link.

## License

Cruiser's own code: TBA (see NOTICE.md for third-party credits).
