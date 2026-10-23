# Pingogram privacy policy

Pingogram is an open-source client for Telegram accounts, made by Kyra Labs. This policy says what the app does with
your data. It applies to every build: the ones on GitHub and on Google Play.

## The short version

- Pingogram has **no server**. It sends your data only to Telegram, the service you sign in to, through Telegram's
  official library (TDLib), encrypted in transit.
- The developer receives **nothing**: no analytics, no crash reports, no advertising identifiers, no tracking.
- Telegram's own privacy policy (https://telegram.org/privacy) covers what Telegram does with the data it receives.

## What goes to Telegram, and when

- **Your account**: your phone number when you log in, and your name, username, photo, bio and birthday when you set
  them.
- **What you send**: messages, photos, videos, voice messages, files, stickers, reactions and polls, when you send
  them. Secret chats are end-to-end encrypted on your device.
- **Calls**: voice calls are end-to-end encrypted; their audio is streamed to the other person and never stored.
- **Your location**: only when you choose to send it in a chat. Pingogram never reads it in the background.
- **Your phone's contacts**: only if you turn on contacts sync (off by default). Then the names and phone numbers of
  your contacts are sent to Telegram when you turn it on, when you open Contacts and when you tap "Sync now". Turning
  it off deletes them from Telegram.
- **A push token** (Google Play build only): so that new messages can reach your phone while Pingogram is closed, the
  app registers a Firebase Cloud Messaging token with Telegram. Telegram encrypts each push for your device; Google
  only sees that something arrived. The GitHub builds without Google services send no token.

## What stays on your phone

- Telegram's local database, encrypted with a key kept in the Android Keystore, and downloaded files.
- Your Pingogram settings, the passcode (stored as a salted hash) and your recent emoji.
- What the camera sees while you scan a QR code: it is decoded on the phone and dropped. In a video call, your camera's
  picture goes to the person you are talking to only, end-to-end encrypted, while your camera is on; it is not
  recorded or kept.
- Diagnostic logs. "Send feedback" prepares a report on your phone (app version, device, Android version, the last
  crash, a log excerpt without message texts or phone numbers) that you can read and then share yourself, through
  Android's share sheet or a GitHub issue. Nothing is sent without you.

## Deleting your data

- Log out to remove everything Pingogram stored on the phone, or uninstall it.
- Delete your Telegram account at https://my.telegram.org/auth?to=delete (and, from a later version, in Settings);
  Telegram then deletes your messages and contacts.

## Children

Pingogram is not meant for children under 13, Telegram's minimum age.

## Contact and changes

Questions: open an issue at https://github.com/kyra-labs/pingogram-android/issues. Changes to this policy are made
in this file; its history is public.
