package dev.kyralabs.pingogram.core.data.repository

import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.ChatWallpaperOverride
import dev.kyralabs.pingogram.core.domain.model.FileRef
import dev.kyralabs.pingogram.core.domain.model.Wallpaper
import dev.kyralabs.pingogram.core.domain.model.WallpaperChoice
import dev.kyralabs.pingogram.core.domain.model.WallpaperImage
import dev.kyralabs.pingogram.core.tdlib.TdError
import dev.kyralabs.pingogram.core.tdlib.TdResult
import dev.kyralabs.pingogram.core.testing.FakeTdlibClient
import dev.kyralabs.pingogram.core.testing.TdApiFixtures
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.drinkless.tdlib.TdApi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The account's wallpapers and the chats' own, from TDLib's updates and the chats' service messages, and setting and
 * removing them, shown at once where the wallpaper is known.
 */
class TdlibWallpaperRepositoryTest {
    private val client = FakeTdlibClient()

    private fun TestScope.repository() = TdlibWallpaperRepository(client, backgroundScope)

    @Test
    fun `keeps the account's wallpaper for each theme as TDLib announces it`() =
        runTest {
            val repository = repository()
            client.emit(TdApi.UpdateDefaultBackground(false, photoBackground(id = 1, blurred = true)))
            client.emit(TdApi.UpdateDefaultBackground(true, patternBackground(id = 2)))

            assertEquals(Wallpaper.Photo(1, PHOTO_IMAGE, isBlurred = true), repository.defaults.value.light)
            assertEquals(
                Wallpaper.Pattern(2, PATTERN_IMAGE, intensity = 50, isInverted = true),
                repository.defaults.value.dark,
            )

            client.emit(TdApi.UpdateDefaultBackground(true, null))
            assertNull(repository.defaults.value.dark)
        }

    @Test
    fun `maps fills, and leaves out chat themes`() =
        runTest {
            client.on(
                TdApi.GetInstalledBackgrounds::class,
                TdResult.Success(
                    TdApi.Backgrounds(
                        arrayOf(
                            background(
                                3,
                                TdApi.BackgroundTypeFill(TdApi.BackgroundFillGradient(0x112233, 0x445566, 45)),
                            ),
                            background(
                                4,
                                TdApi.BackgroundTypeFill(TdApi.BackgroundFillFreeformGradient(intArrayOf(1, 2, 3))),
                            ),
                            background(5, TdApi.BackgroundTypeChatTheme("🎄")),
                        ),
                    ),
                ),
            )

            val installed = repository().installed(forDarkTheme = true)

            assertEquals(
                Outcome.Success(
                    listOf(Wallpaper.Fill(3, listOf(0x112233, 0x445566), 45), Wallpaper.Fill(4, listOf(1, 2, 3))),
                ),
                installed,
            )
            assertTrue(client.sentOf<TdApi.GetInstalledBackgrounds>().single().forDarkTheme)
        }

    @Test
    fun `sets one of Telegram's patterns on the theme's fill, and shows it at once`() =
        runTest {
            val repository = repository()
            val pattern = Wallpaper.Pattern(2, PATTERN_IMAGE, intensity = 50, isInverted = false)
            client.on(TdApi.SetDefaultBackground::class, TdResult.Success(patternBackground(id = 2, inverted = false)))

            val set =
                repository.setDefault(
                    WallpaperChoice.Existing(pattern, patternFill = listOf(0xAABBCC, 0x445566)),
                    false,
                )

            assertEquals(Outcome.Success(2L), set)
            assertEquals(pattern, repository.defaults.value.light)
            with(client.sentOf<TdApi.SetDefaultBackground>().single()) {
                assertEquals(2L, (background as TdApi.InputBackgroundRemote).backgroundId)
                val type = type as TdApi.BackgroundTypePattern
                assertEquals(50, type.intensity)
                val fill = type.fill as TdApi.BackgroundFillGradient
                assertEquals(0xAABBCC to 0x445566, fill.topColor to fill.bottomColor)
                assertFalse(forDarkTheme)
            }
        }

