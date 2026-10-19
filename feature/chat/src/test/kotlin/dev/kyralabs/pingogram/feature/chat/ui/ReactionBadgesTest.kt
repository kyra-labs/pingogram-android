package dev.kyralabs.pingogram.feature.chat.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.kyralabs.pingogram.core.domain.model.FormattedText
import dev.kyralabs.pingogram.core.domain.model.MessageContent
import dev.kyralabs.pingogram.core.ui.text.UiText
import dev.kyralabs.pingogram.core.ui.theme.PingogramTheme
import dev.kyralabs.pingogram.feature.chat.R
import dev.kyralabs.pingogram.feature.chat.ui.preview.BubblePreviewData
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * The reaction badges on a bubble's corner ([ReactionBadges]): tapping one toggles its reaction, the count shows past
 * one, three show and then "+N", which opens the actions, the paid star is no button; and on a bubble in the
 * conversation, screen readers still toggle each reaction through the bubble's custom actions.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class ReactionBadgesTest {
    @get:Rule(order = 0)
    val componentActivity = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val toggled = mutableListOf<ReactionChip>()
    private var moreOpened = 0

    @Test
    fun `tapping a badge toggles its reaction`() {
        val fire = ReactionChip(ReactionLabel.Emoji("🔥"), 5, isChosen = true)
        setBadges(fire, ReactionChip(ReactionLabel.Emoji("👍"), 1, isChosen = false))

        composeRule.onNodeWithText("🔥").performClick()

        assertEquals(listOf(fire), toggled)
    }

    @Test
    fun `a badge shows its count only when more than one person reacted`() {
        setBadges(ReactionChip(ReactionLabel.Emoji("🔥"), 5, isChosen = false))

        composeRule.onNodeWithText("5").assertIsDisplayed()
        composeRule.onNodeWithText("1").assertDoesNotExist()
    }

    @Test
    fun `one reaction shows one badge, without a count`() {
        setBadges(ReactionChip(ReactionLabel.Emoji("😂"), 1, isChosen = false))

        composeRule.onNodeWithText("😂").assertIsDisplayed()
        composeRule.onNodeWithText("1").assertDoesNotExist()
    }

    @Test
    fun `two reactions show two badges side by side`() {
        setBadges(
            ReactionChip(ReactionLabel.Emoji("❤️"), 1, isChosen = false),
            ReactionChip(ReactionLabel.Emoji("😮"), 2, isChosen = true),
        )

        val heart =
            composeRule
                .onNodeWithText("❤️")
                .assertIsDisplayed()
                .fetchSemanticsNode()
                .boundsInRoot
        val wow =
            composeRule
                .onNodeWithText("😮")
                .assertIsDisplayed()
                .fetchSemanticsNode()
                .boundsInRoot
        assertEquals(heart.center.y, wow.center.y, 1f)
        assertTrue(wow.left >= heart.right)
    }

    @Test
    fun `three badges show, then +N for the rest, which opens the message's actions`() {
        val emoji = listOf("👍", "😂", "🔥", "🎉", "❤️")
        setBadges(*emoji.map { ReactionChip(ReactionLabel.Emoji(it), 1, isChosen = false) }.toTypedArray())

        composeRule.onNodeWithText("🔥").assertIsDisplayed()
        composeRule.onNodeWithText("🎉").assertDoesNotExist()
        composeRule.onNodeWithText("+2").assertIsDisplayed().performClick()

        assertEquals(1, moreOpened)
        assertEquals(emptyList<ReactionChip>(), toggled)
    }

    @Test
    fun `the paid star reaction is no button`() {
        setBadges(ReactionChip(ReactionLabel.Paid, 12, isChosen = false))

        composeRule.onNodeWithText("⭐").assertHasNoClickAction()
    }

    @Test
    fun `on a bubble, screen readers toggle each reaction through the bubble's actions`() {
        val events = mutableListOf<ChatEvent>()
        composeRule.setContent {
            PingogramTheme(dynamicColor = false) {
                ChatScreen(state = BubblePreviewData.emojiAndReactions, onEvent = { events += it }, onBack = {})
            }
        }
        val label = string(R.string.message_reaction_remove, "😮")

        val node = composeRule.onNode(hasCustomAction(label)).fetchSemanticsNode()
        val action = node.config[SemanticsActions.CustomActions].single { it.label == label }
        composeRule.runOnIdle { action.action() }

        assertEquals(
            listOf(ChatEvent.ToggleReaction(34, ReactionLabel.Emoji("😮"))),
            events.filterIsInstance<ChatEvent.ToggleReaction>(),
        )
    }

    private fun setBadges(vararg chips: ReactionChip) {
        composeRule.setContent {
            PingogramTheme(dynamicColor = false) {
                ReactionBadges(
                    bubble = bubble(chips.toList()),
                    toggles = rememberReactionToggleState(),
                    onToggle = { toggled += it },
                    onMore = { moreOpened++ },
                )
            }
        }
        composeRule.waitForIdle()
    }

    /** An incoming text message with [reactions]. */
    private fun bubble(reactions: List<ReactionChip>): ChatItem.Bubble {
        val text = FormattedText("Hello", persistentListOf())
        return ChatItem.Bubble(
            id = 1,
            isOutgoing = false,
            position = GroupPosition.Single,
            sender = UiText.Plain("Ada"),
            senderId = 2,
            showsSenderName = false,
            avatar = null,
            hasAvatarColumn = false,
            time = "9:41 AM",
            isEdited = false,
            status = null,
            forwardedFrom = null,
            reply = null,
            content = MessageContent.Text(text, linkPreview = null),
            contentLabel = null,
            text = text,
            spoilersRevealed = false,
            linkPreview = null,
            reactions = reactions.toImmutableList(),
            date = UiText.resource(R.string.chat_date_today),
        )
    }

    private fun string(
        id: Int,
        vararg args: Any,
    ): String = RuntimeEnvironment.getApplication().getString(id, *args)

    /** A node offering the custom accessibility action [label]. */
    private fun hasCustomAction(label: String) =
        SemanticsMatcher("has the custom action $label") { node ->
            node.config
                .getOrNull(SemanticsActions.CustomActions)
                .orEmpty()
                .any { it.label == label }
        }
}
