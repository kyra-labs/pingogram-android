# Feature gaps against Octogram

Status: proposal, 2026-10-24. Source: 17 screenshots of Octogram's settings pages (version 3.6.1 beta 2), shared by the
owner. Octogram is a third-party Telegram client built on the official Android app.

Each setting was compared with Pingogram's `main` at e0026b54: its settings pages, and the TDLib functions the code
calls. The verdicts:

- **Do**: fits Pingogram's rules and is worth building.
- **Owner**: fits, but needs the owner's decision first (a permission, a design rule, Telegram's terms).
- **Queued**: already in a session's list (the prompts of 2026-10-23).
- **Have**: Pingogram already does it, or something equivalent.
- **No**: not built, for the reason given.

Priority applies to Do and Owner items: **P1** is everyday value, or a gap users notice; **P2** is useful polish;
**P3** is nice to have. The owning module follows the session split: A for `feature:chats` and `feature:chat`, B for
`feature:calls`, C for `feature:settings`, `feature:profile` and `feature:chat`'s `panel/`.

## What Octogram has that Pingogram should add

### Chats and conversation

| Feature | What it does | Verdict | Priority | Where |
|---|---|---|---|---|
| Double-tap to react | Double tap a message to add the quick reaction; one setting each for incoming and outgoing messages | Do | P1 | A; quick reaction from TDLib's default reaction (`setDefaultReactionType`), chosen in Settings → Chats |
| Send with Enter | Hardware and on-screen keyboard Enter sends; Shift+Enter starts a line | Do | P1 | A + C (setting); also closes `docs/audit/accessibility.md` A2 |
| Hide keyboard on scroll | Scrolling the messages hides the keyboard | Do | P2 | A |
| Share button beside channel posts and media | A round forward button next to the bubble opens the forward sheet | Do | P2 | A; DESIGN_SYSTEM §6 entry needed |
| Jump to the next unread channel or topic | Pulling up at the bottom of a read channel opens the next channel with unread posts (as the official app does) | Do | P2 | A |
| Online dot on avatars in groups | The sender's avatar shows the online dot | Do | P3 | A |
| Pull down to open the archive | Overscroll at the top of the chat list opens the archive | Do | P3 | A; fits with A's queued archive item |
| Confirm before sending | Ask before sending a sticker, GIF or voice message (protects against mis-taps) | Do | P3 | A + C |
| Send photos in high quality by default | Skip compression (send as the original size) unless changed per send | Do | P2 | A; `AttachmentPreparer` already has the sizes |
| Pinned chats in the forward sheet | Choose chats that always show first in the forward and share sheets, Saved Messages first | Do | P2 | A (`ChatTargets`) + C (setting) |
| Sticker size | A slider for the size stickers show at in the conversation, and hiding the time on them | Do | P3 | C (Chat appearance) + A (bubble) |
| Choose the chat list's swipe actions | Pick which action each swipe direction does (archive, read, pin, mute, delete, change folder) | Do | P2 | A + C (setting) |
| Folder tabs: unread counter options | Hide the counter; count messages instead of chats; include muted chats | Do | P3 | A; TDLib's `updateUnreadChatCount` / `updateUnreadMessageCount` already give both counts |
| Hide stories | No story rings in the chat list; stories stay on profiles | Do | P2 | A + C (setting) |
| Rounded member counts | "2K members" instead of "2,013 members" in headers | Do | P3 | A, `core:ui` formatter |
| Show the user or chat ID and data center | A row on profiles and info pages, off by default, tap to copy | Do | P3 | C |
| Round video messages | Recording video notes | Owner | P2 | A; queued as A item 9 with an ADR, as it is a new use of the camera |

### Media and voice

