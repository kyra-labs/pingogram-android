package dev.kyralabs.pingogram.feature.media.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import dev.kyralabs.pingogram.core.domain.model.FormattedText
import dev.kyralabs.pingogram.core.ui.theme.PingogramTheme
import kotlinx.collections.immutable.persistentListOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The viewer's caption for screen readers (audit A12): a button whose expand or collapse action says what a tap does
 * and does it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class ExpandableCaptionTest {
    @get:Rule(order = 0)
    val componentActivity = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun `the caption is a button that expands and collapses`() {
        composeRule.setContent {
            PingogramTheme(dynamicColor = false) {
                ExpandableCaption(caption = FormattedText(CAPTION, persistentListOf()), key = 1L)
            }
        }
        val caption = composeRule.onNode(hasText(CAPTION))

        caption
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            .assert(SemanticsMatcher.keyIsDefined(SemanticsActions.Expand))
            .assert(SemanticsMatcher.keyNotDefined(SemanticsActions.Collapse))

        caption.performSemanticsAction(SemanticsActions.Expand)
        caption
            .assert(SemanticsMatcher.keyIsDefined(SemanticsActions.Collapse))
            .assert(SemanticsMatcher.keyNotDefined(SemanticsActions.Expand))

        caption.performSemanticsAction(SemanticsActions.Collapse)
        caption.assert(SemanticsMatcher.keyIsDefined(SemanticsActions.Expand))
    }

    @Test
    fun `a long caption ends in Show more, which shows all of it, then Show less`() {
        composeRule.setContent {
            PingogramTheme(dynamicColor = false) {
                ExpandableCaption(caption = FormattedText(CAPTION, persistentListOf()), key = 1L)
            }
        }

        composeRule.onNodeWithText("Show more", useUnmergedTree = true).performClick()
        composeRule.onNode(hasText(CAPTION)).assert(SemanticsMatcher.keyIsDefined(SemanticsActions.Collapse))
        composeRule.onNodeWithText("Show less", useUnmergedTree = true).performClick()
        composeRule.onNodeWithText("Show more", useUnmergedTree = true).assertExists()
    }

    @Test
    fun `a caption that fits offers nothing more`() {
        composeRule.setContent {
            PingogramTheme(dynamicColor = false) {
                ExpandableCaption(caption = FormattedText(SHORT, persistentListOf()), key = 1L)
            }
        }

        composeRule.onNode(hasText(SHORT)).assert(SemanticsMatcher.keyNotDefined(SemanticsActions.Expand))
        composeRule.onNodeWithText("Show more", useUnmergedTree = true).assertDoesNotExist()
    }

    private companion object {
        val CAPTION =
            List(4) { "The view from the summit, after a long climb through the clouds and the morning rain." }
                .joinToString(" ")
        const val SHORT = "The summit."
    }
}
