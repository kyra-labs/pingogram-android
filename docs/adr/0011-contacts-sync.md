# 0011 — Contacts sync: opt-in import of the phone's contacts

Status: accepted (owner approval 2026-10-21)

## Context

`REQUIREMENTS.md` FR-P1 asks for a contacts list. Part 1 lists the user's Telegram contacts and adds them by phone
number or username, without touching the phone's address book. Telegram's own apps also import the phone's contacts,
so that people the user knows show up as Telegram contacts. That means sending the names and phone numbers of everyone
in the address book to Telegram, which is personal data of third parties, and it needs Android's `READ_CONTACTS`.
Pingogram promises no telemetry and network traffic only to Telegram (`REQUIREMENTS.md` §6.2). Every new permission
needs the owner's approval (CLAUDE.md).

## Decision

- **Opt-in, off by default.** "Sync contacts" is offered on the Contacts tab (a card, which "Not now" dismisses for
  good) and as a switch in Settings → Privacy and security. Turning it on first shows a rationale sheet that says what
  is sent (names and numbers), to whom (Telegram, its own contact import), when, and how to undo it. Only then does
  Android ask for `READ_CONTACTS` (Activity Result API), the permission's only use. A refusal for good offers the app's
  settings instead.
- **When it reads and sends.** Only on turning it on, on "Sync now", and when the Contacts tab starts while it is on,
  at most once per app process and not within 6 hours of the last sync (a time kept in DataStore). Otherwise the tab
  matches the phone's numbers with the Telegram contacts on the device, sending nothing. No WorkManager job, no
  `ContentObserver`, no reading in the background. Nothing of the address book is stored by Pingogram; the result
  stays in memory.
- **How.** One `ContactsContract` query (contact id, display name, number, normalized number) on the IO dispatcher;
  numbers in E.164 (the provider's, else `PhoneNumberUtils` for the network's, the SIM's or the language's country).
  Every sync sends the whole list with TDLib's `changeImportedContacts`, which compares it with the list TDLib
  imported last (kept in its database), imports new numbers, deletes removed ones and returns a user id per contact
  (0 for a number not on Telegram). One request at a time, as TDLib requires. `importContacts` is not used for sync:
  it would not update TDLib's saved list, so the next `changeImportedContacts` would import everything again.
- **Results.** People found are Telegram contacts (TDLib's `updateUser` brings them into the list) and are marked as
  from the phone; the others are listed under "Invite to Telegram", each opening the messaging app (`ACTION_SENDTO
  smsto:`, no `SEND_SMS`) or the share sheet with the invitation, sent by the user.
- **Reversible.** Turning it off sends `clearImportedContacts`: Telegram deletes the imported numbers, the contact
  list stays (contacts can be deleted on the tab). The local settings are cleared, and a snackbar points to Android's
  settings to take the permission back. If Telegram cannot be told, sync stays on and says so. Logging out clears the
  local settings, so another account starts with sync off. A permission taken back in Android's settings pauses sync
  until it is granted again or sync is turned off.

## Consequences

- `READ_CONTACTS` joins the manifest, pinned by `AppManifestTest` with its only use.
- The Contacts tab and Settings share the flow (`core:ui` `ContactSyncController`, `ContactSyncHost`).
- The invitation list after a process restart within the 6-hour interval comes from matching numbers on the device:
  a Telegram user whose number Telegram hides can show under "Invite to Telegram" until the next sync.
- Telegram may limit imports (`FLOOD_WAIT`); the sync row shows the error and "Sync now" tries again.
