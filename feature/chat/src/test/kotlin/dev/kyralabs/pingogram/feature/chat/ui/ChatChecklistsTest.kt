package dev.kyralabs.pingogram.feature.chat.ui

import androidx.lifecycle.SavedStateHandle
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.Checklist
import dev.kyralabs.pingogram.core.domain.model.ChecklistTask
import dev.kyralabs.pingogram.core.domain.model.FormattedText
import dev.kyralabs.pingogram.core.domain.model.MessageContent
import dev.kyralabs.pingogram.core.domain.model.MessageSender
import dev.kyralabs.pingogram.core.domain.model.NewChecklistTask
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.ADA
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.GRACE
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.GROUP_CHAT
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.ME
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.groupChat
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.history
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.message
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.user
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

/**
 * Checklists in [ChatViewModel] (`REQUIREMENTS.md` FR-C4): ticking tasks off and unticking them (pending in the
 * bubble meanwhile), what a checklist that forbids it shows, adding a task through the inline field (open across
 * process death), who ticked each task in the bubble, and what Telegram's refusals show.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatChecklistsTest : ChatViewModelHarness() {
    private val effects = mutableListOf<ChatEffect>()

    /** The group with Ada's message [MESSAGE] showing [list]; the effects it sends go into [effects]. */
    private fun TestScope.conversation(
        list: Checklist,
        handle: SavedStateHandle = savedState,
    ): ChatViewModel {
        users.setUsers(user(ME, firstName = "Me"), user(ADA, firstName = "Ada"), user(GRACE, firstName = "Grace"))
        chats.setChats(groupChat)
        messages.setHistory(GROUP_CHAT, history(checklistMessage(list)))
        val viewModel = viewModel(GROUP_CHAT, handle = handle)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.effects.collect { effects += it } }
        runCurrent()
        return viewModel
    }

    /** The checklist shows [list] now, as `updateMessageContent` makes it. */
    private fun TestScope.update(list: Checklist) {
        messages.setHistory(GROUP_CHAT, history(checklistMessage(list)))
        runCurrent()
    }

    private fun checklistMessage(list: Checklist) =
        message(MESSAGE, GROUP_CHAT, MessageSender.User(ADA), content = MessageContent.Checklist(list))

    @Nested
    inner class Marking {
        @Test
        fun `ticking a task sends it as done, and the bubble shows it pending meanwhile`() =
            runViewModelTest {
                val answer = CompletableDeferred<Outcome<Unit>>()
                checklists.onMark = { _, _ -> answer.await() }
                val viewModel = conversation(checklist())

                send(viewModel, ChecklistEvent.MarkTask(MESSAGE, taskId = 2, done = true))
                val pending = viewModel.bubble(MESSAGE).checklist?.pending
                answer.complete(Outcome.Success(Unit))
                runCurrent()

                assertEquals(listOf(Triple(MESSAGE, listOf(2), emptyList<Int>())), checklists.marks)
                assertEquals(persistentSetOf(2), pending)
                assertEquals(persistentSetOf<Int>(), viewModel.bubble(MESSAGE).checklist?.pending)
                assertTrue(effects.isEmpty())
            }

        @Test
        fun `unticking a task sends it as not done`() =
            runViewModelTest {
                val viewModel = conversation(checklist(done = setOf(1)))

                send(viewModel, ChecklistEvent.MarkTask(MESSAGE, taskId = 1, done = false))

                assertEquals(listOf(Triple(MESSAGE, emptyList<Int>(), listOf(1))), checklists.marks)
            }

        @Test
        fun `a second tap while the change is on its way sends nothing`() =
            runViewModelTest {
                val answer = CompletableDeferred<Outcome<Unit>>()
                checklists.onMark = { _, _ -> answer.await() }
                val viewModel = conversation(checklist())

                send(
                    viewModel,
                    ChecklistEvent.MarkTask(MESSAGE, taskId = 2, done = true),
                    ChecklistEvent.MarkTask(MESSAGE, taskId = 2, done = false),
                )
                answer.complete(Outcome.Success(Unit))
                runCurrent()

                assertEquals(1, checklists.marks.size)
            }

        @Test
        fun `a checklist the user may not tick takes no tap and says so`() =
            runViewModelTest {
                val viewModel = conversation(checklist(canMark = false))

                send(viewModel, ChecklistEvent.MarkTask(MESSAGE, taskId = 2, done = true))

                assertTrue(checklists.marks.isEmpty())
                assertEquals(listOf(ChatEffect.ActionUnavailable), effects)
            }

        @Test
        fun `a task that is gone meanwhile says so`() =
            runViewModelTest {
                val viewModel = conversation(checklist())

                send(viewModel, ChecklistEvent.MarkTask(MESSAGE, taskId = 9, done = true))

                assertTrue(checklists.marks.isEmpty())
                assertEquals(listOf(ChatEffect.ActionUnavailable), effects)
            }

        @Test
        fun `a refused change is a snackbar, and the task is no longer pending`() =
            runViewModelTest {
                checklists.onMark = { _, _ -> Outcome.Failure(DomainError.Network) }
                val viewModel = conversation(checklist())

                send(viewModel, ChecklistEvent.MarkTask(MESSAGE, taskId = 2, done = true))

                assertEquals(listOf(ChatEffect.ShowError(DomainError.Network)), effects)
                assertEquals(persistentSetOf<Int>(), viewModel.bubble(MESSAGE).checklist?.pending)
            }

        @Test
        fun `the bubble names who ticked each task`() =
            runViewModelTest {
                val viewModel = conversation(checklist(done = setOf(1), completer = GRACE))

                val completers = viewModel.bubble(MESSAGE).checklist?.completers

                assertEquals(setOf(1), completers?.keys)
                assertEquals(GRACE, completers?.get(1)?.id)
            }
    }

    @Nested
    inner class Adding {
        @Test
        fun `Add task opens the field, and the text goes out with the next free id`() =
            runViewModelTest {
                val viewModel = conversation(checklist())

                send(viewModel, ChecklistEvent.OpenAddTask(MESSAGE))
                val open = viewModel.bubble(MESSAGE).checklist?.isAddingTask
                send(viewModel, ChecklistEvent.AddTask(MESSAGE, " Order chai "))

                assertEquals(true, open)
                assertEquals(listOf(MESSAGE to listOf(NewChecklistTask(4, "Order chai"))), checklists.added)
                assertEquals(false, viewModel.bubble(MESSAGE).checklist?.isAddingTask)
            }

        @Test
        fun `the field stays open across process death`() =
            runViewModelTest {
                val viewModel = conversation(checklist())
                send(viewModel, ChecklistEvent.OpenAddTask(MESSAGE))

                val restored = conversation(checklist(), handle = SavedStateHandle(savedState.keys().associateWith { savedState.get<Any>(it) }))

                assertEquals(true, restored.bubble(MESSAGE).checklist?.isAddingTask)
            }

        @Test
        fun `a refused task keeps the field open with a snackbar`() =
            runViewModelTest {
                checklists.onAdd = { Outcome.Failure(DomainError.Network) }
                val viewModel = conversation(checklist())

                send(viewModel, ChecklistEvent.OpenAddTask(MESSAGE), ChecklistEvent.AddTask(MESSAGE, "Order chai"))

                assertEquals(listOf(ChatEffect.ShowError(DomainError.Network)), effects)
                assertEquals(true, viewModel.bubble(MESSAGE).checklist?.isAddingTask)
            }

        @Test
        fun `a checklist the user may not add to offers nothing and says so`() =
            runViewModelTest {
                val viewModel = conversation(checklist(canAdd = false))

                send(viewModel, ChecklistEvent.OpenAddTask(MESSAGE))
                send(viewModel, ChecklistEvent.AddTask(MESSAGE, "Order chai"))

                assertTrue(checklists.added.isEmpty())
                assertEquals(listOf(ChatEffect.ActionUnavailable, ChatEffect.ActionUnavailable), effects)
                assertFalse(viewModel.bubble(MESSAGE).checklist?.isAddingTask == true)
            }

        @Test
        fun `a blank task goes nowhere`() =
            runViewModelTest {
                val viewModel = conversation(checklist())

                send(viewModel, ChecklistEvent.AddTask(MESSAGE, "   "))

                assertTrue(checklists.added.isEmpty())
                assertEquals(listOf(ChatEffect.ActionUnavailable), effects)
            }

        @Test
        fun `dismissing the field closes it`() =
            runViewModelTest {
                val viewModel = conversation(checklist())

                send(viewModel, ChecklistEvent.OpenAddTask(MESSAGE), ChecklistEvent.DismissAddTask(MESSAGE))

                assertEquals(false, viewModel.bubble(MESSAGE).checklist?.isAddingTask)
                assertNull(viewModel.bubble(MESSAGE).checklist?.pending?.firstOrNull())
            }
    }

    private fun checklist(
        done: Set<Int> = emptySet(),
        canMark: Boolean = true,
        canAdd: Boolean = true,
        completer: Long = ADA,
    ) = Checklist(
        title = FormattedText("Meetup", persistentListOf()),
        tasks =
            listOf("Book the room", "Bring the projector", "Print name tags")
                .mapIndexed { index, text ->
                    val id = index + 1
                    ChecklistTask(
                        id = id,
                        text = FormattedText(text, persistentListOf()),
                        completedBy = if (id in done) MessageSender.User(completer) else null,
                        completionDate = if (id in done) 1_700_000_000L else 0,
                    )
                }.toImmutableList(),
        othersCanAddTasks = canAdd,
        canAddTasks = canAdd,
        othersCanMarkTasksAsDone = canMark,
        canMarkTasksAsDone = canMark,
    )

    private companion object {
        const val MESSAGE = 10L
    }
}
