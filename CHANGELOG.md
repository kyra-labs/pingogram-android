# Changelog

All notable changes to Pingogram are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and the project uses
[Semantic Versioning](https://semver.org/).

The release workflow copies the section for the tagged version into the GitHub Release notes,
so keep headings exactly in the form `## [X.Y.Z] - YYYY-MM-DD`.

## [Unreleased]

### Added
- Recent calls: Settings and the Contacts tab open a list of your calls, newest first and by day, all of them or only
  the missed ones, with who it was, whether it was a voice or a video call, incoming, outgoing, missed or declined, and
  how long it lasted. Tap a call to call back the same way; hold it to delete it from your call history (the other
  person keeps theirs).
- Voice chats: when a group or channel has a voice chat, a bar at the top of the conversation shows it with how many
  are in it, and "Join" opens it (so does "Voice chat" on the group's info page). You join muted; tap Mute to speak,
  and the others see you unmuted. Everyone in it is listed, with who is speaking, who is muted and who raised their
  hand; when an admin keeps you muted, "Raise hand" asks to speak. Leave from the screen or its notification.
  Administrators start a voice chat with "Start voice chat" on the info page and can end it for everyone. Who is
  speaking follows what Telegram reports and may lag a second or two.
- Live streams: a channel's live stream opens the same way and plays full screen; leave the screen and it goes on in
  picture-in-picture with Leave.
- Video call buttons: the call button in a conversation's top bar, and the "Call" button on a profile, now open a
  small menu with "Voice call" and "Video call" where the other person's app takes video calls and your phone has a
  camera; elsewhere a tap calls at once, as before. Calling back from a call in the chat keeps its kind: a missed
  video call calls back with your camera on.
- Delete account: Settings → Privacy and security → Delete account deletes your Telegram account, after your 2-step
  verification password where you set one and a confirmation, and signs out. The same page sets how long your account
  may stay unused before Telegram deletes it (1 to 24 months).
- Chat folders: Settings → Chat folders lists your folders as the tabs above your chats show them. Create a folder,
  rename it, give it an icon, choose the chats and kinds of chats (contacts, groups, channels, bots…) it shows and
  those it leaves out (muted, read, archived), or delete it. Drag a folder by its handle, or touch and hold it, to
  move its tab. Telegram's suggested folders can be added with one tap. Links to Telegram's folder settings open
  the page too.
- Shared folders: a folder link (`t.me/addlist/…`) shows the folder and its chats. Pick the chats you want to join
  and add the folder with one tap, or join the chats added to a folder you have. Nothing is joined until you tap.
- Stickers: Settings → Stickers lists your sticker sets in the order the sticker panel shows them. Drag a set by its
  handle, or touch and hold it, to move it; tap it to share its link, archive it or remove it, with "Undo" right
  after. Trending stickers shows the sets popular on Telegram and Archived stickers the ones you archived, each with
  "Add".
- Sticker set links: a sticker set's link (`t.me/addstickers/…`) shows its stickers, to add the set with one tap; the
  sets in Settings → Stickers open the same way.
- Android Auto (the Play services build): message notifications show in the car, are read aloud and take spoken
  replies; Wear OS watches show them with Reply and Mark as read, as before (`docs/ANDROID_AUTO.md`).
- Send with Ctrl+Enter: Settings → Appearance → Chats can make Ctrl+Enter send a message from a hardware keyboard, Enter
  then starting a new line.

### Changed
- The chat list does less work while it scrolls: rows no longer paint a background that matches the list behind them,
  avatars with a photo skip laying out the initials it covers, and rows set up their swipe feedback only when you
  swipe them.

## [0.5.0] - 2026-10-23

### Added
- Keyboard and mouse on tablets, Chromebooks and DeX: press Tab to move through the chat list and the conversation,
  and Enter on a chat to open it with the cursor in the message field; Esc goes back to the chat list without closing
  the chat. On a message, Enter, the Menu key, Shift+F10 or a right click open its actions, and Esc closes them.
  Ctrl+F searches the chat. In the media viewer, the arrow keys go to the previous and next photo or video, + and −
  zoom (or Ctrl and the mouse wheel), 0 fits the photo again, Space plays or pauses a video and Esc closes it; in
  stories, the arrow keys go back and forward, Space pauses and Esc closes. A sender's name and picture in groups now
  show when the mouse is over them or they have the keyboard's focus.
- Stories: the chats with stories show as rings at the top of the chat list, a coloured ring while there is something
  you haven't seen. Tap one to watch: photos stay for five seconds, videos play once (with a mute button), and the
  next story and the next chat's follow by themselves. Tap the right side for the next story, the left for the
  previous one, hold to pause, swipe sideways to another chat and down to close. You can react with an emoji, and
  reply privately to people's stories. Stories you watch are marked as seen, as in Telegram's apps; Pingogram has no
  way to watch them unseen, and doesn't post or save stories.
- Chat export: "Export chat" on a profile or a group's or channel's info page (Saved Messages: on your own profile)
  writes the chat's history into a folder you choose, as a page to read in any browser (HTML) or as data (JSON), with
  the photos, videos, voice messages and files you pick up to a size you set, and all messages or those from a day
  on. It goes on in the background with a notification you can cancel it from; a cancelled export leaves nothing
  behind. Nothing is uploaded. Secret chats and chats whose owner turned off saving can't be exported.
- Video in calls: turn your camera on during a call with the new Camera button, and see the other person's video
  full screen with your own picture in a corner, where you can switch between the front and back cameras. A video call
  says "Incoming video call" and is answered with your camera on once Pingogram may use it; Pingogram asks for the
  camera the first time you turn it on. Your video goes to the other person only, end-to-end encrypted. Leaving a
  video call keeps it going in picture-in-picture, with Hang up. On mobile data, when roaming or with Data Saver your
  camera sends a smaller picture, and so it does after the connection was lost once in the call. (Video call buttons
  in conversations and profiles come next; until then, turn the camera on during a call.)
- Custom emoji: messages show the custom emoji people send, animated ones playing, in place of the regular emoji they
  stand for, which shows until the picture is there and is what you copy or hear with a screen reader. A message of
  only one to three custom emoji shows them large; single-color ones take the text's color. Reactions show theirs too,
  and so does the text field when you edit a message or open a draft that has some. Pingogram loads only those on
  screen, and offers no custom emoji of its own to add.
- Message translation: hold a message and tap "Translate" to read it in Pingogram's language (or your phone's, while
  Pingogram follows it). Telegram translates it, nothing else sees it; the message shows the translation with
  "Translated from …" next to its time, and "Show original" brings its text back. "Translate" is offered for messages
  your phone finds in a language you don't read (your phone's languages count as read); the language is told on your
  phone, without sending the text anywhere. Where Telegram suggests translating a whole chat (Telegram Premium), a bar
  at the top offers "Translate to English": tap it to translate the messages on screen as you scroll, tap again for
  the originals, or close it for that chat.
- Search in a chat: the search button in a conversation's top bar searches its messages (a topic's or a thread's in
  those). The arrows below go through what it found, newest first, showing each message in the chat with the words
  you searched marked, and "3 of 17" says where you are. In groups you can search one member's messages, also
  without typing anything, and the calendar jumps to the first message of any day. Back closes search. In secret
  chats the encryption key now opens from the self-destruct timer's sheet, as the top bar holds search and the timer.
- A store listing for F-Droid clients and IzzyOnDroid (descriptions, icon, feature graphic and screenshots), and
  unsigned release builds for stores that sign builds themselves (docs/RELEASE.md).
