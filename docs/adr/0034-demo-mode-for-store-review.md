# 0034 · Demo mode for store review

Date: 2026-11-01 · Status: accepted by the owner on 2026-11-01 (the brief of that day); points marked "to confirm"
are Pingogram's reading where the brief leaves a choice open. Replaces `REQUIREMENTS.md` FR-A9's test-server sign-in.
M6, store readiness (`docs/PLAY_STORE.md`, `docs/STORES.md`).

## Context

Every Telegram login needs a one-time code that Telegram sends to the account's phone. So the reviewers of Google Play,
Galaxy Store and AppGallery cannot be given credentials. Their forms ask for a way into an app that needs an account
("App access").

FR-A9 (owner decision 2026-10-25) answered that with Telegram's test accounts (+99966 X YYYY), which sign in on
Telegram's test servers with a fixed code. That no longer works: Telegram's test servers no longer sign test accounts
in. Nothing else on Telegram's side gives a reviewer an account.

## Decision

A **demo mode**: sample chats stored on the phone, shown by the app's own screens without a Telegram account and
without the network. Store reviewers open it with steps in the stores' App access instructions. Everyday users do not
see it.

### Entry and exit

- **Hidden entry**: on the Welcome screen, pressing and holding Pingogram's logo for about a second (a `LongPress`
  haptic) opens an `AlertDialog`: "Open the demo?", "It shows sample chats stored on this phone, without a Telegram
  account.", "Cancel" / "Open". Nothing on screen points to it.
- **Screen readers**: there is no custom action for it. It is for store reviewers, who have the steps.
- **Flavors**: it is in both. Play and Galaxy Store review `gms`, AppGallery reviews `foss`.
- **Labels**: while the demo shows, a slim banner under the status bar, above every screen of the app shell, says
  "Demo · Sample chats on this device. Not connected to Telegram." Settings' account card shows "Demo" in place of a
  phone number.
- **Leaving**: "Leave demo" sits where "Log out" is, in Settings. It returns to Welcome and deletes everything the demo
  made.
- **Restarts**: rotation and process death keep the demo as it was.
- **A banner, not a label per top bar** (owner, 2026-11-01): one place covers every screen, also those without a top
  bar (the media and story viewers, the scanner, full-screen sheets), and no screen changes.

### The demo client

- **`DemoTdlibClient`** implements `core:tdlib`'s `TdlibClient`. It answers TDLib functions from built-in data and emits
  the updates TDLib would: `updateAuthorizationState` (ready), `updateUser`, `updateNewChat`, `updateChatPosition`,
  `updateChatFolders`, `updateNewMessage` and `updateMessageSendSucceeded` for what the user sends, and the read states.
  So the repositories, view models and screens work unchanged.
- **New module `:core:demo`**: the client and its sample data live there. It depends on `core:tdlib` for the interface
  and `TdApi`'s plain Java classes, and on `core:common`. It never touches `org.drinkless.tdlib.Client` or the JNI
  library, so no TDLib instance exists for the demo.
- **Unsupported functions** fail with a TDLib error the repositories map to `DomainError.NotAvailableInDemo`. Error
  texts and snackbars show it as "This needs a Telegram account. The demo can't do it." The places are listed below
  ("What needs a Telegram account").
- **Full access** (owner, 2026-11-01): Google Play's App access form asks whether the reviewers' access covers every
  feature, so the demo answers every screen of a signed-in session with sample data, calls included, and "needs a
  Telegram account" is left only where no sample makes sense, each such place listed in this ADR for the owner to
  check. The client asks areas of functions in turn (`DemoArea`: messages, chats…), added in small pull requests.
- **Calls** (owner, 2026-11-01): scripted. Calling a sample person rings after a second and is answered a few seconds
  later; the call screen shows the timer, the key's four emojis (made up) and every control: mute, speaker or audio
  route, camera with the real self-view, hold, screen sharing's consent, hang up. The call button's menu offers
  "Receive a demo call", which has that person call a moment later. The book club's voice chat goes on with sample
  people, who speak in turn. Telecom, the call notification and the service run as in a real call, and the camera and
  microphone permissions are asked as there; no audio or video leaves the phone and nothing reaches Telegram or
  NTgCalls' network: the demo's engine connects to nothing, never loads NTgCalls, opens the camera only for the
  self-view while it is on, and does not open the microphone. Each call ends in the chat and in Recent calls.
- **Persistence**: each function the demo took as a change (a sent message, a reaction, an edited folder…) is appended
  to a small file in `filesDir/demo/` with its date, its files copied into the demo's folder first, and replayed through
  the same code when the demo client starts. So a process death keeps it, and leaving deletes the folder.

### What needs a Telegram account

Everything else a signed-in session shows works on the sample data. These places alone have no sensible sample and
say that they need a Telegram account, or are not shown in the demo (owner to check):

- **Deleting the account** (Settings → Privacy and security → Delete account): the demo has no account. "Leave demo"
  deletes what the user did in it.
