# Cruizer

<p align="center">
  <img src="docs/screenshots/home.png" width="270" alt="Cruizer home screen" />
  &nbsp;&nbsp;
  <img src="docs/screenshots/settings.png" width="270" alt="Cruizer settings screen" />
</p>

Cruizer is a free, open-source Android app that drives the round
**Royal Enfield Tripper Pod** on your motorcycle's dial with turn-by-turn
directions — without Royal Enfield's own app, an account, or an internet
connection. Your navigation app (OsmAnd, Organic Maps, CoMaps, Google Maps,
Waze) already knows where to turn; Cruizer relays those directions to the
Pod's screen.

## What you get

- Turn arrows on the dial: left/right, slight, sharp, U-turn, roundabouts
  with the exit number.
- Distance to the turn counting down; the bottom line can show remaining
  route distance, your arrival time, or alternate between them.
- Live ride stats in the app: time, distance, speeds, an estimated fuel
  figure (GPS optional; the clock works without location permission).
- Multiple saved bikes (engine size, economy) for the fuel estimate.
- Day/night mode for the Pod that follows the sun's position, or manual.

## ⚠️ Status: early, expect bugs

Cruizer works, but it is **not stable software yet**. It speaks a
reverse-engineered protocol and reads other apps' notifications — corner
cases will misbehave, and significant bugs are likely. Verify it while
parked and be ready to ignore the Pod if the screen goes wrong mid-ride.

**AI notice:** this code was written with substantial assistance from AI
tools (LLM-based coding agents) and reviewed by a human. Read what it does,
not just what it claims.

## How it works

1. Turn the ignition on. The Pod advertises over Bluetooth for about
   30 seconds.
2. Press **Connect** in Cruizer. The Pod shows a 6-digit PIN — type it in
   once and the phone is remembered for later rides.
3. Start navigation in your app. Cruizer reads its turn instructions (from
   the app's own navigation data where available, like CoMaps, or from its
   navigation notifications/screen otherwise) and sends them to the Pod
   about once per second, which also keeps the link's keep-alive heartbeat
   fed.

Nothing leaves your phone: no cloud, no account, no tracking.

## Supported navigation apps

| App | How Cruizer reads turns |
|---|---|
| CoMaps | Structured provider data (exact maneuvers, exits, distances) |
| OsmAnd / Organic Maps / Google Maps / Waze | Navigation notifications |

Google Maps updates its notification only about once a minute, so Cruizer
can additionally read Maps' screen directly (optional, enabled in Android's
accessibility settings; the app shows a banner when it applies).

## Requirements

- An Android 11+ phone with Bluetooth (Android 11 needs Location on to find the Pod).
- A Royal Enfield Tripper Pod (Meteor 350 etc.). The newer Tripper Dash
  cluster (Hunter 350 etc.) is a different device and is **not** supported
  yet.
- Optional: location permission for the ride distance/speed and the day/
  night mode.

## Installing

Grab an APK from [Releases](../../releases) and install it, or build it
yourself:

```sh
./gradlew app:assembleRelease   # APK at app/build/outputs/apk/release/
```

First launch walks you through the permissions (Bluetooth, notifications).
Then press **Connect**, turn the ignition on, and ride.

## Credits

The Bluetooth protocol was worked out from the hardware, cross-checked
against [wazetripper](https://github.com/jnsdias/wazetripper)'s notes; the
Maps icon matcher is adapted from
[Gadgetbridge](https://codeberg.org/Freeyourgadget/Gadgetbridge). Full
details in [NOTICE.md](NOTICE.md).

## License

Cruizer is free software: licensed under the
**GNU General Public License v3 or later** ([LICENSE](LICENSE)). You may
use, study, change and redistribute it; derivative works must stay free
under the same terms.

**No warranty, no liability.** The software is provided "as is", without
warranty of any kind. Never interact with your phone while riding; the
authors are not liable for anything that happens to you, your bike, or
your Pod while using this app. Use it at your own risk.
