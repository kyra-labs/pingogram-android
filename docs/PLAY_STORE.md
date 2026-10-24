# Google Play

What the owner needs to publish the `gms` build on Google Play: the Data safety form, the permission declarations,
the store listing, the content rating, Play App Signing with the existing key, and how a release reaches Play. The
answers are derived from the code as of 0.5.0; when a feature changes what the app reads or sends, update this file in
the same PR (`docs/audit/v1-readiness.md` lists it as a release check).

The `foss` build stays on GitHub (and later F-Droid). Both builds share the application id `dev.kyralabs.pingogram` and
the signing key, so a phone moves between them without losing its data (`docs/RELEASE.md`).

## 1. App details

| Field | Value |
|---|---|
| App name | **Pingogram** (never "Telegram" in the name, `CLAUDE.md` rule 2) |
| Package | `dev.kyralabs.pingogram` |
| Category | Communication |
| Contact email | the owner's support address (Play shows it publicly) |
| Website | `https://github.com/kyra-labs/pingogram-android` |
| Privacy policy | `https://github.com/kyra-labs/pingogram-android/blob/main/PRIVACY.md` (§2 in prose); update both together |
| App access | Pingogram needs a Telegram account. Review needs a test account: give Play's review team a phone number and the login code instructions in "App access" (a number of the owner's that receives codes in the official app, or a Telegram test account is **not** usable: Play reviews against the production data centers) |
| Ads | **No ads.** Sponsored messages in channels are Telegram's, shown as Telegram requires (`REQUIREMENTS.md` §2), not ads Pingogram serves or earns from: answer "No, my app does not contain ads", and say so in the review notes |

## 2. Data safety form

Google's terms: data is **collected** when the app transmits it off the device, whoever receives it; it is **shared**
when it is transferred to a third party, except for transfers the user starts and expects (sending a message), to a
service provider acting for the developer, or for legal reasons. Pingogram has no server: everything it sends goes to
Telegram (through TDLib, `docs/TDLIB.md`), which the user signs in to and whose service the app is a client of, and,
in the `gms` build only, a push token to Firebase Cloud Messaging. The developer receives nothing.

### 2.1 Overview questions

| Question | Answer | Why (code) |
|---|---|---|
| Does your app collect or share any of the required user data types? | **Yes** | messages, contacts (opt-in), location (when sent), media, account details go to Telegram |
| Is all of the user data collected by your app encrypted in transit? | **Yes** | MTProto 2.0 by TDLib to Telegram's data centers; FCM over TLS; `networkSecurityConfig` refuses cleartext (`REQUIREMENTS.md` §6.2) |
| Do you provide a way for users to request that their data is deleted? | **Yes** | account deletion: in the app (Settings → Privacy and security → Delete account) and on the web. **Delete account URL** for the form: `<website>/delete-account` (planned on the Pingogram website, content in `docs/website/delete-account.md`; it must be published, and its domain filled in here, before the Play listing); the page leads to Telegram's own `https://my.telegram.org/deactivate`. Telegram deletes the account's messages and contacts; Pingogram holds no account data. Opt-in contacts sync deletes the imported contacts when turned off (`docs/adr/0011-contacts-sync.md`) |
| Has your app successfully completed an independent security review (MASA)? | No | |

### 2.2 Data types

