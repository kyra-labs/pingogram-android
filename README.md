# Pingogram

**An open-source Telegram client for Android, built the way a Google app would be: Material 3
Expressive, dynamic color, adaptive layouts, and nothing else in the way.**

[![CI](https://github.com/kyra-labs/pingogram-android/actions/workflows/ci.yml/badge.svg)](https://github.com/kyra-labs/pingogram-android/actions/workflows/ci.yml)
[![Latest release](https://img.shields.io/github/v/release/kyra-labs/pingogram-android?include_prereleases&label=download)](https://github.com/kyra-labs/pingogram-android/releases/latest)
[![License: GPL v3](https://img.shields.io/badge/license-GPLv3-blue.svg)](LICENSE)

Pingogram is **not** affiliated with Telegram. It is a third-party client that uses Telegram's
official client library, [TDLib](https://github.com/tdlib/td), and complies with the
[Telegram API Terms of Service](https://core.telegram.org/api/terms).

## Why

Telegram's official Android app is excellent but has its own design language. Pingogram keeps
the exact same account, chats and encryption, and replaces the UI with a native Jetpack Compose
app that follows Google's current guidance: Material 3 Expressive components and motion,
Material You dynamic color, predictive back, edge-to-edge, large-screen layouts, and modern
notification and permission behaviour.

## Status

Early development. See [docs/ROADMAP.md](docs/ROADMAP.md) for milestones and
[Releases](https://github.com/kyra-labs/pingogram-android/releases) for test builds.

## Download

Grab the latest APK from the [Releases page](https://github.com/kyra-labs/pingogram-android/releases/latest).
Use `-universal.apk` unless you know your device's ABI. Verify with `SHA256SUMS.txt`.

## Features (planned for 1.0)

- Chats, groups, channels, forums, secret chats, folders, archive
- Photos, videos, GIFs, stickers (animated), voice messages, files, polls, reactions
- Rich notifications (conversation style, direct reply, shortcuts, bubbles)
- Full settings: privacy, sessions, 2FA, passcode/biometrics, storage, appearance, language
- Material 3 Expressive throughout: Google Sans Flex variable typography, expressive shapes,
  spring motion, dynamic color, light/dark/AMOLED, three contrast levels, adjustable bubble shape
- Built to be faster and lighter than the official app (see docs/PERFORMANCE.md)
- Tablet/foldable list-detail layout, predictive back, per-app language
- No ads, no analytics, no trackers

## Building

```bash
cp secrets.properties.example secrets.properties   # add your api_id / api_hash from my.telegram.org
./gradlew assembleFossDebug
```

Requirements: JDK 17+, an Android SDK with platform 37 (compileSdk), Android Studio (latest stable). The TDLib AAR is downloaded
automatically from this repo's `tdlib-*` releases (see [docs/TDLIB.md](docs/TDLIB.md)).

## Documentation

| Doc | Contents |
|---|---|
| [REQUIREMENTS.md](REQUIREMENTS.md) | Scope, compliance, functional & non-functional requirements |
| [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) | Modules, layers, data flow |
| [docs/DESIGN_SYSTEM.md](docs/DESIGN_SYSTEM.md) | Material 3 Expressive rules |
| [docs/TDLIB.md](docs/TDLIB.md) | TDLib build and wrapper |
| [docs/PERFORMANCE.md](docs/PERFORMANCE.md) | Performance budgets and optimization rules |
| [docs/TESTING.md](docs/TESTING.md) | Test strategy |
| [docs/RELEASE.md](docs/RELEASE.md) | Versioning, signing, releases |
| [docs/OWNER_SETUP.md](docs/OWNER_SETUP.md) | Maintainer setup checklist |
| [CLAUDE.md](CLAUDE.md) | Rules for AI-assisted development |

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md). Commits must be signed off (DCO).

## License

GPL-3.0-or-later — see [LICENSE](LICENSE). TDLib is licensed under the Boost Software License 1.0.
Google Sans Flex is used under the SIL Open Font License 1.1.
"Telegram" is a trademark of Telegram FZ-LLC; Pingogram is an independent project.
