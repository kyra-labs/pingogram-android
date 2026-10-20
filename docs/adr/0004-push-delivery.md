# 0004 — Push delivery: Firebase Cloud Messaging in `gms`, TDLib's own connection in `foss`

Status: accepted (owner instruction, FR-N3) · Date: 2026-10-20 · Milestone: M3 (`REQUIREMENTS.md` FR-N1, FR-N3;
`docs/ROADMAP.md` "gms flavor with FCM registerDevice")

## Context

Pingogram learns about new messages from TDLib's connection to Telegram. While the app is in the background Android
freezes and eventually kills the process, so without help new messages only show when the user opens the app again.
Telegram's apps solve this with push notifications: the server sends a small message through the phone's push service,
which wakes the app. The owner asked to verify, **before adding Firebase**, whether this works for a third-party client
with its own Firebase project, and what the server side needs; to add Firebase to the `gms` flavor only if it does; and
to evaluate UnifiedPush for `foss` in the same record.

### How Telegram delivers pushes to a client (verified against the sources below)

1. The client registers its push token with Telegram: in TDLib `registerDevice(device_token, other_user_ids)`, for
   Firebase Cloud Messaging (FCM) `deviceTokenFirebaseCloudMessaging(token, encrypt)` (Telegram's token type 2).
   TDLib's documentation of `DeviceToken`: "To use specific push notification service, the correct application
   platform must be specified and a valid server authentication data must be uploaded at https://my.telegram.org."
   Telegram's push page says the same: "an application key (GCM) must be specified in the application settings".
   So Telegram's server sends the pushes **with the app developer's own FCM credentials**, which the owner uploads for
   the app's `api_id`. Without them registration still succeeds (TDLib returns a push receiver id), but nothing
   arrives: tdlib/td#873 is the APNS version of exactly that (the server "fails to connect to APNS" with the uploaded
   key), and in tdlib/td#1382 pushes did not arrive on the test DC, where TDLib's author advises to test pushes in
   the production DC.
2. With `encrypt = true` TDLib generates a 256-byte key, keeps it in its encrypted database and passes it to
   Telegram as the registration's `secret`. Telegram then sends FCM **data messages** whose `p` field is the
   base64url, MTProto-2.0-encrypted payload; Google sees only ciphertext. Without encryption the push carries no
   content and TDLib has to connect for every message.
3. The app receives the data message (`FirebaseMessagingService.onMessageReceived`) and hands TDLib "JSON-encoded push
   notification payload with all fields sent by the server, and `google.sent_time` and `google.notification.sound`
   fields added" with `processPushNotification(payload)`. It "can be called before authorization"; TDLib postpones it
   until its database is open, decrypts the payload and creates `notificationTypeNewPushMessage` notifications in its
   notification engine, which are replaced by full `notificationTypeNewMessage` ones once TDLib has synced. It
   "returns only after all updates caused by the push have been sent". Error 406 means the push is of a kind TDLib
   cannot use and it needs the connection to fetch new data.
4. From there it is the existing path of PR #77 (`docs/ARCHITECTURE.md`, Notifications): `TdlibNotificationRepository`
   reduces the notification updates (push messages included: `NotificationContent.NewPushMessage`), and
   `core:notifications`' renderer posts them, in the process FCM started.

Other TDLib facts that shape the design (`td/telegram/DeviceTokenManager.cpp` at the pinned commit):

- TDLib keeps the token in its database and **re-registers it at every start by itself** (a token in state `Sync`
  becomes `Reregister` at start-up). Telegram recommends "to repeat this query at least once every 24 hours or when
  restarting the application"; TDLib's start covers that, and Pingogram adds no periodic job (`docs/PERFORMANCE.md` §6).
- `registerDevice` with the token TDLib already has (same encryption, same other users) answers at once **without a
  request to Telegram**, so registering at every login and start is free.
- A failed first registration leaves the token to be unregistered, so the next start registers it again.
- An empty token unregisters (`account.unregisterDevice`). TDLib does **not** unregister on `logOut`; logging out ends
  the authorization the token was registered for on Telegram's side, and TDLib deletes its database, the encryption
  key included, so a late push could not be read anyway.

### What the owner must do (the part that cannot be verified from here)

Nothing in this repository can prove that pushes arrive: that needs the owner's `api_id` with uploaded credentials
and a phone with Play services. `docs/OWNER_SETUP.md` §8b lists the steps: a Firebase project with Android apps for
`dev.kyralabs.pingogram` (release and benchmark builds) and `dev.kyralabs.pingogram.debug` (debug builds), its
`google-services.json` locally and as the CI secret `GOOGLE_SERVICES_JSON_BASE64`, and the project's server
credentials uploaded for the `api_id` at https://my.telegram.org/apps (FCM credentials). Google shut down FCM's legacy
server keys in 2024, so the form presumably takes the HTTP v1 service-account JSON (Monogram, a TDLib-based client,
documents exactly that: "Register the corresponding FCM credentials for your Telegram API ID at my.telegram.org/apps";
"never package a service-account key in the APK"); the owner checks the form. The service-account key goes to
Telegram only, never into the app, the repository or CI.