- Bot keyboards: a bot's keyboard now shows in place of your phone's keyboard, with a button in the message field to
  switch between the two. Keyboards that are meant to be used once put themselves away after a tap, and keyboards a
  bot keeps on screen come back whenever your phone's keyboard closes. A button can send its text, share your phone
  number (after you agree), your location (through the usual location sheet), a new poll or quiz, people you pick or
  one of your groups or channels (Pingogram shows only those the bot allows and says what the bot gets). When a bot
  asks for a reply, the field starts replying to its message with the bot's hint. Mini App buttons say that Pingogram
  can't open them yet.
- Send feedback: Settings → About → "Send feedback or report a bug" prepares a report on your phone (the app's version,
  your phone and Android version, the last crash and the end of the connection log, with messages, names and phone
  numbers taken out) that you read first and then share with an app you choose or open as a GitHub issue. Pingogram
  sends nothing by itself.
- Bot buttons: the buttons bots put under their messages now show, in rows under the message, and change when the
  bot updates them. A button can answer with a short notice or an alert, open a link (Pingogram asks first when the
  button's text hides where a web link goes), log you in to a site with your Telegram account after you agree (and
  only lets the bot message you if you allow it), ask for your 2-step verification password, put an inline query
  ("@bot …") into this chat's message field or into a chat you pick, copy a text, or open a profile. A button
  waiting for its bot shows a small progress indicator. Mini Apps, games and payments are not supported yet, and
  Pingogram says so.
- Bot commands: in chats with bots, a "/" button at the start of the message field lists the bots' commands with
  what each does (in groups, which bot it belongs to, with search when there are many). Tap one to send it, or the
  arrow beside it to put it into the field and add something after it. Typing "/" suggests the matching commands
  above the field; on a keyboard, the arrow keys pick one, Enter sends it and Tab puts it into the field. The
  attachment button moves to the end of the field in these chats.
- Bots you have not used yet show what they can do, with their picture, and a "Start" button that starts them. Links
  that start a bot (`t.me/bot?start=…`) open its chat with "Start" ready; nothing is sent until you tap it.
- A bot's menu button appears at the start of the field. Bots whose menu opens a Mini App say that Pingogram can't
  open Mini Apps yet.
- Inline bots: type a bot's username and a space at the start of the message field, such as "@gif cats", and its
  results show above the field: GIFs, photos, stickers and videos in a grid, articles, places, songs and the like in
  a list, more loading as you scroll. Tap one to send it (replying, if you are), or hold it to send it without sound
  or schedule it; on a keyboard, the arrow keys pick one and Enter sends it. The bot's hint shows in the field until
  you type. Bots that ask where you are ask you first, once per bot until Pingogram restarts; "Not now" searches
  without your location. Games from inline bots can't be played yet.
- App shortcuts: long-press Pingogram's icon for New chat, Search and Saved Messages.
- Voice calls: Pingogram rings with your ringtone when someone calls, on the lock screen too, where you slide to answer
  or decline, or decline and write a message instead. During a call you can mute, switch between the phone, the speaker
  and a wired or Bluetooth headset, and put the call on hold; the screen turns off when you hold the phone to your ear.
  Four emojis show when the call is end-to-end encrypted: if the other person sees the same ones, nobody is listening
  in. Calls work with your headset's buttons, your car and your watch, and a phone call puts a Pingogram call on hold.
  Pingogram asks for the microphone before your first call, and on Android 12 and later for your Bluetooth headsets.
- Incoming calls now ring in their own "Calls" notification channel with your ringtone; the call you are in shows in
  "Ongoing calls".
- Color themes: Settings → Appearance → Theme lets you pick your wallpaper's colors, Pingogram's blue or one of twelve
  more (blue, teal, green, lime, yellow, orange, red, pink, purple, indigo, brown and monochrome), like Pixel's
  Wallpaper & style, with a preview of a chat and messages in the theme. Dark theme, AMOLED black and contrast work
  with every theme, and the splash screen follows it (Android 13+). It replaces the "Dynamic color" switch.
- A home screen widget of your recent chats with their unread counts: tap a chat to open it, or the pencil for a new
  chat. It updates when your chats change, never on a timer, and shows no chats while a passcode lock is set.
- Chat appearance (Settings → Appearance → Chat appearance): message text size from 85% to 130%, how round message
  bubbles are, and a wallpaper behind the messages, a tint or a gradient in your theme's colors, with a live preview.
  Every conversation shows them.
- Pingogram can be translated on Hosted Weblate (docs/TRANSLATING.md). A language appears in Settings → Language and
  in Android's app languages once 80% of the app is translated; the rest shows in English.

### Changed
- Tapping the title of Saved Messages opens your own profile, with its shared media and "Export chat".
- Pingogram reaches its chats sooner after a cold start: it prepares TDLib's database key while TDLib starts, loads
  its font before the first frame needs it, checks the network off the main thread, and redraws the home screen
  widget a few seconds after the start instead of while the chats load.
- The chat list and conversations prepare the rows just ahead while you scroll and keep those you just passed, so
  scrolling, and flinging back, has less to do in each frame.

### Fixed
- Unencrypted web traffic is now refused on Android 8 and 9 too, as later versions already did. Pingogram never
  used any; this makes sure no part of it can.
- The pinned messages bar counted its messages backwards. It now counts from the oldest, as the chat shows them, so
  the newest pinned message is "5 of 5" with its mark at the bottom, and the list of pinned messages starts with the
  oldest. The bar now also follows the chat as you scroll: it shows the pinned message just above what is on screen,
  so the newest one at the bottom of the chat and older ones as you scroll up. Tapping it still jumps to that message
  and moves on to the next older one, in forum topics and comment threads too.
- Accessibility (docs/audit/accessibility.md):
  - The navigation bar's tabs take taps over the bar's whole height.
  - Field errors after sign-in steps, 2-step verification and group editing are announced to screen readers.
  - Rows that copy, call, terminate a session or open shared media say so to TalkBack.
  - Each language in Settings → Language is read in its own voice and direction.
  - The calling codes in the country picker read "+44" in right-to-left languages.
  - At large font sizes, a profile's long "last seen" no longer hides its "Scam" or "Fake" label, page titles end in an
    ellipsis, and the contact form's choices wrap instead of being cut.
  - TalkBack's slider adjustment now saves the slow-mode delay it lands on.
  - The widget shows and reads muted chats and mentions, and its badge grows with the font size.

## [0.4.0] - 2026-10-22

### Added
- Very wide windows (1600 dp and more, e.g. a desktop or an external display): a profile or a group's or channel's
  info opens beside the conversation, as a third pane, instead of covering the chats. Drag the handle between them to
  make it narrower or wider; back closes it first.
- On tablets and other wide windows, a chat opened from a link, from search or from a profile's "Message" now opens
  beside the chat list instead of covering it.
- The conversation's avatar grows into the profile or info page it opens (unless animations are off).
- Share into Pingogram: text, links, photos, videos and files shared from other apps open a "Send to" sheet with your
  chats (Saved Messages first, and search). Pick one chat to open it with the text in the message field or the files
  ready to send, so you can check and add to it before tapping send; pick several to send it to all of them at once,
  with an optional comment. `t.me/share` links open the same sheet. Your most recent chats also appear directly in
  Android's share sheet (Android 11 and later). A share waits until you are signed in and the app is unlocked, and
  Pingogram needs no storage permission for it.
- Contacts: the Contacts tab lists your Telegram contacts alphabetically, with who is online and when the others were
  last seen, and a letter index along the edge of long lists to jump to a letter. Search filters the list as you type.
  Tap a contact to open your chat; long-press to edit or delete them. Add contacts by phone number or by username;
  if a number is not on Telegram yet, you can send its owner an invitation to Telegram through any app. "Invite
  friends" shares the same invitation. New chat's "New contact" opens Add contact too.
