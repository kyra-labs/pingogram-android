package dev.kyralabs.pingogram.ui

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.performClick
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import dagger.hilt.android.testing.UninstallModules
import dev.kyralabs.pingogram.MainActivityUiState
import dev.kyralabs.pingogram.core.navigation.ChatRoute
import dev.kyralabs.pingogram.core.navigation.MediaViewerRoute
import dev.kyralabs.pingogram.core.tdlib.AuthState
import dev.kyralabs.pingogram.core.tdlib.ConnectionState
import dev.kyralabs.pingogram.core.tdlib.TdError
import dev.kyralabs.pingogram.core.tdlib.TdResult
import dev.kyralabs.pingogram.core.tdlib.TdlibClient
import dev.kyralabs.pingogram.core.tdlib.di.TdlibModule
import dev.kyralabs.pingogram.core.testing.FakeTdlibClient
import dev.kyralabs.pingogram.core.testing.TdApiFixtures
import dev.kyralabs.pingogram.core.ui.theme.PingogramTheme
import dev.kyralabs.pingogram.ui.ChatsScript.ADA
import dev.kyralabs.pingogram.ui.ChatsScript.ADA_TITLE
import org.drinkless.tdlib.TdApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.lang.management.ManagementFactory
import java.time.LocalTime
import dev.kyralabs.pingogram.feature.chat.R as ChatR
import dev.kyralabs.pingogram.feature.media.R as MediaR

/**
 * What composes, frame by frame on the test clock, while a photo opens from a conversation into the media viewer and
 * closes back into its bubble (`docs/PERFORMANCE.md` §4, §9: the container transform runs in the layout and draw
 * phases, so a frame of it composes nothing; the conversation below the overlay composes nothing either way).
 * Counted with [RecompositionCounter] over the whole shell ([PingogramApp]), the chats of [ChatsScript] on a
 * [FakeTdlibClient], as `MediaViewerNavigationTest` runs them. The main thread's CPU time per frame goes with each
 * frame, so a frame that composes the viewer stands out from the frames of the transform; the table is printed for
 * `docs/audit/performance-review-2026-11.md` and for a JFR recording of the test (`-Ppingogram.jfr`, §9), whose
 * samples the printed times place.
 */
@HiltAndroidTest
@UninstallModules(TdlibModule::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, qualifiers = COMPACT)
class MediaViewerTransitionTest {
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val activityRegistration = ActivityRegistration(HiltTestActivity::class.java)

    @get:Rule(order = 2)
    val composeRule = createAndroidComposeRule<HiltTestActivity>()

    private val counter = RecompositionCounter()

    /** Ada's history, newest first: a photo, then texts, enough to fill the screen under the viewer. */
    private val history: List<TdApi.Message> =
        listOf(
            TdApiFixtures.message(
                id = PHOTO_MESSAGE,
                chatId = ADA,
                sender = TdApi.MessageSenderUser(ADA),
                content = TdApiFixtures.messagePhoto(),
            ),
        ) +
            (1..TEXTS).map { index ->
                TdApiFixtures.message(
                    id = PHOTO_MESSAGE - index,
                    chatId = ADA,
                    sender = TdApi.MessageSenderUser(if (index % 3 == 0) ME else ADA),
                    content = TdApiFixtures.messageText("Message $index of the conversation under the viewer"),
                    isOutgoing = index % 3 == 0,
                )
            }

    private val tdlib =
        FakeTdlibClient(authState = AuthState.Ready, connectionState = ConnectionState.Ready).also { tdlib ->
            ChatsScript.install(tdlib)
            tdlib.on(TdApi.GetChatHistory::class) { request ->
                val messages =
                    if (request.chatId == ADA && request.fromMessageId == 0L) history.toTypedArray() else emptyArray()
                TdResult.Success(TdApi.Messages(messages.size, messages))
            }
            tdlib.on(TdApi.GetMessage::class) { request ->
                history.firstOrNull { it.id == request.messageId }?.let { TdResult.Success(it) }
                    ?: TdResult.Failure(TdError(NOT_FOUND, "Not Found"))
            }
            tdlib.on(TdApi.SearchChatMessages::class) {
                val photos = arrayOf(history.first())
                TdResult.Success(TdApi.FoundChatMessages(photos.size, photos, 0))
            }
            tdlib.on(TdApi.GetMessageProperties::class) {
                TdResult.Success(
                    TdApi.MessageProperties().apply {
                        canBeForwarded = true
                        canBeSaved = true
                    },
                )
            }
        }

    @BindValue
    @JvmField
    val tdlibClient: TdlibClient = tdlib

    private lateinit var navController: NavHostController

