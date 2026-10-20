# Architecture decision records

One file per significant decision, `NNNN-title.md`, with Context · Decision · Consequences
(`docs/ARCHITECTURE.md`, Decision log).

| ADR | Decision |
|---|---|
| [0002](0002-open-source-licenses.md) | Open-source licenses screen: our own Gradle task lists each variant's dependencies and POM licenses at build time (not AboutLibraries, owner decision D17); our own Compose screens |
| [0004](0004-push-delivery.md) | Push delivery: Firebase Cloud Messaging through TDLib's `registerDevice` (encrypted) in the `gms` flavor only, after the owner uploads FCM credentials at my.telegram.org; `foss` stays Google-free (enforced by the build); UnifiedPush for `foss` is a follow-up |
