package dev.kyralabs.pingogram.feature.chat.ui

import androidx.lifecycle.SavedStateHandle
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.FormattedText
import dev.kyralabs.pingogram.core.domain.model.MessageContent
import dev.kyralabs.pingogram.core.domain.model.MessageSender
import dev.kyralabs.pingogram.core.domain.model.NewPollOption
import dev.kyralabs.pingogram.core.domain.model.Poll
import dev.kyralabs.pingogram.core.domain.model.PollOption
import dev.kyralabs.pingogram.core.domain.model.PollType
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.ADA
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.GROUP_CHAT
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.ME
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.NOW
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.groupChat
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.history
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.message
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.user
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * What polls show and do since 2026 in [ChatViewModel] (boards 4a, 4b): how long a poll stays open as of the current
 * minute, the description kept in the poll rather than the bubble's text, an answer's link opening for screen readers,
 * and adding an answer through the inline field (open across process death, closed once Telegram took it, open with a
 * snackbar when it refused).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatPollsV2Test : ChatViewModelHarness() {
    private val effects = mutableListOf<ChatEffect>()

    private fun TestScope.conversation(
        content: MessageContent.Poll,
        handle: SavedStateHandle = savedState,
    ): ChatViewModel {
        users.setUsers(user(ME, firstName = "Me"), user(ADA, firstName = "Ada"))
        chats.setChats(groupChat)
        messages.setHistory(
            GROUP_CHAT,
            history(message(MESSAGE, GROUP_CHAT, MessageSender.User(ADA), content = content)),
        )
        val viewModel = viewModel(GROUP_CHAT, handle = handle)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.effects.collect { effects += it } }
        runCurrent()
        return viewModel
    }

    @Test
    fun `a poll that closes by itself counts down from the current minute`() =
        runViewModelTest {
            val viewModel = conversation(content(poll(closeDate = NOW.epochSecond + TWO_HOURS)))

            assertEquals(TWO_HOURS + NOW.epochSecond % MINUTE, viewModel.bubble(MESSAGE).pollClosesIn)
        }

    @Test
    fun `a poll without a closing time has none, and its description stays the poll's`() =
        runViewModelTest {
            val viewModel = conversation(content(poll(), description = "We meet on Dec 14"))

            val bubble = viewModel.bubble(MESSAGE)

            assertNull(bubble.pollClosesIn)
            assertEquals("", bubble.text.text)
        }

    @Test
    fun `an answer's link opens for screen readers`() =
        runViewModelTest {
            val viewModel = conversation(content(poll()))

            send(viewModel, PollEvent.OpenLink("https://example.org"))

            assertEquals(listOf<ChatEffect>(ChatEffect.OpenUrl("https://example.org")), effects)
        }

    @Test
    fun `an added answer goes to Telegram, the field waiting, and closes once taken`() =
        runViewModelTest {
            val answer = CompletableDeferred<Outcome<Unit>>()
            polls.onAddOption = { answer.await() }
            val viewModel = conversation(content(poll(), canAddOption = true))

            send(viewModel, PollOptionEvent.Open(MESSAGE), PollOptionEvent.Add(MESSAGE, " Piranesi "))
            val waiting = viewModel.bubble(MESSAGE).pollAdding
            answer.complete(Outcome.Success(Unit))
            runCurrent()

            assertEquals(listOf(MESSAGE to NewPollOption("Piranesi")), polls.addedOptions)
            assertEquals(PollAdding.Sending, waiting)
            assertNull(viewModel.bubble(MESSAGE).pollAdding)
        }

    @Test
    fun `a refused answer leaves the field open with a snackbar`() =
        runViewModelTest {
            polls.onAddOption = { Outcome.Failure(DomainError.Network) }
            val viewModel = conversation(content(poll(), canAddOption = true))

            send(viewModel, PollOptionEvent.Open(MESSAGE), PollOptionEvent.Add(MESSAGE, "Piranesi"))

            assertEquals(PollAdding.Open, viewModel.bubble(MESSAGE).pollAdding)
            assertEquals(listOf<ChatEffect>(ChatEffect.ShowError(DomainError.Network)), effects)
        }

    @Test
    fun `a poll that takes no answers says so and sends nothing`() =
        runViewModelTest {
            val viewModel = conversation(content(poll(), canAddOption = false))

            send(viewModel, PollOptionEvent.Open(MESSAGE), PollOptionEvent.Add(MESSAGE, "Piranesi"))

            assertTrue(polls.addedOptions.isEmpty())
            assertNull(viewModel.bubble(MESSAGE).pollAdding)
            assertEquals(List(2) { ChatEffect.ActionUnavailable }, effects)
        }

    @Test
    fun `the open field comes back after process death, and dismissing closes it`() =
        runViewModelTest {
            val viewModel = conversation(content(poll(), canAddOption = true))
            send(viewModel, PollOptionEvent.Open(MESSAGE))
            val restored =
                conversation(
                    content(poll(), canAddOption = true),
                    handle = SavedStateHandle(savedState.keys().associateWith { savedState.get<Any>(it) }),
                )
            val reopened = restored.bubble(MESSAGE).pollAdding

            send(restored, PollOptionEvent.Dismiss(MESSAGE))

            assertEquals(PollAdding.Open, reopened)
            assertNull(restored.bubble(MESSAGE).pollAdding)
        }

    private fun content(
        poll: Poll,
        description: String = "",
        canAddOption: Boolean = false,
    ) = MessageContent.Poll(poll, FormattedText(description, persistentListOf()), canAddOption = canAddOption)

    private fun poll(closeDate: Long = 0) =
        Poll(
            id = 9,
            question = FormattedText("Which book?", persistentListOf()),
            options =
                listOf("Piranesi", "Klara")
                    .mapIndexed { index, text ->
                        PollOption(index, FormattedText(text, persistentListOf()), 0, 0, false, false)
                    }.toImmutableList(),
            totalVoterCount = 0,
            isAnonymous = false,
            allowsMultipleAnswers = false,
            canSeeResults = false,
            isClosed = false,
            type = PollType.Regular,
            closeDate = closeDate,
        )

    private companion object {
        const val MESSAGE = 10L
        const val TWO_HOURS = 7_200L
        const val MINUTE = 60L
    }
}
