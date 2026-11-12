# AGENTS.md

FOSS Android (Kotlin/Compose) app driving a Royal Enfield **Tripper Pod** over
BLE with turn-by-turn data parsed from nav apps. One rider, one phone.

## Commands

```sh
./gradlew app:assembleRelease app:testDebugUnitTest --no-daemon  # release build (AOT speed compiled, 120Hz smooth)
./gradlew app:assembleDebug app:testDebugUnitTest --no-daemon    # debug build
./gradlew app:testDebugUnitTest --tests "site.casiopea.cruizer.NavTextParserTest" --no-daemon
```

- No system Android SDK: it lives in `./android-sdk/` (gitignored). `local.properties` is gitignored too.
- APK output: `app/build/outputs/apk/release/app-release.apk` (debug-signed) or `app/build/outputs/apk/debug/app-debug.apk`.
- All tests are pure JVM (JUnit 4) under `app/src/test` — parsers and protocol only. Hardware/BLE has no tests.

## Hard constraints

- `docs/PROTOCOL.md` is the authoritative frame layout (18 payload + CRC-16/CCITT-FALSE).
  Never change frame bytes, handshake constants or icon codes without consulting it; they are
  hardware-verified. Icons marked "Likely" are hardware-observed, never canonically sent by RE.
- **Clean-room**: no RE app code/assets in git (`analysis/` and `*.apks` stay untracked). Credit wazetripper in `NOTICE.md`.
- FOSS-only deps, no cloud. **Manual DI only** — `Session` (app-wide StateFlows/DataStore) is the
  bus between UI, `PodService`, `TurnSource` and `ComapsSource`; no Hilt, no DI framework.
- AGP 9.5 has Kotlin built in: **the `kotlin-android` plugin is banned**. Java 17. material3 pinned to 1.5.0-rc01.
- Commit after every change. **Bump `versionCode`+`versionName` on every build** the user will install.
- Do not launch emulators (user asked to keep none; the AVD was deleted).

## Architecture in three sources

- `PodService` — foreground service; owns BLE (`PodLink`: GATT client + mirror GATT server),
  1 Hz tick renders `Session` state into frames, PING watchdog, self-stops after the
  "Disconnect timeout" setting (default 60 min, 0 = first tick after a real disconnect)
  ("Connect" button revives). Pairing = PIN handshake (0x20 01).
- `TurnSource` — NotificationListenerService over nav apps (OsmAnd, Organic Maps, GMaps, Waze).
  Text parsing lives in pure `NavTextParser`. Quirks: Maps updates its notification at most
  ~once/min, carries the turn in `title` + bottom line in `subText`; "Guiding to…" and
  "Starting navigation…" parse as Idle; `Session.navActive` holds the route-started icon
  between nav start and the first instruction.
- `ComapsSource` — CoMaps' structured `content://<flavour>.provider.navigation/live` provider
  (needs a custom runtime permission, `<flavour>.permission.READ_NAVIGATION_DATA`, requested in
  onboarding). CoMaps is provider-primary with a notification text fallback, so it does stay
  in `TurnSource.NAV_PACKAGES`; `tryParse` ignores its notifications while the provider is granted.

## Debug & device quirks

- Log tag `CruiserNav` (debug builds) logs capture lines for every nav notification,
  turn posts, skipped ticks with link state, and full nav-frame hex. A quiet
  `adb logcat -s CruiserNav` usually means the self-stop fired, not missing logs.
- `Session.recentNotifs` feeds the Settings-tab debug card ("Last nav notifications", copyable).
- User's phone is reachable over adb (`adb devices` — the port/serial changes). Installing
  resets the notification-listener grant; re-add via
  `settings put secure enabled_notification_listeners` appending
  `:site.casiopea.cruizer/site.casiopea.cruizer.notify.TurnSource`.
- GrapheneOS auto-kills Bluetooth: `PodLink` watches adapter state and re-opens the GATT
  server on STATE_ON; a stale remembered MAC re-scans after 3 failed direct connects.
- The Tripper Pod only advertises ~30 s after ignition-on.
- **Compose Debug vs. Release performance**: In `debug` builds, Android ART disables AOT compilation (`status=verify`) and Compose injects per-composable debug tracing. On 120Hz displays and hardened operating systems like GrapheneOS (`hardened_malloc`), this inflates frame evaluation from ~1ms to 25ms–35ms, resulting in perceived frame rate capping at ~20–30 fps. Always test UI smoothness using `./gradlew app:assembleRelease` (which is debug-signed and AOT compiled with `status=speed`).

## Conventions

- Kotlin style of the repo: small `object`s + data classes, flows over callbacks,
  `Session.*` mutation helpers like `postTurn()`. New pure logic gets a JUnit4 test
  (see `NavTextParserTest` fixtures style); Android-dependent state (e.g. `ComapsSource`)
  must not initialize Android classes at object init time or JVM tests break.
