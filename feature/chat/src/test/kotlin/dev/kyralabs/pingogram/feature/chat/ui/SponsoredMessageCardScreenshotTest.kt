package dev.kyralabs.pingogram.feature.chat.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalInspectionMode
import com.github.takahirom.roborazzi.captureRoboImage
import dev.kyralabs.pingogram.core.domain.model.ThemeMode
import dev.kyralabs.pingogram.core.ui.theme.PingogramTheme
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.chat.ui.content.PreviewImages
import dev.kyralabs.pingogram.feature.chat.ui.preview.SponsoredPreviewData
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Goldens of the sponsored message's card on a compact phone (`docs/TESTING.md`), light and dark: a text with
 * formatting and a link, a recommended channel, and a photo with the sponsor's photo. Brand colors; inspection mode,
 * so the photos are the preview images' solid colors.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class SponsoredMessageCardScreenshotTest(
    private val cardName: String,
) {
    @Test
    fun light() = capture("light", ThemeMode.Light)

    @Test
    fun dark() = capture("dark", ThemeMode.Dark)

    private fun capture(
        variant: String,
        themeMode: ThemeMode,
    ) {
        val card = CARDS.getValue(cardName)
        captureRoboImage(goldenPath(javaClass, "$cardName.$variant")) {
            CompositionLocalProvider(LocalInspectionMode provides true) {
                PingogramTheme(themeMode = themeMode, dynamicColor = false) {
                    Surface(color = MaterialTheme.colorScheme.surface) {
                        PreviewImages {
                            SponsoredMessageCard(
                                card = card,
                                onEvent = {},
                                modifier = Modifier.padding(MaterialTheme.spacing.medium),
                            )
                        }
                    }
                }
            }
        }
    }

    companion object {
        val CARDS =
            mapOf(
                "text" to SponsoredPreviewData.text,
                "recommended" to SponsoredPreviewData.recommended,
                "photo" to SponsoredPreviewData.photo,
            )

        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun cards(): List<Array<Any>> = CARDS.keys.map { arrayOf(it) }
    }
}
