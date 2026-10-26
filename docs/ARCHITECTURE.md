# Architecture

## Overview

```
┌────────────────────────────────────────────────────────────────┐
│  :app  — Application, MainActivity, NavHost, DI graph, theme   │
├────────────────────────────────────────────────────────────────┤
│  :feature:*  — one module per user-facing area                 │
│   auth · chats · chat · contacts · profile · settings · media   │
│   · scanner (the QR code scanner, CameraX) · calls (NTgCalls)  │
│   · chat records round video messages with CameraX too         │
│   · feedback (the report testers share themselves)             │
│   Each: Screen composables + ViewModel + UiState + navigation  │
├────────────────────────────────────────────────────────────────┤
│  :core:ui        — PingogramTheme (M3 Expressive), shared       │
│                    components, previews, icons                 │
│  :core:data      — Repositories (chats, messages, users,       │
│                    files, storage), mappers TdApi → domain     │
│  :core:domain    — plain Kotlin models + use cases, no Android │
│  :core:tdlib     — TdlibClient (coroutine wrapper), TDLib AAR, │
│                    lifecycle, auth state machine               │
│  :core:common    — Result/Error types, dispatchers, utils      │
│  :core:datastore — app settings (Preferences DataStore)        │
│  :core:notifications — channels, MessagingStyle builder        │
│  :core:testing   — fakes, test rules, fixtures                 │
├────────────────────────────────────────────────────────────────┤
│  build-logic/   — Gradle convention plugins                    │
│  benchmark/     — Macrobenchmark + Baseline Profile generator  │
└────────────────────────────────────────────────────────────────┘
```

Dependency direction is strictly downward: `app → feature → core`, `feature ↛ feature`
(features talk only via navigation routes defined in `:core:navigation` — a tiny module holding
`@Serializable` route objects). `core:domain` depends on nothing Android.

## Layers and data flow

```
Composable ──events──▶ ViewModel ──▶ UseCase / Repository ──▶ TdlibClient ──▶ TDLib (JNI)
    ▲                     │                     ▲                   │
    └──── UiState (StateFlow) ◀─────────────────┴──── Flow<Update> ◀┘
```

- **TdlibClient** (`core:tdlib`, an interface; `DefaultTdlibClient` in production,
  `FakeTdlibClient` in tests) owns exactly one `org.drinkless.tdlib.Client` per logged-in
  account. It exposes (details in `docs/TDLIB.md`):
  - `suspend fun <T : TdApi.Object> send(function: TdApi.Function<T>): TdResult<T>` — wraps
    `Client.send` in `suspendCancellableCoroutine`; maps `TdApi.Error` to `TdError(code, message)`.
  - `val updates: SharedFlow<TdApi.Update>` — hot, no replay, buffered
    (`extraBufferCapacity = 4096`, `SUSPEND`, never dropping), collected by repositories on
    `Dispatchers.Default`.
  - `val authState: StateFlow<AuthState>` — typed mirror of `updateAuthorizationState`.
  - `val connectionState: StateFlow<ConnectionState>`.
  - `fun start()` — called once by `PingogramApplication`; creates the client in the background.
- **Repositories** (`core:data`) keep in-memory caches keyed by TDLib ids (chat, user, message)
  as `MutableStateFlow<Map<Long, Chat>>` and apply updates incrementally. They never expose
  `TdApi` types upward; they map to domain models (`core:domain`). TDLib already persists
  everything in its SQLite DB, so **we do not add Room**.
  TDLib introduces each user and chat only once and `updates` replays nothing, so caches subscribe
  at app start: a caching repository implements `TdlibUpdateCache`, keeps its state in an
  `UpdateStore` (subscribed before its constructor returns, one immutable snapshot reduced from
  every update, reset on `AuthorizationStateClosed`) and joins the `Set<TdlibUpdateCache>`
  (`@Binds @IntoSet` in `UpdateCacheModule`) that `PingogramApplication` makes before
  `TdlibClient.start()`. Every new caching repository must join that set.
