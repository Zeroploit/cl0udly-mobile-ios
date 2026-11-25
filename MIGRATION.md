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

The whole UI is shared now: the screens in `commonMain` are the Android
screens ported almost line for line, with the Android-only calls swapped for
common ones (`Context` → window size, `Toast` → the shared toast host,
`java.io.File` → server calls, `SpeechRecognizer` → a no-op behind the same
visuals).

| Area | Files | Notes |
|---|---|---|
| Data | `data/Prefs.kt`, `Catalog.kt`, `Chats.kt`, `Chat.kt`, `ChatKind.kt`, `Devices.kt`, `I18n.kt`, `Memory.kt`, `MemoryIO.kt`, `Sounds.kt`, `Team.kt`, `Timeline.kt`, `Usage.kt`, `ContextUsage.kt` | Sessions, streams, catalog, team, memory, usage counters. `Usage` counts remote replies in memory; `Sounds` keeps the PCM synthesis but `play()` is quiet. |
| API | `api/OpenCodeApi.kt`, `Models.kt`, `EventHub.kt` | The REST surface and the SSE stream, on Ktor. |
| Shell | `ui/Root.kt`, `Nav.kt`, `Graph.kt`, `ServerConnect.kt` | Drawer physics, screen transitions, swipe-back, toasts, undo. Routes without a remote equivalent (Setup, local devices) fall through to a "soon" card. |
| Theme | `ui/theme/Looks.kt`, `Theme.kt`, `Icons.kt` | All ten looks with the exact palettes; fonts ship as Compose resources. |
| Components | `ui/components/*` — `CloudUi`, `Composer`, `Markdown`, `Code`, `Media`, `Files`, `FileViewer`, `UsageRing`, `MemoryGraph`, `HelperClouds`, `MascotLife`, `KimiUi`, `FloatMenu`, `Gestures`, `Pickers`, `SlashMenu`, `Dictation`, `SelectionBar`, `Thinking`, `LookThinking`, `Components`, `ChatExtras`, `Greetings`, `Wordmark`, `MenuDot`, `AccessBanner`, `MagnetSheet`, `Format` | Ported from the Android `ui/components` of the same names. The local-file halves (`OutFiles.host`, `FileCards`) stay but never resolve on a client; the remote halves (`RemoteFileCards`, the viewer over `fileContent`) are the ones that run. PDF pages, video and audio need platform players and show a quiet note for now. |
| Screens | `ui/screens/*` — `HomeScreen`, `ChatScreen`, `Messages`, `WorkSteps`, `DrawerContent`, `SideMenu`, `ConnectScreen`, `TeamScreen`, `MemoryScreen`, `LookHome`, `ProjectsScreen`, `SessionActions`, `MessageActions`, `TimelineSheet`, `VoiceMode`, `PreviewScreen` | The chat UI is the Android one: tool cards, diffs, steps, actions, voice visuals, usage ring. |

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

1. Platform players on iOS for PDF pages, video and audio in the file viewer
   (`PdfKit`, `AVPlayer` behind the shared `FileViewer` kinds).
2. Real share/save targets (the viewer's and Media's buttons keep their place
   but are quiet until a `UIActivityViewController` seam lands in `iosMain`).
3. Attachments and dictation (camera, photos, speech) — they need the iOS
   pickers and `SFSpeechRecognizer`; the composer already calls through the
   shared seams.
4. `ChatHub` in `:app` can now delegate its message/session half to `Chats` and
   `Chat`, leaving it with the Android-only extras. That would end the
   duplication instead of letting the two drift.

## OpenCode versions

The app speaks the 1.x API (`/session`, `/global/health`, `/global/event`). A
server running OpenCode **2.x** answers on `/api/…` with wrapped `{ "data": … }`
bodies and different field names, so the 1.x client does not talk to it. If the
computer runs 2.x, either run a 1.x server for the app or add a second transport
to `OpenCodeApi` (detected at connect time).