Every type below is **collected, not shared** (sent to the service the user signed in to, at the user's action, for the
app's functionality), **processed ephemerally: no** (Telegram stores it), and **required or optional** as listed.
Purpose: **App functionality** only; never analytics, advertising, personalization, fraud prevention or account
management by the developer.

| Category · type | Collected | Required? | What exactly, and when (code) |
|---|---|---|---|
| Personal info · Name | Yes | Required | first and last name at registration (`feature:auth`, FR-A5) and in Edit profile |
| Personal info · Phone number | Yes | Required | the login number (FR-A2); a contact's number when the user adds one (`importContacts`) |
| Personal info · User IDs | Yes | Required | the Telegram account the app signs in to |
| Personal info · Other info | Yes | Optional | bio, username, birthday the user sets in Edit profile |
| Messages · Other in-app messages | Yes | Required | messages the user sends, through TDLib; secret chats end-to-end encrypted on the device |
| Photos and videos · Photos, Videos | Yes | Optional | only what the user sends (Photo Picker, no storage permission), a profile or group photo they set |
| Audio · Voice or sound recordings | Yes | Optional | voice messages the user records and sends (`RECORD_AUDIO`); call audio is streamed end-to-end encrypted and never stored (§3) |
| Audio · Music files, Other audio files | Yes | Optional | audio files the user sends |
| Files and docs | Yes | Optional | files the user sends |
| Location · Approximate, Precise | Yes | Optional | only when the user sends their location from the attach menu (`docs/adr/0006-platform-location-manager.md`); never in the background |
| Contacts | Yes | Optional | only with contacts sync turned on (off by default): names and phone numbers of the phone's contacts, sent to Telegram's contact import when turned on, on "Sync now" and when Contacts opens (`docs/adr/0011-contacts-sync.md`) |
| App activity · Other user-generated content | Yes | Optional | reactions, polls, stickers sent, group and channel content the user creates |
| Device or other IDs | Yes (`gms` only) | Required in `gms` | the Firebase Cloud Messaging token, registered with Telegram (`registerDevice`, encrypted) so pushes reach the device (`docs/adr/0004-push-delivery.md`); the `foss` build sends none |
| App info and performance · Crash logs, Diagnostics | **No** | | nothing is sent automatically: no crash reporter, no analytics (`CLAUDE.md` rule 10). "Send feedback" prepares a report on the device that the user shares themselves through Android's share sheet or a GitHub issue they submit; Google counts a transfer the user starts and sees as not collection by the app |
| Financial info, Health and fitness, Web browsing, Calendar, Emails, SMS | No | | |

Not collected despite a permission: the camera (`CAMERA`) scans QR codes, decoded on the device, nothing kept or sent
(`docs/adr/0012-camerax-and-zxing-qr-scanner.md`), and in video calls is streamed to the other person, end-to-end
encrypted, not collected; the microphone during calls is streamed, not collected
(Google: data processed only in real time to provide the function and not stored is "processed ephemerally"; if the
form asks about calls, answer audio and video "collected, processed ephemerally").

### 2.3 Security practices

- Data is encrypted in transit: yes.
- Users can request deletion: yes (§2.1).
- Committed to Play's Families policy: no (not for children, §5).

## 3. Permission declarations

Play Console asks for declarations of the permissions below (App content → Sensitive app permissions, Foreground service
types). The texts are ready to paste; each feature also explains itself in the app before Android asks.

| Permission | Play Console form | Justification to paste | Demo video shows |
|---|---|---|---|
| `USE_FULL_SCREEN_INTENT` | Full-screen intent (Android 14+) | "Pingogram is a messaging app with voice calls. When someone calls, a full-screen notification shows the incoming call over the lock screen so the user can answer or decline it, as phone apps do. It is used for incoming calls only, never for messages or other notifications." Category: **Calling** | an incoming call on a locked phone, answering with the slider |
| `FOREGROUND_SERVICE_PHONE_CALL` (type `phoneCall`) | Foreground service | "Keeps a voice call the user started or answered running while the app is in the background or the screen is locked, with an ongoing call notification and Android's Telecom integration (self-managed ConnectionService through Jetpack core-telecom). It starts when a call rings or is placed and stops when it ends." | placing a call, leaving the app, the ongoing call notification, hanging up from it |
| `FOREGROUND_SERVICE_MICROPHONE` (type `microphone`) | Foreground service | "During a voice call the user is talking in, the microphone keeps capturing their voice while the app is in the background or the screen is off. Only while a call is connected, never otherwise." | the same video, talking with the screen off |
| `FOREGROUND_SERVICE_CAMERA` (type `camera`) | Foreground service | "During a video call, while the user has turned their camera on, the camera keeps sending their video to the other person when they leave the call screen (picture-in-picture, the lock screen). Only while the user's camera is on in a call, never otherwise." | a video call, turning the camera on, leaving the app with picture-in-picture |
| `FOREGROUND_SERVICE_MEDIA_PROJECTION` (type `mediaProjection`) | Foreground service | "During a call, after the user taps Share screen and agrees in Android's screen capture dialog, the screen keeps being shared with the people in the call while the user goes to other apps, which is what sharing a screen is for. The notification and a "You're sharing your screen" pill say so, with Stop. Only while the user shares, never otherwise." (`docs/adr/0026-screen-sharing.md`) | a call, Share screen, Android's consent dialog, going to another app, Stop in the notification |
| `FOREGROUND_SERVICE_DATA_SYNC` (type `dataSync`) | Foreground service | "Uploads of files the user sends and downloads the user taps continue when they leave the app, with a notification showing the progress and Cancel. It runs only while such a transfer is under way and stops 2 seconds after the last one; automatic downloads never start it." | sending a large file, leaving the app, the Transfers notification |
| `MANAGE_OWN_CALLS` | none (normal permission) | for the Data safety and review notes: "registers Pingogram's calls with Android's Telecom as a self-managed calling app, so headsets, cars and watches can answer and hang up, and a phone call puts a Pingogram call on hold." | |
| `RECORD_AUDIO` | none; prominent disclosure in the app | voice messages and calls; the app explains before Android asks | |
| `CAMERA` | none; prominent disclosure | the QR code scanner, and video calls when the user turns the camera on | |
| `ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION` | none (foreground only; **no** `ACCESS_BACKGROUND_LOCATION`) | sending the current location from the attach menu | |
| `READ_CONTACTS` | none; prominent disclosure (Play's User Data policy) | opt-in contacts sync; the rationale sheet says what is sent before Android asks, and it is off by default | |
| `BLUETOOTH_CONNECT` | none | names of Bluetooth headsets to move a call to (Android 12+) | |
| `POST_NOTIFICATIONS`, `POST_PROMOTED_NOTIFICATIONS` | none | message notifications; Live Updates of transfers on Android 16 | |

Not in the `gms` build: `FOREGROUND_SERVICE_SPECIAL_USE` ("Keep connected", `foss` only, which Play would question).
No `QUERY_ALL_PACKAGES`, no `READ_MEDIA_*` (Photo Picker), no exact alarms, no accessibility service, no SMS or call
log permissions.

## 4. Store listing

Never "Telegram" in the title or as the developer name; "a Telegram client" and "your Telegram account" only as
description (`REQUIREMENTS.md` §2). Play's metadata policy forbids implying an affiliation: keep the disclaimer.

- **App name** (30 characters): `Pingogram`
  (or `Pingogram: Material messenger`, 29)
- **Short description** (80): `A fast, private Material You client for your Telegram account. Open source.` (75)
- **Full description** (4000):

  ```
  Pingogram is a client for your Telegram account that looks and feels like an app Google would make: Material 3
  Expressive design, your wallpaper's colors, smooth motion, and layouts that fit phones, foldables and tablets.

  Everything you expect from your chats
  • Chats, groups and channels with folders, archive, pinned chats and a search that finds chats, people, messages,
    media and links
  • Photos, videos, GIFs, stickers (animated too), voice messages, files, polls, locations and contacts
  • Reactions, replies, forwarding, editing, scheduled and silent messages, message threads and forum topics
  • Secret chats, end-to-end encrypted on your device
  • Voice calls, end-to-end encrypted, that ring like phone calls, work with your headset, car and watch
  • Share from any app straight into a chat

  Made for Android
  • Material You colors from your wallpaper, or one of 13 color themes, light, dark and true black
  • Notifications grouped per chat, with reply and mark as read
  • Passcode lock with fingerprint or face unlock
  • Large text, TalkBack and right-to-left languages supported

  Private by design
  • No ads of its own, no analytics, no tracking. Pingogram talks only to Telegram's servers
  • Open source under the GPL: read every line on GitHub

  Pingogram is an unofficial app. It is not affiliated with, endorsed by or sponsored by Telegram. It uses Telegram's
  official library, TDLib, and you sign in with your Telegram account. Sponsored messages that Telegram shows in some
  public channels appear as Telegram requires.
  ```

  Keep each bullet true for the version on Play: drop "voice calls" from a listing whose build has none.
- **Screenshots**: Play takes 2–8 phone screenshots, PNG or JPEG, 320–3840 px on each side, the long side at most
  twice the short side. `docs/screenshots/` are 540 × 1171 (2.17:1) and must be padded to 9:16 first: run
  `python3 tools/play/screenshots.py` (writes `build/play/screenshots/`: each centred on its own background at 9:16,
  659 × 1171, nothing scaled). Phone set, in this order: `chats-light`, `chat-light`, `composer-light`, `reactions-dark`,
  `search-light`, `appearance-light`, `settings-dark`, `welcome-light`; a call screen once a device capture exists
  (`feature/calls` goldens are 411 dp phones: capture from a device for the listing). Tablet (7" and 10"):
  `tablet-light`, `tablet-dark` (1080 × 1146, already within 2:1).
- **Feature graphic** (1024 × 500): the owner's artwork (`art/`), the mark on `#2156C4`; not generated here.
- **App icon** (512 × 512): exported from `art/` (the adaptive icon's foreground on white), PNG without alpha.

## 5. Content rating and target audience

- **IARC questionnaire**: category "Communication / social". Users can interact and exchange content: **yes**. Shares
  the user's location with others: **yes** (only when the user sends it). Digital purchases: **no**. Unrestricted
  internet access through the app (a browser): **no** (links open in the user's browser). Violence, sexuality,
  gambling, drugs in the app's own content: **no**; user-generated content may contain anything, which the
  "users interact" answer covers. Expected result: Teen / PEGI 12 / USK 12 with "Users Interact, Shares Location".
- **Target audience**: 13 and older is Telegram's own minimum age (some countries require older); choose **13–15,
  16–17 and 18+**, not under 13. "Appeals to children": no. The app is not in the Families program.
- **News app**: no. **COVID-19 contact tracing**: no. **Government app**: no. **Financial features**: none.

## 6. Play App Signing with the existing key

GitHub's APKs are signed with the owner's release key (`docs/OWNER_SETUP.md` §3). Play must sign with the **same key**,
or a phone with the GitHub build cannot update from Play (and the other way round). So Play App Signing is set up with
that key, not a key Google generates:

1. Play Console → the app → Test and release → App integrity → Play app signing → **Use existing app signing key from
   Java keystore**. Download Google's encryption public key and the `pepk.jar` it links, then, on the computer that
   holds `pingogram-release.jks` (never in CI):

   ```bash
   java -jar pepk.jar --keystore=pingogram-release.jks --alias=pingogram \
     --output=pingogram-signing-key.zip --include-cert --rsa-aes-encryption \
     --encryption-key-path=encryption_public_key.pem
   ```

   Upload `pingogram-signing-key.zip`, then delete it.
2. **Upload key**: the simplest choice is the same key (the release workflow already signs the `.aab` with it). A
   separate upload key is safer (it can be reset through Play if it leaks, the app signing key cannot): generate one
   as in `OWNER_SETUP.md` §3, upload its certificate (`keytool -export -rfc -keystore upload.jks -alias upload
   -file upload_cert.pem`), and store it as the `SIGNING_*` secrets of a separate GitHub environment for Play. Then
   the GitHub APKs keep the app signing key and the `.aab` gets the upload key: that needs `release.yml`'s signing
   steps changed, which is the owner's call (`CLAUDE.md`).
3. Version codes: the `.aab` has no ABI suffix (`docs/RELEASE.md`: `MAJOR * 1_000_000 + MINOR * 10_000 + PATCH * 100`),
   lower than the same version's per-ABI APKs, higher than any earlier version's. A phone that sideloaded 1.0.0 from
   GitHub gets Play's update from 1.0.1 on, not 1.0.0 again.

## 7. Building and uploading

- Every tag builds the `gms` bundle (signed with the release key, R8, the Baseline Profile) in `release.yml`'s
  `build-and-publish` job and attaches it to the run as the artifact `pingogram-X.Y.Z-play`
  (`pingogram-X.Y.Z-gmsRelease.aab`). The owner downloads it and uploads it in Play Console → Test and release →
  Internal testing, then promotes it.
- **Optional automatic upload, off by default**: the job `upload-to-play` uploads that bundle to the **internal** track
  with Gradle Play Publisher (`com.github.triplet.play`, applied by `:app` only when the Gradle property
  `pingogram.play.serviceAccount` names a key file, so no other build loads it). It runs only when
  - the repository variable `PLAY_PUBLISH` is `true` (Settings → Secrets and variables → Actions → Variables), and
  - the secret `PLAY_SERVICE_ACCOUNT_JSON` holds the JSON key of a Google Cloud service account that Play Console has
    invited with "Release to testing tracks" for the app (Play Console → Users and permissions → Invite new users →
    the service account's email; the key from Google Cloud → IAM → Service accounts → Keys).

  The key is written to the runner's temporary directory for that one step and deleted after it; nothing about it is
  in the repository. Promotion to closed, open or production tracks stays manual in Play Console.
- Locally: `./gradlew :app:publishGmsReleaseBundle --artifact-dir <dir with the .aab>
  -Ppingogram.play.serviceAccount=/path/to/key.json` does the same.
- Release notes for Play (500 characters per language) come from the version's `CHANGELOG.md` section, shortened by
  the owner in Play Console.

## 8. Before the first Play release

- **Account deletion page on the web** (Play's account deletion policy): the app's Delete account page is in
  (Settings → Privacy and security). The owner publishes `docs/website/delete-account.md` on the Pingogram website and
  puts its address in §2.1 and in the Data safety form's "Delete account URL".
- **Privacy policy**: `PRIVACY.md` once this is merged; its GitHub URL is the policy's address.
- **Screenshots** padded to 9:16 (§4).
- **Telegram's terms**: Telegram allows third-party clients built on its API (`https://core.telegram.org/api/terms`)
  but a store may still receive a trademark complaint; the listing's disclaimer and name follow §4 to keep that unlikely.
