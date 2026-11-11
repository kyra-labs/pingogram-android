# 0038. APK size budget for TDLib 1.8.70

Status: accepted (owner, 2026-11-11)

## Context

The owner asked for TDLib 1.8.70 (`23383ae`) on 2026-11-11: it has the community functions that boards 19a and 19b
need (`getJoinedCommunities`, `addCommunityChat`, `getSuitableCommunityChats`), which 1.8.68 lacks. Its `libtdjni.so`
is larger, and the native libraries are stored uncompressed (`docs/TDLIB.md`), so the APKs grow by as much:

| ABI | `libtdjni.so` 1.8.68 | 1.8.70 | APK on `main` | APK with 1.8.70 | Budget before |
|---|---|---|---|---|---|
| arm64-v8a | 17.47 MB | 17.83 MB | about 49.5 MB | 49.9 MB | 50 MB |
| armeabi-v7a | 15.47 MB | 15.80 MB | 40.7 MB | 41.1 MB | none |
| x86_64 | 20.67 MB | 21.10 MB | 56.8 MB | 57.2 MB | none |
| universal | | | 122.5 MB | 123.6 MB | 123 MB |

(`fossBenchmarkRelease`, CI's `apk size` job; a megabyte is 1,048,576 bytes.) The universal APK went over its
budget, and arm64-v8a kept 0.1 MB. Nothing in the app can shrink TDLib; compressing the native libraries is the one
large saving, and `docs/PERFORMANCE.md` §8 keeps them uncompressed for install size and load time.

## Decision

The owner raised the budgets (2026-11-11: "raise to how much you want"): **arm64-v8a ≤ 51 MB, universal ≤ 125 MB**,
the update's growth plus about a megabyte. The other per-ABI APKs keep no budget of their own.

They change together in `build-logic`'s `ApkSize` (checked on every pull request), `release.yml` (checked on every
release), `docs/PERFORMANCE.md` §1 and §8, `REQUIREMENTS.md` (G3, §6.1) and `docs/RELEASE.md`.

## Consequences

- arm64-v8a has about 1.1 MB left and universal about 1.4 MB; `gms` APKs are about 0.2 MB larger than `foss` ones.
- The next addition that grows the native libraries (group video, a later TDLib or NTgCalls) needs the budget
  looked at again, as before.
