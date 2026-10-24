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
- **The grid**: the videos head the participants list and scroll away with it, at most `contentMaxWidth` wide: the
  pinned video across the width at 16:9, then the others two to a row, square, the user's own camera (the self-view)
  last among them; a single video alone takes the width at 16:9. Each tile has `large` corners and the participant's
  name on a `mediaOverlay` pill, a `primary` outline while they speak, and "Video paused" while they hold it back. The
  participants list follows below it as before. Two columns hold four videos and the self-view at a useful size on a
  phone, and larger windows keep them within `contentMaxWidth`. Without others' videos the self-view stays where it was,
  above the participants.
- **Pinning**: a tap on a tile pins it, a tap on the pinned one unpins it (a long press would hide the action from those
  who don't know it); one pinned at a time, on this device only (Telegram has no pin for others to see). The voice chat
  keeps it (`GroupCallManager`) for as long as it lasts, so a rotated or recreated screen shows it again.
- **Picture-in-picture** as live streams: leaving the screen while others' videos show moves it into picture-in-picture
  with the pinned or first video, at 16:9.
- **The frame path**: one `VideoFrameSink` per subscribed SSRC, each tile its own `CallVideoView`; frames of SSRCs
  without a sink are dropped before the copy. The trace sections of `CallTrace` count group frames too, and
  `tools/perfetto/video-call.*` records a video chat the same way.

## Consequences

- No new library, permission or native code: `CAMERA` is already asked for 1:1 video calls (ADR 0019), and the call
  service already declares the `camera` foreground type.
- The cap of four is a number for the device measurement to confirm or change; `docs/PERFORMANCE.md` §9 gets the steps
  for a video chat.
- Screen sharing stays out until the owner approves MediaProjection (item 8): a participant's shared screen is a second
  video (`screen_sharing_video_info`) Pingogram does not receive yet.
