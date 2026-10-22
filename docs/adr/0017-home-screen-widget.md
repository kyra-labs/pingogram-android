# 0017 — Home screen widget: Glance, updated only when the chats change

Status: accepted (scope set by the owner's task of 2026-10-22: "Glance widget: recent chats, unread counts, no
polling, WorkManager updates only when needed"; `REQUIREMENTS.md` §5.9, `docs/ROADMAP.md` M6)

## Context

`REQUIREMENTS.md` §5.9 lists home screen widgets (Glance) for M6. A widget lives in the launcher's process: it is
`RemoteViews`, drawn from whatever the app last sent, and Android asks the app for new content only when the app says
so or on a timer (`updatePeriodMillis`). `docs/PERFORMANCE.md` §6 forbids polling and periodic work under 15 minutes,
and a timer would wake the phone for nothing most of the time. TDLib keeps the chats on the device and pushes every
change while the app's process runs (the app itself, "Keep connected" in `foss`, a push in `gms`). The widget shows
chats on the home screen, where anyone can see them, so the passcode lock (FR-S1) must keep them off it too.

## Decision

- **Jetpack Glance** (`androidx.glance:glance-appwidget` and `glance-material3` 1.2.0, first-party, no network code):
  the widget is written as composables, which Glance turns into `RemoteViews`, with its own components (`Scaffold`,
  `TitleBar`, `CircleIconButton`, `LazyColumn`). It is the widget toolkit `REQUIREMENTS.md` names; the alternative,
  hand-written `RemoteViews`, would need XML layouts, which `CLAUDE.md` rule 4 forbids. Glance runs each update in a
  WorkManager job of its own, which starts only when asked.
- **No timer** (`updatePeriodMillis="0"`). `RecentChatsWidgetUpdater` (`:app`, started by `PingogramApplication` like
  the share targets) follows the login, the passcode setting and the first 10 chats of the main chat list while the
  process runs, and only while a widget is placed (`GlanceAppWidgetManager.getGlanceIds` at start, then the
  receiver's `onEnabled` / `onDisabled` and every `provideGlance`). A change becomes the widget's content and one
  `updateAll` 1 s after the chats last changed. While no process runs, the launcher keeps showing the last content;
  the next process (opening the app, "Keep connected", a push) brings it up to date. The widget never starts TDLib or
  the process by itself, except when Android asks for its content (after a reboot or when it is placed), and then
  loads the first chats if TDLib has fewer, as the chat list would.
- **What it shows**: the chats in the chat list's order (pinned first), each with its avatar (`core:notifications`'
  `AvatarIcons`, the notifications' round photo or initials, 40 dp: every bitmap travels to the launcher, so the list
  stops at 10), title ("Saved Messages", "Deleted Account"), one line of the last message ("Alice: …" in groups) and
  the unread count or "@" for a mention, muted chats in the muted colors. No times: they would go stale without a
  timer. A secret chat shows "Secret chat" in place of its messages. Signed out it invites to sign in; with a
  passcode set it says Pingogram is locked and names no chat. A row opens its chat (the notifications' intent), the
  title bar's button New chat (`tg://new`, as the app shortcut), anything else the app.
- **Colors**: the wallpaper's (Glance's dynamic colors) where the app uses them on Android 12+, otherwise the color
  theme's light and dark schemes (`glance-material3`'s `ColorProviders`), following the system's dark theme: the
  launcher draws the widget, not the app. The launcher's font, as widgets cannot load Google Sans Flex.
- **Not now**: configuration (a folder or a single chat), a preview image in the widget picker before Android 15
  (`providePreview` / `setWidgetPreviews` would show a generated one from 15), and stories or times. Each can be added
  without changing the update model.

## Consequences

- Glance, with the widget, adds 0.7 MB to the arm64 release APK (27.4 MB → 28.1 MB, `benchmarkRelease` with R8,
  2026-10-22; much of it its own protobuf copy for session state), within `docs/PERFORMANCE.md`'s 35 MB budget, and
  runs WorkManager, which the app already uses, only while it draws.
- A widget can lag behind while Android keeps no process of Pingogram (`foss` without "Keep connected"); it is never
  wrong about what it shows, only late. `gms` pushes bring it up to date within the push's few seconds.
- The passcode lock keeps names and messages off the home screen, at the cost of an emptier widget for those who
  set one.
