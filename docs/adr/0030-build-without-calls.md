# 0030 · A build without calls, for F-Droid

Date: 2026-10-25 · Status: proposed, waiting for the owner's answers below (`docs/RELEASE.md`, "F-Droid and
IzzyOnDroid", "Still open").

## Context

F-Droid's main repository builds every app from source and accepts no prebuilt binaries
(https://f-droid.org/docs/Inclusion_Policy/). Pingogram's call engine, NTgCalls (`docs/adr/0015-call-media-engine.md`),
downloads prebuilt WebRTC libraries while it builds. Building WebRTC from source in F-Droid's build server is possible
but large: a Chromium-sized checkout (tens of GB) and hours per ABI, which F-Droid's builders are not meant for.

IzzyOnDroid and the GitHub releases are not affected: they ship the APKs the release workflow builds, with calls.

What depends on calls today:

- `:feature:calls` alone holds the engine (the `pingogram.ntgcalls` plugin, the AAR with `libntgcalls.so`, 20.5 MB on
  `arm64-v8a`), the call screens, `CallManager`, `GroupCallManager`, `CallService`, Telecom and Recent calls.
- `:app` uses it in four files: `PingogramApplication` (starting `Calls`), `MainActivity` (providing
  `LocalCallStarter` and `LocalVoiceChats`), `PingogramApp` (`LocalRecentCallsLauncher`) and `SettingsNavigation`
  (the Recent calls destination).
- Every other module reaches calls through `core:ui`'s composition locals, which are null without a provider. The
  screens already leave their buttons out then: the conversation's call button, the profile's "Call" pill, "Call
  back", the voice chat bar and pill, "Recent calls" in Settings and the Contacts tab.
- `core:data`'s call repositories (`CallRepository`, `GroupCallRepository`, `ConferenceCallRepository`) are TDLib
  only, without the engine.
- `:app`'s manifest asks for the call permissions (`MANAGE_OWN_CALLS`, the `phoneCall`, `microphone`, `camera` and
  `mediaProjection` foreground service types, `USE_FULL_SCREEN_INTENT`, `BLUETOOTH_CONNECT`, `MODIFY_AUDIO_SETTINGS`).

## Proposal

- **A Gradle property, `-Ppingogram.calls=false`** (default `true`), read in `:app`'s build script:
  - `:app` depends on `:feature:calls` only with calls. Without it, `:feature:calls` is not even configured for the
    build (`settings.gradle.kts` leaves it out), so the NTgCalls plugin never downloads anything.
  - `:app`'s four uses move into a source set of their own, `app/src/calls/`, beside `app/src/noCalls/`, which holds the
    same seams with nothing behind them (no call starter, no voice chats, no Recent calls). Kotlin only, no
    reflection, as the flavors' `PushDelivery` binding does today.
  - The call permissions move from `app/src/main/AndroidManifest.xml` into `app/src/calls/AndroidManifest.xml`, so the
    build without calls does not ask for them.
- **It works with both flavors** but is meant for `foss`, which F-Droid builds. It does not change the version codes
  or the application id: F-Droid signs its APKs with its own key, so they never replace an APK from the GitHub releases
  anyway.
- **Incoming calls without calls.** TDLib still announces them and Telegram rings the user's other devices.
  Pingogram shows the call's notification group as an ordinary notification in the "Calls" channel: "Ada is calling
  you", with "This version of Pingogram can't take calls. Answer on another device." It leaves the call ringing, so
  another device can answer it; it is never declined automatically. The call's service message ("Missed call") reaches
  the chat as usual.
- **Links to calls** (`t.me/call/…`) say in the link's snackbar that this version can't join calls.
- **About** names the build: "Calls are not included in this version" under the version number, so a user who
  reports a missing feature can tell.
- **CI**: a job `no calls` in `ci.yml` builds `-Ppingogram.calls=false :app:assembleFossDebug` and runs `:app`'s unit
  tests in that configuration. A task `checkNoCalls`, run by that job, fails if any module of `io.github.pytgcalls`
  reaches the runtime classpath or `libntgcalls.so` reaches the APK, as `checkFossGoogleFree` does for Firebase.
- **The F-Droid recipe** (`docs/fdroid/dev.kyralabs.pingogram.yml`) adds `pingogram.calls=false` to its
  `gradleprops`, and its TODO on NTgCalls goes. Its description says that calls are left out, and why.
- **Size:** the F-Droid APK loses the engine, about 20 MB per ABI.

## Questions for the owner

1. Is an F-Droid build without calls acceptable, rather than waiting for WebRTC to be built from source?
2. Incoming calls in that build: the notification described above, which leaves the call ringing on other devices,
   or nothing at all (Telegram then shows the caller a missed call once it stops ringing)?
3. Should the build without calls also be offered in the GitHub releases (a smaller download without calls), or only
   be what F-Droid builds?

## Consequences

- One more build configuration to keep working: CI builds and tests it on every pull request, so a change that uses
  `:feature:calls` outside the call seams fails there.
- Any new call entry point keeps going through `core:ui`'s locals, which already allow none.
- F-Droid's users get no calls until WebRTC builds from source there, which this ADR does not attempt.
