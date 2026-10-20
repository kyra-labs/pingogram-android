# Architecture decision records

One file per significant decision, `NNNN-title.md`, with Context · Decision · Consequences
(`docs/ARCHITECTURE.md`, Decision log).

| ADR | Decision |
|---|---|
| [0002](0002-open-source-licenses.md) | Open-source licenses screen: our own Gradle task lists each variant's dependencies and POM licenses at build time (not AboutLibraries, owner decision D17); our own Compose screens |
| [0003](0003-passcode-lock.md) | Passcode lock: the platform's `BiometricPrompt` (Android 10+, strong biometrics, no `androidx.biometric`: it needs a `FragmentActivity` and brings AppCompat); PBKDF2-HMAC-SHA512 with 210,000 rounds; a growing wait after five wrong passcodes |
