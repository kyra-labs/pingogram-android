# 0026 · Screen sharing

Status: accepted (the owner's item 8 of 2026-10-23; the permission and the foreground service type approved by the owner
on 2026-10-24)

## Context

The owner asked for screen sharing in one-to-one video calls and in voice chats: Android's consent dialog, a clear
"You're sharing your screen" chip with Stop, and Stop in the call's notification.

What the pinned versions offer:

- **Android**: screen capture is MediaProjection. Each share needs the user's consent through the system's dialog
  (`MediaProjectionManager.createScreenCaptureIntent`). Since Android 14, the token is good for one capture, and the
  app must be running a foreground service of type `mediaProjection` before it creates the projection. That type needs
  `FOREGROUND_SERVICE_MEDIA_PROJECTION`. Android 15+ shows its own status bar chip while the screen is shared, from
  which the user can stop it, and hides notifications' content and one-time codes from the capture.
- **NTgCalls** (e8ab467) captures the screen on Android itself: WebRTC's `ScreenCapturerAndroid` behind the capture
  device named `"screen"`, after the consent's result is handed to
  `JavaVideoCapturerModule.setMediaProjectionPermissionResult`.
  - **One-to-one calls**: the screen is a track of its own, the stream sources' `screen` slot. The other side learns of
    it through the call's media state (`screencast_state`), and its frames arrive with `StreamDevice.SCREEN`.
  - **Voice chats**: the screen goes out on a second connection, the "presentation" (`initPresentation`, `connect(…,
    isPresentation = true)`, `stopPresentation`).
  - A capture that ends by itself (the system's chip, the projection taken back) ends with `onStreamEnd(…, SCREEN)`.
- **TDLib** (a94b83d): in voice chats, `startGroupCallScreenSharing(group_call_id, audio_source_id, payload)` answers
  the presentation's parameters, `endGroupCallScreenSharing` ends it, and `toggleGroupCallScreenSharingIsPaused` pauses
  it. A participant's shared screen is their `screen_sharing_video_info`, received as any other video (ADR 0024).
  One-to-one calls need nothing from TDLib: the media state travels in the call's own signaling.

## Decision

- **Permission**: `FOREGROUND_SERVICE_MEDIA_PROJECTION`, and the call service declares the `mediaProjection` type. It
  runs with it only while the user shares, started after the consent and before the capture, and drops it when
  sharing ends. No other permission: the consent dialog is the only one.
- **One-to-one calls**:
  - "Share screen" is an icon button at the top end of the call screen, while the call is connected and not on hold.
  - The consent dialog opens on a tap. Once the user agrees, the screen replaces the camera: one video at a time, as
    Telegram's apps send it.
  - Turning the camera on stops the screen, and holding the call pauses it, as it does the camera.
  - The other side's shared screen shows in place of their camera, fitted to the window, not cropped like a face.
- **Voice chats** (the next pull request): the same button and chip. The screen goes out as the presentation, beside
  the camera; others' shared screens join the grid as tiles of their own.
- **While sharing**: under the call screen's header a pill says "You're sharing your screen" with a "Stop" button.
  The call's notification gets "Stop sharing". There is no preview of the shared screen: it would show itself inside
  itself.
- **Ending**: Stop, the notification's action, the system's chip, hanging up and the call ending all stop the capture
  and the service's type.

## Consequences

- Google Play asks for a declaration of `FOREGROUND_SERVICE_MEDIA_PROJECTION` (a description and a video):
  `docs/PLAY_STORE.md` lists it.
- The capture runs in NTgCalls' WebRTC, 720p at most at 15 frames a second (`VideoQuality` follows the network as for
  the camera, at a lower frame rate). It costs about as much as the camera.
- Where the button goes is a choice `docs/DESIGN_SYSTEM.md` did not settle: the call screen's panel holds four buttons
  already, which fill a phone's width, so it sits at the top end, opposite "Back to the app". The owner may move it.
