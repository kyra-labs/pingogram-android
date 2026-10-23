# 0015 — The media engine for calls

Status: accepted (owner, 2026-10-22: "no need to care about the budget, use what is the best"): NTgCalls as
published, video included, built from a pinned commit in CI (option 2 below); the APK budget is raised for it. The
brief named tgcalls, which cannot be built for Android today (below).

## Context

`REQUIREMENTS.md` FR-V1 asks for voice calls (M5). TDLib only signals a call: `createCall`, `acceptCall`,
`discardCall`, `sendCallSignalingData`, `updateCall` and `updateNewCallSignalingData` (td_api.tl of the pinned TDLib
1.8.68). Once both sides agree, `callStateReady` carries the protocol (`callProtocol`: UDP P2P and reflector flags,
layers, `library_versions`), the servers (`callServerTypeTelegramReflector` with its peer tag, `callServerTypeWebrtc`
STUN/TURN), the 256-byte encryption key, the four emojis, `allow_p2p` and the engine's `custom_parameters`. Moving the
audio is the job of a separate engine, which must speak the protocol of Telegram's own apps, or nobody else hears us.

The brief asked for Telegram's **tgcalls** behind a JNI layer of our own, built in CI like TDLib (`docs/TDLIB.md`), and
to stop and report if its license or its build made that infeasible. Requirements the engine has to meet: GPL-3.0
compatible (with `LICENSE-EXCEPTION.md`), the protocol versions official apps speak, NDK r28+ with 16 KB-aligned 64-bit
libraries for `arm64-v8a`, `armeabi-v7a` and `x86_64`, a reproducible CI build, no Google Play services in `foss`, and
the 35 MB `arm64-v8a` APK budget (`docs/PERFORMANCE.md` §1). v0.3.0's `arm64-v8a` APK is 25.0 MB, so 10 MB are left,
and native libraries are stored uncompressed (`useLegacyPackaging = false`, `extractNativeLibs="false"`), so a
library's whole size counts.

## What was checked (2026-10-22)

### tgcalls (`TelegramMessenger/tgcalls`, `8d6dd4c`, 2026-10-12)

- **License**: LGPL-3.0, compatible with GPL-3.0-or-later. WebRTC is BSD-3-Clause, Opus BSD-3-Clause. No obstacle.
- **Protocol versions** (`GetVersions()`): `2.7.7`, `5.0.0` (`InstanceImpl`); `7.0.0`, `8.0.0`, `9.0.0`, `12.0.0`,
  `13.0.0` (`InstanceV2Impl`, the production engine per the repository's own July 2026 audit); `10.0.0`, `11.0.0`
  (`InstanceV2ReferenceImpl`); `14.0.0` (`InstanceV2CompatImpl`); `18.0.0`, `19.0.0` (`v2wasm`).
- **Build**: none for Android. tgcalls compiles against Telegram's own WebRTC fork (a submodule of Telegram-iOS), and
  the only build system that builds that fork is Telegram-iOS's Bazel tree (iOS, macOS, and Linux for its test CLI).
  Telegram's Android app builds both from copies in its own source tree with its own CMake files, which this project
  must not use (CLAUDE.md). tgcalls' Android platform code (`platform/android/AndroidContext.cpp`) also binds JNI to
  Telegram's app class `org.telegram.messenger.voip.VideoCameraCapturer`.
- **What it would take**: our own CMake (or GN) build of Telegram's WebRTC fork and its dependencies (BoringSSL or
  OpenSSL, Opus, libsrtp, usrsctp, abseil, libyuv, rnnoise; libvpx and FFmpeg for video) for three ABIs, our own
  Android platform layer and audio device module, kept in step with a fork that moves weekly. That is weeks of work
  before the first call, a build of an hour or more per ABI in CI, and a maintenance burden for every tgcalls update,
  with nothing to compare against but Telegram's app. **Infeasible for M5**; this is where the brief says to stop.

### NTgCalls (`pytgcalls/ntgcalls`, `e8ab467`, 3.0.5, 2026-10-19)

An independent C++ engine for Telegram's 1:1 and group calls (it powers PyTgCalls), not code from Telegram's apps.