    @Test
    fun `uploads a new photo, blurred, for dark theme, and answers its id`() =
        runTest {
            client.on(TdApi.SetDefaultBackground::class, TdResult.Success(photoBackground(id = 9, blurred = true)))

            val set = repository().setDefault(WallpaperChoice.NewPhoto("/cache/wallpaper.jpg", isBlurred = true), true)

            assertEquals(Outcome.Success(9L), set)
            with(client.sentOf<TdApi.SetDefaultBackground>().single()) {
                val file = (background as TdApi.InputBackgroundLocal).background as TdApi.InputFileLocal
                assertEquals("/cache/wallpaper.jpg", file.path)
                assertTrue((type as TdApi.BackgroundTypeWallpaper).isBlurred)
                assertTrue(forDarkTheme)
            }
        }

    @Test
    fun `a failed change keeps the wallpaper there was`() =
        runTest {
            val repository = repository()
            client.emit(TdApi.UpdateDefaultBackground(false, photoBackground(id = 1)))
            client.on(TdApi.SetDefaultBackground::class, TdResult.Failure(TdError(400, "WALLPAPER_FILE_INVALID")))
            client.on(TdApi.DeleteDefaultBackground::class, TdResult.Failure(TdError(400, "SOMETHING")))

            val photo = WallpaperChoice.NewPhoto("/cache/wallpaper.jpg")
            assertInstanceOf(Outcome.Failure::class.java, repository.setDefault(photo, false))
            assertInstanceOf(Outcome.Failure::class.java, repository.removeDefault(false))

            assertEquals(
                1L,
                repository.defaults.value.light
                    ?.id,
            )
        }

    @Test
    fun `removing the account's wallpaper shows the theme's again`() =
        runTest {
            val repository = repository()
            client.emit(TdApi.UpdateDefaultBackground(true, photoBackground(id = 1)))
            client.on(TdApi.DeleteDefaultBackground::class, TdResult.Success(TdApi.Ok()))

            assertEquals(Outcome.Success(Unit), repository.removeDefault(forDarkTheme = true))

            assertNull(repository.defaults.value.dark)
            assertTrue(client.sentOf<TdApi.DeleteDefaultBackground>().single().forDarkTheme)
        }

    @Test
    fun `follows a chat's own wallpaper from its first announcement`() =
        runTest {
            val repository = repository()
            val chat =
                TdApiFixtures
                    .chat(
                        id = 7,
                    ).apply { background = TdApi.ChatBackground(photoBackground(id = 1), 30) }
            client.emit(TdApi.UpdateNewChat(chat))

            assertEquals(
                ChatWallpaperOverride(Wallpaper.Photo(1, PHOTO_IMAGE, isBlurred = false), darkThemeDimming = 30),
                repository.chatWallpaper(7).first(),
            )

            client.emit(TdApi.UpdateChatBackground(7, null))
            assertNull(repository.chatWallpaper(7).first())
        }

    @Test
    fun `sets a chat's wallpaper for the user alone, and resets it without restoring the previous one`() =
        runTest {
            val repository = repository()
            client.on(TdApi.SetChatBackground::class, TdResult.Success(TdApi.Ok()))
            client.on(TdApi.DeleteChatBackground::class, TdResult.Success(TdApi.Ok()))
            val photo = Wallpaper.Photo(1, PHOTO_IMAGE, isBlurred = false)

            repository.setChatWallpaper(7, WallpaperChoice.Existing(photo), darkThemeDimming = 140, onlyForSelf = true)
            repository.removeChatWallpaper(7)

            with(client.sentOf<TdApi.SetChatBackground>().single()) {
                assertEquals(7L, chatId)
                assertEquals(100, darkThemeDimming)
                assertTrue(onlyForSelf)
            }
            assertFalse(client.sentOf<TdApi.DeleteChatBackground>().single().restorePrevious)
        }

