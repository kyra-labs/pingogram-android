# 0024 · Video in voice chats

Status: accepted (the owner's item 7 of 2026-10-23: "send your camera in a video chat, see the speakers' and pinned
participants' videos in a grid (adaptive, at most what PERFORMANCE allows), and pin a participant")

## Context

ADR 0019 left group video for later, "several remote videos at once, a JNI copy per participant", and ADR 0023 shipped
voice chats and live streams without it. What the pinned versions offer:

- **TDLib** (a94b83d): every `groupCallParticipant` has `video_info` (and `screen_sharing_video_info`):
  `groupCallParticipantVideoInfo(source_groups, endpoint_id, is_paused)`, the groups as `groupCallVideoSourceGroup
  (semantics, source_ids)`. `joinVideoChat`'s `groupCallJoinParameters.is_my_video_enabled` and
  `toggleGroupCallIsMyVideoEnabled` / `toggleGroupCallIsMyVideoPaused` say whether the user sends video. The group call
  says whether the user may send video (`groupCall.can_enable_video`) and whether they do (`is_my_video_enabled`,
  `is_my_video_paused`).
- **NTgCalls** (e8ab467): the group call's join payload always carries the camera's track (`GroupCall::init` adds
  capture tracks for the microphone and the camera), so the camera turns on after joining by setting the capture
  sources again (`setStreamSources(CAPTURE, …video…)`), with no new join. Another participant's video is received with
  `addIncomingVideo(chatId, endpoint, ssrcGroups)`, which returns the SSRC its frames carry (`Frame.ssrc` in
  `onFrames`), and stopped with `removeIncomingVideo(chatId, endpoint)`. NTgCalls asks Telegram for every incoming video
  at up to 720p (its data channel's receiver constraints, `maxHeight` 720, not settable from Java).
- **The frame path** of 1:1 video (`docs/PERFORMANCE.md` §9): a remote 720p frame is about 1.4 MB crossing JNI as a new
  `byte[]` and copied once more for the `EglRenderer`, 30 a second. Its device measurement is still to come.

## Decision

- **Sending**: the voice chat screen's panel gets the call screen's Camera toggle where the call allows video and the
  device has a camera; the camera's permission is asked when it is turned on (as in 1:1 calls), the call service adds
  the `camera` type while it sends, and the camera stops while the call is held. The front camera first, "Switch
  camera" on the self-view. The most it sends follows the network as in 1:1 calls (`VideoQuality`).
- **Receiving: at most four videos at once** (the user's own self-view aside), picked in this order: the pinned
  participant, then those speaking, then the others with video by TDLib's order. Only those four are subscribed with
  `addIncomingVideo`; the others are not received at all, so a large video chat costs no more than four. Four 720p
  videos are about the bandwidth and the JNI copies of four 1:1 calls; until the device measurement, four is the
  ceiling the screen can show at a useful size on a phone anyway (a 2 × 2 grid), and larger windows keep it.
- **The grid**: the videos take the participants list's place at the top of the screen: one video full width, two side
  by side (a column on a phone), three and four as 2 × 2 (`LazyVerticalGrid` with fixed columns, 16:9 cells on wide
  windows, 3:4 on phones in portrait), each a `large`-rounded tile with the name and mute state on a `mediaOverlay`
  pill. The participants list follows below it as today. A pinned participant takes the whole video area, the others in
  a row of small tiles beneath it.
- **Pinning**: a tile's long press, or the participant row's menu, pins or unpins; one pinned at a time, kept in the
  view model's saved state, on this device only (Telegram has no pin for others to see).
- **Picture-in-picture** as live streams: leaving the screen while videos show moves it into picture-in-picture with
  the pinned or first speaking video.
- **The frame path**: one `VideoFrameSink` per subscribed SSRC, each tile its own `CallVideoView`; frames of SSRCs
  without a sink are dropped before the copy. The trace sections of `CallTrace` count group frames too, and
  `tools/perfetto/video-call.*` records a video chat the same way.

## Consequences

- No new library, permission or native code: `CAMERA` is already asked for 1:1 video calls (ADR 0019), and the call
  service already declares the `camera` foreground type.
- The cap of four is a number for the device measurement to confirm or change; `docs/PERFORMANCE.md` §9 gets the steps
  for a video chat.
- Screen sharing stays out until the owner approves MediaProjection (item 8): a participant's shared screen is a second
  video (`screen_sharing_video_info`) Pingogram does not receive yet; the participant row says "Sharing screen".
