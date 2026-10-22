# 0013 — Deep links: Telegram links through TDLib's getInternalLinkType

Status: accepted (scope decided by the coordinator, 2026-10-22)

## Context

`docs/ROADMAP.md` M4 asks Pingogram to open Telegram links (`tg://…`, `https://t.me/…`) itself: from other apps
(a browser, a mail, another messenger) and when the user taps one in a message, a bio or a description. Telegram has
dozens of kinds of links (usernames, phone numbers, messages, invitations, QR logins, proxies, sticker sets, bots,
payments …), in several spellings (`tg://resolve?domain=x`, `t.me/x`, `x.t.me`, `telegram.me/x`, `telegram.dog/x`),
and adds new ones in every TDLib release. Some of them change state (joining a chat, logging in another device) or are
dangerous to act on (a login code someone else asked for).

## Decision

- **TDLib reads every link.** `core:domain`'s `LinkRepository.resolve(link): Outcome<LinkTarget>` (`TdlibLinkRepository`)
  asks `getInternalLinkType`, then makes the lookup the kind needs, and returns a domain `LinkTarget`. It is the one
  entry point for links: the intents of `MainActivity`, the app's `LocalUriHandler` (every link tapped in the app), and
  later the QR scanner and direct share. Resolving only reads; nothing joins, logs in, confirms or sends. Pingogram
  parses nothing itself except to tell Telegram links from others (`isTelegramLink`: the `tg` scheme, or `http(s)` to
  `t.me`, `telegram.me`, `telegram.dog` and their subdomains), so that other links keep opening in the browser.
- **One dispatcher in `:app`.** `LinkViewModel` (activity scoped, one link at a time, a new one replacing the last)
  turns the target into a screen to open or a snackbar; `PingogramApp` navigates (`openLinkTarget`) and shows the
  snackbars in a host of its own. A lookup that takes longer than Material's 200 ms says "Opening link…" with "Cancel";
  there is no blocking dialog.
- **Intents.** `MainActivity` takes browsable `VIEW` intents for `tg:` and for `http`/`https` on `t.me`, `*.t.me`,
  `telegram.me` and `telegram.dog` (not a permission; pinned by `AppManifestTest`). It is `singleTask`, so a link from
  another app opens in Pingogram's own task, through `onNewIntent` when it runs, not in a second copy of the app inside
  the other app's task. The link opens once the account is logged in and the app unlocked (the lock screen holds it,
  as the app is not composed while locked). **Signed out, it waits** for the login and opens after it, in memory only:
  a link belongs to no account, unlike a notification's chat, which is dropped. An intent replayed from the recent apps
  (`FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY`) or after process death is not handled again.
- **No App Links verification.** Android 12+ opens `https` links in the browser unless the app is a verified App Link
  handler, which needs `/.well-known/assetlinks.json` on the domain; only Telegram can publish it for `t.me`. So the
  web filter says `autoVerify="false"` (Lint asks browsable web filters that are no App Links to say so), and on
  Android 12+ the user decides: Settings → About → "Open Telegram links" opens Android's "Open by default" page for
  Pingogram (`Settings.ACTION_APP_OPEN_BY_DEFAULT_SETTINGS`), where "Open supported links" lets it open them. Before Android 12, Android asks which app opens a link. `tg://` links work everywhere.
