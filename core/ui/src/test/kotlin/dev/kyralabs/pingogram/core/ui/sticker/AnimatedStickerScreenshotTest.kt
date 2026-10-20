package dev.kyralabs.pingogram.core.ui.sticker

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.github.takahirom.roborazzi.captureRoboImage
import dev.kyralabs.pingogram.core.domain.model.FileState
import dev.kyralabs.pingogram.core.domain.model.ThemeMode
import dev.kyralabs.pingogram.core.ui.theme.PingogramTheme
import dev.kyralabs.pingogram.core.ui.theme.sizes
import kotlinx.coroutines.Dispatchers
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The first frame of an animated sticker, as [AnimatedSticker] draws it on light and dark backgrounds. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class AnimatedStickerScreenshotTest {
    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun firstFrameLight() = capture(ThemeMode.Light)

    @Test
    fun firstFrameDark() = capture(ThemeMode.Dark)

    private fun capture(themeMode: ThemeMode) {
        val files = FakeStickerFiles().apply { state.value = FileState.Downloaded(pulseTgs(folder.root).path, 100) }
        // Previews and screenshots show the first frame only.
        val stickers = AnimatedStickers(files, LottieStickerDecoder(), Dispatchers.Unconfined, animate = false)
        captureRoboImage {
            PingogramTheme(themeMode = themeMode, dynamicColor = false) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        AnimatedSticker(stickers, fileId = 1, modifier = Modifier.size(MaterialTheme.sizes.sticker))
                    }
                }
            }
        }
    }
}
