# 0023 — Group voice chats and live streams

Status: accepted (owner's brief, 2026-10-23: "group voice chats: join, speak, mute, raise a hand, leave, the
participant list with who is speaking, and starting or ending one as an admin"; "watching live streams, receive-only";
"group calls may raise the budget"). One question is open for the owner: who is speaking, below.

## Context

ADR 0019 rated group voice chats and live streams feasible with the engine Pingogram ships (NTgCalls 3.0.5 at
`e8ab467`, ADR 0015), after 1:1 video, which shipped in 0.5.x. This ADR records what NTgCalls and TDLib give, read from
NTgCalls' source and Android API at the pinned commit and from `TdApi` of the pinned TDLib build, what it costs and
what is left out.

### What NTgCalls gives (`GroupCall`, `io.github.pytgcalls.NTgCalls`)

- **Joining**: `createCall(chatId)` opens a WebRTC connection and returns its join payload (JSON: ICE ufrag and
  password, DTLS fingerprints, the microphone's SSRC). TDLib's `joinVideoChat` sends it to Telegram and answers with
  the call's parameters (JSON), which `connect(chatId, params, isPresentation = false)` takes. Media sources are set as
  for 1:1 calls (`setStreamSources`: the microphone through Oboe, the speaker), so muting is `mute` / `unmute` and
  leaving is `stop`.
- **Three connection modes**, chosen by the parameters Telegram sends: `Rtc` (a voice chat: everyone sends and
  receives through Telegram's SFU), `Stream` (a large or broadcast call: NTgCalls receives Telegram's recorded
  segments instead of WebRTC, asking for each with `onRequestBroadcastTimestamp` and `onRequestBroadcastPart`, which
  the app answers with `sendBroadcastTimestamp` / `sendBroadcastPart` from TDLib's `getGroupCallStreams` and
  `getGroupCallStreamSegment`), and `Rtmp` (a channel's RTMP live stream, received the same way). A call may move from
  `Rtc` to `Stream` (Telegram decides); NTgCalls refuses the other way.
- **Participants' audio**: incoming audio needs no mapping in a voice chat (NTgCalls adds a channel for each new SSRC
  it receives); `updateAudioSsrcMappings` and `onRequestParticipants` are for conference calls.
- **Video**: `addIncomingVideo(chatId, userId, endpoint, ssrcGroups)` per participant, frames through `onFrames` as for
  1:1 video. Screen sharing is a second "presentation" connection.
- **No audio levels.** NTgCalls reads the RTP audio level extension of incoming packets
  (`GroupConnection::rtp_packet_received`) but only to keep the channel alive, and has no callback for levels or speech
  in its C++ or Java API. So the engine cannot say who is speaking now.
- **No new native code**: group calls are in the `libntgcalls.so` the APK ships already (ADR 0019). Their cost is
  Kotlin and UI only.

### What TDLib gives (pinned build)

`createVideoChat(chat_id, title, start_date, is_rtmp_stream)`, `getGroupCall`, `joinVideoChat(group_call_id,
participant_id, join_parameters(audio_source_id, payload, is_muted, is_my_video_enabled), invite_hash)` (→ the
parameters), `leaveGroupCall`, `endGroupCall`, `loadGroupCallParticipants`, `toggleGroupCallParticipantIsMuted`,
`toggleGroupCallParticipantIsHandRaised`, `setGroupCallParticipantIsSpeaking`, `getVideoChatAvailableParticipants`,
`getGroupCallStreams` / `getGroupCallStreamSegment`; updates `updateChatVideoChat` (a chat's call:
`videoChat.group_call_id`, `has_participants`, `default_participant_id`), `updateGroupCall` (title, count, whether the
user joined or must rejoin, `can_be_managed`, `is_rtmp_stream`, `mute_new_participants`, `recent_speakers` with
`is_speaking`) and `updateGroupCallParticipant` (each participant's audio source, mute state and what may be changed,
`is_hand_raised`, `is_speaking`, `order`); messages `messageVideoChatStarted` / `messageVideoChatEnded`.

TDLib's `is_speaking` is what the app reports through `setGroupCallParticipantIsSpeaking`, from audio levels it
measures; `groupCall.recent_speakers` comes from Telegram (participants who spoke in the last seconds, as other apps
report them). Without levels, Pingogram has the latter only.

## Decision

- **One call at a time**, as ADR 0018: a 1:1 call and a group call never run together. Joining a voice chat during a
  call says to end the call first; an incoming 1:1 call during a voice chat is declined as busy.
- **`GroupCallRepository`** (`core:domain`, `TdlibGroupCallRepository` in `core:data`, a `TdlibUpdateCache`: TDLib
  announces calls and participants only through updates) keeps each chat's video chat, the group calls and the
  participants of the call the user is in, and wraps the functions above.
- **`GroupCallManager`** (`:feature:calls`) is the voice chat's state machine, beside `CallManager`: the engine's group
  API behind `CallEngine` (`GroupCallSession`), the same Telecom call (`CallSystem`, a self-managed call named after
  the chat), the same foreground service (`CallService`: `phoneCall`, then `microphone` once unmuted; no proximity
  lock: voice chats play on the speaker), the "Ongoing calls" notification with "Leave".
- **Joining as**: the chat's default participant (`videoChat.default_participant_id`: the user, or a channel they
  manage), joined muted. No picker for now.