- **Accounts** (`docs/adr/0031-multiple-accounts.md`): the repositories that talk to TDLib are an account's, not the
  app's. They are `@AccountScoped` and live in `core:data`'s `AccountComponent`, a Dagger component of its own per
  account (its `AccountSlot` and `TdlibClient` given when it is made, the app's parts through `AccountDependencies`,
  which `:app`'s `AccountsModule` implements as a Hilt entry point). `Accounts` holds them and the active one;
  `ActiveAccountModule` binds each repository interface in Hilt's graph to the active account's, so view models,
  workers and services inject the interfaces as before. What belongs to the device (the network, Battery Saver, the
  phone's contacts and location, shared storage, the emoji catalog, picked photos, diagnostics) stays `@Singleton`
  (`DeviceModule`). A new account repository is `@AccountScoped`, bound in `RepositoryModule` and listed in
  `AccountRepositories` and `ActiveAccountModule` (`AccountBindingsTest` checks the three agree); what it needs from
  the app goes into `AccountDependencies`. `Accounts` reads which accounts there are and the shown one from a small
  file (`AccountRegistryFile`, `noBackupFilesDir/accounts`) on the IO dispatcher as the app starts, runs every
  account's client (the first's is the app's `TdlibClient`, the others' from `core:tdlib`'s `TdlibClientFactory`),
  each graph in a scope of its own that ends with it, and removes an account that logs out, or that signs in as a
  user already on the device, while another stays (its client then stays closed and `AccountStorage` deletes its
  slot). Switching saves the shown slot; `MainActivity` then opens the app again in a new task
  (`AccountRestart`), so no screen keeps the other account's repositories. `core:domain`'s
  `DeviceAccountsRepository` (`DefaultDeviceAccountsRepository`) lists the signed-in accounts with their unread chats
  and adds, switches and leaves an account's sign-in; it is not offered on screen (`isAvailable` false) until the
  background parts follow every account. The passcode lock is the app's (`DefaultAppLockRepository`, `@Singleton`):
  it follows the accounts' login steps together, and forgetting the passcode logs every account out.
- **Test servers** (FR-A9, `docs/TDLIB.md` "Test servers"): `TdlibClient.servers` / `useServers` restart TDLib on
  Telegram's test servers or back. Only `TdlibAuthRepository` asks for a switch: `setPhoneNumber` by the number
  (`core:domain`'s `telegramServersFor`), `requestQrCode` for the production servers; any other close returns to them.
  `AuthRepository.servers` (`TelegramServers`) tells `feature:settings` (the account card, Active sessions) to show
  "Test server", and `AuthViewModel` keeps the phone number step, with what was typed, through the restart's steps
  while it sends the number.
- **Connection** (FR-L5, `docs/audit/accessibility.md` A11): `core:domain`'s `ConnectionRepository`
  (`TdlibConnectionRepository`: `TdlibClient.connectionState` as `ConnectionStatus`) feeds the chat list's pill
  (`ChatListViewModel`) and the conversation's top bar, through `feature:chat`'s `ChatConnectionViewModel`, a view model
  of its own beside `ChatViewModel` (the connection is the app's, not the chat's, and only the top bar reads it), which
  `ConversationRoute` hands to `ChatScreen` as `connection`.
- **Settings** (`core:datastore`) are the app's own, kept on the device and kept across logouts: one
  Preferences DataStore file (`filesDir/datastore/settings.preferences_pb`, `DataStoreModule`, one
  instance per process, IO dispatcher) behind the settings repositories of `core:domain`, e.g.
  `DataStoreAppearanceRepository` for `AppearanceRepository` (and `DataStoreComposerSettingsRepository` for
  `ComposerSettingsRepository`, which Enter sends the composer's message (`SendKeys`), set in Appearance → Chats and
  handed to the conversation's fields by `feature:chat`'s `SendKeysViewModel` through `LocalSendKeys`;
  `DataStoreDoubleTapSettingsRepository` for `DoubleTapSettingsRepository`, on which messages a double tap adds the
  quick reaction, which is the account's: `QuickReactionRepository`, `TdlibQuickReactionRepository` in `core:data`, a
  `TdlibUpdateCache` of `updateDefaultReactionType`, set with `setDefaultReactionType`; both chosen in Appearance →
  Chats → Double-tap to react (`QuickReactionRoute`, `QuickReactionViewModel`) and handed to the conversation's
  bubbles by `feature:chat`'s `DoubleTapViewModel` through `LocalDoubleTapReaction`, the reaction itself going through
  `ChatEvent.ToggleReaction` as a tapped badge's does). Preferences rather than Proto
  DataStore: Proto needs the protobuf Gradle plugin and `protobuf-javalite`, which the project does
  not use, for a handful of typed keys. Enums are stored by name; unreadable or unknown values read
  as the defaults, and a write that fails reaches the ViewModel as an `Outcome.Failure`.
- **Language** (FR-S1, §6.4): the languages Pingogram ships are English and every language its translations cover at
  80% (`docs/TRANSLATING.md`, `docs/adr/0020-translations-on-weblate.md`). `build-logic`'s `pingogram.android.locales`
  works them out from all modules' `strings.xml` and generates `:app`'s `xml/locales_config.xml` from them, which
  Android 13+ lists in its app language settings (`android:localeConfig`) and Settings → Language lists on every
  version, and the build packages resources for them only (`androidResources.localeFilters`, so libraries add no other
  languages), plus the pseudo-locales `en-XA` and `ar-XB` that debug builds generate. `core:domain`'s `AppLanguageRepository` is bound in
  `:app` (`PlatformAppLanguageRepository`): from Android 13 it is Android's per-app language (`LocaleManager`), which
  Android applies by creating the activity again, read anew at each collection since Android's settings change it too.
  Before Android 13 the choice is kept in DataStore (`core:datastore`'s `DataStoreLanguagePreference`) and
  `MainActivity` applies it itself, without AppCompat: `attachBaseContext` gives it a configuration context in the
  language (`contextInLanguage`, which sets the process's default locale too) and it creates itself again when the
  language read later or chosen in Settings differs, composing nothing until they match; reading it is never blocking,
  so the first activity of a process with a chosen language is created twice, behind the splash screen. Notifications
  and other text drawn outside the activity keep the system's language there. Telegram's own language packs
  (`language_pack_id`) are not used.
- **Theme**: `MainActivityViewModel.appearance` (null until the settings are read) feeds
  `PingogramTheme` in `MainActivity`, which also styles the system bar icons for the theme it
  resolves to. The splash screen waits for the settings as for TDLib's first login step (at most
  300 ms, `docs/PERFORMANCE.md` §2), and nothing is composed before they are known, so the app never
  draws in a theme the user did not choose. A change made in Appearance reaches the theme through the
  same flow and cross-fades its colors (`docs/DESIGN_SYSTEM.md` §1). The colors are the wallpaper's or a color theme's
  (`AppearanceSettings.palette`, `core:domain`'s `ThemePalette`, `docs/adr/0016-color-themes.md`), whose schemes
  `tools/theme` generates into `core:ui` (`themeColorScheme`) and whose splash screens into `:app`
  (`SplashPaletteThemes.kt`); `feature:settings`' Theme page (`ColorThemeRoute`, `ColorThemeViewModel`) picks one,
  saving it and dynamic color in one write (`AppearanceRepository.setPalette`).
- **Chat appearance** (FR-S1, Chat settings): `AppearanceSettings.chat` (`ChatAppearance`, `core:domain`: message text
  scale 0.85–1.3 in 5% steps, `BubbleCorners` from the shape scale, `ChatWallpaper` from the theme's roles) lives with
  the theme settings in DataStore (`appearance.chat.*`), so `PingogramTheme` gets it with the rest and hands
  conversations a `ChatStyle` (`MaterialTheme.chatStyle`, `core:ui`): the scaled message text with its optical size
  re-derived, the bubble shapes with the chosen corners, and the wallpaper as a `Brush` (null for none), rebuilt as
  the scheme cross-fades. `feature:settings`' Chat appearance page (`ChatAppearanceRoute`, `ChatAppearanceViewModel`)
  sets it, with a preview drawn from that same style. The conversation draws with it too: `feature:chat`'s
  `BubbleTheme` takes its bubble shapes, `BubbleText` its message text (texts and captions; the composer, link previews
  and sponsored messages keep their own styles), and the conversation's sheet paints its wallpaper behind the bars and
  the messages, in the draw phase.
- **Passcode lock** (FR-S1, `docs/adr/0005-passcode-lock.md`): `PasscodeRepository` (`core:domain`,
  `DataStorePasscodeRepository` in `core:datastore`) keeps the settings and the passcode as a salted PBKDF2 hash in the
  settings file; `AppLockRepository` (`DefaultAppLockRepository`, `core:data`) is the lock's state machine over those
  settings and the login steps: locked at every start with a passcode, again after the auto-lock delay out of sight
  (`MainActivity.onStop`/`onStart`, not for configuration changes), never while signed out; it counts wrong attempts
  and makes the next ones wait. `MainActivity` waits for it like for the Appearance settings, then `LockableApp`
  (`:app`) shows `feature:settings`' `AppLockRoute` instead of `PingogramApp`, which leaves the composition while
  locked and keeps its saved state (`rememberSaveableStateHolder`), so nothing of it draws, runs or reaches screen
  readers, and a notification's chat (`chatToOpen`) opens after unlocking. The window is `FLAG_SECURE` while locked;
  "Hide content in recent apps" uses `setRecentsScreenshotEnabled` (Android 13+) or `FLAG_SECURE`. Biometrics go
  through the platform's `BiometricPrompt` (`BiometricAvailability`, `rememberBiometricPrompt`), not `androidx.biometric`;
  where the user turned on "Unlock with screen lock" (`PasscodeSettings.deviceCredentialUnlock`, off by default), the
  prompt takes the phone's PIN, pattern or password too (`DEVICE_CREDENTIAL`, Android 11+, `ScreenLock`).
- **Locked chats** (`docs/adr/0029-locked-chats.md`): Pingogram's own, kept on the device. `core:domain`'s
  `ChatLockRepository` (`DefaultChatLockRepository`, `core:data`, a singleton) holds the locked chats' ids
  (`LockedChatIdsRepository`, `DataStoreLockedChatIdsRepository`: the settings file) and whether they are open: only
  while a passcode is set (none locked otherwise; removing the passcode or logging out unlocks them all for good), opened
  by the app lock's passcode check (`AppLockRepository.checkPasscode`, attempts and waits shared) or biometrics, closed
  again when the app lock locks or after a minute out of sight (`MainActivity.onStop`/`onStart`). `hiddenChatIds` (a
  `StateFlow`, current from the start) is what to hide now. Readers: `ChatListViewModel` (`feature:chats`) leaves locked
  chats out of every list, folder and the archive row, and shows a "Locked chats" row (`LockedChatsRow`) at the end of
  the main list; `SearchViewModel` and `ChatTargets` (the forward and share sheets, `feature:chat`) leave the hidden ones
  out; `ChatRoute` wraps the conversation in `core:ui`'s `LocalLockedChatGate`, which `MainActivity` provides as
  `feature:settings`' `LockedChatGate` (the passcode entry, `ChatUnlockRoute` and `ChatUnlockViewModel`, in the
  conversation's place, its saved state kept). `NotificationRenderer` posts one anonymous "New message" for their
  groups (`ConversationNotificationBuilder.locked`, opening `LockedChatsRoute` through `NotificationIntents`) and
  removes their conversation shortcuts; `ShareTargetShortcuts` and the widget (`RecentChatsWidgetUpdater`) leave them
  out. Settings → Privacy and security → "Locked chats" (`LockedChatsRoute`, `LockedChatsViewModel`) lists them behind
  the passcode, locks more (a picker of the main and archive lists, its picks in saved state) and unlocks; its chats open
  through `core:ui`'s `LocalChatLauncher`, the chat list's row through `LocalLockedChatsLauncher`, both provided by
  `PingogramApp`.
- **Chats shown first** (`docs/audit/feature-gaps-octogram.md` P2): `core:domain`'s `ChatsShownFirstRepository`
  (`DataStoreChatsShownFirstRepository`, the settings file, at most 5 ids in order) lists the chats the forward and
  share sheets show right after Saved Messages. `feature:chat`'s `ChatTargets` takes it with the locked chats as one
  `TargetPreferences` (the hidden chats, the chats shown first), so `ChatForwarding`, `ForwardViewModel`,
  `ShareViewModel` and `ChatViewModel` take one parameter for both; chats shown first go through the same checks as the
  rest (locked chats left out, only chats the sheet may send to), and one TDLib does not know is left out. `feature:settings`' `ChatsShownFirstRoute` (`ChatsShownFirstViewModel`: removing,
  moving with screen readers' "Move up" / "Move down", a picker of the main list limited to the places left, its picks
  in saved state) sits above Appearance, whose Chats group opens it.
- **Chat folders** (FR-L3): `ChatRepository` keeps the folders TDLib announces (`updateChatFolders`: the tabs above
  the chat list and the main list's place among them). `core:domain`'s `ChatFolderRepository` (`TdlibChatFolderRepository`,
  `core:data`, no cache: that update announces every change) reads what a folder holds (`getChatFolder`, as
  `ChatFolderSettings`), creates, edits, deletes (`deleteChatFolder`, its chats kept) and reorders folders
  (`reorderChatFolders`, the main list kept in its place), lists Telegram's suggestions (`getRecommendedChatFolders`),
  and the requests of shared folders: their invite links (`getChatFolderInviteLinks`, `createChatFolderInviteLink`),
  and a shared folder's link (`checkChatFolderInviteLink`, `addChatFolderByInviteLink`). A folder that includes nothing is refused before
  TDLib (`DomainError.ChatFolderEmpty`). `feature:settings`' Chat folders page (`ChatFoldersRoute`,
  `ChatFoldersViewModel`: each folder read again whenever the folders change, a dragged order shown at once and taken
  back when Telegram refuses it) opens the editor (`ChatFolderEditRoute(folderId)`, `ChatFolderEditViewModel`: the
  draft in saved state; what it does not show, the pinned chats, the tag's color and a title's custom emoji, kept
  from the folder as Telegram has it; the picker's chats from the main list, filtered on the device).
  `tg://settings/folders` opens the page. A shared folder's link (`t.me/addlist/…`, `LinkTarget.ChatFolderInvite`)
  opens `ChatFolderInviteRoute(inviteLink)`, a dialog destination like the join sheet (`chatFolderInviteSheet`,
  registered in `:app`'s `NavHost`, `FolderInviteViewModel`), which checks the link (only reading) and adds the folder
  with the chats the user picked, joining them, only on "Add folder"; a folder the user has offers the chats added to
  it since.
- **Sticker sets** (FR-C5, Settings → Stickers): the panel's installed sets are `StickerRepository.installedSets`
  (`getInstalledStickerSets` on `updateInstalledStickerSets`); `core:domain`'s `StickerSetRepository`
  (`TdlibStickerSetRepository`, `core:data`, no cache: TDLib keeps the sets and announces their changes) reads, for a
  `StickerSetKind` (stickers, or custom emoji: td_api.tl's `StickerType`, whose lists Telegram keeps apart), the
  installed sets, the archived sets (`getArchivedStickerSets`, the first 100) and the trending ones (`getTrendingStickerSets`, the first
  page, again on `updateTrendingStickerSets`), both again whenever the installed sets change, and one set with its
  stickers (`getStickerSet`, again on its `updateStickerSet`); it installs, archives and removes sets
  (`changeStickerSet`), reorders the installed ones (`reorderInstalledStickerSets`), marks trending sets seen
  (`viewTrendingStickerSets`) and finds a set by its link's name (`searchStickerSet`). `feature:settings`' Stickers page
  (`StickersRoute`, `StickersViewModel`: a dragged order shown at once and taken back when Telegram refuses it, an
  archived or removed set gone at once with "Undo", which installs it again at its place) opens Trending stickers and
  Archived stickers (`StickerSetListRoute(archived)`, `StickerSetListViewModel`: "Add" per set, the trending sets shown
  marked seen once). The pages draw covers with `core:ui`'s `StickerCover` (still, from Coil, the emoji until then;
  animated TGS ones through the app's `AnimatedStickers` where asked). A set's sheet (`StickerSetRoute(setId, name)`, a
  dialog destination of `settingsPages`, so it is in `:app`'s `NavHost` and the Settings detail pane's alike,
  `StickerSetSheetViewModel`: a link's name found first, the set read again on its changes, "Add stickers" / "Remove"
  only on a tap) opens from a set's row and from `t.me/addstickers/<name>` and `t.me/addemoji/<name>` links
  (`LinkTarget.StickerSet`). The custom emoji sets have the same pages (`StickersRoute(emoji)`,
  `StickerSetListRoute(archived, emoji)`, the view models taking the kind from their route), opened from the Stickers
  page's "Emoji" row; `StickerSetSummary.kind` words a set as stickers or emoji (`StickerKindTexts`).
- **The archive** (FR-L9): `core:domain`'s `ArchiveSettingsRepository` (`TdlibArchiveSettingsRepository`, `core:data`, no
  cache: TDLib sends no update for them) reads and sets the account's `archiveChatListSettings` (new chats from
  non-contacts archived and muted, unmuted chats and folders' chats kept archived) with whether TDLib's option
  `can_archive_and_mute_new_chats_from_unknown_users` lets the account turn on the first, and marks a whole list as read
  (`readChatList`, the folder tabs' "Mark all as read"; here rather than on `ChatRepository`, whose fakes live in every
  feature). How the main list's archive row shows (`ArchiveRowStyle`: full, compact, hidden) is the device's own,
  `ArchiveRowRepository` (`DataStoreArchiveRowRepository`, the settings file), which `ChatListViewModel` combines with
  the archive's chats: a hidden row leaves the archive to the top bar (`ChatListUiState.hiddenArchive`), and hiding it
  offers "Undo". `feature:chats`' `ArchiveSettingsRoute` (`ArchiveSettingsViewModel`, route `ArchiveSettingsRoute` of
  `core:navigation`, `archiveSettingsScreen`) is a settings-style page built from that pattern's parts (features share
  no code), pushed above the archive from its top bar; any feature can navigate to the route. A folder tab's menu
  (`FolderTabMenu.kt`) is the other hook: `ChatListPane(onEditFolder)` adds "Edit folder", which `:app` points at
  `feature:settings`' `ChatFolderEditRoute(folderId)` (`ChatFoldersRoute` for "All chats").
- **Contacts** (FR-P1): `ContactRepository` (`core:domain`, `TdlibContactRepository` in `core:data`) is no
  `TdlibUpdateCache`: TDLib has no update for the contact list, but announces every user, `is_contact` included, with
  `updateUser`, which `TdlibUserRepository` already keeps. So the contacts are `getContacts` once per collection (which
  makes TDLib announce them all), then the users of `UserRepository.users` that are contacts, sorted by name with a
  `Collator` of the user's language; a contact added, renamed or deleted on any device changes the list by itself.
  Search is TDLib's `searchContacts` (transliteration included) next to the view model's own filter; adding goes
  through `importContacts` (a phone number, user id 0 when it is not on Telegram) or `searchPublicChat` + `addContact`
  (a username), editing through `addContact` (keeping the note of `userFullInfo`), deleting through `removeContacts`,
  and an invitation carries `getApplicationDownloadLink` (Telegram's download page offline), shared through Android's
  share sheet from the route, without permissions. `feature:contacts` draws the tab (`ContactsViewModel`: alphabetical
  sections by the language's `Collator`, a fast-scroll index for long lists, the query and the delete question in saved
  state), Add contact (`NewContactRoute`, `NewContactViewModel`) and Edit contact (`EditContactRoute(userId)`,
  `EditContactViewModel`), which `navigateToEditContact(userId)` opens from anywhere, e.g. a profile.
- **Contacts sync** (FR-P1, `docs/adr/0011-contacts-sync.md`, opt-in, off by default, `READ_CONTACTS` its only use):
  `core:domain`'s `ContactSyncRepository` (`TdlibContactSyncRepository`, `core:data`) over `DeviceContactsRepository`
  (`AndroidDeviceContactsRepository`: one `ContactsContract` query of names and numbers on the IO dispatcher, numbers
  made E.164 by the provider or `PhoneNumberUtils`, nothing kept) and `ContactSyncSettingsRepository`
  (`DataStoreContactSyncSettingsRepository`: on or off, the last sync's time, the dismissed offer). A sync sends every
  name and number with `changeImportedContacts`, which TDLib diffs against what it imported last (only changes reach
  Telegram) and answers with a user id per contact (0: not on Telegram); the answer stays in memory. It syncs when the
  user turns it on, on "Sync now", and when the Contacts tab starts while it is on, at most once per process and not
  within 6 h of the last sync (otherwise the phone's numbers are matched with the Telegram contacts on the device,
  sending nothing). No background job, no observer. Turning off sends `clearImportedContacts` (Telegram deletes the
  imported numbers; contacts stay) and clears the settings; a logout (`LoggingOut`/`Closed`, it is a
  `TdlibUpdateCache` for that) clears them too. A permission taken back pauses it (`NeedsPermission`) until granted
  again. `core:ui`'s `ContactSyncController` (held by `ContactsViewModel` and `feature:settings`'
  `ContactSyncSettingViewModel`) and `ContactSyncHost` share the rationale sheet, the permission request (Activity
  Result API), the turn-off question and the snackbars between the Contacts tab and Settings → Privacy and security.
- **Creating groups and channels** (FR-P4): `ChatCreationRepository` (`core:domain`, `TdlibChatCreationRepository` in
  `core:data`, no cache: TDLib announces the new chat with `updateNewChat` before it answers) creates groups with
  `createNewBasicGroupChat`, or `createNewSupergroupChat` when no one or more people than a basic group holds (200,
  `ChatCreationLimits`) are added, then `addChatMembers`, as Telegram's apps do; channels with `createNewSupergroupChat`
  (`is_channel`), made public with `checkChatUsername` + `setSupergroupUsername`, a private one's link from
  `supergroupFullInfo.invite_link` or `replacePrimaryChatInviteLink`. `failed_to_add_members` (privacy) comes back as
  the people not added. A picked photo is prepared by `PickedPhotoRepository` (Files and media) and set with
  `setChatPhoto` in the app's scope once the chat exists, its copy deleted when TDLib answered. `feature:chats` runs
  each flow as a nested graph pushed above New chat (`NewGroupRoute`: members, details; `NewChannelRoute`: details,
  which creates the channel, type, subscribers), whose steps share one view model held by the graph's entry
  (`NewGroupViewModel`, `NewChannelViewModel`, with `MemberPicker` for the contacts), with effects per step; typed text,
  picks, the photo's copy and a created channel survive process death in its saved state. Once the channel exists, back
  opens it. The new chat's `ChatRoute` takes the place of New chat and the flow, so back returns to the chat list.
- **2-step verification** (FR-S1): `TwoStepVerificationRepository` (`core:domain`, `TdlibTwoStepVerificationRepository`
  in `core:data`, no cache: TDLib has no updates for it) reads the state with `getPasswordState`, checks the current
  password with `getRecoveryEmailAddress` and sets, changes, removes and recovers the password and its recovery email.
  `feature:settings` shows the state on `TwoStepVerificationRoute` and runs each change on a `TwoStepVerificationFlowRoute`
  pushed above it (`TwoStepVerificationFlowViewModel`, steps on one page, what was typed never in saved state), which
  leaves its result in the page's saved state (`popBackStackAfterTwoStepVerification`) for a snackbar there.
- **Changing the phone number** (FR-S1, Account): `core:domain`'s `PhoneNumberChangeRepository`
  (`TdlibPhoneNumberChangeRepository`, `core:data`, no cache: TDLib keeps the pending change and announces the new
  number with `updateUser`) sends the code (`sendPhoneNumberCode` with `phoneNumberCodeTypeChange` and the login's code
  settings: no flash or missed calls, no SMS Retriever, no Firebase), asks for it again (`resendPhoneNumberCode`) and
  checks it (`checkPhoneNumberCode`), waiting as long as authentication requests; another account's number fails with
  `DomainError.PhoneNumberOccupied`. `feature:settings`' `ChangePhoneNumberRoute` (`ChangePhoneNumberViewModel`, above
  Edit profile, whose "Phone number" row opens it) types the number as sign-in does, with what both share in `core:ui`'s
  `phone/` (`PhoneNumberForm` and its `with*` changes, `CountryPickerSheet`, `CountryField`, `PhoneNumberFields`, the
  login code texts) and `time/` (countdowns, `CountdownIndicator`); the step and the number typed are in its saved
  state, never the code.
- **Privacy beyond the rules** (FR-S1, Privacy and security): `PrivacyRepository` also reads and sets whether the read
  time shows (`getReadDatePrivacySettings`, `setReadDatePrivacySettings`; the Last seen page's switch) and who may start
  a chat (`getNewChatPrivacySettings`, `setNewChatPrivacySettings`, with the option
  `can_set_new_chat_privacy_settings` as `NewChatPrivacy.canRestrict`; a price another app set for non-contacts'
  messages is kept while they may write and dropped when they may not, as Telegram requires).
  `feature:settings`' `NewChatPrivacyRoute` (`NewChatPrivacyViewModel`) is its page. A setting's exceptions are
  `PrivacyRepository.exceptions` (the users' and groups' ids of its rules, `PrivacyExceptionLists`) and `setExceptions`
  (`setUserPrivacySettingRules` with those rules replaced, the never allowed first, every other rule kept in its
  order); `PrivacyExceptionsRoute(setting, always)` (`PrivacyExceptionsViewModel`) shows one side, names from
  `UserRepository` and `ChatRepository` (asked for once where TDLib has not announced them), and picks from the main
  chat list (people and groups; Saved Messages, channels and secret chats left out), its picks in the saved state.
  Its picker shares its parts with the chat folders' (`PickerParts.kt`).
- **Birthday** (FR-S1, Account): `ProfileRepository.editableProfile` carries the birthday (`userFullInfo.birthdate`,
  its year 0 for none) and `setBirthdate` sets or removes it; `EditProfileViewModel` saves it with the names and the
  bio, and follows who may see it (`PrivacySetting.Birthdate`, `userPrivacySettingShowBirthdate`) through
  `PrivacyRepository`, whose page Privacy and security lists too.
- **Login email and auto-delete** (FR-S1, Account): `core:domain`'s `LoginEmailRepository`
  (`TdlibLoginEmailRepository`, `core:data`, no cache) reads the login email as Telegram's pattern of it
  (`getPasswordState`'s `login_email_address_pattern`; Telegram lets an account change it only once it set one, so
  Privacy and security shows the row only then), sets a new one (`setLoginEmailAddress`), sends its code again
  (`resendLoginEmailAddressCode`) and checks it (`checkLoginEmailAddressCode`). `feature:settings`' `LoginEmailRoute`
  (`LoginEmailViewModel`) runs it as Change number does, sharing its step parts (`account/FlowStepState.kt`:
  `FlowStepState`, `StepText`, `StepActions`); the address typed is in its saved state, never the code.
  `AccountRepository` also reads and sets the self-destruct timer of new chats (`getDefaultMessageAutoDeleteTime`,
  `setDefaultMessageAutoDeleteTime`), which `AutoDeleteRoute` (`AutoDeleteViewModel`) offers as Telegram's choices
  (off, a day, a week, a month, and a value another app set), saving a pick at once and taking it back on a refusal.
- **Proxies** (Data and storage, `docs/adr/0027-proxies.md`): `core:domain`'s `ProxyRepository`
  (`TdlibProxyRepository`, `core:data`, a singleton without a cache) wraps `getProxies`, `addProxy`, `editProxy`,
  `enableProxy`, `disableProxy`, `removeProxy` and `pingProxy`; TDLib keeps the proxies, credentials included, and has
  no update for them, so `proxies` reads them again after every change made through it. `feature:settings`' Proxy page
  (`ProxiesRoute`, `ProxiesViewModel`: each proxy pinged once as it first shows, four at a time, and on "Check again")
  and its form (`EditProxyRoute(proxyId?)`, `EditProxyViewModel`: checked on the device first, the password and the
  secret never in saved state) sit above Data and storage, whose "Proxy" row shows the one in use. A proxy link
  (`tg://proxy`, `t.me/socks`) resolves to `LinkTarget.ProxyServer`, which `:app` opens as `ProxyLinkRoute`, a dialog
  destination of `settingsPages` (`ProxyLinkViewModel`), connecting only on "Connect".
- **Data options** (Data and storage): `core:domain`'s `DataOptionsRepository` (`TdlibDataOptionsRepository`,
  `core:data`, no cache) reads TDLib's option `prefer_ipv6` with `getOption` per collection, follows its `updateOption`
  and sets it with `setOption`, and deletes every cloud draft (`clearAllDraftMessages`, secret chats left out).
  `feature:settings`' `DataOptionsViewModel` holds the switch and the question before deleting (in its saved state),
  beside `DataStorageViewModel`, which also resets the automatic download to `AutoDownloadSettings()`'s defaults.
- **Frequent contacts** (Privacy and security, Contacts): `core:domain`'s `FrequentContactsRepository`
  (`TdlibFrequentContactsRepository`, `core:data`, no cache) is TDLib's option `disable_top_chats`, inverted: read with
  `getOption` per collection, followed through `updateOption`, set with `setOption` (TDLib clears the top chats it
  counted when it is turned off). `core:data`'s `TdlibOptions.kt` (`booleanOption`, `setBooleanOption`) reads and sets
  such boolean options for it and the data options. `feature:settings`' `FrequentContactsViewModel` holds the switch.
- **Network usage** (Data and storage): `core:domain`'s `NetworkUsageRepository` (`TdlibNetworkUsageRepository`,
  `core:data`, no cache) reads TDLib's own statistics (`getNetworkStatistics` of every session since the last reset,
  answered from its database) into `NetworkUsage`, by network (TDLib's "other" networks counted as Wi-Fi, as automatic
  download treats them) and `DataKind` (TDLib's file types summed up), and resets them (`resetNetworkStatistics`).
  `feature:settings`' `NetworkUsageRoute` (`NetworkUsageViewModel`: the network the device uses first, the one chosen in
  saved state) shows them above Data and storage.
- **Personal channel** (Edit profile): `core:domain`'s `PersonalChannelRepository` (`TdlibPersonalChannelRepository`,
  no cache: `getSuitablePersonalChats`, `setPersonalChat` with 0 for none), the one shown read with the editable profile
  (`userFullInfo.personal_chat_id`); `feature:settings`' `PersonalChannelRoute` (`PersonalChannelViewModel`, the titles
  from `ChatRepository`) above Edit profile. Other users' profiles read it from `UserFullInfo.personalChatId` and the
  chat from `ChatRepository` (`getChat` where TDLib has not announced it).
- **Contact joined Telegram** (Settings → Notifications): `core:domain`'s `ContactJoinedRepository`
  (`TdlibContactJoinedRepository`, `core:data`, no cache) reads TDLib's option `disable_contact_registered_notifications`
  with `getOption` per collection, follows its `updateOption`, and sets it with `setOption`.
- **Link previews in secret chats** (Privacy and security, off by default): `core:domain`'s
  `LinkPreviewSettingsRepository` (`DataStoreLinkPreviewSettingsRepository`, the settings file). `core:data`'s
  `SecretChatLinkPreviews` gives the texts sent to a chat their `linkPreviewOptions` (disabled in a secret chat while
  the setting is off, `getChat` telling the chat's type), used by `TdlibMessageRepository.sendText`, the share sheet's
  texts and a notification's reply. `feature:settings`' `SecretChatPreviewsViewModel` holds the switch.
- **Hiding phone numbers** (Privacy and security, for sharing the screen or streaming): `core:domain`'s
  `PhoneNumberVisibilityRepository` (`DataStorePhoneNumberVisibilityRepository`, the settings file, off by default).
  `MainActivity` provides it as `core:ui`'s `LocalPhoneNumbersHidden`, composing nothing until it is read (with the
  Appearance settings, from the same file), so no number shows before it is known and the static local never changes
  after the first frame, and every screen that shows a number passes its formatted number through `phoneNumberToShow`, which keeps
  the calling code and the separators and turns the other digits into dots (`maskPhoneNumber`). What is copied, dialled
  or sent keeps the number; fields where the user types one show what is typed. `feature:settings`'
  `PhoneNumberVisibilityViewModel` holds the switch.
- **Showing IDs** (Privacy and security, off by default): `core:domain`'s `IdVisibilityRepository`
  (`DataStoreIdVisibilityRepository`, the settings file). `feature:profile`'s `ProfileViewModel` and
  `ChatInfoViewModel` follow it and put the user's or chat's ID (TDLib's, the one the bot API uses) into their state
  while it is on (`ProfileInfo.id`, `ChatInfoUiState.Content.id`), which the Info group shows as a row that copies it
  (`IdRow`). td_api.tl tells no user's or chat's data center, so none shows. `feature:settings`' `IdVisibilityViewModel`
  holds the switch.
- **Websites** (Privacy and security, "Log in with Telegram"): `core:domain`'s `ConnectedWebsiteRepository`
  (`TdlibConnectedWebsiteRepository`, `core:data`, no cache: TDLib announces no changes to them) lists the websites
  logged in with the account (`getConnectedWebsites`) and logs one or all of them out (`disconnectWebsite`,
  `disconnectAllWebsites`). `feature:settings`' `ConnectedWebsitesRoute` (`ConnectedWebsitesViewModel`: the list loaded
  again whenever the page shows, the bot named from `UserRepository`, the destructive question in saved state) is
  pushed above Privacy and security.
- **Deleting the account** (Google Play's account deletion policy, `docs/PLAY_STORE.md`): `AccountRepository`
  (`core:domain`, `TdlibAccountRepository` in `core:data`, no cache: TDLib has no updates for it) reads and sets how long
  the account may stay unused (`getAccountTtl`, `setAccountTtl`, in days) and deletes it (`deleteAccount` with the
  reason and the 2-step verification password, waiting as long as authentication requests). TDLib then closes the
  session by itself (`LoggingOut`, `Closed`), which the app handles as a logout. `feature:settings`' `DeleteAccountRoute`
  (`DeleteAccountViewModel`, above Privacy and security, which shows the period on its row) offers Telegram's periods
  (1 to 24 months, and one another app set) and deletes behind a destructive `AlertDialog`; what is typed stays in the
  page. The web page Play asks for is `docs/website/delete-account.md`.
- **Secret chats** (FR-E1): TDLib does all of the cryptography and accepts the secret chats other users start by itself
  (td_api.tl has no function for it). `SecretChatRepository` (`core:domain`, `TdlibSecretChatRepository` in `core:data`,
  a `TdlibUpdateCache`: TDLib announces each secret chat once with `updateSecretChat`, before its chat) keeps every
  `SecretChat` (state pending / ready / closed, outbound, layer, key hash as `EncryptionKeyHash`, which turns td_api.tl's
  36 bytes into the 12 × 12 image's pixels and the hexadecimal pairs), starts one (`createNewSecretChat`, returning its
  chat id), ends one (`closeSecretChat`) and sets the self-destruct timer, which is the chat's `messageAutoDeleteTime`
  (`setChatMessageAutoDeleteTime`, any number of seconds in a secret chat). Deleting a secret chat closes it first
  (`ChatDeletion`). The chat list shows the state of a chat that waits or ended in its preview (`core:ui`
  `SecretChat.stateText`); `feature:chat`'s `ChatSecretChat` (a part of `ChatViewModel`) gives the header its lock,
  state and timer, the composer its label while the chat is not ready, and keeps the timer and key sheets
  (`secretChatSheet`) out of `ChatUiState`. Screenshot blocking: a screen asks for a secure window with `core:ui`'s
  `SecureWindow` (requests counted in `SecureWindowRequests`, `LocalSecureWindow`), which `ChatRoute` does for a secret
  chat and `MediaViewerRoute` for its media; `MainActivity` keeps `FLAG_SECURE` while the lock or any screen needs it, in
  its one `SecureWindowEffect`, so neither clears the flag the other set.
- **Stories** (M6, read only): `core:domain`'s `StoryRepository` (`TdlibStoryRepository`, `core:data`, a
  `TdlibUpdateCache`: TDLib announces each chat's active stories only with `updateChatActiveStories`) keeps every chat's
  `ActiveStories` (list, `order`, `max_read_story_id`, story ids and dates) for the session and lists those of a
  `StoryList` in TDLib's order (by `order`, then chat id, both descending). The first chats of the main list load once
  per session (`loadActiveStories`, which answers 404 once all are loaded), more as the rings' row nears its end; a chat
  not announced is asked for with `getChatActiveStories`. A story is `getStory`, then its `updateStory` and
  `updateStoryDeleted` while collected; viewing is `openStory` / `closeStory`, reactions `setStoryReaction` (emoji
  only, those `getStoryAvailableReactions` offers without Premium), replies a text message to the poster's private chat
  replying to the story (`inputMessageReplyToStory`). `activateStoryStealthMode` is never sent: every story shown is
  marked viewed. Areas, privacy settings, interactions, saving and posting are left out. `feature:chats` draws the rings
  as the chat list's first row (`StoryRingsViewModel`, `StoryRingsRow`, passed to `ChatListScreen` as its `stories`
  slot, which the route gives only while there are rings and whose state the row reads in its own composition, so the
  rings changing recomposes nothing else; none while Appearance → Chat list → "Show stories" is off:
  `core:domain`'s `ChatListSettingsRepository`, `DataStoreChatListSettingsRepository`, the settings file, whose rings
  then load nothing). A ring opens `StoryViewerRoute(chatId, storyId?)` of `core:navigation`, a
  full-screen destination of `feature:media` (beside the media viewer, whose video player, images, caption and
  swipe-to-close it reuses; a module of its own would have needed copies of them, as features do not share code):
  `StoryViewerViewModel` pages through the main list's chats as they were when it opened, from the chat's first unseen
  story, keeps the chats, the one shown and its story in its saved state, and plays each story through `StoryTimeline`
  (a photo's 5 s on an injected `TimeSource`, a video on the `VideoPlayer`, progress in snapshot state read in the draw
  phase), pausing while held, paging, typing, choosing a reaction or stopped. It downloads the story's file at visible
  priority and the next one's at prefetch priority, and opens each story for TDLib once per showing while started.
- **Chat list swipes** (Appearance → Chat list → Swipe actions): `ChatListSettingsRepository` also keeps what swiping a
  chat toward each side does (`SwipeAction`) and whether swiping the list changes the folder instead
  (`swipeChangesFolder`), which the Swipe actions page (`SwipeActionsRoute`, `SwipeActionsViewModel`, `feature:settings`)
  sets and `feature:chats`' `ChatSwipesViewModel` hands to the chat list through `LocalChatSwipes`. While the list
  changes the folder, rows do not swipe and `Modifier.folderSwipe` (`FolderSwipe.kt`) moves the whole list with the
  finger, in its graphics layer (`FolderSwitch.position`, read in the draw phase, so a drag recomposes nothing); past a
  third of the width it selects the tab beside the selected one (`FolderNeighbours`, `ChatListEvent.SelectFolder`), and
  the folder's list slides in from the other side instead of fading through. The neighbouring folder's chats are not
  loaded ahead: the list slides away and the new one in, as a tab's tap fades them.
- **Pinned messages** (FR-C9): `PinnedMessageRepository` (`core:domain`, `TdlibPinnedMessageRepository` in `core:data`,
  no cache: TDLib introduces pinned messages by no update) loads a chat's pinned messages once per collection
  (`searchChatMessages` with `searchMessagesFilterPinned`, the newest 100), subscribed to the updates before it asks, then
  follows `updateMessageIsPinned` (a new pin fetched with `getMessage`), `updateDeleteMessages`, `updateMessageContent`
  and `updateMessageEdited`. `HiddenPinsRepository` (`DataStoreHiddenPinsRepository`, the settings file) keeps, per chat,
  the newest pin when a member who may not unpin hid the bar, which returns with a newer pin. `feature:chat`'s
  `ChatPinnedMessages` (a part of `ChatViewModel`, given both through `ThreadRepositories`) maps the bar
  (`ChatUiState.pinnedBar`, its position counted from the oldest pin), follows the list (`ChatEvent.MessagesVisible`:
  the newest pin at or above the newest message on screen), cycles through the pins as taps show each in the list
  (`ChatWindow.showMessage`, the jump of a reply quote; the choice holds until the user drags the list,
  `PinnedEvent.ListDragged`), unpins (`MessageActionRepository.unpin`, where `getMessageProperties` of the newest pin allows it) or
  hides, and keeps the sheet of every pin (`pinnedSheet`) out of `ChatUiState`.
- **Seen by and reactions** (FR-C6): `core:domain`'s `MessageInteractionRepository` (`TdlibMessageInteractionRepository`,
  `core:data`, no cache) lists who saw a group message (`getMessageViewers`, all at once, the latest view first) and who
  added its reactions (`getMessageAddedReactions`, every kind or one, paged by `next_offset`); `MSG_TOO_OLD` and
  `CHAT_TOO_BIG` are `DomainError.MessageViewersUnavailable`. TDLib decides where either is possible
  (`MessageProperties.canGetViewers`, `InteractionInfo.canGetAddedReactions`), and the message's actions
  (`ChatMessageActions`) offer "Seen by" and "Reactions" only there, handing a `MessageInteractionsRequest` over through
  `ChatEffect.OpenInteractions`. The sheet has its own view model, `MessageInteractionsViewModel` (`feature:chat`
  `interactions/`, keyed by the chat like `ShareLocationViewModel`), so `ChatViewModel` takes no repository more: it asks
  for nothing before the sheet opens and only for the tabs the user selects, each once while open, looks up the users and
  chats of a page (`UserRepository`, `ChatRepository`) before it shows, words the times as the conversation does
  (`ChatTimeFormatter.dayAndTime`), and keeps the request and the selected tab in its saved state, loading that tab again
  after process death. A row opens the profile through the route's `onOpenProfile`, a channel through `onOpenChatInfo`.
- **Scheduled messages** (FR-C5): sending with a time (`SendOptions.scheduledAt`) holds a message on Telegram's servers,
  outside the history (`HistoryCache` leaves every message with a scheduling state out). `core:domain`'s
  `ScheduledMessageRepository` (`TdlibScheduledMessageRepository`, `core:data`, no cache, as the pinned messages) loads a
  chat's `ScheduledMessage`s (the `Message` and its `MessageSchedule`: at a time, with td_api.tl's `repeat_period`, when
  the other user comes online, once a video is processed) once per collection (`getChatScheduledMessages`), subscribed
  to the updates before it asks, then follows `updateNewMessage` of messages with a scheduling state,
  `updateMessageSendSucceeded` / `Failed` of those being scheduled, `updateDeleteMessages` (also how one leaves once
  Telegram sent it: TDLib deletes it and announces the sent message, without a scheduling state, which the history
  takes), `updateMessageContent` and `updateMessageEdited`. "Send now" is `editMessageSchedulingState` without a state,
  rescheduling the same with `messageSchedulingStateSendAtDate` (keeping the repeat period), deleting `deleteMessages`;
  td_api.tl has no update for a new scheduling state, so each change made there loads the collections' lists again.
  `Chat.hasScheduledMessages` (`updateChatHasScheduledMessages`, kept by `TdlibChatRepository`) says whether to ask at
  all. `feature:chat`'s `ChatScheduledMessages` (a part of `ChatViewModel`, the whole chat's conversation only) gives
  the bar its count and the first time (`ChatUiState.scheduledBar`); the bar opens `ScheduledMessagesRoute(chatId)` of
  `core:navigation` (`scheduledMessagesScreen`, pushed by `:app` above the conversation, also from the detail pane).
  `ScheduledMessagesViewModel` maps the messages with the conversation's own mapping (`inputsOf`, `toItem`, so the
  bubbles are the conversation's `MessageBubbleRow`s) under their send days (`ScheduledItemMapper`), with the send time
  and the schedule in the meta (`ChatItem.Bubble.schedule`); its actions sheet sends now, reschedules (the composer's
  `ScheduleDialog`), edits in the conversation's composer (`ChatBottomBar` in edit mode, saved with
  `MessageActionRepository.editText` / `editCaption`; the message edited and its text in the saved state), copies and
  deletes (after an `AlertDialog`).
