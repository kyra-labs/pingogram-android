# Owner setup — what you (the human) need to do

Claude Code can write every line of app code, but these steps require your accounts, your
phone, or your secrets. Do them in order; each takes a few minutes.

## 1. Create the GitHub repository

1. Create `pingogram-android` (public). Do **not** initialize with a README; push this folder.
2. Add a license: **Settings → Add file → Create new file → `LICENSE`** and pick
   **GNU GPLv3** from GitHub's license picker (or Apache-2.0 if you decide so; update
   `REQUIREMENTS.md` §2 accordingly). GitHub inserts the full text for you.
3. Push:
   ```bash
   git init && git add . && git commit -s -m "chore: project scaffold and docs"
   git branch -M main
   git remote add origin git@github.com:<you>/pingogram-android.git
   git push -u origin main
   ```
4. **Settings → General**: enable "Automatically delete head branches"; disable Wiki; enable
   Issues and Discussions.
5. **Settings → Branches → Add rule** for `main`: require PR, require status checks
   (`CI / build`), require linear history, block force pushes.
6. **Settings → Actions → General**: Workflow permissions → "Read and write permissions"
   (needed so `release.yml` and `tdlib.yml` can create releases).
7. Edit `.github/CODEOWNERS` and replace `@OWNER` with your GitHub username.
8. (Optional) Settings → Security → enable Dependabot alerts and security updates;
   `dependabot.yml` is already included for Gradle and Actions.

## 2. Get Telegram API credentials

1. Go to https://my.telegram.org, log in with your phone number.
2. **API development tools** → create an app:
   - App title: `Pingogram`
   - Short name: `pingogram`
   - Platform: Android
   - Description: "Open-source Material 3 Expressive Telegram client for Android"
3. Copy **`api_id`** (number) and **`api_hash`** (32-char hex). Keep them private — they
   identify *you* as the developer and can be banned if abused.
4. Read https://core.telegram.org/api/terms once. You are agreeing to it on behalf of the app.

## 3. Create the release signing key (once, keep forever)

```bash
keytool -genkeypair -v \
  -keystore pingogram-release.jks \
  -alias pingogram \
  -keyalg RSA -keysize 4096 -validity 10000 \
  -dname "CN=Pingogram, O=Pingogram, C=US"
```

- Store `pingogram-release.jks` and both passwords in a password manager **and** an
  offline backup. If you lose it, users cannot update without uninstalling.
- Base64-encode it for GitHub: `base64 -w0 pingogram-release.jks > keystore.b64`
  (macOS: `base64 -i pingogram-release.jks | tr -d '\n' > keystore.b64`).

## 4. Add GitHub Actions secrets

**Settings → Secrets and variables → Actions → New repository secret**:

| Secret | Value |
|---|---|
| `TELEGRAM_API_ID` | from step 2 |
| `TELEGRAM_API_HASH` | from step 2 |
| `SIGNING_KEYSTORE_BASE64` | contents of `keystore.b64` |
| `SIGNING_KEYSTORE_PASSWORD` | keystore password |
| `SIGNING_KEY_ALIAS` | `pingogram` |
| `SIGNING_KEY_PASSWORD` | key password |
| `GOOGLE_SERVICES_JSON_BASE64` | optional, only for `gms` releases (§8b) |
| `TELEGRAM_BOT_TOKEN` | optional: the bot that posts builds to your channels (below) |
| `TELEGRAM_CHANNEL_ID` | optional: the releases channel, `@name` for a public one, `-100…` for a private one (below) |
| `TELEGRAM_DEBUG_CHANNEL_ID` | optional: the debug channel, the same way (below) |
| `TELEGRAM_UPLOAD_API_ID`, `TELEGRAM_UPLOAD_API_HASH` | optional: an `api_id` and `api_hash` of their own for the uploader; without both, it uses `TELEGRAM_API_ID` and `TELEGRAM_API_HASH` (below) |

Delete `keystore.b64` from disk afterwards.

**Builds in your channels (optional).** The release workflow posts every release (an announcement with its changes,
then its 8 APKs) to your releases channel, and CI the debug APKs of every push to `main` to your debug channel
(`docs/RELEASE.md`, "Channel uploads"). Without the token, or without a channel's secret, nothing is posted there.

1. In Telegram, open @BotFather, send `/newbot`, pick a name and a username. It answers with the bot's token: keep it
   like a password (anyone with it can post as the bot), and put it only into the secret `TELEGRAM_BOT_TOKEN`.