### UnifiedPush for `foss`

UnifiedPush lets a user pick a push distributor app (ntfy, NextPush, a Conversations-style distributor…) instead of
Google; the app gets an HTTPS endpoint from the distributor through the UnifiedPush connector library (Apache-2.0, no
Google code). Telegram supports two token types for such endpoints:

- `deviceTokenSimplePush(endpoint)` (type 4): "a simple PUT request with a `version=number` payload to the specified
  HTTPS endpoint" for each update; the number means nothing, the client "should connect to MTProto and fetch updates".
  A wake-up only: TDLib must connect for every push, then its notification engine shows the messages. Telegram's page
  names UnifiedPush as a user of this type. Risk: Telegram sends **PUT**, the UnifiedPush server spec expects POST;
  ntfy accepts PUT, other distributors may not.
- `deviceTokenWebPush(endpoint, p256dh, auth)` (type 10): RFC 8291 encrypted Web Push with content. UnifiedPush
  distributors increasingly speak Web Push, but TDLib's `processPushNotification` reads FCM- and APNS-shaped payloads;
  whether a Web Push payload decrypted by the connector can be handed to it, or only used as a wake-up, is unverified.

Either way it needs no Telegram-side credentials (the endpoint is public), but a distributor app on the phone, a
distributor picker in Settings, the connector dependency and testing against several distributors.

## Decision

1. **FCM in `gms`, implemented now**: it works for a third-party client once the owner has uploaded the FCM
   credentials for the `api_id` (above); the implementation is complete, and a `gms` build without
   `google-services.json` refuses to package instead of shipping a build that silently gets no pushes.
   - Dependencies, `gms` only: `firebase-messaging` through `firebase-bom` (35.0.2 → 26.0.0), and
     `kotlinx-coroutines-play-services` for `Task.await()`. No other Firebase product: no Analytics, no Crashlytics.
     Their classes target Java 8 (class file version 52, `javap`), fine for JVM tests on JDK 17.
   - Build: `pingogram.android.gms` (`build-logic`) applies the Google Services plugin (4.5.0) only when
     `app/src/gms/google-services.json` exists, telling it to ignore the variants without a file, so `foss` variants
     get no Firebase resources even then. Without the file, configuration, `./gradlew check` (CI has no file) and every
     `foss` build work; packaging a `gms` variant fails before anything runs, naming the file and the setup guide (the
     release-signing guard's pattern). A file in `app/` itself fails configuration: the plugin would hand it to `foss`
     too. The file is git-ignored.
   - `foss` stays Google-free, enforced: `check<FossVariant>GoogleFree` fails if a `foss` variant's runtime classpath
     holds any `com.google.firebase`, `com.google.android.gms`, `com.google.android.datatransport` or
     `com.google.android.play` module; `check` runs it for every `foss` variant and each `foss` build runs its own.
   - Code: `core:domain` `PushRepository` and `PushTokenSource`; `core:data` `TdlibPushRepository` with an
     `Optional<PushTokenSource>` (`@BindsOptionalOf`): `gms` binds `FirebasePushTokenSource`, `foss` binds nothing and
     the repository does nothing. `PingogramApplication` starts it. At every login (and start while logged in) it
     registers FCM's current token, `encrypt = true`, no other user ids; a new token (`onNewToken`) follows at once; a
     `FLOOD_WAIT` is waited out (at most three attempts), other failures are dropped for the next start. At logout it
     deletes the FCM token (`FirebaseMessaging.deleteToken`), so no push reaches the device after it, whatever
     Telegram's server does with the ended session.
   - `PingogramMessagingService` (`gms`, not exported) passes each push's fields to `TdlibPushRepository`, which builds
     the payload (`PushPayload`: the data fields plus `google.sent_time` and the sound) and calls
     `processPushNotification`, on Firebase's worker thread, waiting at most
     6 s for TDLib (its start included), then at most 3 s for held-back notifications (`updateHavePendingNotifications`)
     or, after a 406, for the connection. Firebase gives `onMessageReceived` "a short execution window" and sends
     "long-running tasks (10 seconds or more)" to WorkManager; TDLib normally answers well within a second, so no
     worker or foreground service is involved.
   - Privacy: FCM's auto-init is off (`firebase_messaging_auto_init_enabled`), so Firebase creates no installation or
     token, and does not contact Google, before a logged-in account asks; Firebase's data-collection default, Analytics
     collection and FCM's delivery-metrics export are off in the manifest. Push contents are end-to-end encrypted
     between Telegram and TDLib. Google still learns that a message arrived and when (inherent in any push service).
   - Manifest (`gms`): Firebase's `FirebaseInstanceIdReceiver` (only Play services may send to it), our messaging
     service (not exported), Firebase's init provider and component discovery, and Google's data transport (idle
     with delivery metrics off), plus the permissions `com.google.android.c2dm.permission.RECEIVE` and `WAKE_LOCK`
     for every version; `AppManifestTest` pins both flavors (see Consequences).
   - The notification settings' "Keep connected" (`foss`, separate PR) is not offered in `gms`.
2. **`foss` keeps TDLib's own connection** for now (and the optional "Keep connected" service from the notification
   settings work). **UnifiedPush is a follow-up after M3**, not part of v0.3.0: start with `deviceTokenSimplePush`
   (a wake-up; simple and needs no payload format), behind a distributor picker, tested with ntfy and NextPush, and
   evaluate Web Push second. It needs an ADR of its own for the connector library.

