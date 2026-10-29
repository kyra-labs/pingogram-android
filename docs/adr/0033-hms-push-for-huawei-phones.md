# 0033 — HMS push for Huawei phones

Status: **blocked**: Telegram does not send Huawei pushes to third-party apps. Nothing is built.
Date: 2026-10-29.
Milestone: M6 (the AppGallery listing, `docs/STORES.md`).

## Context

Huawei phones sold since 2019 have no Google services, so the `gms` build's Firebase pushes
(`docs/adr/0004-push-delivery.md`) never reach them. Their own push service is HMS Push Kit. On 2026-10-29 the owner
approved Push Kit, a proprietary SDK that talks to the network, for an `hms` flavor beside `foss` and `gms`, for the
AppGallery listing. The approval came with an order of work, and its first step was to check whether it can work at
all.

A push reaches a Telegram client only if two things hold:

1. **The client can register a Huawei token with Telegram.** TDLib must support it.
2. **Telegram's server sends Huawei pushes for the app's `api_id`.** For FCM, Telegram sends with the app developer's
   own credentials, which the developer uploads at https://my.telegram.org for that `api_id` (ADR 0004). Huawei's push
   service also needs the sender's credentials: the AppGallery Connect project's app id and app secret. So Telegram
   would need ours.

## Findings

### TDLib: ready

- td_api.tl has `deviceTokenHuaweiPush token:string encrypt:Bool = DeviceToken`, "A token for HUAWEI Push Service".
  The token may be empty, to unregister. With `encrypt` set, the payload is encrypted as for FCM.
- The pinned build has it: `TdApi.DeviceTokenHuaweiPush(String token, boolean encrypt)` is in the AAR of `a94b83d`
  (checked with `javap`).
- `registerDevice` and `processPushNotification` are the ones the `gms` build already uses.
- Telegram's push page lists the token type ("13 – Huawei push").

### Telegram's server: supported, but not for third-party apps

- **TDLib's maintainer (levlam) in tdlib/td#3057**, opened by a developer of a third-party client:
  - **The problem.** Registering a Huawei token for their own `api_id` answered `APP_PUSH_APIKEY_MISSING`, and
    my.telegram.org offered "only … the entry for adding FCM credential".
  - **2024-09-20:** "It is not possible to upload certificate for sending Huawei push notifications now, but this
    can be supported in the future at my.telegram.org."
  - **2025-05-24, the latest word found:** "Huawei Push Service is supported server-side, but my.telegram.org still
    does not have interface to upload the certificate for its usage by third-party apps."
  - **Status.** The issue was closed by its author without a change.
- **Telegram's push page** names credentials for APNS and FCM only ("application certificates (APNS) or an application
  key (GCM) must be specified in the application settings"), none for Huawei.
- **Nothing newer.** No later source says the form gained a Huawei entry (searched 2026-10-29).
  - Telegram's own app uses Huawei push with Telegram's own credentials, which no third-party app can use.
  - Telegram's bug tracker has users' reports that pushes do not work on Huawei phones (bugs.telegram.org/c/12998).

So Pingogram's `hms` build could register its Huawei token, but Telegram would refuse it, or accept it and send
nothing, until my.telegram.org lets the owner upload Pingogram's Huawei credentials. As ADR 0004 says of FCM, the app
cannot tell the difference.

### What one community project does instead, and why Pingogram does not

Telegram-HMS (github.com/xymeng16/Telegram-HMS), a fork of Telegram's app, runs a **relay server**.
- **How it works.** The server is a Telethon program holding Telegram's `api_id` and `api_hash` and the HMS app id and
  secret. It logs in to the user's Telegram account and forwards each incoming message to the phone as a Huawei push.
- **Why Pingogram does not do it:**
  - The server holds the user's whole Telegram session, so it can read every message.
  - Pingogram would have to run that server for every user.
  - Pingogram has no server and talks only to Telegram (`REQUIREMENTS.md` §6.2, `CLAUDE.md` rule 10).
  - A user's session on a third party's server hardly meets Telegram's terms on privacy ("guard users' privacy with
    utmost care", `docs/API_TERMS.md` 1.1).
- **Rejected.**

## Decision

**Stop at the first step, as the owner's instruction says: build no `hms` flavor.**
- **Not done**, until Telegram offers the upload:
  - no Push Kit dependency;
  - no AppGallery Connect plugin;
  - no change to `LICENSE-EXCEPTION.md`: the additional permission for Huawei's libraries is not drafted, since
    nothing would use it;
  - no change to `release.yml`.
- **AppGallery gets the `foss` build** (`docs/STORES.md`). Its notifications come from TDLib's own connection, and
  "Keep connected" (ADR 0003) keeps it alive in the background. Huawei's battery manager can still stop it unless the
  user lets Pingogram run in the background; `docs/STORES.md` words the note for the listing.

## Revisit when

my.telegram.org's app page offers Huawei push credentials. The owner can see it at https://my.telegram.org/apps, logged
in with the account that owns the `api_id`. Then the owner's order of work of 2026-10-29 picks up at its second step:
1. the license text for Huawei's libraries, approved by the owner before any `hms` build is distributed;
2. the `hms` flavor (Push Kit, `deviceTokenHuaweiPush(token, encrypt = true)` for every account, its own messaging
   service, the classpath checks);
3. the release workflow, which builds `hms` only when its secret is set.

Until then, the open option for Huawei phones without Google services is ADR 0004's follow-up for `foss`:
UnifiedPush through `deviceTokenSimplePush`.
- **How it works.** A wake-up through a distributor app the user installs, such as ntfy. It needs no Telegram-side
  credentials and no Huawei SDK.
- **Status.** Not scheduled; it needs an ADR of its own.

## Sources

- td_api.tl, `deviceTokenHuaweiPush`:
  https://core.telegram.org/tdlib/docs/classtd_1_1td__api_1_1device_token_huawei_push.html
- Telegram, Handling push notifications (token type 13; credentials in the application settings for APNS and FCM):
  https://core.telegram.org/api/push-updates
- tdlib/td#3057, "APP_PUSH_APIKEY_MISSING when trying to register device for Huawei Push":
  https://github.com/tdlib/td/issues/3057
- Telegram bug tracker, "Push Notifications don't work on Huawei": https://bugs.telegram.org/c/12998
- Telegram-HMS (relay server design): https://github.com/xymeng16/Telegram-HMS
