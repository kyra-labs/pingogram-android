# 0021 — Chat export to a folder of the user's, as HTML or JSON

Date: 2026-10-23 · Status: proposed

## Context

`REQUIREMENTS.md` §5.9 lists chat export for 1.0: the history of a chat, with its media if the user wants, written to
a folder the user picks, as a page to read or as data for programs, in the background with progress and a way to
cancel. `REQUIREMENTS.md` §6.2 and `CLAUDE.md` rule 10 bind it: nothing leaves the device but what the user takes
from the folder. No storage permission may be added (`CLAUDE.md`, "Ask the owner before"), and none is needed: the
Storage Access Framework hands the app one folder the user picked. Telegram's own apps export the chats of people,
groups, channels and Saved Messages, never secret chats, and refuse chats whose owner turned off saving their content
(`has_protected_content`).

Options considered:

- **Where it is written**: the Storage Access Framework's folder picker (`ACTION_OPEN_DOCUMENT_TREE`) with
  `DocumentsContract`; `MediaStore` Downloads (Android 10+ only, one fixed place, no folder of the user's); a file
  saved with `ACTION_CREATE_DOCUMENT` (one file, so no media beside it).
- **`androidx.documentfile`** over `DocumentsContract`: it only wraps `createDocument`, `deleteDocument` and queries,
  each of which the platform has; it adds nothing the export needs.
- **Running it**: in the page's view model (it ends with the page and the process); WorkManager (as the user's
  transfers, ADR 0009: a `dataSync` foreground service, its own notification, survives the page and process death
  by running again).
- **The order of messages**: TDLib pages the history from the newest message back, a reader wants it oldest first,
  and a stream cannot be written backwards. Holding the whole history in memory breaks `docs/PERFORMANCE.md`'s memory
  budget for large chats; asking for the oldest message first needs a message id TDLib does not give.
- **The size of the history**: td_api.tl's `getChatMessageCount` refuses `searchMessagesFilterEmpty` ("Can't use
  searchMessagesFilterEmpty", in the pinned libtdjni), so no count of every message comes from it.

## Decision

- **Entry**: "Export chat" in the profile's "More" group (`feature:profile`, hoisted as `onExportChat` like
  `onEditContact`, the private chat's id) and in the group and channel info page's last group (`ChatInfoPage.Export`),
  both only where the chat lets its content be saved. Saved Messages through the user's own profile, which its title
  now opens. Not in the conversation's top bar (two actions at most: search, and a call or the timer) nor in the chat
  list's selection toolbar (five buttons fit the narrowest phones, all taken).
- **Not exported**: secret chats (their messages are meant to stay on the device, in the chat, and cannot be
  forwarded) and chats with protected content; the page says why. Self-destructing (view-once) media is named, never
  copied.
- **The page** (`ChatExportRoute(chatId)`, `feature:chat`): format, media kinds with a size limit, all messages or
  those from a day on, the folder; then the export's progress, its result or why it failed. The folder's grant is
  persisted (`takePersistableUriPermission`) from when it is picked until the export into it ends, and given back
  then, or when the page closes without exporting.
- **The job**: `ChatExportWorker` (`core:notifications`), unique work per chat, expedited, in WorkManager's
  foreground service with type `dataSync` (the declaration and permission ADR 0009 added; no new permission), its
  notification in the "Transfers" channel with "Cancel" (`WorkManager.createCancelPendingIntent`); progress through
  WorkManager's progress data, which the page reads. A note that it ended stays after it.
- **Reading**: `getChatHistory` with `only_local` false, 100 messages a page, from the newest back to the day chosen;
  a `FLOOD_WAIT` is waited out for the seconds Telegram asks (at least one), three times at most in a row; files with
  `downloadFile` at priority 1, synchronous, one at a time, cancelled in TDLib when the export is. Progress is
  determinate by the messages read against `searchChatMessages`' total (no query, no filter) where TDLib gives one, by
  the days read against the days chosen when a day is, and indeterminate otherwise.
- **Writing**: each page goes to a temporary file in the app's cache as it is read (records of the export model); the
  output is written from the last page to the first, each reversed in memory, so never more than a page is held. The
  export makes a folder of its own in the one picked, "<chat> – <date>", with `messages.html` or `messages.json`, and
  `media/<message id>_<name>` for each file copied.
- **HTML**: one page with inline CSS (light and dark from the reader's system), no script, nothing loaded from the
  network; user text escaped and in its own direction (`dir="auto"`); links only to the http, https and tg links the
  messages hold, and to the files in `media/`; media not copied is named with why.
- **JSON** (`"format": "pingogram_chat_export"`, `"version": 1`): `exported_at` (ISO 8601, UTC); `chat` (`id`,
  `title`, `type`: `private`, `bot`, `saved_messages`, `group`, `channel`); `message_count`; `messages`, oldest first,
  each with `id`, `date` and `date_unix`, `edited` and `edited_unix` if edited, `from` (`id`, `type` `user` or `chat`,
  `name`; left out for a channel's own posts), `outgoing`, `service` (what a service message reports, in the app's
  language), `text` with `entities` (`type`, `offset` and `length` in UTF-16 code units as TDLib counts them, and
  `url`, `language`, `user_id`, `custom_emoji_id`, `seconds` or `unix_time` where the type has one), `summary` (what
  a message without text is: "Location", a poll's question), `reply_to_message_id`, `forwarded` (`from`, `date`,
  `date_unix`), `media` (`type`: `photo`, `video`, `animation`, `video_message`, `voice_message`, `audio`, `file`,
  `sticker`; `file`, the path of the copy relative to the JSON file; `file_name`; `size` in bytes; `not_exported`:
  `not_chosen`, `too_large`, `self_destructing`, `unavailable`). A field without a value is left out.
- **Cancelled or failed**: the export deletes its folder and its temporary files; nothing half-written stays, rather
  than a folder named "(incomplete)" that looks like an export. A flood wait that does not end, no network, a
  logged-out session, a folder that is gone or full each fail it with their own words on the page.

## Consequences

- Nothing is uploaded: only TDLib is asked, for the chat's messages and their files, and the output goes only to the
  folder picked. Logs hold no message text, names or the folder.
- A long export of media holds the `dataSync` foreground service, within Android 15's daily limit for the type, as
  transfers do; a process Android ends is run again by WorkManager from the start.
- The downloaded files stay in TDLib's cache, as files the user opened do, until "Clear cache" or the storage
  optimizer removes them.
- An export cancelled after a long time loses what it read; trying again starts over.
- The JSON schema is versioned; a change that removes or renames a field raises `version`.
