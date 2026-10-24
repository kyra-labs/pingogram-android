# 0025 · Conference calls

Status: accepted (the owner's item 9 of 2026-10-23: "joining one from a t.me/call link (LinkRepository), and turning a
1:1 call into a group call"; option 1, patching NTgCalls, approved by the owner on 2026-10-24)

## Context

Telegram's new group calls ("conference calls") are end-to-end encrypted group calls that live outside any chat: one is
created by a user, joined through a link (`t.me/call/…`), and a one-to-one call can be turned into one. The other
apps can do all of it now, so Pingogram meets them: a link someone sends, or a call the other person upgrades.

What the pinned versions offer:

- **TDLib** (a94b83d) has all of it, and keeps the encryption to itself:
  - `createGroupCall(join_parameters)` starts one; `joinGroupCall(input_group_call, join_parameters)` joins one by its
    link (`inputGroupCallLink`) or by an invitation message; both answer a `groupCallInfo` (the call's id and the
    engine's join payload).
  - `getInternalLinkType` turns a `t.me/call/…` link into `internalLinkTypeGroupCall(invite_link)`.
  - A one-to-one call upgraded by the other side ends with `callDiscardReasonUpgradeToGroupCall(invite_link)`.
    `inviteGroupCallParticipant` invites a user (by a `messageGroupCall` in their chat), and
    `declineGroupCallInvitation` declines one.
  - `updateGroupCallParticipants(group_call_id, participant_user_ids)` lists who is in the call's blockchain, and
    `updateGroupCallVerificationState(generation, emojis)` gives the emojis to compare, as one-to-one calls do.
  - TDLib keeps the call's blockchain itself: td_api.tl has no function or update carrying its blocks. The media is
    encrypted through it: the engine is to call `encryptGroupCallData` for every outgoing frame (and its data channel)
    and `decryptGroupCallData` for every incoming one, which is how Telegram's own tgcalls does it.
- **NTgCalls** (e8ab467), our call engine (ADR 0015), has conference calls too (`initConference`, `ConferenceJoinParams`
  with a public key and a block, `onOutboundBlock`, `onSubchainRequest`, `applyBlocks`), but it runs the blockchain
  itself (`e2e::Session`, set as the connection's encryptor). It is made for clients that speak MTProto themselves:
  its examples feed it Telegram's `updateGroupCallChainBlocks` and send its blocks with
  `phone.sendConferenceCallBroadcast` and `phone.getGroupCallChainBlocks`. TDLib exposes none of those, and its Java API
  has no hook to hand frames to someone else's encryption.

So the two do not fit: TDLib holds the chain and encrypts on request, NTgCalls wants the chain and encrypts itself.
Neither can be told to step aside from Kotlin.

## Options

1. **Patch NTgCalls** (recommended): an encryptor for conference calls that calls back into Java for each frame
   (`encrypt(channel, data, unencryptedPrefix)` and `decrypt(participant, channel, data)`), which Pingogram answers with
   TDLib's `encryptGroupCallData` / `decryptGroupCallData`, and a conference mode without NTgCalls' own chain. The rest
   is NTgCalls' group call, which voice chats already use (ADR 0023). It is native work in NTgCalls' C++ and JNI, built
   by the same workflow as today's AAR (`pingogram.ntgcalls`), best offered upstream. A JNI round trip per frame is
   about 50 audio frames a second and the video's 30, each a few hundred bytes to a few kilobytes; to be measured, as
   the 1:1 frame path is (`docs/PERFORMANCE.md` §9).
2. **Bypass TDLib for the chain**: Pingogram cannot send raw MTProto requests through TDLib, so this would need its own
   MTProto connection, which `REQUIREMENTS.md` §1.2 rules out.
3. **Wait** for TDLib or NTgCalls to meet: nothing announced.

## Decision

Option 1: Pingogram patches NTgCalls (`tools/ntgcalls/`), applied by the workflow that builds its AAR
(`.github/workflows/ntgcalls.yml`), whose release then carries the patches' name (`ntgcalls-<short-sha>-pingogram1`,
the catalog's `ntgcalls = "<commit>-pingogram1"`) and their source.

- **The patch** (`0001-external-conference-encryption.patch`):
  - `ExternalEncryptor` is the connection's encryptor in place of NTgCalls' `e2e::Session`. It hands each frame to
    handlers the binding sets once: channel 0 for the main connection, 1 for screen sharing (the presentation).
  - `ConferenceCall::init_external` is a conference call without NTgCalls' chain: no `Session`, so no blocks, short
    polls or emojis of its own. The ssrc-to-participant requests (`onRequestParticipants`, `updateAudioSsrcMappings`)
    work as in NTgCalls' own conference calls, since decrypting needs the sender.
  - `NTgCalls.initExternalConference(chatId)` makes one. Like `initConference`, it turns the chat's one-to-one call
    into it when there is one, keeping its microphone and camera; it answers the join payload.
  - A hand-written JNI file and `io.github.pytgcalls.ExternalEncryption` (as `JavaVideoCapturerModule` is
    hand-written beside the generated layer) call `ExternalEncryption.Handler` on WebRTC's threads and wait for its
    answer; an empty answer drops the frame.
- **Pingogram's handler** answers with TDLib's `encryptGroupCallData` (the main or the screen sharing data channel)
  and `decryptGroupCallData` (the sender as a `messageSenderUser`). TDLib answers them only asynchronously, so the
  handler waits on WebRTC's thread, with a short timeout after which the frame is dropped. TDLib's blockchain,
  participants (`updateGroupCallParticipants`) and emojis (`updateGroupCallVerificationState`) reach the call screen
  from TDLib's updates, not from the engine.
- Offered upstream once it works on devices.

## Consequences

- Pingogram's NTgCalls is no longer upstream's build: each bump of `NTGCALLS_COMMIT` applies the patches again, and
  `git apply` fails the workflow where upstream changed the lines they touch.
- A frame's trip is a JNI call, a request to TDLib and its answer, on WebRTC's encoder and decoder threads: about 50
  audio frames a second per speaker and 30 video frames per video, each a few hundred bytes to a few kilobytes. It is
  measured as the 1:1 frame path is (`docs/PERFORMANCE.md` §9) before conference calls ship.
- The work in Pingogram: `GroupCallRepository.createConference` / `joinConference` (`createGroupCall`,
  `joinGroupCall`), a `LinkTarget.GroupCall(link)` that opens the voice chat screen (asking before it joins, as the join
  sheet does), the verification emojis on that screen, the upgrade path in `CallManager` (`callDiscardReasonUpgradeToGroupCall`
  opening the conference, "Add people" through `inviteGroupCallParticipant`), and `messageGroupCall` in the chat and in
  Recent calls.
- The same patching serves audio levels (ADR 0023) and the engine's log (`sendCallLog`) later, if the owner wants them.
- Until the patched AAR is pinned, a `t.me/call/…` link stays one Pingogram does not open (`LinkTarget.Unsupported`,
  "Open in browser"), and a one-to-one call the other side upgrades ends as hung up.
