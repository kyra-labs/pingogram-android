# 0019 — Scope of video calls and group calls

Status: accepted (owner's brief for M5, 2026-10-22: "implement 1:1 video only if it is small on top of voice; group
calls and live streams stay in the backlog"; then, the same day: "1:1 video calls if they were deferred and the ADR
found them feasible; then group voice chats and live streams only if the ADR rated them feasible")

## Context

M5 ships voice calls (`REQUIREMENTS.md` FR-V1, ADR 0015, ADR 0018). FR-V2 (video calls, group calls, live streams) is
"Later". This ADR says how big each is on top of voice, and whether it is feasible with the engine Pingogram ships
(NTgCalls 3.0.5, ADR 0015), read from its Android API and source at the pinned commit.

### 1:1 video

- **Sending**: NTgCalls captures the camera itself: `setStreamSources(CAPTURE, …camera = VideoDescription(DEVICE,
  w, h, fps, <camera id>))`, with the cameras from `getMediaDevices().video` (Camera2 through WebRTC's enumerators, its
  `SmartVideoSource`). Front and back are two device ids: switching is a new `setStreamSources`.
- **Receiving**: the other person's video reaches the app as frames, `onFrames(callId, PLAYBACK, CAMERA, frames)`, each
  a packed I420 `byte[]` that NTgCalls scales on the native side to the size the app asked for, with its rotation. The
  AAR bundles WebRTC's Java API (`libs/webrtc.jar`) and a renderer (`org.webrtc.TextureViewRenderer`): a frame is
  wrapped in a `JavaI420Buffer` and drawn by the renderer on its own EGL thread, so there is no renderer to write.
- **The rest**: TDLib's video state (`createCall(is_video)`, `discardCall(is_video)`, the other side's `call.is_video`),
  the camera preview, front and back camera, turning the camera on and off, picture-in-picture for an ongoing video
  call, the `CAMERA` permission's step on the call screen, the `camera` foreground service type, lower resolutions on
  weak connections.
- **Risk**: every remote frame crosses JNI as a new `byte[]` (about 1.4 MB at 1280 × 720, 30 a second). Asking for the
  size the view needs, capping it at 720p and halving it on a weak connection keeps that within reach; it is measured
  on a device before the feature ships (`docs/PERFORMANCE.md`).

### Group calls (voice chats) and live streams

- NTgCalls is first of all a group call library (it is what pytgcalls runs on): `createCall(chatId)` makes the join
  payload, TDLib's `joinGroupCall` answers with the connection parameters, `connect(chatId, params)` joins; speakers
  arrive through `updateAudioSsrcMappings` and `onRemoteSourceChange`, live streams through `onRequestBroadcastPart` /
  `sendBroadcastPart`, fed from TDLib's `getGroupCallStreamSegment`.
- TDLib has the rest: `createVideoChat`, `getGroupCall`, `updateGroupCall`, `updateGroupCallParticipant`,
  `toggleGroupCallParticipantIsMuted`, `setGroupCallParticipantIsSpeaking`, `toggleGroupCallParticipantIsHandRaised`.
- The UI is new: a group call screen (participants, speaking indicator, raise hand, mute others as an admin), joining
  from a chat's "video chat started" and its top bar, a minimized call bar over the chats.

## Decision

| Feature | Feasible | Size on top of voice | Plan |
|---|---|---|---|
| 1:1 video calls | **Yes** | Medium: no engine or CI work, no renderer to write; the camera, the video layout, PiP, `CAMERA` | Next, in small PRs: engine and state; the screen (remote video, self-view, switch camera, camera on and off); picture-in-picture and bandwidth |
| Group voice chats | **Yes** | Large: a new call type in `CallManager`, new TDLib updates, a new screen | After 1:1 video, voice only first (join, speak, mute, raise hand, leave), then starting one as an admin |
| Live streams (watching) | **Yes, later** | Medium on top of group calls | After group voice chats; streaming out (RTMP) is not planned |
| Group video | Yes, later | Large: several remote videos at once, a JNI copy per participant | Backlog until 1:1 video's cost is measured |

- Until 1:1 video ships, **video calls from others ring as voice calls**: an incoming video call (`call.is_video`) rings
  like any call and is answered with audio only, and the call screen says "Video calls aren't supported yet: you're
  talking with audio only"; the same when the other side turns video on during a call. The other person keeps a working
  call and sees that no video comes.
- Nothing offers what does not work yet: no video button before video ships, no "Join" for a video chat before group
  calls do.
- `CAMERA` (approved by the owner for 1:1 video) is asked for calls only once video ships, and only when the user turns
  the camera on; the scanner keeps its own use (ADR 0012).

## Consequences

- M5 closes with voice calls; FR-V2's 1:1 video moves from "Later" to the next calls work, group voice chats after it.
- The APK keeps NTgCalls' video and group code (upstream's build, ADR 0015), so neither needs a new engine build.
- The video frame path is the one performance risk; it gets a device measurement before the feature ships.

## Implementation notes (1:1 video, 2026-10-23)

What the code found at NTgCalls' pinned commit, where it differs from the reading above:

- **Cameras**: `getMediaDevices().camera`, each with JSON metadata (`{"id": "1", "is_front": true}`). The capture size
  is fixed: NTgCalls scales every camera frame to the `VideoDescription`'s width and height. Pingogram sends 1280 × 720
  at 30 frames a second, landscape as cameras capture, and each frame carries its rotation.
- **Self-view**: the camera's own frames come through `onFrames(CAPTURE, CAMERA, …)` too, so the self-view and the other
  side's video are drawn the same way. Before the engine connects there are no frames, so a video call shows no
  self-view while it rings yet.
- **Receiving**: NTgCalls scales the other side's frames to a fixed size whatever their shape, which would stretch them.
  So they come at the size they are sent (`VideoDescription` 0 × 0 at 0 fps, "as sent"; Telegram's apps send at most
  720p), and the screen scales them.
  - Playback sources negotiate receiving video again whenever they change. So a session receives video only while a
    sink takes it (`CallSession.setVideoSinks`), and a call nobody watches pays nothing for frames.
- **Renderer**: the AAR has no `TextureViewRenderer`. WebRTC's `SurfaceViewRenderer` is what it bundles.
- **The other side's video state** comes through `onRemoteSourceChange` (camera: active, paused, idling).
  - NTgCalls tells the other side "suspended" while the user is muted (its `MediaState.video_paused` is `paused ||
    muted`). How Telegram's apps show a muted user's video is checked on a device.