| Feature | What it does | Verdict | Priority | Where |
|---|---|---|---|---|
| Raise to listen | Raising the phone plays voice messages through the earpiece (proximity sensor) | Do | P1 | A; the proximity wake lock exists in `CallService` |
| Unmute videos with volume down | The volume keys unmute an inline or viewer video | Do | P3 | A + `feature:media` |
| Tap the screen edges to change media | In the viewer, a tap near the side goes to the previous or next item | Do | P3 | `feature:media` |
| Swipe up to picture-in-picture | Videos in the viewer go on in a picture-in-picture window | Do | P2 | `feature:media`; calls already use PiP |
| Pause music while recording or playing | Ask for audio focus so other apps' music pauses | Do | P2 | A; check `ExoAudioPlayer` and `MediaVoiceRecorder` |
| Microphone for voice messages | Built-in or a Bluetooth headset | Do | P3 | A + C |
| Stream videos and music | Play while downloading, without waiting for the whole file | Do | P1 | `feature:media` + A; first check what the player does today |
| Save to gallery by chat type | Media from private chats, groups or channels saved to the gallery automatically | Do | P3 | C (setting) + `core:data` (`SharedStorageRepository`); no new permission from Android 10 |
| Motion photos | Send a motion photo as an animated photo | No (for now) | — | Needs parsing the file; the Photo Picker gives the still. Revisit later |

### Privacy and security

| Feature | What it does | Verdict | Priority | Where |
|---|---|---|---|---|
| Locked chats | Hide chosen chats behind the passcode; they open only after unlocking | Owner | P1 | C + A; extends `docs/adr/0005-passcode-lock.md` (where they show, what notifications say) |
| Lock the archive, secret chats, call history and settings | The same lock for whole areas | Owner | P2 | C; same ADR |
| Allow the device PIN | The lock accepts the device's PIN or pattern as well as biometrics (`DEVICE_CREDENTIAL`) | Do | P2 | C (`BiometricPrompt` allows it from Android 11) |
| Hide phone numbers on screen | One switch hides the user's and others' numbers everywhere (for screen sharing and streaming) | Do | P2 | C + every screen showing numbers, through a `core:ui` helper |
| Websites logged in with Telegram | List and log out of sites (`getConnectedWebsites`, `disconnectWebsite`, `disconnectAllWebsites`) | Do | P2 | C |
| Suggest frequent contacts | Turn off the frequent contacts search shows (`setOption("disable_top_chats")`), clearing them | Do | P3 | C |
| Link previews in secret chats | Off by default, with a note that Telegram's servers make the preview | Do | P2 | C + A (composer); a privacy setting secret chats need |
| More privacy settings | Bio, Birthday, Gifts, Saved Music, Messages, read time | Queued (C7) | — | Pingogram has phone number, last seen, photo, forwards, calls, invites, voice messages |
| Delete account, "If away for" | | Queued (C1) | — | |
| Default auto-delete timer, login email | | Queued (C6) | — | |

### Notifications

| Feature | What it does | Verdict | Priority | Where |
|---|---|---|---|---|
| Reactions | Notify about reactions to your messages and stories (`setReactionNotificationSettings`) | Do | P1 | C (`TdlibNotificationSettingsRepository`) |
| Stories | Notifications for stories, on or off with exceptions (story notification scope) | Do | P3 | C |
| Contact joined Telegram | Turn off "X joined Telegram" (`setOption("disable_contact_registered_notifications")`) | Do | P2 | C |
| In-app banner | A banner for messages of other chats while the app is open, instead of a system notification | Do | P3 | `core:notifications` + `:app` |
| Badge counter options | Include muted chats, count messages or chats | Do | P3 | C + `core:notifications`; Android's launcher badges follow the notifications, so only what Android allows |
| Repeat notifications | Remind about unread messages after an interval | No | — | Needs a periodic job; `docs/PERFORMANCE.md` §6 allows none under 15 minutes and asks for none without need |
| Call ringtone and vibration | | Have | — | Android's channel settings ("Sound and vibration") |
| Keep-alive service, background connection | | Have | — | `foss`'s "Keep connected" (`docs/adr/0003`) |

### Data, storage and network

| Feature | What it does | Verdict | Priority | Where |
|---|---|---|---|---|
| Power saving | Follows Android's Battery Saver (or "below N%"), turning off animated stickers and emoji, autoplaying videos and GIFs, and call animations | Do | P2 | C (setting) + `core:ui` (`AnimatedStickers`, `CustomEmojiRenderer`) + A |
| Reset auto-download settings | One row that puts the defaults back | Do | P3 | C |
| Delete all cloud drafts | `clearAllDraftMessages`, behind an `AlertDialog` | Do | P3 | C |
| Prefer IPv6 | TDLib's `prefer_ipv6` option | Do | P3 | C, beside the proxies |
| Proxies, data usage | | Queued (C8) | — | |
| Use less data for calls | | Do | P3 | B (engine bitrate), setting in C |
| Noise suppression in calls | | Do | P3 | B, if NTgCalls exposes it |
| Upload and download boost | More parallel connections than Telegram gives free accounts | No | — | Circumvents Telegram's limits (`CLAUDE.md` rule 3) |