## Consequences

- **The owner must act before `gms` receives anything**: Firebase project, `google-services.json`, and the FCM
  credentials at my.telegram.org (`docs/OWNER_SETUP.md` §8b). Until then a `gms` build cannot be packaged, and with a
  file but without uploaded credentials it registers fine and receives nothing (indistinguishable from the app's side).
  Pushes need the production DC; the test DC (debug toggle) gets none.
- **Licensing**: Play services and a few Firebase libraries are proprietary (POM license "Android Software Development
  Kit License"), listed in `gms`' open-source licenses screen as `LicenseRef-Android-SDK` with the text the SDK manager
  ships (`config/licenses/texts/`); `foss`' list is unchanged. Shipping proprietary libraries in a GPL-3.0 app is a
  question for the owner as copyright holder (an additional permission for the `gms` flavor, or Apache-2.0, which
  `REQUIREMENTS.md` §2 already considers) before a `gms` build is distributed.
- **Network**: the `gms` flavor talks to Google (FCM, Firebase Installations) once logged in, an exception to
  `REQUIREMENTS.md` §6.2 that FR-N3 asks for; `foss` talks to Telegram only, enforced by the build.
- **Size and startup**: `gms` APKs grow by Firebase Messaging and Play services' client libraries: 0.8 MB in a debug
  universal APK (without R8); release sizes are not measured yet. Firebase's init provider runs at every `gms` process start (a few ms); the benchmarks measure `foss`.
- **Permissions** (`gms` only, normal permissions without a prompt, approved by the owner as "the gms flavor's FCM
  permissions"): `com.google.android.c2dm.permission.RECEIVE` (receive FCM messages) and `WAKE_LOCK` for every version
  (FCM holds a wake lock while it hands a push to the service and logs "service start may be delayed" without it;
  `foss` keeps it to API 30 for WorkManager). FCM also declares `ACCESS_NETWORK_STATE`, which stays removed as in
  `foss`: FCM checks for it at run time and does without (only its token refresh task, idle with auto-init off, reads
  connectivity).
- **Not verified here**: delivery end to end, the my.telegram.org form, behaviour under Doze with normal-priority
  pushes. The service, the registration and the build guard have unit tests; the phone test is the owner's.
- UnifiedPush stays open for `foss` users who want pushes without Google (follow-up).

## Sources

- Telegram, Handling push notifications: https://core.telegram.org/api/push-updates
- TDLib, Notification API: https://core.telegram.org/tdlib/notification-api/
- TDLib docs: `DeviceToken` https://core.telegram.org/tdlib/docs/classtd_1_1td__api_1_1_device_token.html ,
  `deviceTokenFirebaseCloudMessaging`, `deviceTokenSimplePush`, `registerDevice`, `processPushNotification`
  (https://core.telegram.org/tdlib/docs/classtd_1_1td__api_1_1process_push_notification.html)
- TDLib source at the pinned commit, `td/telegram/DeviceTokenManager.cpp`:
  https://github.com/tdlib/td/blob/a94b83d67ab4c8397c6245e541b31a4131ea2f79/td/telegram/DeviceTokenManager.cpp
- tdlib/td#873 (server-side push credentials) https://github.com/tdlib/td/issues/873 , tdlib/td#1382 (test pushes in
  the production DC) https://github.com/tdlib/td/issues/1382
- Monogram (TDLib client with its own Firebase project and UnifiedPush): https://github.com/monogram-android/monogram
- Firebase, Receive messages in an Android app: https://firebase.google.com/docs/cloud-messaging/android/receive-messages
- UnifiedPush: https://unifiedpush.org
