# Credits & Clean-Room Architecture

Cruizer is an original, independent clean-room implementation authored by **neo**.
The BLE wire protocol it speaks was reverse-engineered from first principles for
interoperability with the Royal Enfield Tripper Pod hardware. No Royal Enfield
code, assets, or binaries are included or redistributed.

- **wazetripper** (`jnsdias/wazetripper`) — credited for independent hardware
  observations that confirmed the Pod's phone-hosted mirror-GATT-server reply
  pattern and the `0x32` roundabout exit icon index offset. All frame factories,
  dynamic CRC algorithms, state engines, and Compose UI were independently
  written from scratch.
- **Gadgetbridge** (`Freeyourgadget/Gadgetbridge`, GPLv3) — the Google Maps
  largeIcon arrow matcher (`GoogleMapsIconMatcher`) is adapted from
  Gadgetbridge's icon-matching approach; the CoMaps navigation provider
  contract follows Gadgetbridge's `CoMapsNavigationReceiver`. This is why
  Cruizer is licensed GPL-3.0-or-later.

Author: neo (<site.casiopea.cruizer>)