- Secret chats: end-to-end encrypted chats that live on this phone only show a lock before their name in the chat
  list and the conversation. While one waits for the other person to come online, the chat list and the conversation
  say so and the composer explains why you cannot write yet; an ended one says "Secret chat ended". Their start
  explains what a secret chat is, and Pingogram blocks screenshots and screen recordings while one is open, its
  photos and videos in the media viewer too.
- Self-destruct timer in secret chats: the timer button in the conversation's top bar picks how long after reading
  new messages delete themselves on both phones, from 1 second to 1 week, and shows the timer on a badge. Messages
  sent with a timer show it beside their time.
- Encryption key: the key button in a secret chat shows its key as an image and as 32 pairs of characters, to compare
  with the other person's screen, and can end the secret chat for both of you.
- Contacts sync, off unless you turn it on (on the Contacts tab or in Settings → Privacy and security): Pingogram
  explains first that the names and phone numbers of your phone's contacts go to Telegram, which finds who of them
  uses Telegram, then Android asks for access to your contacts. While it's on, the people it found are marked on the
  Contacts tab, and the rest are listed under "Invite to Telegram", each with an Invite button that opens your
  messaging app. Your contacts are read only when you turn it on, open Contacts (at most every few hours) or tap
  Sync now, never in the background. Turning it off deletes the imported numbers from Telegram and points you to
  Android's settings to take back the access.
- Profiles: tap the name at the top of a chat with someone, a sender's photo or name in a group, or a mention in a
  message to see their profile, with their photos, bio, username, phone number (if they share it) and birthday. From
  there you can message them, mute them, start a secret chat (after a short explanation of what that is), add them to
  your contacts or edit their contact, block or report them, and copy or share their username. Below, their shared
  media: photos and videos, files, links, voice messages, music and GIFs from your chat, each opening where it
  belongs. On tablets the shared media sit beside the profile.
- New groups and channels: New chat's "New group" lets you pick people from your contacts (search them, tap to pick,
  tap a chip to undo), then name the group, give it a photo and choose whether its messages delete themselves after a
  day, a week or a month. "New channel" asks for a name, a description and a photo, then whether the channel is
  private, with an invite link to copy or share, or public, with a t.me link checked as you type, and finally lets you
  add subscribers from your contacts or skip that. If someone's privacy settings keep Telegram from adding them,
  Pingogram tells you who and offers to share the invite link instead. The new chat opens once it is ready.
- Group and channel info: tap the name at the top of a group or channel to see its photo, name, whether it is public
  or private and how many members or subscribers it has, its description with links, and its public link or, for
  admins, its invite link, to copy, share or show as a QR code. Mute or leave it from there, clear its history, and,
  if you own it, delete it for everyone; each asks first. Below: the first members with their role (owner, admin or
  their tag) and the group's shared media. "See all members" lists every member, with search and, for admins, the
  admins, restricted and removed users.
- Group administration, as far as your admin rights allow: change or remove the group's or channel's photo (picked
  with Android's photo picker, without any permission, its upload's progress around it) and edit the name and
  description; choose what members may
  do and set slow mode; promote members to admins and choose their rights, restrict, remove or let back removed
  users, one person at a time; and manage invite links: create links with a name, an expiry, a member limit or admin
  approval, edit, revoke and delete them.
- Telegram links open in Pingogram: `tg://` links, and `t.me`, `telegram.me` and `telegram.dog` links from other apps
  or tapped in messages, bios and sponsored messages. A username opens the person's profile, or the chat of a bot,
  group or channel; a phone number or contact link the person's profile; a message link the chat at that message;
  links to Saved Messages, Contacts, Settings and its pages, Edit profile, New chat and search open those screens. A
  link with a suggested text puts it in the chat's composer, never sending it. Links Pingogram can't open yet say so,
  and web links among them offer "Open in browser". Pingogram never uses login codes from links. A link opened while
  you are signed out opens once you've logged in, and one opened while the app is locked after you unlock it. On
  Android 12 and later, Settings → About → "Open Telegram links" lets Pingogram open `t.me` links instead of the
  browser.
- Joining by invite link: an invite link (`t.me/+…`) opens a sheet with the group or channel's photo, name, description,
  how many are in it and a few members you may know. Nothing happens until you tap "Join"; where admins accept new
  members, "Request to join" sends a request and says so. A link that expired says so; if you're in the chat already,
  it opens at once. Public groups and channels you're not in show "Join" in place of the composer, and search finds
  them by a typed `@username` or `t.me/` link too.
- Scan QR codes: the Contacts tab's QR button opens a scanner that reads Telegram QR codes with the camera, or from an
  image you pick. Pingogram asks for the camera only when you open the scanner, after saying why, and only uses it
  there; codes are read on your phone, and nothing the camera sees leaves it. A code that isn't a Telegram link shows
  its text, which you can copy. Phones without a camera can still scan from an image.
- Link desktop device: Settings → Privacy and security → Active sessions → "Link desktop device" scans the QR code
  Telegram shows on your computer. Pingogram asks before logging the other device in, also for a login code opened
  from another app, then shows the new session; an expired code says to show a new one.
- Your QR code: the Contacts tab's other QR button shows a QR code of your profile link with your photo and name, to
  share or to let others scan. Without a username, it's a temporary link and says how long it works.
- Boosts: admins of a channel or supergroup see its boost level, its boosts and how many more the next level needs in
  its info page; boost links open the same page. Pingogram shows boosts but doesn't give them.
- Pinned messages bar: a chat with pinned messages shows the pinned one at the top of the conversation, with "2 of 5"
  where there are several. Tap it to jump to the message; each tap then moves on to the next older pin. The list button
  shows them all, and the close button unpins the message if you may, or hides the bar until something new is pinned.
- Forum topics: groups with topics open their list of topics, General first, then pinned ones and the most recent,
  each with its icon, its last message, the time and its unread messages and mentions; closed topics show a lock.
  Opening a topic shows its messages, and what you send goes there; closed topics say that only admins can write.
  On tablets the topic opens in place of the list beside the chats, and Back returns to the topics.
- Comments: channel posts show "12 comments" (or "Leave a comment") under them where the channel has a discussion
  group, and group messages show "3 replies" once someone replied in their thread. Tap it to read the thread, the post
  first, and to write into it; where the group lets anyone comment, you can without joining.

### Changed
- A more compact look, closer to Google's own apps: the navigation bar at the bottom and the search button beside it
  are as tall as Google Photos', the "New chat" button is as large as Gmail's Compose, and the top bars and folder tabs
  of the chat list, the archive and Contacts are lower, so more chats fit on the screen.
- The message bar is now like Google Messages': a rounded field with "+" at its start and the emoji and gallery
  buttons at its end, beside one round button that records voice messages or sends. Hold the send button to send
  without sound or to schedule the message.
- Photos, videos and albums in a conversation are wider on phones, so their captions take fewer lines.
- Pingogram starts faster right after installing, about a quarter faster from cold: it now ships a Baseline Profile, so
  Android compiles the code used at start ahead of time instead of while you use it.

### Fixed
- Coming back to Pingogram after a while in another app no longer leaves it "Connecting…" until it is closed from the
  recent apps: it now tells Telegram's library when it is in use, which then reconnects at once.
- Swiping a photo down to close it sometimes closed the chat too; it now closes only the photo.
- A photo opened from a chat shows the picture the chat already loaded at once, instead of loading it again.
- The "New chat" button no longer stutters as it grows into New chat or shrinks while the list scrolls.

## [0.3.0] - 2026-10-21

### Added
- Releases now include a second set of APKs with "-gms" in their names, for phones with Google Play services: they get
  notifications through Google's push service even when Pingogram is closed, without "Keep connected". The plain APKs
  stay free of Google services. Both are the same app, so installing one replaces the other and keeps your data.
