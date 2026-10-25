# 0031 · Multiple accounts

Date: 2026-10-25 · Status: proposed, waiting for the owner's answers below (`docs/audit/feature-gaps-octogram.md`, P1,
"Owner"; `REQUIREMENTS.md` §1.2 excludes multi-account *automation*, not several accounts of one person).

## Context

Pingogram signs in one Telegram account. Telegram's own apps and most other clients let a person keep several accounts
(a personal and a work number) in one app and switch between them, with notifications from all of them. It is the
largest gap the feature audit lists.

What TDLib gives:

- One `Client` per account, each with its own `databaseDirectory` and `filesDirectory`: several clients run in one
  process, each with its own updates, login steps and connection.
- Push (checked in the pinned bindings, a94b83d): `registerDevice(device_token, other_user_ids)` names the other accounts of the app, and `getPushReceiverId(payload)`
  tells which account a push is for, without decrypting it, by the `push_receiver_id` that `registerDevice` returned.
- No limit on accounts: the limit is the client's policy. Telegram's apps allow 3 accounts, more with Telegram Premium.

What Pingogram has today, all written for one account:

- `core:tdlib`: one `DefaultTdlibClient` (`@Singleton`), its directories `filesDir/tdlib` (`tdlib-test` on the test
  DC), its database key in `noBackupFilesDir/tdlib_database_key`, wrapped by one Keystore key.
- About 86 `@Singleton`s in `core:data`, 20 of them caches of TDLib's updates (`TdlibUpdateCache`), and the parts that
  follow them in every process: `NotificationRenderer`, `ShareTargetShortcuts`, the widget, transfers, `CallManager`,
  `GroupCallManager`, push. View models take the repositories' interfaces from Hilt.
- Logging out (`AuthorizationStateClosed`) resets every cache and the navigation graph, and TDLib's client is created
  again at `WaitTdlibParameters`.

## Proposal

### Accounts and storage

- **An account is a slot** (`AccountSlot`, 0 to 2), created when the user adds an account and kept in the settings
  file with its user id once logged in, its test DC flag and its order. The first slot keeps today's directories and
  key file, so an update moves nothing: `tdlib/` and `tdlib_database_key` for slot 0, `tdlib-1/`,
  `tdlib_database_key_1` (and `tdlib-test-1/`) for the others.
- **A database key per account**: 32 random bytes each, wrapped by a Keystore key of its own (`tdlib_db_wrap_1`, …), so
  removing an account deletes its key, alias and directories, and a damaged key costs only that account
  (`docs/TDLIB.md`, Database encryption key).
- **One client per account, all running**: each account's TDLib client starts with the app, so notifications and calls
  arrive for every account, as in Telegram's apps. TDLib's memory is about 10–30 MB per account.

### One graph per account

- **An account component** (a Hilt `@DefineComponent` child of the singleton component, `AccountComponent`, built once
  per slot by an `Accounts` singleton) holds what belongs to one account: its `TdlibClient`, every repository of
  `core:data` (each with its own cache, so caches are kept per account without changing any repository), and the
  background parts (notifications, share targets, transfers, calls, push).
- **The active account** (`Accounts.active`, a `StateFlow`) is the one the screens show. The repository interfaces
  that view models inject are bound, unscoped, to the active account's instances at the time the view model is made.
  Switching closes the main graph and opens it again, as a logout does today, so every view model is made again for the
  new account; nothing keeps a reference to the old one's repositories past the switch.
- **Settings that are the app's, not the account's**, stay singletons: appearance, language, the passcode lock, power
  saving, automatic download, proxies (TDLib keeps proxies per client: Pingogram sets the same list on every client).
  Those that are Telegram's (privacy, notification settings, the account's own) are per account already.

### The switcher

- **Settings home's account card** gets the accounts below it: one segmented row per other account (avatar, name,
  phone, unread count `Badge`), "Add account" (`ic_person_add`) while fewer than the limit are signed in. A tap switches
  (the app shows the chat list of that account, with the shared axis X). The card itself still opens Edit profile.
