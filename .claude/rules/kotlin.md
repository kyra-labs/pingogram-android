---
paths:
  - "**/*.kt"
  - "**/*.kts"
---

# Kotlin

- Names follow `docs/ARCHITECTURE.md` "Naming conventions". One top-level class per file,
  file named after it.
- `private` or `internal` by default; `public` only for a module's real API.
- Fix a detekt finding instead of suppressing it. `LongParameterList` → a state class plus an
  actions class. `TooManyFunctions`, `LongMethod` → split the file or extract a collaborator.
  `@Suppress` is the last resort and needs a comment saying why it cannot be fixed.
- New files stay under about 500 lines. Do not add to a file already over 1,000 lines;
  extract first.
- No `!!` in production code: `requireNotNull`/`checkNotNull` with a message, or handle null.
- No `TODO`/`FIXME` without an issue number.
- Coroutines: inject dispatchers; no `runBlocking` outside tests; catch specific exceptions
  and always rethrow `CancellationException`.
- State: private `MutableStateFlow` exposed as `StateFlow`; collect in composables with
  `collectAsStateWithLifecycle()`. UI state is immutable and uses
  `kotlinx.collections.immutable` for lists.
- Logging: never log message text, phone numbers, user or chat IDs, media paths or tokens.
- KDoc: what the class does and why it exists, in at most about five lines. No history, no
  board or audit numbers. Comments explain why, never what.
- A new module applies the `pingogram.*` convention plugins and is added to
  `settings.gradle.kts` and to a group in `tools/ci/check-shards.json`.
- `foss` must never depend on Google Play services or Firebase; that code lives in the `gms`
  source set behind an interface in a shared module.