- Swipe to reply: swipe a message toward the other side of the screen (to the right, or to the left in right-to-left
  languages) and let go once the reply icon pops to start a reply to it, with a short vibration as you pass the point.
  A short swipe just springs back. Messages still being sent, or chats you cannot write in, do not reply.
- Copy part of a message: long-press a message, then long-press its text in the lifted message to select words, and
  copy them from the menu that appears.
- Forward from the media viewer: the toolbar starts with Forward wherever Telegram lets the photo, video or GIF be
  forwarded (never for protected content). It opens the same forward sheet as the conversation, over the viewer, and
  after forwarding to one chat the snackbar there offers to open it.

### Changed
- On Android 16, the progress of files you send or download shows as a Live Update in the status bar and on the lock
  screen while Pingogram is in the background. Android's settings can turn it off for Pingogram.
- License: Pingogram stays GPL-3.0-or-later, with an additional permission to combine it with Google's Play services
  and Firebase libraries, which the "-gms" builds need (`LICENSE-EXCEPTION.md`).
- Settings on tablets and other wide windows shows its pages beside the list of settings, as the chats do: the page
  you pick opens beside them, the pages it leads to open in its place, and back returns through them. Phones keep
  opening each page full screen.
- Settings pages have the large title of Settings itself, which shrinks into the top bar as you scroll, and line up
  with their content on wide windows; on tablets and unfolded phones their content keeps a wider margin from the edges.
- Long-pressing a message now lifts it out of the conversation, which blurs behind it (on Android 12 and later), with
  its quick reactions above it and its actions in a menu right below it, as in Google Messages; if the message is too
  close to an edge, everything slides into view. Tap outside or go back to close it. With TalkBack, and in windows too
  short for the menu (phones in landscape), the actions still open in the sheet from the bottom.
- Settings look more like Android 16's: each row's icon sits in a small colored circle, one color per group; switches
  show a check in their knob when on, and buzz lightly as they turn on and off; the theme and contrast choices are a
  row of joined buttons that tick as you choose. Edit profile's fields sit directly on the page as rounded fields,
  without a box around each. With AMOLED black on, settings pages are black and their rows dark grey, rather than the
  other way round. Blocked users have an "Unblock" button instead of an X, and their phone numbers are formatted, and
  names, usernames and devices from other languages read in their own direction. The switches of a new poll and of
  forwarding show the same check.
- The message composer is slimmer and looks like Google Messages': the field is a filled, rounded pill with the emoji
  button inside, "+" in a tonal circle attaches, and sending is a small icon button with its menu (without sound, at
  a time) beside it, so the field keeps most of the width even with large text. The bar is lower, the voice recording
  bar matches the field, and on tablets the composer is no wider than a message can be. Text written in a
  right-to-left language now lines up to the right as you type it.

### Fixed
- Names in Hebrew or Arabic no longer jumble the sentences around them, and English names no longer do in a
  right-to-left language: "… is typing", "Forwarded from …", group events such as "… joined the group", the sender
  before a chat's last message, "Forwarded to …" and the delete dialogs keep their words in order. Phone numbers and
  email addresses in the sign-in steps stay left to right. Settings too: "… unblocked" in Blocked users, the devices
  and places in Active sessions, the recovery email and password hint of 2-step verification, and usernames in Edit
  profile.
- The reply bar above the composer, chat names in the forward and "Send messages as" sheets and file names before
  sending now read in their own direction, like messages; a location's coordinates read left to right before you send
  it.

## [0.3.0-beta.3] - 2026-10-21

### Added
- Send as a channel: in groups and channels where you can post as a channel you own or anonymously, the composer
  starts with the avatar of who your messages go out as. Tap it to choose your account, one of your channels or
  "Anonymous admin"; the choice is kept for the chat. Channels that only Telegram Premium users can post as are shown
  but cannot be chosen.
- Emoji and stickers: the smiley at the start of the message field opens a panel in the keyboard's place, at the
  keyboard's height, so nothing jumps when you switch back and forth. The Emoji tab has every emoji your phone can
  show, by category, with the ones you used last first; tapping one puts it where the cursor is, and the panel's
  backspace deletes a whole emoji at a time. The Stickers tab has your favorite and recently used stickers and every
  sticker set you added, each a tap away in the row at the top; tapping a sticker sends it right away, as a reply if you
  were replying. Back or the keyboard button brings the keyboard back.
- Animated stickers play, in chats and in the sticker panel, once they are downloaded. To save battery, at most eight
  play at once, only while they are on screen and Pingogram is in front, and not at all when animations are turned off
  in Android's settings. Video stickers still show a still picture.
- Files you send and files you tap to download keep going when you leave Pingogram: while they are on their way, a quiet
  notification in the new "Transfers" channel shows how many files and how much of them, with "Cancel", and it goes
  away a moment after the last one. Files you save from a selection count too. Photos and videos that download by
  themselves never keep Pingogram running in the background.
- Language: Settings → Language chooses Pingogram's language apart from your phone's, or follows the phone's (the
  default). Pingogram ships in English for now; the page lists every language once translations arrive. On Android 13
  and later the choice is the same as in Android's own app language settings, which the page also opens; on Android
  8 to 12 Pingogram keeps it itself.
- Two-step verification in Settings → Privacy and security: set a password with a hint and a recovery email (confirmed
  with the code Telegram sends there), change it, turn it off, or change the recovery email, each after entering the
  current password. A forgotten password is reset with a code sent to the recovery email, or, without one, after
  Telegram's waiting period of several days, which the page shows with when it ends and lets you cancel. A recovery
  email still waiting for its code can be confirmed or dropped later. Wrong passwords and codes show at the field,
  and when Telegram asks to wait, a countdown says how long.
- Send your location: "Location" in the attach menu finds where you are and shows the coordinates and how accurate
  they are, improving while you look, with "Open in maps" to check them in your maps app. Send it now, without sound or
  at a time. Pingogram explains why before Android asks for the location, uses it only while the sheet is open, never
  in the background, and works without Google Play services. If you allow only an approximate location, that is what
  is sent, and the sheet offers to ask for the precise one; if location is turned off, a snackbar takes you to the
  setting.
- Automatic download: Settings → Data and storage now says what downloads by itself on mobile data, on Wi-Fi and while
  roaming, and each opens a page with switches for photos, videos and GIFs, files, and voice and video messages, with a
  size limit for videos and for files. By default Wi-Fi downloads photos, videos up to 10 MB, files up to 3 MB and voice
  and video messages; mobile data downloads photos; roaming downloads nothing. Conversations follow the network you
  are on: a photo that doesn't download by itself shows a download button, and other media downloads when you tap it,
  as before. While Android's Data Saver is on, nothing downloads by itself on mobile data. Stickers, profile photos and
  previews always download.
- Select messages: "Select" in a message's actions starts selection mode. Tap or long-press other messages to select
  or deselect them (an album as a whole); selected messages turn a different color and show a check. The top bar
  shows how many are selected, and a toolbar in place of the composer copies their texts (several at once with who
  sent them and when, as the official apps do), forwards them, reports them or deletes them, offering only what
  Telegram allows for every selected message. Back or the close button leaves selection mode, which also survives
  turning the phone. With TalkBack, each message says whether it is selected and can be selected from its actions.
- Report messages: "Report" in a message's actions (and in the selection toolbar) where Telegram allows it asks the
  reasons Telegram offers, and a comment where it wants one, then thanks you once the report is sent.
