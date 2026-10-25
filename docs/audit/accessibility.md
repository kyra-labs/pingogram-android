# Accessibility audit

Audit date: 2026-10-22 · M6 ("accessibility audit on every screen", `docs/ROADMAP.md`) · against `REQUIREMENTS.md` §6.3,
`docs/DESIGN_SYSTEM.md` §9 and the per-screen checklist of `REQUIREMENTS.md` §7.

The audit has three parts:

- automated checks, which now run in `./gradlew check`;
- a reading of the code of every screen;
- the checks only a device can do, left to the owner (§4).

Findings have IDs:

- `F…`: settings, sign-in, profiles, group and channel info, contacts, the app shell, the widget, `core:ui`, notifications. Fixed in the pull request that adds this file.
- `A…`: the chat screens (`feature:chat`, `feature:chats`, `feature:media`), which session A owns. Listed here for it.

Calls (`feature:calls`, session C) were not audited here.

## 1. Automated checks

| Check | Where | What it asserts |
|---|---|---|
| `ThemeContrastTest` | `core:ui` | Every color theme (13 palettes) in light, dark and AMOLED black, at standard, medium and high contrast: text on the containers the app puts it on keeps 4.5:1, and outlines and icons that show a control keep 3:1. The wallpaper's colors are the platform's and are not checked. |
| `assertAccessible()` | `core:testing` | Every control (click, toggle or adjust action) wholly on screen, in every window (dialogs and sheets too), has a name screen readers read, and a touch target of at least 48 dp (sliders 44 dp, Material's slider height, whose touches Compose's hit testing widens to 48 dp). Links in text are skipped: screen readers read them with their text. |
| `SettingsAccessibilityChecksTest` | `feature:settings` | 13 settings pages with content |
| `ProfileAccessibilityChecksTest` | `feature:profile` | Profile, group info, members, member rights, permissions, editing, invite links |
| `ContactsAccessibilityChecksTest` | `feature:contacts` | The Contacts tab, Add contact ("not on Telegram" too), Edit contact |
| `AuthAccessibilityChecksTest` | `feature:auth` | Welcome, phone number (with its error), country picker, code, password, registration, QR login, email code |
| `AppShellAccessibilityTest` | `:app` | The floating navigation bar and the chat list's chrome, at 100% and 200% font scale |

