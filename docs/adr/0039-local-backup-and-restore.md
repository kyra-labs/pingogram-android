# 0039 — Backup and restore of what Pingogram keeps on the phone, in a file the user chooses

Status: accepted (owner's request; the owner took both recommended choices, 2026-11-17) ·
Date: 2026-11-17 ·
Milestone: after `v2.3.0` (`docs/ROADMAP.md`)

## Context

Much of what users set up in Pingogram never reaches Telegram's servers, so nothing brings it back on a new phone,
after a reinstall, or after logging out:

- **The settings file** (`SettingsDataStore`, one for the app): appearance, chat list, notifications, Focus profiles
  (`DataStoreFocusRepository`), keyword alerts' words and scope (`DataStoreKeywordAlertsRepository`), auto-download,
  calls, stickers, transcription, playback and the other `DataStore*Repository` settings. The passcode's hash and
  settings live in the same file.
- **Each account's files** in its TDLib directory (`TdlibDirectories.of(filesDir, slot).root`): bookmarks
  (`bookmarks.txt`, `FileBookmarkRepository`), reminders (`reminders.txt`, `FileReminderRepository`) and keyword hits.
  They name chats and messages by their ids, which mean something only to the same Telegram account, and the account
  leaving deletes them with its directory.

Android's own backup is off on purpose (`android:allowBackup="false"`, `REQUIREMENTS.md` §6: TDLib's directories never
go into a backup) and would send data to Google, which `CLAUDE.md` rule 10 rules out unless the owner adds it.

The owner asked for a backup in the phone's storage that the app finds by itself after signing in. Since Android 11 an
app cannot scan shared storage for a file an earlier install wrote: reading another app's (or a removed install's)
files needs `MANAGE_EXTERNAL_STORAGE`, which Google Play allows only for file managers and similar apps. What Android
does allow is the Storage Access Framework: the user picks a folder once (`ACTION_OPEN_DOCUMENT_TREE`), and the app
keeps the right to read and write it (`takePersistableUriPermission`) for as long as it stays installed. Chat export
already writes through the Storage Access Framework (`ChatExportRoute`, `ExportWriter`).

## Decision

### What a backup is

- One file, `Pingogram backup YYYY-MM-DD.pgbackup`, written into a folder the user picked: a small header (format
  version, app version, date, which sections it holds) and a body of JSON sections, compressed, then encrypted (below).
- **Sections**:
  - `settings`: every setting of the settings file **except** the passcode (its hash, salt, auto-lock, hide content),
    the locked chats' state, the account registry, one-time hints and prompts (viewer hints, notification prompt,
    welcome pack, declined birthdays) and anything a setting stores about a device (power saving's measurements).
  - `account`: for each logged-in account, its Telegram user id and its bookmarks (with tags and notes), reminders,
    birthday reminders (`REQUIREMENTS.md` FR-X18), keyword hits and its notes and names for chats (FR-X20).
- Nothing from TDLib's database, no messages, no media, no session or key: signing in brings those back from Telegram.

### Backing up

- **Settings → On this phone → Backup & restore**: "Backup folder" (picked once, shown by name; "Change"), "Back up
  now", and the last backup's date and size.
- **Automatic backups** (owner decision, 2026-11-17): "Back up automatically" with Off · Daily · Weekly (default
  Weekly once a folder is picked), run by WorkManager while charging or with battery not low, keeping the newest 3
  files.

### Restoring

- **After signing in** (and on the Backup & restore page): if a backup folder is kept and holds backups, a card in the
  chat list offers "Restore from your backup of 12 Nov?" with Restore and Not now (Not now hides it for that backup).
  This covers logging out and in again, and adding an account, on the same install.
- **After a reinstall or on a new phone**, where no folder is kept yet: the card says "Restore settings from a backup?"
  and Choose file opens the file picker (`ACTION_OPEN_DOCUMENT`), which also finds backups in Drive or a USB stick.
- **Restore** asks for the backup's password (below), shows what the backup holds ("Settings, 42 bookmarks, 5 reminders
  for @name") and then:
  - `settings` replace the current ones, except the passcode, which stays as it is on this phone;
  - `account` sections are restored only into the logged-in account with the same Telegram user id; bookmarks and
    reminders merge with any already on the phone (the same message keeps the newer entry); reminders in the past are
    restored as done; a birthday reminder comes back for a contact who has none on the phone, and is scheduled again;
    a chat's note and name come back for a chat that has none on the phone; a backup of another account says so and
    restores only the settings.
- A restore never sends anything to Telegram and never deletes a chat, message or file.

### Protecting the file

- **A password** (owner decision, 2026-11-17): always encrypted with a password the user sets at the first backup
  (AES-256-GCM, its key from the password with PBKDF2-HMAC-SHA512, 210,000 rounds and a random salt, as the passcode,
  ADR 0005), because the file can end up in Drive or on another device and holds bookmark notes and reminder notes. The
  password is never kept: automatic backups encrypt with a key derived once, kept in Android's Keystore for that
  purpose.

### Privacy and permissions

- No new permission, no network: the file goes only where the user puts it.
- Secret chats' bookmarks and reminders, and those of locked chats, are left out (secret chats exist on one device
  only; a locked chat's existence is itself private), as is the birthday reminder of a contact whose chat is locked.
- The demo account (ADR 0034) is never backed up and never offered a restore.

## Consequences

- Users can move what they set up in Pingogram to a new phone, and get it back after logging out, without anything
  leaving the phone except by their own choice of folder.
- Every new setting must say whether it goes into a backup: the `settings` section lists the keys it holds rather
  than copying the whole file, so a new key stays out until it is added there, with a test that every key of the
  settings file is either backed up or listed as left out.
- A format version in the header: a newer app restores older backups; an older app refuses a newer backup with a
  message to update.
- Work, in PRs of their own: (1) `core:domain` / `core:data` backup format, writer and reader with their tests;
  (2) the Backup & restore page and the restore card in `feature:settings` and `feature:chats`; (3) automatic backups
  with WorkManager. `REQUIREMENTS.md` gains the requirement, `docs/ARCHITECTURE.md` a
  "Backup and restore" section, and `CHANGELOG.md` an entry with the page.
