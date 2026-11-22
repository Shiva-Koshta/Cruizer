# 03 · feat · Traffic-side setting (left / right)

| | |
|---|---|
| Priority | P1 |
| Depends on | 00 |
| Unblocks | 04 (roundabouts), 05 (U-turn) |
| Bench test | — |

## Why

Roundabout direction and U-turn direction depend on which side of the road traffic drives on.
India (and UK, Japan, Australia …) drive on the **left** → clockwise roundabouts, clockwise U-turns.
Cruizer hard-codes right-hand traffic. Navigation text ("take the 2nd exit", "make a U-turn") does not
carry the direction, so the app needs a setting.

## Design

```
enum TrafficSide { LEFT, RIGHT }           // proto/ — pure, used by the icon mapping
Settings.trafficSide  default = from phone region (IN → LEFT)
Settings tab: "Traffic drives on"  [left] [right]
```

## Implementation

### 1. `proto/TrafficSide.kt` (new, pure)

```kotlin
package site.casiopea.cruizer.proto

enum class TrafficSide {
    LEFT, RIGHT;

    companion object {
        /** ISO-3166 alpha-2 codes of left-hand-traffic countries. */
        private val LEFT_HAND = setOf(
            "IN", "GB", "IE", "JP", "AU", "NZ", "ZA", "PK", "BD", "LK", "NP", "BT",
            "MY", "SG", "TH", "ID", "HK", "MO", "KE", "TZ", "UG", "ZM", "ZW", "MZ",
            "BW", "NA", "MU", "MT", "CY", "JM", "TT", "BS", "BB", "GY", "SR", "FJ",
        )

        fun forCountry(iso2: String?): TrafficSide =
            if (iso2?.uppercase() in LEFT_HAND) LEFT else RIGHT
    }
}
```

(Keeping the list next to the enum follows the repo's "table lives with its type" style, like `Icons`.)

### 2. `Session.kt`

- Const: `private const val KEY_TRAFFIC_SIDE = "traffic_side"`.
- `Settings` data class: add `val trafficSide: TrafficSide = TrafficSide.RIGHT` (placeholder; real default below).
- `init()` load block:
  ```kotlin
  trafficSide = p[intPreferencesKey(KEY_TRAFFIC_SIDE)]
      ?.let { TrafficSide.entries.getOrNull(it) }
      ?: TrafficSide.forCountry(regionOf(context)),
  ```
  with a private helper
  ```kotlin
  /** SIM country first (where the bike is), then the phone locale. */
  private fun regionOf(c: Context): String? =
      (c.getSystemService(Context.TELEPHONY_SERVICE) as? android.telephony.TelephonyManager)
          ?.simCountryIso?.takeIf { it.isNotBlank() }
          ?: java.util.Locale.getDefault().country
  ```
  (`simCountryIso` needs no permission.)
- `updateSettings` edit block: `it[intPreferencesKey(KEY_TRAFFIC_SIDE)] = next.trafficSide.ordinal`.

### 3. `ui/SettingsScreen.kt`

- Next to `NIGHT_OPTIONS` (line ~53): `private val TRAFFIC_OPTIONS = listOf(TrafficSide.LEFT to "left", TrafficSide.RIGHT to "right")`.
- New `Column` after the "Bottom line" block, same FilterChip pattern as "Pod night mode":
  title **"Traffic drives on"**, helper text: *"Sets roundabout and U-turn arrows. India: left."*

### 4. Docs

- `GLOSSARY.md`: add **Traffic side** — "which side of the road traffic drives on; decides roundabout and U-turn icon direction."

## Tests (`TrafficSideTest.kt`, new)

- `forCountry("IN") == LEFT`, `forCountry("in") == LEFT`, `forCountry("US") == RIGHT`,
  `forCountry(null) == RIGHT`, `forCountry("") == RIGHT`.

## Acceptance criteria

- [ ] Fresh install on an Indian SIM/locale → setting shows **left**.
- [ ] Changing it persists across app restart.
- [ ] No behaviour change yet (consumers come in 04/05).