2. Add the bot to **both** channels as an administrator with the right to post messages (nothing else).
3. Each channel's id: `@yourchannel` for a public channel. For a private one, forward a post of the channel to
   @userinfobot (or any bot that shows ids) and use the `-100…` number. The releases channel goes into
   `TELEGRAM_CHANNEL_ID`, the debug one into `TELEGRAM_DEBUG_CHANNEL_ID`; debug builds are never posted to the releases
   channel.
4. The bot uploads over MTProto, as Telegram's apps do, which lets it send files of up to 2 GB (the Bot API stops at
   50 MB). MTProto needs an `api_id` and `api_hash`: by default the app's, from step 2. To keep the uploader apart from
   the app (its own entry in my.telegram.org's list, its own limits), create another application at
   https://my.telegram.org and put its values into `TELEGRAM_UPLOAD_API_ID` and `TELEGRAM_UPLOAD_API_HASH`; both or
   neither.
5. To see the format before a release, run **Actions → Telegram post → Run workflow** with a published tag (e.g.
   `v0.6.0`) and the channel `debug`; the same workflow posts a release again if its post failed. The next push to
   `main` posts its debug APKs: check the "debug channel" job of the CI run if nothing arrives (it warns with
   Telegram's reason, e.g. that the bot cannot reach the channel).

## 5. Local development setup

1. Install Android Studio (latest stable), JDK 17, Android SDK 36 + NDK (latest, ≥ r28).
2. Copy `secrets.properties.example` → `secrets.properties` and fill in your `api_id`/`api_hash`.
3. (Optional) create `keystore.properties` as described in `docs/RELEASE.md` for local release builds.
4. Install Claude Code and run it in the repo root. It reads `CLAUDE.md` automatically.
   First prompt suggestion:
   > "Read CLAUDE.md and all docs. Start Milestone M0 from docs/ROADMAP.md. Write a plan first."

## 5b. Add the Google Sans Flex font (once)

1. Go to https://fonts.google.com, search **Google Sans Flex**, "Get font" → "Download all".
2. From the zip, copy the **variable** TTF (the single file with all axes, usually
   `GoogleSansFlex[...].ttf`) to `core/ui/src/main/res/font/google_sans_flex.ttf` — Android
   resource names must be lowercase letters, digits and underscores only.
3. Copy `OFL.txt` from the zip to `core/ui/src/main/assets/licenses/google_sans_flex_OFL.txt`
   (not into `res/font/`: Android rejects non-font files there and the build fails). The
   license allows bundling and redistribution; shipping it in `assets/` keeps it in the APK.
   The open-source licenses screen shows the same text from
   `config/licenses/texts/OFL-1.1.txt`; if the font's license ever changes, update both
   (a unit test compares them).
4. Commit both files (`git add -f` is not needed; they are not ignored). Claude Code will wire
   the type scale when it starts M0.

## 6. Build TDLib once

1. In GitHub → **Actions → Build TDLib → Run workflow** (takes ~30–60 min).
2. It publishes a pre-release `tdlib-<sha>` with `tdlib-<sha>.aar` and `SHA256SUMS.txt`. The
   build downloads the AAR by itself (`docs/TDLIB.md`); CI needs it before the app can build.
3. Copy the AAR's SHA-256 from `SHA256SUMS.txt` into `gradle/libs.versions.toml` →
   `tdlibAarSha256` and commit it. The build verifies every download against it, and fails with
   instructions while it is still `"unpinned"`.
4. Re-run only when you bump `TDLIB_COMMIT` (then update `tdlibAarSha256` too).

The call engine (NTgCalls, `docs/adr/0015-call-media-engine.md`) works the same way: **Actions → Build NTgCalls →
Run workflow** publishes `ntgcalls-<sha>`, whose SHA-256 goes into `ntgcallsAarSha256`. Re-run it only when you bump
`NTGCALLS_COMMIT` in `.github/workflows/ntgcalls.yml` (and `ntgcalls` in the catalog).

## 7. Test on your phone

- Enable developer options + USB debugging, or just sideload the debug APK from the CI
  artifact / the release APK from GitHub Releases ("Install unknown apps" for your browser).
- Log in with your own account. During development, prefer a **second account** or the
  test DC (`useTestDc` toggle in debug) so a mistake can't disturb your main account.

## 8. First release

```bash
git tag -a v0.0.1 -m "v0.0.1" && git push origin v0.0.1
```

Watch **Actions → Release**; the APKs appear under **Releases** in ~10 minutes.

## 8b. Push notifications for the gms flavor (optional)

The `gms` flavor receives new messages through Firebase Cloud Messaging (FCM): Telegram's server sends each one,
encrypted, to Google, which wakes the app (`docs/adr/0004-push-delivery.md`). Telegram sends these pushes with **your**
Firebase project's credentials, so nothing arrives until you have done all of this. GitHub releases (`foss`) need none
of it.

1. **Firebase project.** At https://console.firebase.google.com create a project (e.g. "Pingogram"). Turn Google
   Analytics **off** when asked; the app ships no Analytics.
2. **Android apps.** In the project, *Add app → Android* twice: package `dev.kyralabs.pingogram` (release and
   benchmark builds) and `dev.kyralabs.pingogram.debug` (debug builds; skip it if you never test pushes with a debug
   build). No SHA-1 is needed for FCM.
3. **`google-services.json`.** Download it (*Project settings → General → Your apps*; one file holds both apps) to
   `app/src/gms/google-services.json`. Git ignores it. Not `app/google-services.json`: the build refuses that place,
   because the file would then configure the `foss` builds too. Without the file every `gms` APK or bundle task fails
   with a message pointing here; `foss` builds and `./gradlew check` never need it.
4. **Server credentials for Telegram.** *Project settings → Service accounts → Generate new private key* downloads
   a service-account JSON. At https://my.telegram.org/apps (the app you created in §2) upload it where the page asks for
   **FCM credentials** (Google retired the old "server key" in 2024; if the form still asks for a key, tell Claude Code
   what it shows). This key goes to Telegram **only**: never into the repository, the app, CI secrets or a chat. Delete
   the downloaded copy afterwards, or keep it in your password manager.
5. **CI.** `base64 -w0 app/src/gms/google-services.json` (macOS: `base64 -i … | tr -d '\n'`; Windows PowerShell:
   `[Convert]::ToBase64String([IO.File]::ReadAllBytes("$PWD\app\src\gms\google-services.json")) | Set-Clipboard`)
   and add the output as the repository secret `GOOGLE_SERVICES_JSON_BASE64`. Every release builds the `gms` flavor
   after the `foss` one and writes the file only for it; without the secret the release fails before publishing.
6. **Test** on a phone with Play services, logged in to your account on the **production** DC (the test DC sends no
   pushes): install a `gms` build (`./gradlew :app:installGmsDebug`), log in, swipe the app away from Recents, and
   have someone send you a message. A notification should appear within seconds. If none does, check the
   credentials of step 4 first: the app cannot tell, registration succeeds either way.

## 8c. Google Play (optional)

`docs/PLAY_STORE.md` is the checklist: the listing, the Data safety form and the permission declarations to fill in,
Play App Signing with the release key of §3 (so GitHub and Play builds update each other), and where each release's
bundle is (the workflow artifact `pingogram-X.Y.Z-play`). The automatic upload to the internal track stays off until
you add the repository variable `PLAY_PUBLISH` = `true` and the secret `PLAY_SERVICE_ACCOUNT_JSON` (§7 there).

## 8d. Samsung Galaxy Store and Huawei AppGallery (optional)

`docs/STORES.md` is the checklist: the Samsung Seller Portal and AppGallery Connect accounts step by step (commercial
seller status, identity verification), each store's form answers, the listing texts, the images
(`tools/stores/images.sh`), and which release asset each store gets. AppGallery gets the `foss` build: Huawei push
for a third-party app is not possible yet (`docs/adr/0033-hms-push-for-huawei-phones.md`).

## 9. Decisions only you can make (tell Claude Code when ready)

- Brand seed color and app icon artwork (a designer or an image you provide).
- Whether to subset the Google Sans Flex font (smaller APK) or ship the full file (all scripts).
- Translations platform (Crowdin / Weblate) and F-Droid submission.
- Play Store account (one-time $25) if you want Play distribution.

## 10. Things to never do

- Never paste `api_hash` or keystore passwords into an issue, PR, Discord, or a Claude chat.
- Never run bulk/automated actions against Telegram with your `api_id` — it can get banned.
- Never regenerate the release keystore after publishing.
