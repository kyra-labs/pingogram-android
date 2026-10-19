# 0001 — WorkManager for notification actions

Date: 2026-10-19 · Status: accepted

## Context

A notification's Reply and Mark as read actions (`REQUIREMENTS.md` FR-N2) arrive as broadcasts, often while the app is
in the background or its process is gone. A `BroadcastReceiver` may run only for a few seconds, and the work has to
wait for TDLib to open its database (a broadcast can start a new process) before it can send a message or read
messages. A `goAsync()` receiver would race those limits, and a foreground service of our own would need a notification,
a service type and the code around it for a task of a second.

## Decision

`androidx.work:work-runtime` (2.12.0, first-party, no network code) runs each action as expedited one-time work
(`OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST`), created by Hilt (`androidx.hilt:hilt-work` and its KSP
processor `androidx.hilt:hilt-compiler`, 1.4.0, the same `androidxHilt` version as `hilt-lifecycle-viewmodel-compose`).
`work-testing` is a test-only dependency.

- WorkManager starts on demand: `PingogramApplication` implements `Configuration.Provider` and the manifest removes
  `WorkManagerInitializer` from App Startup, so the process start pays nothing for it (`docs/PERFORMANCE.md` §2).
- Permissions: WorkManager declares `WAKE_LOCK`, `FOREGROUND_SERVICE`, `ACCESS_NETWORK_STATE` and
  `RECEIVE_BOOT_COMPLETED`. Before Android 12 expedited work runs in WorkManager's foreground service, which holds a
  wake lock, so `WAKE_LOCK` and `FOREGROUND_SERVICE` stay, limited to API 30 (`maxSdkVersion`); from Android 12 on it
  runs as an expedited job. `ACCESS_NETWORK_STATE` serves network constraints only (the work has none: TDLib queues
  requests while offline and sends them once connected, even after a restart), and `RECEIVE_BOOT_COMPLETED` only
  reschedules work after a reboot (WorkManager schedules non-persisted jobs on API 23+, and its reboot receiver is
  disabled there): both are removed. `AppManifestTest` pins the result.
- The work never retries: TDLib accepts the request locally and carries it out itself.

## Consequences

- A few hundred KB more per APK before R8 (WorkManager and the Room runtime it uses internally for its own job database;
  the app itself still keeps no Room database). Classes target Java 11 or lower (`hilt-compiler` Java 17).
- WorkManager schedules a sentinel alarm ten years ahead to notice force stops; it never fires, so the "no
  `AlarmManager`" rule of `docs/PERFORMANCE.md` §6 (no periodic wakeups) holds.
- The later background transfers (FR-N4) and the `gms` flavor's push handling can use the same WorkManager setup and
  worker factory; transfers will need `FOREGROUND_SERVICE_DATA_SYNC` and `FOREGROUND_SERVICE` without the API 30 limit.
