package dev.kyralabs.pingogram.feature.chat.ui.content

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.annotation.DelicateCoilApi
import coil3.asImage
import coil3.decode.DataSource
import coil3.fetch.Fetcher
import coil3.fetch.ImageFetchResult
import coil3.request.allowHardware
import coil3.serviceLoaderEnabled
import dev.kyralabs.pingogram.core.domain.model.FileRef
import dev.kyralabs.pingogram.core.domain.model.Sticker
import dev.kyralabs.pingogram.core.domain.model.StickerFormat
import dev.kyralabs.pingogram.core.ui.image.TdlibFileImage
import dev.kyralabs.pingogram.core.ui.image.TdlibFileImageKeyer
import dev.kyralabs.pingogram.core.ui.theme.PingogramTheme
import dev.kyralabs.pingogram.feature.chat.ui.COMPACT
import dev.kyralabs.pingogram.feature.chat.ui.ComponentActivityRegistration
import kotlinx.coroutines.CompletableDeferred
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A still sticker's emoji stands in for it while its picture loads and fades in, and is gone, not composed at alpha 0
 * under the picture, once it has (`docs/PERFORMANCE.md` §5); without a picture it stays.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class StickerImageTest {
    @get:Rule(order = 0)
    val componentActivity = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    /** Completed when the picture may load: until then Coil waits for it, as for a download. */
    private val loaded = CompletableDeferred<Unit>()

    @OptIn(DelicateCoilApi::class)
    private fun pictures() {
        val picture = Bitmap.createBitmap(PICTURE_PX, PICTURE_PX, Bitmap.Config.ARGB_8888)
        val fetcher =
            Fetcher {
                loaded.await()
                ImageFetchResult(picture.asImage(), isSampled = false, DataSource.DISK)
            }
        SingletonImageLoader.setUnsafe(
            ImageLoader
                .Builder(composeRule.activity)
                .components {
                    add(TdlibFileImageKeyer())
                    add(Fetcher.Factory<TdlibFileImage> { _, _, _ -> fetcher })
                }.allowHardware(false)
                .serviceLoaderEnabled(false)
                .build(),
        )
    }

    @OptIn(DelicateCoilApi::class)
    @After
    fun noPictures() = SingletonImageLoader.reset()

    @Test
    fun `a still sticker's emoji shows until its picture has faded in, then leaves the composition`() {
        pictures()
        composeRule.setContent {
            PingogramTheme(dynamicColor = false) { StickerImage(STILL, DpSize(SIDE.dp, SIDE.dp)) }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText(EMOJI).assertExists()

        loaded.complete(Unit)

        // Coil decodes off the main thread; the emoji goes once the picture has faded in.
        composeRule.waitUntil(LOAD_TIMEOUT_MILLIS) {
            composeRule.onAllNodesWithText(EMOJI).fetchSemanticsNodes().isEmpty()
        }
        composeRule.onNodeWithText(EMOJI).assertDoesNotExist()
    }

    @Test
    fun `a sticker without a picture keeps its emoji`() {
        val noPicture = STILL.copy(format = StickerFormat.Webm, thumbnail = null)
        composeRule.setContent {
            PingogramTheme(dynamicColor = false) { StickerImage(noPicture, DpSize(SIDE.dp, SIDE.dp)) }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText(EMOJI).assertExists()
    }

    private companion object {
        const val EMOJI = "🐱"
        const val SIDE = 160
        const val PICTURE_PX = 64
        const val LOAD_TIMEOUT_MILLIS = 5_000L
        val STILL =
            Sticker(
                setId = 1,
                width = 512,
                height = 512,
                emoji = EMOJI,
                format = StickerFormat.Webp,
                thumbnail = null,
                file = FileRef(7, 1_000),
            )
    }
}
