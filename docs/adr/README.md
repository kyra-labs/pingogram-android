# Architecture decision records

One file per significant decision, `NNNN-title.md`, with Context · Decision · Consequences
(`docs/ARCHITECTURE.md`, Decision log).

| ADR | Decision |
|---|---|
| [0002](0002-open-source-licenses.md) | Open-source licenses screen: our own Gradle task lists each variant's dependencies and POM licenses at build time (not AboutLibraries, owner decision D17); our own Compose screens |
| [0003](0003-keep-connected-foreground-service.md) | "Keep connected" (foss only): a `specialUse` foreground service keeps TDLib's connection open while the user wants it; not `dataSync` (6 h/day on Android 15) nor `remoteMessaging` (cross-device relay) |
| [0004](0004-voice-recording.md) | Voice messages: the platform's `MediaRecorder`, Ogg Opus on Android 10+ and AAC (`.m4a`) on 8–9, no library; level and Telegram's 5-bit waveform from the recorder's peak amplitude |
