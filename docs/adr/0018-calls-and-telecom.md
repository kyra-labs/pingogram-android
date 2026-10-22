# 0018 — Calls in Android: Telecom, the foreground service, the notifications and the call screen

Status: accepted (permissions approved by the owner for calls only, 2026-10-22)

## Context

`REQUIREMENTS.md` FR-V1 asks for voice calls that behave as calls on Android: an incoming call rings with the
ringtone and shows over the lock screen, where it can be answered or declined; the system's UI, Bluetooth headsets,
cars and watches know of it; the audio goes to the earpiece, the speaker, a wired or Bluetooth headset; a phone call
puts it on hold; the screen turns off at the ear. ADR 0015 chose the engine that moves the audio (NTgCalls); this one
decides how a call lives in Android around it. Pingogram's `minSdk` is 26.

The options for the system's side:

- **Nothing**: the app sets the audio mode and routes itself, and no other app knows of the call. A phone call coming
  in during it plays over it; Bluetooth headsets and cars cannot answer or end it.
- **A self-managed `ConnectionService`** (Android 8+): the platform's Telecom knows the call; much boilerplate, and
  Android 14 added a simpler call API (`TelecomManager.addCall`, `CallControl`) that apps are told to use instead.
- **Jetpack `androidx.core:core-telecom`** (1.0.1, stable, `minSdk` 24): one API, `CallsManager.addCall`, that uses the
  platform's call API from Android 14 and its own `ConnectionService` (`JetpackConnectionService`) before, with the
  audio endpoints (earpiece, speaker, wired headset, Bluetooth), mute, hold and answering from other surfaces as flows
  and callbacks.

## Decision

- **Telecom through `core-telecom` 1.0.1** (`TelecomCallSystem`, behind `CallSystem` in `:feature:calls`). Pingogram
  registers as a self-managed calling app (`MANAGE_OWN_CALLS`, `CAPABILITY_BASELINE`); each call is added with its
  direction, the other person's name and the address `pingogram:user/<id>`, audio only, `SUPPORTS_SET_INACTIVE` (hold).
  The audio endpoints Telecom offers are the call screen's routes; Telecom's mute (a headset's button) mutes the
  engine; Telecom's hold (a phone call) pauses the engine and mutes the microphone; answering or ending from a headset,
  a car or a watch answers or ends the call. Where the device has no Telecom (some tablets and Chromebooks, declared
  optional in the manifest) or Telecom refuses the call, `AudioManagerCall` sets the voice communication mode and the
  route directly (Android 12+ `setCommunicationDevice`, before 12 the speakerphone).
- **One call at a time** (`CallManager`): an incoming call while the user is in another is declined; TDLib tells the
  caller it was declined (td_api.tl has no "busy").
- **Foreground service** (`CallService`): type `phoneCall` while a call rings or goes on, `phoneCall|microphone` once
  the user talks (Android lets a service use the microphone in the background only after the app was in the
  foreground, so answering opens the call screen, which starts it). Its notification is the call's. Started for an
  outgoing call from the screen; for an incoming call when the process may (Android 12+ refuses foreground services
  started from the background otherwise, and the call's notification, which has a full-screen intent, rings anyway).
  `FOREGROUND_SERVICE_PHONE_CALL`, `FOREGROUND_SERVICE_MICROPHONE`.
- **Notifications** (`core:notifications`' `CallNotificationBuilder`): Android's call style. Incoming: "Decline" and
  "Answer", in the "Calls" channel, which rings with the default ringtone (`USAGE_NOTIFICATION_RINGTONE`) and
  vibrates until answered, declined or gone (`FLAG_INSISTENT`), and opens the call screen over the lock screen with its
  full-screen intent (`USE_FULL_SCREEN_INTENT`); on Android 14+, where the user may take that permission back, it is
  left out (`canUseFullScreenIntent`) and the notification shows as heads-up. The channel is new ("incoming_calls"): a
  channel's sound cannot change once created, and 0.4.0's "calls" had the notification sound; it is deleted. Ongoing:
  "Hang up" and the call's timer in the silent "Ongoing calls" channel. TDLib's own call notification group is no
  longer drawn by `NotificationRenderer`. No sound files are bundled: the ringtone is the user's, and the caller's
  ringback and busy tones are the platform's `ToneGenerator` on the voice call stream.
- **The call screen is an activity of its own** (`CallActivity`, not exported, `singleTask` in its own task, out of the
  recent apps): it shows over the lock screen and turns the screen on (`setShowWhenLocked`, `setTurnScreenOn`) and
  keeps it on, and shows only the call: the other person's name and avatar, as the lock screen's notification does.
  The chats stay behind the lock screen and the passcode lock. "Back to the app" asks to unlock first.
- **Proximity**: while the call's audio comes from the earpiece, the service holds a
  `PROXIMITY_SCREEN_OFF_WAKE_LOCK` (`WAKE_LOCK`, now on every version), released with
  `RELEASE_FLAG_WAIT_FOR_NO_PROXIMITY` when the call ends or its audio moves away from the earpiece.
- **Permissions** (owner, 2026-10-22, calls only): `RECORD_AUDIO` (already declared) is asked before the first call or
  answer, after the call screen says why; on Android 12+ `BLUETOOTH_CONNECT` is asked with it, for the names of
  Bluetooth headsets (optional: the call goes on without it). `MODIFY_AUDIO_SETTINGS` for the audio mode without
  Telecom. All pinned by `AppManifestTest`.
- **Signal quality**: NTgCalls reports the connection's state, not its bitrate or loss, so the indicator has two
  levels: good while connected, weak (and "Reconnecting…") while the engine looks for a new way after a lost
  connection; an engine that gives up hangs up as disconnected.
- **Network changes and `FLOOD_WAIT`**: TDLib keeps the signaling through network changes, and the engine reconnects
  by itself; a refused call (privacy settings, an outdated app, `FLOOD_WAIT`) ends at once on the call screen with the
  reason.

## Consequences

- The system's dialer UI, a car's or a watch's show Pingogram's calls with the other person's name, and a phone call
  puts the Pingogram call on hold instead of playing over it.
- Calls survive the app being in the background or the screen locked, through the foreground service and Telecom.
- Calls rated after hanging up (TDLib's `need_rating`) and debug logs sent to Telegram are not offered yet.
- Devices without Telecom have earpiece and speaker (and on Android 12+ headsets) only, without the system's call UI.