- **License**: LGPL-3.0. Its prebuilt libraries link WebRTC m152 (BSD-3-Clause, built from upstream by
  `pytgcalls/webrtc-build`), Boost (BSL-1.0), libc++ (Apache-2.0 with LLVM exception), FFmpeg 9.0.1 (LGPL-2.1-or-later,
  configured without `--enable-gpl`/`--enable-nonfree`), Opus (BSD-3-Clause) and, on Android, Oboe (Apache-2.0), as
  its `THIRD_PARTY_NOTICES.md` lists. All GPL-3.0 compatible; LGPL-3.0 is GPL-3.0 plus permissions. A separate `.so`
  satisfies the LGPL's relinking condition, so `LICENSE-EXCEPTION.md`'s combination with Play services in `gms` is
  unaffected. H.264/HEVC code (OpenH264, FFmpeg) carries the patent notice NTgCalls documents; a voice-only build has
  none.
- **Protocol versions** (`Signaling::match_version`): `8.0.0`, `9.0.0` (signaling v2) and `12.0.0`, `13.0.0`
  (signaling v3), the highest common one chosen. Official apps offer `13.0.0` (tgcalls' production engine above), so
  calls with them use it. Telegram reflectors and WebRTC STUN/TURN servers are both taken (`RTCServer`).
- **TDLib**: TDLib does the Diffie-Hellman exchange itself; `P2PCall::skip_exchange(key, isOutgoing)` takes
  `callStateReady.encryption_key` as it is, then `connect(servers, versions, p2pAllowed, customParameters)`. Signaling
  goes out through a callback (→ `sendCallSignalingData`) and comes in with `send_signaling_data`
  (← `updateNewCallSignalingData`). The emojis come from TDLib (`callStateReady.emojis`).
- **Android build**: an Android target (`targets/android`, CMake + Gradle) with NDK r28b; audio through Oboe with
  `Usage::VoiceCommunication` and `InputPreset::VoiceCommunication`, so the platform's echo canceller and noise
  suppressor apply; `minSdk` 21. Every dependency is pinned in `version.properties` and downloaded as prebuilt static
  libraries from `pytgcalls/*` releases whose build scripts are public, so a pinned commit builds the same AAR again.
- **The published AAR** (`io.github.pytgcalls:ntgcalls:3.0.5`, Maven Central, 37.2 MB): `libntgcalls.so` per ABI,
  checked here: `arm64-v8a` 21.5 MB (9.4 MB gzipped), `armeabi-v7a` 14.3 MB, `x86_64` 25.9 MB, `x86` 23.1 MB; every
  ABI's `LOAD` segments 16 KB aligned (`0x4000`); only platform libraries needed (`libandroid`, `liblog`,
  `libOpenSLES`, `libEGL`). It also carries `libs/webrtc.jar` (WebRTC's Java classes, 1.2 MB) and its own Java API
  (`io.github.pytgcalls.NTgCalls`), and asks for `CAMERA` and `RECORD_AUDIO` in its manifest.
- **Size against the budget**: added as published, the `arm64-v8a` APK grows from 25.0 MB to about 46.5 MB with
  uncompressed native libraries (or about 34.4 MB if every native library were compressed again, which costs install
  space and load time for TDLib too). The library includes video (camera capture, H.264/VP8/VP9/AV1, FFmpeg), which
  voice calls do not need. A voice-only build (no video sources, no FFmpeg or OpenH264, WebRTC without video codecs)
  would be much smaller; how much is not measured: it needs our CI build.

### Others

- **A Kotlin implementation of the protocol over a stock WebRTC SDK**: the signaling encryption, the reflector port
  (a custom `cricket::Port` subclass) and the v3 transport live inside WebRTC's native internals, which the Java SDK
  does not expose. Not viable.
- **libtgvoip**: unmaintained, versions official apps no longer offer. Not viable.

## Options (as proposed to the owner)

1. **NTgCalls, voice-only, built from source in our CI** (proposed first): `.github/workflows/ntgcalls.yml` checks out
   NTgCalls at a pinned commit, builds `libntgcalls.so` for `arm64-v8a`, `armeabi-v7a` and `x86_64` with NDK r28+ and
   video turned off, verifies 16 KB alignment, and attaches `ntgcalls-<sha>.aar` + `SHA256SUMS.txt` to a pre-release
   `ntgcalls-<short-sha>`; a `pingogram.calls` convention plugin fetches and verifies it like `pingogram.tdlib`, pinned
   in `libs.versions.toml`. Our JNI layer: NTgCalls' own JNI (its generated `NTgCalls` class) or a thin one of ours
   over its C API in the same workflow, wrapped by a Kotlin `CallEngine` in `:core:calls` that nothing else touches.
   Needs: approval of NTgCalls as the engine (a native dependency over 1 MB that touches the network, CLAUDE.md), and
   a size check once the first voice-only AAR exists; if it does not fit the 10 MB left, a budget decision.
2. **NTgCalls as published** (Maven Central, or the same build with video): least work, 1:1 video possible later, but
   about +21.5 MB on `arm64-v8a`. Needs the APK budget raised to about 50 MB, or native libraries compressed.
3. **tgcalls after all**: start a separate project to build Telegram's WebRTC fork and tgcalls for Android with our own
   build files and Android platform layer. Weeks, with the risks above; M5 waits for it.
4. **Defer calls**: tag v0.4.0, keep calls in the backlog, and revisit when tgcalls or another engine has an Android
   build.

Video calls and group calls (the ROADMAP's second M5 item) get their own ADR once the engine is chosen: with option 1
1:1 video means rebuilding with video (option 2's size); group calls are a different protocol (`createGroupCall`,
`joinGroupCall`) that NTgCalls also implements.

## Decision

**NTgCalls, as its upstream builds it for Android (video included), from a pinned commit in our CI** (option 2,
built the way option 1 describes):

- `.github/workflows/ntgcalls.yml` (manual) checks out `pytgcalls/ntgcalls` at `NTGCALLS_COMMIT` (3.0.5,
  `e8ab467`), builds it with its own tooling (`setup.py build_lib --target=android`, then its Gradle project's
  `assembleRelease`) for `arm64-v8a`, `armeabi-v7a` and `x86_64` (32-bit x86 left out, as everywhere in Pingogram),
  checks the ABIs and the 16 KB alignment of the 64-bit libraries, and publishes `ntgcalls-<short-sha>.aar`, the
  corresponding source (`git archive` of the commit) and `SHA256SUMS.txt` as the pre-release `ntgcalls-<short-sha>`.
- The `pingogram.ntgcalls` convention plugin, applied by `:feature:calls` only, installs that AAR into
  `feature/calls/libs/maven` (git-ignored; `settings.gradle.kts` takes the group `io.github.pytgcalls` from there
  only) and verifies it against `ntgcallsAarSha256` in `libs.versions.toml`, exactly like TDLib (`PrebuiltAar`,
  shared with `pingogram.tdlib`); `pingogram.ntgcalls.aar` replaces it with a self-built AAR and `fetchNtgcalls`
  downloads it on demand.
- The JNI layer is NTgCalls' own (`io.github.pytgcalls.NTgCalls`, generated from its C++ API), not one of ours:
  it is a thin, mechanical binding of the engine's API, and a second one would duplicate it. Pingogram's code
  reaches it only through a Kotlin `CallEngine` interface in `:feature:calls`, which tests replace.
- Video stays in the library, so 1:1 video calls need no second build (their scope: a separate ADR).
- Budget: `docs/PERFORMANCE.md` §1 raises the `arm64-v8a` APK to ≤ 50 MB and the universal APK to ≤ 125 MB.

## Consequences

- Per-ABI APKs grow by the engine's library (`arm64-v8a` about +21.5 MB, uncompressed like TDLib's), within the
  raised budget. `arm64-v8a` stays under the 50 MB a bot may post to the owner's channel (`docs/RELEASE.md`).
- The rest of M5 is engine-independent behind the `CallEngine` interface: the TDLib
  signaling repository (`core:data`), Android Telecom as a self-managed `ConnectionService` (or `androidx.core.telecom`,
  a separate ADR), the `CallStyle` notifications in the existing "Calls" channel, the in-call screen in `:feature:calls`,
  call history from `messageCall` service messages, and the permissions the owner approved for calls.
- The engine's licenses join `config/licenses/extra.json` and `texts/` (its AAR has no POM license our task can read),
  and the open-source licenses screen lists them.
