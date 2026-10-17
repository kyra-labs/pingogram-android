# Contributing to Pingogram

Thanks for helping. Please read `CLAUDE.md`, `REQUIREMENTS.md` and `docs/` first — the same
rules apply to humans and AI assistants.

## Ground rules

- Be kind (see `CODE_OF_CONDUCT.md`).
- Never commit secrets (`api_id`, `api_hash`, keystores). Never copy code from the official
  Telegram apps or other GPL forks into this repo.
- Nothing that violates the Telegram API Terms of Service will be merged.
- UI must be Jetpack Compose + Material 3 Expressive (`docs/DESIGN_SYSTEM.md`).

## Local setup

1. JDK 17 or newer, and an Android SDK with platform 37 (Android Studio installs it, or Gradle
   downloads it once the SDK licenses are accepted). Point `local.properties` → `sdk.dir` at it.
2. `cp secrets.properties.example secrets.properties` and fill in your own `api_id` /
   `api_hash` from https://my.telegram.org. The build fails with a clear message without it.
3. `./gradlew check` runs everything CI runs on a PR; `./gradlew :app:assembleFossDebug`
   builds an installable debug APK.

## Workflow

1. Open or pick an issue; comment that you are working on it.
2. Branch from `main`: `feat/<area>-<short-name>`, `fix/<area>-<short-name>`.
3. Follow Conventional Commits and sign off every commit (`git commit -s`), which certifies
   the [Developer Certificate of Origin](https://developercertificate.org/).
4. Run `./gradlew check` locally; add/updates tests; update `CHANGELOG.md` under `Unreleased`.
5. Open a PR using the template. Keep it small; one concern per PR. Screenshots/recordings for
   UI changes (light + dark).
6. A maintainer reviews; CI must be green; squash-merge.

## Style

- Kotlin official code style, enforced by ktlint and detekt (`./gradlew ktlintFormat`).
- Compose: stateless screens, previews for every state, `Modifier` as first optional param.
- Strings in `strings.xml` only (English source). No hard-coded user-facing text.

## Reporting bugs / security

Bugs → GitHub issues using the template. Security issues → see `SECURITY.md` (do not open a
public issue).