- Save to Downloads: documents, music, videos, GIFs and photos can be saved to your phone's Downloads folder (in a
  Pingogram folder) from a message's actions, where the chat allows saving. A file that isn't on the phone yet
  downloads first. A snackbar confirms it, with a button that opens the file. On Android 8 and 9 Pingogram asks for
  storage access first.
- Notifications for new messages, mentions, new secret chats and calls, while Pingogram is running. Each chat gets one
  notification that shows its latest messages with who sent them and their photos; media show as "Photo", "Voice
  message" and so on, and chats whose previews you turned off show "New message". Muted chats and exceptions work as
  in your other apps, and a notification goes away once you read the chat anywhere. Only new messages make a sound:
  updates, messages sent silently and the chat you are looking at stay quiet.
- Reply right from a notification, or mark the chat as read; tap a notification to open its chat.
- Notification channels for private chats, groups, channels, calls and everything else, so you can set their sound
  and importance in your device's settings. On Android 11 and later, chats show in the conversations section of the
  notification shade, where you can make one a priority conversation.
- On Android 13 and later, the chat list explains why Pingogram asks to show notifications before it asks. Choose
  "Not now" and it won't ask again; if you refused before, it offers your device's notification settings instead.
- Settings → Notifications: turn notifications and message previews on or off for private chats, groups and
  channels, choose whether mentions and replies notify in muted groups and whether pinned messages notify, and open
  each kind's sound and vibration in your device's settings. These settings belong to your Telegram account, so they
  apply on your other devices too. Exceptions lists the chats with settings of their own and whether they are muted
  (and until when); tap one to mute it for an hour, 8 hours, a day or for good, unmute it or let it follow the default
  again. "Reset all notification settings" restores everything after asking. If your device blocks Pingogram's
  notifications, the page says so at the top and helps you turn them back on.
- "Keep connected" (GitHub and F-Droid builds, off by default): keeps Pingogram connected in the background, so new
  messages notify at once, at the cost of some battery. A quiet notification shows while it is on, with "Turn off".
  It starts again after your device restarts, but only if you turned it on, and stops when you log out.
- Sponsored messages: channels and chats with bots show Telegram's sponsored messages as a card labelled
  "Sponsored" (or "Recommended" for a recommended channel) below the newest posts, with the advertiser's button.
  Tap the button, a link or the picture to open it. The info button shows "About this ad": who placed it and more
  about it, and lets you report it, with the reasons Telegram asks for.
- A reactions picker: the + button at the end of a message's quick reactions shows every reaction you can add in
  that chat, the chat's most used ones first, then your recent ones and the popular ones, with those you added
  marked. Tap one to add it, or tap it again to take it back. The picker also shows every reaction already on the
  message with how often it was added. Reactions that need Telegram Premium only show if you have it. Custom emoji
  reactions show as a still picture where Telegram has one.
- Open-source licenses: About now lists the libraries, fonts and icons Pingogram is built with, each with its
  version and license. Tap one to read the full license text, which you can select and copy, and to open its
  website. The list is made from the app's own build, so it always matches what you installed, and it works
  offline.
- Push notifications for builds with Google Play services (the "gms" build, not yet published): new messages arrive
  through Firebase Cloud Messaging even when Pingogram is closed. Telegram encrypts each push for your device, so
  Google only sees that something arrived. The GitHub releases stay free of Google's services.
- Forward messages: "Forward" in a message's actions (where Telegram allows it; protected content can't be forwarded)
  opens a sheet with your chats, Saved Messages first, and a search. Pick one or more chats you can write in, add a
  comment if you like, and send. "Hide sender's name" sends copies without "Forwarded from", and for photos, videos
  or files with a caption "Hide captions" leaves the captions out. An album is forwarded whole. A snackbar confirms
  it, and when you forwarded to one other chat, opens that chat.
- A "New chat" button on the chat list. It shrinks to an icon while you scroll down and grows back when you scroll
  up, and opens a New chat screen that grows out of it, with your contacts to start a conversation with. New group,
  New channel and New contact are listed there and come in a later version. An empty chat list offers the same
  button.
- More you can do with a photo or video you are viewing: delete it (where Telegram lets you, for yourself or for
  everyone, after asking), jump to it in its chat with "Show in chat", and copy a photo to paste it into another
  app. Copying and sharing stay off in chats that don't allow saving their content.
- Passcode lock: Privacy and security → Passcode lock asks for a PIN of 4 to 8 digits, or a password, whenever you
  come back to Pingogram: immediately, or after 1 minute, 5 minutes or an hour in the background, and always after a
  restart. On Android 10 and later you can unlock with your fingerprint or face instead. While locked, nothing of your
  chats shows, screenshots are blocked, and a notification you tap opens its chat only after you unlock. "Hide content
  in recent apps" keeps your chats out of the recent apps screen (before Android 13 this also blocks screenshots).
  After five wrong tries the lock makes you wait, longer each time. If you forget the passcode, you can log out to
  remove it. Changing or turning off the passcode asks for it first.
- Search filters: chips under the search field narrow the results to chats, contacts, messages, media (photos and
  videos) or links. Media and Links also work without typing anything, showing every photo and video or every link
  in your chats.
- On tablets and other wide screens, search opens in the chat list's place next to the conversation, and a chat or
  message you find opens beside it. Back closes that conversation first, then search.
- Voice messages: while the message field is empty, a microphone button sits where the send button goes. Hold it to
  record and release to send; slide toward the start to cancel, or slide up to keep recording hands-free, then send,
  delete or pause and go on. The recording shows how long it is and a live waveform of your voice, and the message
  replies to the message you are replying to. Recordings shorter than a second aren't sent. With a screen reader or
  a keyboard, the button records hands-free right away. Pingogram asks for the microphone the first time you hold the
  button and explains why first; if you refused, it offers your device's settings. Leaving the app or turning the
  phone pauses a recording, which waits for you to go on, send or delete it.

### Changed
- Pingogram now shows every screen in its own languages only: built-in texts of the libraries it uses (the date
  picker, for example) no longer switch to your phone's language when Pingogram has no translation for it, and the
  download is a little smaller.
- Search looks like the rest of the app: results come in rounded groups under their headings, one wavy progress line
  under the search field replaces the loading animations in every section, a section that failed shows its error in
  a red box with "Try again", and the search screen grows out of the search button and shrinks back into it.
