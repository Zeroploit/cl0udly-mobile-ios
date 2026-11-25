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
| `api/EventHub.kt` | The SSE connection. It no longer decides between local and remote by itself: `start(scope, targets)` takes that decision as a flow. |
| `data/Chat.kt` | One open chat's messages and streamed parts. Deltas are batched by a coroutine on the UI dispatcher instead of `Handler`/`Looper`. |
| `data/Chats.kt` | The client half of `ChatHub`: sessions, statuses, permissions, questions, sending, aborting. The Android-only parts (team, memory, sounds, notifications, local runtime) stay in `:app`. |
| `ui/theme/Theme.kt` | The palettes of `Looks.kt` and the `Oc` object. Fonts are the same files, shipped as Compose resources. |
| `ui/theme/Icons.kt` | The `Ic` icon set, unchanged: it was already pure Compose vectors. |
| `ui/components/Kit.kt` | `Txt`, `Ico`, `CircleBtn`, `RoundBtn`, the spring `tap`, the top bar and the model pill. |
| `ui/Markdown.kt` | Replies at 18/30 sp in the reply face, code blocks, inline bold/italic/code. |
| `ui/App.kt` | Home, chat, drawer, permission and question cards, the pixel clouds and mascot. |
| `ui/Connect.kt` | The first screen: address + password, probed against `/global/health`. |

Platform seams in `shared/`:

- `SettingsStore` — key/value settings (`MemorySettingsStore` for tests).
- `PlatformInfo` — name, whether a local server is possible.
- `randomToken` — `SecureRandom` on Android/JVM, UUIDs on iOS.
- `nowMillis` — `System.currentTimeMillis()` on Android/JVM, `NSDate` on iOS.
- `nowStamp` — the "Now: …" line the agent is told; `java.time` vs `NSDateFormatter`.
- `httpEngineFactory` — OkHttp on Android/JVM, Darwin on iOS.
- `rememberMotionEnabled` — the animator scale on Android, always on elsewhere.

## Looking at the UI without a Mac

`./gradlew :shared:run` opens the shared Compose tree in a phone-sized window.
`preview.properties` decides what it shows: `server=` skips the connect form,
`session=` opens that chat, `edition=claude` switches the warm look. A small
stand-in server (`node tools/preview-server.mjs`) answers with the 1.x shapes the
client parses, so the screens can be looked at with realistic data.

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

1. The rich message parts: tool cards that expand to their output, diffs,
   images, the "steps" timeline with thought/command runs.
2. The parts of the frame the phone shows and iOS does not yet: the greeting
   pills, the mascot's moods and animations, the drawer's staging, sheets.
3. Attachments and dictation (camera, photos, speech) — they need the iOS
   pickers and `SFSpeechRecognizer`, behind the shared composer.
4. `ChatHub` in `:app` can now delegate its message/session half to `Chats` and
   `Chat`, leaving it with the Android-only extras. That would end the
   duplication instead of letting the two drift.
5. `Workspace`: 17 files write to `Graph.paths.host()`. On iOS those paths exist
   on the server, not on the phone, so they need a contract with a local and a
   remote implementation — only for features that exist on both sides.

## OpenCode versions

The app speaks the 1.x API (`/session`, `/global/health`, `/global/event`). A
server running OpenCode **2.x** answers on `/api/…` with wrapped `{ "data": … }`
bodies and different field names, so the 1.x client does not talk to it. If the
computer runs 2.x, either run a 1.x server for the app or add a second transport
to `OpenCodeApi` (detected at connect time).
