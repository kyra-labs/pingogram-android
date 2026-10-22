package dev.kyralabs.pingogram.feature.chat.ui

import app.cash.turbine.test
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.ChatMemberStatus
import dev.kyralabs.pingogram.core.domain.model.ChatType
import dev.kyralabs.pingogram.core.domain.model.JoinResult
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.CHANNEL_CHAT
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.SUPERGROUP_ID
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.channelChat
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.history
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.message
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.supergroup
import dev.kyralabs.pingogram.feature.chat.ui.composer.ComposerUiState
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** "Join" in the composer's place in a public group or channel the user is not in (`REQUIREMENTS.md` FR-P5). */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatJoiningTest : ChatViewModelHarness() {
    private fun publicChannel(status: ChatMemberStatus = ChatMemberStatus.Left) =
        supergroup(memberCount = 1_000, status = status).copy(usernames = persistentListOf("news"))

    private fun TestScope.channel(status: ChatMemberStatus = ChatMemberStatus.Left) =
        run {
            groups.supergroupMap.value = mapOf(SUPERGROUP_ID to publicChannel(status))
            showing(channelChat, history(message(1, CHANNEL_CHAT)))
        }

    @Test
    fun `a public channel the user is not in offers to join it`() =
        runViewModelTest {
            val viewModel = channel()

            assertEquals(ComposerUiState.Join(isChannel = true, requestsToJoin = false), viewModel.state.composer)
        }

    @Test
    fun `a public group that accepts members by request offers a request`() =
        runViewModelTest {
            groups.supergroupMap.value =
                mapOf(SUPERGROUP_ID to publicChannel().copy(isChannel = false, joinByRequest = true))
            val viewModel =
                showing(
                    channelChat.copy(type = ChatType.Supergroup(SUPERGROUP_ID, isChannel = false)),
                    history(message(1, CHANNEL_CHAT)),
                )

            assertEquals(ComposerUiState.Join(isChannel = false, requestsToJoin = true), viewModel.state.composer)
        }

    @Test
    fun `nothing to join for a member, someone removed, or a private channel`() =
        runViewModelTest {
            assertEquals(ComposerUiState.Hidden, channel(ChatMemberStatus.Member).state.composer)
            assertEquals(ComposerUiState.Hidden, channel(ChatMemberStatus.Banned).state.composer)

            groups.supergroupMap.value = mapOf(SUPERGROUP_ID to supergroup(1_000, status = ChatMemberStatus.Left))
            assertEquals(ComposerUiState.Hidden, viewModel(CHANNEL_CHAT).state.composer)
        }

    @Test
    fun `joining asks Telegram once, busy meanwhile`() =
        runViewModelTest {
            val viewModel = channel()

            // The second tap comes while the first still waits for Telegram.
            viewModel.onEvent(ChatEvent.JoinChat)
            viewModel.onEvent(ChatEvent.JoinChat)
            runCurrent()

            assertEquals(listOf(CHANNEL_CHAT), joins.joined)
            assertEquals(ComposerUiState.Join(isChannel = true, requestsToJoin = false), viewModel.state.composer)
        }

    @Test
    fun `a request sent and a refusal are snackbars`() =
        runViewModelTest {
            val viewModel = channel()
            viewModel.effects.test {
                joins.result = Outcome.Success(JoinResult.RequestSent)
                send(viewModel, ChatEvent.JoinChat)
                assertEquals(ChatEffect.JoinRequestSent, awaitItem())

                joins.result = Outcome.Success(JoinResult.Declined)
                send(viewModel, ChatEvent.JoinChat)
                assertEquals(ChatEffect.JoinRefused, awaitItem())
            }
        }

    @Test
    fun `a failure to join is a snackbar with the error`() =
        runViewModelTest {
            val viewModel = channel()
            joins.result = Outcome.Failure(DomainError.TooManyChats)

            viewModel.effects.test {
                send(viewModel, ChatEvent.JoinChat)
                assertEquals(ChatEffect.ShowError(DomainError.TooManyChats), awaitItem())
            }
        }
}
