package dev.kyralabs.pingogram.feature.profile.chatinfo

import androidx.lifecycle.SavedStateHandle
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.data.repository.OpenChats
import dev.kyralabs.pingogram.core.data.repository.TdlibChatEventLogRepository
import dev.kyralabs.pingogram.core.data.repository.TdlibChatRepository
import dev.kyralabs.pingogram.core.data.repository.TdlibUserRepository
import dev.kyralabs.pingogram.core.tdlib.TdError
import dev.kyralabs.pingogram.core.tdlib.TdResult
import dev.kyralabs.pingogram.core.testing.TdApiFixtures
import dev.kyralabs.pingogram.core.ui.text.UiText
import dev.kyralabs.pingogram.feature.profile.R
import dev.kyralabs.pingogram.feature.profile.chatinfo.actions.RecentActionsEffect
import dev.kyralabs.pingogram.feature.profile.chatinfo.actions.RecentActionsEvent
import dev.kyralabs.pingogram.feature.profile.chatinfo.actions.RecentActionsFilter
import dev.kyralabs.pingogram.feature.profile.chatinfo.actions.RecentActionsUiState
import dev.kyralabs.pingogram.feature.profile.chatinfo.actions.RecentActionsViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.drinkless.tdlib.TdApi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.Locale

/** The recent actions page on the real TDLib repositories. */
@OptIn(ExperimentalCoroutinesApi::class)
class RecentActionsViewModelTest : GroupViewModelTest() {
    private fun event(
        id: Long,
        action: TdApi.ChatEventAction,
        actor: Long = ADA,
    ) = TdApi.ChatEvent(id, NOW.epochSecond.toInt(), TdApi.MessageSenderUser(actor), action)

    private fun answer(vararg events: TdApi.ChatEvent) =
        client.on(TdApi.GetChatEventLog::class, TdResult.Success(TdApi.ChatEvents(arrayOf(*events))))

    private suspend fun TestScope.viewModel(handle: SavedStateHandle = SavedStateHandle()): RecentActionsViewModel {
        val users = TdlibUserRepository(client, backgroundScope)
        val chats = TdlibChatRepository(client, backgroundScope, StandardTestDispatcher(testScheduler), OpenChats())
        runCurrent()
        client.emit(TdApiFixtures.updateUser(TdApiFixtures.user(id = ADA, firstName = "Ada")))
        client.emit(TdApiFixtures.updateUser(TdApiFixtures.user(id = GRACE, firstName = "Grace")))
        runCurrent()
        val viewModel =
            RecentActionsViewModel(
                CHAT,
                handle,
                TdlibChatEventLogRepository(client),
                chats,
                users,
                clock,
                patterns,
                { Locale.US },
            )
        created += viewModel
        runCurrent()
        return viewModel
    }

    private val RecentActionsViewModel.content get() = uiState.value as RecentActionsUiState.Content

    @Test
    fun `lists who did what and when, newest first`() =
        runTest {
            answer(
                event(2, TdApi.ChatEventTitleChanged("Rust", "Rust Berlin")),
                event(1, TdApi.ChatEventMemberInvited(GRACE, TdApiFixtures.memberStatus())),
            )

            val content = viewModel().content
            val actions = content.actions

            assertEquals(listOf(2L, 1L), actions.map { it.id })
            assertEquals(UiText.Plain("Ada"), actions[0].name)
            assertEquals(R.string.recent_actions_title, (actions[0].text as UiText.Resource).id)
            assertEquals(
                UiText.resource(R.string.recent_actions_invited, UiText.Plain("⁨Grace⁩")),
                actions[1].text,
            )
            assertEquals("Oct 22, 12:00 PM", actions[0].time)
            assertFalse(content.hasMore)
        }

    @Test
    fun `the chips picked go to TDLib as filters, and survive process death`() =
        runTest {
            answer()
            val handle = SavedStateHandle()
            val viewModel = viewModel(handle)
            assertNull(client.sentOf<TdApi.GetChatEventLog>().single().filters)

            viewModel.onEvent(RecentActionsEvent.ToggleFilter(RecentActionsFilter.Admins))
            runCurrent()

            val filters = client.sentOf<TdApi.GetChatEventLog>().last().filters
            assertTrue(filters.memberPromotions)
            assertFalse(filters.messageEdits)
            assertEquals(setOf(RecentActionsFilter.Admins), viewModel(handle).content.filters)
        }

    @Test
    fun `a full page lets the next one load from its last action`() =
        runTest {
            answer(*Array(50) { event(100L - it, TdApi.ChatEventMemberLeft()) })
            val viewModel = viewModel()
            assertTrue(viewModel.content.hasMore)

            answer(event(10, TdApi.ChatEventMemberLeft()))
            viewModel.onEvent(RecentActionsEvent.LoadMore)
            runCurrent()

            assertEquals(51L, client.sentOf<TdApi.GetChatEventLog>().last().fromEventId)
            assertEquals(51, viewModel.content.actions.size)
            assertFalse(viewModel.content.hasMore)
        }

    @Test
    fun `a person's row opens their profile`() =
        runTest {
            answer(event(1, TdApi.ChatEventMemberLeft()))
            val viewModel = viewModel()

            viewModel.onEvent(RecentActionsEvent.Open(ADA))

            assertEquals(RecentActionsEffect.OpenProfile(ADA), viewModel.effects.first())
        }

    @Test
    fun `actions that cannot be read show the error, and trying again reads them`() =
        runTest {
            client.on(TdApi.GetChatEventLog::class, TdResult.Failure(TdError(400, "CHAT_ADMIN_REQUIRED")))
            val viewModel = viewModel()
            assertEquals(RecentActionsUiState.Error(DomainError.AdminRightsRequired), viewModel.uiState.value)

            answer()
            viewModel.onEvent(RecentActionsEvent.Retry)
            runCurrent()

            assertTrue(viewModel.content.actions.isEmpty())
        }

    private companion object {
        const val GRACE = 43L
    }
}
