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
2. Build with **NDK r28 or newer** so all `.so` files are **16 KB page-size aligned**
   (verify with `llvm-readelf -l libtdjni.so | grep LOAD` → alignment `0x4000`).
3. ABIs: `arm64-v8a`, `armeabi-v7a`, `x86_64`. Minimum API 26.
4. The workflow uploads `tdlib-<sha>.aar` (+ `SHA256SUMS`) as a workflow artifact **and**
   attaches it to a GitHub Release tagged `tdlib-<short-sha>` marked pre-release, so the app
   build can download it deterministically.
5. `core/tdlib/build.gradle.kts` has a Gradle task `fetchTdlib` that downloads that AAR into
   `core/tdlib/libs/tdlib.aar` (git-ignored) if missing or checksum mismatches, then
   `implementation(files("libs/tdlib.aar"))`. Local devs can also drop a self-built AAR there.
6. Keep `android.useLegacyPackaging = false`; set
   `packaging { jniLibs { useLegacyPackaging = false } }`.

If the owner prefers, the AAR can instead be committed via Git LFS; document the choice in an ADR.

## Kotlin wrapper (`core:tdlib`)

```kotlin
class TdlibClient @Inject constructor(
    @ApplicationContext ctx: Context,
    @Dispatcher(Default) private val dispatcher: CoroutineDispatcher,
    private val keyStore: DatabaseKeyProvider,
    private val params: TdlibParametersProvider,
) {
    private val client: Client = Client.create(::onUpdate, ::onException, ::onException)
    private val _updates = MutableSharedFlow<TdApi.Update>(extraBufferCapacity = 4096)
    val updates: SharedFlow<TdApi.Update> = _updates.asSharedFlow()
    val authState: StateFlow<AuthState>
    val connectionState: StateFlow<ConnectionState>

    suspend fun <T : TdApi.Object> send(fn: TdApi.Function<T>): TdResult<T> =
        suspendCancellableCoroutine { cont ->
            client.send(fn) { obj ->
                when (obj) {
                    is TdApi.Error -> cont.resume(TdResult.Failure(TdError(obj.code, obj.message)))
                    else -> @Suppress("UNCHECKED_CAST") cont.resume(TdResult.Success(obj as T))
                }
            }
        }
}
```

- `onUpdate` posts to `_updates` via `tryEmit`; if the buffer is full, log and `runBlocking`
  emit — losing updates corrupts state.
- `Client.execute(TdApi.SetLogVerbosityLevel(...))` before creating the client: 1 in release,
  3 in debug. Never log `TdApi` objects containing text in release.
- Call `setLogStream` to a rotating file only in debug builds.

## Parameters

Send on `AuthorizationStateWaitTdlibParameters`:

| Field | Value |
|---|---|
| `useTestDc` | `false` (debug toggle available) |
| `databaseDirectory` | `filesDir/tdlib/db` |
| `filesDirectory` | `filesDir/tdlib/files` (cache-clearable via `optimizeStorage`) |
| `databaseEncryptionKey` | 32 random bytes generated once, wrapped by an Android Keystore AES key, stored in EncryptedFile/DataStore |
| `useFileDatabase`, `useChatInfoDatabase`, `useMessageDatabase` | `true` |
| `useSecretChats` | `true` |
| `apiId` / `apiHash` | `BuildConfig.TELEGRAM_API_ID` / `TELEGRAM_API_HASH` |
| `systemLanguageCode` | `Locale.getDefault().toLanguageTag()` |
| `deviceModel` | `"${Build.MANUFACTURER} ${Build.MODEL}"` |
| `systemVersion` | `"Android ${Build.VERSION.RELEASE}"` |
| `applicationVersion` | `"Pingogram ${BuildConfig.VERSION_NAME}"` |

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

- Modelled as a sealed `AuthState` in `core:tdlib`; `feature:auth` renders one screen per state.
- `AuthorizationStateClosed` must destroy and recreate the `Client` and wipe in-memory repos.
- Rate limits during auth (`FLOOD_WAIT_N`, `PHONE_NUMBER_FLOOD`) show a countdown screen.

## Updates we must handle from day one

`updateAuthorizationState`, `updateConnectionState`, `updateOption`,
`updateNewChat`, `updateChatPosition`, `updateChatLastMessage`, `updateChatReadInbox/Outbox`,
`updateChatUnreadMentionCount`, `updateChatDraftMessage`, `updateChatNotificationSettings`,
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

`core:testing` provides `FakeTdlibClient` that replays scripted `TdApi.Update`s and answers
`send()` from a map of function class → response. Repositories and ViewModels are tested
against it; only `connectedAndroidTest` touches the real library (against the test DC).
