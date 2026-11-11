package dev.kyralabs.pingogram.feature.chat.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.kyralabs.pingogram.core.testing.assertAccessible
import dev.kyralabs.pingogram.core.ui.theme.PingogramTheme
import dev.kyralabs.pingogram.feature.chat.R
import dev.kyralabs.pingogram.feature.chat.ui.content.GiveawayAction
import dev.kyralabs.pingogram.feature.chat.ui.content.MessageGiveaway
import dev.kyralabs.pingogram.feature.chat.ui.content.MessageGiveawayWinners
import dev.kyralabs.pingogram.feature.chat.ui.content.PaidPostSamples
import dev.kyralabs.pingogram.feature.chat.ui.giveaway.GiveawayInfoContent
import dev.kyralabs.pingogram.feature.chat.ui.preview.GiveawayPreviewData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Giveaways on screen (`REQUIREMENTS.md` FR-C4): what a channel's chip, a winner's tile and "Learn more" send; screen
 * readers hear the prize, the channels and the winners within the bubble and open them through its custom actions;
 * every control of the bubbles and the details sheet has a name and a touch target.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class GiveawayScreenTest {
    @get:Rule(order = 0)
    val componentActivity = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val actions = mutableListOf<GiveawayAction>()
    private val events = mutableListOf<ChatEvent>()

    @Test
    fun `a channel's chip and Learn more send their actions`() {
        composeRule.setContent {
            PingogramTheme(dynamicColor = false) {
                MessageGiveaway(PaidPostSamples.giveaway(), ui = PaidPostSamples.giveawayUi(), onAction = {
                    actions +=
                        it
                })
            }
        }

        composeRule.onNodeWithText("Kotlin Daily").performClick()
        composeRule.onNodeWithText(string(R.string.giveaway_learn_more)).performClick()

        assertEquals(listOf(GiveawayAction.OpenChat(PaidPostSamples.OTHER_CHANNEL), GiveawayAction.LearnMore), actions)
    }

    @Test
    fun `a winner's tile opens their profile`() {
        composeRule.setContent {
            PingogramTheme(dynamicColor = false) {
                MessageGiveawayWinners(
                    PaidPostSamples.giveawayWinners(),
                    ui = PaidPostSamples.giveawayUi(),
                    onAction = { actions += it },
                )
            }
        }

        composeRule.onNodeWithText("Meera").performClick()

        assertEquals(listOf<GiveawayAction>(GiveawayAction.OpenWinner(PaidPostSamples.WINNER_MEERA)), actions)
    }

    @Test
    fun `screen readers hear the giveaway within its bubble and open its parts through custom actions`() {
        composeRule.setContent {
            PingogramTheme(dynamicColor = false) {
                ChatScreen(state = GiveawayPreviewData.posts, onEvent = { events += it }, onBack = {})
            }
        }

        val giveaway =
            composeRule
                .onNode(hasContentDescription(string(R.string.giveaway_prize_kicker), substring = true))
                .fetchSemanticsNode()
        val description =
            giveaway.config
                .getOrNull(SemanticsProperties.ContentDescription)
                ?.joinToString()
                .orEmpty()
        val custom = giveaway.config.getOrNull(SemanticsActions.CustomActions).orEmpty()
        custom.first { it.label == string(R.string.giveaway_open_channel, "Kotlin Daily") }.action()
        custom.first { it.label == string(R.string.giveaway_learn_more) }.action()
        val winners =
            composeRule
                .onNode(hasContentDescription(string(R.string.giveaway_winners_title), substring = true))
                .fetchSemanticsNode()
        val winnerActions = winners.config.getOrNull(SemanticsActions.CustomActions).orEmpty()
        winnerActions.first { it.label == string(R.string.giveaway_open_winner, "Ravi K") }.action()

        assertTrue(description, description.contains(string(R.string.giveaway_prize_premium)))
        assertTrue(description, description.contains("Android Devs Weekly"))
        assertEquals(
            listOf(
                ChatEvent.OpenChat(PaidPostSamples.OTHER_CHANNEL),
                GiveawayEvent.OpenInfo(2),
                ChatEvent.OpenProfile(PaidPostSamples.WINNER_RAVI),
            ),
            events.filter { it is ChatEvent.OpenChat || it is GiveawayEvent || it is ChatEvent.OpenProfile },
        )
    }

    @Test
    fun `every control has a name and a touch target`() {
        composeRule.setContent {
            PingogramTheme(dynamicColor = false) {
                ChatScreen(state = GiveawayPreviewData.posts, onEvent = {}, onBack = {})
            }
        }

        composeRule.assertAccessible()
    }

    @Test
    fun `the details sheet's buttons send their events, and it has names and touch targets`() {
        composeRule.setContent {
            PingogramTheme(dynamicColor = false) {
                GiveawayInfoContent(GiveawayPreviewData.infoFailed, onEvent = { events += it })
            }
        }

        composeRule.onNodeWithText(string(R.string.giveaway_info_retry)).performClick()
        composeRule.onNodeWithText(string(R.string.giveaway_info_close)).performClick()
        composeRule.assertAccessible()

        assertEquals(listOf<ChatEvent>(GiveawayEvent.RetryInfo, GiveawayEvent.DismissInfo), events)
    }

    private fun string(
        id: Int,
        vararg args: Any,
    ): String = RuntimeEnvironment.getApplication().getString(id, *args)
}
