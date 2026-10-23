package dev.kyralabs.pingogram.feature.calls.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import com.github.takahirom.roborazzi.captureRoboImage
import dev.kyralabs.pingogram.core.domain.model.CallProblem
import dev.kyralabs.pingogram.core.domain.model.Contrast
import dev.kyralabs.pingogram.core.domain.model.ThemeMode
import dev.kyralabs.pingogram.core.ui.theme.PingogramTheme
import dev.kyralabs.pingogram.core.ui.theme.spacing
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Goldens of "Rate this call" (`docs/TESTING.md`), its content on the sheet's container as `ModalBottomSheet` draws
 * it: no star yet, two stars with problems picked in a video call, five stars; light, dark, AMOLED, high contrast,
 * right to left, 200% font scale and a 700 dp window. Brand colors.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class CallRatingScreenshotTest {
    @Test
    fun unratedLight() = capture()

    @Test
    fun unratedDark() = capture(themeMode = ThemeMode.Dark)

    @Test
    fun problemsLight() = capture(stars = 2, problems = PROBLEMS, isVideo = true)

    @Test
    fun problemsDark() = capture(stars = 2, problems = PROBLEMS, isVideo = true, themeMode = ThemeMode.Dark)

    @Test
    fun problemsAmoled() =
        capture(stars = 2, problems = PROBLEMS, isVideo = true, themeMode = ThemeMode.Dark, amoled = true)

    @Test
    fun problemsHighContrast() = capture(stars = 2, problems = PROBLEMS, isVideo = true, contrast = Contrast.High)

    @Test
    fun problemsRtl() = capture(stars = 2, problems = PROBLEMS, isVideo = true, layoutDirection = LayoutDirection.Rtl)

    @Test
    @Config(fontScale = 2f)
    fun problemsFontScale200() = capture(stars = 2, problems = PROBLEMS, isVideo = true)

    @Test
    @Config(qualifiers = "w700dp-h891dp-xxhdpi")
    fun voiceProblemsMedium() = capture(stars = 3)

    @Test
    fun fiveStarsLight() = capture(stars = 5)

    @Suppress("LongParameterList") // One golden per theme variant and state.
    private fun capture(
        stars: Int = 0,
        problems: Set<CallProblem> = emptySet(),
        isVideo: Boolean = false,
        themeMode: ThemeMode = ThemeMode.Light,
        amoled: Boolean = false,
        contrast: Contrast = Contrast.Standard,
        layoutDirection: LayoutDirection = LayoutDirection.Ltr,
    ) = captureRoboImage {
        CompositionLocalProvider(LocalLayoutDirection provides layoutDirection) {
            PingogramTheme(themeMode = themeMode, dynamicColor = false, amoled = amoled, contrast = contrast) {
                SheetSurface {
                    CallRatingContent(
                        isVideo = isVideo,
                        onSend = { _, _ -> },
                        onNotNow = {},
                        initialStars = stars,
                        initialProblems = problems,
                    )
                }
            }
        }
    }

    private companion object {
        val PROBLEMS = setOf(CallProblem.Echo, CallProblem.PixelatedVideo)
    }
}

/** [content] on a bottom sheet's container, its top corners and padding, as in the sheet's window. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SheetSurface(content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = BottomSheetDefaults.ExpandedShape,
        color = BottomSheetDefaults.ContainerColor,
    ) {
        Box(Modifier.padding(top = MaterialTheme.spacing.extraLarge)) { content() }
    }
}
