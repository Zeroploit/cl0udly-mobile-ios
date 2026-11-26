# Installing the iOS build on an iPhone

The IPA is unsigned: it is built with `builder ios build --unsigned`, which skips
code signing entirely. iOS refuses an unsigned app, so something has to sign it
with an Apple identity before it can run.

## Which build to use

The current build is `dist/cl0udly-22a72550.ipa` (17.6 MB, commit `6a418f6`).
It carries every screen the Android app has; only the sections that need a
server running on the phone itself are left out.

Build with `"ios": { "configuration": "Release" }` in `builder.json` when the
IPA is going to be signed by hand:

- The Debug configuration produces `cl0udly` plus a separate
  `cl0udly.debug.dylib` (Xcode's "debug dylib"). Third-party re-signers often
  miss the nested dylib and the app dies on launch.
- Release is a single binary, smaller and faster to start.

## Free Apple ID (7 days, 3 apps)

No developer program needed.

1. Install [Sideloadly](https://sideloadly.io) and iTunes (or iCloud) from
   Apple's site — the Microsoft Store versions do not ship the drivers.
2. On the iPhone: **Settings → Privacy & Security → Developer Mode → On**, then
   restart. The toggle only appears once the phone has been connected to Xcode
   or to a signing tool.
3. Connect the phone by cable, unlock it, and accept *Trust This Computer*.
4. In Sideloadly: drop `dist/cl0udly-*.ipa`, enter the Apple ID and password
   (a separate Apple ID is safer than your main one), answer the 2FA prompt,
   and start. Sideloadly re-signs the app with a development profile it creates.
5. On the iPhone: **Settings → General → VPN & Device Management →** trust the
   developer certificate.

The app stops opening after **7 days**. Re-sign by repeating step 4; the same
Apple ID over the same bundle ID keeps the app's data.

Limits of a free account: 7 days per signature, 3 sideloaded apps per device,
10 new app IDs per week.

## Paid Apple Developer Program (1 year, TestFlight)

Signing is done in CI instead of on the phone:

```bash
builder auth apple                      # App Store Connect API key
builder signing setup --distribution development --device <udid>
builder ios build --profile development --distribute   # prints a QR to install
```

`--distribution store` plus `builder ios release` puts builds in TestFlight.
No Mac is needed for any of it.

## Automatic refresh

Sideloadly is manual every week. AltStore or SideStore with AltServer on the same
computer refreshes the signature over Wi-Fi while the phone is charging, which
is the least annoying option for a 7-day signature.
