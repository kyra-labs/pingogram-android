# Android Auto and Wear OS

`REQUIREMENTS.md` §5.9: Pingogram's message notifications reach the car and the watch as they are, through Android's
own notification bridges. Pingogram has no car or watch app of its own: no screen in the car (Android Auto's
templates), no Wear OS app (`REQUIREMENTS.md` §1.2). Nothing here needs a permission or a library.

## What Pingogram does

- **Android Auto** shows the notifications of messaging apps in the car, reads them aloud and takes voice replies,
  when the app declares it (`app/src/main/AndroidManifest.xml`: the meta-data `com.google.android.gms.car.application`
  pointing at `res/xml/automotive_app_desc.xml`, which uses `notification` only). The name is Android Auto's; the app
  gains no Google Play services library by it, so the `foss` flavor declares it too.
- **Wear OS** shows a phone's notifications on the watch by itself; watches take the same `MessagingStyle`, the Reply
  action (voice, keyboard, smart replies) and Mark as read.
- Both read the conversation notifications of `core:notifications` (`ConversationNotificationBuilder`), which follow
  Android Auto's rules for messaging apps (checked by `AndroidAutoNotificationTest`; the manifest by `AppManifestTest`):
  - `NotificationCompat.MessagingStyle` with the user as a named `Person` ("You") and a named `Person` for each
    sender; group chats as group conversations with their title;
  - a **Reply** action with a `RemoteInput`, `SEMANTIC_ACTION_REPLY`, `setShowsUserInterface(false)` and generated
    replies allowed;
  - a **Mark as read** action with `SEMANTIC_ACTION_MARK_AS_READ` and `setShowsUserInterface(false)`;
  - both handled by a broadcast receiver (`NotificationActionReceiver` → `NotificationActionWorker`), never an
    activity, so nothing opens on the phone while driving;
  - after a reply, TDLib marks the chat read and removes its notification, as Android Auto expects of an answered
    conversation.
- Mark as read stays a visible action, as it is useful on the phone too. Android Auto also accepts it as an invisible
  action (`addInvisibleAction`); if a car shows it twice, that is the change to make.
- Hidden previews ("New message", when Telegram's settings hide the text) are what the car reads aloud too: the car
  never gets more than the lock screen. Secret chats are notified like other private chats, by Telegram's settings.
- Calls are not part of this: incoming calls are `CallStyle` notifications with a self-managed Telecom call
  (`docs/adr/0018-calls-and-telecom.md`). Whether a car shows them is up to Android Auto; it is not tested yet.

## Testing with the Desktop Head Unit (DHU)

The DHU runs Android Auto's car screen on a computer, connected to a phone over USB. It needs a phone with Android Auto
(built into Android 10+), a computer with the Android SDK, and Pingogram installed on the phone.

1. **Developer mode in Android Auto:** on the phone, Settings → Connected devices → Connection preferences →
   Android Auto (or the Android Auto app) → tap "Version" 10 times, accept.
2. **Unknown sources:** in Android Auto's ⋮ menu → Developer settings, turn on "Unknown sources". Android Auto ignores
   apps not installed from Google Play otherwise, which is every build from GitHub or a local `assembleDebug`.
3. **Head unit server:** in the same ⋮ menu, "Start head unit server".
4. **On the computer:** install "Android Auto Desktop Head Unit Emulator" in Android Studio's SDK Manager (SDK Tools),
   then:

   ```bash
   adb forward tcp:5277 tcp:5277
   $ANDROID_HOME/extras/google/auto/desktop-head-unit
   ```

5. **Send a message** to the phone's account from another device (or have someone send one). In the DHU:
   - the notification shows as a heads-up with the sender and the text;
   - tapping it reads it aloud and offers "Reply" and "Mark as read";
   - "Reply" takes a spoken answer (the DHU's microphone is the computer's) and sends it: check on another device
     that it arrived, in the right chat;
   - after the reply or "Mark as read" the notification goes, and the chat reads as read on the other device;
   - a group chat shows the group's name and each sender's;
   - with "Show message previews" off for a chat (Pingogram's notification settings, or Telegram's), the car says
     "New message" only.

The DHU's `--help` lists its options (night mode, input with the keyboard as the car's rotary controller).

## What needs the owner's phone and car

None of this can be checked in CI, which has no Android Auto, no DHU and no Telegram account on a phone:

- **The DHU run above**, on the owner's phone, with a build from this branch (`foss` or `gms`).
- **A real car** (or a head unit) with Android Auto, wired or wireless, once: car head units differ from the DHU in
  how they show actions and read messages, and some show Mark as read twice (see above).
- **A Wear OS watch** paired with the phone: a message notification shows on the watch with the conversation, Reply
  (voice, keyboard, smart replies) sends from the watch, Mark as read clears it on both.
- **The `gms` flavor while the phone sleeps:** with Firebase push, a message wakes the phone and reaches the car or
  the watch at once; `foss` delivers while Android keeps the app running, or with "Keep connected" on
  (`docs/adr/0003-keep-connected-foreground-service.md`).
