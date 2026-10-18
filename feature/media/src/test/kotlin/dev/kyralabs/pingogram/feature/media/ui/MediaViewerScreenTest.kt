package dev.kyralabs.pingogram.feature.media.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.click
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import dev.kyralabs.pingogram.core.domain.model.ThemeMode
import dev.kyralabs.pingogram.core.ui.theme.PingogramTheme
import dev.kyralabs.pingogram.feature.media.R
import dev.kyralabs.pingogram.feature.media.ui.preview.MediaPreviewData
import kotlinx.collections.immutable.toPersistentList
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * The media viewer's screen on Robolectric: turning pages, the chrome, leaving with back or a swipe down, zooming, and
 * what screen readers get. The screen is stateless; its events are recorded.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class MediaViewerScreenTest {
    @get:Rule(order = 0)
    val componentActivity = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val events = mutableListOf<MediaViewerEvent>()
    private var backs = 0
    private var state by mutableStateOf<MediaViewerUiState>(MediaPreviewData.photo)

    private fun setScreen(initial: MediaViewerUiState = MediaPreviewData.photo) {
        state = initial
        composeRule.setContent {
            PingogramTheme(themeMode = ThemeMode.Light, dynamicColor = false) {
                MediaViewerScreen(state = state, onEvent = { events += it }, onBack = { backs++ })
            }
        }
        composeRule.waitForIdle()
    }

    private val settled get() = events.filterIsInstance<MediaViewerEvent.PageSettled>().map { it.messageId }

    private fun backButton(): SemanticsNodeInteraction =
        composeRule.onNode(hasContentDescription(string(R.string.media_back)))

    /** The page of the photo, by its description, which starts with its kind and position. */
    private fun photoPage(): SemanticsNodeInteraction =
        composeRule.onNode(hasContentDescription("Photo, 3 of 120, Ada Lovelace", substring = true))

    private fun string(id: Int): String = RuntimeEnvironment.getApplication().getString(id)

    // Pages

    @Test
    fun `the pager starts on the current item`() {
        setScreen()

        assertEquals(listOf(1L), settled)
        photoPage().assertIsDisplayed()
    }

    @Test
    fun `swiping turns to the newer and back to the older page`() {
        setScreen()

        composeRule.onRoot().performTouchInput { swipeLeft() }
        composeRule.waitForIdle()
        assertEquals(listOf(1L, 2L), settled)

        composeRule.onRoot().performTouchInput { swipeRight() }
        composeRule.waitForIdle()
        assertEquals(listOf(1L, 2L, 1L), settled)
    }

    @Test
    fun `the page stays on its item when older media loads in front`() {
        setScreen()
        val content = MediaPreviewData.photo
        val older = content.items.first().copy(messageId = 0)

        state =
            content.copy(
                items = (listOf(older) + content.items).toPersistentList(),
                currentIndex = 1,
                count = MediaCount(firstNumber = 2, total = 120),
            )
        composeRule.waitForIdle()

        assertEquals(listOf(1L), settled)
        photoPage().assertIsDisplayed()
    }

    @Test
    fun `screen readers can turn the page`() {
        setScreen()

        photoPage().performCustomAction(string(R.string.media_show_newer))
        composeRule.waitForIdle()

        assertEquals(listOf(1L, 2L), settled)
    }

    // Chrome

    @Test
    fun `tapping the media hides the chrome, and tapping again shows it`() {
        setScreen()
        backButton().assertIsDisplayed()

        photoPage().performTouchInput { click() }
        composeRule.mainClock.advanceTimeBy(DOUBLE_TAP_TIMEOUT_MILLIS)
        composeRule.waitForIdle()
        composeRule.onAllNodes(hasContentDescription(string(R.string.media_back))).assertCountEquals(0)

        photoPage().performTouchInput { click() }
        composeRule.mainClock.advanceTimeBy(DOUBLE_TAP_TIMEOUT_MILLIS)
        composeRule.waitForIdle()
        backButton().assertIsDisplayed()
    }

    @Test
    fun `screen readers show and hide the chrome with a click`() {
        setScreen()

        photoPage().performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()

        composeRule.onAllNodes(hasContentDescription(string(R.string.media_back))).assertCountEquals(0)
    }

    @Test
    fun `the top bar shows the sender, the date and the position`() {
        setScreen()

        composeRule.onNode(hasText("Ada Lovelace")).assertIsDisplayed()
        composeRule.onNode(hasText("Oct 18, 9:41 AM")).assertIsDisplayed()
        composeRule.onNode(hasText("3 of 120")).assertIsDisplayed()
    }

    @Test
    fun `a video's controls play and pause it`() {
        setScreen(MediaPreviewData.video)

        composeRule.onNode(hasContentDescription(string(R.string.media_pause))).performClick()

        assertTrue(MediaViewerEvent.PlayPause in events)
        composeRule.onNode(hasContentDescription(string(R.string.media_seek))).assertIsDisplayed()
    }

    // Leaving

    @Test
    fun `back leaves`() {
        setScreen()

        composeRule.runOnUiThread { composeRule.activity.onBackPressedDispatcher.onBackPressed() }
        composeRule.waitForIdle()

        assertEquals(1, backs)
    }

    @Test
    fun `the back button leaves`() {
        setScreen()

        backButton().performClick()

        assertEquals(1, backs)
    }

    @Test
    fun `a swipe down past the threshold closes the viewer`() {
        setScreen()

        composeRule.onRoot().performTouchInput { swipeDown(startY = centerY, endY = bottom) }
        composeRule.waitForIdle()

        assertEquals(1, backs)
    }

    @Test
    fun `a short drag down springs back`() {
        setScreen()

        composeRule.onRoot().performTouchInput {
            swipeDown(startY = centerY, endY = centerY + height * 0.05f, durationMillis = 1_000)
        }
        composeRule.waitForIdle()

        assertEquals(0, backs)
        photoPage().assertIsDisplayed()
    }

    // Zoom

    @Test
    fun `a double tap zooms in, and then swipes pan instead of turning the page`() {
        setScreen()

        photoPage().performTouchInput { doubleClick() }
        composeRule.waitForIdle()
        photoPage().assertStateDescription(string(R.string.media_zoomed_in))

        composeRule.onRoot().performTouchInput { swipeLeft() }
        composeRule.waitForIdle()
        assertEquals(listOf(1L), settled)
        composeRule.onRoot().performTouchInput { swipeDown(startY = centerY, endY = bottom) }
        composeRule.waitForIdle()
        assertEquals(0, backs)
    }

    @Test
    fun `screen readers zoom in and out`() {
        setScreen()

        photoPage().performCustomAction(string(R.string.media_zoom_in))
        composeRule.waitForIdle()
        photoPage().assertStateDescription(string(R.string.media_zoomed_in))

        photoPage().performCustomAction(string(R.string.media_zoom_out))
        composeRule.waitForIdle()
        photoPage().assertStateDescription(null)
    }

    // States

    @Test
    fun `a spoiler shows when tapped`() {
        setScreen(MediaPreviewData.spoiler)
        val page = composeRule.onNode(hasContentDescription("Photo, Spoiler", substring = true))

        assertEquals(
            string(R.string.media_show_spoiler),
            page.fetchSemanticsNode().config[SemanticsActions.OnClick].label,
        )
        page.performTouchInput { click() }
        composeRule.waitForIdle()

        assertTrue(MediaViewerEvent.RevealSpoiler(4) in events)
    }

    @Test
    fun `an error offers to try again`() {
        setScreen(MediaPreviewData.error)

        composeRule.onNode(hasText(string(R.string.media_retry))).performClick()

        assertTrue(MediaViewerEvent.Retry in events)
        backButton().assertIsDisplayed()
    }

    @Test
    fun `deleted media says it is no longer available`() {
        setScreen(MediaPreviewData.unavailable)

        composeRule.onNode(hasText(string(R.string.media_unavailable_title))).assertIsDisplayed()
    }

    // Share and Save

    @Test
    fun `Share and Save send their events`() {
        setScreen(MediaPreviewData.withActions)

        composeRule.onNode(hasContentDescription(string(R.string.media_share))).assertHasClickAction().performClick()
        composeRule.onNode(hasContentDescription(string(R.string.media_save))).assertHasClickAction().performClick()

        assertEquals(listOf(MediaViewerEvent.Share, MediaViewerEvent.Save), events.takeLast(2))
    }

    @Test
    fun `a chat that protects its content shows neither`() {
        setScreen(MediaPreviewData.protectedContent)

        composeRule.onNode(hasContentDescription(string(R.string.media_share))).assertDoesNotExist()
        composeRule.onNode(hasContentDescription(string(R.string.media_save))).assertDoesNotExist()
    }

    @Test
    fun `while Share waits for the file, it says so and both are disabled`() {
        setScreen(MediaPreviewData.sharing)

        val share = composeRule.onNode(hasContentDescription(string(R.string.media_share)))
        share.assertStateDescription(string(R.string.media_share_pending))
        share.assertIsNotEnabled()
        composeRule.onNode(hasContentDescription(string(R.string.media_save))).assertIsNotEnabled()
    }

    @Test
    fun `the reason for the storage permission answers with Continue or Not now`() {
        val rationale =
            MediaPreviewData.withActions.copy(
                actions = MediaActions(canSave = true, showsStorageRationale = true),
            )
        setScreen(rationale)

        composeRule.onNode(hasText(string(R.string.media_storage_rationale_title))).assertIsDisplayed()
        composeRule.onNode(hasText(string(R.string.media_storage_rationale_continue))).performClick()
        composeRule.onNode(hasText(string(R.string.media_storage_rationale_not_now))).performClick()

        assertEquals(
            listOf(
                MediaViewerEvent.StorageRationaleAnswered(proceed = true),
                MediaViewerEvent.StorageRationaleAnswered(proceed = false),
            ),
            events.filterIsInstance<MediaViewerEvent.StorageRationaleAnswered>(),
        )
    }

    private fun SemanticsNodeInteraction.performCustomAction(label: String) {
        val action =
            fetchSemanticsNode().config[SemanticsActions.CustomActions].single { it.label == label }
        composeRule.runOnUiThread { action.action() }
    }

    private fun SemanticsNodeInteraction.assertStateDescription(expected: String?) {
        assertEquals(expected, fetchSemanticsNode().config.getOrNull(SemanticsProperties.StateDescription))
    }

    private companion object {
        /** Longer than any double tap timeout, so a single tap counts as one. */
        const val DOUBLE_TAP_TIMEOUT_MILLIS = 1_000L
    }
}
