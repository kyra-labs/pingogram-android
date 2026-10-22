package dev.kyralabs.pingogram.feature.chat.ui.composer

import androidx.activity.ComponentActivity
import androidx.annotation.StringRes
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.kyralabs.pingogram.core.domain.model.FormattedText
import dev.kyralabs.pingogram.core.ui.theme.PingogramSizes
import dev.kyralabs.pingogram.core.ui.theme.PingogramTheme
import dev.kyralabs.pingogram.feature.chat.R
import dev.kyralabs.pingogram.feature.chat.ui.COMPACT
import dev.kyralabs.pingogram.feature.chat.ui.ComponentActivityRegistration
import dev.kyralabs.pingogram.feature.chat.ui.EXPANDED
import dev.kyralabs.pingogram.feature.chat.ui.attach.AttachOption
import kotlinx.collections.immutable.persistentListOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * The composer's size, as Google Messages' (`docs/DESIGN_SYSTEM.md` §6 Composer): a 72 dp bar with a 56 dp pill at one
 * line beside a 56 dp round button, buttons with 48 dp touch targets, the emoji button at the bottom of a growing
 * field, the gallery button while the field is empty, the send menu on a long press, a field that keeps most of the
 * width at 200% font, a width capped like the bubbles' on wide windows, and the field and buttons named for screen
 * readers.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class ComposerLayoutTest {
    @get:Rule(order = 0)
    val componentActivity = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private var sent = 0

    private fun setComposer(text: String = "") {
        composeRule.setContent {
            PingogramTheme(dynamicColor = false) {
                ChatBottomBar(
                    composer =
                        ComposerUiState.Available(
                            canSchedule = true,
                            attachOptions = persistentListOf(AttachOption.Gallery),
                        ),
                    text = ComposerText(FormattedText(text, persistentListOf())),
                    onSend = { sent++ },
                    onFocusChanged = {},
                    modifier = Modifier.testTag(BAR),
                )
            }
        }
    }

    private fun bar() = composeRule.onNodeWithTag(BAR)

    private fun field() = composeRule.onNode(hasSetTextAction())

    private fun button(
        @StringRes id: Int,
    ) = composeRule.onNodeWithContentDescription(RuntimeEnvironment.getApplication().getString(id))

    private fun SemanticsNodeInteraction.height(): Dp =
        with(composeRule.density) {
            fetchSemanticsNode().size.height.toDp()
        }

    private fun SemanticsNodeInteraction.width(): Dp =
        with(composeRule.density) { fetchSemanticsNode().size.width.toDp() }

    private fun SemanticsNodeInteraction.assertTouchTarget() {
        val bounds = fetchSemanticsNode().touchBoundsInRoot
        val minimum = with(composeRule.density) { TOUCH_TARGET.toPx() }
        assertTrue("touch target ${bounds.width} × ${bounds.height} px", bounds.width >= minimum - 1)
        assertTrue("touch target ${bounds.width} × ${bounds.height} px", bounds.height >= minimum - 1)
    }

    @Test
    fun `at one line the composer is a 72 dp bar around a 56 dp pill and a 56 dp button`() {
        setComposer()

        assertEquals(PingogramSizes().composerField.value, field().height().value, TOLERANCE)
        assertEquals(ACTION_SIZE.value, button(R.string.voice_record).height().value, TOLERANCE)
        assertEquals(ACTION_SIZE.value, button(R.string.voice_record).width().value, TOLERANCE)
        assertEquals(BAR_HEIGHT.value, bar().height().value, TOLERANCE)
    }

    @Test
    fun `with text, the bar keeps its height and the send button takes the microphone's place`() {
        setComposer("Hello")

        assertEquals(ACTION_SIZE.value, button(R.string.composer_send).height().value, TOLERANCE)
        button(R.string.voice_record).assertDoesNotExist()
        assertEquals(BAR_HEIGHT.value, bar().height().value, TOLERANCE)
    }

    @Test
    fun `the gallery button shows only while the field is empty`() {
        setComposer()
        button(R.string.composer_gallery).assertTouchTarget()

        composeRule.onNode(hasSetTextAction()).performTextInput("Hi")

        button(R.string.composer_gallery).assertDoesNotExist()
    }

    @Test
    fun `a tap on send sends, a long press opens its other ways to send instead`() {
        setComposer("Hello")

        button(R.string.composer_send).performClick()
        assertEquals(1, sent)

        button(R.string.composer_send).performTouchInput { longClick() }

        assertEquals(1, sent)
        composeRule.onNodeWithText(string(R.string.composer_send_silently)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.composer_schedule)).assertIsDisplayed()
    }

    @Test
    fun `screen readers reach the other ways to send as custom actions`() {
        setComposer("Hello")

        val actions =
            button(R.string.composer_send)
                .fetchSemanticsNode()
                .config
                .getOrNull(SemanticsActions.CustomActions)
                .orEmpty()
                .map { it.label }
        assertEquals(listOf(string(R.string.composer_send_silently), string(R.string.composer_schedule)), actions)
    }

    private fun string(
        @StringRes id: Int,
    ) = RuntimeEnvironment.getApplication().getString(id)

    @Test
    fun `as the field grows the emoji button stays at its bottom, level with the last line`() {
        setComposer("Running late\nthe ferry was slow again\nSave me a seat by the window?")

        val field = field().fetchSemanticsNode().boundsInRoot
        val emoji = button(R.string.panel_open).fetchSemanticsNode().boundsInRoot
        val oneLine = with(composeRule.density) { PingogramSizes().composerField.toPx() }
        assertTrue("the field is ${field.height} px tall", field.height > 2 * oneLine)
        // Centred where it is on a one-line field: half its height above the field's bottom.
        assertEquals(oneLine / 2, field.bottom - emoji.center.y, PIXEL_TOLERANCE)
    }

    @Test
    fun `every button has a 48 dp touch target`() {
        setComposer()

        button(R.string.attach).assertTouchTarget()
        button(R.string.panel_open).assertTouchTarget()
        button(R.string.voice_record).assertTouchTarget()
    }

    @Test
    fun `the field is labelled by its placeholder while empty`() {
        setComposer()

        val texts =
            field()
                .fetchSemanticsNode()
                .config
                .getOrNull(SemanticsProperties.Text)
                .orEmpty()
        val placeholder = RuntimeEnvironment.getApplication().getString(R.string.composer_placeholder)
        assertTrue("field texts $texts", texts.any { it.text == placeholder })
    }

    @Test
    @Config(fontScale = FONT_SCALE_200)
    fun `at 200 percent font the field keeps most of the width beside the send button`() {
        setComposer("Running ten minutes late, the ferry was slow again.")

        val share = field().width() / composeRule.onRoot().width()
        assertTrue("the field takes $share of the width", share >= MIN_FIELD_SHARE)
    }

    @Test
    @Config(qualifiers = EXPANDED)
    fun `on a wide window the composer is no wider than a bubble`() {
        setComposer("Hello")

        val root = composeRule.onRoot().width()
        assertTrue("the field is ${field().width()} wide", field().width() < PingogramSizes().bubbleMaxWidth)
        assertTrue("the window is $root wide", root > PingogramSizes().bubbleMaxWidth)
    }

    private companion object {
        const val BAR = "composer"
        val BAR_HEIGHT = 72.dp
        val ACTION_SIZE = 56.dp
        val TOUCH_TARGET = 48.dp
        const val TOLERANCE = 0.5f
        const val PIXEL_TOLERANCE = 1.5f
        const val FONT_SCALE_200 = 2f

        /** More than half; the outlined field beside the 56 dp split button took less (`docs/audit` G05). */
        const val MIN_FIELD_SHARE = 0.55f
    }
}