### Account and profile

| Feature | What it does | Verdict | Priority | Where |
|---|---|---|---|---|
| Personal channel | Show one of your channels on your profile (`setPersonalChat`) | Do | P2 | C (Edit profile) |
| Name and profile color | Pick the accent color of your name and profile (`setAccentColor`, `setProfileAccentColor`; Premium colors shown as information only) | Owner | P3 | C; needs a mapping of Telegram's color ids to tonal roles (DESIGN_SYSTEM §2.2 avoids TDLib's accent colors today) |
| Birthday, change number | | Queued (C6) | — | |
| Add account | Several accounts | Owner | P1 | Its own ADR and session (one TDLib client per account) |
| Chat automation | A bot that answers on your behalf (Telegram Business) | No (for now) | — | Business features are in the backlog (ROADMAP) |

### App

| Feature | What it does | Verdict | Priority | Where |
|---|---|---|---|---|
| Alternative app icons | Pick another launcher icon (activity aliases) | Owner | P3 | `:app`; Pingogram's own artwork only (`CLAUDE.md` rule 9), themed icon kept |
| Direct share switch | Turn off Pingogram's chats in Android's share sheet | Do | P3 | C + `core:notifications`' `ShareTargetShortcuts` |
| Distance units | Metres or feet for locations | Do | P3 | A, following the locale by default |
| Show 18+ content | Telegram's `ignore_sensitive_content_restrictions`, only where TDLib's `can_ignore_sensitive_content_restrictions` allows | Owner | P3 | C; Telegram's terms and the stores' rules apply |
| Announce upload and download progress | Screen readers hear the percentage | Do | P2 | Part of the accessibility audit (A) |

## What Pingogram leaves out on purpose

These are styling and customization switches that contradict Pingogram's rules. Pingogram follows Material 3
Expressive as one design (`docs/DESIGN_SYSTEM.md`), not a set of looks.

- **Choosing UI styles:** switch, checkbox, slider and chat-action styles; icon packs; a centred title; squared
  buttons; hiding dividers; glass and blur effects; the "gooey" profile; snowflakes; the "pacman" archive animation;
  more haptics. These conflict with DESIGN_SYSTEM §§4–6 and §11.
- **The system font and emoji sets:** Google Sans Flex is the only typeface (`CLAUDE.md` rule 4b), and no emoji fonts
  are bundled (`docs/PERFORMANCE.md` §8).
- **AI features through Google, OpenAI or OpenRouter keys:** they send message text to third parties.
  `REQUIREMENTS.md` §6.2 allows network only to Telegram and links the user opens. Translation already goes through
  Telegram. Telegram's own speech recognition (`recognizeSpeech`, for Premium users) would be allowed, and fits FR-C4
  voice messages: Do, P3, A.
- **Identifying the device as another model:** the client must identify itself honestly (`CLAUDE.md` rule 3).
- **Guessing a user's registration date:** it is shown as fact but is a guess.
- **An in-app browser:** links open in the user's browser, which Pingogram never replaces. A Custom Tab, which is the
  browser itself, could be offered later (P3).
- **Customizing the navigation bar, the chat list menu and "rapid actions"; three-line chat rows:** the floating
  navigation bar and the chat row's density are fixed design decisions (DESIGN_SYSTEM §6.1 and §7, owner 2026-10-19).
- **Shake gestures; forcing the maximum refresh rate; the performance class; max recent stickers:** Android and
  TDLib decide these, and shaking is no Material interaction.
- **Hiding the send-as button, custom emoji, or channel bottom bar buttons:** they only show where they act.

## Suggested order

**P1:**
- double-tap to react
- send with Enter
- raise to listen
- streaming videos
- reaction notifications
- locked chats (owner)
- multiple accounts (owner, own session)

**P2:**
- power saving
- picture-in-picture videos
- hiding stories
- choosing swipe actions
- the forward sheet's pinned chats
- high-quality photos
- the share button
- the next channel
- hiding phone numbers
- the device PIN
- connected websites
- secret chat link previews
- contact-joined notifications
- personal channel
- audio focus

**P3:** the rest. Items that touch a design rule get their DESIGN_SYSTEM §6 entry in the same pull request.