- **Scope** (`InternalLinkType*` classes of the pinned TDLib 1.8.68):
  - Opened: `PublicChat` (`searchPublicChat`: a person's profile; a bot's chat; a group's or channel's chat, or its
    info with `?profile` (`ChatInfoRoute`); `?text=` opens the chat with the text as
    its draft unless it has one, never sent), `UserPhoneNumber` (`searchUserByPhoneNumber`, not only local) and
    `UserToken` (`searchUserByToken`) like a person's username, `Message` (`getMessageLinkInfo`: the chat at the
    message, the chat alone if the message is gone, "This message isn't available" if the chat is not accessible; a
    topic or thread opens in the chat itself for now, as `ChatRoute` has no topic yet), `SavedMessages`,
    `MyProfilePage` and `Settings` → Edit profile (`SettingsSectionEditProfile` too), `Settings` → Settings, or its page
    for appearance, data and storage, devices (active sessions), language, notifications, privacy and security,
    `ContactsPage` → Contacts, `NewPrivateChat` / `NewGroupChat` / `NewChannelChat` → New chat, `Search` → search.
  - Resolved, screens added later (until then "Pingogram can't open this link yet"): `ChatInvite` (the join sheet, which
    asks before joining), `QrCodeAuthentication` (a confirmation that shows the device before
    `confirmQrCodeAuthentication`; the target keeps the link, which TDLib's type does not), `ChatBoost` (read only),
    `MessageDraft` (direct share's chat picker with the text in the composer).
  - Not opened ("Pingogram can't open this kind of link yet", and "Open in browser" for `https` links, which targets a
    browser through a `CATEGORY_APP_BROWSER` selector, or a chooser without Pingogram, so it never comes back here):
    `AuthenticationCode` (see Security), `Proxy`, `LanguagePack`, `Theme`, `Background`, `StickerSet`, bots
    (`BotStart`, `BotStartInGroup`, `BotAddToChannel`, `AttachmentMenuBot`, `WebApp`, `MainWebApp`, `Game`), `Invoice`,
    `PassportDataRequest`, `PremiumFeaturesPage`, `PremiumGiftCode`, `PremiumGiftPurchase`, `StarPurchase`, `TonConnect`,
    `TonWalletTransfer`, `GiftAuction`, `GiftCollection`, `UpgradedGift`, `Story`, `StoryAlbum`, `NewStory`, `LiveStory`,
    `VideoChat`, `GroupCall`, `CallsPage`, `BusinessChat`, `DirectMessagesChat`, `ChatAffiliateProgram`,
    `ChatSelection`, `ChatFolderInvite`, `InstantView`, `Oauth`, `RequestManagedBot`, `RestorePurchases`,
    `TextCompositionStyle`, `PhoneNumberConfirmation`, `UnknownDeepLink`, any kind a newer TDLib adds, and links TDLib
    refuses as not internal.
- **Security.** `AuthenticationCode` (`t.me/login/12345`) is never entered or acted upon: someone may be logging in to
  the user's account with it; the snackbar says not to share it. Everything that changes state waits for the user on
  the screen the target opens (joining, confirming a QR login, sending a shared text). A prefilled draft is the one
  write, and keeps a draft the chat has. `linkOf` checks an intent's data again, as any app can send an explicit
  intent. Targets and notices leave user content out of `toString()`.
- **Errors.** Nobody with the username, phone number or token, or a message the user cannot see, is a
  `LinkTarget.NotFound` with its own text ("No one has this username" …); no network and `FLOOD_WAIT` show the app's
  error texts; the lookups are made only for a link the user opened, once, never retried in a loop.
- **Telegram API terms.** Only the user's own taps and intents start lookups, one at a time; nothing is automated,
  scraped or joined without a confirmation, and the client identifies itself as before.

## Consequences

- `ChatInviteRoute`, the QR login confirmation, boosts and direct share add their screens by returning true from
  `LinkTarget.hasScreen()` and a branch in `openLinkTarget`; `LinkRepository` already gives them their targets.
- A chat's info opens `ChatInfoRoute(chatId)` (group and channel info, #129).
- `@username` mentions keep their own path (`ChatMentions`, the profile's mentions: `searchPublicChat`), as they need
  no `getInternalLinkType`; `t.me` and `tg:` links in the same texts go through `LinkRepository`.
- Link preview cards are not clickable yet; their link in the text is.
- On Android 12+, `t.me` links from other apps reach Pingogram only after the user turns on "Open supported links".
