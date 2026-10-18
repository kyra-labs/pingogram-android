package dev.kyralabs.pingogram.feature.chat.ui.content

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalInspectionMode
import com.github.takahirom.roborazzi.captureRoboImage
import dev.kyralabs.pingogram.core.domain.model.ThemeMode
import dev.kyralabs.pingogram.core.ui.theme.PingogramTheme
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Goldens of every content component state ([ContentSamples]) on a compact phone (`docs/TESTING.md`), light and dark,
 * in sample bubbles of both roles where the content has a bubble. Brand colors, so goldens do not depend on a
 * wallpaper; inspection mode, so Coil shows the samples' preview images (solid colors) instead of loading TDLib files.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class MessageContentScreenshotTest(
    private val sampleName: String,
) {
    @Test
    fun light() = capture("light", ThemeMode.Light)

    @Test
    fun dark() = capture("dark", ThemeMode.Dark)

    private fun capture(
        variant: String,
        themeMode: ThemeMode,
    ) {
        val sample = ContentSamples.single { it.name == sampleName }
        captureRoboImage("src/test/snapshots/${javaClass.name}.$sampleName.$variant.png") {
            CompositionLocalProvider(LocalInspectionMode provides true) {
                PingogramTheme(themeMode = themeMode, dynamicColor = false) {
                    Surface(color = MaterialTheme.colorScheme.background) { ContentSampleFrame(sample) }
                }
            }
        }
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun samples(): List<Array<Any>> = ContentSamples.map { arrayOf(it.name) }
    }
}
