# 0004 — Passcode lock: platform BiometricPrompt and PBKDF2

Date: 2026-10-20 · Status: accepted (biometric library choice to be confirmed by the owner)

## Context

The passcode lock (`REQUIREMENTS.md` FR-S1, §6.2) needs three things: a passcode stored so that reading the settings
file does not reveal it, unlocking with a fingerprint or the face, and a lock that covers the whole app.

The brief suggested `androidx.biometric`. Its latest stable release is 1.1.0 (2021); the 1.2 and 1.4 lines are alphas
(1.4.0-alpha07 is the newest). Both lines:

- show their prompt only from a `FragmentActivity` or `Fragment` (`BiometricPrompt(FragmentActivity, …)`), while
  `MainActivity` is a `ComponentActivity`;
- depend on `androidx.appcompat:appcompat` (1.2.0) at run time, and before Android 9 (and on some Android 9 models)
  draw their own fingerprint dialog with AppCompat. `CLAUDE.md` rule 4 forbids AppCompat widgets.

The platform's own `android.hardware.biometrics.BiometricPrompt` (API 28) and `BiometricManager` (API 29) need no
library and work from any activity.

For the passcode, Android 8 and later ship `PBKDF2WithHmacSHA1/256/512` in `SecretKeyFactory`. `security-crypto` is
deprecated (`docs/TDLIB.md`), and Argon2 or scrypt would need a native library.

## Decision

- **Biometrics: the platform's `BiometricPrompt`, Android 10 and later, without `androidx.biometric`.** On Android 11+
  only strong (class 3) biometrics (`BIOMETRIC_STRONG`); on Android 10, which cannot tell classes apart, what
  `BiometricManager.canAuthenticate()` accepts. Never `DEVICE_CREDENTIAL`: the phone's own PIN would open the app
  without its passcode, which defeats a lock meant for people who can use the phone. Before Android 10 the passcode
  is the only way in (Android 8–9 have no `BiometricManager` to tell whether biometrics are enrolled). Needs
  `USE_BIOMETRIC` (owner-approved, a normal permission, pinned by `AppManifestTest`). The code sits behind
  `BiometricAvailability` and `rememberBiometricPrompt` (`feature:settings`), so `androidx.biometric` can replace it
  in one place if the owner wants Android 9 or a future stable release without AppCompat.
- **Passcode: PBKDF2-HMAC-SHA512, 210,000 rounds (OWASP's Password Storage Cheat Sheet, 2023), a random 16-byte salt
  per passcode, 512-bit output**, compared in constant time. Hash, salt and rounds are stored in the app's
  Preferences DataStore (never backed up), so the rounds can be raised later without breaking passcodes set before.
  Hashing runs on the CPU dispatcher; it takes a few hundred milliseconds on a phone, during which the entry shows
  progress. The passcode is a PIN of 4–8 digits or a password of 4–64 characters.
- **Attempts:** after five wrong passcodes the next attempt waits 30 s, doubling with each further one up to 30 min.
  The count is stored, so killing the app restarts the wait instead of ending it; the wait is measured on the time
  since boot (deep sleep included).
- **Lock:** `DefaultAppLockRepository` (`core:data`) is a state machine over the settings and the login steps; the
  activity reports when the app leaves the screen and comes back (not for configuration changes). While locked the app
  leaves the composition (its saved state is kept), the window is `FLAG_SECURE`, and a notification's chat opens only
  after unlocking. "Hide content in recent apps" uses `setRecentsScreenshotEnabled(false)` on Android 13+ and
  `FLAG_SECURE` before.
- **Forgot passcode:** logging out (which deletes the account's data on the device), then removing the passcode. There
  is no recovery: a recovery path would be a way around the lock.

## Consequences

- No new dependency, no AppCompat, nothing added to the APK.
- Biometric unlock is not offered on Android 8–9 (about 5% of devices); the passcode works everywhere.
- The passcode lock protects the UI only. The data at rest is TDLib's database, encrypted with a key in the Android
  Keystore (`docs/TDLIB.md`); someone with root access to the device can reach both. A 4-digit PIN has 10,000
  possibilities: the slow hash makes guessing it from a copied settings file cost hours rather than milliseconds, not
  impossible. The attempt limit protects the lock screen itself.
- An immediate auto-lock also locks when the user leaves for another app from inside Pingogram (the photo picker, a
  share sheet); results those apps return are delivered after unlocking.
- A corrupt settings file is replaced with defaults (`DataStoreModule`), which turns the lock off. Corrupting it needs
  access to the app's private files, which also gives access to everything the lock covers.