- **Message translation** (FR-C3, FR-C6): Telegram translates, nothing else sees a message. `core:domain`'s
  `TranslationRepository` (`TdlibTranslationRepository`, `core:data`, no cache) sends `translateMessageText` (the
  message by its ids, into one of the language codes td_api.tl lists, `targetLanguageOf` mapping a BCP 47 tag onto
  them; Telegram keeps the formatting for Telegram Premium subscribers only), and its refusals (a flood wait, Premium
  required) are shown, never worked around. Translations go into Pingogram's language (`AppLanguageRepository`), or
  the system's first one while it follows the system; the user reads that language and the system's languages.
  `DeviceLanguageDetector` (`AndroidDeviceLanguageDetector`, `core:data`) tells a text's language **on the device**:
  Android's `TextClassificationManager` (`detectLanguage`, Android 10+, on the IO dispatcher; the classifier runs in the
  app or the system's on-device text classification service), no library or model of its own, no text sent anywhere;
  before Android 10 it cannot tell, so "Translate" is always offered and the bar never shows. `Chat.isTranslatable`
  (`chat.is_translatable`, `updateChatIsTranslatable`, kept by `TdlibChatRepository`) is Telegram's suggestion to
  translate a whole chat, which it makes for Telegram Premium subscribers only. `HiddenTranslateBarsRepository`
  (`DataStoreHiddenTranslateBarsRepository`, the settings file) keeps the chats whose bar the user closed.
  `feature:chat`'s `ChatTranslation` (a part of `ChatViewModel`, `translation/`, given them through
  `TranslationRepositories`) offers "Translate" or "Show original" in a message's actions (`ChatMessageActions`'
  `translationAction`, asked with its properties) where the device found no language the user reads, keeps the
  translations in memory per message and language for the view model's life (dropped when the message's text
  changes), and copies them into the bubbles they change only (`withTranslations`: the text replaced, the meta shown
  with its label). The translate bar (`ChatUiState.translateBar`) shows where the chat is translatable, the device can
  detect, the user did not close it, and most of the 10 newest incoming texts are in one language the user does not
  read; turned on, it translates the messages on screen (`ChatEvent.MessagesVisible`, batched for 300 ms, at most four
  requests at once, those that leave the screen no longer waited for), never the history around them. Whether it is
  on, and the messages chosen or kept in the original, are in the saved state. A translated bubble that is the result
  the search in the chat shows marks the query's words in the translation it shows (`withSearchMatch` after
  `withTranslations`); words only its original has stay unmarked there, and "Show original" marks them.
- **Search in a chat** (§5.9): `core:domain`'s `ChatSearchRepository` (`TdlibChatSearchRepository`, `core:data`, no
  cache) pages a chat's messages that a `ChatSearchQuery` finds (`searchChatMessages` with the words, the sender and
  the topic, by `next_from_message_id`; a secret chat's, which only the device has, with `searchSecretMessages`, by its
  offset, words only), as message ids, and finds the message to show for a day (`getChatMessageByDate` before the day,
  then the next newer one with `getChatHistory`; `getChatMessageCalendar` counts only one kind of message, so it cannot
  say which days have any). `feature:chat`'s `ChatSearch` (a part of `ChatViewModel`, `search/`, given both it and
  `GroupInfoRepository` for a group's members through `ChatSearchRepositories`) searches the field's text 300 ms after
  the last edit, the newest query cancelling the search still running, and shows each result in the list as a reply
  quote's jump does (`ChatWindow.showMessage`); the next page loads near the last loaded result. Its state
  (`searchState`, the members' sheet) is kept out of `ChatUiState`, except for the one bubble of the result shown,
  copied with the words to mark (`ChatItem.Bubble.searchMatch`, `withSearchMatch`), which `core:ui`'s `withMatches`
  turns into blocks `textBlocks` draws, so no other bubble composes again. Whether search is open, the query, the
  sender and the result shown are kept in the saved state; after process death it searches again at that result
  without moving the list. `core:ui`'s `MatchHighlight` (the query's words and where a text has them) is shared with
  global search.
