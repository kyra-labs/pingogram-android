package dev.kyralabs.pingogram.core.testing

import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.AccountWallpapers
import dev.kyralabs.pingogram.core.domain.model.ChatWallpaperOverride
import dev.kyralabs.pingogram.core.domain.model.FileRef
import dev.kyralabs.pingogram.core.domain.model.Wallpaper
import dev.kyralabs.pingogram.core.domain.model.WallpaperChoice
import dev.kyralabs.pingogram.core.domain.model.WallpaperImage
import dev.kyralabs.pingogram.core.domain.repository.WallpaperRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/**
 * [WallpaperRepository] in memory. Setting a wallpaper that already exists shows it; a new photo becomes a photo with
 * [uploadedId]. With [failure] every change fails and changes nothing, as a failed upload does.
 */
class FakeWallpaperRepository(
    defaults: AccountWallpapers = AccountWallpapers(),
    /** What [installed] answers. */
    var installedWallpapers: List<Wallpaper> = emptyList(),
) : WallpaperRepository {
    private val mutableDefaults = MutableStateFlow(defaults)
    private val chats = MutableStateFlow(emptyMap<Long, ChatWallpaperOverride>())

    /** What the changes fail with; null succeeds. */
    var failure: DomainError? = null

    /** The id a new photo gets once "uploaded". */
    var uploadedId: Long = UPLOADED_ID

    /** Every [setDefault] made, with its theme. */
    val defaultsSet = mutableListOf<Pair<WallpaperChoice, Boolean>>()

    /** Every [setChatWallpaper] made: the chat, the choice, the dimming and whether it was for the user alone. */
    val chatWallpapersSet = mutableListOf<ChatWallpaperSet>()

    override val defaults: StateFlow<AccountWallpapers> = mutableDefaults.asStateFlow()

    override fun chatWallpaper(chatId: Long): Flow<ChatWallpaperOverride?> =
        chats.map { it[chatId] }.distinctUntilChanged()

    /** Gives chat [chatId] [wallpaper] as its own, as an update from the other person would; null removes it. */
    fun setChat(
        chatId: Long,
        wallpaper: ChatWallpaperOverride?,
    ) = chats.update { if (wallpaper == null) it - chatId else it + (chatId to wallpaper) }

    override suspend fun installed(forDarkTheme: Boolean): Outcome<List<Wallpaper>> =
        failure?.let { Outcome.Failure(it) } ?: Outcome.Success(installedWallpapers)

    override suspend fun setDefault(
        choice: WallpaperChoice,
        forDarkTheme: Boolean,
    ): Outcome<Unit> {
        failure?.let { return Outcome.Failure(it) }
        defaultsSet += choice to forDarkTheme
        val set = wallpaperOf(choice)
        mutableDefaults.update { if (forDarkTheme) it.copy(dark = set) else it.copy(light = set) }
        return Outcome.Success(Unit)
    }

    override suspend fun removeDefault(forDarkTheme: Boolean): Outcome<Unit> {
        failure?.let { return Outcome.Failure(it) }
        mutableDefaults.update { if (forDarkTheme) it.copy(dark = null) else it.copy(light = null) }
        return Outcome.Success(Unit)
    }

    override suspend fun setChatWallpaper(
        chatId: Long,
        choice: WallpaperChoice,
        darkThemeDimming: Int,
        onlyForSelf: Boolean,
    ): Outcome<Unit> {
        failure?.let { return Outcome.Failure(it) }
        chatWallpapersSet += ChatWallpaperSet(chatId, choice, darkThemeDimming, onlyForSelf)
        setChat(chatId, ChatWallpaperOverride(wallpaperOf(choice), darkThemeDimming))
        return Outcome.Success(Unit)
    }

    override suspend fun removeChatWallpaper(chatId: Long): Outcome<Unit> {
        failure?.let { return Outcome.Failure(it) }
        setChat(chatId, null)
        return Outcome.Success(Unit)
    }

    private fun wallpaperOf(choice: WallpaperChoice): Wallpaper =
        when (choice) {
            is WallpaperChoice.Existing -> {
                (choice.wallpaper as? Wallpaper.Photo)?.copy(isBlurred = choice.isBlurred) ?: choice.wallpaper
            }

            is WallpaperChoice.NewPhoto -> {
                Wallpaper.Photo(uploadedId, UPLOADED_IMAGE, isBlurred = choice.isBlurred)
            }
        }

    /** One [setChatWallpaper] call. */
    data class ChatWallpaperSet(
        val chatId: Long,
        val choice: WallpaperChoice,
        val darkThemeDimming: Int,
        val onlyForSelf: Boolean,
    )

    companion object {
        const val UPLOADED_ID = 9_000L

        /** The image of an uploaded photo. */
        val UPLOADED_IMAGE = WallpaperImage(FileRef(9_001, 0))
    }
}
