# Samsung Galaxy Store and Huawei AppGallery

What the owner needs to publish Pingogram on Samsung's Galaxy Store and Huawei's AppGallery:
- which build each store gets;
- the developer accounts;
- each store's form answers;
- the listing texts and images;
- how a release reaches the stores.

It follows `docs/PLAY_STORE.md` and reuses its answers rather than repeating them: when a feature changes what the app
reads, sends or asks for, update `PLAY_STORE.md` and check this file in the same PR.

**Sources.** The store facts below were checked on 2026-10-29.
- Samsung's are from its developer pages (developer.samsung.com), read directly.
- Huawei's pages (developer.huawei.com) could not be fetched from here. Their facts come from the text of those
  official pages as search results quoted it, with the page named.
- What neither confirmed is marked **Unconfirmed**: check it in the console before relying on it.

## 1. Which build each store gets

| Store | Build | File from the GitHub release's assets | Why |
|---|---|---|---|
| Galaxy Store | `gms` | `pingogram-X.Y.Z-universal-gms.apk` | Samsung phones have Google services, so `gms` gets push notifications through Firebase (`docs/adr/0004-push-delivery.md`). Universal, because the store serves one APK to every phone |
| AppGallery | `foss` | `pingogram-X.Y.Z-universal.apk` | Huawei phones since 2019 have no Google services, so `gms` would get no pushes there. `foss` runs on TDLib's own connection and "Keep connected" (§4.3). An `hms` build with Huawei's push is blocked: Telegram offers third-party apps no way to register Huawei push credentials (`docs/adr/0033-hms-push-for-huawei-phones.md`). AppGallery keeps `foss` until that changes |

