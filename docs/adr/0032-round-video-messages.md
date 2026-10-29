# 0032 — Round video messages: CameraX's VideoCapture in feature:chat, square MP4, sent as a video note

Status: accepted (the camera's third use, CameraX in feature:chat and camera-video approved by the owner, 2026-10-23) ·
Date: 2026-10-23 ·
Milestone: M6 polish (`REQUIREMENTS.md` FR-C4 shows video messages; recording them is new)

## Context

Telegram's apps record round video messages ("video notes") from the composer: a tap on the microphone button switches
it to a camera, and holding it records a short square video shown as a circle, with the same gestures as a voice
message (slide to cancel, slide up to lock). Pingogram plays video notes (`messageVideoNote`) but cannot record them.

TDLib sends one with `sendMessage(inputMessageVideoNote(inputVideoNote(video, thumbnail, duration, length),
self_destruct_type))` (pinned `TdApi`): a local video file, an optional thumbnail, its length in seconds and its
`length`, the side of the square in pixels. The Bot API documents what Telegram's clients take as a video note:
"rounded square MPEG4 videos of up to 1 minute long" (`sendVideoNote`, https://core.telegram.org/bots/api#sendvideonote).

What this touches that nothing approved yet:

- **The camera, a third time.** `REQUIREMENTS.md` §6.2 allows the camera for the QR code scanner
  (`docs/adr/0012-camerax-and-zxing-qr-scanner.md`) and for video calls (`docs/adr/0019-video-and-group-calls-scope.md`).
  `CAMERA` and `RECORD_AUDIO` are already declared and asked for, so no new permission, but a new use of them.
- **CameraX outside `:feature:scanner`.** Features do not depend on each other (`docs/ARCHITECTURE.md`), and CameraX
  is in the scanner module only. Video calls use NTgCalls' own camera capture, not CameraX.
- **A new artifact.** Recording video with CameraX needs `androidx.camera:camera-video` (its `VideoCapture` and
  `Recorder`), which the app does not have yet (first party, Apache 2.0, no network, no Play services).

Options considered:

1. **CameraX `VideoCapture` + `Recorder`, in `:feature:chat`.** First party, the same CameraX version as the scanner,
   records H.264 + AAC MP4 through the platform's `MediaCodec` and `MediaMuxer`; a 1:1 `ViewPort` crops the video to a
   square (CameraX applies a viewport's crop to the recording since 1.3, through its own GL pipeline where the camera
   cannot crop itself: to verify on devices); front and back cameras; pause and resume. Costs `camera-video` (a few
   hundred kB of code before R8) and the CameraX AARs in one more module (no extra APK size: they are in the app
   already).
2. **Camera2 + `MediaRecorder` with our own square crop.** No new artifact, but a camera pipeline of our own with
   device quirks (CameraX exists to absorb them), and square output needs a GL crop pass we would write.
3. **A shared `:core:camera` module** holding CameraX for the scanner and video notes. Cleaner if a third camera
   feature comes; today it would move the scanner's working code for one more user.

## Decision

- **Option 1.** `:feature:chat` adds `camera-core`, `camera-camera2`, `camera-lifecycle`, `camera-compose` (already in
  the catalog at 1.6.2) and the new `camera-video` 1.6.2, used only by `feature:chat`'s `videonote/` package, behind a
  `VideoNoteRecorder` interface (as `VoiceRecorder` is), so view model tests run on the JVM. `:feature:scanner` keeps
  its own CameraX code. If a third camera feature comes, both move into a `:core:camera` module then.
- **What is recorded**: from the front camera by default (flip to the back one), a square of **384 × 384 px**, H.264
  at about **1 Mbit/s** and 30 fps, AAC mono at 64 kbit/s, in an MP4 file, at most **60 s** (Telegram's limit), so at
  most about **8 MB** per message. The size and bitrate follow what TDLib and Telegram's apps accept for video notes;
  the exact numbers are checked against a video note Telegram's apps send before shipping.
- **Where it goes**: into the upload cache (`uploads/<uuid>/video_note.mp4`, `VoiceNoteRepository.newVoiceNoteFile`'s
  pattern), with a JPEG thumbnail of its first frame next to it, then `sendMessage(inputMessageVideoNote(...))` through
  the thread's `MessageRepository` like every attachment (reply, silent and scheduled options included). The file is
  deleted once TDLib uploaded it (`UploadCleaner`), when the recording is cancelled, or when TDLib refuses it. Nothing
  else is kept; nothing the camera sees is sent anywhere but in the message the user sends.
- **When the camera runs**: only while the user records (held or locked) and the preview shows; the camera and the
  microphone are released at once when the recording ends, is cancelled, or the screen stops. The camera is asked for
  the first time the user switches the button to video, after a rationale, as the scanner does; without it (refused,
  no camera), the button stays a microphone.
- **UI** (to be written into `docs/DESIGN_SYSTEM.md` §6 Composer when built): a tap on the microphone button switches
  it to a camera and back (Telegram's apps' gesture), holding the camera records; a round preview above the composer
  (`MaterialShapes`' circle, 2/3 of the window's shorter side) with a progress ring for the 60 s, the timer in the
  recording bar, slide to cancel and to lock as for voice, flip camera and Pause / Resume while locked, send or delete.
- **`REQUIREMENTS.md` §6.2** gains a third camera use: "round video messages, asked when the user first switches the
  microphone button to video; the video goes only into the message the user sends, end-to-end encrypted in secret chats,
  and is not kept."

## Consequences

- One new first-party artifact (`camera-video`); the APK grows by the code R8 keeps of it (measured in the PR against the
  arm64-v8a budget of `docs/PERFORMANCE.md` §1, checked by CI's `apk size` job).
- CameraX in two feature modules: an upgrade changes both; `docs/ARCHITECTURE.md` says so.
- Recording uses the hardware encoder; on devices without a square-capable crop path CameraX falls back to its GL
  pipeline, which costs some battery while recording (only then).
- Not built: video notes with a self-destruct timer outside secret chats (Telegram Premium-free, but the composer has
  no timer for media yet), the "view once" option, background blur, and recording while the conversation is not on
  screen.
- As built (2026-10-25): CameraX's public `VideoCapture` API takes no output size (its `setTargetResolution` is
  restricted to the library), so the recorder asks for SD quality (480p, or the closest the camera has) and the 1:1
  `ViewPort` crops it: 480 × 480 on most devices rather than 384 × 384, at the same 1 Mbit/s, so a minute stays about
  8 MB. `inputVideoNote.length` is the side the file has. CameraX's audio bit rate is its own (no public setting). Leaving
  the app or rotating ends a video recording (the camera is the app's only in the foreground); the user then sends or
  deletes what was recorded.