The checks are ours, not Google's Accessibility Test Framework: `ui-test-junit4-accessibility` turns ATF off under
Robolectric (it looks for Robolectric's fingerprint), so it cannot run in JVM tests. Accessibility Scanner on a device
covers what these checks do not (§4).

Existing tests already covered:

- roles: switches, radio groups, tabs;
- live regions: countdowns, connection;
- custom actions on rows;
- 200% font goldens of most screens;
- right-to-left goldens;
- reduced motion.

## 2. Findings in this area (fixed)

| # | Where | Finding | Rule | Fix |
|---|---|---|---|---|
| F1 | `PingogramNavigationBar` | The floating bar's tabs took touches over 40 dp of the pill's 56 dp height | 48 dp touch targets | A tab takes touches over the pill's whole height. Its indicator and ripple are drawn inset as before. |
| F2 | `ProfileTopBar` (`ProfileSubtitle`) | At 200% font, a long status pushed the "Premium", "Scam" and "Fake" labels out of the row | 200% font without loss | The status ends in an ellipsis and the labels keep their width |
| F3 | `ContactRow` | The row's description joined its parts with a hard-coded `", "` | `docs/TRANSLATING.md` | `spokenList` |
| F4 | `ChatBoostScreen` | The level's description: `"$level, $boosts. $next"` | `docs/TRANSLATING.md` | `spokenList` |
| F5 | `ProfileTopBar`, `ChatInviteSheet` | The verified title, `"$name, $verified"`. On the invitation, the description also dropped the "Scam" / "Fake" label of the same row | `spokenList`; merged semantics | `spokenList`, with the warning last |
| F6 | `ChatInviteSheet` | "Members include …" joined the names with `joinToString()` | `docs/TRANSLATING.md` | `spokenList` |
| F7 | `CountryPickerSheet` | The calling code read "44+" in right-to-left layouts | `numericContent` (§9) | `labelLarge.numericContent` |
| F8 | `ContactFormParts` (`ConnectedChoices`) | "Phone number" / "Username" were cut at 200% font | 200% font | Labels wrap to two lines, centered |
| F9 | Auth fields, 2-step verification fields, `EditChatInfoScreen` | An error after submitting showed under its field without being announced, while focus is on the button | Live region for changing status | The error is a polite live region |
| F10 | Profile username, phone and link rows, Active sessions, shared media cells | TalkBack said only "double-tap to activate" for rows that copy, call, terminate or open | Click labels | "Copy", "Call", "Terminate", "Open" as click labels |
| F11 | Widget rows | Mentions and the muted state were not spoken, and muted showed by the badge's color alone | Not by color alone | Both are spoken, and a muted chat shows the muted symbol |
| F12 | Widget badge | A fixed 20 dp height around sp text | No fixed heights on text | Vertical padding; it grows with the text |
| F13 | `ChatPermissionsScreen` (slow mode) | `onValueChangeFinished` read the step of the last composition, so TalkBack's adjust action could save the previous delay | Accessible adjust actions | It reads the slider's state |
| F14 | `LanguageScreen` | Each language's own name ("العربية") took the layout's direction and the UI language's voice | Text direction (§9) | Marked with its locale (`SpanStyle.localeList`) in `userContent` |
| F15 | Settings and info page titles, contact form and Contacts titles, the phone number of an invitation row | `maxLines = 1` without an ellipsis cut text at 200% font | No truncation | `TextOverflow.Ellipsis` |

Already good, from the same reading:

- **Labels:** every icon button has a label; decorative icons and avatars are hidden.
- **Rows:** rows merge into one item with custom actions for their menus.
- **Roles:** switch rows are `Role.Switch` with their state, radio groups use `selectableGroup`, tabs use `Role.Tab`.
- **Headings and live regions:** page and section titles are headings. Live regions cover:
  - the connection pill;
  - the flood-wait and resend countdowns (every 15 s at most);
  - the passcode entry;
  - contact sync.
- **Sliders and the fast-scroll index:** they state their values.
- **Text direction:** user content takes its own direction.
- **Reduced motion:** stops the empty state's turn, the morphs and the stickers.
- **Keyboard:** the PIN pad takes digits, Backspace and Enter.

## 3. Findings on the chat screens (for session A)

These were found by reading the code. Each was checked against the code around it.

The chat screens already do well on several points:

- icon-only buttons are labelled;
- bubbles and chat rows merge their semantics, with custom actions for swipe and long press;
- no fixed heights on text;
- no hard-coded English in spoken strings;
- animations follow reduced motion.

A1, A3 and A4 are done (the keyboard and mouse pull request), and A5–A15 (the chat screens' accessibility pull request): their rows say what was done. A2 is done too (the send-keys setting).

| # | Where | Finding | Rule | Suggested fix |
|---|---|---|---|---|
| A1 | `feature/chat/…/ui/MessageBubble.kt` (`bubbleGestures`, `LongPress.kt`) | A message's actions open by touch or TalkBack only: no keyboard focus, no key, no right-click | §9 keyboard & mouse | **Done.** The row is focusable where the keyboard moves the focus (`bubbleKeys`), with the theme's focus ring in the bubble's shape. Enter, Shift+F10, the Menu key and a right click open the floating menu, which takes the focus and closes on Esc, giving the focus back. Tests: `MessageKeysTest` |
| A2 | `feature/chat/…/ui/composer/ComposerField.kt` (`fieldKey`) | Fixed (2026-10-24): which Enter sends is a setting for a hardware keyboard (Ctrl+Enter sends where Enter does not) and for the on-screen keyboard (`SendKeys`) | §9 keyboard | Done; where the rows live is the owner's to confirm (§5) |
| A3 | `feature/media/…/ui/MediaViewerScreen.kt`, `MediaPage.kt` | No keys: arrows do not turn pages, +/− do not zoom, Esc does not close | §9 keyboard & mouse | **Done.** Left/Right (mirrored in RTL), +/= and −, 0 to fit, Ctrl and the wheel, Space for a video, Esc (`MediaViewerKeys.kt`). The stories viewer has Left/Right, Space and Esc (`StoryViewerKeys.kt`). Tests: `MediaViewerKeysTest`, `StoryViewerKeysTest` |
| A4 | `MessageBubble.kt` (sender name), `BubbleFrames.kt` (sender avatar) | `clickable(indication = null)`: no focus or hover state, no role or click label | §9 focus, hover | **Done.** `openProfileTarget`: a button with Material's pressed, hover and focus states clipped to the name or the circle, `Role.Button` and "Open profile" as its click label, on the same 48 dp target. Tests: `MessageKeysTest` |
| A5 | `feature/chat/…/ui/BubbleDescription.kt` | The bubble's description and its reactions are joined with `", "` | `docs/TRANSLATING.md` | **Done.** `spokenList` (`core:ui`): the language's list separator, blank parts left out, for the description and its reactions. Tests: `ChatScreenTest` |
| A6 | `feature/chats/…/ui/ChatListItemDescription.kt`, `ArchiveRow.kt`, `SearchRows.kt` | The same for chat rows, the archive row and search results (and `" "` between sender and text) | `docs/TRANSLATING.md` | **Done.** `spokenList` for chat rows, the archive row and search results; sender and text through `chat_list_a11y_sender_text`. Tests: `ChatListScreenTest`, `SearchScreenTest` |
| A7 | `JumpToBottomButton.kt`, `bot/CommandRows.kt`, `topics/TopicRow.kt`, `location/ShareLocationSheet.kt` (`". "`) | The same | `docs/TRANSLATING.md` | **Done.** `spokenList` for the jump button, command rows and topic rows; the location's two sentences through `location_found_description`. Tests: `ShareLocationSheetTest` |
| A8 | `feature/chats/…/ui/create/CreateChatParts.kt` | The names of people not added are joined with `", "` on screen | `docs/TRANSLATING.md` | **Done.** `ListFormatter` in the screen's language ("Ada, Grace, and Alan"), each name isolated |
| A9 | `BubbleParts.kt` (reaction badges), `ReactionPicker.kt` (`MessageReactionChip`) | The user's own reaction differs only by its container color | §6.3 not by color alone | **Done** (owner, 2026-10-23). A badge's ring turns from `surface` to `primary` as it turns chosen, blended in the draw phase, at the same width; the picker's chips get a `primary` outline along the carousel's mask (`maskBorder`). Goldens: the reactions' states in `ChatScreenScreenshotTest`, `MessageActionsScreenshotTest` |
| A10 | `voice/RecordingBar.kt` | Recording and paused differ only by color and motion, and screen readers hear no state | §6.3 | **Done.** The dot is a polite live region, "Recording" or "Paused"; on screen Pause turning into Resume says it without color. Tests: `VoiceRecordingTest` |
| A11 | `ChatListTopBar.kt` (`ConnectionPill`); the conversation and the media viewer | Screen readers hear "Connecting…" but not that the connection is back. The conversation and the viewer show no connection state | §9 live region for the connection | **Done** (owner, 2026-10-23). The chat list: a polite live region behind its top bar holds "Connected" for 3 s once the connection is back after the pill showed a state, nothing at a start that finds it connected (`ConnectedAnnouncement`, `ConnectedAnnouncementTest`). The conversation: "Waiting for network…", "Connecting…" or "Updating…" in place of the status, the title a polite live region meanwhile (`ChatConnectionViewModel`, `ChatTopBarConnectionTest`, goldens `connectingLight` / `connectingDark`). The media viewer stays as it is |
| A12 | `feature/media/…/ui/MediaChrome.kt` (caption) | An expandable caption without a role or an expanded / collapsed state | §9 roles on custom toggles | **Done.** `Role.Button` and the `expand` / `collapse` action of its state. Tests: `ExpandableCaptionTest` |
| A13 | `feature/chats/…/ui/SearchPeople.kt` (`Person`) | A fixed-width column with one line of name: names are cut at 200% font | §6.3 font scale | **Done.** Two lines of name from 150% font (`isLargeFontScale`). Tests: `TopPeopleTest` |
| A14 | `FolderTabs.kt`, `panel/EmojiPanel.kt`, `PanelSections.kt` | Toggle buttons with `Role.Tab` may announce "checked" on a tab | §9 | **Done** for the folder tabs: each toggle button sits in a node with `clearAndSetSemantics { role = Tab; selected; onClick }`, the layout unchanged (`FolderTabsTest`). The emoji panel's tabs belong to the panel's owner and are done too (2026-10-23): `panelTab`, `PanelTabSemanticsTest` |
| A15 | `attach/MediaPreviewItem.kt` | The remove button is `extraSmallContainerSize()`. Its touch target needs checking on a device | §6.3 48 dp | **Done**, no change needed: the icon button widens its 32 dp container's touch target to 48 dp itself (`minimumInteractiveComponentSize` inside `FilledIconButton`), and `assertAccessible` counts that. Tests: `AttachPreviewAccessibilityTest` |

`assertAccessible()` (`core:testing`) is ready for the chat screens' tests as well.

## 4. What needs a device (owner)

1. **TalkBack**, through one journey:
   - sign in;
   - the chat list, its tabs and a swipe action;
   - Settings → Appearance and Chat appearance (the sliders' values);
   - Language: a right-to-left language's name should be read in its own voice;
   - a profile: tap the username (copy) and the phone number (call), long-press the phone number;
   - Contacts: a row's actions;
   - the widget: a muted chat should be read as "muted".
2. **Accessibility Scanner** on the screens of §1, in light and dark. Its contrast check also covers the wallpaper's
   (dynamic) colors, which `ThemeContrastTest` cannot.
3. **Font size and display size at their largest** (Android's settings): Settings, a profile with a long "last seen"
   status, Add contact, the widget.
4. **Switch Access** or a keyboard on a tablet: Tab and Enter through Settings and sign-in, and through the chat
   list, the conversation and the media viewer (`docs/DESIGN_SYSTEM.md` §9 "Keyboard & mouse" lists what should
   work).

## 5. Decisions for the owner

- **Ctrl+Enter to send (A2):** done (2026-10-24). Appearance → Chats → "Send with Enter on a keyboard" (on by default:
  Enter sends, Shift+Enter and Ctrl+Enter start a new line; off: Ctrl+Enter sends, Enter starts a new line) and "Send
  with Enter on the screen keyboard" (off by default: on, the keyboard's action key reads "Send"), which the composer's
  key handling follows (`ComposerField`, `LocalSendKeys`). "Send with Ctrl+Enter", saved before, carries over. Where
  the rows live is still the owner's to confirm.
