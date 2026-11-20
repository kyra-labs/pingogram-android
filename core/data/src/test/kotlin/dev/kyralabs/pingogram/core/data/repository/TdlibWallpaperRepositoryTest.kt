package dev.kyralabs.pingogram.core.data.repository

import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.FileRef
import dev.kyralabs.pingogram.core.domain.model.Wallpaper
import dev.kyralabs.pingogram.core.domain.model.WallpaperChoice
import dev.kyralabs.pingogram.core.domain.model.WallpaperImage
import dev.kyralabs.pingogram.core.tdlib.TdError
import dev.kyralabs.pingogram.core.tdlib.TdResult
import dev.kyralabs.pingogram.core.testing.FakeTdlibClient
import dev.kyralabs.pingogram.core.testing.TdApiFixtures
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.drinkless.tdlib.TdApi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** The account's wallpapers from TDLib's updates, the installed ones, and setting and removing them. */
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

            assertEquals(Outcome.Success(Unit), set)
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
    fun `uploads a new photo, blurred, for dark theme`() =
        runTest {
            client.on(TdApi.SetDefaultBackground::class, TdResult.Success(photoBackground(id = 9, blurred = true)))

            repository().setDefault(WallpaperChoice.NewPhoto("/cache/wallpaper.jpg", isBlurred = true), true)

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

    private companion object {
        val PHOTO_IMAGE = WallpaperImage(FileRef(60, 1_024), thumbnail = FileRef(61, 1_024))
        val PATTERN_IMAGE = WallpaperImage(FileRef(70, 1_024), isVector = true)

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
