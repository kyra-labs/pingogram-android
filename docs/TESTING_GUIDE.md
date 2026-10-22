# Tester guide

For the dogfood before 1.0 (`docs/ROADMAP.md` M6: 7 days, at least 10 testers, crash-free sessions ≥ 99.5%). Thank you
for helping. Pingogram is an unofficial Telegram client: you sign in with your own Telegram account, and everything
you do in it is real (messages are sent, calls ring, groups are created). Test with people who know you are testing.

## Installing

- Download an APK from the latest pre-release on GitHub
  (https://github.com/kyra-labs/pingogram-android/releases) or the owner's channel:
  - `pingogram-X.Y.Z-universal.apk` works on every phone; `-arm64-v8a.apk` is smaller and fits almost every phone
    since 2017.
  - The files without a suffix (`foss`) use no Google services: notifications arrive while Pingogram runs, or always
    with Settings → Notifications → "Keep connected". The `-gms` files get notifications through Google's push service
    even when Pingogram is closed. Both install over each other and keep your data.
- Android asks you to allow installing from your browser or file manager the first time.
- Updates install over the old version. Do not uninstall between versions: you would have to sign in again and secret
  chats would be lost.

## What to test

Use Pingogram as your everyday Telegram app for the week; the list says what to look at on purpose. Each day, try one
area in depth.

| Day | Area | Try |
|---|---|---|
| 1 | Sign in, chat list | Sign in with your number (and 2-step verification password), or with a QR code from another device. Scroll a long chat list; folders, the archive, pinning, muting, swiping to archive or mark read; search for chats, people and messages |
| 2 | Conversations | Long chats with photos, videos, stickers, voice messages and polls; replies, forwards, reactions, editing and deleting; the unread divider and "jump to bottom"; sending text with formatting, photos, files, your location, voice messages (hold the microphone), stickers and emoji |
| 3 | Notifications | Notifications with Pingogram in the background and closed (`gms`) or with "Keep connected" (`foss`); replying and marking read from the notification; muted chats stay quiet |
| 4 | Calls | Voice calls with a friend on another Telegram app: calling, answering (slide), declining, "Message" instead of answering, speaker, Bluetooth, mute, hold, the four emojis matching on both phones, a weak network (walk away from Wi-Fi), a call while the phone is locked |
| 5 | Groups and channels | Creating a group or a channel, inviting people, admin rights and permissions, invite links, forum topics, comments under channel posts, joining by a link or a QR code |
| 6 | Contacts, profiles, secret chats | Adding and editing contacts, contact sync (opt-in), profiles and their shared media, starting a secret chat and its self-destruct timer, comparing its encryption key |
| 7 | Settings and the device | Appearance (dark, AMOLED, color themes, contrast), font size at 200% in Android's settings, TalkBack, landscape, a tablet or foldable if you have one, the passcode lock, sharing into Pingogram from other apps, opening `t.me` links |

Things that are worth a report even if they look small: anything slow (a list that stutters, a chat that takes long to
open), anything that looks cut off or overlapping, text that is not translated or reads oddly, a notification that
did not come or came twice, battery or data use that seems high (Android's battery and data usage pages).

## Reporting a problem

1. If Pingogram crashed, open it again: the crash was kept on your phone.
2. Settings → About → **Send feedback or report a bug**.
3. Write what you did, what you expected and what happened instead.
4. Read the report ("Show report"). It holds:
   - the app's version and build, your phone's model, Android version and processor type;
   - the last crash, if one was kept: where in the code it happened;
   - the last lines of the connection library's (TDLib's) error log.

   Messages, names, phone numbers, quoted text and long tokens are taken out of the crash and the log before you see
   them. If you still see something private, delete it from the text after sharing it, or do not send it.
5. Send it yourself, one of two ways:
   - **Open GitHub issue**: opens a new bug report on GitHub with the fields filled (you need a GitHub account). The
     address holds as much of the log as fits; paste the shared report into the issue for the rest.
   - **Share report**: Android's share sheet, to any app you pick (email to the owner, a message to the testers'
     group).

Pingogram sends nothing by itself: no crash reporter, no analytics. A report reaches someone only when you share it.

After sending, "Delete crash report" forgets the kept crash, so the next report shows only a new one.

Please do not attach screenshots of private chats or other people's phone numbers. A screenshot of the problem in a
chat with yourself (Saved Messages) or of a test group is best.

## Good to know

- Pingogram follows Telegram's terms for apps: it shows sponsored messages in channels, and it has no features for
  automation or mass messaging. Do not script it or send many messages in a row while testing; Telegram may limit your
  account.
- Some things are not there yet and need no report: video calls (a video call from someone rings as a voice call),
  group voice chats, stories, Mini Apps, payments, in-chat search, chat export.
- A crash-free week counts too: say so in the testers' group at the end, with how much you used it.