    @Test
    fun `opening and closing a photo composes nothing per frame of the transform`() {
        setApp()
        awaitNode(adaRow)
        composeRule.onNode(adaRow).performClick()
        awaitPhotoBubble()
        val open =
            composeRule
                .onNode(photoBubble)
                .fetchSemanticsNode()
                .config[SemanticsActions.CustomActions]
                .single { it.label == string(ChatR.string.content_open) }
        composeRule.mainClock.autoAdvance = false
        counter.start()
        counter.reset()

        // For a JFR recording, more openings and closings give more samples (REPEATS_PROPERTY); the last one counts.
        var opening = emptyList<FrameRecord>()
        var closing = emptyList<FrameRecord>()
        repeat(System.getProperty(REPEATS_PROPERTY)?.toIntOrNull() ?: 1) {
            composeRule.runOnUiThread { open.action() }
            opening = frames(OPEN_FRAMES)
            assertTrue(navController.currentDestination?.hasRoute<MediaViewerRoute>() == true)
            composeRule.onNode(viewerPage).assertExists()

            composeRule.runOnUiThread { composeRule.activity.onBackPressedDispatcher.onBackPressed() }
            closing = frames(CLOSE_FRAMES)
            assertTrue(navController.currentDestination?.hasRoute<ChatRoute>() == true)
            composeRule.onAllNodes(viewerPage).fetchSemanticsNodes().let { assertEquals(0, it.size) }
            print("opening", opening)
            print("closing", closing)
        }

        // The first frames of the opening compose the viewer (its view model, its page, its chrome), then the overlay's
        // entry as its transition starts and the shared media's own composables (the bubble's content, the title's
        // avatar: their shared-element modifiers read whether a transition is active); the frames of the transform
        // after that compose nothing, in either screen, until the entry composes again as the transition settles.
        val viewerShown = opening.indexOfFirst { it.recomposed.any { name -> VIEWER_SCREEN in name } }
        assertTrue("the viewer's screen never composed: $opening", viewerShown >= 0)
        // The bottom bar (caption, toolbar and its tooltips) composes once the way in has settled, not with the pages
        // in the frames before the transform starts (docs/audit/performance-review-2026-11.md, M2).
        val bottomBar = opening.indexOfFirst { it.bottomBarShown }
        assertTrue("the bottom bar never showed", bottomBar >= 0)
        assertTrue("the bottom bar composed before the way in settled: $bottomBar", bottomBar > TRANSFORM_FRAMES)
        val transformIn = opening.transformFrames(from = viewerShown + SETTLE_FRAMES)
        assertTrue("too few frames of the transform in: ${transformIn.size}", transformIn.size >= TRANSFORM_FRAMES)
        assertEquals("composed in the frames of the transform in", emptyList<String>(), transformIn.composed())
        // Closing composes the overlay's host, the entry and the shared media's own composables in its first frames,
        // then nothing until the viewer is gone, when the overlay leaves the composition.
        val transformOut = closing.transformFrames(from = SETTLE_FRAMES)
        assertTrue("too few frames of the transform out: ${transformOut.size}", transformOut.size >= TRANSFORM_FRAMES)
        assertEquals("composed in the frames of the transform out", emptyList<String>(), transformOut.composed())
        // No bubble row composes either way: only the shared media's own content, in the photo's bubble, and never the
        // list.
        val items = (opening + closing).flatMap { it.recomposed }.filter { it.isConversationItem() }
        assertEquals("bubble rows composed", emptyList<String>(), items.filter { "MessageBubbleKt" in it })
        assertEquals("content of other bubbles composed", emptyList<String>(), items.filterNot { it.isSharedMedia() })
        val lists = (opening + closing).flatMap { it.recomposed }.filter { MESSAGE_LIST in it }
        assertEquals("the list composed", emptyList<String>(), lists)
    }

    /**
     * The frames of the transform: from [from] until the overlay's entry composes again (the transition settled, or the
     * overlay is gone).
     */
    private fun List<FrameRecord>.transformFrames(from: Int): List<FrameRecord> =
        drop(from).takeWhile { frame -> frame.recomposed.none { OVERLAY_ENTRY in it } }

    /**
     * One frame of the test clock: what Pingogram's own composables composed again, the main thread's CPU µs, and when
     * it started (the JVM's clock, for a JFR recording's samples).
     */
    private class FrameRecord(
        val index: Int,
        val cpuMicros: Long,
        val recomposed: List<String>,
        val time: LocalTime,
        val bottomBarShown: Boolean,
    )

    private fun frames(count: Int): List<FrameRecord> =
        (0 until count).map { index ->
            counter.reset()
            val cpu = ManagementFactory.getThreadMXBean()
            val time = LocalTime.now()
            val before = cpu.currentThreadCpuTime
            composeRule.mainClock.advanceTimeByFrame()
            composeRule.waitForIdle()
            val after = cpu.currentThreadCpuTime
            val bottomBar = composeRule.onAllNodes(forwardButton).fetchSemanticsNodes().isNotEmpty()
            val recomposed = counter.recomposedOwn.map { it.shortName() }
            FrameRecord(index, (after - before) / NANOS_PER_MICRO, recomposed, time, bottomBar)
        }

