# Credits

Cruiser is an original, independent implementation. The BLE wire protocol it
speaks was reverse-engineered from the published behavior of the hardware and
the Royal Enfield app, and cross-checked during development against the
community protocol write-up below. No Royal Enfield code, assets, or binaries
are included or redistributed.

- **wazetripper** (`jnsdias/wazetripper`) — their `docs/PROTOCOL.md` was used to
  confirm frame layout, CRC constants (handshake/ping), distance/intensity
  encodings and Pod quirks such as the ~5 s watchdog and the
  mirror-GATT-server behavior. Two of their observations were corrected after
  decompilation analysis and the corrections are documented in
  `docs/PROTOCOL.md` (SET_TIME AM/PM encoding, bottom-line byte ranges).

Other tools used during the protocol study (not shipped):
jadx (Apache-2.0) for the decompile pass.