**Every build carries the owner's release key** (`docs/OWNER_SETUP.md` §3), so a phone moves between the stores and
GitHub, and between `foss` and `gms`, without losing its data (`docs/RELEASE.md`):
- **Upload APKs, not bundles.** Both stores re-sign a bundle with a key they hold:
  - Galaxy Store: "Galaxy Store must manage the signing key" for an AAB (Seller Portal's launch guide);
  - AppGallery: App Signing is required for an AAB and optional for an APK (AppGallery Connect, "App Signing").
- **On AppGallery, leave App Signing off.** An APK keeps the release key as it is.
- **Version codes** (`docs/RELEASE.md`): the universal APK has the lowest version code of its version. A phone that
  installed a per-ABI APK of the same version from GitHub (e.g. arm64-v8a, `…02`) takes the store's update from the next
  version on.
- **Updates from another store.** Android 14 and later ask the user before an app installed from one store updates
  from another (Samsung, "Cross-store updates").

## 2. Developer accounts

### 2.1 Samsung Seller Portal

Publishing is free: "there is no sign-up nor annual fee to publish in Galaxy Store" (Samsung's Galaxy Store FAQ).
1. **Samsung account.** Go to https://seller.samsungapps.com, sign in with a Samsung account (or create one), and accept
   the terms.
2. **Seller type.** Seller Portal asks for one. Distributing apps, free ones too, needs **commercial seller** status
   (Samsung, "Prepare"); a private seller cannot publish. There are two kinds of commercial seller:
   - **Corporate**, under Kyra Labs' name. Samsung names D-U-N-S as the easiest proof of the business, with a "doing
     business as" or another national registration document as the alternative. A D-U-N-S number is free from Dun &
     Bradstreet and takes up to 10 business days.
   - **Individual.** **Unconfirmed** whether an individual commercial seller may publish Android apps: a Samsung forum
     answer says they need corporate status.
3. **Documents.**
   - **Name.** The seller name must match the government ID and the financial information.
   - **Financial information.** Required even for a free app; PayPal is the simplest.
   - **Language.** Everything in English.
   - **Timing.** Approval takes "several days"; D-U-N-S and international bank checks up to 10 business days each.
4. **The app.** Once approved: *Applications → Add New Application → Android*, default language **English**. The store
   requires English when the app is sold in more than one country.

### 2.2 Huawei AppGallery Connect

Registration is free.
1. **HUAWEI ID.** Create one at https://developer.huawei.com/consumer/en/ (*Sign up*). Use the owner's address, not a
   personal one that may change.
2. **Identity verification.**
   - *Console → Identity verification → Individual*, or *Enterprise* for Kyra Labs with its business licence.
   - An individual needs:
     - an ID card, passport or driver's licence;
     - **a bank document**: a card scan, bankbook, account-opening letter or statement.
   - Every document must show the full name in English and come from the country chosen there, which **cannot be
     changed later** (Huawei, "Identity verification").
   - Developers outside China can register.
   - How long the check takes is **Unconfirmed**: 1–3 working days according to a third-party guide.
3. **The app.** *AppGallery Connect → My apps → New app*:
   - platform **Android**, package `dev.kyralabs.pingogram`, default language English;
   - **leave App Signing off** (§1);
   - **no Push Kit project setup**: there is no `hms` build (ADR 0033).

## 3. Form answers

Most answers are `docs/PLAY_STORE.md`'s; the table says where a store differs.

| Field | Galaxy Store (`gms`) | AppGallery (`foss`) |
|---|---|---|
| App name | `Pingogram` (`PLAY_STORE.md` §1, never "Telegram") | `Pingogram`. AppGallery's guidelines also forbid "trademarks, popular app names or aliases" in a name |
| Category | **Social**. Galaxy Store has no Communication category; the category cannot be changed later through its API, and Samsung may reclassify an app | **Unconfirmed**: Huawei's category list could not be read. Choose "Communication" if offered, otherwise "Social" |
| Age rating | Samsung's own scale (0, 4, 12, 16, 18). Answer as Play's IARC questionnaire (`PLAY_STORE.md` §5: users interact, shares location; nothing else): **12**, matching Play's expected Teen / PEGI 12. **Unconfirmed** whether Samsung uses IARC or wants a certificate; a forum answer calls one optional | Huawei's content-rating questionnaire (*Release app → Version information → Content rating*, 3+, 8+, 12+, 16+, 18+), answered as Play's IARC one. **Unconfirmed** result: 12+ expected, 16+ if the questionnaire treats unmoderated chat so. Answer it again when content changes |
| Privacy policy | `https://github.com/kyra-labs/pingogram-android/blob/main/PRIVACY.md` (`PLAY_STORE.md` §1). Galaxy Store's guide (§3.1.2) asks apps that handle user data to show the policy **in the app** too: see the gap below | the same URL; AppGallery also asks that it can be reached from inside the app: see the gap below |
| Account deletion | not required by Galaxy Store. Its guide (§3.1.3) asks that the privacy policy say how data is deleted, "for example, upon account deletion", which `PRIVACY.md` does | **required**: apps with accounts must let users deregister "without obstacles" (review guidelines §7). In the app: Settings → Privacy and security → Delete account. AppGallery's optional URL for exercising the user's rights: `https://pingogram.kyralabs.dev/delete-account/` |
| Data and privacy declarations | the registration's **Data Safety** tab: answer as `PLAY_STORE.md` §2 (the same `gms` build: everything to Telegram, collected not shared, for app functionality; the FCM token as "Device or other IDs") | no data form like Play's (none found). The privacy policy and the permission descriptions below cover it. The `foss` build sends no push token: nothing goes anywhere but Telegram |
| Permissions | no declaration form. If **South Korea** is among the countries, the guide (§3.4.3) asks to list the required and optional permissions and why; **Unconfirmed** where Seller Portal asks for it. Use `PLAY_STORE.md` §3's texts | the "Privacy Description" step of an APK with sensitive permissions: one explanation per permission, `PLAY_STORE.md` §3's texts. "Highly sensitive" ones also take a usage scenario and an optional demo video; §3's "Demo video shows" column says what to film. Huawei publishes no list of which they are. `foss` adds the two below, and lacks `gms`' `com.google.android.c2dm.permission.RECEIVE` |
| Ads | none (`PLAY_STORE.md` §1: sponsored messages are Telegram's) | none |
| Countries | all except **China**: Telegram is blocked there, and Samsung wants China's MIIT registration data. Choose the countries one by one or as groups | all except **Mainland China** (*Release countries/regions*: leave it unselected). A release there needs a software copyright certificate (Huawei, "Releasing an app") and Telegram is blocked there. Whether an ICP filing would also be needed is **Unconfirmed** (forum only), and moot |

**Gap: the policy inside the app.** Both stores ask for the privacy policy to be reachable in the app.
- **What the app has now:** the Welcome screen's "Privacy Policy" link, which opens **Telegram's** policy
  (`telegram.org/privacy`); Settings → About has no link to Pingogram's `PRIVACY.md`.
- **What both stores need:** a "Privacy policy" row in About that opens `PRIVACY.md`'s address, a small change in
  `feature:settings`. It is listed in `docs/audit/v1-readiness.md` and needed before the first submission to either
  store. Play asks only for the URL.

The `foss` build's two permissions beyond `PLAY_STORE.md` §3, for AppGallery's Privacy Description:

| Permission | Explanation to paste |
|---|---|
| `FOREGROUND_SERVICE_SPECIAL_USE` | "Keep connected (Settings → Notifications, off by default): keeps the app's connection to Telegram open while the user has turned the setting on, so new messages arrive and notify at once without a push service. An ongoing notification shows while it runs and turns it off. It never starts unless the user turns it on." (`docs/adr/0003-keep-connected-foreground-service.md`) |
| `RECEIVE_BOOT_COMPLETED` | "Starts Keep connected again after the phone restarts, only for users who turned it on; the receiver is disabled otherwise." |

### 3.1 The reviewer's login

Both stores review signed-in apps with a login the developer gives:
- **Galaxy Store:** the **Review comment** field, which only the review team sees (Seller Portal's launch guide).
- **AppGallery:** the **For reviewer** area; account details must not appear in the listing itself (review guidelines §1).

Paste the instructions of `PLAY_STORE.md` §1, "App access": the Telegram test account `+999 66 2 4321` on Telegram's
test servers, code `22222` (`REQUIREMENTS.md` FR-A9). Check before each submission that it still signs in with the code
alone. Add for both: "Pingogram is a client for Telegram accounts; Telegram's test servers are separate from the real
ones and the account sees no real users."

## 4. Listing texts

### 4.1 Galaxy Store (limits in bytes, from its Content Publish API reference)

- **Title** (100): `Pingogram`
- **Short description** (40): `Fast, private client for Telegram` (33). Play's short description (75) does not fit.
- **Long description** (4,000): `PLAY_STORE.md` §4's full description as it is (1,551 bytes), the paragraph "Pingogram
  is an unofficial app. It is not affiliated with, endorsed by or sponsored by Telegram…" kept. Galaxy Store's guide
  forbids brand names used "that can mislead users" (§3.2.6); that paragraph is what keeps "Telegram" from misleading.
- **What's new** (4,000): the version's `CHANGELOG.md` section, shortened as for Play.

### 4.2 AppGallery (limits in characters, from its review guidelines and Connect API)

- **App name** (30): `Pingogram`
- **Brief introduction** (80; 25 or fewer recommended): `Fast, private client for Telegram` (33). Play's short
  description (75) fits too, where the shorter one reads cut.
- **Description** (8,000): `PLAY_STORE.md` §4's full description with the disclaimer, and then §4.3's paragraph.
- **New features** (500): the version's `CHANGELOG.md` section, shortened as for Play.

### 4.3 Notifications on Huawei phones (AppGallery's description)

The `foss` build gets new messages over its own connection. On Huawei phones the battery manager stops apps in the
background unless the user lets them run, so the description ends with:

```
Notifications on Huawei phones
Pingogram has no push service on phones without Google services. To get new messages while it is closed, turn on
Settings → Notifications → Keep connected in Pingogram, and let Pingogram run in the background: in the phone's
Settings → Battery → App launch, find Pingogram, turn off "Manage automatically" and leave Auto-launch, Secondary
launch and Run in background on. Keep connected shows a notification while it runs and uses some battery.
```

**Unconfirmed**: the exact menu names. They vary between EMUI and HarmonyOS versions ("App launch" is EMUI 9 and later's
name; older versions call it "Startup manager"). Check them on the phone a reviewer or tester uses and adjust.

## 5. Images

`tools/stores/images.sh` writes all of them into `build/stores/` (git-ignored). Its inputs:
- **Screenshots:** `docs/screenshots/`, which `tools/readme/screenshots.sh` makes from the goldens. They are padded to
  exactly 9:16 by `tools/play/screenshots.py --exact`, without scaling.
- **Icon:** the 512 px store icon of `fastlane/metadata/android/en-US/images/` (`tools/fastlane/images.sh`, from
  `art/`).
- **Cover:** rendered from `art/` with headless Chromium (`CHROMIUM=…` if it is not on the PATH).

Run the screenshot and fastlane scripts first after re-recording goldens.

| Image | Galaxy Store | AppGallery | File |
|---|---|---|---|
| Icon | PNG, 512 × 512, at most 1,024 KB | PNG, WebP or JPG, 216 × 216 or 512 × 512, at most 2 MB, no rounded corners or padding | `galaxy/icon.png`, `appgallery/icon.png`: 512 × 512, full bleed on white, no alpha |
| Screenshots | 4–8, JPG or PNG, 320–3,840 px a side, at most 2:1 | 3–8, 450 × 800 (9:16) or 800 × 450, 320–3,840 px a side | `*/screenshots/1.png`…`8.png`: 666 × 1184 (9:16), the light phone set in `tools/fastlane/images.sh`'s order (welcome, chats, chat, reactions, composer, search, settings, appearance) |
| Banner | cover image 1200 × 675, at most 1,024 KB; asked only for some categories and countries (Games) | introduction and promotional images: **Unconfirmed** for Android apps; the Connect API lists an "introduction image" upload without its size | `galaxy/cover.png`: 1200 × 675, the logo on the brand's background, as the feature graphic |
| Video | a YouTube link only (optional) | optional: 1–3 MP4 or MOV, 1280 × 720 or 720 × 1280, 15 s to 2 min | none yet |

All screenshots must be one orientation on AppGallery. The tablet screenshots stay Play's (`PLAY_STORE.md` §4): neither
store's form needs them.

## 6. How a release reaches the stores

**Manual for now.** After `release.yml` publishes the GitHub release:
1. **Galaxy Store** (Seller Portal → the app → *Update*): upload `pingogram-X.Y.Z-universal-gms.apk`, paste "What's
   new", submit. Review time is **Unconfirmed**: forum posts quote 2–5 business days and report longer delays.
2. **AppGallery** (AppGallery Connect → the app → *Version information*): upload `pingogram-X.Y.Z-universal.apk`, paste
   "New features", check the content rating, submit. Review "generally takes 3-5 working days".

Both refuse a version code that is not higher than the last one (AppGallery accepts an equal one only to re-release
the same version, which `docs/RELEASE.md`'s scheme never needs).

**Upload APIs**, written down for a later workflow job, not built (each needs a secret in GitHub and the owner's
approval for `release.yml`):
- **Galaxy Store Developer API, Content Publish API.**
  - Auth: a service account created in Seller Portal → *Assistance → API Service* (at most 5). A JWT signed with its
    RS256 key (scope `publishing`, at most 20 minutes) is posted to `https://devapi.samsungapps.com/auth/accessToken`;
    the access token "never expires" until revoked.
  - Flow: an upload session, the file to `https://seller.samsungapps.com/galaxyapi/fileUpload` (which answers a
    `fileKey`), the binary added to the app, the listing updated (`contentUpdate`), then submitted.
  - Limits: it cannot register a new app. From July 2026 the binary goes through its own endpoints, not
    `contentUpdate`'s old `binaryList`.
  - Docs: https://developer.samsung.com/galaxy-store/galaxy-store-developer-api/content-publish-api.html
- **AppGallery Connect Publishing API.**
  - Auth: an API client created under *Users and permissions → API key → Connect API*. Its client id and secret get a
    token (`grant_type=client_credentials` to `https://connect-api.cloud.huawei.com/api/oauth2/v1/token`), sent as a
    bearer token with a `client_id` header.
  - Flow: an upload URL, the APK, the app's file information, its language information, then the submission.
  - Limits: submissions at least 2 minutes apart, and about 2 minutes after an upload while the package is parsed. The
    content rating cannot be answered through the API for a first release.
  - Docs: https://developer.huawei.com/consumer/en/doc/AppGallery-connect-References/agcapi-obtain_token-0000001158365043

## 7. Before the first submission

- Both accounts approved (§2); for Samsung, commercial seller status (D-U-N-S if corporate).
- The images made (§5) and the screenshots current.
- The reviewer's test number checked (§3.1).
- `PRIVACY.md` names both stores, and Settings → About links it (§3, the gap).
- **Telegram's terms**: as on Play (`PLAY_STORE.md` §8). Neither store's rules name Telegram clients. Both forbid
  misleading brand use, and no rejection of a Telegram client's listing on either was found. Telegram X, for one, is on
  AppGallery.

## Sources

- Samsung, Prepare / Launch / Distribution guide / FAQ / Cross-store updates:
  https://developer.samsung.com/galaxy-store/prepare.html , https://developer.samsung.com/galaxy-store/launch.html ,
  https://developer.samsung.com/galaxy-store/distribution-guide.html , https://developer.samsung.com/galaxy-games/faq.html ,
  https://developer.samsung.com/galaxy-store/cross-store-updates.html
- Samsung, Content Publish API reference (field limits, images, countries):
  https://developer.samsung.com/galaxy-store/galaxy-store-developer-api/content-publish-api/reference.html
- Huawei, identity verification: https://developer.huawei.com/consumer/en/doc/help/identityverfication-0000001053292680
- Huawei, App Signing: https://developer.huawei.com/consumer/en/doc/development/AppGallery-connect-Guides/agc-appsigning-introduction-0000001051379577
- Huawei, review guidelines: https://developer.huawei.com/consumer/en/doc/app/50104-01 ,
  https://developer.huawei.com/consumer/en/doc/app/50104-07
- Huawei, image requirements: https://developer.huawei.com/consumer/en/doc/app/agc-help-app-material-requirement-0000001146534651
- Huawei, releasing an app (regions, copyright certificate, permissions):
  https://developer.huawei.com/consumer/jp/doc/app/agc-help-releasebundle-0000001100316672
