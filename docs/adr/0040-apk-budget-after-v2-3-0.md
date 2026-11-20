# 0040. APK size budget after v2.3.0

- Status: accepted (owner, 2026-11-20)
- Supersedes the numbers of `docs/adr/0038-apk-budget-for-tdlib-1-8-70.md`; its reasoning stands.

## Context

ADR 0038 set the budgets at 51 MB for the arm64-v8a APK and 125 MB for the universal one, from TDLib 1.8.70's APKs
(49.9 and 123.6 MB). The features merged after v2.3.0 grew the app's own code and resources, with no native library
changed:
- backup and restore;
- the Channel feed;
- the settings redesign and its filled icons;
- photo and Telegram wallpapers;
- story albums.

CI's `apk size` job (`fossBenchmarkRelease`, a megabyte being 1,048,576 bytes) then measured arm64-v8a at 51.0 MB, a
few kilobytes over its budget, and universal at 124.7 MB. Every pull request failed that check, whatever its own change.

## Decision

The owner raised the budgets (2026-11-20, "raise + audit"): **arm64-v8a ≤ 53 MB, universal ≤ 128 MB**. That is about
2 MB and 3.3 MB above today's APKs, room for the next features. As in ADR 0038, they change together in `ApkSize`,
`ci.yml`, `release.yml`, `docs/PERFORMANCE.md` §1, `REQUIREMENTS.md` and `docs/RELEASE.md`.

The raise comes with a size audit, as a separate task:
- compare v2.3.0's arm64-v8a APK with `main`'s, by dex, resources and assets;
- name what grew and by how much;
- trim what isn't worth its bytes;
- record the findings in `docs/PERFORMANCE.md` §8.

## Consequences

- PRs pass `apk size` again; the release workflow's gate uses the same numbers.
- The budget still catches growth: about 2 MB of arm64-v8a headroom is a few features, not a library.
- A change that grows the native libraries (a later TDLib or NTgCalls, group video) still needs the budget looked at
  again, in its own ADR.