- **Signing in, adding an account and logging out**: left out of the demo's screens; "Leave demo" takes the place of
  logging out.
- **Conference calls** (`t.me/call/…` links, upgrading a call): the demo has no conference call to join, so a call
  does not offer "Add people" and such a link says it needs a Telegram account.
- **GIF search**: the demo names no GIF search bot, so the GIF tab shows the saved GIFs (none) without its search
  field. The sample bot shows inline results, buttons and a keyboard instead; Mini Apps, games and payments are not
  offered by Pingogram at all.
- **Sponsored messages**: Telegram chooses them; the demo shows none rather than made-up advertisements.
- **Notifications, push and the home screen widget**: they never see the demo (its account graph is kept apart).

What the demo has no data for is not an error but empty, as Telegram would answer: public chats, usernames, invite
links and message links that are not the demo's find nothing, and public search finds only the demo's chats.

### Its own account graph

The demo runs as an account graph of its own, made as `docs/adr/0031-multiple-accounts.md` makes one per account: an
`AccountComponent` with the demo client, in a scope of its own, under a slot of its own (`AccountSlot.Demo`), not one of
the four of real accounts, with no TDLib directories or database key.

- **Not in the registry**: `Accounts` keeps it apart from the registry of accounts. `Accounts.active` (what
  `ActiveAccountModule` binds the repository interfaces to, so what the screens see) is the demo's graph while the demo
  is on. `Accounts.all()`, `registry` and `AccountGraphs` never list it.
- **What follows from that**: notifications, push registration, widgets, conversation shortcuts, Direct Share targets,
  the weekly storage clean-up and the call managers follow the registry's accounts, so none of them sees the demo. The
  demo never counts toward the accounts limit.
- **Real accounts**: an account signed in on the device keeps running, unaffected.
- **Entering and leaving**: whether the demo is on is a marker file, `noBackupFilesDir/demo`. Entering writes it, and
  leaving deletes it and the demo's folder. Then the app opens again in a new task (`AccountRestart`), as switching
  accounts does, so no screen keeps the other graph's repositories.
- **Not offered in the demo** (to confirm): settings that belong to the device and guard real accounts say "Not
  available in the demo". They are the passcode lock, locked chats, contacts sync, "Keep connected" and adding an
  account. Appearance and the app's other preferences are the device's and stay as the user leaves them.

### The sample data

Kotlin in `:core:demo`, in English. The texts shown as UI are translatable strings. It holds:

- **Chats**: Saved Messages, 3–4 people, a group with several members, a channel with a few posts, one archived chat,
  and one folder, "Work".
- **Messages**: text with formatting and links, emoji-only, a reply, a forward, a poll, a photo, a voice message, a
  sticker drawn from Pingogram's own mark, reactions, a pinned message, and unread counts.
- **Pictures**: only Pingogram's own artwork or images generated in code. Avatars are the app's initials. The photo is
  a gradient of the brand's colors. The sticker is the mark rendered to a bitmap. The voice message is a short tone
  written as a WAV file. No stock photos and no Telegram assets (CLAUDE.md rule 9).
- **Names**: fictional and plainly so ("Ada Example").
- **Sending**: what the user sends appears in its chat. It is gone when the demo ends.

### Telegram's terms

The demo is never a way around them (`https://core.telegram.org/api/terms`, CLAUDE.md rule 3):

- It uses no network at all, and no TDLib client is created for it.
- Nothing in it looks like a real person's account.
- Its labels never imply that it is connected to Telegram.

### The test-server sign-in goes

These are removed:

- `TdlibClient.servers` and `useServers`, `TdlibServers`;
- `core:domain`'s `TelegramServers` and `telegramServersFor`, and `AuthRepository.servers`;
- the "Test server" labels;
- the handling of +999 numbers on the phone number step;
- their tests and strings.

FR-A9 is replaced by the demo mode. What an earlier version left for the test servers (the `tdlib-test` directories and
the `tdlib_test_servers` marker) is deleted when TDLib starts. Everything that serves a real sign-in stays.

### Permanent

The stores review every update, so App access must work for each release:

- The demo is never removed, and the logo is never moved or its gesture changed, without the owner's decision and the
  store instructions updated in the same pull request.
- `docs/RELEASE.md`'s checklist checks it before each store upload: press and hold the logo on a phone and see the demo
  start.

## Consequences

- The reviewers get the app's real screens with sample data, offline, in both flavors, without an account. Reviewers
  with their own Telegram account can still sign in with it.
- One more module and one more `TdlibClient`. Each screen's requests need an answer in the demo client, or the screen
  says it needs a Telegram account. New screens add their answers to the demo, or their place to the list above.
- No dependency and no permission are added. The sample pictures are made on the device the first time the demo starts.
- Built in three pull requests:
  1. this ADR and the removal of the test servers;
  2. the demo client, its data and its graph;
  3. the entry, the labels, leaving, and the store instructions.
