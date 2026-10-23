package dev.kyralabs.pingogram.feature.chats.ui

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.annotation.DelicateCoilApi
import coil3.asImage
import coil3.decode.DataSource
import coil3.fetch.Fetcher
import coil3.fetch.ImageFetchResult
import coil3.request.allowHardware
import coil3.serviceLoaderEnabled
import dev.kyralabs.pingogram.core.domain.model.ConnectionStatus
import dev.kyralabs.pingogram.core.domain.model.FileRef
import dev.kyralabs.pingogram.core.domain.model.ProfilePhoto
import dev.kyralabs.pingogram.core.ui.component.rememberScrollingListState
import dev.kyralabs.pingogram.core.ui.image.TdlibFileImage
import dev.kyralabs.pingogram.core.ui.image.TdlibFileImageKeyer
import dev.kyralabs.pingogram.core.ui.theme.PingogramTheme
import dev.kyralabs.pingogram.feature.chats.ui.preview.ChatListPreviewData
import dev.kyralabs.pingogram.feature.chats.ui.stories.StoryRingsPreviewData
import dev.kyralabs.pingogram.feature.chats.ui.stories.StoryRingsRow
import kotlinx.collections.immutable.toImmutableList
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.lang.management.ManagementFactory

/**
 * What the chat list's scroll frames cost on the JVM (`docs/PERFORMANCE.md` §9): the main thread's CPU time per frame
 * while the list scrolls by [STEP] px a frame through 600 rows below the story rings, four in five with a profile
 * photo ([photos]), after the JIT warmed up, printed as percentiles and the mean, with Pingogram's list state
 * ([rememberScrollingListState]) and with Compose's default one. A
 * measurement, not a check: it runs only with `-Ppingogram.scrollCost` (`./gradlew :feature:chats:testDebugUnitTest
 * -Ppingogram.scrollCost --tests '*ScrollCostTest*' --rerun`), and its numbers are relative: compare two runs on the
 * same machine, each test in its own run, as the JIT carries over between tests of one run. Robolectric neither draws
 * nor runs Compose's prefetch between frames as a phone does, so only composition and measuring are seen.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class ChatListScrollCostTest {
    @get:Rule(order = 0)
    val componentActivity = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var listState: LazyListState

    @Before
    fun onlyWhenAsked() = assumeTrue(System.getProperty(SCROLL_COST_PROPERTY).toBoolean())

    /**
     * Photos for most rows' avatars, as on a phone: Coil loads them through the app's keyer and a fetcher that hands
     * back a decoded picture at once, and keeps them in its memory cache, so rows that come back show theirs at once,
     * as after a first fling on a phone.
     */
    @OptIn(DelicateCoilApi::class)
    @Before
    fun photos() {
        val picture =
            Bitmap.createBitmap(PHOTO_PX, PHOTO_PX, Bitmap.Config.ARGB_8888).apply { eraseColor(PHOTO_COLOR) }
        val fetcher = Fetcher { ImageFetchResult(picture.asImage(), isSampled = false, DataSource.DISK) }
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
    fun noPhotos() = SingletonImageLoader.reset()

    @Test
    fun `scroll frames with the list's own state`() = measure(scrollingState = true)

    @Test
    fun `scroll frames with Compose's default state`() = measure(scrollingState = false)

    private fun measure(scrollingState: Boolean) {
        val items =
            (0 until ROWS)
                .map { index ->
                    val base = ChatListPreviewData.items[index % ChatListPreviewData.items.size]
                    // A photo for most chats, as on a phone, from a few distinct files Coil's memory cache soon holds.
                    val photo =
                        if (index % PLACEHOLDER_EVERY == 0) {
                            null
                        } else {
                            val fileId = index % DISTINCT_PHOTOS + 1
                            ProfilePhoto(FileRef(fileId, 0), FileRef(fileId + DISTINCT_PHOTOS, 0), minithumbnail = null)
                        }
                    base.copy(id = FIRST_ID + index, photo = photo)
                }.toImmutableList()
        composeRule.setContent {
            PingogramTheme(dynamicColor = false) {
                listState = if (scrollingState) rememberScrollingListState() else rememberLazyListState()
                ChatListScreen(
                    state =
                        ChatListUiState(
                            content = ChatListContent.Content(items),
                            connection = ConnectionStatus.Ready,
                            folderTabs = ChatListPreviewData.folders,
                        ),
                    onEvent = {},
                    listState = listState,
                    // The Chats tab's first row, as it has stories.
                    stories = { modifier ->
                        StoryRingsRow(
                            rings = StoryRingsPreviewData.rings,
                            onOpen = {},
                            onNearEnd = {},
                            modifier = modifier,
                        )
                    },
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.mainClock.autoAdvance = false
        repeat(WARMUP_RUNS) { scroll() }
        val frames = (1..MEASURED_RUNS).flatMap { scroll() }.sorted()
        println(
            "Scroll cost (${if (scrollingState) "scrolling" else "default"} state): CPU per frame P50 " +
                "${frames.percentile(50)} µs, P90 ${frames.percentile(90)} µs, P99 ${frames.percentile(99)} µs, " +
                "mean ${frames.average().toLong()} µs",
        )
    }

    /** [FRAMES] frames scrolling by [STEP] px each; the main thread's CPU time of each, scroll and frame, in µs. */
    private fun scroll(): List<Long> {
        val threads = ManagementFactory.getThreadMXBean()
        return List(FRAMES) {
            val start = threads.currentThreadCpuTime
            // Scrolling measures the list at once, composing what comes into view, before the frame.
            composeRule.runOnIdle { listState.dispatchRawDelta(STEP) }
            composeRule.mainClock.advanceTimeByFrame()
            composeRule.waitForIdle()
            (threads.currentThreadCpuTime - start) / NANOS_PER_MICRO
        }
    }

    private fun List<Long>.percentile(percent: Int) = this[(size - 1) * percent / PERCENT]

    private companion object {
        const val ROWS = 600
        const val FIRST_ID = 1_000L
        const val FRAMES = 120
        const val STEP = 40f
        const val WARMUP_RUNS = 3
        const val MEASURED_RUNS = 8
        const val NANOS_PER_MICRO = 1_000
        const val PERCENT = 100
        const val SCROLL_COST_PROPERTY = "pingogram.scrollCost"
        const val PLACEHOLDER_EVERY = 5
        const val DISTINCT_PHOTOS = 24
        const val PHOTO_PX = 160
        const val PHOTO_COLOR = 0xFF5577AA.toInt()
    }
}
