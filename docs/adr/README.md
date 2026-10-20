# Architecture decision records

One file per significant decision, `NNNN-title.md`, with Context · Decision · Consequences
(`docs/ARCHITECTURE.md`, Decision log).

| ADR | Decision |
|---|---|
| [0002](0002-open-source-licenses.md) | Open-source licenses screen: our own Gradle task lists each variant's dependencies and POM licenses at build time (not AboutLibraries, owner decision D17); our own Compose screens |
| [0003](0003-keep-connected-foreground-service.md) | "Keep connected" (foss only): a `specialUse` foreground service keeps TDLib's connection open while the user wants it; not `dataSync` (6 h/day on Android 15) nor `remoteMessaging` (cross-device relay) |
| [0004](0004-push-delivery.md) | Push delivery: Firebase Cloud Messaging through TDLib's `registerDevice` (encrypted) in the `gms` flavor only, after the owner uploads FCM credentials at my.telegram.org; `foss` stays Google-free (enforced by the build); UnifiedPush for `foss` is a follow-up |
| [0005](0005-passcode-lock.md) | Passcode lock: the platform's `BiometricPrompt` (Android 10+, strong biometrics, no `androidx.biometric`: it needs a `FragmentActivity` and brings AppCompat); PBKDF2-HMAC-SHA512 with 210,000 rounds; a growing wait after five wrong passcodes |
| [0006](0006-platform-location-manager.md) | Sharing the location: the platform's `LocationManager` (no Google Play services), continuous updates only while the sheet is open, foreground permissions only, coordinates and "Open in maps" instead of a map |
| [0007](0007-voice-recording.md) | Voice messages: the platform's `MediaRecorder`, Ogg Opus on Android 10+ and AAC (`.m4a`) on 8–9, no library; level and Telegram's 5-bit waveform from the recorder's peak amplitude |
| [0008](0008-lottie-for-animated-stickers.md) | Animated (TGS) stickers: Lottie for Android (pure Java, ~76 KB after R8, without its AppCompat dependency) behind `core:ui`'s `StickerAnimationDecoder`, not rlottie (native code built in CI); frames rendered off the main thread, first frames cached by sticker and size, at most 8 playing at 30 fps |
| [0009](0009-background-transfers.md) | Background transfers (FR-N4): the user's uploads and downloads (never automatic ones) keep the app running in WorkManager's `dataSync` foreground service, with a "Transfers" progress notification; Live Update promotion waits for `POST_PROMOTED_NOTIFICATIONS` |
