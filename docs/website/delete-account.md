<!--
  The content of the Pingogram website's account deletion page, which Google Play asks for (its account deletion
  policy: a web page where users can ask to delete their account and its data, without the app). Published at
  `https://pingogram.kyralabs.dev/delete-account/` (docs/PLAY_STORE.md §2.1). Keep it in step with the app's Delete account page
  (feature:settings, `DeleteAccountScreen`) and with Telegram's own deactivation page.
-->

# Delete your Pingogram account

Pingogram is an app for your **Telegram account**. Your account is Telegram's: deleting it deletes it from Telegram,
for every app and device you use it on. Pingogram is not affiliated with Telegram.

## Delete it in the app

1. Open Pingogram and go to **Settings → Privacy and security → Delete account**.
2. Under "Delete now", say why you are leaving if you like, and enter your 2-step verification password if you set one.
3. Tap **Delete account** and confirm.

Telegram deletes the account right away, and Pingogram signs out.

On the same page, **"Delete my account if away for"** sets how long the account may stay unused (1 to 24 months).
If you don't come online once in that time, Telegram deletes the account by itself.

## Delete it without the app

You can delete the account on Telegram's own page, from any browser:

1. Go to **[my.telegram.org/deactivate](https://my.telegram.org/deactivate)**.
2. Enter your phone number in international format. Telegram sends a confirmation code to your Telegram apps (not by
   SMS).
3. Enter the code, then confirm the deletion.

## What is deleted

- The account itself, on every device, for good. It can't be undone.
- Your messages and media, your contacts and your settings.
- Your secret chats.
- Groups and channels you created stay without their owner; their admins keep their rights.

## What stays

- Messages you sent stay with the people and groups who received them, shown as from a deleted account.
- Pingogram's data on your phone (its copy of your chats, its settings) stays until you uninstall Pingogram or clear
  its storage in Android's settings.
- You can sign up again later with the same phone number, as a new, empty account.

## What Pingogram keeps

Nothing. Pingogram has no servers and holds no account data: the app talks only to Telegram, through Telegram's own
library (TDLib). The developer receives none of your data, so there is nothing for us to delete on our side.

Questions: [open an issue on GitHub](https://github.com/kyra-labs/pingogram-android/issues).
