# Pingogram — Claude Code project rules

Pingogram is an open-source, third-party **Telegram client for Android**, written from scratch on
**TDLib** with **Kotlin + Jetpack Compose + Material 3 Expressive**. It is **not** a fork of the
official Telegram app; no code from `DrKLO/Telegram` or Telegram X is copied into this repo.

## Project map

- `app` wires everything; `feature:*` holds one screen area each; `core:*` is shared.
- Feature modules never depend on each other. Shared code moves down into `core`.
- Where new code goes: repository interface → `core:domain`; its TDLib implementation →
  `core:data`; a composable used by two features → `core:ui`; routes → `core:navigation`;
  fakes and fixtures → `core:testing`; a screen, its ViewModel and state → its `feature`.
- Flavors: `foss` (no Google services) and `gms`.

## Where the rules live (read on demand, never whole)

Find the section with `grep -n "^## \|^### " <file>` and read only that section.

| Before you touch...                         | Read the matching section of |
|---------------------------------------------|------------------------------|
| Any feature                                 | `REQUIREMENTS.md`            |
| A module, layer, repository, navigation     | `docs/ARCHITECTURE.md`       |
| Any composable, color, type, shape, motion  | `docs/DESIGN_SYSTEM.md`      |
| `core:tdlib` or a TDLib call                | `docs/TDLIB.md`              |
| Lists, images, animations, startup          | `docs/PERFORMANCE.md`        |
| Tests, goldens, CI shards                   | `docs/TESTING.md`            |
| Versions, signing, tags                     | `docs/RELEASE.md`            |
| What to work on next                        | `docs/ROADMAP.md`            |

Path-scoped rules for Kotlin, Android resources, tests and docs live in `.claude/rules/` and load
when you open a matching file. Hooks in `.claude/hooks/` enforce the git rules below.

## Hard rules (never break these)

1. **Secrets.** Never write `api_id`, `api_hash`, keystore passwords, or tokens into source,
   Gradle files, docs, logs, tests or commit messages. They come only from `secrets.properties`
   (git-ignored) locally and from GitHub Actions secrets in CI. If a value is missing, fail the
   build with a clear message; never hard-code a fallback.
2. **Branding.** The word "Telegram" never appears in: app name, `applicationId`, package names,
   launcher icon, notification channel names, or store/release titles. It is allowed only in
   descriptive text such as "a Telegram client" or "Telegram account". The app is **Pingogram**,
   package root `dev.kyralabs.pingogram`.
