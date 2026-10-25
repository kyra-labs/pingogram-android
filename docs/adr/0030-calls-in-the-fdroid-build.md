# 0030 · Calls in the F-Droid build

Date: 2026-10-25 · Status: decided by the owner on 2026-10-25 ("check Telegram or other Telegram clients, and do the
same"): **no build without calls**. F-Droid's build keeps calls, with the call engine built from source, as the
Telegram clients on F-Droid do. The proposal of a `-Ppingogram.calls=false` build is withdrawn.

## Context

F-Droid's main repository builds every app from source and accepts no prebuilt binaries
(https://f-droid.org/docs/Inclusion_Policy/). Pingogram's call engine, NTgCalls (`docs/adr/0015-call-media-engine.md`),
builds against prebuilt libraries its `version.properties` pins (`.github/workflows/ntgcalls.yml`):

- WebRTC, from `pytgcalls/webrtc-build`, itself built from a Chromium checkout;
- Chromium's clang and libc++;
- FFmpeg, OpenH264 and Boost.

IzzyOnDroid and the GitHub releases are not affected: they ship the APKs the release workflow builds, with calls.

### What the Telegram clients on F-Droid do

The two Telegram clients F-Droid builds keep their calls. Each carries the sources of its call engine and of WebRTC
in its own repository, and F-Droid compiles them with the NDK.

- **Telegram FOSS** (`org.telegram.messenger`, no longer maintained) and its fork **Mercurygram**
  (`it.belloworld.mercurygram`, fdroiddata `metadata/it.belloworld.mercurygram.yml`):
  - they build Telegram's own sources, whose `TMessagesProj/jni` holds tgcalls and a trimmed WebRTC tree;
  - the recipe deletes nothing of it but scans it (`scandelete: TMessagesProj/jni`), with
    `QUIET_NATIVE_BUILD=true` so Gradle builds the native code.
- **Telegram X** (`org.thunderdog.challegram`, GPL-3.0, on TDLib like Pingogram, fdroiddata
  `metadata/org.thunderdog.challegram.yml`):
  - `submodules: true` and `./scripts/setup.sh --skip-sdk-setup` with NDK r27d;
  - its `app/jni/tgvoip` holds tgcalls with WebRTC under `third_party/webrtc`;
  - the recipe only removes WebRTC's tests (`rm: app/jni/tgvoip/third_party/webrtc/test`) and scans `app/jni`,
    `thirdparty` and `tdlib`.

Neither ships a build without calls.

## Decision

- **F-Droid's build has calls**, with nothing left out by a build flag. No `pingogram.calls` property, no build without
  `:feature:calls`.
- **The call engine is built from source for it.** NTgCalls cannot be built so today: its WebRTC needs a Chromium
  checkout (tens of GB), Chromium's own toolchain and hours per ABI, which F-Droid's build server does not offer.
  Pingogram takes Telegram X's route instead:
  - WebRTC as a trimmed source tree in the repository (a submodule), compiled with the NDK and CMake, as tgcalls'
    trees are;
  - built from source in the release workflow as well, so GitHub's and F-Droid's APKs come from the same sources;
  - FFmpeg and OpenH264 from their sources the same way.
- **Which engine builds on it.** Two ways, chosen by a spike (below):
  1. NTgCalls, built against that WebRTC tree instead of its prebuilt one, keeping today's engine and its patches
     (`tools/ntgcalls/`);
  2. tgcalls, as Telegram X uses it, behind the same `CallEngine` and `GroupCallSession` seams of `:feature:calls`,
     with no change to the screens.

  Whichever builds with the NDK alone, within F-Droid's limits, and keeps 1:1 video, voice chats, live streams,
  screen sharing and conference calls, becomes the engine for every build.
- **Until then** the F-Droid submission waits, and the recipe's TODO on NTgCalls says why. IzzyOnDroid (the GitHub APKs,
  with calls) stays the F-Droid-compatible way to install Pingogram.

## What the engines need (research, 2026-10-25)

- **Telegram X** (`.gitmodules`, `app/jni/tgvoip/BuildWebRTC.cmake`):
  - WebRTC is a submodule (`TGX-Android/webrtc`, branch `production`, from Telegram iOS's tree). About 1,300 files
    are listed by hand in CMake and compiled with the NDK; no gn or ninja.
  - VP8 and VP9 come from libvpx, H.264 from OpenH264. libvpx, OpenH264, FFmpeg, abseil, usrsctp and libsrtp are all
    submodules built from source. tgcalls is its own submodule, with group calls (`GroupInstanceCustomImpl`) and
    screen capture.
  - The source list holds no frame encryptor or transformer.
  - F-Droid builds it as Telegram X FOSS: NDK r27d, a 5 h `timeout`, `arm64-v8a` only, a 70 MiB APK.
- **NTgCalls** (`cmake/FindWebRTC.cmake`, `FindClang.cmake`, `FindLibCXX.cmake`, `FindFFmpeg.cmake`):
  - It always downloads WebRTC M152 from `pytgcalls/webrtc-build`, which runs gn and ninja on a Chromium checkout. It
    also downloads Chromium's clang and libc++ and a prebuilt FFmpeg.
  - Its one hook is an imported target `WebRTC::webrtc` defined before it looks, which skips the download.
  - On Android it needs WebRTC's frame transformer (for conference calls), FFmpeg, and WebRTC's Java SDK. It does not
    need desktop capture or OpenH264.
- So route 1 means a WebRTC M152 built with the NDK alone, the NDK's clang and libc++ in place of Chromium's, and
  FFmpeg from source. Route 2 means an older WebRTC than NTgCalls uses, without the frame transformer that
  Pingogram's conference calls rely on (`docs/adr/0025-conference-calls.md`).

## The spike, before any engine change

1. Build Telegram X's tgcalls and WebRTC tree for `arm64-v8a`, `armeabi-v7a` and `x86_64` with NDK r28 (16 KB pages,
   `docs/TDLIB.md`), in a CI job. Measure:
   - the build time and disk use;
   - the size of the libraries against `libntgcalls.so` (20.5 MB on `arm64-v8a`), against the APK budget of
     `docs/PERFORMANCE.md` §1, which has 1.8 MB left.
2. Try NTgCalls' CMake build against that tree. Note what NTgCalls uses of WebRTC that the trimmed tree lacks:
   screen capture, the external encryption of conference calls, H.264.
3. Write the result into this ADR, choose the engine, and plan the change in small PRs.

## Consequences

- No second build configuration: the F-Droid build is the `foss` build.
- Building WebRTC from source adds a long native build to the release workflow, cached per commit as TDLib's is.
- Pingogram's F-Droid listing waits for the spike and the engine work; the other F-Droid TODOs (the API values in the
  public recipe, TDLib's AAR packaged in the recipe) are independent of it.
