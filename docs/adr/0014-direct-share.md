# 0014 — Direct share: a share target, a chat picker and the composer

Status: accepted (scope set by the coordinator, 2026-10-22; `REQUIREMENTS.md` FR-N2, `docs/ROADMAP.md` M4)

## Context

Other apps share text, links, photos, videos and files through Android's share sheet (`ACTION_SEND`,
`ACTION_SEND_MULTIPLE`), and the share sheet lists recent conversations of messaging apps (Direct Share targets,
sharing shortcuts). Telegram's API terms forbid sending anything the user did not ask for, and Pingogram adds no
permissions without the owner: the shared files must be read with the grant that comes with the share, never with a
storage permission. `t.me/share?url=…&text=…` links ask for the same thing: pick a chat and send a text
(`InternalLinkTypeMessageDraft`, `docs/adr/0013-deep-links.md`).

## Decision

- **Intent filters** on the single-task `MainActivity`: `SEND` for `text/plain`, `image/*`, `video/*` and `*/*`;
  `SEND_MULTIPLE` for `image/*`, `video/*` and `*/*` (pinned by `AppManifestTest`). `shareOf` reads `EXTRA_TEXT` and
  `EXTRA_STREAM` (or the clip data's URIs), at most 100 files, only `content://` URIs and never one of Pingogram's own
  providers (an app could otherwise make Pingogram send TDLib's files), and the chat of a Direct Share target
  (`EXTRA_SHORTCUT_ID`). The files go as photos and videos when the sharing app's type says all are images or videos
  (`SharedContent.kindOf`), otherwise as documents. A share replayed from the recent apps is not handled again.
- **When**: like a link, a share waits in `MainActivityViewModel` for the login and the passcode lock, in memory only:
  the read grants end with the process (and last while the activity's task does), so nothing is restored after
  process death.
- **Where it goes**: `feature:chat`'s `ShareInbox` (a singleton, in memory) hands it to the screen that takes it.
  - From Pingogram itself in the share sheet, or a `t.me/share` link: the **share sheet** (`ShareRoute(shareId)`, a
    dialog destination like the forward sheet, reusing its search field, chat list and rows through `ChatTargets`):
    Saved Messages first, then the chat list and search, only chats where the user can write. One chat picked: "Open
    chat" opens it with the share in its composer. Several: an optional comment and "Send", which sends to each
    (`ShareRepository`), the comment first; the result is a snackbar of the app shell.
  - From a **Direct Share target**: that chat opens above the chat list, as from a notification, with the share in its
    composer.
  - **The composer** takes it once the conversation is on screen (`ChatShares`, so a conversation hidden under another
    screen leaves it to the one shown): a text joins the field after the chat's draft and becomes the draft; files join
    the attach preview, prepared like picked files (`AttachmentPreparer`, copies in `uploads/`), the text as their
    caption. The user sends it.
- **Several chats** (`TdlibShareRepository`): the files are copied once and the same copies go to every chat. The
  first chat's upload would let `UploadCleaner` delete them before the next chats are sent, so the share holds them
  (`UploadCleaner.hold`) until every chat was sent to, then releases them after the cleaner's grace; they go once no
  message uploads them. A refusing chat counts as failed; a `FLOOD_WAIT` stops the share.
- **Direct Share targets**: the conversation shortcuts (`ConversationShortcuts`, Android 11+) carry the category of
  the app's `<share-target>` (`res/xml/shortcuts.xml`), so every chat with a notification is a target, and
  `ShareTargetShortcuts` publishes the 8 most recent chats of the main chat list where the user can write (Saved
  Messages left out) 5 s after they last changed, ranked in the list's order, removing those that dropped out.
  Logging out removes them with the other shortcuts. No `androidx.sharetarget` (Android 10 and older get no targets).
- **Telegram API terms**: nothing is sent without the user's tap on "Send" in the sheet or in the composer; no
  automation, one share at a time.

## Consequences

- No new permission and no new dependency.
- Android 10 shows Pingogram in the share sheet but no Direct Share targets; Android 8–10 users pick chats in the
  sheet.
- A share whose files' grant ended (the process died while the user picked a chat) fails to prepare them, with the
  composer's "file unavailable" snackbar.
- Shared files to one chat use the composer's single preview kind: mixed photos and documents go as documents.
