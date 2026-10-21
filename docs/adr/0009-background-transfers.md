# 0009 — Background transfers in WorkManager's `dataSync` foreground service

Date: 2026-10-20 · Status: accepted

## Context

Files the user sends or downloads stop when Android ends the process in the background (`REQUIREMENTS.md` FR-N4):
TDLib uploads and downloads only while Pingogram runs. Keeping the process needs a foreground service, which Android
14+ types (`dataSync` for transfers), Android 12+ lets start only from the foreground or an exemption, and Android 15+
limits to 6 hours a day for `dataSync` (`Service.onTimeout`). `docs/PERFORMANCE.md` §6 allows it only while a transfer
the user started runs, stopped within 5 s of the last one. Most downloads are not the user's: what is on screen, avatars,
the automatic downloads; those must not keep the process.

Options considered:

- A `Service` of our own, as "Keep connected" (ADR 0003). It would duplicate what WorkManager's foreground service
  already does for the notification actions (ADR 0001), timeout handling included.
- User-initiated data transfer jobs (Android 14+, `RUN_USER_INITIATED_JOBS`): not before Android 14, and a permission
  the owner had not approved.
- A long-running `CoroutineWorker` that calls `setForeground` with type `dataSync`: WorkManager is in the app already,
  its foreground service handles `onTimeout` (2.10+: it stops the worker and leaves the foreground), and its worker
  factory is set up.

## Decision

- `TransferRepository` (`core:domain`, `TdlibTransferRepository` in `core:data`) counts the user's transfers: the file
  of each message being sent from this device, and the downloads asked for with the new `DownloadPriority.Requested`
  (a document or photo tapped, files saved from a selection). Everything else keeps its priority and never counts.
- `TransfersWorker` (`core:notifications`) runs as unique, expedited work (`APPEND_OR_REPLACE`: one queued behind a
  worker that is about to end), calls `setForeground` with `FOREGROUND_SERVICE_TYPE_DATA_SYNC` at once, updates its
  notification at most once a second and ends 2 s after the last transfer. `:app`'s `TransfersController` starts it as
  transfers begin while the app is in the foreground, and again when the user comes back while they run (after a
  refused start or the daily limit); never from the background.
- Permissions (approved by the owner): `FOREGROUND_SERVICE` on every version (WorkManager's up to API 30 before) and
  `FOREGROUND_SERVICE_DATA_SYNC`; WorkManager's `SystemForegroundService` declares the type `dataSync`.
- The notification sits in a new "Transfers" channel (low importance, no badge) with "Cancel", which stops the
  downloads and deletes the messages being sent (TDLib's way to cancel an upload). Android 12+ defers a foreground
  service's notification by a few seconds, so short transfers show none.
- Android 16: `NotificationCompat.ProgressStyle`, asking for promotion to a Live Update (`setRequestPromotedOngoing`,
  the percentage as status bar text). Android promotes only apps holding `POST_PROMOTED_NOTIFICATIONS`, a normal
  permission granted at install, which Pingogram requests (approved by the owner on 2026-10-21); the user can turn Live
  Updates off for the app in Android's settings.

## Consequences

- No new library. WorkManager initializes at the first transfer, not at start.
- Transfers TDLib resumes after a restart (messages still being sent) are not counted until they are sent again.
- A download stalled while offline keeps the service until it ends or the daily limit is reached.
