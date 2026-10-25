package dev.kyralabs.pingogram.feature.chat.ui

import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.InteractionInfo
import dev.kyralabs.pingogram.core.domain.model.Message
import dev.kyralabs.pingogram.core.domain.model.Reaction
import dev.kyralabs.pingogram.core.domain.model.ReactionType
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.GROUP_CHAT
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.groupChat
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.history
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.message
import dev.kyralabs.pingogram.feature.chat.ui.interactions.MessageInteractionsRequest
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

/**
 * "Seen by" and "Reactions" in a group message's actions: offered first where TDLib lists the viewers
 * (`canGetViewers`) or who reacted (`canGetAddedReactions`, with reactions on the message), left out where not, and
 * opening the sheet with what it may list, checked against TDLib again when chosen.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatMessageInteractionsTest : ChatViewModelHarness() {
    private val heart = Reaction(ReactionType.Emoji("❤"), totalCount = 3, isChosen = true)
    private val paid = Reaction(ReactionType.Paid, totalCount = 9, isChosen = false)

    private fun TestScope.effectsOf(viewModel: ChatViewModel): List<ChatEffect> {
        val effects = mutableListOf<ChatEffect>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.effects.toList(effects) }
        return effects
    }

    private fun allowing(viewers: Boolean) {
        messageActions.onProperties = {
            Outcome.Success(FakeMessageActionRepository.ALL_ALLOWED.copy(canGetViewers = viewers))
        }
    }

    private fun groupMessage(
        listsReactions: Boolean = true,
        vararg reactions: Reaction,
    ): Message =
        message(
            id = 5,
            chatId = GROUP_CHAT,
            interactionInfo =
                InteractionInfo(
                    viewCount = 0,
                    forwardCount = 0,
                    replyCount = null,
                    reactions = persistentListOf(*reactions),
                    canGetAddedReactions = listsReactions,
                ),
        )

    private val ChatViewModel.actions get() = state.messageActions?.actions.orEmpty()

    @Test
    fun `Seen by and Reactions come first where TDLib lists them`() =
        runViewModelTest {
            allowing(viewers = true)
            val viewModel = showing(groupChat, history(groupMessage(true, heart)))

            send(viewModel, ChatEvent.ShowMessageActions(5))

            assertEquals(listOf(MessageAction.SeenBy, MessageAction.Reactions), viewModel.actions.take(2))
        }

    @Test
    fun `neither shows where TDLib lists nobody`() =
        runViewModelTest {
            allowing(viewers = false)
            val viewModel = showing(groupChat, history(groupMessage(false, heart)))

            send(viewModel, ChatEvent.ShowMessageActions(5))

            assertFalse(MessageAction.SeenBy in viewModel.actions)
            assertFalse(MessageAction.Reactions in viewModel.actions)
        }

    @Test
    fun `Reactions needs reactions other than the paid one`() =
        runViewModelTest {
            allowing(viewers = true)
            val viewModel = showing(groupChat, history(groupMessage(true, paid)))

            send(viewModel, ChatEvent.ShowMessageActions(5))

            assertEquals(MessageAction.SeenBy, viewModel.actions.first())
            assertFalse(MessageAction.Reactions in viewModel.actions)
        }

    @Test
    fun `choosing one opens the sheet with what it may list`() =
        runViewModelTest {
            allowing(viewers = true)
            val viewModel = showing(groupChat, history(groupMessage(true, heart, paid)))
            val effects = effectsOf(viewModel)

            send(viewModel, ChatEvent.ShowMessageActions(5), ChatEvent.ShowInteractions(5, reactions = true))

            assertEquals(
                listOf(
                    ChatEffect.OpenInteractions(
                        MessageInteractionsRequest(
                            chatId = GROUP_CHAT,
                            messageId = 5,
                            reactionsMessageId = 5,
                            canGetViewers = true,
                            reactions = persistentListOf(heart),
                            opensReactions = true,
                        ),
                    ),
                ),
                effects,
            )
            assertEquals(null, viewModel.state.messageActions)
        }

    @Test
    fun `Seen by that TDLib no longer allows only says so`() =
        runViewModelTest {
            allowing(viewers = true)
            val viewModel = showing(groupChat, history(groupMessage(false)))
            val effects = effectsOf(viewModel)
            send(viewModel, ChatEvent.ShowMessageActions(5))

            allowing(viewers = false)
            send(viewModel, ChatEvent.ShowInteractions(5, reactions = false))

            assertEquals(listOf(ChatEffect.ActionUnavailable), effects)
        }
}
