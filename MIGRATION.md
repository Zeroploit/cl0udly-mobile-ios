# Porting cl0udly to iOS

The Android app is the source; iOS is the target. It is one Kotlin codebase with
the UI in Compose Multiplatform, so the screens are written once.

## Why iOS is a client only

The Android app runs OpenCode locally: a proot Ubuntu rootfs, an `opencode
serve` process, foreground services, executables from the app's data directory.
iOS allows none of that — no JIT, no unsigned binaries, no long-lived background
process. So on iOS the phone connects to an `opencode serve` running on a
computer, and the parts of the app that manage a local server are Android-only
by design.

## Layout

| Path | What it is |
|---|---|
| `app/` | The Android application. Unchanged and shippable; it now depends on `:shared`. |
| `shared/` | Kotlin Multiplatform library: shared data, networking and UI. |
| `iosApp/` | The Xcode project that hosts `MainViewController()` from `:shared`. |

`:app` did **not** depend on `:shared` until commit `502257d`; both modules were
being compiled separately, so "the shared module is used by the app" was not
true. Keep that link in mind: every file moved to `commonMain` reaches the
Android app through it.

## What is shared today

| Moved | Notes |
|---|---|
| `api/Models.kt` | `Session`, `MessageInfo`, `Part`, serializers, parsing helpers. |
| `data/Prefs.kt` | Over `SettingsStore`: `SharedPreferences` on Android, `NSUserDefaults` on iOS. Keys unchanged. |
| `api/OpenCodeApi.kt` | The whole REST surface, on Ktor. The endpoint arrives as a lambda instead of being read from `Graph`. |
| SSE | `OpenCodeApi.streamLines`; the hub in `:app` parses the lines. |
| `ui/Connect.kt` | The iOS start: address + password, then the server's session list. |

Platform seams in `shared/`:

- `SettingsStore` — key/value settings (`MemorySettingsStore` for tests).
- `PlatformInfo` — name, whether a local server is possible.
- `randomToken` — `SecureRandom` on Android/JVM, UUIDs on iOS.
- `nowMillis` — `System.currentTimeMillis()` on Android/JVM, `NSDate` on iOS.
- `httpEngineFactory` — OkHttp on Android/JVM, Darwin on iOS.

## Verified

- `./gradlew :shared:compileKotlinJvm :shared:jvmTest` — 46 tests, no Android SDK
  and no Mac needed. This is the fast gate before pushing.
- CI `Android` job — `:app:assembleDebug` plus `:shared:jvmTest` on a clean
  runner. Green from `308bcd7` on. It is what catches `:app` breakage, since
  there is no Android SDK on the development machine.
- `builder ios build --unsigned` — Gradle → Compose framework → Xcode → IPA.
  Green from `30b848d` (the first build with real shared code in it).

## Known porting friction

- Kotlin refuses to smart cast a property declared in another module. Moving a
  model to `commonMain` turns `x.tokens != null && x.tokens.sum > 0` into a
  compile error (`ContextUsage.kt` was the first case).
- A trailing lambda fills the **last** parameter. `OpenCodeApi(endpoint = { … })`
  must stay named now that the engine factory follows it.
- `:app` cannot be compiled locally: no Android SDK on this machine, and its
  configuration requires one, hence `--configure-on-demand`.

## Next

1. `Workspace`: 17 files write to `Graph.paths.host()` (`/root/work`,
   `/root/memory`, `/root/.config/opencode`). On iOS those paths exist on the
   server, not on the phone, so they need a contract with a local and a remote
   implementation.
2. `Graph` carries an `Application`; nothing in the data layer compiles in
   `commonMain` until it does not.
3. `data/Chat.kt` batches SSE deltas with `android.os.Handler`/`Looper` — a
   hidden Android dependency in the data layer.
4. The chat itself: `ChatHub`, the session screen and the composer are the
   remaining large pieces.
