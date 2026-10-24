package dev.kyralabs.pingogram.feature.chat.ui

import androidx.lifecycle.SavedStateHandle
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.FormattedText
import dev.kyralabs.pingogram.core.domain.model.MessageContent
import dev.kyralabs.pingogram.core.domain.model.MessageSender
import dev.kyralabs.pingogram.core.domain.model.Poll
import dev.kyralabs.pingogram.core.domain.model.PollOption
import dev.kyralabs.pingogram.core.domain.model.PollType
import dev.kyralabs.pingogram.core.domain.model.PollVoter
import dev.kyralabs.pingogram.core.domain.model.PollVoters
import dev.kyralabs.pingogram.core.ui.text.UiText
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.ADA
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.GRACE
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.GROUP_CHAT
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.ME
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.groupChat
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.history
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.message
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.user
import dev.kyralabs.pingogram.feature.chat.ui.poll.StopPollDialog
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

/**
 * Polls in [ChatViewModel] (`REQUIREMENTS.md` FR-C4): voting with one tap or several answers then "Vote", the checked
 * answers in their bubble and across process death, taking a vote back and stopping a poll from the message actions,
 * a quiz's answer, the voters' sheet, and what Telegram's refusals show.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatPollsTest : ChatViewModelHarness() {
    /** The group with its message [MESSAGE] showing [poll]; the effects it sends go into [effects]. */
    private fun TestScope.conversation(
        poll: Poll,
        handle: SavedStateHandle = savedState,
    ): ChatViewModel {
        users.setUsers(user(ME, firstName = "Me"), user(ADA, firstName = "Ada"), user(GRACE, firstName = "Grace"))
        chats.setChats(groupChat)
        messages.setHistory(GROUP_CHAT, history(pollMessage(poll)))
        val viewModel = viewModel(GROUP_CHAT, handle = handle)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.effects.collect { effects += it } }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.pollVotersSheet.collect {} }
        runCurrent()
        return viewModel
    }

    private val effects = mutableListOf<ChatEffect>()

    /** The poll shows [poll] now, as `updatePoll` makes it. */
    private fun TestScope.update(poll: Poll) {
        messages.setHistory(GROUP_CHAT, history(pollMessage(poll)))
        runCurrent()
    }

    private fun pollMessage(poll: Poll) =
        message(MESSAGE, GROUP_CHAT, MessageSender.User(ADA), content = MessageContent.Poll(poll, FormattedText.Empty))

    @Nested
    inner class Voting {
        @Test
        fun `a tap on an answer votes for it at once`() =
            runViewModelTest {
                val viewModel = conversation(poll())

                send(viewModel, PollEvent.Vote(MESSAGE, 1))

                assertEquals(listOf(MESSAGE to listOf(1)), polls.votes)
                assertTrue(effects.isEmpty())
            }

        @Test
        fun `a second tap while the vote is on its way sends nothing`() =
            runViewModelTest {
                val answer = CompletableDeferred<Outcome<Unit>>()
                polls.onVote = { answer.await() }
                val viewModel = conversation(poll())

                send(viewModel, PollEvent.Vote(MESSAGE, 1), PollEvent.Vote(MESSAGE, 0))
                answer.complete(Outcome.Success(Unit))
                runCurrent()

                assertEquals(listOf(MESSAGE to listOf(1)), polls.votes)
            }

        @Test
        fun `a closed poll takes no vote and says so`() =
            runViewModelTest {
                val viewModel = conversation(poll(isClosed = true))

                send(viewModel, PollEvent.Vote(MESSAGE, 1))

                assertEquals(emptyList<Any>(), polls.votes)
                assertEquals(listOf(ChatEffect.ActionUnavailable), effects)
            }

        @Test
        fun `answers checked in a poll with several answers show in its bubble, then go out with Vote`() =
            runViewModelTest {
                val viewModel = conversation(poll(multiple = true))

                send(
                    viewModel,
                    PollEvent.Toggle(MESSAGE, 2),
                    PollEvent.Toggle(MESSAGE, 0),
                    PollEvent.Toggle(MESSAGE, 2),
                )
                val checked = viewModel.bubble(MESSAGE).pollChoices
                send(viewModel, PollEvent.Toggle(MESSAGE, 1), PollEvent.SubmitVotes(MESSAGE))

                assertEquals(persistentSetOf(0), checked)
                assertEquals(listOf(MESSAGE to listOf(0, 1)), polls.votes)
                assertNull(viewModel.bubble(MESSAGE).pollChoices)
            }

        @Test
        fun `Vote without a checked answer sends nothing`() =
            runViewModelTest {
                val viewModel = conversation(poll(multiple = true))

                send(viewModel, PollEvent.SubmitVotes(MESSAGE))

                assertEquals(emptyList<Any>(), polls.votes)
                assertEquals(listOf(ChatEffect.ActionUnavailable), effects)
            }

        @Test
        fun `checked answers survive process death`() =
            runViewModelTest {
                val handle = SavedStateHandle()
                val first = conversation(poll(multiple = true), handle)
                send(first, PollEvent.Toggle(MESSAGE, 3), PollEvent.Toggle(MESSAGE, 1))
                clear(first)

                val restored =
                    conversation(
                        poll(multiple = true),
                        SavedStateHandle(handle.keys().associateWith { handle.get<Any>(it) }),
                    )

                assertEquals(persistentSetOf(1, 3), restored.bubble(MESSAGE).pollChoices)
            }

        @ParameterizedTest
        @MethodSource("dev.kyralabs.pingogram.feature.chat.ui.ChatPollsTest#refusals")
        fun `a refused vote shows why and keeps the checked answers`(error: DomainError) =
            runViewModelTest {
                polls.onVote = { Outcome.Failure(error) }
                val viewModel = conversation(poll(multiple = true))

                send(viewModel, PollEvent.Toggle(MESSAGE, 1), PollEvent.SubmitVotes(MESSAGE))

                assertEquals(listOf(ChatEffect.ShowError(error)), effects)
                assertEquals(persistentSetOf(1), viewModel.bubble(MESSAGE).pollChoices)
            }
    }

    @Nested
    inner class Quiz {
        @Test
        fun `a right answer confirms once the poll shows it`() =
            runViewModelTest {
                val viewModel = conversation(quiz())

                send(viewModel, PollEvent.Vote(MESSAGE, 2))
                assertTrue(effects.isEmpty())
                update(quiz(chosen = 2))

                assertEquals(listOf(PollEffect.QuizAnswered(correct = true)), effects)
                assertTrue(viewModel.state.content is ChatContent.Content)
            }

        @Test
        fun `a wrong answer rejects`() =
            runViewModelTest {
                conversation(quiz()).also { send(it, PollEvent.Vote(MESSAGE, 1)) }

                update(quiz(chosen = 1))

                assertEquals(listOf(PollEffect.QuizAnswered(correct = false)), effects)
            }

        @Test
        fun `a refused answer says why and gives no haptic`() =
            runViewModelTest {
                polls.onVote = { Outcome.Failure(DomainError.PollClosed) }
                conversation(quiz()).also { send(it, PollEvent.Vote(MESSAGE, 2)) }

                assertEquals(listOf(ChatEffect.ShowError(DomainError.PollClosed)), effects)
            }
    }

    @Nested
    inner class Actions {
        @Test
        fun `the actions of a voted poll that allows it offer to retract the vote and to stop it`() =
            runViewModelTest {
                val viewModel = conversation(poll(chosen = 1, revoting = true))

                send(viewModel, ChatEvent.ShowMessageActions(MESSAGE))

                val actions =
                    viewModel.uiState.value.messageActions
                        ?.actions
                        .orEmpty()
                assertTrue(MessageAction.RetractVote in actions)
                assertTrue(MessageAction.StopPoll in actions)
            }

        @Test
        fun `a quiz's answer cannot be retracted, and a message the user cannot edit cannot stop its poll`() =
            runViewModelTest {
                messageActions.onProperties = {
                    Outcome.Success(FakeMessageActionRepository.ALL_ALLOWED.copy(canBeEdited = false))
                }
                val viewModel = conversation(quiz(chosen = 2))

                send(viewModel, ChatEvent.ShowMessageActions(MESSAGE))

                val actions =
                    viewModel.uiState.value.messageActions
                        ?.actions
                        .orEmpty()
                assertFalse(MessageAction.RetractVote in actions)
                assertFalse(MessageAction.StopPoll in actions)
            }

        @Test
        fun `retracting sends no answers`() =
            runViewModelTest {
                val viewModel = conversation(poll(chosen = 1, revoting = true))

                send(viewModel, PollEvent.RetractVote(MESSAGE))

                assertEquals(listOf(MESSAGE to emptyList<Int>()), polls.votes)
            }

        @Test
        fun `a vote the poll does not let be changed is not retracted`() =
            runViewModelTest {
                val viewModel = conversation(poll(chosen = 1, revoting = false))

                send(viewModel, PollEvent.RetractVote(MESSAGE))

                assertEquals(emptyList<Any>(), polls.votes)
                assertEquals(listOf(ChatEffect.ActionUnavailable), effects)
            }

        @Test
        fun `stopping asks first, then stops the poll`() =
            runViewModelTest {
                val viewModel = conversation(poll())

                send(viewModel, PollEvent.StopPoll(MESSAGE))
                val asked = viewModel.stopPollDialog.value
                val stoppedBefore = polls.stopped.toList()
                send(viewModel, PollEvent.ConfirmStop)

                assertEquals(StopPollDialog(MESSAGE), asked)
                assertEquals(emptyList<Long>(), stoppedBefore)
                assertEquals(listOf(MESSAGE), polls.stopped)
                assertNull(viewModel.stopPollDialog.value)
            }

        @Test
        fun `dismissing the question stops nothing`() =
            runViewModelTest {
                val viewModel = conversation(poll())

                send(viewModel, PollEvent.StopPoll(MESSAGE), PollEvent.DismissStop)

                assertNull(viewModel.stopPollDialog.value)
                assertEquals(emptyList<Long>(), polls.stopped)
            }

        @ParameterizedTest
        @MethodSource("dev.kyralabs.pingogram.feature.chat.ui.ChatPollsTest#refusals")
        fun `a refused stop shows why`(error: DomainError) =
            runViewModelTest {
                polls.onStop = { Outcome.Failure(error) }
                val viewModel = conversation(poll())

                send(viewModel, PollEvent.StopPoll(MESSAGE), PollEvent.ConfirmStop)

                assertEquals(listOf(ChatEffect.ShowError(error)), effects)
            }
    }

    @Nested
    inner class Voters {
        private fun page(
            total: Int,
            vararg userIds: Long,
        ) = Outcome.Success(
            PollVoters(
                total,
                userIds.map { PollVoter(MessageSender.User(it), date = 1_700_000_000) }.toImmutableList(),
            ),
        )

        @Test
        fun `the sheet has a section per answer someone chose, each loading its first page`() =
            runViewModelTest {
                polls.onVoters = { option, _ -> if (option == 1) page(2, ADA, GRACE) else page(1, ME) }
                val viewModel = conversation(publicResults())

                send(viewModel, PollEvent.OpenVoters(MESSAGE))

                val sheet = viewModel.pollVotersSheet.value!!
                assertEquals(listOf(1, 2), sheet.sections.map { it.optionIndex })
                assertEquals(
                    listOf(listOf(UiText.Plain("Ada"), UiText.Plain("Grace")), listOf(UiText.Plain("Me"))),
                    sheet.sections.map { section -> section.voters.map { it.name } },
                )
                assertEquals(setOf(Triple(1, 0, 20), Triple(2, 0, 20)), polls.voterRequests.toSet())
                assertFalse(sheet.sections.any { it.hasMore })
            }

        @Test
        fun `more voters load after those shown`() =
            runViewModelTest {
                polls.onVoters = { _, offset -> if (offset == 0) page(3, ADA, GRACE) else page(3, ME) }
                val viewModel = conversation(publicResults(summer = 3, autumn = 0))

                send(viewModel, PollEvent.OpenVoters(MESSAGE))
                val first =
                    viewModel.pollVotersSheet.value!!
                        .sections
                        .single()
                send(viewModel, PollEvent.LoadMoreVoters(1))

                assertTrue(first.hasMore)
                val section =
                    viewModel.pollVotersSheet.value!!
                        .sections
                        .single()
                assertEquals(listOf(ADA, GRACE, ME), section.voters.map { it.id })
                assertFalse(section.hasMore)
                assertEquals(Triple(1, 2, 20), polls.voterRequests.last())
            }

        @Test
        fun `a page that fails says so in its section, and trying again loads it`() =
            runViewModelTest {
                polls.onVoters = { _, _ -> Outcome.Failure(DomainError.Network) }
                val viewModel = conversation(publicResults(summer = 2, autumn = 0))

                send(viewModel, PollEvent.OpenVoters(MESSAGE))
                val failed =
                    viewModel.pollVotersSheet.value!!
                        .sections
                        .single()
                polls.onVoters = { _, _ -> page(2, ADA, GRACE) }
                send(viewModel, PollEvent.LoadMoreVoters(1))

                assertEquals(DomainError.Network, failed.error)
                assertEquals(
                    2,
                    viewModel.pollVotersSheet.value!!
                        .sections
                        .single()
                        .voters.size,
                )
            }

        @Test
        fun `a tapped voter closes the sheet and opens their profile, a chat its chat`() =
            runViewModelTest {
                polls.onVoters = { _, _ -> page(1, ADA) }
                val viewModel = conversation(publicResults())

                send(viewModel, PollEvent.OpenVoters(MESSAGE), PollEvent.OpenVoter(MessageSender.User(ADA)))
                val closed = viewModel.pollVotersSheet.value
                send(viewModel, PollEvent.OpenVoters(MESSAGE), PollEvent.OpenVoter(MessageSender.Chat(GROUP_CHAT)))

                assertNull(closed)
                assertEquals(listOf(ChatEffect.OpenProfile(ADA), ChatEffect.OpenChat(GROUP_CHAT)), effects)
            }

        @Test
        fun `an anonymous poll lists no voters`() =
            runViewModelTest {
                val viewModel = conversation(publicResults().copy(isAnonymous = true))

                send(viewModel, PollEvent.OpenVoters(MESSAGE))

                assertNull(viewModel.pollVotersSheet.value)
                assertEquals(emptyList<Any>(), polls.voterRequests)
                assertEquals(listOf(ChatEffect.ActionUnavailable), effects)
            }

        @Test
        fun `dismissing closes the sheet`() =
            runViewModelTest {
                val viewModel = conversation(publicResults())

                send(viewModel, PollEvent.OpenVoters(MESSAGE), PollEvent.DismissVoters)

                assertNull(viewModel.pollVotersSheet.value)
            }
    }

    @Test
    fun `an updated poll recomposes nothing but its bubble's state`() =
        runViewModelTest {
            val viewModel = conversation(publicResults())
            val before = viewModel.bubble(MESSAGE)

            update(publicResults(summer = 5))

            val after = viewModel.bubble(MESSAGE)
            assertEquals(before.copy(content = after.content), after)
        }

    companion object {
        const val MESSAGE = 30L

        @JvmStatic
        fun refusals() = listOf(DomainError.FloodWait(12), DomainError.Network, DomainError.Unauthorized)

        private val seasons = listOf("Spring", "Summer", "Autumn", "Winter")

        fun poll(
            multiple: Boolean = false,
            isClosed: Boolean = false,
            chosen: Int? = null,
            revoting: Boolean = true,
        ) = Poll(
            id = 9,
            question = FormattedText("Best season?", persistentListOf()),
            options =
                seasons
                    .mapIndexed { index, season ->
                        PollOption(index, FormattedText(season, persistentListOf()), 0, 0, index == chosen, false)
                    }.toImmutableList(),
            totalVoterCount = if (chosen != null) 1 else 0,
            isAnonymous = true,
            allowsMultipleAnswers = multiple,
            canSeeResults = chosen != null || isClosed,
            isClosed = isClosed,
            type = PollType.Regular,
            allowsRevoting = revoting,
        )

        fun quiz(chosen: Int? = null) =
            poll(chosen = chosen, revoting = false).let { poll ->
                poll.copy(
                    type = PollType.Quiz(FormattedText("Autumn starts then.", persistentListOf())),
                    options =
                        poll.options
                            .map { it.copy(isCorrect = chosen != null && it.index == 2) }
                            .toImmutableList(),
                )
            }

        fun publicResults(
            summer: Int = 2,
            autumn: Int = 1,
        ) = poll(chosen = 1).let { poll ->
            poll.copy(
                isAnonymous = false,
                canGetVoters = true,
                totalVoterCount = summer + autumn,
                options =
                    poll.options
                        .map {
                            when (it.index) {
                                1 -> it.copy(voterCount = summer)
                                2 -> it.copy(voterCount = autumn)
                                else -> it
                            }
                        }.toImmutableList(),
            )
        }
    }
}
