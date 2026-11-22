# Issues — Tripper Pod fixes & features

Backlog for this fork of Cruizer. Each item is small enough to build, test and ship on
its own. One file per item, numbered in the recommended build order.

Sources: gap analysis of Cruizer vs wazetripper, OpenTripper (OSolerHugo), spandan-kumar/OpenTripper,
Quicker-pod, PodBridge (Nov 2026), plus the security review of this repo.

## Build order

```
Phase 0  setup ─────────► 00
Phase 1  test tooling ──► 01 ──► 02 (bench session on the bike)
                                   │ results decide 07, 08, 09, 19, 20
Phase 2  India fixes ───► 03 ──► 04, 05        06
Phase 3  frame alignment ────────► 07, 08, 09   (gated by 02)
Phase 4  link + security ► 10, 11, 12, 13
Phase 5  features ──────► 14, 15, 16 ──► 17 ──► 18, 19, 20
```

## Index

| # | Type | Title | Priority | Depends on | Needs bench result (02) |
|---|---|---|---|---|---|
| [00](00-chore-dev-setup-and-app-id.md) | chore | Dev setup, own app ID, install over adb | P0 | — | — |
| [01](01-feat-frame-lab.md) | feat | Frame lab: raw frames, icon sweep, Pod reply log | P0 | 00 | — |
| [02](02-test-bench-checklist.md) | test | Bench-test session on the bike (B1–B13) | P0 | 01 | — |
| [03](03-feat-traffic-side-setting.md) | feat | Traffic-side setting (left/right, India = left) | P1 | 00 | — |
| [04](04-fix-roundabout-traffic-side.md) | fix | Roundabout icons follow traffic side | P1 | 03 | B1 (confirm) |
| [05](05-fix-uturn-traffic-side.md) | fix | U-turn icon follows traffic side | P1 | 03 | B12 (confirm) |
| [06](06-fix-keep-icons.md) | fix | Keep left/right use the soft-keep icons | P1 | 00 | B4 (confirm) |
| [07](07-fix-intensity-ladder.md) | fix | Ring intensity byte [6] ladder | P2 | 01 | **B2** |
| [08](08-fix-next-arrow-bytes-8-10.md) | fix | Next-arrow bytes [8..10] alignment | P2 | 07 | **B3** |
| [09](09-fix-12h-time-encoding.md) | fix | 12-hour SET_TIME / ETA encoding | P2 | 01 | **B5** |
| [10](10-fix-gatt-server-hardening.md) | fix | Mirror GATT server: props, response, sender check | P2 | 00 | — |
| [11](11-fix-first-write-delay.md) | fix | 200 ms pause after service discovery | P3 | 00 | — |
| [12](12-fix-nav-package-exact-match.md) | fix (security) | Exact package match for nav notifications | P2 | 00 | — |
| [13](13-fix-comaps-provider-owner.md) | fix (security) | Verify CoMaps provider owner package | P3 | 00 | — |
| [14](14-fix-reroute-screen.md) | fix | Show "recalculating" icon on reroute | P2 | 00 | B10 (confirm) |
| [15](15-feat-pod-info.md) | feat | Read Pod firmware version + serial | P3 | 01 | B11 |
| [16](16-feat-idle-compass.md) | feat | Compass screen when not navigating | P2 | 00 | B9 (confirm) |
| [17](17-feat-phone-battery-alert.md) | feat | Phone low-battery icon on the Pod | P3 | 14 | B10 (confirm) |
| [18](18-feat-no-data-alert.md) | feat | "No mobile data" icon on the Pod | P3 | 17 | B10 (confirm) |
| [19](19-feat-time-left-bottom-line.md) | feat (experimental) | Time-left bottom-line mode | P4 | 01 | **B6** |
| [20](20-feat-idle-speedometer.md) | feat (experimental) | Speedometer idle screen | P4 | 16 | **B13** |

"Needs bench result": **bold** = do not merge until the bench test picks the answer;
"(confirm)" = evidence is already hardware-backed elsewhere, re-check on our Pod.

## Rules for every issue

From `AGENTS.md` (project) and the owner's global instructions:

- `docs/PROTOCOL.md` is authoritative. **Any change to frame bytes or icon codes must update it in the
  same commit**, with its confidence level (Confirmed / Derived / Open) and the bench-test ID that proved it.
- Clean-room: never copy code from decompiled apps (Quicker-pod's `smali-reference/`, `captures/`).
  Use facts only, and prefer our own bench results.
- Manual DI only: `Session` is the bus between UI, `PodService`, `TurnSource`, `ComapsSource`.
- No magic numbers: new thresholds/timings go in `app/src/main/kotlin/site/casiopea/cruizer/Constants.kt`
  (`object Constants`, create on first use). Icon codes stay in `proto/Icons.kt` (that enum *is* the table).
- New pure logic gets a JUnit4 test in `app/src/test/kotlin/site/casiopea/cruizer/` (style: `PodProtocolTest`).
  Pure objects must not touch Android classes at init time.
- Keep code minimal: data classes / enums for structured data, collection ops over loops,
  `try/catch` only around Android/BLE calls that can throw.
- Minimal logging: debug-only (`if (BuildConfig.DEBUG)`), tag `CruizerNav`.
- New settings: add a field to `Session.Settings`, a `KEY_*` const, load in `Session.init`, save in
  `Session.updateSettings`, a chip row in `ui/SettingsScreen.kt`. **Only append to existing enums**
  (ordinals are persisted).
- Bump `versionCode` + `versionName` in `app/build.gradle.kts` on every build you install. One commit per issue.

## Definition of done (all issues)

1. `./gradlew app:testDebugUnitTest --no-daemon` passes.
2. `./gradlew app:assembleRelease --no-daemon` builds.
3. Installed on the S25 Ultra, the issue's acceptance criteria are checked on the bike (parked).
4. `docs/PROTOCOL.md` / `docs/AUDIT.md` updated where the issue says so.

## Not planned (and why)

| Idea | Why not |
|---|---|
| Street names, text, SMS, music titles | No source found any text command; firmware draws fixed glyphs only |
| Lane guidance, speed limits, custom images | Same: no field or bitmap command exists |
| Brightness control | Only the per-frame intensity ring (#07) and night bit exist |
| Speed-camera / radar alerts | Protocol trick exists (`[7]=0x3C`), but there is no free camera database for India; revisit if one appears |
| Firmware update (OTA) | Opcodes only partly known; a mistake can brick the Pod |
| Tripper Dash support | Different device and protocol |
