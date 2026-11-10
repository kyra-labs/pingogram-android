# 0036 — Posting stories: the Photo Picker and the camera app, TDLib's `postStory`, long videos cut at key frames

Status: proposed (owner decisions to confirm, 2026-11-10) ·
Date: 2026-11-10 ·
Milestone: after `v2.0.0` (`docs/ROADMAP.md`; stories were read only in M6, `docs/ARCHITECTURE.md` "Stories")

## Context

Pingogram shows stories (the rings above the chat list, the stories viewer) but cannot post one. The owner's design
(Session B, boards 10a–10c) adds posting in three steps:

- **10a, picking**: "New story · 4 selected", an "In this story" strip whose items drag to reorder, "Add more",
  "Camera", "Next", and the note "Picked with Android's photo picker. Pingogram can only see these 4 items."
- **10b, composing**: the item full bleed, a caption with its formatting bar (Aa, bold, italic, link, spoiler), the
  privacy chip (Everyone / Contacts / Close friends / Selected), "Stays for 24 h", "Shown to 14 close friends · 1 of 4
  items", and "Post", which posts every item.
- **10c, uploading**: the user's own ring reads "Posting 62%"; a tap offers cancelling or watching the progress.

TDLib (a94b83d, checked in its `TdApi`) has what this needs:

- `postStory(chat_id, content, areas, caption, privacy_settings, album_ids, active_period, from_story_full_id,
  is_posted_to_chat_page, protect_content)` returns the story at once with `is_being_posted`; the upload ends in
  `updateStoryPostSucceeded(story, old_story_id)` or `updateStoryPostFailed(story, error, error_type)`.
- `inputStoryContentPhoto(photo, added_sticker_file_ids)` and `inputStoryContentVideo(video, added_sticker_file_ids,
  duration, cover_frame_timestamp, is_animation)`.
- `storyPrivacySettingsEveryone(except_user_ids)`, `…Contacts(except_user_ids)`, `…CloseFriends`, and
  `…SelectedUsers(user_ids)`; `getCloseFriends` and `setCloseFriends(user_ids)`.
- `canPostStory(chat_id)`, which says why not: `ActiveStoryLimitExceeded`, `WeeklyLimitExceeded`,
  `MonthlyLimitExceeded`, `PremiumNeeded`, `BoostNeeded`, `LiveStoryIsActive`.
- `deleteStory(chat_id, story_id)`, which also takes a story still being posted.

A story's video lasts 60 s at most (Telegram's limit; TDLib refuses longer ones). Telegram's apps cut a longer video
into 60 s stories posted one after another.

## Decision

- **Picking without permissions.** Android's Photo Picker (`PickMultipleVisualMedia`, photos and videos, at most
  10 items) and the camera app (`ACTION_IMAGE_CAPTURE` into a file of `core:ui`'s `CameraFiles`, as the attach menu's
  "Camera" does). `REQUIREMENTS.md` §7 forbids storage permissions; no `CAMERA` permission is used either (the camera
  app takes the picture). The note on 10a says so.
- **Where it lives.** `feature:media`, beside the stories viewer (`storypost/`), whose full-bleed photo and video
  parts it reuses: `NewStoryRoute` of `core:navigation`, full screen, opened from the "Your story" ring
  (`feature:chats`' `StoryRingsRow`, the rings being this work's, not the chat list's), which then shows first even
  while the user has no active story, with an add badge (Owner decision to confirm (2026-11-10)): a tap opens the user's
  stories where there are some and posting where there are none, and its long press always opens posting.
  The picked items, their order, the caption's text and the privacy choice are in its `SavedStateHandle`; the files are
  copied into the upload cache (`uploads/<uuid>/`) by `core:data`'s `AttachmentPreparer` as picked photos are (upright
  JPEG without metadata, videos as they are), so the picker's grants may lapse.
- **Posting.** `core:domain`'s `StoryPostRepository` (`TdlibStoryPostRepository`, `core:data`, an `UpdateStore`
  `TdlibUpdateCache` of the stories being posted) asks `canPostStory` before the composer opens and before "Post", and
  says why not in the user's words, never offering Telegram Premium or boosts (`CLAUDE.md` rule 3). "Post" posts the
  items one after another, in the strip's order, each with the caption only on the first (as Telegram's apps do with
  several items), the chosen privacy, `active_period` 86 400 (24 h; other periods need Telegram Premium), the user's own
  chat, posted to the profile (`is_posted_to_chat_page` true) and `protect_content` false. Each file is deleted by
  `UploadCleaner` once TDLib took it, or when the post fails or is cancelled.
- **Long videos.** A video longer than 60 s is cut on the phone into parts of at most 60 s, at the key frames before
  each limit, with the platform's `MediaExtractor` and `MediaMuxer`, without encoding again (no library, no quality
  lost); each part is a story of its own in order. A video whose key frames lie more than 60 s apart, or whose tracks
  the muxer refuses, is refused with a snackbar ("This video can't be cut into stories"). Each part counts toward the
  10 items.
- **Privacy.** The chip opens a `ModalBottomSheet` of the four choices as radio rows; Close friends shows how many
  (`getCloseFriends`) and Selected opens a picker of the contacts (the privacy exceptions' picker sheet,
  `feature:settings`' parts copied, as features share no code). "Everyone" and "Contacts" are posted without
  exceptions; editing the close friends list stays in Telegram's own settings for now.
- **Progress.** The ring of the user's own stories (`StoryRingsRow`) shows the items being posted: a determinate
  `CircularWavyProgressIndicator` around the avatar and "Posting 62%" (the files' uploaded bytes over their sizes, from
  `updateFile`) under it, read in the draw phase. A tap opens a `ModalBottomSheet` with each item's progress and
  "Cancel posting", which deletes the stories still being posted. A failed post keeps the ring red with "Couldn't post"
  until tapped, which offers "Try again" (posting the item again from its copy) and "Discard". While they upload,
  `TransferRepository` counts their files as the user's transfers too (today it counts the files of messages being
  sent), so `TransfersWorker` keeps the app running when it goes to the background
  (`docs/adr/0009-background-transfers.md`).
- **Left out.** Owner decision to confirm (2026-11-10): story editing tools in a later PR. "Add text", "Add sticker"
  and "Draw" (the composer's story areas and stickers, td_api.tl `inputStoryAreas`, `added_sticker_file_ids`) are not
  in this work. Also left out: posting as a channel, albums when posting, reposting another story, and the other
  active periods.

## Consequences

- No new permission, dependency or library; no network but TDLib.
- Posting is three pull requests: picking (10a), composing and posting (10b), and the ring's progress (10c). Each adds
  its `docs/DESIGN_SYSTEM.md` §6 rows and §13.11 notes.
- Owner decisions to confirm (2026-11-10):
  1. Story editing tools (text, stickers, drawing) in a later PR.
  2. Cutting long videos on the phone at key frames, without encoding again; parts may be a little shorter than 60 s.
  3. The caption only on the first of several items.
  4. Editing the close friends list stays outside Pingogram for now.
  5. At most 10 items per post, the Photo Picker's limit chosen for the strip.
  6. The "Your story" ring always first, with an add badge, as the way into posting.
