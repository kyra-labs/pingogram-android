package dev.kyralabs.pingogram.feature.chat.ui

import androidx.activity.ComponentActivity
import androidx.annotation.StringRes
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.domain.model.MessageContent
import dev.kyralabs.pingogram.core.domain.model.MessageSender
import dev.kyralabs.pingogram.core.domain.model.PollVoteRestriction
import dev.kyralabs.pingogram.core.testing.assertAccessible
import dev.kyralabs.pingogram.core.ui.theme.PingogramTheme
import dev.kyralabs.pingogram.feature.chat.R
import dev.kyralabs.pingogram.feature.chat.ui.content.ContentPreviewData
import dev.kyralabs.pingogram.feature.chat.ui.content.MessagePoll
import dev.kyralabs.pingogram.feature.chat.ui.content.PollAction
import dev.kyralabs.pingogram.feature.chat.ui.poll.POLL_VOTERS_TEST_TAG
import dev.kyralabs.pingogram.feature.chat.ui.poll.PollVoterSection
import dev.kyralabs.pingogram.feature.chat.ui.poll.PollVotersBottomSheet
import dev.kyralabs.pingogram.feature.chat.ui.poll.StopPollAlert
import dev.kyralabs.pingogram.feature.chat.ui.preview.PollPreviewData
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Polls on screen (`REQUIREMENTS.md` FR-C4): what a tap on an answer, a check box, "Vote" and "View votes" sends, with
 * their haptics; a poll the user may not vote in takes no tap; screen readers hear each answer within the bubble and
 * vote through its custom actions; the voters' sheet and the stop question send their events; every control has a
 * name and a touch target.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class PollScreenTest {
    @get:Rule(order = 0)
    val componentActivity = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val actions = mutableListOf<PollAction>()
    private val events = mutableListOf<ChatEvent>()
    private val performedHaptics = mutableListOf<HapticFeedbackType>()
    private val haptics =
        object : HapticFeedback {
            override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {
                performedHaptics += hapticFeedbackType
            }
        }

    private fun setPoll(
        content: MessageContent.Poll,
        choices: Set<Int> = emptySet(),
    ) {
        composeRule.setContent {
            PingogramTheme(dynamicColor = false) {
                CompositionLocalProvider(LocalHapticFeedback provides haptics) {
                    MessagePoll(
                        content,
                        choices = persistentSetOf(*choices.toTypedArray()),
                        onAction = { actions += it },
                    )
                }
            }
        }
    }

    private fun setScreen(state: ChatUiState) {
        composeRule.setContent {
            PingogramTheme(dynamicColor = false) { ChatScreen(state = state, onEvent = { events += it }, onBack = {}) }
        }
    }

    @Test
    fun `a tap on an answer votes for it with a toggle haptic`() {
        setPoll(ContentPreviewData.poll(voted = false))

        composeRule.onNodeWithText("Summer").performClick()

        assertEquals(listOf(PollAction.Vote(1)), actions)
        assertEquals(listOf(HapticFeedbackType.ToggleOn), performedHaptics)
    }

    @Test
    fun `answers of a poll with several are checked and unchecked, and Vote sends them`() {
        setPoll(ContentPreviewData.poll(voted = false, multipleAnswers = true), choices = setOf(2))

        composeRule.onNodeWithText("Summer").performClick()
        composeRule.onNodeWithText("Autumn").performClick()
        composeRule.onNodeWithText(string(R.string.poll_vote)).performClick()

        assertEquals(listOf(PollAction.Toggle(1), PollAction.Toggle(2), PollAction.Submit), actions)
        assertEquals(listOf(HapticFeedbackType.ToggleOn, HapticFeedbackType.ToggleOff), performedHaptics)
    }

    @Test
    fun `Vote waits for a checked answer`() {
        setPoll(ContentPreviewData.poll(voted = false, multipleAnswers = true))

        composeRule.onNodeWithText(string(R.string.poll_vote)).assertIsNotEnabled()
    }

    @Test
    fun `a public poll's results open its voters`() {
        setPoll(ContentPreviewData.poll(voted = true, publicVoters = true))

        composeRule.onNodeWithText(string(R.string.poll_view_votes)).performClick()

        assertEquals(listOf(PollAction.ShowVoters), actions)
    }

    @Test
    fun `a poll the user may not vote in says why and takes no tap`() {
        setPoll(ContentPreviewData.poll(voted = false, restriction = PollVoteRestriction.MembershipRequired(1)))

        composeRule.onNodeWithText(string(R.string.poll_restricted_members)).assertExists()
        composeRule.onNodeWithText("Summer").assertHasNoClickAction()
    }

    @Test
    fun `screen readers hear each answer with its share and mark within the bubble`() {
        setScreen(PollPreviewData.results)

        val description =
            composeRule
                .onNode(hasCustomAction(string(R.string.poll_view_votes)))
                .fetchSemanticsNode()
                .config[SemanticsProperties.ContentDescription]
                .single()

        assertTrue(description, "Autumn, 41%, 41 votes, Your answer" in description)
        assertTrue(description, "Spring, 12%, 12 votes" in description)
        assertTrue(description, "100 votes" in description)
    }

    @Test
    fun `screen readers vote through the bubble's actions, and hear the checked answers`() {
        setScreen(PollPreviewData.voting)

        performCustomAction(string(R.string.poll_vote_for, "Summer"))
        performCustomAction(string(R.string.poll_vote))

        assertEquals(listOf(PollEvent.Vote(3, 1), PollEvent.SubmitVotes(2)), events.filterIsInstance<PollEvent>())
        val multiple =
            composeRule.onNode(hasCustomAction(string(R.string.poll_vote))).fetchSemanticsNode()
        val description = multiple.config[SemanticsProperties.ContentDescription].single()
        assertTrue(description, "Summer, chosen" in description)
    }

    @Test
    fun `a conversation with polls has names and touch targets for every control`() {
        setScreen(PollPreviewData.voting)

        composeRule.assertAccessible()
    }

    @Test
    fun `a tapped voter, Show more and Try again send their events`() {
        val sheet =
            PollPreviewData.voters.copy(
                sections =
                    persistentListOf(
                        PollPreviewData.voters.sections[0],
                        PollVoterSection(optionIndex = 2, text = "Autumn", count = 2, error = DomainError.Network),
                    ),
            )
        composeRule.setContent {
            PingogramTheme(dynamicColor = false) { PollVotersBottomSheet(sheet, onEvent = { events += it }) }
        }

        composeRule.onNodeWithText("Grace Hopper").performClick()
        composeRule.onNodeWithTag(POLL_VOTERS_TEST_TAG).performScrollToNode(hasText(string(R.string.poll_voters_more)))
        composeRule.onNodeWithText(string(R.string.poll_voters_more)).performClick()
        composeRule.onNodeWithTag(POLL_VOTERS_TEST_TAG).performScrollToNode(hasText(string(R.string.poll_voters_retry)))
        composeRule.onNodeWithText(string(R.string.poll_voters_retry)).performClick()

        assertEquals(
            listOf(
                PollEvent.OpenVoter(MessageSender.User(41)),
                PollEvent.LoadMoreVoters(1),
                PollEvent.LoadMoreVoters(2),
            ),
            events,
        )
    }

    @Test
    fun `the voters' sheet has names and touch targets for every control`() {
        composeRule.setContent {
            PingogramTheme(dynamicColor = false) { PollVotersBottomSheet(PollPreviewData.voters, onEvent = {}) }
        }

        composeRule.assertAccessible()
    }

    @Test
    fun `the stop question stops the poll or dismisses`() {
        composeRule.setContent { PingogramTheme(dynamicColor = false) { StopPollAlert(onEvent = { events += it }) } }

        composeRule.assertAccessible()
        composeRule.onNodeWithText(string(R.string.poll_stop_cancel)).performClick()
        composeRule.onNodeWithText(string(R.string.poll_stop_confirm)).performClick()

        assertEquals(listOf(PollEvent.DismissStop, PollEvent.ConfirmStop), events)
    }

    private fun string(
        @StringRes id: Int,
        vararg args: Any,
    ): String = RuntimeEnvironment.getApplication().getString(id, *args)

    /** Does what the custom accessibility action [label] of the one node that offers it does, as TalkBack would. */
    private fun performCustomAction(label: String) {
        val node = composeRule.onNode(hasCustomAction(label)).fetchSemanticsNode()
        val action = node.config[SemanticsActions.CustomActions].single { it.label == label }
        composeRule.runOnIdle { action.action() }
    }

    private fun hasCustomAction(label: String) =
        SemanticsMatcher("has the custom action $label") { node ->
            node.config
                .getOrNull(SemanticsActions.CustomActions)
                .orEmpty()
                .any { it.label == label }
        }
}
