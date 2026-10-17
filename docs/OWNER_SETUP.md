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

Delete `keystore.b64` from disk afterwards.

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
4. Commit both files (`git add -f` is not needed; they are not ignored). Claude Code will wire
   the type scale when it starts M0.

## 6. Build TDLib once

1. In GitHub → **Actions → Build TDLib → Run workflow** (takes ~30–60 min).
2. It publishes a pre-release `tdlib-<sha>` with `tdlib-<sha>.aar`. Claude Code's `fetchTdlib`
   Gradle task downloads it; CI needs this to exist before the app can build.
3. Re-run only when you bump `TDLIB_COMMIT`.

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

## 9. Decisions only you can make (tell Claude Code when ready)

- Final license (GPL-3.0 vs Apache-2.0) — before the first public release.
- Brand seed color and app icon artwork (a designer or an image you provide).
- Whether to subset the Google Sans Flex font (smaller APK) or ship the full file (all scripts).
- Whether to ship a `gms` (FCM push) build in addition to `foss`.
- Translations platform (Crowdin / Weblate) and F-Droid submission.
- Play Store account (one-time $25) if you want Play distribution.

## 10. Things to never do

- Never paste `api_hash` or keystore passwords into an issue, PR, Discord, or a Claude chat.
- Never run bulk/automated actions against Telegram with your `api_id` — it can get banned.
- Never regenerate the release keystore after publishing.
