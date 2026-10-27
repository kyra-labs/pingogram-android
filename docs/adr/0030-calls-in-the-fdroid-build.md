# 0030 · Calls in the F-Droid build

Date: 2026-10-25, spike 2026-10-27 · Status: decided by the owner on 2026-10-25 ("check Telegram or other Telegram
clients, and do the same"): **no build without calls**. F-Droid's build keeps calls, with the call engine built from
source, as the Telegram clients on F-Droid do. The proposal of a `-Ppingogram.calls=false` build is withdrawn.

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
  - The source list compiles WebRTC's frame transformer delegates (audio and video, sending and receiving) and the
    frame decryptor; it leaves out desktop capture. (The first reading of 2026-10-25 had missed them; corrected by the
    spike.)
  - F-Droid builds it as Telegram X FOSS: NDK r27d, a 5 h `timeout`, `arm64-v8a` only, a 70 MiB APK.
- **NTgCalls** (`cmake/FindWebRTC.cmake`, `FindClang.cmake`, `FindLibCXX.cmake`, `FindFFmpeg.cmake`):
  - It always downloads WebRTC M152 from `pytgcalls/webrtc-build`, which runs gn and ninja on a Chromium checkout. It
    also downloads Chromium's clang and libc++ and a prebuilt FFmpeg.
  - Its one hook is an imported target `WebRTC::webrtc` defined before it looks, which skips the download.
  - On Android it needs WebRTC's frame transformer (for conference calls), FFmpeg, and WebRTC's Java SDK. It does not
    need desktop capture or OpenH264.
- So route 1 means a WebRTC M152 built with the NDK alone, the NDK's clang and libc++ in place of Chromium's, and
  FFmpeg from source. Route 2 means an older WebRTC than NTgCalls uses, whose frame transformer is compiled, but
  whose tgcalls has not been checked for Telegram's conference calls (`docs/adr/0025-conference-calls.md`).

## The spike, before any engine change

1. Build Telegram X's tgcalls and WebRTC tree for `arm64-v8a`, `armeabi-v7a` and `x86_64` with NDK r28 (16 KB pages,
   `docs/TDLIB.md`), in a CI job. Measure:
   - the build time and disk use;
   - the size of the libraries against `libntgcalls.so` (20.5 MB on `arm64-v8a`), against the APK budget of
     `docs/PERFORMANCE.md` §1, which has 1.8 MB left.
2. Try NTgCalls' CMake build against that tree. Note what NTgCalls uses of WebRTC that the trimmed tree lacks:
   screen capture, the external encryption of conference calls, H.264.
3. Write the result into this ADR, choose the engine, and plan the change in small PRs.

## The spike's result (2026-10-27)

`.github/workflows/fdroid-calls-spike.yml`, run 40589378332, on GitHub's `ubuntu-24.04` runner (4 cores, 16 GB of
memory). It built Telegram X FOSS 0.28.11.1800 (`AmanoTeam/Telegram-X-FOSS` at `807947f`, the repository and commit
fdroiddata's recipe builds) as that recipe does: NDK r27d, its submodules, `./scripts/setup.sh --skip-sdk-setup`, then
`assembleMarshmallowArm64Release`. Its WebRTC is `TGX-Android/webrtc` at `6ecff4f` (from `ali-fareed/webrtc`
`e3dfe44`, Telegram iOS's tree), its tgcalls `332e581`.

**Step 1, Telegram X's engine from source: it builds.**

| | |
|---|---|
| Clone with submodules | 3 min 34 s, 6.2 GB |
| ... of which `app/jni/tgvoip` (tgcalls, WebRTC and their dependencies) | 2.6 GB |
| ... of which Chromium's `base` and `third_party` (`webrtc_deps`) | 2.3 GB |
| ... of which WebRTC's own tree | 116 MB |
| `setup.sh` (FFmpeg, libvpx, OpenSSL, the other native dependencies, for every ABI) | about 3 min |
| Release build of `arm64-v8a` (the native code and the app) | 34 min 53 s, exit status 0 |
| Build tree | 2.8 GB |
| `libtgcallsjni.so` (tgcalls, WebRTC, libvpx, OpenH264, stripped) | **14.3 MB**, against `libntgcalls.so`'s 20.5 MB |
| The APK (`arm64-v8a`, with Telegram X's own TDLib at 32.9 MB) | 70.1 MB |

So F-Droid's build server already does this for Telegram X: within its 5 h timeout with half an hour to spare per ABI,
about 9 GB of disk per ABI, nothing downloaded prebuilt. What it needs beyond the NDK: `cmake`, `gperf`,
`build-essential`, `automake` and `libtool`, as the recipe installs. Not measured yet: `armeabi-v7a` and `x86_64`
(the workflow's `flavors` input, `MarshmallowArm32 MarshmallowX64`), and NDK r28 for 16 KB pages (its `ndk` input,
`28.2.13676358`); Telegram X's recipe builds `arm64-v8a` only.

**Step 2, NTgCalls against that tree: close, not as it is.** Of the 141 WebRTC headers NTgCalls 3.0.5 includes, the
tree lacks 6, all from WebRTC versions after it:

- `api/audio/builtin_audio_processing_builder.h`
- `api/video/adapted_video_track_source.h`
- `pc/codec_vendor.h`
- `pc/sdp_payload_type_suggester.h`
- `rtc_base/base64.h`
- `rtc_base/crypto_random.h`

What Pingogram's calls need is there: the frame transformer and frame encryptor interfaces (conference calls' external
encryption), H.264, and the Android SDK's `ScreenCapturerAndroid` (screen sharing). `modules/desktop_capture` is in the
tree but not compiled, which Android does not need. The missing headers mark APIs that moved or were split out since
the tree's version; how much of NTgCalls' code uses them beyond the includes is what compiling it would tell.

**Verdict: feasible.** Building a call engine and WebRTC from source with the NDK alone, within F-Droid's limits, is
shown by Telegram X's build, and it comes out 6 MB smaller than NTgCalls' library. What remains open is which engine,
and that is the next step, not a guess:

1. **NTgCalls compiled against a source-built WebRTC** (route 1), by its imported-target hook (`WebRTC::webrtc`),
   first against Telegram X's tree with the 6 gaps patched, counting what else fails to compile. A short list keeps
   NTgCalls, its patches (`tools/ntgcalls/`) and every call feature as they are. A long one means a trimmed M152 tree
   with CMake source lists of its own, kept by hand per WebRTC milestone as Telegram X does.
2. **tgcalls** (route 2) only if route 1 fails: it builds today, but takes every call feature through a new engine
   behind `CallEngine` and `GroupCallSession`, and conference calls are unchecked.

Nothing of Pingogram's build changed: the spike workflow builds and ships nothing. The submission keeps waiting for
the engine.

## Consequences

- No second build configuration: the F-Droid build is the `foss` build.
- Building WebRTC from source adds a long native build to the release workflow, cached per commit as TDLib's is.
- Pingogram's F-Droid listing waits for the engine work (the spike is done); the other F-Droid TODOs (the API values
  in the public recipe, TDLib's AAR packaged in the recipe) are independent of it.
