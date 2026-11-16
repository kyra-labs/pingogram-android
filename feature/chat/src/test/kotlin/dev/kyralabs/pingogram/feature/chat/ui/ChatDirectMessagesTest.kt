package dev.kyralabs.pingogram.feature.chat.ui

import app.cash.turbine.test
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.ChatMemberStatus
import dev.kyralabs.pingogram.core.domain.model.ChatPermissions
import dev.kyralabs.pingogram.core.domain.model.ChatType
import dev.kyralabs.pingogram.core.domain.model.DirectMessagesChat
import dev.kyralabs.pingogram.core.domain.model.SuggestedPost
import dev.kyralabs.pingogram.core.domain.model.SuggestedPostPrice
import dev.kyralabs.pingogram.core.ui.text.UiText
import dev.kyralabs.pingogram.feature.chat.R
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.CHANNEL_CHAT
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.SUPERGROUP_ID
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.channelChat
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.history
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.message
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.supergroup
import dev.kyralabs.pingogram.feature.chat.ui.composer.ComposerUiState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * A channel's direct messages, the subscriber's side (the owner's boards 7a and 7b): "Message channel" in the
 * composer's place of a channel that takes them, with its price where messages cost Telegram Stars, opening the thread;
 * and the thread's composer, a label where Pingogram can't pay.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatDirectMessagesTest : ChatViewModelHarness() {
    private fun TestScope.channel(
        stars: Long = 0,
        hasDirectMessages: Boolean = true,
        status: ChatMemberStatus = ChatMemberStatus.Member,
    ) = run {
        groups.supergroupMap.value =
            mapOf(
                SUPERGROUP_ID to supergroup(1_000, status = status).copy(hasDirectMessages = hasDirectMessages),
                DM_GROUP to directMessagesGroup(stars),
            )
        joins.directMessagesChats[SUPERGROUP_ID] = Outcome.Success(DirectMessagesChat(DM_CHAT, DM_GROUP))
        showing(channelChat, history(message(1, CHANNEL_CHAT)))
    }

    private fun directMessagesGroup(
        stars: Long,
        administered: Boolean = false,
    ) = supergroup(0, isChannel = false, status = ChatMemberStatus.Left).copy(
        id = DM_GROUP,
        isDirectMessages = true,
        isAdministeredDirectMessages = administered,
        paidMessageStarCount = stars,
    )

    /** The user's thread with the channel, its messages costing [stars] each. */
    private fun TestScope.thread(
        stars: Long,
        administered: Boolean = false,
        canSend: Boolean = true,
    ) = run {
        groups.supergroupMap.value = mapOf(DM_GROUP to directMessagesGroup(stars, administered))
        val chat =
            channelChat.copy(
                id = DM_CHAT,
                type = ChatType.Supergroup(DM_GROUP, isChannel = false),
                permissions = if (canSend) ChatPermissions.All else ChatPermissions.None,
            )
        showing(chat, history(message(1, DM_CHAT)))
    }

    @Test
    fun `a channel the user reads offers to message it, opening the user's thread`() =
        runViewModelTest {
            val viewModel = channel()

            assertEquals(ComposerUiState.MessageChannel(DM_CHAT, starCount = 0), viewModel.state.composer)
            viewModel.effects.test {
                send(viewModel, ChatEvent.OpenChat(DM_CHAT))
                assertEquals(ChatEffect.OpenChat(DM_CHAT), awaitItem())
            }
            assertTrue(joins.joined.isEmpty())
        }

    @Test
    fun `where messages cost Stars, the button says how many and follows the price`() =
        runViewModelTest {
            val viewModel = channel(stars = 10)
            assertEquals(ComposerUiState.MessageChannel(DM_CHAT, starCount = 10), viewModel.state.composer)

            groups.supergroupMap.value += DM_GROUP to directMessagesGroup(25)
            runCurrent()

            assertEquals(ComposerUiState.MessageChannel(DM_CHAT, starCount = 25), viewModel.state.composer)
        }

    @Test
    fun `nothing where the channel takes no direct messages, or Telegram names no chat`() =
        runViewModelTest {
            assertEquals(ComposerUiState.Hidden, channel(hasDirectMessages = false).state.composer)

            joins.directMessagesChats[SUPERGROUP_ID] = Outcome.Failure(DomainError.NotFound)
            assertEquals(ComposerUiState.Hidden, viewModel(CHANNEL_CHAT).state.composer)
        }

    @Test
    fun `an administrator who posts keeps the composer`() =
        runViewModelTest {
            val viewModel = channel(status = ChatMemberStatus.Administrator(canPostMessages = true))

            assertTrue(viewModel.state.composer is ComposerUiState.Available)
        }

    @Test
    fun `the thread takes free messages from a subscriber who is no member of it`() =
        runViewModelTest {
            assertTrue(thread(stars = 0).state.composer is ComposerUiState.Available)
        }

    @Test
    fun `the thread of a channel that charges says so instead of a composer`() =
        runViewModelTest {
            val viewModel = thread(stars = 10)

            assertEquals(
                ComposerUiState.Unavailable(UiText.plural(R.plurals.composer_channel_charges, 10, 10)),
                viewModel.state.composer,
            )
        }

    @Test
    fun `the channel's administrators never pay, and nobody writes where Telegram forbids it`() =
        runViewModelTest {
            groups.supergroupMap.value =
                mapOf(
                    DM_GROUP to
                        directMessagesGroup(10, administered = true).copy(
                            status = ChatMemberStatus.Administrator(canPostMessages = true),
                        ),
                )
            val chat = channelChat.copy(id = DM_CHAT, type = ChatType.Supergroup(DM_GROUP, isChannel = false))
            assertTrue(showing(chat, history(message(1, DM_CHAT))).state.composer is ComposerUiState.Available)

            assertEquals(
                ComposerUiState.Unavailable(UiText.resource(R.string.composer_restricted)),
                thread(stars = 0, canSend = false).state.composer,
            )
        }

    @Test
    fun `a subscriber's thread offers to suggest a post, the administrators' does not`() =
        runViewModelTest {
            assertEquals(true, (thread(stars = 0).state.composer as ComposerUiState.Available).canSuggestPost)

            groups.supergroupMap.value =
                mapOf(
                    DM_GROUP to
                        directMessagesGroup(0, administered = true).copy(
                            status = ChatMemberStatus.Administrator(canPostMessages = true),
                        ),
                )
            val chat = channelChat.copy(id = DM_CHAT, type = ChatType.Supergroup(DM_GROUP, isChannel = false))
            val composer = showing(chat, history(message(1, DM_CHAT))).state.composer as ComposerUiState.Available
            assertEquals(false, composer.canSuggestPost)
        }

    @Test
    fun `a suggested post shows its card, which a free one's administrator can approve`() =
        runViewModelTest {
            val post =
                message(2, DM_CHAT).copy(
                    suggestedPost = SuggestedPost(sendDate = 0, canBeApproved = true, canBeDeclined = true),
                )
            val paid =
                message(3, DM_CHAT).copy(
                    suggestedPost =
                        SuggestedPost(SuggestedPostPrice.Stars(50), canBeApproved = true, canBeDeclined = true),
                )
            groups.supergroupMap.value = mapOf(DM_GROUP to directMessagesGroup(0))
            val chat = channelChat.copy(id = DM_CHAT, type = ChatType.Supergroup(DM_GROUP, isChannel = false))
            val viewModel = showing(chat, history(message(1, DM_CHAT), post, paid))

            val free = viewModel.bubble(2).suggestedPost!!
            assertEquals(true to true, free.canApprove to free.canDecline)
            assertEquals(false, free.approvesElsewhere)
            val offer = viewModel.bubble(3).suggestedPost!!
            assertEquals(false to true, offer.canApprove to offer.canDecline)
            assertEquals(true, offer.approvesElsewhere)
            assertEquals(UiText.plural(R.plurals.suggested_post_offers_stars, 50, 50L), offer.price)
            assertEquals(null, viewModel.bubble(1).suggestedPost)
        }

    private companion object {
        const val DM_CHAT = -1_002_000_000_001L
        const val DM_GROUP = 2_000_000_001L
    }
}
