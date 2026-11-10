# 0037 — Passkeys through Android's own Credential Manager, no library

Status: proposed (owner decisions to confirm, 2026-11-10) ·
Date: 2026-11-10 ·
Milestone: after `v2.0.0` (`docs/ROADMAP.md`; the owner's boards 17a and 17b, Session C)

## Context

Telegram lets an account sign in with a passkey instead of a code sent by SMS or to another app. The owner's design
adds two places for it:

- **17a**: Settings → Privacy and security → Passkeys: a status card, "Your passkeys" (each with the password manager
  that keeps it and when it was added and last used), "Add passkey", a footer, and "Also protecting this account".
- **17b**: the sign-in's phone number step: "or", then "Sign in with a passkey".

TDLib (a94b83d, checked in its `TdApi`) has every function for it:

- **Adding**: `getPasskeyParameters` answers the WebAuthn creation options as JSON (`text`); the authenticator's answer
  goes back as `addLoginPasskey(client_data, attestation_object)`, which answers the new `passkey` (`id`, `name`,
  `addition_date`, `last_usage_date`, `software_icon_custom_emoji_id`).
- **Listing and removing**: `getLoginPasskeys` (`passkeys`), `removeLoginPasskey(passkey_id)`.
- **Signing in**: while TDLib waits for a phone number, `getAuthenticationPasskeyParameters` answers the WebAuthn
  request options as JSON; the assertion goes back as `checkAuthenticationPasskey(credential_id, client_data,
  authenticator_data, signature, user_handle)`, after which TDLib moves on as after a code.
- `suggestedActionAddLoginPasskey` is Telegram's suggestion to add one.

The authenticator is the user's password manager (Google Password Manager, Samsung Pass, Bitwarden, 1Password, …),
which an Android app reaches through the Credential Manager. Pingogram never sees or keeps the passkey's private key.

The owner approved adding Jetpack's `androidx.credentials` for this (2026-11-10). Its 1.6.0, the latest stable,
resolved with its dependencies (checked 2026-11-10), brings `androidx.appcompat:appcompat` 1.2.0 and
`androidx.biometric:biometric` 1.1.0 to every flavor. `CLAUDE.md` rule 4 rules AppCompat out, and
`docs/adr/0005-passcode-lock.md` chose the platform's `BiometricPrompt` over `androidx.biometric`. Its Play services
provider for Android 9 to 13, `credentials-play-services-auth`, adds `play-services-fido`, `play-services-auth`, an
alpha `play-services-identity-credentials` and Google's sign-in library to `gms`, over a megabyte of libraries where
the `gms` APK has about 1.6 MB left of its budget (`docs/PERFORMANCE.md` §8).

## Decision

1. **The platform's Credential Manager** (`android.credentials.CredentialManager`, Android 14, API 34), called
   directly, with no library, as the passcode lock uses the platform's `BiometricPrompt` and location the platform's
   `LocationManager` (`docs/adr/0006-platform-location-manager.md`). `core:data`'s
   `PlatformPasskeyAuthenticator`, behind `core:domain`'s `PasskeyAuthenticator`, builds the requests password
   managers expect from Jetpack, with the same keys (read from `androidx.credentials` 1.6.0's compiled constants,
   not written from memory): the type `androidx.credentials.TYPE_PUBLIC_KEY_CREDENTIAL`, the subtype and
   `androidx.credentials.BUNDLE_KEY_REQUEST_JSON` (TDLib's JSON as it comes), the display info for creating, and reads
   `androidx.credentials.BUNDLE_KEY_REGISTRATION_RESPONSE_JSON` / `BUNDLE_KEY_AUTHENTICATION_RESPONSE_JSON` back into
   TDLib's fields (base64url-decoded where TDLib takes bytes). It needs an `Activity` to show Android's sheet, so the
   screens call it through their route.
2. **Android 14 and later, both flavors**: before Android 14 the Passkeys row and the sign-in button are left out, in
   `foss` and `gms` alike. Owner decision to confirm (2026-11-10): left out rather than shown disabled with a note; and
   no passkeys before Android 14 in `gms`, which the Play services provider would have given at the cost above.
3. **No new dependency**: nothing is added to `gradle/libs.versions.toml`; the approval given for `androidx.credentials`
   is not used. If the owner prefers the library (Android 9 to 13 in `gms`), this ADR changes and `CLAUDE.md` rule 4
   and ADR 0005 need an exception first.
4. **Digital Asset Links**: Telegram's relying party (`telegram.org`) has to list Pingogram's signing certificates for
   Android to hand Telegram's passkeys to Pingogram. Until it does, password managers refuse; Pingogram then says
   "Passkeys aren't available to Pingogram yet" in a snackbar rather than hiding a feature the owner can take up with
   Telegram. Owner decision to confirm (2026-11-10).
5. **Privacy**: nothing goes anywhere but to Telegram (through TDLib) and to the user's own password manager, on the
   user's tap. No passkey, credential id or response is logged or kept.
6. **The demo** answers the passkey functions with sample passkeys and never opens Android's sheet; "Add passkey" and
   "Sign in with a passkey" say the demo can't do it.

## Consequences

- No new dependency, no APK growth beyond Pingogram's own code.
- Users on Android 13 and earlier get no passkeys in either flavor.
- Pingogram keeps a small copy of Jetpack's request format; a password manager that changes what it reads would need
  Pingogram to follow, where the library would have followed for it. The keys are part of Jetpack's public API, so a
  change there is unlikely.
- Until Telegram's asset links name Pingogram's certificates, adding a passkey and signing in with one fail with the
  snackbar above; listing and removing work, as they go through TDLib only.