- **Replies and links** (FR-C5, FR-C6): what a message being sent or a draft replies to is `core:domain`'s
  `ReplyTarget` (a message of the chat, or of another chat, with the `TextQuote` it quotes), which every send of
  `MessageRepository`, `AttachmentRepository`, `VoiceNoteRepository` and `InlineBotRepository`, `ChatDraft` and the
  composer's `ComposerMode.Replying` carry; `core:data` turns it into `inputMessageReplyToMessage` or
  `inputMessageReplyToExternalMessage` with its `inputTextQuote` (`ReplyMappers.kt`) and back for drafts. Received
  quotes come with the replied message (`ReplyTo.Message.quote`), which the bubble's reply quote marks. `feature:chat`:
  `ChatMessageActions` quotes the part of the text selected in the lifted bubble ("Quote" in its text selection's
  menu, `quoteOf`: the selection's part found in the text or caption, with the formatting a quote keeps), copies a
  message's link (`MessageActionRepository.messageLink`, `getMessageLink`: an album's for an album's item, in its topic
  or thread in a conversation that is one), and opens `ChatReplyElsewhere` for "Reply in another chat": the forward
  sheet's `ChatTargets` in a sheet of its own (`replyChatsSheet`, kept out of `ChatUiState`), the picked chat getting
  the reply through `ShareInbox` (`deliverReply`, in memory, like a share: the reply goes the same way, from one screen
  to a chat's conversation, so it has no inbox of its own), which that chat's conversation takes into its composer once
  on screen; its bar fetches the other chat's message (`ComposerTargets.requireElsewhere`) and names the chat
  (`referencedChats`). Editing a message whose media TDLib lets be replaced (`MessageProperties.canEditMedia`) offers
  "Replace" (`ComposerMode.Editing.replace`); `ChatMediaReplacement` opens the picker through the route
  (`ReplyEffect.Pick`, `AttachLaunchers.replace`), prepares the pick in an attach preview of its own
  (`ChatAttachments.pickReplacement`, kept in saved state) and sends `AttachmentRepository.editMedia`
  (`editMessageMedia`, with the edit's text as caption, waited for without a timeout in the application scope, as TDLib
  answers after the upload, the copy deleted then); the bubble follows TDLib's `updateMessageContent`.
- **Bots** (FR-C5): `core:domain`'s `BotInfoRepository` (`TdlibBotInfoRepository`, `core:data`, no cache: TDLib keeps
  the full info and reports its changes) gives a conversation its `ChatBots`: `getChat` says what the chat is; a
  private chat whose user is a bot (`getUser`) follows `getUserFullInfo` and `updateUserFullInfo` (`bot_info`: the
  commands, the description, its photo or animation, the menu button), a basic group or a supergroup its full info and
  `updateBasicGroupFullInfo` / `updateSupergroupFullInfo` (`bot_commands`, bot by bot); channels, secret chats and
  chats with people ask for nothing more. `sendStartMessage` is `sendBotStartMessage`. `feature:chat`'s
  `ChatBotCommands` (a part of `ChatViewModel`, `bot/`) maps them: the composer's bot button
  (`ComposerUiState.Available.bot`: the menu button where the bot has one, which opens a Mini App and only says that
  Pingogram can't yet, else "/" for the commands), the commands sheet (`botCommandsSheet`) and the suggestions while a
  command is typed (`commandSuggestions`, from the composer's text through `snapshotFlow`), both kept out of
  `ChatUiState`; a group's commands are addressed to their bot ("/start@bot"). A command goes out as a text message
  through the thread's `MessageRepository`, taking the composer's reply along; inserting puts it into the field.
  In a private chat with a bot that has no messages, or that a start link opened, "Start" takes the composer's place
  (`ComposerUiState.BotStart`) and the bot's intro the empty state's (`ChatUiState.botIntro`); a tap sends
  `sendBotStartMessage` with the link's parameter, which `:app`'s `LinkViewModel` hands over through `feature:chat`'s
  `BotStartInbox` (a singleton, in memory, like `ShareInbox`; the conversation takes it, also when already open, and
  keeps it in its saved state until sent). Mini Apps come later; reply keyboards are below (Bot keyboards).
- **Inline bots** (FR-C5, "@gif cats"): `core:domain`'s `InlineBotRepository` (`TdlibInlineBotRepository`, `core:data`,
  no cache: TDLib keeps the users it resolved and recent answers) finds the bot a typed username names
  (`searchPublicChat`, then `getUser`'s `userTypeBot.is_inline`, `inline_query_placeholder`, `need_location`; a username
  nobody has is no bot), pages its results (`getInlineQueryResults`, each page with its own `inline_query_id`, into
  `InlineResult`s of every td_api.tl kind and the results' `InlineResultsButton`) and sends one
  (`sendInlineQueryResultMessage`, the bot named, the composer's reply, silent and scheduled options, the topic's or
  thread's `topic_id` through `ChatThread.inlineBots`). `feature:chat`'s `ChatInlineBots` (a part of `ChatViewModel`,
  `inline/`, given its repositories as one `InlineBotRepositories`) follows the field through `snapshotFlow`: only text
  that starts with "@username " (`inlineQuery`), not while editing, looks a bot up (once per username per
  conversation, never another bot ahead of time); queries wait 400 ms after the last change and the latest wins
  (`flatMapLatest` cancels the older one, whose late answer TDLib drops); results stay while a newer query is on its
  way; the list asks for the next page near its end (`InlineBotEvent.LoadMore`); a flood wait counts down before "Try
  again", which nothing presses by itself. Its state (`inlineResults`) is kept out of `ChatUiState`, as the "/"
  suggestions are, and drawn above the composer (`InlineResultsArea`), the placeholder after "@gif " in the field
  (`ComposerField`'s `hint`, placed from the text's layout). A bot that asks for the location gets a question first
  (`inlineLocationSheet`, `InlineLocationBottomSheet`, which asks Android's location permission itself where needed),
  whose answer `InlineLocationConsent` (a singleton, in memory) keeps per bot for the process: Pingogram asks again
  after a restart. With consent, `DeviceLocationRepository` finds the location once per conversation (10 s at most) and
  it goes with that bot's queries only; without consent or Android's permission nothing is located or sent. The
  results' "Start" button hands its parameter to the bot's chat through `BotStartInbox` and opens it, where "Start"
  waits for the user; a Mini App's button and a game result only say Pingogram can't open them yet.
- **Forum topics** (FR-C9): a message's `topic` (`MessageTopic`, `core:domain`: a forum topic or a message thread) comes
  from td_api.tl `message.topic_id`; `SendOptions.topic` sends into one (`sendMessage.topic_id`, every kind of message).
  `MessageRepository` keeps a history per `HistoryKey` (a chat, or one of its topics): a topic's pages come from
  `getForumTopicHistory` (a thread's from `getMessageThreadHistory`), its newest message from `getForumTopic`, and a new
  message joins the windows of the topics it is in (`HistoryKey.holds`; General also takes messages without a topic).
  `ForumTopicRepository` (`TdlibForumTopicRepository`, `core:data`: an `UpdateStore` of the forums someone observes,
  dropped with their last observer, so no `TdlibUpdateCache`) pages `getForumTopics`, follows `updateForumTopicInfo`,
  `updateForumTopic` and new messages, and fetches a topic again (`getForumTopic`) where no update says all (its unread
  count). `feature:chat`: `ChatRoute` decides what a chat opens to (`ChatOpeningViewModel`: a supergroup's `isForum` from
  `GroupRepository`), drawing the conversation's first frame meanwhile: the conversation (`ConversationRoute`), a
  forum's topic list (`ForumTopicsRoute`, `ForumTopicsViewModel`), or for a message found in a forum the conversation of
  its topic. `ChatRoute(chatId, topicId)` of `core:navigation` is a topic's conversation: `ChatViewModel` with a
  `MessageTopic`, whose `ChatThread` scopes the repositories to it (`TopicMessageRepository`, `TopicAttachmentRepository`,
  `TopicVoiceNoteRepository`, `TopicChatRepository` for `viewMessages` from the topic's history), reads the chat as the
  topic (unread count, read state; no draft, which topics keep apart and the composer does not sync yet), and puts the
  topic's name in the header and a closed topic's label in the composer. A topic tapped in the list is pushed above it
  (`chatScreen`'s `onOpenThread`); in the Chats tab's detail pane it takes the pane's place (`ChatPane`'s saved stack of
  topics and threads, shared axis X, back returning to the list). Managing topics: `ForumTopicRepository` also creates
  (`createForumTopic`, the name and one of Telegram's six icon colors), renames (`editForumTopic`, the icon kept: a
  topic's color is chosen once, and Pingogram offers no custom emoji icons), closes and reopens
  (`toggleForumTopicIsClosed`), pins, unpins and reorders the pinned ones (`toggleForumTopicIsPinned`,
  `setPinnedForumTopics`, the new order applied once TDLib took it), deletes (`deleteForumTopic`, the topic dropped then)
  and hides or shows General (`toggleGeneralForumTopicIsHidden`); TDLib's updates bring the rest back, and a blank name
  is refused before TDLib (`DomainError.TitleInvalid`). What the user may do comes from their role in the forum
  (`GroupInfoRepository.groupInfo`, `ForumRights` of `feature:chat`'s `topics/`): `can_manage_topics` for everything but
  deleting, which takes `can_delete_messages`; the creator of a topic renames, closes and reopens it; creating takes the
  members' `can_create_topics` (a restricted member's too). `TopicManagement` (the editor and the question before
  deleting in saved state, the toggles, their effects) is a part of both the list's `ForumTopicsViewModel` (which also
  keeps a hidden General for those who may show it, and a dragged order until Telegram takes or refuses it) and
  `TopicActionsViewModel`, a view model of the topic's conversation apart from `ChatViewModel`
  (`rememberTopicActions` in `ConversationRoute`), which gives its top bar "Manage topic".
- **Next unread channel** (`docs/audit/feature-gaps-octogram.md` P2): `feature:chat`'s `ChatNextChannel` (a part of
  `ChatViewModel`, `ChatViewModel.nextChannel`, kept out of `ChatUiState`) follows the chat while the conversation is a
  whole channel's, and only while it has no unread posts the main chat list (`ChatRepository.chatList`), whose first
  other channel with unread posts it offers (`NextChannel`). `NextChannelPullState` takes the message list's leftover
  upward drags through a nested scroll connection, read in the layout and draw phases; a release past the threshold
  sends `ChatEvent.OpenNextChannel`, which the view model checks against what it still offers before
  `ChatEffect.OpenNextChannel`. The route opens it through `onOpenNextChat`: `:app` replaces the pushed conversation's
  `ChatRoute` (`popUpTo<ChatRoute>`), and the Chats tab's detail pane shows it as any chat it opens.
- **Saved Messages tabs**: TDLib sorts Saved Messages into topics, one per chat the user saved from, the user's notes
  and messages of hidden authors (td_api.tl `savedMessagesTopic`, `message.topic_id` `messageTopicSavedMessages`, which
  `MessageTopic.SavedMessages` maps). `core:domain`'s `SavedMessagesTopicRepository` (`TdlibSavedMessagesTopicRepository`,
  `core:data`, a `TdlibUpdateCache`: TDLib announces each topic only with `updateSavedMessagesTopic`, after
  `loadSavedMessagesTopics`) keeps them for the session, pinned first and by `order`. A topic's history is
  `MessageRepository`'s window of that `HistoryKey` (`getSavedMessagesTopicHistory`; its newest message from its first
  page, as td_api.tl has no `getSavedMessagesTopic`). `feature:chat`: `ChatOpeningViewModel` opens Saved Messages as
  `ChatOpening.SavedMessages`, whose `SavedMessagesConversation` shows the selected tab's conversation (`ChatViewModel`
  with the topic, its `ChatThread` reading it as `ThreadState.SavedTopic`), each tab in a view model store of its own,
  and `SavedTabsViewModel` (the tabs, the first topics loaded once collected and more near the row's end, the selection
  in its saved state) hands the tabs to the top bar through `LocalConversationTabs`. What is sent from a tab goes to
  Saved Messages without a topic (`SendOptions.tdTopic` leaves Saved Messages topics out), which Telegram files under
  the user's notes, so only the notes' tab has a composer.
- **Comment threads** (FR-C9): a channel post's comments live in the channel's discussion group, and a group's message
  others replied to has a thread of its replies; TDLib gives both a reply count (`interactionInfo.reply_info`), which
  `ChatItem.Bubble.thread` turns into a link under the bubble ("12 comments" on every post of a channel with a
  discussion group, "3 replies" once there are some). `MessageThreadRepository` (`core:domain`,
  `TdlibMessageThreadRepository` in `core:data`, no cache) asks `getMessageThread`: the thread's chat (the discussion
  group), its id, the messages that start it (its head: the post as the group has it) and its read state. Tapping the
  link (`ChatEvent.OpenThread`, `ChatThread.open`) opens `ChatRoute(chatId, threadId = …)` of the discussion group,
  pushed or in the detail pane like a topic. Its conversation is `ChatViewModel` with `MessageTopic.Thread`: the replies
  from `getMessageThreadHistory`, with the head as their oldest messages once the history reaches the start (without
  its link), everything sent into the thread (`sendMessage.topic_id`), the chat's name over "12 comments" in the top
  bar, the thread's unread count and read state as they were when it opened, and a composer for anyone where the group
  does not ask to join first (`supergroup.join_to_send_messages`). A thread that cannot be fetched shows its error, and
  "Try again" asks again.
- **Bot buttons** (inline keyboards): a message's `replyMarkup` (`core:domain`'s `ReplyMarkup`: an inline keyboard, a
  bot's reply keyboard, removing it, forcing a reply, every kind of button of td_api.tl modelled) is mapped with the
  message (`ReplyMarkupMappers`, `core:data`) and replaced by every `updateMessageEdited`, which carries the bot's new
  markup (`HistoryCache`), so buttons change in place. `BotButtonRepository` (`TdlibBotButtonRepository`, no cache)
  sends what a tap asks: `getCallbackQueryAnswer` (a bot that does not answer, TDLib's 502 or no answer within 30 s,
  is `CallbackAnswer.NoAnswer`, not an error), `getLoginUrlInfo` and `getLoginUrl`, and `shareUsersWithBot` /
  `shareChatWithBot` for the reply keyboard (Bot keyboards, below). `feature:chat` shows the inline keyboard under its
  bubble (`ChatItem.Bubble.keyboard`, `BotKeyboardButtons`, a part of the bubble's footer layout beside the comments
  link, no subcomposition), and `ChatBotButtons` (a part of `ChatViewModel`) handles taps: the buttons waiting for
  Telegram (`ChatUiState` items copied only for those bubbles, `withPendingButtons`), the alerts and questions
  (`botButtonDialog`, the password in a `TextFieldState` never saved) and the chat picker of an inline query
  (`switchInlineSheet`, the forward sheet's `ChatTargets` filtered by the chat kinds the bot allows), kept out of
  `ChatUiState`. A query for another chat reaches its composer through `ShareInbox.deliver`, as a direct share does.
  Mini Apps, games and payments say that Pingogram does not offer them yet.
- **Bot keyboards** (reply keyboards): TDLib names the message whose markup is in effect for the user
  (`chat.reply_markup_message_id`, `updateChatReplyMarkup`; `Chat.replyMarkupMessageId`, kept by `TdlibChatRepository`;
  TDLib applies the personal flags and removals). `ChatBotKeyboard` (a part of `ChatViewModel`, `bots/`, the whole
  chat's conversation only) takes that message from the history, so a bot's edit counts at once, or fetches it once
  (`MessageRepository.message`), and gives the composer `BotReplyUiState` (`botReply`, kept out of `ChatUiState`: the
  keyboard's rows as the inline keyboards' `BotKeyboard`, its resize, one-time and persistent flags, the field's hint).
  Text buttons send their text (in groups replying to the keyboard's message); the phone number goes as the user's own
  contact (`AttachmentRepository.sendContact`) after an `AlertDialog` (`botKeyboardDialog`); the location through the
  attach menu's "Location" sheet and its permission step (`BotKeyboardEffect.ShareLocation`); a poll through the poll
  form, a quiz or a regular poll where the button requires one (`PollEditor.requiredQuiz`, saved); users and chats
  through a picker (`keyboardShareSheet`, `ChatTargets` with the contacts for people, filtered by the button's
  restrictions as far as the device knows them: kind, forum, username, creator, administrator) and
  `BotButtonRepository.shareUsers` / `shareChat`, never `only_check`. A force reply (or a keyboard that asks for one)
  starts a reply to its message once (saved), with its hint. On screen the keyboard takes the panel's place
  (`ComposerPanelState.content`, `PanelContent.BotKeyboard`, `BotReplyKeyboardPanel`): `BotKeyboardEffects` shows it
  whenever neither the system keyboard nor the emoji panel is up, unless the user put it away (`BotKeyboardState`,
  saved: the toggle, the field, the emoji button, back, a one-time keyboard's tap), which a persistent keyboard ignores
  except after a one-time tap. While an inline query shows its results (Inline bots), the keyboard steps aside.
- **Polls** (FR-C4): `core:domain`'s `PollRepository` (`TdlibPollRepository`, `core:data`, no cache) votes
  (`setPollAnswer`, the options' 0-based indexes in TDLib's order, `PollOption.index`; none takes the vote back), stops a
  poll (`stopPoll`) and pages an answer's voters (`getPollVoters`, 20 a page), each on a tap of the user's. TDLib marks a
  vote on its way at once (`pollOption.is_being_chosen`) and announces every change of a poll with `updatePoll`, which
  names no chat: `HistoryCache` replaces the poll of every loaded message that shows it, in every window and among the
  replied messages, also while a page loads (`HistoryChange.ReplacePoll`), so a poll forwarded into several chats stays
  current everywhere. `Poll` carries what the UI offers: `canVote` (open, no `voteRestriction`, not voted), `canRetractVote`
  (`allows_revoting`, never a quiz), `canGetVoters`. `feature:chat`'s `ChatPolls` (a part of `ChatViewModel`, `poll/`)
  sends the votes, keeps the answers checked before "Vote" (by message, in the saved state, copied into their bubbles
  only, `withPollChoices`), sends a quiz's `PollEffect.QuizAnswered` once the poll shows the answer (the `Confirm` or
  `Reject` haptic), and holds the question before a stop (`stopPollDialog`) and the voters' sheet (`pollVotersSheet`,
  the users named from `UserRepository`), both kept out of `ChatUiState`. "Retract vote" and "Stop poll" are message
  actions (`ChatMessageActions`), checked against `getMessageProperties` like the others: stopping needs
  `can_be_edited`. The bubble draws its bars with a `Modifier.Node` (`pollBar`) that animates the share in the draw
  phase; screen readers hear the answers in the bubble's description and vote through its custom actions.
- **ViewModels** expose a single `StateFlow<UiState>` (sealed: `Loading`, `Content`, `Error`,
  `Empty`) and accept events through plain functions. Side effects (navigation, snackbars) go
  through a `Channel<Effect>` consumed with `LaunchedEffect`. ViewModels are `@HiltViewModel`,
  take `SavedStateHandle` for route args, and use `viewModelScope` only.
- **Composables** are stateless where possible (`Screen(state, onEvent)`), with a thin
  `Route` composable that hoists the ViewModel. Every `Screen` has `@Preview`s for each state
  using `core:ui` preview parameter providers.

## Navigation

- Single-activity, `NavHost` in `:app`, type-safe routes with `kotlinx.serialization`.
- Graphs: `AuthGraph`, `MainGraph` (bottom nav / navigation rail: Chats, Contacts, Settings),
  nested `ChatRoute(chatId, messageId?)`, `ProfileRoute(userId)`, etc.
- Adaptive: on `WindowWidthSizeClass.Expanded` the Chats tab uses `ListDetailPaneScaffold`
  (`material3-adaptive`) so list + conversation show side by side; the same `ChatScreen`
  composable is reused in the detail pane. `:app` composes it (`ChatsListDetail`, a
  `NavigableListDetailPaneScaffold`) from `feature:chats`' `ChatListPane` and `feature:chat`'s
  `ChatPane`; the navigator holds the open chat. With one pane (compact, medium) a tap still pushes
  `ChatRoute`, and the archive pushes it on every window. New chat (`NewChatRoute`, `feature:chats`) is pushed above
  the Chats tab on every window too; a contact's chat takes its place on the back stack, so back from the chat
  returns to the chat list. With a hardware keyboard (`:app`'s `PaneKeyboard`), the list pane is one focus group that
  restores its last focused row; a chat opened from it in keyboard mode hands `ChatPane` `focusComposer`, which the
  conversation passes to its composer's `ComposerFocusState` once; Esc that the conversation did not take gives the
  focus back to the list (`docs/DESIGN_SYSTEM.md` §9).
- Settings is a list-detail too (`SettingsListDetail`, `feature:settings`, composed by `settingsScreen` as the
  `SettingsRoute` destination): Settings home as the list, the navigator holding the open page by its name. With one
  pane a row pushes the page's route above the tab (`settingsPages`, every window narrower than expanded); with two
  it shows the page in the detail pane, in a `NavHost` of its own that starts at the page and holds the pages it opens
  above it (the same `settingsPages` graph, on its own `NavController`), with a `ViewModelStoreOwner` of its own
  (`rememberViewModelStoreOwner`), so its view models survive configuration changes and are cleared when the page
  closes. Back pops that `NavHost` first, then the navigator closes the page. Both list-detail layouts share their pane
  parts from `core:ui` (`ListDetailPanes.kt`: the rounded pane container, the drag handle and its anchors, following
  the window's directive, showing a detail in place of another).
- Third pane (extra-large windows, ≥ 1600 dp): the Chats tab's scaffold has a third partition there
  (`chatsPaneScaffoldDirective`; Material gives large windows three already, which `:app` caps at two), and opens a
  user's profile or a group's or channel's info page in the extra pane beside the conversation instead of pushing it
  (`ChatsExtraPane`). Its navigator is `:app`'s `ChatsPaneNavigator`, Material's list-detail navigation except that the
  extra pane takes a partition only while the history holds it, so the conversation is not narrowed by an empty pane;
  the history (the list, a chat, a page beside it, as `ChatsPaneContent` string keys) is saved. The pane holds a
  `NavHost` of its own, as Settings' detail pane does (`ProfileRoute` or `ChatInfoRoute` at its start, the info page's
  pages and other profiles above it), with its own view model store, and is laid out as a window of its width
  (`core:ui`'s `PaneAsWindow`), so the pages show their one-pane layouts. A chat a page opens goes to the detail pane,
  closing the extra one. Back pops the pane's `NavHost`, then closes the pane, then the conversation. Its width is
  `core:ui`'s `ExtraPaneWidth` (360 dp, 412 dp or a third of the window, saved), resized by `ExtraPaneDragHandle`,
  since Material's drag handle resizes only two panes. Below extra-large, profiles and info pages are pushed as before.
- Opening a chat beside the list from elsewhere (expanded and wider windows): a deep link, full-screen search (a window
  that grew while it showed) and a profile's or info page's "Message" and shared media call `navigateToChat(chatId,
  messageId, besideList)` (`ChatsPaneNavigation.kt`), which leaves the request in the Chats tab entry's saved state
  (`chatToShow`) and pops back to the tab; `ChatsListDetail` shows it in the detail pane and takes it, so it survives
  rotation and process death on the way. A profile opened from the chat's own title still pops back to it. Narrower
  windows push `ChatRoute` as before.
- Deep links (`docs/adr/0013-deep-links.md`): `tg://` and `http(s)://` links to `t.me` (and its subdomains),
  `telegram.me` and `telegram.dog`. `core:domain`'s `LinkRepository.resolve(link)` (`TdlibLinkRepository`, `core:data`,
  no cache) is the one entry point: `getInternalLinkType`, then the lookup the kind needs (`searchPublicChat`,
  `searchUserByPhoneNumber`, `searchUserByToken`, `getMessageLinkInfo`, Saved Messages through `getMe`), into a
  `LinkTarget` (a chat at a message with an optional draft, a profile, a chat's info, a page of the app, an invitation,
  a QR login, a chat's boosts, the user's QR code; a bot to start with a parameter (`t.me/bot?start=…`, its chat
  with "Start" ready, never started by itself); a message to share, which opens the share sheet (Direct share,
  below); not found; a login code; unsupported).
  `:app`'s `LinkViewModel` (a `LinkDispatcher`, scoped to the activity, one link at a time) turns it into a
  `LinkEffect`: a screen, which `PingogramApp` opens (`openLinkTarget`, `LinkNavigation.kt`), or a notice in the shell's
  own `SnackbarHost` ("Opening link…" with Cancel after 200 ms, "Open in browser" for web links it does not open, which
  targets a browser and never Pingogram). Links arrive from `MainActivity`'s browsable `VIEW` filters (not App Links
  verified; `singleTask`, `linkOf`), kept in `MainActivityViewModel.linkToOpen` through the login and the passcode lock
  and handed over by `OpenLinkEffect` once the main graph shows; and from taps in the app, through the app's
  `LocalUriHandler` (`LinkUriHandler`, provided by `MainActivity`), which every feature already opens links with, so messages, bios, descriptions
  and sponsored messages need no hook of their own. Other links keep going to the system's handler. Mentions
  (`@username`) keep their own `searchPublicChat` path.
- App shortcuts (M6): the launcher icon's long press offers New chat, Search and Saved Messages, static shortcuts of
  `:app`'s `res/xml/shortcuts.xml`. Each is a Telegram link (`tg://new`, `tg://chats/search`,
  `tg://settings/saved-messages`) to `MainActivity`, which opens it as any other link (Deep links, above): kept through
  the login and the passcode lock, resolved by `LinkRepository`, opened by `openLinkTarget` (search docked on expanded
  windows). Their intents name the build's own package through the generated string `shortcut_target_package`
  (`app/build.gradle.kts`), as debug builds add ".debug". They count toward Android's shortcut limit with the
  conversation shortcuts, whose oldest `pushDynamicShortcut` drops.
- Predictive back: `PredictiveBackHandler` for the media viewer, attach sheet and selection
  mode; everything else relies on Navigation's built-in support, with the `NavHost`'s predictive
  pop transitions from `core:ui` (`docs/DESIGN_SYSTEM.md` §5).
- Shared elements: the app shell (`PingogramApp`: the navigation bar or rail and the `NavHost`) sits in a
  `SharedTransitionLayout`, whose scope `core:ui` hands down as `LocalSharedTransitionScope`
  (`ProvideSharedTransitionScope`); destinations that share content provide their animated visibility scope
  (`ProvideNavAnimatedVisibilityScope`), and `Modifier.containerTransform` (a no-op without both) runs a
  container transform between the two destinations' content of one key: the Chats tab's "New chat" button
  grows into the New chat screen, and the search button (outside the `NavHost`, in an `AnimatedVisibility` of
  its own that hides it while search shows) grows into search. `Modifier.sharedMediaBounds` (the bubble's
  side) and `Modifier.sharedMediaViewerBounds` (the viewer's) run that of a photo, video or GIF between the
  conversation (pushed or in the detail pane) and the media viewer, keyed by message id. Each bubble
  publishes its media's clip shape in `SharedMediaShapes` (`LocalSharedMediaShapes`, from a `Modifier.Node`
  while it is laid out), so the viewer morphs from and back to exactly those corners.
- Profiles (`ProfileRoute(userId)`, `feature:profile`): pushed above the screen that opens them, with the shared axis
  on X, on every window: from the title of a private or secret chat, a sender's avatar or name in a group, and a
  mention (`feature:chat` hands the user up through `onOpenProfile`, `:app` navigates). The profile's "Message" pops
  back to the conversation below it when that is the user's chat, and pushes the chat otherwise; its shared media open
  the media viewer or the chat at a message. "Add to contacts" / "Edit contact" call `profileScreen`'s `onEditContact`,
  which `:app` points at `feature:contacts`' `navigateToEditContact(userId)` (without it the rows are left out). On
  extra-large windows the Chats tab opens them in its third pane instead (above). The title's avatar grows into the
  pushed profile or info page with a container transform (`core:ui`'s `avatarTransform` / `avatarTransformTarget`, keyed
  by `AvatarTransformKey`), which steps aside with reduced motion like every `containerTransform`; not into the third
  pane, which comes in with the scaffold's own motion.
- "Show in chat" in the media viewer pops it and pushes `ChatRoute(chatId, messageId)` in place of the
  conversation of the same chat below it, if there is one.
- Forwarding (FR-C6): the forward sheet (`ChatForwarding` and `ForwardBottomSheet`, `feature:chat`) exists once. The
  conversation shows it in place, for its own messages (`ChatViewModel`). Other features reach it through
  `ForwardRoute(fromChatId, messageIds)`: a Navigation `dialog` destination (`forwardSheet`, `feature:chat`, registered
  in `:app`'s `NavHost`), so the screen below stays composed and visible. Its own window is transparent and dims nothing;
  the sheet keeps its `ModalBottomSheet` window, scrim and predictive back. `ForwardViewModel` reads the route
  from its `SavedStateHandle` and forwards in the `@ApplicationScope`, as the destination leaves when the sheet closes.
  What came of it goes to the `ForwardResults` singleton. The screen below shows those snackbars through `ForwardNotices`,
  which `:app` passes into the media viewer as a slot; without such a screen they are dropped. From the viewer, the
  snackbar's "Open" pushes the chat in the viewer's place. The app shell follows the screen below a dialog destination
  (`screenEntryAsState`), so the sheet neither shows the navigation nor changes the tab.
- Global search: on compact and medium windows a destination (`SearchRoute`) pushed above the tab; on
  expanded ones docked in the Chats tab's list pane (`SearchPane`, `feature:chats`) in place of the chat
  list, its results opening in the detail pane. `GlobalSearchState` (`feature:chats`, held by `PingogramApp`
  and saved) says whether search is docked and carries the two sides' shapes for the container transform.

## Profiles

- `feature:profile` shows other users' profiles (FR-P2; the user's own account is edited in Settings, through
  `ProfileRepository`). `ProfileViewModel` reads the user from `UserRepository` (`getUser` while TDLib has not announced
  them) and their full info from `core:domain`'s `UserProfileRepository` (`TdlibUserProfileRepository`, `core:data`, no
  cache: `getUserFullInfo`, then every `updateUserFullInfo` while collected): bio, birthday, block state, a bot's
  description. It makes the private chat on the device (`ChatRepository.privateChat`), which "Message" opens, the mute
  toggle mutes (`setMuted`), reporting reports (`MessageActionRepository.report` without messages, `reportChat`) and the
  shared media search. Blocking goes through `BlockListRepository`, secret chats through
  `SecretChatRepository.createSecretChat` (`createNewSecretChat`, Secret chats above), profile photos through
  `UserProfileRepository.profilePhotos` (`getUserProfilePhotos`, paged), and a mention's `@username` through
  `SearchRepository.publicChat` (`searchPublicChat`), which the conversation uses too.
- Shared media: `core:domain`'s `SharedMediaRepository` (`TdlibSharedMediaRepository`, no cache) pages a chat's
  messages of one `SharedMediaKind` (`searchChatMessages` with the kind's filter, by `next_from_message_id`);
  `ProfileSharedMedia` keeps each tab's pages and the tab shown (in the `SavedStateHandle`), and `SharedMediaMapper`
  turns messages into the grid's cells and the rows of files, songs, voice messages and links. The media viewer's own
  pages stay `ChatMediaRepository`'s.
- Compact and medium windows show one `LazyVerticalGrid` (header items, then the shared media); expanded ones a
  `SupportingPaneScaffold` (`material3-adaptive`) with the shared media as the supporting pane.
- "Online" and "last seen" are worded once, by `core:ui`'s `UserStatusFormatter`, for the conversation's top bar and
  the profile.

## Group and channel info

- `feature:profile` also shows groups and channels (FR-P3, the leaving and deleting of FR-P4): `ChatInfoRoute(chatId)`,
  which the conversation's title opens in basic groups, supergroups and channels (`ChatHeader.opensChatInfo`;
  private and secret chats keep `ProfileRoute`), pushed above the conversation, also from the detail pane, except on
  extra-large windows, where the Chats tab opens it in its third pane (Navigation). Three repositories of `core:domain`, none a cache (TDLib announces groups with `updateBasicGroup` /
  `updateSupergroup` and their full info only to whoever asks):
  - `GroupInfoRepository` (`TdlibGroupInfoRepository`): `groupInfo(chatId)` is `getBasicGroup` / `getSupergroup` with
    their full info, then every update of the group, its full info and `updateChatPermissions` while collected, as
    one `GroupInfo` (kind, usernames, counts, the user's `MemberRole` and its rights, the default permissions, the
    description, the slow mode, the primary invite link); full info that fails leaves the head with `detailsError`.
    `members` pages `getSupergroupMembers` (recent, administrators, restricted, banned, or a search), up to 200 a
    page; a basic group's members all come with its full info (`searchChatMembers` filters them), and it
    restricts and bans nobody; `member` is `getChatMember`.
  - `GroupAdministrationRepository` (`TdlibGroupAdministrationRepository`): `setChatTitle`, `setChatDescription`,
    `setChatPhoto` (a photo prepared by `PickedPhotoRepository`, uploaded with its progress as Edit profile's, or null
    to remove it), `setChatPermissions`, `setChatSlowModeDelay`, `setChatMemberStatus` (promoting with `ChatAdministratorRights`,
    restricting, banning, lifting), transferring the ownership (`canTransferOwnership`, then `transferChatOwnership`
    with the 2-step verification password, waiting as long as authentication requests; the password only in the view
    model's memory), and clearing the history (`deleteChatHistory` for the user only). Leaving and
    deleting go through `ChatRepository.deleteChat` (`ChatDeletion`), as the chat list's do.
  - `InviteLinkRepository` (`TdlibInviteLinkRepository`): the user's links (`getChatInviteLinks` by creator, active or
    revoked), one link, creating, editing, revoking and deleting them.

  - `ChatSettingsRepository` (`TdlibChatSettingsRepository`): a group's or channel's own settings, `getChat`
    (reactions, protected content), `getSupergroup` (signatures, topics, the user's role) and `getSupergroupFullInfo`
    (the linked chat) or `getBasicGroup`, each followed by its updates while collected; it sets them
    (`setChatAvailableReactions`, `toggleChatHasProtectedContent`, `toggleSupergroupSignMessages`,
    `toggleSupergroupIsForum`, `setChatDiscussionGroup` from `getSuitableDiscussionChats`) and upgrades a basic group
    (`upgradeBasicGroupChatToSupergroupChat`). The emoji Telegram offers as reactions come once at TDLib's start
    (`updateActiveEmojiReactions`), so it is a `TdlibUpdateCache` for them.

  - `JoinRequestRepository` (`TdlibJoinRequestRepository`): the requests to join through links that need approval,
    every link's at once (`getChatJoinRequests` with an empty link, pages of 50 after the last request), approved or
    dismissed one by one (`processChatJoinRequest`) or all together (`processChatJoinRequests`). How many wait is
    `GroupInfo.pendingJoinRequests` (`chat.pending_join_requests`, then `updateChatPendingJoinRequests`).
  - `ChatEventLogRepository` (`TdlibChatEventLogRepository`): the recent actions of a supergroup or channel, which its
    administrators read (`getChatEventLog`, the last 48 hours, pages of 50 before the last action, no query, filtered
    by `ChatEventFilter`s), mapped to `ChatEvent`s (who, when, a `ChatEventAction` of the kinds Pingogram words, the
    rest `Other`). `feature:profile`'s `RecentActionsRoute(chatId)` (`RecentActionsViewModel`: the chips in saved state,
    names from `UserRepository` and `ChatRepository`, once per person or chat) shows them.
  - `ChatStatisticsRepository` (`TdlibChatStatisticsRepository`): `getChatStatistics` (`is_dark` false: the theme
    colors the charts) into `ChatStatistics` (the period's values against the period before, the graphs, a supergroup's
    top senders), and `getStatisticalGraph` for the graphs Telegram gives as a token. Their data is Telegram's chart JSON,
    which `chartOf` reads with the platform's `org.json` into a `Chart` (x values, named series, line, bar or area,
    stacked, shares of the whole). `GroupDetails.canGetStatistics` says whether the user may ask. `feature:profile`'s
    `ChatStatisticsRoute(chatId)` (`StatisticsViewModel`: the tokens' graphs three at a time, numbers and days formatted
    in the user's language by `StatisticsFormat`) draws them on a canvas (`StatisticsChart`), no chart library.

  Telegram's refusals map to `DomainError`s of their own (`AdminRightsRequired`, `TitleInvalid`,
  `DescriptionTooLong`, `NotMutualContact`, `TooManyAdmins`), shown as snackbars or under their field.
- Pages (`feature:profile` `chatinfo/`, each with its view model, routes in `core:navigation`): the info page
  (`ChatInfoViewModel`: the chat from `ChatRepository`, the group from `GroupInfoRepository`, the first members, mute,
  the confirmations, and the shared media through the profile's `ProfileSharedMedia`), the members
  (`ChatMembersRoute(chatId, filter)`, `ChatMembersViewModel`: the filters the user's rights allow, paging, a search
  after a pause, the actions each member's role and the user's rights allow), a member's rights
  (`MemberRightsRoute(chatId, userId, restrict)`: promoting only with rights the user has, restricting only within the
  chat's defaults), the join requests (`JoinRequestsRoute`, `JoinRequestsViewModel`: a request leaves the list once
  Telegram answered, "Dismiss all" asks first), the settings (`ChatSettingsRoute`, `ChatSettingsViewModel`: each change one request, the page
  following Telegram's answer through the repository's flow; upgrading opens the new supergroup), the photo, name and
  description (`EditChatInfoRoute`, `EditChatInfoViewModel`: the photo apart from
  saving the texts, following `ChatRepository.chat` for the new one), the permissions and slow mode
  (`ChatPermissionsRoute`), and the invite links (`InviteLinksRoute`, `EditInviteLinkRoute(chatId, link?)`). What a
  view model types or chooses is kept in its `SavedStateHandle`. A page that changed members or links leaves a flag in
  the saved state of the page below (`popBackStackAfterGroupChange`), which reads its list again. Once the user left
  or deleted the chat, `:app` pops back to the chat list.
- Compact and medium windows show the info page as one `LazyVerticalGrid`, as the profile; expanded ones put the shared
  media in a supporting pane (`SupportingPanes`, shared with the profile).
- QR codes of a link are encoded on the device with ZXing's core by `core:ui`'s `QrCode` (`qr/`), shared with QR login
  and the user's own code: the modules as one path built once per size, dark on light in every theme; nothing leaves
  the device.

## QR codes, joining and boosts

FR-P5 (`docs/adr/0012-camerax-and-zxing-qr-scanner.md`, `docs/adr/0013-deep-links.md`):

- **Joining**: `core:domain`'s `ChatJoinRepository` (`TdlibChatJoinRepository`, no cache) checks an invite link
  (`checkChatInviteLink`, which only reads, into a `ChatInvite`) and joins by it (`joinChatByInviteLink`) or a public
  chat (`joinChat`), into a `JoinResult` (joined, request sent, a bot's check, declined). `feature:profile`'s join sheet
  (`ChatInviteRoute(inviteLink)`, a dialog destination like the forward sheet, `ChatInviteViewModel`) shows the chat and
  joins only on "Join"; a chat the user is in opens at once, a joined one in the sheet's place. `feature:chat`'s
  `ChatJoining` (a part of `ChatViewModel`) puts "Join" in the composer's place of a public supergroup or channel the
  user left or never joined (`ComposerUiState.Join`, `Supergroup.joinByRequest`); TDLib's `updateSupergroup` then
  turns the composer back on by itself.
- **Boosts**, read only: `ChatBoostRepository` (`TdlibChatBoostRepository`: `getChatBoostStatus`, and
  `getAvailableChatBoostSlots` only to count unused boosts; `boostChat` is never sent). `ChatBoostRoute(chatId)` is a
  page of the group info pages, a row of administrators' "Administration" in supergroups and channels, and what a boost
  link opens (`getChatBoostLinkInfo` in `TdlibLinkRepository`).
- **The scanner**: `feature:scanner` (the only module with CameraX) holds `QrScannerRoute(purpose)`, full screen
  (`isFullScreen`, no navigation): `ScannerCamera` (CameraX `Preview` in `CameraXViewfinder`, `ImageAnalysis` on its
  own thread, the torch) and `QrAnalyzer`/`QrDecoder` (ZXing, at most five frames a second, stopping at the first
  code), `QrImageReader` for a picked image (Photo Picker, IO dispatcher). `QrScannerViewModel` holds the camera's
  permission step (`CameraAccess`, refused for good in the `SavedStateHandle`), resolves a Telegram link with
  `LinkRepository` and keeps what does not open on screen (`ScanResult`: no Telegram link, with Copy; no login code
  when linking a device; a login code; not found; a failure). A target that opens leaves the scanner through `:app`,
  which pops it and hands the target to `LinkDispatcher.show`, as a tapped link that resolved. It opens from the
  Contacts tab's top bar, the user's QR code, and Active sessions' "Link desktop device" (`ScanPurpose.DeviceLogin`,
  through `settingsPages`' `onScanQrCode`, which the detail pane of Settings passes on to `:app`).
- **Linking a device**: `SessionRepository.confirmQrCodeLogin` (`confirmQrCodeAuthentication`) behind
  `feature:settings`' `QrLoginRoute(link)` (`QrLoginViewModel`), which sends nothing until "Log in", for a scanned code
  and a `tg://login` link from another app alike, then shows the new session; Active sessions below it loads its list
  again as it shows.
- **The user's QR code**: `ProfileRepository.profileLink` (`getUserLink`: `t.me/<username>`, or a temporary link
  without one) behind `MyQrCodeRoute` (`MyQrCodeViewModel`, a settings page), from the Contacts tab and
  `tg://settings/qr_code`, shared through Android's share sheet.

## Threading

- TDLib callbacks arrive on TDLib's thread; `TdlibClient` immediately hops to
  `Dispatchers.Default`. Nothing TDLib-related touches `Main` except state emission.
- File IO (saving media) on `Dispatchers.IO`. Image decoding via Coil's own threads.
- A conversation's audio player (`ExoAudioPlayer`, Media3) runs on a thread of its own: the main
  thread posts to it and reads the position from the player's last report (`PlaybackClock`), so
  no call, `release()` included (which waits for the codec and audio track), ever blocks a frame.
  A voice message holds a transient audio focus of its own (`SpeechFocus`, `AUDIOFOCUS_GAIN_TRANSIENT`, paused rather
  than ducked), so another app's music pauses for it and plays on after; music keeps ExoPlayer's permanent focus.
  Calls without Telecom take the focus as a call (`AudioManagerCall`), so a playing voice message or video pauses.
  Raise to listen (`RaiseToListen`, beside it): while voice messages play, the proximity sensor (no permission) moves
  a raised phone to the earpiece (communication mode, the earpiece as communication device on Android 12+, the
  proximity wake lock, the player's attributes for voice), never with headphones, Bluetooth or a call; it lets go
  1.5 s after the last voice message, so the next one stays at the ear. Its settings are `core:domain`'s
  `PlaybackSettingsRepository` (`DataStorePlaybackSettingsRepository`), which Appearance → Chats sets.
- So does its voice recorder (`MediaVoiceRecorder`, the platform's `MediaRecorder`, behind `VoiceRecorder`): every
  call suspends while it runs on the recorder's thread, `cancel()` and `release()` are posted, as preparing, starting
  and stopping a recording wait for the audio input and the encoder (`docs/adr/0007-voice-recording.md`).
- Dispatchers are injected (`@Dispatcher(IO)`, `@Dispatcher(Default)`) so tests can use
  `StandardTestDispatcher`. The qualifier lives in `core:common`; `:app`'s `DispatchersModule`
  provides the bindings (`core:common` is a plain JVM module without Hilt's plugin).

## Files and media

- TDLib downloads to its `filesDirectory`; UI requests via `downloadFile(fileId, priority,
  offset, limit, synchronous=false)` and observes `updateFile`. A `FileRepository` dedups
  requests and exposes `Flow<FileState>` (`NotDownloaded`, `Downloading(progress)`,
  `Downloaded(path)`).
- Streaming (`docs/adr/0028-streaming-media.md`): `core:domain`'s `FileStreamRepository` (`TdlibFileStreamRepository`,
  `core:data`, no cache) waits until a range of a file is on the device, downloading from its offset at visible
  priority (`downloadFile` with `limit` 0) unless a download it started covers it, and stops that download
  (`cancelDownloadFile`) when the reader leaves; TDLib keeps what came. `core:ui`'s `TdlibDataSource` is a Media3
  `DataSource` over it for `tdlib-file://<file id>` URIs, waiting on the player's loading thread (20 s at most) and
  reading the partial file directly. The media viewer's `ExoVideoPlayer` plays videos that support streaming through
  it as soon as they open (`PlaySource.Stream`), and its own downloads (`MediaDownloads`) leave such a file to the
  stream. The seek bar shows the player's buffered position.
- Picture-in-picture of a viewer's video: `feature:media`'s `VideoPipActivity` (its own task, out of the recent apps,
  not exported), not `MainActivity`, which would resize with the window. The viewer hands the playing video with its
  position to `PictureInPictureVideos` (a singleton, in memory) and closes; `VideoPipViewModel` plays it on a player
  of its own from there, the window's play or pause reaching it through `PipActionReceiver`. Expanding the window gives
  the video back, and `ReturnFromPictureInPictureEffect` (in `:app`'s `PingogramNavHost`) opens the viewer again at it,
  where `MediaPlayback.startAt` takes up the position. Secret chats' media stays in the viewer's secure window.
- Coil 3: custom `Fetcher` keyed by TDLib `fileId` that resolves the local path through
  `FileRepository`; thumbnails/minithumbnails shown while downloading (blurhash-like via
  `minithumbnail` bytes). The media viewer starts a photo from the largest copy of it already in Coil's memory cache
  (`largestImageKey`, as its placeholder), so the picture the conversation showed is there at once, sharp as it gets.
- Animated stickers (TGS = gzipped Lottie JSON, `docs/adr/0008-lottie-for-animated-stickers.md`): `core:ui`'s
  `AnimatedStickers` (one per process, `:app`'s `AnimatedStickersModule`, handed down as `LocalAnimatedStickers`; none
  in previews, where stickers show their thumbnail) downloads the file through `FileRepository`, decodes it with Lottie
  for Android behind `StickerAnimationDecoder` (the seam for rlottie later), and renders frames on two background
  threads; `AnimatedSticker` (a `Modifier.Node` in its own layer) draws them in bubbles and the sticker panel. WebM
  stickers show their thumbnail.
- Custom emoji (`docs/adr/0022-custom-emoji-rendering.md`): `core:domain`'s `CustomEmojiRepository`
  (`TdlibCustomEmojiRepository`, `core:data`, an `UpdateStore` emptied when TDLib closes, no `TdlibUpdateCache`: no
  update announces custom emoji) keeps every `CustomEmoji` (its sticker, whether it takes the text's color) that
  `getCustomEmojiStickers` returned, by id, asking only for ids it has not asked for (deduplicated across callers, at
  most 200 a request, a cancelled caller's ids asked for again next time). `feature:chat`'s `ChatCustomEmoji` (a part
  of `ChatViewModel`) hands it the ids of the texts, captions and reactions of the messages on screen
  (`ChatEvent.MessagesVisible`) and of the composer's text, at most every 100 ms, never the history's, and exposes what
  is loaded as `ChatViewModel.customEmoji`, outside `ChatUiState`. `ConversationRoute` hands it to `core:ui`'s
  `CustomEmojiRenderer` (`rememberCustomEmojiRenderer`, `LocalCustomEmoji`: snapshot state read only by the drawing
  nodes' coroutines and draw phase), with the app's `AnimatedStickers` for TGS ones and Coil (`CoilCustomEmojiStills`)
  for WebP pictures and WebM thumbnails. `toAnnotatedString` marks custom emoji with `CUSTOM_EMOJI_TAG` annotations,
  and `Modifier.customEmoji` (bubble texts, emoji-only messages, the composer's field through `CustomEmojiField`) finds
  their glyph boxes in the cached `TextLayoutResult`, clips them out of the text once a picture is there and draws it
  in their place, keeping a picture per id (`CustomEmojiFrames`, shared with reactions' `CustomEmojiImage`) whose
  frames redraw only the text's own layer; TGS ones play with the stickers' `StickerPlayer`, under the same slots.
  The fallback emoji stays in the text, for copying and screen readers, and shows until the picture does. Pingogram
  offers no custom emoji to add (Telegram Premium); a draft's or an edited message's keep their entities, which an
  edit inside them removes (`EntityEdits`).
- Emoji and sticker panel (`feature:chat` `panel/`, FR-C5): `EmojiPanelViewModel` (keyed by the chat like
  `ShareLocationViewModel`, loading only once the panel first opens) reads `EmojiCatalogRepository`
  (`AndroidEmojiCatalogRepository`, `core:data`: the bundled `res/raw/emoji_catalog.txt` generated by `tools/emoji` from
  Unicode's emoji data, without what the device's fonts cannot draw), `RecentEmojiRepository`
  (`DataStoreRecentEmojiRepository`, the settings file) and `StickerRepository` (`TdlibStickerRepository`: installed
  sets, recent and favorite stickers, each read again on its update, and a set's stickers when its section comes near
  the screen). Emoji go into the composer's `ComposerText` at the cursor; a sticker goes through `ChatViewModel`
  (`ChatEvent.SendSticker` → `ChatAttachments` → `AttachmentRepository.sendSticker`), so it takes the composer's reply
  along. The panel takes the keyboard's place (`ComposerPanelState`, `PanelArea`): it takes the keyboard's last height
  and stays until the keyboard is fully up, read from the IME insets in the layout phase, so switching moves nothing.
  The GIF tab (`PanelGifs`, a part of `EmojiPanelViewModel`, which takes the chat's id for it) reads
  `SavedGifRepository` (`TdlibSavedGifRepository`, `core:data`, no cache: `getSavedAnimations`, read again on
  `updateSavedAnimations`; `removeSavedAnimation`) and searches through the bot Telegram names for it (the option
  `animation_search_bot_username`) as an inline query in that chat (`InlineBotRepository`: the bot looked up at the
  first search, queries 400 ms after the last change, the newest cancelling the older, its GIF results only, paged by
  `next_offset`). A GIF goes out through `ChatViewModel` (`ChatEvent.SendAnimation` → `ChatAttachments` →
  `AttachmentRepository.sendAnimation`, `inputMessageAnimation` of its file by id), with the composer's reply. While
  the tab's search field has the focus (`ComposerPanelState.searching`), the keyboard comes up below the panel, which
  stays above it instead of giving way.
- Auto-download (`docs/PERFORMANCE.md` §7): the rules of each network (`AutoDownloadSettings`, `core:domain`) are kept in
  DataStore (`DataStoreAutoDownloadRepository`), not in TDLib's `setAutoDownloadSettings`, which stores them on
  Telegram's servers, has no switch for voice messages and needs the network to read them back. `AndroidNetworkRepository`
  (`core:data`, `ACCESS_NETWORK_STATE`) follows the default network and Data Saver; `DefaultAutoDownloadPolicy` combines
  both into the rules in effect (`AutoDownloadPolicy`). The conversation enforces them (`ChatAutoDownloads`): the files
  of messages on screen that the rules allow download at prefetch priority, and photos load through Coil with
  `TdlibFileImage(download = false)` where the rules leave them for a tap, which only waits for the file, under a
  download button.
- Power saving (Settings → Power saving): `core:domain`'s `PowerSavingSettings` (the mode: off, with Battery Saver,
  always; the parts it turns off: animated stickers and custom emoji, autoplay, call screen animations) are kept in
  DataStore (`DataStorePowerSavingRepository`, bound in its own `PowerSavingModule`), `AndroidBatterySaverRepository`
  (`core:data`) follows `PowerManager.isPowerSaveMode` through `ACTION_POWER_SAVE_MODE_CHANGED`, and
  `DefaultPowerSavingPolicy` combines both into what is turned off now (`PowerSavingPolicy.inEffect`, `PowerSaving`).
  Three places follow it: `:app`'s `AnimatedStickersModule` hands `AnimatedStickers` a `still` flow, which holds every
  sticker and custom emoji at its first frame (`StickerPlayer`); `MediaViewerViewModel` turns `MediaPlayback`'s
  autoplay off, so the current video or GIF waits for its play button (`VideoPlayback.waitsForTap`); `CallActivity`
  and `GroupCallActivity` pass it to `CallTheme`, whose `LocalStillCallAnimations` holds the ringing avatar's turn and
  the speaking rings' fades still. `feature:settings`' `PowerSavingRoute` (`PowerSavingViewModel`) sets it. Reduced
  motion stays the system's.
- Background transfers (FR-N4, `docs/adr/0009-background-transfers.md`): `core:domain`'s `TransferRepository`
  (`TdlibTransferRepository`, `core:data`, a `TdlibUpdateCache`) counts what the user started and is still on its way:
  the file of each message being sent from this device (from its `updateNewMessage` to its send succeeding, failing or
  its deletion, progress from `updateFile`), and the downloads asked for with `DownloadPriority.Requested` (a document
  or a photo tapped in a bubble, files saved from a selection), which `TdlibFileRepository` hands it. Automatic downloads
  (prefetch) and what is on screen (visible) never count. `:app`'s `TransfersController` starts `core:notifications`'
  `TransfersWorker` (unique expedited work) as transfers begin while the app is in the foreground, or when the user
  comes back to them; the worker runs in WorkManager's foreground service with type `dataSync`, shows their progress
  in the "Transfers" channel (`TransferNotificationBuilder`, `ProgressStyle` on Android 16) with "Cancel"
  (`TransferCancelReceiver` → `cancelAll`: `cancelDownloadFile`, `deleteMessages` of the messages being sent), and ends
  2 s after the last one. Android 15's daily `dataSync` limit reaches it through WorkManager's `onTimeout`.
- Send-as (FR-C5): the chat's `Chat.messageSender` (`updateChatMessageSender`, kept by `TdlibChatRepository`) is who new
  messages go out as; `SendAsRepository` (`TdlibSendAsRepository`, `core:data`, no cache) lists who the user can send as
  (`getChatAvailableMessageSenders`) and changes it (`setChatMessageSender`). `feature:chat`'s `ChatSendAs` (a part of
  `ChatViewModel`) puts the sender's avatar on the composer and keeps the picker (`sendAsSheet`) out of `ChatUiState`.
- Voice messages (FR-C5) are recorded by `feature:chat`'s `ChatRecorder` (a part of `ChatViewModel`, its state kept
  out of `ChatUiState` as only the composer shows it) into a file `VoiceNoteRepository.newVoiceNoteFile`
  (`TdlibVoiceNoteRepository`, `core:data`) makes in the upload cache (`uploads/<uuid>/voice.ogg` or `.m4a`), and sent
  with `sendVoiceNote` (`inputMessageVoiceNote`); like
  picked files, the file is deleted once TDLib uploaded it (`UploadCleaner`), or when the recording is cancelled or
  refused.
- Round video messages (FR-C5, `docs/adr/0032-round-video-messages.md`) go through the same `ChatRecorder`, whose
  record button a tap switches to video (`RecordModeUiState`, kept in `ComposerSettingsRepository.recordsVideoMessages`
  where the device has a camera): `feature:chat`'s `videonote/` package records them on `VideoNoteRecorder`
  (`CameraXVideoNoteRecorder`: CameraX's `Preview` and `VideoCapture` of a `Recorder` in one `UseCaseGroup` with a 1:1
  `ViewPort`, bound to a lifecycle of its own that is resumed only while it records, a persistent recording so a flip
  rebinds the other camera; SD quality cropped square, about 1 Mbit/s, at most 60 s) into the files
  `VideoNoteRepository.newVideoNoteFiles` (`TdlibVideoNoteRepository`, `core:data`) makes in the upload cache
  (`uploads/<uuid>/video_note.mp4` and the first frame's `video_note.jpg`), and sends them with `sendVideoNote`
  (`inputMessageVideoNote`, through the thread's `TopicVideoNoteRepository` in a topic), cleaned up as voice notes
  are. The preview is the recorder's `SurfaceRequest` (`ChatViewModel.videoNotePreview`), drawn by camera-compose's
  `CameraXViewfinder` in the round preview over the messages. CameraX is in two feature modules now, `feature:scanner`
  and `feature:chat`, at one catalog version: an upgrade changes both, and a third camera feature moves them into a
  `:core:camera` module.
- Without compression (`AttachmentKind.Original`, the attach preview's switch, its default `ComposerSettingsRepository.sendPhotosInOriginalQuality`
  in Data and storage): `ChatAttachments` keeps the gallery's URIs of the preview's items and, on sending, prepares
  each again as a file. `AttachmentPreparer` copies a JPEG's image data byte for byte without its metadata
  (`JpegMetadata`: every application segment but JFIF and every comment taken out, the EXIF orientation written back
  alone), and writes any other photo anew as a JPEG at its size (at most `ORIGINAL_MAX_SIDE`, 8192 px) and quality 100;
  videos go as they are. A photo whose metadata cannot be taken out is not sent so: its compressed copy goes instead.
  A photo more than 20 times as long as wide (td_api.tl `inputPhoto`'s limit) goes as such a file either way.
- Picked photos (Edit profile, a new group or channel, a group's or channel's info): `core:domain`'s
  `PickedPhotoRepository` (`AndroidPickedPhotoRepository`, `core:data`) is the one place that prepares them, as picked
  photos are prepared for sending (`AttachmentPreparer`: copied into `uploads/`, decoded and written anew as an
  upright JPEG without metadata), at most `PickedPhotoLimits.MAX_SIDE_PX` (1280 px) on the longer side; anything
  Android cannot decode as a still image is refused (`DomainError.PhotoInvalid`). The caller discards the copy once
  TDLib answered; one left behind goes with the upload cache's cleaning. `core:ui`'s `PhotoEditor` (with `PhotoTask`)
  draws the photo with its buttons and progress for Edit profile and the chat info's edit page; uploads with progress
  share `uploadPhoto` (`core:data`, the file's `updateFile`s).
- Files leave the app only on the user's request and only through `:app`'s `FileProvider`
  (`${applicationId}.files`, `res/xml/file_paths.xml`), which serves TDLib's files directories and
  never its database; `core:ui`'s `TdlibFiles.kt` builds the URIs and the share and view intents.
  The media viewer saves into the gallery through `MediaStore` (Android 10+) or the public
  directories with `WRITE_EXTERNAL_STORAGE` (Android 8–9 only), and the conversation saves files into Downloads the
  same way, both behind `SharedStorageRepository` (`core:domain`; `AndroidSharedStorageRepository` in `core:data`).

## Location

- The attach menu's "Location" sends the device's location (`docs/adr/0006-platform-location-manager.md`).
  `AndroidDeviceLocationRepository` (`core:data`, behind `core:domain`'s `DeviceLocationRepository`) reads the access the
  permissions give and finds the location with the platform's `LocationManager` (`LocationManagerCompat`), never Google
  Play services: a cold `Flow` of the best location so far, the last known one first, whose collection keeps the
  providers on.
- The sheet has its own view model, `ShareLocationViewModel` (`feature:chat`, keyed by the chat like `ChatViewModel`):
  the permission step, the search while the sheet is open and the screen collects its state (`WhileSubscribed`, so it
  stops in the background), a 30 s timeout for a first location, and the snackbar when the location is off. The route
  asks Android for the permissions and opens the settings and the maps app (`LocationInputs`). Sending goes through
  `ChatViewModel` (`ChatEvent.SendLocation` → `ChatAttachments` → `AttachmentRepository.sendLocation`), so the location
  takes the composer's reply along like every attachment.

## Notifications

- TDLib's own notification engine decides what to show: it applies mute settings, exceptions and read messages,
  and removes what was read anywhere. `TdlibNotificationRepository` (`core:data`, a `TdlibUpdateCache`, as
  `updateActiveNotifications` comes only once after TDLib starts) turns it on at every login
  (`notification_group_count_max` 25, `notification_group_size_max` 10, `docs/TDLIB.md`) and reduces
  `updateActiveNotifications`, `updateNotificationGroup`, `updateNotification` and `updateHavePendingNotifications`
  into `ActiveNotifications` (`core:domain`), with the chats on screen (`OpenChats`, kept by `TdlibChatRepository` as it
  opens and closes chats). Logging out drops everything.
- `core:notifications` only renders: `NotificationRenderer` (started by `PingogramApplication` in every process, on a
  background coroutine just after TDLib) posts one `MessagingStyle` notification per TDLib group under one summary, with a `Person` per sender
  (avatars from `AvatarIcons`, initials on the placeholder colors without a photo) and the chat list's previews
  (`core:ui` `messagePreviewText`, or "New message" without `showPreview`). Only notifications not seen before alert;
  updates, silent messages and restored notifications set `GROUP_ALERT_SUMMARY` under a summary with
  `GROUP_ALERT_CHILDREN`, so they do not alert. Chats on screen get no notification. After a restart, notifications
  TDLib no longer knows are cancelled, and a restored group Android still shows as an earlier process posted it (its
  fingerprint in the notification's extras, `GroupFingerprint`) is not posted again; logging out cancels everything
  and removes the conversation shortcuts.
- Channels (`PingogramChannel`, `NotificationChannels`): Private chats (secret chats too), Groups, Channels, Calls,
  Other, and Transfers (low importance, no badge: the progress of background transfers), created at every start; the one place that decides channels, except the `foss` flavor's "Connection" channel
  of "Keep connected", which only that flavor creates, with its service.
- Android 11+: a long-lived conversation shortcut per chat (`ConversationShortcuts`), linked by `setShortcutId` and
  `LocusId`, so notifications show in the conversation section. Every one is a Direct Share target: it carries
  `NotificationIntents.SHARE_TARGET_CATEGORY`, which `:app`'s `res/xml/shortcuts.xml` `<share-target>` names for
  `MainActivity`, and `ShareTargetShortcuts` (started by `PingogramApplication`) publishes the 8 most recent chats of
  the main chat list where the user can write, Saved Messages left out, 5 s after they last changed (Direct share,
  below), leaving the targets Android already has unchanged (their key and rank in the shortcut's extras, read once
  per process), so a start does not push them all again. No bubbles yet (they need a resizable chat activity of their own).
- Android Auto and Wear OS take these notifications as they are (`docs/ANDROID_AUTO.md`): `:app`'s `gms` flavor declares
  Android Auto's notification use (`res/xml/automotive_app_desc.xml`, a meta-data name, no library), and the
  `MessagingStyle`, Reply and Mark as read follow its rules for messaging apps (`AndroidAutoNotificationTest`). No car
  screen, no watch app.
- Tapping opens the chat: an explicit intent to the launcher activity (`NotificationIntents`), which `MainActivity`
  hands to `PingogramNavHost` (`chatToOpen`), which pushes `ChatRoute` above the chat list once logged in.
- Every account (`docs/adr/0031-multiple-accounts.md`): `NotificationRenderer` follows `core:domain`'s `AccountGraphs`
  (`DefaultAccountGraphs`, `core:data`: each account's `AccountRepositories`, once the registry is read) and runs an
  `AccountNotificationRenderer` per account, from that account's repositories. An account's notifications have their
  own tags (`group_1`), bundle and channels (`PingogramChannel.idFor`: `private_chats_1`), and its conversation
  shortcuts their own ids (`chat_1_<id>`); the first account's are as before there were several. While more than one
  account is signed in, each notification shows its account's first name as its subtext, and each account's channels
  are in a channel group named after it. An account that leaves the device takes its notifications, shortcuts and
  channels with it. Intents, shortcut ids and the actions' work carry the slot: `MainActivity`'s `AccountRestart`
  switches to an intent's account first and opens the app again with it, and `NotificationActionWorker` acts on that
  account's repositories. Direct Share targets and the widget are the shown account's, with its slot.
  "Notifications from all accounts" (`core:domain`'s `AccountNotificationsRepository`,
  `DataStoreAccountNotificationsRepository`, the settings file, on by default) hides the other accounts' notifications
  while off; showing them again posts them quietly. Settings → Notifications (`NotificationAccounts`) shows the switch
  with more than one account and opens the shown account's channels.
- Reply (`RemoteInput`, `SEMANTIC_ACTION_REPLY`), Mark as read (`SEMANTIC_ACTION_MARK_AS_READ`) and dismissing go
  through `NotificationActionReceiver` to `NotificationActionWorker`, expedited one-time work (WorkManager with
  `HiltWorkerFactory`, initialized on demand by `PingogramApplication`'s `Configuration.Provider`), which waits for
  TDLib (`AuthRepository.awaitLoggedIn`), then `sendMessage` (keeping the draft) + `viewMessages(force_read)`,
  `viewMessages(force_read)`, or `removeNotificationGroup`. TDLib's updates then remove the notification.
- Push (`gms` flavor only, `docs/adr/0004-push-delivery.md`): `TdlibPushRepository` (`core:data`, started by the
  `gms` `BackgroundConnection`, `FirebasePushConnection`) registers the Firebase Cloud Messaging token with `registerDevice` (encrypted) at every
  login and start while logged in and whenever FCM issues a new one, and deletes the token at logout. The token comes from `core:domain`'s `PushTokenSource`, bound only in `gms` (`FirebasePushTokenSource`,
  `app/src/gms`); `foss` binds none (`@BindsOptionalOf`) and the repository does nothing. `PingogramMessagingService`
  (`gms`) hands each push's data to `processPushNotification`, waiting a few seconds at most; TDLib's notification
  engine then produces the notifications above (`NewPushMessage` until it has synced), in the process FCM started.
  Firebase starts where it is first needed (`FirebaseStart`: the token source, the messaging service's `onCreate`),
  not in its `FirebaseInitProvider`, which the flavor's manifest removes.
  Every account (`docs/adr/0031-multiple-accounts.md`): the connection and the messaging service use `core:domain`'s
  `DevicePushRepository` (`DefaultDevicePushRepository`), which starts each account's `TdlibPushRepository`, hands a new
  token to all of them and a push to the account whose receiver id (`registerDevice`'s answer) the push names
  (`getPushReceiverId`), or to every account when none matches. Each account's registration names the other
  signed-in accounts' users (`other_user_ids`, `SignedInUsers`), and only the last one signed in deletes the token.
- Android 13+: the chat list asks for `POST_NOTIFICATIONS` with a card above the list (`NotificationPromptHost`,
  `feature:chats`); "Not now" and the first request are kept in DataStore (`NotificationPromptRepository`), so it never
  nags, and after a final refusal it offers the app's notification settings.
- Notification settings (Settings → Notifications, `feature:settings`): `TdlibNotificationSettingsRepository`
  (`core:data`, no cache: TDLib keeps them) reads and sets the defaults of each `NotificationScope`
  (`getScopeNotificationSettings`, `setScopeNotificationSettings`, which replace every field, so it reads first and
  changes only what Pingogram shows), lists the exceptions (`getChatNotificationSettingsExceptions` without comparing
  sounds, then `getChat` for each), changes a chat's mute (`setChatNotificationSettings`, the time left of a limited
  mute turned into its end with the `WallClock`), resets everything (`resetAllNotificationSettings`) and follows
  `updateScopeNotificationSettings` and `updateChatNotificationSettings`. A scope's defaults carry its story
  notifications too (`ScopeNotificationSettings.stories`, `StoryNotifications`: td_api.tl `use_default_mute_stories` and
  `mute_stories`, and `show_story_poster`), which the private chats' group sets; Pingogram words a story's push as any
  push without a text ("New message"), and lists no chat's own story settings yet
  (`getStoryNotificationSettingsExceptions`). Sounds stay with Android: the page opens each scope's channel
  (`PingogramChannel.of(scope)`) in the system settings. It shares the chat list's
  `NotificationPromptRepository` to ask for the permission while Android still shows its dialog.
- Reaction notifications (Settings → Notifications → Reactions): `core:domain`'s `ReactionNotificationRepository`
  (`TdlibReactionNotificationRepository`, `core:data`, a `TdlibUpdateCache`: TDLib announces the settings once per
  start, `updateReactionNotificationSettings`, and has no function to read them) sets whom the account hears of
  reactions and poll votes from (`setReactionNotificationSettings`, the sound Telegram keeps sent back as it was). TDLib
  (a94b83d) refuses Telegram's reaction pushes (`REACT_*`, "unsupported"), so no reaction notification reaches
  `NotificationRenderer`: the settings serve the account's other apps until TDLib passes them on.
- Background delivery depends on the flavor (`PushDelivery`, bound in `app/src/foss` and `app/src/gms` with a
  `BackgroundConnection` that `PingogramApplication` starts). `foss`: the optional "Keep connected" foreground service
  (`KeepConnectedService`, type `specialUse`, its own low-importance "Connection" channel), run by
  `KeepConnectedController` from the setting (`KeepConnectedRepository`, DataStore), any account's login and the app being in
  the foreground, with a boot receiver enabled only while the setting is on
  (`docs/adr/0003-keep-connected-foreground-service.md`). `gms`: `FirebasePushConnection`, which starts
  `TdlibPushRepository` (Push, above).
- `core:notifications` depends on `core:domain`, `core:common` and `core:ui` (previews, names, avatar colors), never
  on `core:data` or TDLib.

## Direct share

`REQUIREMENTS.md` FR-N2, `docs/adr/0014-direct-share.md`. Nothing is ever sent without the user's tap.

- **In**: `MainActivity`'s `SEND` / `SEND_MULTIPLE` filters (text, images, videos, any files). `:app`'s `shareOf` makes
  an `IncomingShare`: `core:domain`'s `SharedContent` (the text, the files' `content://` URIs, never `file://` ones or
  Pingogram's own providers, and whether they go as media or documents) and the chat of a Direct Share target
  (`EXTRA_SHORTCUT_ID`, `NotificationIntents.chatIdOfShortcut`). `MainActivityViewModel.shareToOpen` holds it, like
  `linkToOpen`, until logged in and unlocked (`OpenShareEffect`); a `t.me/share` link (`LinkTarget.MessageDraft`)
  arrives through `openLinkTarget`. In memory only: the files' read grants end with the process.
- **Hand-over**: `feature:chat`'s `ShareInbox` (singleton): `offer` keeps a share for the share sheet by id
  (`ShareRoute(shareId)`), `deliver` keeps one for a chat. `:app`'s `openShare` opens the sheet, or a Direct Share
  target's chat above the chat list.
- **Share sheet** (`ShareRoute`, a dialog destination like the forward sheet, `ShareViewModel`): the forward sheet's
  chat list (`ChatTargets`: Saved Messages first, the chat list, search; chats where the user can write) and parts. One
  chat picked opens it with the share delivered to it; several get it at once after "Send" with an optional comment,
  through `core:domain`'s `ShareRepository` (`TdlibShareRepository`) in the `@ApplicationScope`, its result
  (`ShareResults`) a snackbar of the app shell (`ShareNotices`).
- **Composer**: `ChatShares` (part of `ChatViewModel`) takes a share delivered to its chat once the conversation is on
  screen: a text into the field after the chat's draft (`ChatComposer.addShared`), files into the attach preview with
  the text as caption (`ChatAttachments.addShared`), prepared and later cleaned up as picked files are.
- **Several chats**: `TdlibShareRepository` prepares the files once (`AttachmentPreparer`) and sends the same copies to
  each chat (`sendAttachments`), holding them from `UploadCleaner` (`hold`, `release`) until all were sent.

## Calls

`REQUIREMENTS.md` FR-V1, `docs/adr/0015-call-media-engine.md` (the engine), `docs/adr/0018-calls-and-telecom.md` (Android).

- **Signaling**: `core:domain`'s `CallRepository` (`TdlibCallRepository`, `core:data`, a `TdlibUpdateCache`: TDLib
  announces every call with `updateCall` only) keeps the session's calls and passes the other side's engine data
  (`updateNewCallSignalingData`) on; it starts (`createCall`), answers (`acceptCall`), ends (`discardCall`) calls and
  sends the engine's data (`sendCallSignalingData`). TDLib does the Diffie-Hellman exchange and gives the key, the
  emojis and the servers in `callStateReady` (`CallConnection`).
- **Engine**: `:feature:calls`' `CallEngine` (`NtgCallsEngine`: NTgCalls, the AAR `pingogram.ntgcalls` installs) takes
  TDLib's key (`skipExchange`), the servers and the other side's library versions, and opens the microphone and the
  speaker through Oboe. Its blocking Java API runs on one thread of its own; its library loads with the first call.
  Video (`docs/adr/0019-video-and-group-calls-scope.md`): it lists the cameras, sends one (`CallSession.setCamera`,
  720p), reports the other side's video (`remoteVideo`) and hands both sides' frames, packed I420, to the
  `VideoFrameSink`s the call screen sets. It receives the other side's video only while there is such a sink.
- **`CallManager`** (`:feature:calls`, started by `PingogramApplication` through `Calls`, in the background just after
  TDLib, losing nothing: `CallRepository` keeps every call TDLib announced) is the call's state
  machine: one call at a time (another incoming one is declined), `ActiveCall` as its state (with `CallVideo`: the
  camera sent, front or back, and the other side's video; the camera goes off while held), the engine started once
  TDLib says ready (with the data the other side sent before kept per call), Telecom (`CallSystem`:
  `TelecomCallSystem` on Jetpack's `core-telecom`, `AudioManagerCall` without Telecom) for routes, hold, mute and
  answering from headsets, the foreground service (`CallService`: `phoneCall`, `microphone` once talking, the proximity
  wake lock at the earpiece) and the ringback and busy tones (`ToneGenerator`). Its parts are interfaces bound in
  `CallsModule`, so its tests run on the JVM. Every account's one-to-one calls (`docs/adr/0031-multiple-accounts.md`):
  `CallManager` runs `AllAccountsCallRepository` (`@AllAccounts`), every account's `CallRepository` as one, whose call
  ids carry the account's slot above TDLib's (`CallIds`), so a call to an account in the background rings, answers and
  ends in that account; new calls go out from the shown account. Its caller's name, picture and chat come from that
  account (`CallerNames`, `CallUsers`, `CallNotificationBuilder`, "Message"). Voice chats stay the shown account's.
- **Notifications**: `CallNotifications` posts `core:notifications`' `CallNotificationBuilder` (call style) as the call
  changes, with the service's id; "Decline" and "Hang up" go to `CallActionReceiver`, "Answer" and the full-screen
  intent to `CallActivity`. `NotificationRenderer` skips TDLib's call notification groups.
- **Call screen**: `CallActivity` (its own task, over the lock screen, not exported) with `CallViewModel` (the call,
  the other person, the timer; the microphone's permission step in the `SavedStateHandle`) and `CallScreen`. The call
  buttons (conversation, profile) and "Call back" reach it through `Calls.call(userId)`, which `:app` hands to the
  features as a callback (features do not depend on each other); `Calls.videoCall(userId)` calls with the camera on.
  `Calls` is `core:ui`'s `CallStarter` (`LocalCallStarter`, provided by `MainActivity`), whose `canVideoCall(userId)`
  (the device's camera, then `UserProfileRepository.fullInfo`'s `canBeCalled` and `supportsVideoCalls`) decides whether
  `core:ui`'s `CallButtonMenu`, the call button of the conversation's top bar and the profile's "Call" pill, offers
  "Video call": the one flow a composable collects there, once per screen, as the decision is the call module's and the
  screens' view models stay as they are.
  Video (`docs/adr/0019-video-and-group-calls-scope.md`):
  `CallScreen` takes the video as a slot (`CallVideo`, a placeholder in previews and goldens), which `CallActivity`
  fills with `CallVideoView` (WebRTC's `EglRenderer` in a `TextureView`); each view hands its renderer to the call as a
  `VideoFrameSink` while the screen is started (`CallViewModel.setVideoSink`), so a screen in the background draws and
  receives nothing. The camera's permission is asked when it is turned on (`CameraAccess`, `CallEffect`), and the call
  service adds the `camera` foreground type while the camera sends. "Use less data for calls" (`core:domain`'s
  `CallSettingsRepository`, `DataStoreCallSettingsRepository`, Data and storage's `CallDataSavingViewModel`) reaches
  both call managers through the network they follow (`DataSavingNetworkRepository`, `@CallNetwork`), which reads as
  held back by Data Saver where the setting applies, so `VideoQuality.forNetwork` sends at most 360p.
  A video call goes on in picture-in-picture when
  the user leaves it (`CallActivity`: auto-enter on Android 12+, `onUserLeaveHint` before, back and "Back to the app";
  "Hang up" as its `RemoteAction`), the screen showing only the video there. The most the camera sends
  (`VideoQuality`) follows `NetworkRepository`: 720p on Wi-Fi, 480p on mobile data and after the connection was lost
  once, 360p roaming or with Data Saver.
- **Voice chats and live streams** (`docs/adr/0023-group-voice-chats-and-live-streams.md`): `core:domain`'s
  `GroupCallRepository` (`TdlibGroupCallRepository`, `core:data`, a `TdlibUpdateCache`) keeps each chat's voice chat
  (`chat.video_chat`, `updateChatVideoChat`), the group calls (`updateGroupCall`, `getGroupCall` for one not announced)
  and the participants of the call the user is in (`updateGroupCallParticipant`; an empty `order` drops one), and wraps
  `createVideoChat`, `joinVideoChat`, `leaveGroupCall`, `endGroupCall`, the mute and hand toggles and a live stream's
  `getGroupCallStreams` / `getGroupCallStreamSegment`. The engine's group call (`CallEngine.createGroupCall`,
  `GroupCallSession`) makes the join payload, connects with TDLib's answer and asks for a stream's time and one-second
  segments (`StreamRequest`). **`GroupCallManager`** (`:feature:calls`) is its state machine beside `CallManager`
  (`ActiveGroupCall`: joining, connected, reconnecting, ended; muted, may unmute, hand raised, may end): it joins muted
  as the chat's default participant, tells TDLib when the user unmutes, follows an administrator's mute, leaves, ends
  the call for everyone, and answers a stream's requests from TDLib. It shares Telecom (`SystemCallRequest.voiceChat`,
  `pingogram:chat/<id>`) and the foreground service, and `CallLine` keeps one call at a time across both managers: an
  incoming one-to-one call during a voice chat is declined.
  Its screen is `GroupCallActivity` (in the call screen's task, not exported) with `GroupCallViewModel` (the voice chat,
  its chat through `GroupCallChats`, the participants named from `UserRepository`, the timer; the microphone's
  permission step in the `SavedStateHandle`) and `GroupCallScreen`, which shares the call screen's controls
  (`CallControls.kt`) and theme. `CallService` and `CallNotifications` follow whichever call is under way; the voice
  chat's notification (`CallNotificationBuilder.voiceChat`) has "Leave" (`CallActionReceiver`). A live stream's video
  goes to the screen's `CallVideoView` (`GroupCallManager.setVideoSink`) and on in picture-in-picture. The features
  reach voice chats through `core:ui`'s `VoiceChats` (`LocalVoiceChats`, provided by `:app` from `Calls`): the
  conversation's `VoiceChatBarSlot` at the top of its sheet and the info page's "Voice chat" pill.
  Video in voice chats (`docs/adr/0024-video-in-voice-chats.md`): where Telegram allows it (`groupCall.can_enable_video`),
  `GroupCallManager.setCameraOn` sends a camera through the engine's group session (`GroupCallSession.setCamera`: its
  capture sources again, as NTgCalls' join payload already carries the camera's track) and tells the others with
  `GroupCallRepository.setMyVideoEnabled` (`toggleGroupCallIsMyVideoEnabled`); the most it sends follows the network as
  in one-to-one calls (`VideoQuality.forNetwork`), none while held, and it stops when Telegram takes video back. The
  screen draws the self-view through `setSelfVideoSink`, and the call service adds the `camera` type while it sends.
  The others' videos: `GroupCallParticipant.video` (`video_info`: the endpoint, its SSRC groups, paused) picks at most
  four (`GroupCallManager.chooseVideos`: the pinned one, `setPinned`, then those speaking, then TDLib's order; none in
  a stream), which the engine subscribes to (`GroupCallSession.receiveVideos`, NTgCalls' `addIncomingVideo` /
  `removeIncomingVideo`) and routes by their frames' SSRC to the sink of each tile (`setParticipantVideoSink`, one
  `CallVideoView` per endpoint); the screen gets them as `ActiveGroupCall.video.remote`.
- **Screen sharing** (`docs/adr/0026-screen-sharing.md`): the call screen asks for Android's consent
  (`rememberScreenCaptureRequest`, `MediaProjectionManager.createScreenCaptureIntent`) and hands the result to the
  engine (`ScreenCaptureConsent`, NTgCalls' `JavaVideoCapturerModule`). `CallManager.startScreenSharing` has the call
  service run as `mediaProjection` first (`CallForeground.setScreenCapture`, which waits for it), then the session sends
  the screen in place of the camera (`CallSession.setScreen`: the stream sources' `screen` slot); hold pauses it,
  turning the camera on, Stop (the pill's or the notification's, `CallActionReceiver`) and the capture ending by itself
  (`screenEnded`, NTgCalls' `onStreamEnd`) stop it. The other side's screen comes as their video (`remoteIsScreen`),
  drawn fitted (`CallVideoSlot.RemoteScreen`, `CallVideoView`'s `fit`). In voice chats `GroupCallManager.startScreenSharing`
  opens the engine's presentation (`GroupCallSession.startPresentation`, NTgCalls' `initPresentation`), joins it with
  `GroupCallRepository.startScreenSharing` (`startGroupCallScreenSharing`) and connects it
  (`connectPresentation`), beside the camera; others' screens (`GroupCallParticipant.screen`,
  `screen_sharing_video_info`) are received as their cameras are, first among the videos (`chooseVideos`).
- **Conference calls** (`docs/adr/0025-conference-calls.md`): end-to-end encrypted group calls outside any chat.
  `core:domain`'s `ConferenceCallRepository` (`TdlibConferenceCallRepository`, `core:data`, a `TdlibUpdateCache`:
  `updateGroupCallVerificationState` and `updateGroupCallParticipants` come only as updates) joins one by its link
  (`joinGroupCall` with `inputGroupCallLink`), follows its emojis and the users of its blockchain, and encrypts and
  decrypts every frame (`encryptGroupCallData`, `decryptGroupCallData`, 100 ms at most, a late frame dropped). The
  engine is NTgCalls patched by `tools/ntgcalls/` (`CallEngine.createConference`): its `ExternalEncryption` hands each
  frame to `TdlibConferenceEncryption` on WebRTC's thread, which waits for TDLib, and asks whose an audio source is
  (`GroupCallSession.unknownSources`), which `GroupCallManager.joinConference` answers from the participants'
  `audioSourceId` (`mapSources`). Everything else is a voice chat's: `GroupCallManager` follows the call by the id
  TDLib answered, with chat 0, and Telecom names it "Group call". A `t.me/call/…` link (`LinkTarget.GroupCall`) opens
  the voice chat's screen through `core:ui`'s `VoiceChats.joinConference`, which asks before it joins
  (`GroupCallUiState.ConfirmConference`, the answer in the saved state) and shows the emojis to compare under the
  header. Upgrading a one-to-one call, inviting and invitations come later.
- **Rating a call**: TDLib's `callStateDiscarded.need_rating` reaches `CallManager` as `CallState.Discarded.needsRating`;
  for a call hung up after at least 5 s connected it holds a `CallRatingRequest` (`rating`), which outlives the ended
  call's 2 s on screen until the user rates (`CallRepository.rateCall`, `sendCallRating` with `inputCallDiscarded`, in
  the app's scope), declines, or another call starts. `CallViewModel` keeps the ended call on screen while it asks,
  `CallScreen` shows `CallRatingSheet` over it (never in picture-in-picture, which declines it). The engine's log
  (`sendCallLog`) is not sent: NTgCalls writes none Pingogram could hand over.
- **Recent calls**: `core:domain`'s `CallHistoryRepository` (`TdlibCallHistoryRepository`, `core:data`, no cache) pages
  the call service messages of every private chat (`searchCallMessages`, by TDLib's offset, all or only missed),
  deletes one for the user only (`deleteMessages` without `revoke`) and follows `updateNewMessage` with a `messageCall`
  and permanent `updateDeleteMessages` while collected. `feature:calls`' `RecentCallsRoute` (`RecentCallsViewModel`,
  `history/`, a destination of `:app`'s `NavHost`) lists them by day with the people from `UserRepository`, keeps the
  filter in its saved state, and calls back through `LocalCallStarter`, a video call for a video call. Settings home
  and the Contacts tab open it through `core:ui`'s `LocalRecentCallsLauncher`, which `PingogramApp` provides, as
  "Send feedback" is opened.

## Home screen widget

`docs/adr/0017-home-screen-widget.md`. Jetpack Glance, in `:app`'s `widget/` package; nothing polls.

- **Content**: `RecentChatsWidgetUpdater` (singleton, started by `PingogramApplication`) follows `AuthRepository`,
  `PasscodeRepository` and the first 10 chats of `ChatRepository.chatList(Main)` with `UserRepository`'s names, only
  while a widget is placed (`RecentChatsWidgets`: `GlanceAppWidgetManager` behind a seam for tests). It maps them into
  `RecentChatsContent` (loading, signed out, locked, the chats as `WidgetChat` rows with `AvatarIcons`' bitmaps at
  40 dp) and calls `updateAll` 1 s after the chats last changed. A process started for the widget loads the first
  chats itself (`loadChats`) if TDLib has fewer.
- **Drawing**: `RecentChatsWidget` (a `GlanceAppWidget`) collects the updater's `content` and the Appearance settings
  in `provideGlance`, and draws `RecentChatsWidgetContent` in `GlanceTheme` with the wallpaper's colors or the color
  theme's (`glance-material3`). `RecentChatsWidgetReceiver` tells the updater when the first widget comes and the last
  goes. Rows open their chat with `NotificationIntents.openChat`; "New chat" sends `tg://new` to `MainActivity`.
- Goldens draw the real `RemoteViews` (`GlanceRemoteViews`) in an `AppWidgetHostView`, the only parent Android fills a
  widget's list in.

## Chat export

`REQUIREMENTS.md` §5.9, `docs/adr/0021-chat-export.md`. Nothing is uploaded: only TDLib is asked, for the chat's
messages and their files, and the output goes only to the folder the user picked; logs hold no message text, names or
folder.

- **Entry**: the profile's "More" group (`feature:profile`'s `profileScreen(onExportChat)`, the private chat's id,
  Saved Messages on one's own profile, which the Saved Messages title opens) and the info page's last group
  (`ChatInfoPage.Export`), where the chat lets its content be saved. Both push `feature:chat`'s
  `ChatExportRoute(chatId)` (`chatExportScreen`, registered in `:app`'s `NavHost` and in the extra pane's).
- **The page** (`feature:chat` `export/`, `ChatExportViewModel`): `core:domain`'s `ChatExportRepository` says whether
  the chat may be exported (not secret chats, not chats with protected content); the form (format, media kinds, the
  size limit, the day to start from, the folder) lives in the `SavedStateHandle`. The folder comes from the Storage
  Access Framework's picker (`OpenDocumentTree`, no permission); `ExportFolderRepository`
  (`AndroidExportFolderRepository`, `core:data`) persists its grant and reads its name, and gives the grant back when
  another is picked or the page closes without exporting. "Export" hands the options to `ChatExportJobs`, whose job
  then holds the folder; the page follows it (`job(chatId)`) to its result.
- **The job**: `core:notifications`' `WorkManagerChatExportJobs` runs `ChatExportWorker` as unique work per chat
  (`chat_export_<id>`, expedited, kept while one runs) in WorkManager's `dataSync` foreground service, as the user's
  transfers run, with `ChatExportNotificationBuilder`'s notification in the "Transfers" channel ("Exporting Ada", the
  progress, "Cancel" through `WorkManager.createCancelPendingIntent`) and a note once it ended. Its options are its
  input data, its progress WorkManager's progress data, its result or error its output (`ChatExportInput.kt`). It
  waits for TDLib to log in after a restart, and gives the folder's grant back at the end.
- **The export**: `core:data`'s `DocumentChatExporter` (behind `core:domain`'s `ChatExporter`) reads the history
  through `TdlibChatExportRepository` (`getChatHistory`, `only_local` false, 100 a page, newest first, a `FLOOD_WAIT`
  waited out for the seconds asked; names with `getUser` / `getChat`, once per export; files with `downloadFile` at
  priority 1, synchronous, one at a time, cancelled in TDLib with the export), maps each message to `ExportMessage`
  (`core:domain`: service texts and summaries worded by `ResourceExportTexts`, `core:ui`'s previews), copies the
  chosen media into `media/` and writes each page to a temporary file in the cache (`ExportRecords.kt`). Then it
  writes `messages.html` (`HtmlExportWriter`: self-contained, no script) or `messages.json` (`JsonExportWriter`, the
  ADR's schema) from the last page to the first, each reversed, so it never holds more than a page. All writing goes
  through `DocumentTree` (`AndroidDocumentTree` on `DocumentsContract`, an in-memory folder in tests). Cancelled or
  failed, it deletes its folder and its temporary files.

## Feedback

`docs/TESTING_GUIDE.md`, `CLAUDE.md` rule 10: nothing leaves the device unless the user shares it.

- `core:data`'s `CrashRecorder`, made and installed by `PingogramApplication` before anything else (Hilt included),
  chains Android's default uncaught exception handler: it writes the last crash (time, thread, the exception and its
  causes with redacted messages, at most 40 frames each) to `filesDir/diagnostics/last_crash.txt`, then hands the crash
  on. TDLib logs its errors (verbosity 1) to its rotating file in release builds too (`docs/TDLIB.md`).
- `core:domain`'s `DiagnosticsRepository` (`AndroidDiagnosticsRepository`, `core:data`) reads them on the IO dispatcher
  into a `DiagnosticReport`: `AppInfo` (version, version code, flavor, build type), the device, the crash and the last
  200 lines of TDLib's log, every line through `Redaction` (TDLib's text and name fields, quoted strings, emails,
  tokens, numbers of 7 digits or more).
- `feature:feedback` (`FeedbackRoute`, pushed above Settings → About, whose row opens it through `core:ui`'s
  `LocalFeedbackLauncher`, which `PingogramApp` provides) shows the report with the user's description
  (`FeedbackViewModel`, the description in saved state), and on request shares its text (`ACTION_SEND`), copies it,
  or opens a new GitHub issue from `.github/ISSUE_TEMPLATE/bug_report.yml` with its fields in the address (at most
  8,000 characters, the log's oldest lines dropped first).

## Error handling

- `TdResult<T> = Success(value) | Failure(TdError)`; repositories convert to domain errors:
  `FloodWait(seconds)`, `Unauthorized`, `NotFound`, `Network`, `Unknown(code, msg)`.
- ViewModels map domain errors to user strings in `core:ui` (`ErrorText(error)`), never show
  raw TDLib messages except in debug builds.

## Build & modules

- `build-logic/convention` with plugins: `pingogram.android.application`,
  `pingogram.android.library`, `pingogram.android.feature`, `pingogram.android.compose`,
  `pingogram.android.hilt`, `pingogram.jvm.library`, `pingogram.android.lint`, `pingogram.ktlint`,
  `pingogram.android.licenses` (the open-source licenses list of `:app`), `pingogram.android.gms` (Firebase for
  `:app`'s `gms` flavor, the `foss` Google-free check),
  `pingogram.detekt`, `pingogram.tdlib` (the TDLib AAR for `:core:tdlib`, `docs/TDLIB.md`), `pingogram.ntgcalls` (the
  call engine's AAR for `:feature:calls`). `:app` applies Gradle Play Publisher only for the optional upload to Google
  Play, when the property `pingogram.play.serviceAccount` is given (`docs/PLAY_STORE.md` §7).
  `build-logic/detekt-rules` holds our detekt rules (`PingogramHardcodedToken`).
- `gradle/libs.versions.toml` is the only place versions live. Compose BOM pins Compose.
- `secrets.properties` (git-ignored) → `BuildConfig.TELEGRAM_API_ID` (int) /
  `TELEGRAM_API_HASH` (String), parsed and validated in `build-logic` (`TelegramSecrets`, applied
  by `pingogram.android.application`). CI writes the file from GitHub secrets. A missing file or
  key, a non-integer id or a hash that is not 32 hex characters fails configuration with a
  readable message that names the key but never its value.
- Open-source licenses (About, FR-S1): `:app` applies `pingogram.android.licenses`, whose task
  `generate<Variant>OpenSourceLicenses` lists each variant's runtime dependencies at build time with
  the licenses of their POMs (parent POMs included, resolved through Gradle's own dependency
  resolution while the configuration cache entry is stored), mapped to SPDX ids (`SpdxLicenses`),
  completed by `config/licenses/extra.json` (TDLib's license, Google Sans Flex, Material Symbols)
  and `config/licenses/texts/` (every license's text, never downloaded), into the generated raw
  resource `open_source_licenses`. A dependency without a license it can name, or a license without
  a text, fails the build. `:app`'s `GeneratedOpenSourceLicensesRepository` reads it with the
  platform's `org.json` behind `core:domain`'s `OpenSourceLicensesRepository`; `feature:settings`
  draws the screens (`docs/adr/0002-open-source-licenses.md`).
- Product flavors: `foss` (no Google services) and `gms` (FCM push). GitHub releases ship both, `foss` as the default
  download (`docs/RELEASE.md`). `gms` alone depends on Firebase Cloud Messaging (`gmsImplementation`); `foss` variants
  fail `check<Variant>GoogleFree` (run by `check` and by their builds) if any Firebase or Play services module reaches
  their runtime classpath. `pingogram.android.gms` applies the Google Services plugin only when the owner's
  git-ignored `app/src/gms/google-services.json` exists; without it, packaging a `gms` variant fails before anything
  runs (`docs/adr/0004-push-delivery.md`, `docs/OWNER_SETUP.md` §8b). Build types: `debug` (suffix `.debug`,
  debuggable), `release` (R8 full mode, resource shrinking, Baseline Profiles).

## Naming conventions

- Packages: `dev.kyralabs.pingogram.<module>.<layer>` e.g. `dev.kyralabs.pingogram.feature.chat.ui`,
  `dev.kyralabs.pingogram.core.data.repository`.
- Composables: `ChatListScreen`, `ChatListRoute`, `ChatListItem`; previews `ChatListScreenPreview`.
- ViewModel: `ChatListViewModel`; state `ChatListUiState`; events `ChatListEvent`; effects `ChatListEffect`.
- Repositories: interface `ChatRepository` in `core:domain`, impl `TdlibChatRepository` in `core:data`.
- Tests mirror source paths; test names `fun \`loads chats and applies position updates\`()`.

## Decision log (ADRs)

Add a file `docs/adr/NNNN-title.md` for each significant decision (TDLib vs fork, rlottie vs
Lottie, FCM flavor, etc.). Template: Context · Decision · Consequences.
