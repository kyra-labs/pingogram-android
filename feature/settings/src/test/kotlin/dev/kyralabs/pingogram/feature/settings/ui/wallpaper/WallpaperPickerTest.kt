package dev.kyralabs.pingogram.feature.settings.ui.wallpaper

import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.FileRef
import dev.kyralabs.pingogram.core.domain.model.Wallpaper
import dev.kyralabs.pingogram.core.domain.model.WallpaperChoice
import dev.kyralabs.pingogram.core.domain.model.WallpaperImage
import dev.kyralabs.pingogram.core.testing.FakePickedPhotoRepository
import dev.kyralabs.pingogram.core.testing.FakeWallpaperRepository
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/** Picking a wallpaper: installed ones per theme, a photo, blur, applying, and failures that change nothing. */
@OptIn(ExperimentalCoroutinesApi::class)
class WallpaperPickerTest {
    private val wallpapers = FakeWallpaperRepository(installedWallpapers = listOf(PHOTO, PATTERN, FILL))
    private val photos = FakePickedPhotoRepository()
    private val errors = mutableListOf<DomainError>()

    private fun TestScope.picker() = WallpaperPicker(backgroundScope, wallpapers, photos) { errors += it }

    @Test
    fun `shows the installed photos and patterns for the theme, without fills`() =
        runTest {
            val picker = picker()
            picker.showTheme(dark = true)
            runCurrent()

            assertEquals(true, picker.state.value.forDarkTheme)
            assertEquals(InstalledWallpapers.Loaded(persistentListOf(PHOTO, PATTERN)), picker.state.value.installed)
        }

    @Test
    fun `installed wallpapers that cannot load can be tried again`() =
        runTest {
            val picker = picker()
            wallpapers.failure = DomainError.Network
            picker.showTheme(dark = false)
            runCurrent()
            assertEquals(InstalledWallpapers.Failed, picker.state.value.installed)

            wallpapers.failure = null
            picker.retry()
            runCurrent()
            assertEquals(InstalledWallpapers.Loaded(persistentListOf(PHOTO, PATTERN)), picker.state.value.installed)
        }

    @Test
    fun `a picked photo is previewed, blurred on request, applied for the theme, and its copy deleted`() =
        runTest {
            val picker = picker()
            picker.showTheme(dark = true)
            picker.pickPhoto("content://photo", SCREEN_SIDE)
            runCurrent()
            picker.setBlurred(true)

            val photo = WallpaperChoice.NewPhoto("/cache/uploads/content://photo.jpg", isBlurred = true)
            assertEquals(photo, picker.state.value.pending)

            picker.apply(emptyList(), wallpapers::setDefault)
            runCurrent()

            assertEquals(listOf<Pair<WallpaperChoice, Boolean>>(photo to true), wallpapers.defaultsSet)
            assertNull(picker.state.value.pending)
            assertFalse(picker.state.value.isWorking)
            assertEquals(listOf(photo.path), photos.discarded)
        }

    @Test
    fun `a pattern is applied on the theme's colors`() =
        runTest {
            val picker = picker()
            picker.showTheme(dark = false)
            picker.pick(PATTERN)
            picker.apply(listOf(0x112233), wallpapers::setDefault)
            runCurrent()

            assertEquals(
                WallpaperChoice.Existing(PATTERN, patternFill = listOf(0x112233)),
                wallpapers.defaultsSet.single().first,
            )
        }

    @Test
    fun `a failed upload keeps the pick and the wallpaper there was, and says why`() =
        runTest {
            val picker = picker()
            picker.showTheme(dark = false)
            runCurrent()
            picker.pick(PHOTO)
            wallpapers.failure = DomainError.Network

            picker.apply(emptyList(), wallpapers::setDefault)
            runCurrent()

            assertEquals(listOf<DomainError>(DomainError.Network), errors)
            assertEquals(WallpaperChoice.Existing(PHOTO), picker.state.value.pending)
            assertNull(wallpapers.defaults.value.light)
        }

    @Test
    fun `a photo that cannot be read says so and previews nothing`() =
        runTest {
            val picker = picker()
            photos.onPreparePhoto = { Outcome.Failure(DomainError.PhotoInvalid) }

            picker.pickPhoto("content://broken", SCREEN_SIDE)
            runCurrent()

            assertEquals(listOf<DomainError>(DomainError.PhotoInvalid), errors)
            assertNull(picker.state.value.pending)
            assertFalse(picker.state.value.isWorking)
        }

    @Test
    fun `switching theme or cancelling drops the pick and deletes a photo's copy`() =
        runTest {
            val picker = picker()
            picker.showTheme(dark = false)
            picker.pickPhoto("content://a", SCREEN_SIDE)
            runCurrent()

            picker.showTheme(dark = true)

            assertNull(picker.state.value.pending)
            assertEquals(listOf("/cache/uploads/content://a.jpg"), photos.discarded)

            picker.pickPhoto("content://b", SCREEN_SIDE)
            runCurrent()
            picker.cancel()
            assertEquals(2, photos.discarded.size)
        }

    private companion object {
        val PHOTO = Wallpaper.Photo(1, WallpaperImage(FileRef(10, 0)), isBlurred = false)
        val PATTERN = Wallpaper.Pattern(2, WallpaperImage(FileRef(20, 0), isVector = true), 50, false)
        val FILL = Wallpaper.Fill(3, listOf(0xFFFFFF))
        const val SCREEN_SIDE = 2400
    }
}
