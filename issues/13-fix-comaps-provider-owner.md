# 13 · fix (security) · Verify CoMaps provider owner package

| | |
|---|---|
| Priority | P3 (only matters if CoMaps is *not* installed and a malicious app is) |
| Depends on | 00 |
| Bench test | — (CoMaps on phone if you use it) |

## Problem

`notify/ComapsSource.kt:45`:

```kotlin
fun installed(context: Context): List<String> = PACKAGES.filter {
    context.packageManager.resolveContentProvider(it + AUTHORITY_SUFFIX, 0) != null
}
```

Content-provider authorities are first-come: if CoMaps is not installed, **any** app can register
`app.comaps.provider.navigation` and also define the permission `app.comaps.permission.READ_NAVIGATION_DATA`
(as `normal`, so it is auto-granted). Cruizer would then read that app's rows as live turns.

## Fix

Require the provider to belong to the expected package.

```kotlin
private fun ownsProvider(context: Context, pkg: String): Boolean =
    context.packageManager.resolveContentProvider(pkg + AUTHORITY_SUFFIX, 0)?.packageName == pkg

fun installed(context: Context): List<String> = PACKAGES.filter { ownsProvider(context, it) }
```

And in `query()` (line ~119) skip targets that fail `ownsProvider` (covers an uninstall/replace while running):

```kotlin
if (!granted(ctx, target) || !ownsProvider(ctx, target)) return@forEach
```

Optional hardening (only if cheap): also require the permission to be declared by the same package —
`context.packageManager.getPermissionInfo(pkg + PERMISSION_SUFFIX, 0).packageName == pkg`
(wrap in `runCatching`, it throws `NameNotFoundException`).

The manifest `<queries>` already lists the CoMaps packages and authorities, so `resolveContentProvider`
returns the owner's `ProviderInfo` on Android 11+.

### Docs

`docs/AUDIT.md` → "Fixed": CoMaps provider owner check.

## Tests

`ComapsSourceTest` covers only pure mapping; `ownsProvider` needs `PackageManager`. Keep it a one-liner and
verify manually:

- [ ] With CoMaps installed + permission granted: turns still arrive (provider path).
- [ ] Code review: no `resolveContentProvider(...) != null` checks remain (`grep -n resolveContentProvider`).
