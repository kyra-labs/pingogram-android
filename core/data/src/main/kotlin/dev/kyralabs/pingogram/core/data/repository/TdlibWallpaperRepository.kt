package dev.kyralabs.pingogram.core.data.repository

import dev.kyralabs.pingogram.core.common.ApplicationScope
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.common.result.map
import dev.kyralabs.pingogram.core.data.di.AccountScoped
import dev.kyralabs.pingogram.core.data.mapper.toInput
import dev.kyralabs.pingogram.core.data.mapper.toOverride
import dev.kyralabs.pingogram.core.data.mapper.toWallpaper
import dev.kyralabs.pingogram.core.domain.model.AccountWallpapers
import dev.kyralabs.pingogram.core.domain.model.ChatWallpaperOverride
import dev.kyralabs.pingogram.core.domain.model.Wallpaper
import dev.kyralabs.pingogram.core.domain.model.WallpaperChoice
import dev.kyralabs.pingogram.core.domain.repository.WallpaperRepository
import dev.kyralabs.pingogram.core.tdlib.TdlibClient
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import org.drinkless.tdlib.TdApi
import javax.inject.Inject
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * [WallpaperRepository] on TDLib (`docs/TDLIB.md` "Backgrounds"): two [TdlibUpdateCache] stores, the account's
 * wallpapers from `updateDefaultBackground` (sent for both themes at start and on every change) and the chats' own from
 * `updateNewChat` and `updateChatBackground`, holding only the chats that have one. A change also applies TDLib's
 * answer at once, so the picker does not wait for the update.
 */
@AccountScoped
class TdlibWallpaperRepository
    @Inject
    constructor(
        private val client: TdlibClient,
        @ApplicationScope scope: CoroutineScope,
    ) : WallpaperRepository,
        TdlibUpdateCache {
        private val defaultStore = UpdateStore(client, scope, AccountWallpapers(), ::reduceDefaults)
        private val chatStore = UpdateStore(client, scope, persistentMapOf(), ::reduceChats)

        override val defaults: StateFlow<AccountWallpapers> = defaultStore.state

        override fun chatWallpaper(chatId: Long): Flow<ChatWallpaperOverride?> =
            chatStore.state.map { it[chatId] }.distinctUntilChanged()

        override suspend fun installed(forDarkTheme: Boolean): Outcome<List<Wallpaper>> =
            client.request(TdApi.GetInstalledBackgrounds(forDarkTheme)).map { answer ->
                answer.backgrounds.mapNotNull { it.toWallpaper() }
            }

        override suspend fun setDefault(
            choice: WallpaperChoice,
            forDarkTheme: Boolean,
        ): Outcome<Unit> {
            val (input, type) = choice.toInput()
            return client.request(TdApi.SetDefaultBackground(input, type, forDarkTheme), choice.timeout()).map { set ->
                defaultStore.update { it.with(forDarkTheme, set.toWallpaper()) }
            }
        }

        override suspend fun removeDefault(forDarkTheme: Boolean): Outcome<Unit> =
            client.request(TdApi.DeleteDefaultBackground(forDarkTheme)).map {
                defaultStore.update { it.with(forDarkTheme, null) }
            }

        override suspend fun setChatWallpaper(
            chatId: Long,
            choice: WallpaperChoice,
            darkThemeDimming: Int,
            onlyForSelf: Boolean,
        ): Outcome<Unit> {
            val (input, type) = choice.toInput()
            val dimming = darkThemeDimming.coerceIn(0, MAX_DIMMING)
            return client
                .request(TdApi.SetChatBackground(chatId, input, type, dimming, onlyForSelf), choice.timeout())
                .map {}
        }

        override suspend fun removeChatWallpaper(chatId: Long): Outcome<Unit> =
            client.request(TdApi.DeleteChatBackground(chatId, false)).map {
                chatStore.update { it.removing(chatId) }
            }
    }

/** A new photo uploads first, which can take minutes on a slow network; anything else is a plain request. */
private fun WallpaperChoice.timeout(): Duration =
    if (this is WallpaperChoice.NewPhoto) UPLOAD_TIMEOUT else REQUEST_TIMEOUT

private fun AccountWallpapers.with(
    dark: Boolean,
    wallpaper: Wallpaper?,
) = if (dark) copy(dark = wallpaper) else copy(light = wallpaper)

private fun reduceDefaults(
    state: AccountWallpapers,
    update: TdApi.Update,
): AccountWallpapers =
    if (update is TdApi.UpdateDefaultBackground) {
        state.with(update.forDarkTheme, update.background?.toWallpaper())
    } else {
        state
    }

private fun reduceChats(
    state: PersistentMap<Long, ChatWallpaperOverride>,
    update: TdApi.Update,
): PersistentMap<Long, ChatWallpaperOverride> =
    when (update) {
        is TdApi.UpdateNewChat -> state.withChat(update.chat.id, update.chat.background)
        is TdApi.UpdateChatBackground -> state.withChat(update.chatId, update.background)
        else -> state
    }

private fun PersistentMap<Long, ChatWallpaperOverride>.withChat(
    chatId: Long,
    background: TdApi.ChatBackground?,
): PersistentMap<Long, ChatWallpaperOverride> {
    val override = background?.toOverride()
    return when {
        override != null -> putting(chatId, override)
        containsKey(chatId) -> removing(chatId)
        else -> this
    }
}

/** `chatBackground.dark_theme_dimming`'s range, in percent. */
private const val MAX_DIMMING = 100

private val UPLOAD_TIMEOUT = 10.minutes
