# TDLib integration

TDLib (https://github.com/tdlib/td, Boost Software License 1.0) is Telegram's official
cross-platform client library. It implements MTProto, encryption, secret chats, local storage,
file management and notifications. Pingogram never talks to Telegram servers except through it.

## Pinned version

- Pin an exact **commit SHA or tag** of `tdlib/td` in `gradle/libs.versions.toml` under
  `tdlib = "<sha>"` and in `.github/workflows/tdlib.yml` (`TDLIB_COMMIT`).
- Bump only in a dedicated PR titled `chore(tdlib): bump to <sha>` with the diff of `td_api.tl`
  summarized in the description.

## Build strategy

TDLib is not published as a ready-made Android artifact on Maven Central, so we build it once
per pinned commit in CI and reuse the AAR everywhere.

1. `.github/workflows/tdlib.yml` (manual `workflow_dispatch`, also on change of
   `TDLIB_COMMIT`) checks out `tdlib/td` at the pinned commit and runs the official Android
   example scripts in `example/android/` (`check-environment.sh`, `fetch-sdk.sh`,
   `build-openssl.sh`, `build-tdlib.sh`). Verify the exact script arguments against the README
   of the pinned commit; they take the SDK path, NDK version, OpenSSL dir, STL and API level.
2. Build with **NDK r28 or newer** so the 64-bit `.so` files (`arm64-v8a`, `x86_64`) are **16 KB
   page-size aligned** (verify with `llvm-readelf -l libtdjni.so | grep LOAD` → alignment `0x4000`;
   the workflow fails otherwise). `armeabi-v7a` stays 4 KB aligned: 32-bit devices only use 4 KB pages.
3. ABIs: `arm64-v8a`, `armeabi-v7a`, `x86_64`. Minimum API 26.
4. The workflow uploads `tdlib-<sha>.aar` (+ `SHA256SUMS`) as a workflow artifact **and**
   attaches it to a GitHub Release tagged `tdlib-<short-sha>` marked pre-release, so the app
   build can download it deterministically.
5. The build fetches that AAR by itself (`pingogram.tdlib` convention plugin, applied by
   `core/tdlib/build.gradle.kts`; logic in `build-logic/.../TdlibAar.kt`):
   - The expected SHA-256 is pinned in `gradle/libs.versions.toml` as `tdlibAarSha256` (copy it
     from `SHA256SUMS.txt` of the release). Until a release exists it is `"unpinned"` (version
     catalogs allow no empty value), and the build fails with instructions.
   - When `:core:tdlib` is configured, the AAR is downloaded from
     `https://github.com/kyra-labs/pingogram-android/releases/download/tdlib-<short-sha>/tdlib-<short-sha>.aar`
     if it is missing or does not match the pin, verified, and installed as `org.drinkless:tdlib:<sha>`
     in a file-based Maven repository, `core/tdlib/libs/maven/` (git-ignored; delete it any time).
     `settings.gradle.kts` declares that repository for the group `org.drinkless` only, and
     `:core:tdlib` has it as an `api` dependency. A repository rather than
     `files("libs/tdlib.aar")`, because AGP refuses direct local `.aar` dependencies in library
     modules when it bundles their AAR.
   - The check runs in a Gradle `ValueSource`: with the configuration cache, dependencies are
     resolved while the cache entry is stored, so the AAR must exist at configuration time, and
     Gradle obtains the value again before reusing an entry, so a deleted or modified AAR is
     restored even on a cache hit. Offline (`--offline`) the build fails instead of downloading.
   - `./gradlew :core:tdlib:fetchTdlib` does the same on demand (e.g. before going offline).
   - **Self-built AAR:** set the Gradle property `pingogram.tdlib.aar` to its path (absolute, or
     relative to the repository root), in `~/.gradle/gradle.properties` or with
     `-Ppingogram.tdlib.aar=…`. It is installed as `org.drinkless:tdlib:<sha>-local`, without
     download or checksum. A compile-only AAR without native libraries is enough to build and
     run the unit tests, but not to run the app.
6. Keep `android.useLegacyPackaging = false`; set
   `packaging { jniLibs { useLegacyPackaging = false } }`.

If the owner prefers, the AAR can instead be committed via Git LFS; document the choice in an ADR.

## Kotlin wrapper (`core:tdlib`)

```kotlin
interface TdlibClient {
    val updates: SharedFlow<TdApi.Update>          // every update, in order, never dropped
    val authState: StateFlow<AuthState>            // typed mirror of updateAuthorizationState
    val connectionState: StateFlow<ConnectionState>
    fun start()                                    // non-blocking, idempotent
    suspend fun <T : TdApi.Object> send(function: TdApi.Function<T>): TdResult<T>
}
```

`DefaultTdlibClient` (`@Singleton`, bound by `TdlibModule`) implements it:

- `send` wraps `Client.send` in `suspendCancellableCoroutine` and maps `TdApi.Error` to
  `TdResult.Failure(TdError(code, message))`. Cancelling only stops the wait: TDLib cannot cancel
  a request, and its late answer is dropped. `TdError.retryAfter` reads `FLOOD_WAIT_x` and
  `Too Many Requests: retry after x` for the countdown UI.
- Updates leave TDLib's thread at once: the callback puts them into an unbounded queue, drained
  in order by one coroutine on the `@Dispatcher(Default)` dispatcher. It applies `authState` /
  `connectionState`, then emits to `updates` (`MutableSharedFlow`, `extraBufferCapacity = 4096`):
  `tryEmit`, or, if slow collectors filled the buffer, log and suspend in `emit`. Updates are
  never dropped (losing one corrupts state), and TDLib's thread never blocks: it also delivers
  the answers to `send`, so blocking it while a collector awaits one would deadlock.
- `updates` has no replay: a repository that needs every update subscribes before `start()`.
- `start()` runs `Client.execute(SetLogVerbosityLevel)` before creating the client: 1 in release
  (`REQUIREMENTS.md` §6.2: ≤ 2), 3 in debug, where `SetLogStream` also writes a rotating file
  (`filesDir/tdlib/logs/tdlib.log`, 10 MB). Our own logs never contain `TdApi` objects (their
  `toString()` dumps everything, message text included) or user data; `AuthState.toString()`
  leaves payloads out.
- `org.drinkless.tdlib.Client` sits behind an internal seam (`TdlibNative` / `NativeClient`), so
  JVM unit tests drive `DefaultTdlibClient` without `libtdjni`. `TdApi` classes are plain Java
  and work in JVM tests, except their native `toString()`.
- `PingogramApplication.onCreate()` calls `start()`, so TDLib starts in the background before
  the first frame (`docs/PERFORMANCE.md` §2).

## Parameters

Send on `AuthorizationStateWaitTdlibParameters`:

| Field | Value |
|---|---|
| `useTestDc` | `false` (debug toggle available) |
| `databaseDirectory` | `filesDir/tdlib/db` (`filesDir/tdlib-test/db` on the test DC) |
| `filesDirectory` | `filesDir/tdlib/files` (`tdlib-test/files` on the test DC; cache-clearable via `optimizeStorage`) |
| `databaseEncryptionKey` | 32 random bytes generated once, wrapped by an Android Keystore AES key, stored in `noBackupFilesDir` (see below) |
| `useFileDatabase`, `useChatInfoDatabase`, `useMessageDatabase` | `true` |
| `useSecretChats` | `true` |
| `apiId` / `apiHash` | `BuildConfig.TELEGRAM_API_ID` / `TELEGRAM_API_HASH` |
| `systemLanguageCode` | `Locale.getDefault().toLanguageTag()` |
| `deviceModel` | `"${Build.MANUFACTURER} ${Build.MODEL}"` |
| `systemVersion` | `"Android ${Build.VERSION.RELEASE}"` |
| `applicationVersion` | `"Pingogram ${BuildConfig.VERSION_NAME}"` |

`TdlibConfig` (api id/hash, version name, debug flag, test DC) comes from `:app`'s
`TdlibConfigModule`, which reads `BuildConfig`; `AndroidTdlibParametersProvider` builds the rest.

### Database encryption key

`KeystoreDatabaseKeyProvider`: 32 random bytes from `SecureRandom`, encrypted with AES-256-GCM by
a key that lives in the Android Keystore (no user authentication, so TDLib can start in the
background) and stored as IV + ciphertext in `noBackupFilesDir/tdlib_database_key`, which is
never backed up or transferred. No crypto library: `security-crypto` is deprecated.

If the stored key can never be decrypted again (Keystore key deleted or permanently
invalidated, unreadable entry, file corrupt or encrypted with another key), a new key is
generated. TDLib cannot open the old database with it, so the database and files directories
are deleted and the user logs in again (secret chats are lost; there is no way around that).
Transient errors (IO, a busy Keystore) are thrown instead and never cost the session. If TDLib
still answers `SetTdlibParameters` with 401 (wrong key), the client discards key and database
once and sends the parameters again.

Useful options via `setOption`: `notification_group_count_max`, `notification_group_size_max`,
`ignore_inline_thumbnails` (false), `use_quick_ack`, `prefer_ipv6`, `language_pack_id`.

## Authorization state machine

```
WaitTdlibParameters ─▶ WaitPhoneNumber ─▶ WaitCode ─▶ WaitPassword? ─▶ Ready
          │                  │                 └──▶ WaitRegistration ─▶ Ready
          │                  ├──▶ WaitOtherDeviceConfirmation (QR) ─▶ WaitPassword? ─▶ Ready
          │                  └──▶ WaitEmailAddress ─▶ WaitEmailCode ─▶ WaitCode …
          └── LoggingOut ─▶ Closing ─▶ Closed (recreate Client, clear caches, go to Welcome)
```

- Modelled as a sealed `AuthState` in `core:tdlib`: one entry per `AuthorizationState` of the
  pinned TDLib version (some carry TdApi payloads for `core:data` to map), plus `Initializing`
  until TDLib reports its first state. `feature:auth` renders one screen per state. A TDLib bump
  that adds a state fails `TdApiFixturesTest` until `AuthState` and the fixtures cover it.
- `WaitTdlibParameters` is handled inside `TdlibClient`: it sends the parameters itself.
- On `AuthorizationStateClosed` the client creates a new `Client` (which starts again at
  `WaitTdlibParameters`) and resets `connectionState`. Repositories wipe their in-memory caches
  when they see `UpdateAuthorizationState(AuthorizationStateClosed)` in `updates` (`authState` is
  conflated and may skip `Closed`).
- Rate limits during auth (`FLOOD_WAIT_N`, `PHONE_NUMBER_FLOOD`) show a countdown screen.

## Updates we must handle from day one

`updateAuthorizationState`, `updateConnectionState`, `updateOption`,
`updateNewChat`, `updateChatPosition`, `updateChatLastMessage`, `updateChatReadInbox/Outbox`,
`updateChatUnreadMentionCount`, `updateChatDraftMessage`, `updateChatPermissions`, `updateChatNotificationSettings`,
`updateChatFolders`, `updateUser`, `updateUserStatus`, `updateBasicGroup`, `updateSupergroup`,
`updateSecretChat`, `updateNewMessage`, `updateMessageSendSucceeded/Failed`,
`updateMessageContent`, `updateMessageEdited`, `updateDeleteMessages`,
`updateMessageInteractionInfo`, `updateChatAction`, `updateFile`, `updateFileDownloads`,
`updateNotificationGroup`, `updateNotification`, `updateActiveNotifications`,
`updateUnreadMessageCount`, `updateUnreadChatCount`, `updateScopeNotificationSettings`,
`updateDefaultReactionType`, `updateAvailableMessageEffects`, `updateStickerSet`,
`updateInstalledStickerSets`, `updateRecentStickers`, `updateSavedAnimations`.

Unknown updates are ignored but counted in debug logs.

## Compliance notes (must read)

- https://core.telegram.org/api/terms — third-party apps must not spam, must not hide
  sponsored messages in channels, must respect the privacy of users, and must identify
  themselves honestly.
- https://core.telegram.org/api/obtaining_api_id — one `api_id` per app; do not share it in
  public builds' source; it is embedded in the binary (unavoidable) but never in git.
- Avoid behaviour that looks like automation during testing (mass joins, scripted sends) —
  Telegram bans accounts and `api_id`s for this. Use the test DC and official test numbers
  (https://core.telegram.org/api/auth#test-accounts) for automated tests.
- New `api_id`s can be subject to stricter limits for a while; expect `FLOOD_WAIT` during
  development and handle it, do not retry in a loop.

## Testing without the network

`core:testing` provides `FakeTdlibClient`: `on(TdApi.GetMe::class) { … }` registers the answer
for a function class (handlers may suspend), `sent` records every request, `emit(update)` and
`emitAuthorizationState(…)` script updates, and `authState` / `connectionState` can be set
directly. `TdApiFixtures` builds TdApi objects (authorization and connection states, errors,
flood waits). Repositories and ViewModels are tested against them; only `connectedAndroidTest`
touches the real library (against the test DC).
