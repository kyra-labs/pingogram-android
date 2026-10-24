# 0028 · Locked chats

Date: 2026-10-24 · Status: proposed, waiting for the owner's answers below (`docs/audit/feature-gaps-octogram.md`, P1,
"Owner"). Extends `docs/adr/0005-passcode-lock.md`.

## Context

The passcode lock (ADR 0005) covers the whole app. Users also ask to keep a few chats behind it while the rest of the
app stays open, as Octogram and other clients offer: a chat shared with a partner, a work chat on a family tablet.

What Telegram and TDLib give:

- Nothing. td_api.tl has no "locked" or "hidden" chat: the lock would be Pingogram's alone, kept on this device and
  not synced to the user's other apps, where the chats stay as they are.
- The chat list, search, folders, notifications, the home screen widget, conversation shortcuts and Direct Share all
  name chats today, each through its own repository or builder.

## Proposal

- **Needs the app's passcode.** "Lock chat" is offered only once Settings → Privacy and security → Passcode lock is
  on; the same passcode (and biometrics, where turned on) unlocks them. Turning the passcode off asks first and
  unlocks every chat.
- **Storage:** the locked chats' ids in the settings file (`DataStore`), never backed up (`android:allowBackup` is
  false), cleared at logout.
- **Locking:** a chat's actions ("Lock chat" in the chat list's selection toolbar overflow and on its profile or info
  page), and Settings → Privacy and security → "Locked chats" listing them, with "Unlock" per chat.
- **One unlock for all:** unlocking opens every locked chat until the app locks again (the auto-lock delay of ADR
  0005, or leaving the app), not one chat at a time.
- **Everything outside the open chat follows the decisions below**, through one `LockedChats` repository that the
  chat list, search, notifications, the widget and the share targets read, so no place forgets one.

## Decisions for the owner

1. **Where locked chats show while locked.**
   - (a) Hidden from the chat list, folders, search and the archive, reached only through a "Locked chats" row at the
     end of the chat list that asks for the passcode. Proposed: someone holding the phone sees no trace beyond that
     row.
   - (b) Shown in the list with their name and avatar, the preview replaced by "Locked", opening behind the passcode.
2. **Notifications of locked chats.**
   - (a) None at all. Proposed, since Android shows notifications on the lock screen and in the shade, outside the
     app's lock.
   - (b) One summary "New message" without the chat's name, sender or text, opening the passcode.
   - (c) As other chats (only the app's UI is locked).
3. **The home screen widget, conversation shortcuts and Direct Share:** leave locked chats out of all three.
   Proposed: yes.
4. **Sync:** locks stay on this device, and other devices show the chats as usual. The setting's summary says so.
   Proposed: yes; there is no Telegram API for it.
5. **Lock the archive, secret chats, call history or Settings** (P2 in the audit): the same mechanism later, each its
   own switch. Proposed: not in the first pull request.

## Consequences

- No new permission, dependency or network request: a list of ids in the settings file.
- It protects the UI only, as the app lock does (ADR 0005). TDLib's database still holds the chats, encrypted with
  the Keystore key.
- Touches Session A's modules (`feature:chats` for the list and search, `feature:chat` for opening a chat) in small
  commits, and `core:notifications`, the widget and `ShareTargetShortcuts` in `:app` and `core`.