    /** The frames' table, for the review and for placing a JFR recording's samples (the clock is the JVM's). */
    private fun print(
        phase: String,
        frames: List<FrameRecord>,
    ) {
        println("== $phase: ${frames.size} frames, ${frames.first().time} to ${frames.last().time}")
        println("frame  start         cpu_us  composed")
        frames.forEach { frame ->
            val composed =
                frame.recomposed
                    .groupingBy { it }
                    .eachCount()
                    .entries
                    .joinToString { "${it.key}×${it.value}" }
            val start = frame.time.toString().take(TIME_LENGTH)
            println("%5d  %-12s  %6d  %s".format(frame.index, start, frame.cpuMicros, composed))
        }
    }

    private fun List<FrameRecord>.composed(): List<String> =
        flatMap { frame -> frame.recomposed.map { "${frame.index}:$it" } }

    private fun String.shortName(): String = removePrefix(OWN_PACKAGE)

    /** The composables of the conversation's entries: bubbles and what they show. */
    private fun String.isConversationItem(): Boolean =
        ITEM_FILES.any { "feature.chat.ui.$it." in this || "feature.chat.ui.content.$it." in this }

    /** The composables of a photo bubble's media, which its shared-element modifier composes again. */
    private fun String.isSharedMedia(): Boolean = SHARED_MEDIA.any { it in this }

    private fun setApp() = composeRule.setContent { App() }

    @Composable
    private fun App() {
        PingogramTheme(dynamicColor = false) {
            PingogramApp(
                uiState = MainActivityUiState.SignedIn,
                navController = rememberNavController().also { navController = it },
            )
        }
    }

    private fun awaitPhotoBubble() {
        composeRule.waitUntil(TIMEOUT_MILLIS) {
            composeRule.onAllNodes(photoBubble).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun awaitNode(matcher: SemanticsMatcher) {
        composeRule.waitUntil(TIMEOUT_MILLIS) { composeRule.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty() }
    }

    /** The photo's bubble in the conversation: the one row that offers to open its content. */
    private val photoBubble =
        SemanticsMatcher("has custom action Open") { node ->
            node.config
                .getOrElseNullable(SemanticsActions.CustomActions) { null }
                .orEmpty()
                .any { it.label == string(ChatR.string.content_open) }
        }

    /** The viewer's toolbar's Forward button: there once its bottom bar composed. */
    private val forwardButton = hasContentDescription(string(MediaR.string.media_forward))

    private val viewerPage =
        SemanticsMatcher("is a viewer page") { node ->
            node.config
                .getOrNull(SemanticsProperties.ContentDescription)
                ?.firstOrNull()
                ?.startsWith("Photo, ") == true
        }

    private val adaRow =
        SemanticsMatcher("is Ada's row") { node ->
            node.config
                .getOrNull(SemanticsProperties.ContentDescription)
                ?.firstOrNull()
                ?.startsWith(ADA_TITLE) == true
        } and hasClickAction()

    private fun string(
        @StringRes id: Int,
    ): String = RuntimeEnvironment.getApplication().getString(id)

    private companion object {
        const val TIMEOUT_MILLIS = 5_000L
        const val ME = 1L
        const val PHOTO_MESSAGE = ADA * 1_000 + 100
        const val TEXTS = 30
        const val NOT_FOUND = 404
        const val NANOS_PER_MICRO = 1_000L

        /** Frames of the test clock (16 ms each) for each way: the springs settle well within them. */
        const val OPEN_FRAMES = 60
        const val CLOSE_FRAMES = 60

        /** Frames after the viewer's or the overlay's composition in which the sides of the transform still compose. */
        const val SETTLE_FRAMES = 4

        /** A system property with how many times to open and close; 1 unless a profiling run sets it. */
        const val REPEATS_PROPERTY = "pingogram.mediaTransitionRepeats"

        /** The least frames a transform takes on the test clock before its transition settles. */
        const val TRANSFORM_FRAMES = 20

        /** "HH:MM:SS.mmm" of a frame's start. */
        const val TIME_LENGTH = 12

        const val OWN_PACKAGE = "dev.kyralabs.pingogram."
        const val VIEWER_SCREEN = "MediaViewerScreenKt.MediaViewerScreen"
        const val OVERLAY_ENTRY = "OverlayNavigatorKt.OverlayEntry"
        const val MESSAGE_LIST = "ChatScreenKt.MessageList"

        /** What `sharedMediaBounds` composes again in a photo's bubble as a transition starts and ends. */
        val SHARED_MEDIA =
            listOf("MessageContentBodyKt.MediaContent", "MessagePhotoKt.MessagePhoto", "MessagePhotoKt.PhotoImage")

        /** The files whose composables draw the conversation's entries (`feature:chat`'s `ChatRecompositionTest`). */
        val ITEM_FILES =
            listOf(
                "MessageBubbleKt",
                "BubbleFramesKt",
                "BubblePartsKt",
                "BubbleMetaKt",
                "MessageContentBodyKt",
                "MessagePhotoKt",
                "MediaImageKt",
                "ChatPillsKt",
            )
    }
}

private const val COMPACT = "w411dp-h891dp-xxhdpi"