- The login screens are clearer and fit wide and landscape screens: each step starts with a large title under a
  shaped icon, which turns into a warning sign when something needs fixing or Telegram asks you to wait. "Next" is a
  normal-sized button at the bottom right, with the other choice ("Log in with QR code", "Wrong number?", "Forgot
  password?") at the bottom left. On tablets and on phones held sideways the title sits beside the form, so "Next"
  is always on screen without scrolling.
- Logging in feels more like a Google app: the country is a field with a drop-down arrow like the number fields next
  to it, and the country list has a proper search bar and highlights the country you have. Codes show larger and
  centered, with digits evenly spaced, and codes sent by email can be filled in by your password manager or
  keyboard, like SMS codes. On Android 14 and later the phone gives a short confirming buzz when Telegram accepts a
  code or password, and a different one when it's wrong. The offline and "Too many attempts" banners have an icon,
  and the wait before trying again or getting a new code shows as a shrinking ring next to the time. Swiping back
  slides in the previous step as you swipe, so you can see where you'll land before letting go.
- The chat list is denser, closer to Google's own apps: a slimmer top bar that no longer collapses, smaller avatars,
  a one-line preview of the last message, and a lower row of folder tabs. About nine chats fit on a typical phone
  instead of six or seven.
- Selected chats get rounded corners instead of turning into pills, and the buttons of the selection toolbar show
  their name on a long press.
- Folder tabs that don't fit scroll the selected folder into view and fade out at the edge, so no tab is cut off
  without a hint. A chat being swiped keeps its rounded shape as it slides.
- The "+N" badge under a message with many reactions now opens the reactions picker with all of them, instead of
  the message's actions. TalkBack offers the same as "All reactions".
- Chats without a photo show their initials in a circle in one of four calm colors, instead of six different shapes
  in seven colors, some of which stayed bright in dark theme. The chat open beside the list on a tablet keeps its
  avatar visible against the highlighted row.
- The app's own colors (when dynamic color is off or unavailable) are a more vivid blue, closer to the icon.
- Loading indicators show only when loading takes longer than a moment, so screens that load quickly no longer
  flash one.
- Opening a chat, the archive, search or a settings page now slides the new screen in from the side, and going
  back slides it out the same way, instead of fading. Switching tabs still fades.
- Phones in landscape show the navigation rail at the side instead of the floating bar, leaving more room for
  chats. On large tablets and desktop windows the rail is expanded, with each tab's name and a labelled Search
  button.
- On tablets and other wide windows, the chat list and the open chat each sit in their own rounded panel, and a
  handle between them resizes the list. With no chat open, the empty panel lines up with the list.
- Tap the Chats tab while you're on it to jump back to the top of the chat list. The highlight behind the selected
  tab in the floating bar now grows out from its middle.
- The Contacts tab has a title bar and a rounded panel like the Chats tab, says plainly that contacts are coming in a
  later version, and offers "New chat", which lists your contacts in the meantime.
- Empty and error screens feel calmer: the shape behind the picture turns gently now and then (it stays still when
  animations are turned off), and errors show a soft rounded shape instead of a spiky one.
- With very large text, the chat list shows each chat's time at the start of the message preview instead of beside
  the name, so long names are no longer cut short.
- On tablets and other wide windows, the chat list's title, connection status and folder tabs line up with the chats
  below them.
- Messages are tidier, as in Google Messages: a run of messages from one person shows the time only under the last
  one. Tap any message to see its time, and tap it again to hide it. TalkBack still reads every message's time.
- Message text sits a little closer to the sides of its bubble, so more fits on a line.
- Replies and link previews inside a message are rounded cards without the colored bar at their side.
- A message that could not be sent says "Not sent · Tap to retry" under it in red. Tap it to send the message again.
  The phone also vibrates briefly when a message fails to send while the chat is open (Android 14 and later). In
  channels and Saved Messages, messages being sent and failed ones now show it too.
- Reactions under a message are easier to tap: each reacts to touches a bit around it, without looking bigger.
- The media viewer's actions now sit in a floating toolbar at the bottom, each button named when you press and hold
  it, with further actions under "More options". On tablets the caption and the video controls no longer stretch
  across the whole screen.
- In a chat, the name at the top is set a little bolder, and while someone is typing three dots pulse before "is
  typing…". TalkBack announces when someone starts typing. The dots stay still when animations are turned off.
- Code, code blocks and hidden spoilers in messages sit on rounded backgrounds instead of square ones, and a hidden
  spoiler is a softer tinted block.
- The button that jumps to the newest message is the regular size of a floating button, with the number of unread
  messages on its corner.
- In dark theme, photos, videos and GIFs in a chat are slightly dimmed so they don't glare; the media viewer still
  shows them at full brightness.
- Custom emoji reactions that haven't loaded show a soft placeholder instead of a dark dot, and stickers without an
  emoji show a rounded placeholder while they load.
- The launch screen matches your theme: its background follows your wallpaper colors on Android 12 and later, and on
  Android 13 and later also your Appearance choices (light or dark, wallpaper or Pingogram colors, AMOLED black) from
  the next start on. It no longer cuts away: it fades out as its icon grows.
- With a keyboard, the focused button or row shows a ring instead of a faint tint, which is easier to follow on
  tablets and Chromebooks.
- Turning animations off in Android's settings now applies while Pingogram is open, and shapes that change, such as a
  selected chat's avatar turning into a check, switch at once instead of morphing.
- More buttons change shape while you press them: back and close buttons in top bars, the media viewer's buttons,
  dialog buttons and "Try again" buttons.
- With high contrast (in Appearance, or Android's contrast setting on Android 14 and later), text is slightly bolder:
  its strokes are thicker while it keeps its width, so nothing moves.

### Fixed
- Closing the media viewer no longer makes the photo's corners jump at the end: it shrinks back into exactly the
  shape it has in its message, including the square edge above a caption and the corners of an album.
- In right-to-left languages, the phone number at login reads left to right, the calling code first ("+44 7911
  123456"), instead of showing its digit groups and the "+" reversed.
- Messages, captions, chat names, previews, link previews, sponsored messages and times read the way they are written
  in any language: English in a right-to-left language keeps its punctuation at its end ("Thanks!", "9:41 AM",
  "@ada") instead of moving it to the start, and Hebrew or Arabic messages read right to left in an English layout. A
  shared contact's phone number and a location's coordinates read left to right.

## [0.3.0-beta.2] - 2026-10-19

### Added
- Pingogram has its own icon, with a themed version that follows your wallpaper's colors on Android 13 and later. It
  also shows on the splash screen, on the Welcome screen and, with the full logo, at the top of About.

### Changed
- The app's own colors, used when dynamic color is off or unavailable (before Android 12), are now built from the
  icon's blue instead of a placeholder pink.

### Fixed
- "Wallpaper colors" now follow the wallpaper on phones that publish its colors only the older way, such as Samsung
  phones with One UI, where the app showed the system's stock blue instead.

## [0.3.0-beta.1] - 2026-10-19

### Added
- You can write and send messages. Format selected text as bold, italic, underlined, strikethrough, monospace,
  spoiler or a link (from the selection menu, or Ctrl+B, I, U and K on a keyboard). Send without sound, or
  schedule a message for later. Unsent text is kept as a draft that syncs with your other devices. Where you
  can't write in a chat, a short note says why; channels you only read show no composer.
- Message actions: long-press a message (or use TalkBack's actions) for quick reactions, reply, copy text, edit,
  pin or unpin (in private chats also only for yourself), delete for you or for everyone, and send a failed
  message again, each only where Telegram allows it. A reply shows a bar above the composer and is kept in the
  chat's draft on all your devices; editing puts the message's text or caption, with its formatting, back in the
  composer. Tap a reaction under a message to add or remove yours.
- Attach photos, videos, files, contacts and polls: the attach button beside the message field opens Gallery (up to
  10 at once), Camera, File, Contact and, in groups, channels, bots and Saved Messages, Poll. Picked items show in a
  preview first, where you can remove some, add a formatted caption, and send now, without sound or later. Photos are
  sent upright, at most 2560 px and without location data; photos and videos go as albums of up to 10, files as
  albums of files. Polls can be anonymous, allow several answers, or be a quiz with one correct answer. A photo, video
  or file shows its upload progress in the chat; tap X (or TalkBack's "Cancel upload") to stop it.
- Settings shows your account at the top (photo, name, phone number and username) and has new Appearance, Data
  and storage, and About pages; Log out moved to the end.
- Appearance: a System, Light or Dark theme, AMOLED black for the dark theme, dynamic color from your wallpaper
  (Android 12+) and contrast (on Android 14+ it follows your device's setting). Changes apply at once with a
  smooth color fade, and the app always opens in the theme you chose.
- Data and storage: how much space downloaded files and the database use, and "Clear cache" to remove downloaded
  photos, videos, voice messages, music and files. Your chats and secret chats are not affected; files download
  again when you open them.
- About: the app version, a link to the source code, the license and a note that Pingogram is an unofficial
  Telegram client.
- Edit your profile from Settings: set a new profile photo from the photo picker (with upload progress) or remove
  it, and change your first and last name, your username and your bio. Usernames are checked as you type
  (available, taken or for sale on Fragment), Save applies only what changed, and leaving with unsaved changes
  asks first.
- Privacy and security in Settings: choose who can see your phone number, last seen and online status and
  profile photos, whose forwards of your messages link to your account, and who can call you, add you to groups
  and channels or send you voice and video messages (limiting the last needs Telegram Premium). Exceptions set
  in other apps are shown and kept when you change a choice.
- Blocked users: everyone you blocked, with a button to unblock and Undo.
- Active sessions: the devices logged in to your account. Confirm new logins, or terminate one session or all
  other sessions.
- Whether two-step verification is on, and whether a recovery email is set.

### Changed
- Settings follow the Material 3 Expressive look of Google's own apps. Your account sits on a card at the top (photo,
  name, phone number, username, and a Premium label for Telegram Premium subscribers); tap it to edit your profile.
  Every settings page shows its options as connected, rounded groups whose corners round out when you press them.
- The app's font is stored uncompressed, so Android reads it straight from the app when the first text draws
  instead of unpacking all 4 MB into memory: a faster start, for about 1.7 MB more download.
- Text is set the way Google sets Google Sans Flex on Android: Material's weights (titles were one to three
  steps heavier), without the extra letter spacing meant for Roboto, and rounded letters for emphasized text
  only. Sizes are unchanged, but text reads lighter and takes less room.
- Going back from a chat is smoother: the floating navigation bar stays ready while it is hidden instead of being
  rebuilt, and keeps its tab selected while it slides away and back. Chat list rows do less work as they appear;
  screen-reader descriptions are built only when a screen reader asks for them.
- Message bubbles follow Material 3 Expressive: round corners (one-line messages are pills), and runs of messages from
  one sender sit close together with tight corners on the sender's side, without tails. A message of one to three
  emoji shows large, without a bubble. Reactions show as round badges on the bubble's corner, up to three and then
  "+N"; tap one to add or take back yours.
- Conversations open more smoothly: no loading spinner flashes while messages come from the device.

### Fixed
- A chat with unread messages opens at the first unread message instead of the newest, also when there are many.
  Quotes and search results far back load the messages around them at once, and the position in a conversation
  survives rotation and the app being closed in the background.
- The chat list shows its "Connecting…" status once, below the title, instead of twice while the top bar collapses.
- Leaving a chat after playing a voice message or song no longer holds up the screen for a moment: the audio
  player now runs on its own thread, and frees itself there.
- "Clear cache" and "Log out" keep their card while they run, and settings page titles fit at large font sizes
  instead of being cut off.

## [0.2.0] - 2026-10-18

### Added
- Tapping a chat opens the conversation (read-only for now). The top bar shows the avatar and name with
  "online", "last seen …", who is typing, or the number of members or subscribers. Messages load as you
  scroll back, with date pills, a divider before the first unread message and a button that jumps to the
  newest. Bubbles group consecutive messages, show sender names and avatars in groups, the time, "edited",
  and sent, read, pending or failed status, forwarded-from headers, reply quotes, reactions and link
  previews. Text keeps its formatting: bold, italic, underline, strikethrough, code, quotes, spoilers (tap
  to reveal) and links. Service messages appear as pills.
- Media in conversations. Photos fill their bubble with a blurred preview while they load, albums show as
  one mosaic, and videos and GIFs show their thumbnail with the length. Stickers, animated emoji, dice and
  round video messages appear without a bubble. Voice messages show their waveform and length; documents
  show their name and size and download on tap, with progress and cancel. Music, polls with their results,
  locations and venues (which open your maps app), contacts, games and invoices have their own cards.
  Tapping a photo, video, GIF or album item opens it full screen.
- Voice messages and music play in the conversation, one at a time: the waveform fills as it plays, tapping
  it seeks, and voice messages play on to the next one. A file that isn't downloaded yet downloads first.
  Playback pauses when you leave the chat or unplug headphones.
- Messages you see in a conversation are marked as read, and the unread count follows.
- Tapping a reply quote jumps to the original message and highlights it, loading older messages if needed.
- A full-screen media viewer: swipe through all the photos and videos of a chat (GIFs among the chat's GIFs)
  with "N of total" at the top; pinch or double-tap to zoom and drag to pan; videos play with play/pause and
  a seek slider, GIFs loop silently. The sender, date and caption show over the media; tap to hide them
  along with the system bars. Swipe down or go back to close. The viewer is always dark, like a photo app.
- Photos, videos and GIFs grow out of their bubble into the media viewer and shrink back into it when you close
  it. The viewer can share what it shows with another app or save it to Pictures/Pingogram or Movies/Pingogram,
  unless the chat protects its content; on Android 8 and 9 saving first asks for storage access, with the reason.
  Tapping a downloaded document opens it in an app that can view it.
- Search, from the round button beside the navigation bar or at the top of the rail. Before you type it shows the
  people you message most and your recent searches, which you can remove one by one or clear. As you type it finds
  your chats, your contacts, public chats and channels, and messages in all your chats, with the matching words
  highlighted; more messages load as you scroll. Tapping a message opens its chat at that message.
- On tablets, foldables and other large screens, the chat list and the open conversation sit side by side;
  the open chat is highlighted in the list. Narrowing the window shows the conversation alone, and back
  returns to the list.

### Changed
- Folder tabs respond like Material 3 Expressive button groups: a pressed folder grows and nudges its
  neighbours, and the selected folder turns into a pill with a bold label. Every folder stays a tab; the row
  scrolls sideways when they do not fit. Switching folders fades the list out and the new folder's list in,
  from its top; with animations turned off, the lists swap at once.
- "Archived chats" is bold while it holds unread chats, like any unread chat.
- The chat list, the archive and conversations sit on a sheet with rounded top corners below their top bar, in the
  style of Google's Material 3 Expressive apps; chats and messages scroll inside it. Selected chats turn into
  rounded pills set in from the edges, and swiping a chat reveals its action on a rounded card.

### Fixed
- A swipe on a chat runs its action once. Archiving a chat inside a folder no longer archives and unarchives
  it over and over with its row stuck halfway, archiving in All chats no longer brings the chat straight back,
  and "Undo" on "Chat archived" moves the chat back to the chat list again. A chat just marked as read or
  unread by swiping can be swiped again at once, for example to unarchive it in Archived chats.
- With Android's "Bold text" setting on, text is one step bolder instead of nearly black, and unread chat
  names still stand out from read ones.
- Switching back to a chat folder shows its list where you left it, or at its top the first time, instead of
  jumping to the chat that was first in the other folder.
- The navigation bar stays in place when you switch tabs, instead of sliding sideways and back.
- Going back from a chat, the archive or another tab with the back gesture is smoother: the screen you leave
  shrinks a little and fades away over the one you go back to, instead of shrinking far, waiting and vanishing at
  once. Opening and leaving a chat no longer redraws every screen behind it, which caused stutters.
- Switching chat folders no longer stutters: the new folder's chats appear in their places instead of sliding in
  from where the other folder had them, and the chat list does less work per chat.
- Fewer stutters when going between screens: each text style's font is prepared once, instead of again whenever
  enough other styles had been shown since.

## [0.1.2] - 2026-10-18

### Added
- The chat list. The top bar shows the connection state ("Connecting…", "Waiting for network…") while not
  connected. Your chat folders are tabs with unread counts, with "All chats" where you placed it. Each chat
  shows its avatar with an online dot, verified or scam badges, a lock for secret chats, who is typing, your
  draft or the last message with its sender, the time in your 12- or 24-hour format, read ticks, and unread,
  mention, reaction, muted and pinned markers. Saved Messages and deleted accounts get their own icons. More
  chats load as you scroll. Opening a chat comes with the conversation screen.
- Chat actions in the chat list. Swipe a chat right to mark it as read or unread, or left to archive it (with
  Undo). Long-press to select chats: a floating toolbar takes the navigation bar's place to pin, mute, mark
  as read or unread, archive or delete them all at once, and the selected avatars turn into a check. Deleting
  asks first and offers "Also delete for …" or "Delete for all members" where Telegram allows it; leaving a
  group or channel is worded as such. An "Archived chats" row at the top of All chats opens the archive, which
  works like the chat list. Screen readers get every action on each chat, so none needs a swipe.
- A floating navigation bar on phones, in the style of Google's Material 3 Expressive apps: the tabs in a
  pill that floats over the content (the selected tab shows its icon and label, the others their label, or
  their icon when the labels do not fit), with a round search button beside it. Tablets keep the
  navigation rail, now with the search button at its top. Search itself is not built yet.
- Data layer for the chat list and conversations (no screens yet): chats with lists, folders, unread counts and
  mute state; users and their online status; paged chat history kept current by TDLib's updates, with at most
  three chats' messages in memory; files with download priorities; and a message model covering every content,
  formatting and service message type of the TDLib version in use. All of it starts listening before TDLib
  starts, so no update is missed.
- Groundwork for the chat list (no screen yet): typing indicators; verified, scam and fake badges for groups
  and channels; the chat actions behind swipes (pin, mute, archive, mark as read or unread, and delete or
  leave with the right steps for each kind of chat), with a clear message when the pinned-chat limit is
  reached; and avatars, which show the photo from TDLib's files through Coil 3 (which makes no network
  requests of its own) or else initials on an expressive Material shape in one of seven color pairs, with an
  online dot.

### Changed
- The Contacts placeholder no longer says that sign-in is coming.

## [0.1.1] - 2026-10-18

### Fixed
- The app could not connect to Telegram and stayed on "Connecting…": it did not request the internet
  permission.

## [0.1.0] - 2026-10-18

### Added
- Project scaffold, documentation, CI, TDLib and release workflows.
- Gradle project: version catalog, `build-logic` convention plugins, Gradle 9.8.1 wrapper and the
  `:app`, `:core:*` and `:feature:*` modules from `docs/ARCHITECTURE.md` (mostly empty for now).
  AGP 9.4.1, Kotlin 2.4.21, Compose BOM 2026.09.00; `foss`/`gms` flavors; app version and
  per-ABI version codes from `-Pversion`.
- Telegram API credentials come from the git-ignored `secrets.properties`; the build fails with a
  clear message (never echoing values) when the file or a key is missing or malformed.
- Committed debug keystore for consistent debug signatures; release signing from environment or
  `keystore.properties` (v2/v3/v4), and release packaging fails early when signing is not configured.

- Static analysis in `./gradlew check`: ktlint 1.8 (`./gradlew ktlintFormat`), detekt 1.23.8 with the
  `PingogramHardcodedToken` rule (no literal colors, text sizes, sizes, shapes or animation specs in
  `:feature:*` code), Android Lint with warnings as errors, and `./gradlew dependencyUpdates`.

- `:benchmark` module: Macrobenchmark `StartupBenchmark` and Baseline/Startup Profile generation
  (`./gradlew :app:generateBaselineProfile`); release builds ship the profile via profileinstaller.

- `PingogramTheme` on `MaterialExpressiveTheme`: six generated brand color schemes (seed `#E8457C`,
  standard/medium/high contrast), dynamic color on Android 12+, system contrast on Android 14+,
  AMOLED black, harmonized online green; the full Google Sans Flex type scale (15 styles + 15
  emphasized, per-style variable axes); the expressive shape scale with `MaterialShapes` helpers;
  spacing tokens; preview helpers; splash and window themes.
- Roborazzi screenshot tests (Robolectric, native graphics) verified by `./gradlew check`.

- Release builds produce one APK per ABI (`arm64-v8a`, `armeabi-v7a`, `x86_64`) plus a universal APK,
  with distinct version codes. Native code for other ABIs is left out, so devices that only support
  32-bit x86 cannot install the app.

- TDLib 1.8.68 for Android, built by the `Build TDLib` workflow and published as the `tdlib-a94b83d67a`
  release; the build downloads it and verifies its pinned SHA-256 (`pingogram.tdlib.aar` overrides it with
  a local AAR).
- `core:tdlib`: coroutine `TdlibClient` with typed authorization and connection state, automatic
  `SetTdlibParameters` (honest client identification), database key wrapped by the Android Keystore and
  kept out of backups; `FakeTdlibClient` and TdApi fixtures for tests.

- App shell: `MainActivity` (splash, edge-to-edge, predictive back) with type-safe navigation and an
  adaptive `NavigationSuiteScaffold` — short navigation bar on phones, navigation rail on larger
  screens — with Chats, Contacts and Settings placeholder tabs and Material Symbols Rounded icons.

- Sign-in flow (`feature:auth`): welcome, phone number with country picker and live formatting, login
  codes of every type with resend countdown, two-step verification password with recovery by email,
  registration with terms of service, QR code login, email address and email code, and an explanation when
  an account needs Telegram Premium to sign in (no purchase flow). Rate limits show a countdown and an
  offline banner appears while there is no connection; errors map to readable messages.

- The app opens on the sign-in flow when logged out and on the chats when logged in; the splash screen
  waits up to 300 ms for TDLib. Logging out (Settings → Log out, with confirmation), being logged out
  elsewhere, or a closed session returns to sign-in with nothing of the previous account left behind.

- Sign-in screens polished: real QR code for QR login (ZXing), password visibility toggle, adaptive layout
  for tablets, landscape and large fonts, shared-axis transitions and predictive back between steps,
  screen-reader friendly countdowns and banners; screenshot tests for every step in light, dark and
  dynamic color.

### Changed
- Package root and `applicationId` are `dev.kyralabs.pingogram` (debug: `dev.kyralabs.pingogram.debug`).
- `compileSdk` is 37 (needed by Compose 1.12); `targetSdk` stays 36.
- Uses the pre-release `androidx.compose.material3:material3:1.5.0-beta01` (allowed by CLAUDE.md rule 7):
  in the stable 1.4.0 the Material 3 Expressive APIs (`MotionScheme.expressive()`, `MaterialShapes`,
  the emphasized type styles, the increased shape sizes) are internal.
- Backups and device transfer are disabled for all app data.

[Unreleased]: https://github.com/kyra-labs/pingogram-android/compare/v0.5.0...HEAD
[0.5.0]: https://github.com/kyra-labs/pingogram-android/compare/v0.4.0...v0.5.0
[0.4.0]: https://github.com/kyra-labs/pingogram-android/compare/v0.3.0...v0.4.0
[0.3.0]: https://github.com/kyra-labs/pingogram-android/compare/v0.3.0-beta.3...v0.3.0
[0.3.0-beta.3]: https://github.com/kyra-labs/pingogram-android/compare/v0.3.0-beta.2...v0.3.0-beta.3
[0.3.0-beta.2]: https://github.com/kyra-labs/pingogram-android/compare/v0.3.0-beta.1...v0.3.0-beta.2
[0.3.0-beta.1]: https://github.com/kyra-labs/pingogram-android/compare/v0.2.0...v0.3.0-beta.1
[0.2.0]: https://github.com/kyra-labs/pingogram-android/compare/v0.1.2...v0.2.0
[0.1.2]: https://github.com/kyra-labs/pingogram-android/compare/v0.1.1...v0.1.2
[0.1.1]: https://github.com/kyra-labs/pingogram-android/compare/v0.1.0...v0.1.1
[0.1.0]: https://github.com/kyra-labs/pingogram-android/releases/tag/v0.1.0
