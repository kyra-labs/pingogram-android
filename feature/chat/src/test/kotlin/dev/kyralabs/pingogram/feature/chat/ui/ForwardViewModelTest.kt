package dev.kyralabs.pingogram.feature.chat.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.CreationExtras
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.navigation.ForwardRoute
import dev.kyralabs.pingogram.core.ui.text.UiText
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.ADA
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.BASIC_GROUP_ID
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.GROUP_CHAT
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.ME
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.PRIVATE_CHAT
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.basicGroup
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.chat
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.groupChat
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.message
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.user
import dev.kyralabs.pingogram.feature.chat.ui.forward.ForwardResults
import dev.kyralabs.pingogram.feature.chat.ui.forward.ForwardViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.reflect.KClass
import kotlin.reflect.cast

/**
 * The forward destination's view model (`ForwardRoute`, the media viewer's Forward): it opens the conversation's sheet
 * for the route's messages, forwards in the app's scope so that leaving the destination does not stop it, hands what
 * came of it to [ForwardResults] for the screen below, and closes once the sheet closed or could not open. The sheet
 * itself is [ChatForwardTest]'s; reading the route after process death is [ForwardViewModelSavedStateTest]'s.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ForwardViewModelTest : ChatViewModelHarness() {
    private val results = ForwardResults()

    /** What [results] got, in order. */
    private val reported = mutableListOf<ChatEffect>()

    /**
     * Ada's messages 1 and 2 forwarded from her chat, which TDLib finds unless [found] is false; the group is where
     * they can go.
     */
    private fun TestScope.forwardViewModel(found: Boolean = true): ForwardViewModel {
        users.setUsers(user(ME), user(ADA, firstName = "Ada"))
        chats.setChats(chat(title = "Ada Lovelace"), chat(id = ME, title = "Me"), groupChat)
        chats.mainList.value = listOf(groupChat)
        groups.basicGroupMap.value = mapOf(BASIC_GROUP_ID to basicGroup(memberCount = 3))
        messages.onMessage =
            { _, id -> if (found) Outcome.Success(message(id)) else Outcome.Failure(DomainError.NotFound) }
        val viewModel =
            ForwardViewModel(
                route = ForwardRoute(PRIVATE_CHAT, listOf(1, 2)),
                chats = chats,
                messages = messages,
                search = search,
                users = users,
                groups = groups,
                actions = messageActions,
                dispatcher = StandardTestDispatcher(testScheduler),
                applicationScope = backgroundScope,
                results = results,
            )
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.sheet.collect {} }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { results.effects.collect { reported += it } }
        runCurrent()
        return viewModel
    }

    private fun TestScope.send(
        viewModel: ForwardViewModel,
        vararg events: ForwardEvent,
    ) {
        events.forEach {
            viewModel.onEvent(it)
            runCurrent()
        }
    }

    @Test
    fun `opens the sheet for the route's messages, once TDLib told they can be forwarded`() =
        runViewModelTest {
            val viewModel = forwardViewModel()

            assertEquals(listOf(PRIVATE_CHAT to 1L, PRIVATE_CHAT to 2L), messages.messageRequests)
            assertEquals(listOf(1L, 2L), messageActions.propertyRequests)
            assertEquals(2, viewModel.sheet.value?.messageCount)
            assertEquals(
                listOf(ME, GROUP_CHAT),
                viewModel.sheet.value
                    ?.targets
                    ?.map { it.chatId },
            )
            assertFalse(viewModel.closed.value)
        }

    @Test
    fun `forwarding goes on after the destination left, and the screen below hears of it`() =
        runViewModelTest {
            val forwarded = CompletableDeferred<Outcome<Unit>>()
            messageActions.onAction = { forwarded.await() }
            val viewModel = forwardViewModel()

            send(viewModel, ForwardEvent.ToggleTarget(GROUP_CHAT), ForwardEvent.Send)
            assertTrue(viewModel.closed.value)
            assertNull(viewModel.sheet.value)
            clear(viewModel)
            forwarded.complete(Outcome.Success(Unit))
            runCurrent()

            assertEquals(listOf("forward:$PRIVATE_CHAT:[1, 2]:$GROUP_CHAT:false:false"), messageActions.calls)
            assertEquals(listOf(ChatEffect.Forwarded(1, UiText.Plain("Group"), openChatId = GROUP_CHAT)), reported)
        }

    @Test
    fun `a failed forward is reported with its error`() =
        runViewModelTest {
            messageActions.onAction = { Outcome.Failure(DomainError.SendingNotAllowed) }
            val viewModel = forwardViewModel()

            send(viewModel, ForwardEvent.ToggleTarget(GROUP_CHAT), ForwardEvent.Send)

            assertEquals(listOf(ChatEffect.ForwardFailed(DomainError.SendingNotAllowed, 1, 0)), reported)
        }

    @Test
    fun `dismissing closes without forwarding`() =
        runViewModelTest {
            val viewModel = forwardViewModel()

            send(viewModel, ForwardEvent.ToggleTarget(GROUP_CHAT), ForwardEvent.Dismiss)

            assertTrue(viewModel.closed.value)
            assertTrue(messageActions.calls.isEmpty())
            assertTrue(reported.isEmpty())
        }

    @Test
    fun `a message that cannot be loaded closes it with the error`() =
        runViewModelTest {
            val viewModel = forwardViewModel(found = false)

            assertTrue(viewModel.closed.value)
            assertNull(viewModel.sheet.value)
            assertEquals(listOf<ChatEffect>(ChatEffect.ShowError(DomainError.NotFound)), reported)
        }

    @Test
    fun `messages TDLib no longer lets be forwarded close it, saying so`() =
        runViewModelTest {
            messageActions.onProperties = {
                Outcome.Success(FakeMessageActionRepository.ALL_ALLOWED.copy(canBeForwarded = false))
            }
            val viewModel = forwardViewModel()

            assertTrue(viewModel.closed.value)
            assertNull(viewModel.sheet.value)
            assertEquals(listOf<ChatEffect>(ChatEffect.ActionUnavailable), reported)
        }

    /** Clears [viewModel] as leaving the destination does, which cancels its scope. */
    private fun clear(viewModel: ForwardViewModel) {
        val store = ViewModelStore()
        val factory =
            object : ViewModelProvider.Factory {
                override fun <T : ViewModel> create(
                    modelClass: KClass<T>,
                    extras: CreationExtras,
                ): T = modelClass.cast(viewModel)
            }
        ViewModelProvider.create(store, factory)[ForwardViewModel::class]
        store.clear()
    }
}