- **A long press on the Settings tab** (the floating navigation bar and the rail) opens an expressive
  `DropdownMenuPopup` anchored to it: the accounts with their unread counts, the active one checked, and "Add account".
  Screen readers get the same as the tab's custom actions.
- **Adding an account** opens the sign-in flow (`feature:auth`) for a new slot above the current account, with back
  returning to it; once logged in, the new account becomes the active one. An account already signed in (the same user
  id) is refused and its slot removed.

### Notifications, shortcuts and links

- **Per account**: each account's renderer posts under tags that carry its slot (`group:1`), its summary groups apart
  (`setGroup` per slot), and its conversation shortcuts' ids carry the slot (`chat:1:<chatId>`; slot 0 keeps today's
  ids, so existing shortcuts keep working).
- **Channels**: with more than one account, Android's notification channel groups, one per account, named by the
  account ("Ada Lovelace"), each with the five channels, so a user can silence one account. One account keeps today's
  channels.
- **Which account opens**: a notification, a conversation shortcut and a Direct Share target carry their slot; opening
  one switches to that account first (after the passcode lock), then opens the chat. Telegram links and shares from
  other apps open in the active account.
- **The notification's account**: with more than one account, a notification shows the account's name as its
  sub-text ("Work"), as Telegram's apps do.

### Lock, widget, push, logout, limits

- **Passcode lock**: one lock for the app, as today (ADR 0005); unlocking unlocks every account.
- **Widget**: the active account's recent chats; a widget per account is left for later.
- **Push (`gms`)**: one FCM token for the app; each account registers it with `registerDevice`, naming the others in
  `other_user_ids`, and keeps the `push_receiver_id` it got. A push goes to the account `getPushReceiverId` names (asked
  of any running client), or to every account when none matches.
- **`foss`**: "Keep connected" keeps every account's connection, as its one service already keeps the process.
- **Logging out of one account**: Settings → Log out acts on the active account; the app switches to another signed-in
  account (or Welcome when none is left) and deletes the slot's directories, key and settings once TDLib closed it.
- **Limit**: 3 accounts, as Telegram's apps without Premium; a fourth with Telegram Premium on one of them, where the
  owner agrees (question 3). Pingogram never offers Premium.
- **Calls**: one call at a time across all accounts (`CallLine`): an incoming call on another account while in a call
  is declined, as today's second call is.

### Stages, one pull request each

1. Account slots and storage: slot 0 as today, per-slot directories, keys and their migration test; still one account.
2. The account component: `TdlibClient` and `core:data` move into it; the active account feeds the screens; still one
   account.
3. Adding an account and the switcher (Settings card, the tab's long press), switching, logging out of one.
4. Background accounts: their notifications, shortcuts, channel groups, calls; opening the right account.
5. Push per account (`gms`) and "Keep connected" for all (`foss`).
6. The limit, the widget and the docs (`ARCHITECTURE.md`, `DESIGN_SYSTEM.md` §6 entries for the switcher).

## Questions for the owner

1. Every account's client running all the time (notifications from all, ~10–30 MB and a connection each), or only the
   active one (less memory and data, no notifications from the others)?
2. The switcher's places: Settings home's account card and a long press on the Settings tab, as proposed?
3. The limit: 3 accounts, and a fourth with Telegram Premium on one of them, as Telegram's apps do?
4. Notification channels per account (channel groups) once there are two accounts, or one set of channels for all?
5. The widget: the active account's chats, or a choice of account per widget?

## Consequences

- Stage 2 touches every module that injects `core:data` bindings (the bindings move into the account component); the
  screens and view models keep their code, as they keep their interfaces.
- Tests: `FakeTdlibClient` per slot; a test of switching that checks no view model of the old account survives.
- Memory and data grow with each account; `docs/PERFORMANCE.md` §1's idle memory budget is measured with one account
  and needs a line for more.
