# 0003 — "Keep connected": a `specialUse` foreground service in the foss flavor

Date: 2026-10-20 · Status: accepted

## Context

TDLib keeps one connection to Telegram and delivers new messages through it (`REQUIREMENTS.md` FR-N1). The connection
lives as long as Pingogram's process. Android stops the processes of apps in the background within minutes, and Doze
cuts their network, so the `foss` flavor, which has no push service, misses notifications until the app is opened
again. The `gms` flavor will use Firebase Cloud Messaging (`registerDevice`, FR-N3) and needs nothing of this.

Some users accept a little battery for messages that arrive at once, as other open-source messengers offer. That
needs a foreground service: the only way Android lets an app keep its process running, with an ongoing notification
that tells the user so. Android 14 requires each foreground service to declare a type, and Android 12 limits starting
one from the background.

Types considered (Android 14 foreground service types):

- `dataSync`: for transferring data, e.g. uploads and downloads. Limited to 6 hours a day from Android 15
  (`onTimeout`), so it cannot keep a connection open, and Android 15 forbids starting it from `BOOT_COMPLETED`. It is
  the right type for the background transfers of FR-N4, not for this.
- `remoteMessaging`: documented for "transferring text messages from one device to another" to continue a messaging
  task when the user switches devices (e.g. a phone relaying messages to a car or a computer). Pingogram's connection
  is not that.
- `specialUse`: for valid uses no other type covers, with `PROPERTY_SPECIAL_USE_FGS_SUBTYPE` explaining the use. No
  time limit, allowed from `BOOT_COMPLETED`. Google Play reviews the explanation; that matters only for a Play release,
  which would be the `gms` flavor, which does not ship this service.
- `shortService`: at most about 3 minutes.

## Decision

- "Keep connected" (Settings → Notifications, off by default, `foss` only) runs `KeepConnectedService` with type
  `specialUse`, its subtype explaining a messaging app's persistent connection kept open at the user's request.
  Permissions (approved by the owner on 2026-10-19): `FOREGROUND_SERVICE` on every version (the main manifest keeps
  WorkManager's up to API 30), `FOREGROUND_SERVICE_SPECIAL_USE`, and `RECEIVE_BOOT_COMPLETED`.
- Everything lives in `app/src/foss/` (service, receiver, controller, channel, strings, manifest entries); `gms` binds
  `PushDelivery.Firebase` and a no-op `BackgroundConnection` instead, and Settings shows the switch only for
  `PushDelivery.TdlibConnection`. The setting is a key of the app's DataStore (`KeepConnectedRepository`).
- `KeepConnectedController` (started by `PingogramApplication` in every process) starts the service while the setting
  is on, an account is logged in and the app is in the foreground (`ProcessLifecycleOwner`), and stops it when the
  setting is turned off or nobody is logged in (logging out, here or elsewhere). Stopping is allowed any time.
- Android 12+ background starts: the service is started only from the foreground, or by `KeepConnectedBootReceiver`
  on `BOOT_COMPLETED` and `MY_PACKAGE_REPLACED`, both exemptions. A refused start
  (`ForegroundServiceStartNotAllowedException`) is caught, in the starter and in the service, whose sticky restart
  after a kill for memory can be refused too; it then starts again the next time the app is in the foreground.
- The boot receiver is disabled in the manifest and enabled with `PackageManager.setComponentEnabledSetting` exactly
  while the setting is on, so the device never starts Pingogram for users who did not ask.
- The ongoing notification sits in its own "Connection" channel at low importance (Android raises a foreground
  service's minimum-importance notification to low anyway): silent, no badge, a tap opens the app, "Turn off" turns
  the setting off.

## Consequences

- Battery: an open connection, TDLib's keep-alive and the wakeups of incoming updates; the switch says so under it,
  and `docs/PERFORMANCE.md` §6 records the trade-off. The 8 h background budget (≤ 1.5 %) applies with the setting off.
- No new library: `androidx.lifecycle:lifecycle-process` was already in the app through emoji2; it is now declared
  for the foss flavor at the lifecycle version.
- A future Play release of `foss` would have to justify `specialUse` in the Play Console; `gms` avoids the question.
- Users who swipe the notification away on Android 14+ keep the service; turning the setting off is the way to stop
  it.
