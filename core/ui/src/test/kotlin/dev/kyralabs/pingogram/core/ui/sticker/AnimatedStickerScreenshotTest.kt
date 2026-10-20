package dev.kyralabs.pingogram.core.ui.sticker

import android.content.ComponentName
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import dev.kyralabs.pingogram.core.domain.model.FileState
import dev.kyralabs.pingogram.core.domain.model.ThemeMode
import dev.kyralabs.pingogram.core.ui.theme.PingogramTheme
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing
import kotlinx.coroutines.Dispatchers
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * The first frame of an animated sticker, as [AnimatedSticker] draws it on light and dark surfaces, captured once it
 * reported that frame (it renders off the composition, so a capture right after composing could come too early).
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class AnimatedStickerScreenshotTest {
    // Declares the activity to Robolectric, as ui-test-manifest would.
    @get:Rule(order = 0)
    val activityRegistration =
        object : ExternalResource() {
            override fun before() {
                val application = RuntimeEnvironment.getApplication()
                shadowOf(application.packageManager)
                    .addActivityIfNotPresent(ComponentName(application, ComponentActivity::class.java))
            }
        }

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule(order = 2)
    val folder = TemporaryFolder()

    @Test
    fun firstFrameLight() = capture("firstFrameLight", ThemeMode.Light)

    @Test
    fun firstFrameDark() = capture("firstFrameDark", ThemeMode.Dark)

    private fun capture(
        name: String,
        themeMode: ThemeMode,
    ) {
        val files = FakeStickerFiles().apply { state.value = FileState.Downloaded(pulseTgs(folder.root).path, 100) }
        // Previews and screenshots show the first frame only.
        val stickers = AnimatedStickers(files, LottieStickerDecoder(), Dispatchers.Unconfined, animate = false)
        var shown = false
        composeRule.setContent {
            PingogramTheme(themeMode = themeMode, dynamicColor = false) {
                Surface(color = MaterialTheme.colorScheme.surface) {
                    Box(Modifier.padding(MaterialTheme.spacing.large)) {
                        AnimatedSticker(
                            stickers = stickers,
                            fileId = 1,
                            modifier = Modifier.size(MaterialTheme.sizes.sticker),
                            onFirstFrame = { shown = true },
                        )
                    }
                }
            }
        }
        composeRule.waitUntil(TIMEOUT_MILLIS) { shown }
        composeRule.waitForIdle()
        composeRule.onRoot().captureRoboImage("src/test/snapshots/${javaClass.name}.$name.png")
    }

    private companion object {
        const val TIMEOUT_MILLIS = 5_000L
    }
}
