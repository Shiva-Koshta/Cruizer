# 00 · chore · Dev setup, own app ID, install over adb

| | |
|---|---|
| Priority | P0 (blocks everything) |
| Depends on | — |
| Bench test | — |

## Why

- Nothing in this repo can be verified until we can build and install our own APK.
- Our build has the same `applicationId` (`site.casiopea.cruizer`) as the upstream release but a
  different signing key, so Android refuses to install it over the upstream app. A distinct ID lets
  both live side by side (upstream = known-good fallback on the bike).
- Sideloading through the browser hits Play Protect's fraud block (notification listener +
  accessibility). `adb install` avoids it.

## Scope

1. Local toolchain.
2. New `applicationId` + app label (code package/namespace stays `site.casiopea.cruizer`).
3. adb install + permission re-grant workflow.

Out of scope: renaming Kotlin packages, CI signing (fork has no release secrets; CI builds unsigned).

## Steps

### 1. Toolchain (macOS)

```sh
brew install openjdk@17 android-platform-tools
# Android SDK into ./android-sdk (gitignored, per AGENTS.md)
brew install --cask android-commandlinetools
export ANDROID_HOME="$PWD/android-sdk"
sdkmanager --sdk_root="$ANDROID_HOME" "platform-tools" "platforms;android-37" "build-tools;37.0.0"
yes | sdkmanager --sdk_root="$ANDROID_HOME" --licenses
echo "sdk.dir=$ANDROID_HOME" > local.properties
```

(If `compileSdk = 37` build-tools version differs, take the newest `build-tools;37.*` that `sdkmanager --list` shows.)

### 2. Own application ID

`app/build.gradle.kts`, `defaultConfig`:

```kotlin
applicationId = "dev.tripperpod.nav"   // pick once, never change again (it's the install identity)
versionCode = 51                       // > upstream 50
versionName = "1.0.2-fork.1"
```

Keep `namespace = "site.casiopea.cruizer"` — R/BuildConfig classes and all source packages stay as is.

`app/src/main/res/values/strings.xml`: change `app_name` (e.g. `Cruizer Fork`) so the two launchers differ.

Check for hard-coded package strings that must follow the *application* id, not the namespace:

```sh
grep -rn '"site.casiopea.cruizer' app/src/main
```

- `ComponentName(context, X::class.java)` uses the runtime package → fine.
- The manifest permission `site.casiopea.cruizer.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` is generated
  by AndroidX from the application id → fine.

Update `AGENTS.md` "Debug & device quirks": the listener component becomes
`dev.tripperpod.nav/site.casiopea.cruizer.notify.TurnSource`.

### 3. Phone setup (once)

Settings → About phone → Software information → tap **Build number** ×7 → Developer options →
**USB debugging** on. Plug in, accept the RSA prompt.

### 4. Build + install loop

```sh
./gradlew app:testDebugUnitTest app:assembleDebug --no-daemon
adb install -r app/build/outputs/apk/debug/app-debug.apk
# re-grant notification access (install resets it):
adb shell cmd notification allow_listener dev.tripperpod.nav/site.casiopea.cruizer.notify.TurnSource
```

Debug build = Frame playground/lab visible (issue 01). For ride-quality UI use `assembleRelease`
(debug-keystore signed locally).

## Acceptance criteria

- [ ] `./gradlew app:testDebugUnitTest` green on a clean checkout.
- [ ] Debug APK installs over adb **next to** the upstream Cruizer app (two launcher icons).
- [ ] Fork app pairs with the Pod and shows a Google Maps turn (smoke test).
- [ ] `AGENTS.md` updated with the new component name.