- **Who is speaking**: TDLib's `recent_speakers` (`is_speaking`) and the participants' order, which Telegram updates
  with each speaker; the user's own state from the microphone (muted or not). It lags a second or two and covers the
  three most recent speakers. **Open for the owner**: exact speaking indicators need audio levels, which NTgCalls does
  not expose. Options:
  1. Keep TDLib's recent speakers (this decision; nothing more to build).
  2. Patch NTgCalls in `ntgcalls.yml` to report the RTP audio level of each incoming SSRC and the microphone's (a
     callback in its C++ core, JNI and Java API; a few KB of native code), offered upstream. Then Pingogram reports
     its own speaking with `setGroupCallParticipantIsSpeaking`, as Telegram's apps do.
  3. Play the call's audio ourselves (NTgCalls' `EXTERNAL` speaker, frames per SSRC) and measure it: loses WebRTC's
     echo cancellation reference. Not recommended.
- **Live streams** (receive-only): a live stream is a video chat in `Stream` or `Rtmp` mode. Joining is the same
  `joinVideoChat` (muted); NTgCalls then asks for the stream's time and for one-second segments (`MTProtoStream`,
  `segment_duration_` 1000 ms: TDLib's `scale` 0), an audio part and a part per video channel and quality. The time is
  the newest `time_offset` of `getGroupCallStreams` (0 while there is none: NTgCalls asks again a second later), each
  part `getGroupCallStreamSegment(time_offset, 0, channel_id, quality)`. A part Telegram has not recorded yet (TDLib's
  `TIME_TOO_BIG`) is answered "not ready" (asked again 100 ms later), any other failure "resync needed" (the time is
  asked again). Requests run as they come, cancelled when the user leaves. Video comes through `onFrames` as
  for 1:1 video and is shown full screen, with picture-in-picture when the user leaves the screen. Streaming out (RTMP,
  `getVideoChatRtmpUrl`) is not planned.
- **Entry points**: the chat's info page ("Voice chat": join, or start one as an admin), a "Join" bar at the top of the
  conversation while the chat has a call with participants, the service messages ("Voice chat started": "Join"), and
  the call screen's own "End voice chat" for those who may (`can_be_managed`).

## Left out

- Group video (sending or receiving participants' cameras) and screen sharing: several remote videos at once, a JNI
  copy per frame each (ADR 0019); backlog.
- Scheduling a voice chat (`createVideoChat` with a start date), its reminders, and starting a scheduled one; a
  scheduled call shows as "Scheduled" without "Join".
- Choosing whom to join as (`getVideoChatAvailableParticipants`), invite links to a voice chat (`inviteHash`, links
  `t.me/…?voicechat=`: opened as the chat for now), changing the title, recording, per-participant volume, muting new
  participants, the call's own messages (`canSendMessages`), conference calls (`createGroupCall`, end-to-end
  encrypted, NTgCalls' `initConference`), and live stories.

## Size

No native code is added. The Kotlin and resources of group calls and live streams are measured on the `fossBenchmarkRelease`
APKs once they ship, and the budgets of `docs/PERFORMANCE.md` §1 are set from that measurement (owner, 2026-10-23:
"measure the APKs with group calls and set new limits at measured size plus a small margin", changed together in
`docs/PERFORMANCE.md` §1 and §8, `REQUIREMENTS.md` §1.1 G3 and §6.1, build-logic's `ApkSize` and `release.yml`'s "Check
APK sizes"); the result is added below.

## Consequences

- FR-V2's group voice chats and watching live streams ship without a new engine build.
- The speaking indicator is approximate until the owner picks option 2.
- `CallService`, `CallSystem` and the call notifications serve two state machines; `CallService` follows whichever call
  is ongoing.