3. **Telegram API Terms of Service** (https://core.telegram.org/api/terms) are mandatory:
   no spam/automation features, no hiding or altering sponsored messages in channels, no
   scraping, no circumventing limits, no misrepresenting the client as official. Identify the
   client honestly in `SetTdlibParameters` (`deviceModel`, `applicationVersion`).
4. **Compose only, Material 3 Expressive only.** No XML layouts, no AppCompat/Material
   Components (View) widgets, no Material 2 (`androidx.compose.material`). UI is 100%
   `androidx.compose.material3` under `MaterialExpressiveTheme`, following **every** rule in
   `docs/DESIGN_SYSTEM.md`: color roles only, the full type scale, the shape scale +
   `MaterialShapes`, `MotionScheme.expressive()` springs, and the Expressive component set.
   No literal colors, sizes, durations or easing in feature code (detekt rule
   `PingogramHardcodedToken`). Exceptions: the manifest, splash theme, and framework-required
   resources (icons, strings, notification icons).
4b. **Typography is Google Sans Flex (variable).** Bundled under OFL in `core:ui`; every text
   style sets `wght`, `opsz` (= size), `ROND`, `GRAD` (0; 25 with high contrast) and `slnt` through
   `FontVariation.Settings` as specified in `docs/DESIGN_SYSTEM.md` §3. No other font family
   except `FontFamily.Monospace` for code and system fallbacks for missing scripts.
4c. **Performance is a requirement.** Follow `docs/PERFORMANCE.md`: immutable UI state,
   keyed lazy lists, animations in the draw/layout phase, explicit image sizes, cancellable
   TDLib calls, no polling. A PR that regresses a benchmark budget by > 10% does not merge.
5. **Kotlin only.** No Java sources except the generated TDLib JNI bindings (`org.drinkless.tdlib`).
6. **Architecture.** Unidirectional data flow: `Screen(Composable) → ViewModel(UiState, events) →
   Repository/UseCase → TdlibClient`. Composables never call TDLib directly. No `GlobalScope`.
   No blocking calls on the main thread.
7. **Dependencies** go through `gradle/libs.versions.toml` only, pinned to exact versions. Use
   the latest *stable* AndroidX/Compose versions; alpha/beta only when a required M3 Expressive
   component exists nowhere else, and then note it in `CHANGELOG.md`.
8. **Every PR**: compiles, `./gradlew check` passes (ktlint/detekt, unit tests), new ViewModels
   have unit tests, new screens have a Compose preview, `CHANGELOG.md` "Unreleased" is updated
   for user-visible changes.
9. **No copyrighted assets.** No Telegram logos, stickers, sounds, or icon sets. Use Material
   Symbols (Apache 2.0) and our own artwork only.
10. **Privacy.** No analytics SDKs, no crash reporters that send data off-device unless the
    owner explicitly adds one. No ad SDKs. Ever.

## Working style

- Start by stating the milestone/feature, the branch name you will create, and the files you
  expect to touch.
- Ask before starting only when the task is ambiguous or falls under "Ask the owner before".
  Otherwise state the plan in a few bullets and proceed; do not wait for approval.
- If blocked (a failing check you cannot fix, a missing decision), push what you have as a
  draft PR, say exactly what is blocking, and stop. Never work around it by weakening a rule,
  a test or a budget.
- Do only what was asked. No drive-by refactors, renames, reformatting or dependency bumps in
  a feature PR; propose them as a separate task.
- Prefer editing existing files over creating parallel ones. Delete dead code.
- Never guess an API. TDLib: check `TdApi.java` in `core/tdlib/libs`. Material 3 Expressive:
  check the current Compose Material 3 API reference. Mark experimental opt-ins with the exact
  annotation required, in one `@OptIn` at the composable, never project-wide.
- Report honestly: say which checks you ran and their result, and which you could not run.
  Never tick a PR checklist box you did not verify.
- If the docs conflict with the code, the docs win: fix the code, or propose the doc change in
  the PR description.
- Never modify `.github/workflows/release.yml` signing steps without saying so explicitly.
- When the conversation is compacted, keep: the branch name, open PR links, files changed,
  failing checks, and decisions the owner made in this session.

## Git workflow

### Identity (before the first commit of every session)

    git config user.name  "YamTee"
    git config user.email "thalha.moha@gmail.com"

- Commit with `git commit -s`. The only trailer is
  `Signed-off-by: YamTee <thalha.moha@gmail.com>`.

### No AI attribution, anywhere

- Never write "Generated with Claude Code", "Generated by Claude", "Co-Authored-By: Claude",
  `Claude-Session:`, a claude.ai session link, a robot emoji, or any other mention of Claude,
  Anthropic or an AI tool as the author.
- This covers commit messages, the squash-merge message, PR titles and descriptions, PR and
  issue comments, review replies, code comments, KDoc, `CHANGELOG.md`, docs and release notes.
- Never sign off or commit as "Claude" or with an `@anthropic.com` address.
- Before pushing, run `git log origin/main..HEAD --format=%B` and remove any such line.
  Before opening or merging a PR, reread its description for the same.

### Branches

- Never commit to `main`. Start every task from fresh `origin/main`:
  `git fetch origin && git switch -c <branch> origin/main`.
- Name: `<type>/<scope>-<short-kebab-description>`, e.g. `fix/chat-drop-zone-font-scale`.
  Lowercase, hyphens, at most 50 characters. Hotfix: `hotfix/X.Y.Z` from the tag.
- If the session starts on an auto-named branch (`claude/...`, `ccr-...`), rename it
  (`git branch -m <branch>`) before the first push.
- Never push `worktree-agent-*`, `debug/*` or other scratch branches.
- A branch lives for one PR. Once its PR is merged, never commit to it again.

### Commits (Conventional Commits 1.0)

- `<type>(<scope>): <subject>`
- Types: `feat` `fix` `perf` `refactor` `test` `docs` `build` `ci` `chore`.
  Gradle/build-logic changes are `build`. Dependency bumps are `chore(deps)`.
- Exactly one scope: the module name without `core:`/`feature:` (`chat`, `chats`, `auth`,
  `media`, `calls`, `settings`, `tdlib`, `ui`, ...) or `app`, `widget`, `stories`, `deps`,
  `design`, `benchmark`, `release`. A change that needs two scopes is two commits, or has
  no scope.
- Subject: imperative ("add", "fix", "show"), lowercase start, no period, at most 72
  characters including the prefix. No "board 23", "audit A11" or "the owner" in the subject.
- Body (when the why is not obvious): why, not what; wrap at 100; `Closes #123`.
- Breaking change: `!` after the scope plus a `BREAKING CHANGE:` footer.
- Each commit compiles. No "wip", "fix review", "address comments".

### Keeping a branch current

- Before the first push: `git rebase origin/main`.
- After the first push: `git merge origin/main`, only for a conflict or when CI needs it.
  Never rebase or amend pushed commits, never `git push --force`, never `--no-verify`.

### Pull requests

- One task = one branch = one PR.
- Several independent tasks in one session: one at a time. For each, a new branch from fresh
  `origin/main` and its own PR, pushed before the next one starts. Never two tasks in one PR,
  never one task's branch cut from another's.
- Dependent tasks: one PR if it fits the size limit. Otherwise stack them: branch the second
  from the first, open its PR against the first branch, write "Depends on #N" at the top.
- Size: under about 400 changed lines, not counting goldens and generated files. Split larger
  work by layer (domain + data, then UI) or by screen state.
- Title: exactly the commit format; PRs are squash-merged, so the title becomes the commit.
- Description: `.github/PULL_REQUEST_TEMPLATE.md`, every section filled in, plus what to test
  by hand on a phone.
- Before pushing: `./gradlew ktlintFormat`, then `./gradlew check`. If `check` could not run
  or does not pass, open the PR as a draft and say which task failed.
- Push with `git push -u origin <branch>`, open the PR, report its link.
- Review feedback: new commits on the same branch, then reply to each comment.
- Never close an issue, create or move a tag, or delete a remote branch.

### Merging into `main` (the session does this itself)

After opening a PR, check it and merge it yourself. Do every step in order.

**1. Decide whether this PR is yours to merge.** Merge only when ALL of these hold; otherwise
leave the PR open, say which condition failed, and stop:

- `./gradlew check` passed in this session, and the PR is not a draft.
- It is within the size limit and its base branch is `main` (not stacked).
- It touches none of: `.github/`, `.claude/`, `CLAUDE.md`, `core/tdlib/`,
  `gradle/libs.versions.toml`, any `AndroidManifest.xml`, any `proguard-rules.pro`,
  `benchmark/budgets.json`, `benchmark/baselines/`, signing or release files.
- It needs nothing from "Ask the owner before", makes no ADR-level decision, and has no open
  question for the owner.

**2. Review the PR as a reviewer, not as its author.** Read the whole diff
(`git diff origin/main...HEAD`) and confirm each point; fix anything that fails with a new
commit, then start this step again:

- Only what the task asked for; no unrelated files, no debug code, no leftover logging.
- Every hard rule in this file; the "Definition of done" list; tests exist for new logic.
- No new `@Suppress`, no weakened test, no golden re-recorded beyond the intended change.
- Title, description and `CHANGELOG.md` match what the diff actually does.

**3. Wait for CI on the PR's latest commit.** Poll every 2 minutes, for at most 75 minutes:

    SHA=$(git rev-parse HEAD)
    gh api repos/kyra-labs/pingogram-android/commits/$SHA/check-runs \
      --jq '.check_runs[] | select(.name=="build") | "\(.status) \(.conclusion)"'

- `completed success` → continue. Anything else that is `completed` → read the failing job,
  fix it with a new commit, push, and go back to step 2.
- Never merge while a check is pending, failed, cancelled or skipped, and never re-run a
  failed check hoping it passes.
- No result after 75 minutes → leave the PR open and report that.

**4. Merge with a squash, pinned to the commit you checked:**

    gh api -X PUT repos/kyra-labs/pingogram-android/pulls/<N>/merge \
      -f merge_method=squash \
      -f sha="$SHA" \
      -f commit_title="<PR title> (#<N>)" \
      -f commit_message="<why, one short paragraph, or empty>

    Signed-off-by: YamTee <thalha.moha@gmail.com>"

- Always `squash`; never a merge commit or a rebase merge. Use this REST call: `gh pr merge`
  does not work in cloud sessions.
- If the call is refused (the branch is behind, a conflict, a rule), run
  `git merge origin/main`, push, and go back to step 3. Never force anything.

**5. After the merge:** confirm it with
`gh api repos/kyra-labs/pingogram-android/pulls/<N> --jq .merged`, report the PR link and the
commit on `main`, and stop using that branch. For the next task, start again from fresh
`origin/main`. Never merge a PR this session did not open, and never push to `main` directly.

## Commands

```bash
./gradlew assembleDebug              # debug APK → app/build/outputs/apk/debug/
./gradlew check                      # ktlint + detekt + unit tests (must pass before finishing)
./gradlew testDebugUnitTest          # unit tests only
./gradlew ktlintFormat               # auto-format Kotlin
./gradlew :app:connectedDebugAndroidTest   # instrumented tests (needs emulator/device)
./gradlew assembleRelease            # needs signing config (see docs/RELEASE.md)
./gradlew dependencyUpdates          # list newer dependency versions (ben-manes plugin)
./gradlew :feature:chat:check          # one module while iterating (any module path)
./gradlew :feature:chat:testDebugUnitTest --tests "*ChatViewModelTest"   # one test class
./gradlew :feature:chat:recordRoborazziDebug   # re-record one module's goldens
./gradlew ktlintFormat && ./gradlew check       # once, before pushing
```

If `secrets.properties` is missing in a cloud session, create it with CI's placeholders
(`TELEGRAM_API_ID=0`, `TELEGRAM_API_HASH=00000000000000000000000000000000`). Never a real value.

## Definition of done for a feature

- [ ] Behaviour matches `REQUIREMENTS.md`; edge cases (offline, FLOOD_WAIT, logged-out mid-flow) handled
- [ ] Passes the per-screen M3 Expressive audit (`docs/DESIGN_SYSTEM.md` §12): tokens only, Google Sans Flex styles, shapes, motion-scheme springs, listed components; light/dark/dynamic/AMOLED/high-contrast OK
- [ ] Works at font scale 200%, with TalkBack, and in landscape / tablet width (adaptive)
- [ ] Unit tests for ViewModel + repository; preview for every new screen state (loading/empty/error/content)
- [ ] No new lint warnings; no new `@Suppress` without a comment explaining why
- [ ] `docs/PERFORMANCE.md` §10 checklist done; benchmark for the screen unchanged or better
- [ ] `CHANGELOG.md` updated; docs updated if architecture changed

## Ask the owner before

- Adding any new third-party dependency over ~1 MB or any SDK that touches the network
- Changing `minSdk`, `targetSdk`, TDLib version, or the signing/release workflow
- Adding permissions to the manifest
- Anything that could conflict with Telegram's API terms