    @Test
    fun `a chat's flow shows a wallpaper on Telegram as soon as it is set`() =
        runTest {
            val repository = repository()
            client.on(TdApi.SetChatBackground::class, TdResult.Success(TdApi.Ok()))
            val photo = Wallpaper.Photo(1, PHOTO_IMAGE, isBlurred = false)

            repository.setChatWallpaper(7, WallpaperChoice.Existing(photo, isBlurred = true), 40, onlyForSelf = true)

            assertEquals(ChatWallpaperOverride(photo.copy(isBlurred = true), 40), repository.chatWallpaper(7).first())
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `a new photo set for Saved Messages shows when its service message arrives`() =
        runTest {
            val repository = repository()
            client.on(TdApi.SetChatBackground::class, TdResult.Success(TdApi.Ok()))
            val shown = mutableListOf<ChatWallpaperOverride?>()
            val dispatcher = UnconfinedTestDispatcher(testScheduler)
            backgroundScope.launch(dispatcher) { repository.chatWallpaper(ME).toList(shown) }

            repository.setChatWallpaper(ME, WallpaperChoice.NewPhoto("/cache/photo.jpg"), 30, onlyForSelf = true)
            val mine = setBackground(ME, photoBackground(id = 9), onlyForSelf = true, isOutgoing = true)
            client.emit(TdApiFixtures.updateNewMessage(mine))

            val photo = ChatWallpaperOverride(Wallpaper.Photo(9, PHOTO_IMAGE, isBlurred = false), 30)
            assertEquals(listOf(null, photo), shown)
        }

    @Test
    fun `another's wallpaper for both shows from their service message, and other messages change nothing`() =
        runTest {
            val repository = repository()
            val forBoth = setBackground(7, patternBackground(id = 2), onlyForSelf = false)
            client.emit(TdApiFixtures.updateNewMessage(forBoth))
            val before = repository.chatWallpaper(7).first()

            client.emit(TdApiFixtures.updateNewMessage(TdApiFixtures.message(chatId = 7)))

            assertEquals(Wallpaper.Pattern(2, PATTERN_IMAGE, intensity = 50, isInverted = true), before?.wallpaper)
            assertSame(before, repository.chatWallpaper(7).first())
        }

    @Test
    fun `the other person's wallpaper for themselves alone stays theirs, one for both and the user's own apply`() =
        runTest {
            val repository = repository()

            suspend fun emit(message: TdApi.Message) = client.emit(TdApiFixtures.updateNewMessage(message))

            emit(setBackground(7, photoBackground(id = 1), onlyForSelf = false))
            val forBoth = repository.chatWallpaper(7).first()
            emit(setBackground(7, patternBackground(id = 2), onlyForSelf = true))
            val afterTheirs = repository.chatWallpaper(7).first()
            emit(setBackground(7, photoBackground(id = 3), onlyForSelf = true, isOutgoing = true))

            assertEquals(1L, forBoth?.wallpaper?.id)
            assertSame(forBoth, afterTheirs)
            assertEquals(
                3L,
                repository
                    .chatWallpaper(7)
                    .first()
                    ?.wallpaper
                    ?.id,
            )
        }

    private companion object {
        const val ME = 1L
        val PHOTO_IMAGE = WallpaperImage(FileRef(60, 1_024), thumbnail = FileRef(61, 1_024))
        val PATTERN_IMAGE = WallpaperImage(FileRef(70, 1_024), isVector = true)

        /** A `messageChatSetBackground` in [chatId] setting [background] dimmed by 30%, the user's if [isOutgoing]. */
        fun setBackground(
            chatId: Long,
            background: TdApi.Background,
            onlyForSelf: Boolean,
            isOutgoing: Boolean = false,
        ) = TdApiFixtures.message(
            chatId = chatId,
            content = TdApi.MessageChatSetBackground(0, TdApi.ChatBackground(background, 30), onlyForSelf),
            isOutgoing = isOutgoing,
        )

        fun background(
            id: Long,
            type: TdApi.BackgroundType,
            document: TdApi.Document? = null,
        ) = TdApi.Background(id, false, false, "name$id", document, type)

        fun photoBackground(
            id: Long,
            blurred: Boolean = false,
        ) = background(
            id,
            TdApi.BackgroundTypeWallpaper(blurred, false),
            TdApiFixtures.document(
                mimeType = "image/jpeg",
                thumbnail = TdApiFixtures.thumbnail(file = TdApiFixtures.file(id = 61)),
                file = TdApiFixtures.file(id = 60),
            ),
        )

        fun patternBackground(
            id: Long,
            inverted: Boolean = true,
        ) = background(
            id,
            TdApi.BackgroundTypePattern(TdApi.BackgroundFillSolid(0), 50, inverted, false),
            TdApiFixtures.document(mimeType = "application/x-tgwallpattern", file = TdApiFixtures.file(id = 70)),
        )
    }
}
