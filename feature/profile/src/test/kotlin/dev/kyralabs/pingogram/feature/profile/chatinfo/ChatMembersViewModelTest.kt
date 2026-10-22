package dev.kyralabs.pingogram.feature.profile.chatinfo

import androidx.lifecycle.SavedStateHandle
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.data.repository.OpenChats
import dev.kyralabs.pingogram.core.data.repository.TdlibChatRepository
import dev.kyralabs.pingogram.core.data.repository.TdlibGroupAdministrationRepository
import dev.kyralabs.pingogram.core.data.repository.TdlibGroupInfoRepository
import dev.kyralabs.pingogram.core.data.repository.TdlibUserRepository
import dev.kyralabs.pingogram.core.domain.model.MemberFilter
import dev.kyralabs.pingogram.core.domain.model.MessageSender
import dev.kyralabs.pingogram.core.tdlib.TdError
import dev.kyralabs.pingogram.core.tdlib.TdResult
import dev.kyralabs.pingogram.core.testing.TdApiFixtures
import dev.kyralabs.pingogram.core.ui.text.UiText
import dev.kyralabs.pingogram.feature.profile.R
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.drinkless.tdlib.TdApi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.Locale
import kotlin.time.Duration.Companion.milliseconds

/**
 * [ChatMembersViewModel] on the real TDLib repositories: filters by the user's rights, pages, searching after a pause,
 * the actions on a member, errors, and the filter and query kept across process death.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatMembersViewModelTest : GroupViewModelTest() {
    private suspend fun TestScope.viewModel(
        handle: SavedStateHandle = SavedStateHandle(),
        filter: MemberFilter = MemberFilter.Recent,
    ): ChatMembersViewModel {
        val users = TdlibUserRepository(client, backgroundScope)
        val chats = TdlibChatRepository(client, backgroundScope, StandardTestDispatcher(testScheduler), OpenChats())
        runCurrent()
        client.emit(TdApiFixtures.myIdOption(ME))
        client.emit(TdApiFixtures.updateUser(TdApiFixtures.user(id = ADA, firstName = "Ada", lastName = "Lovelace")))
        runCurrent()
        val viewModel =
            ChatMembersViewModel(
                chatId = CHAT,
                initialFilter = filter,
                savedStateHandle = handle,
                groupInfoRepository = TdlibGroupInfoRepository(client),
                administrationRepository = TdlibGroupAdministrationRepository(client),
                userRepository = users,
                chatRepository = chats,
                clock = clock,
                timePatterns = patterns,
                dispatcher = StandardTestDispatcher(testScheduler),
                locale = { Locale.US },
                searchDelay = SEARCH_DELAY,
            )
        created += viewModel
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        runCurrent()
        return viewModel
    }

    private fun TestScope.effectsOf(viewModel: ChatMembersViewModel): List<ChatMembersEffect> {
        val effects = mutableListOf<ChatMembersEffect>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.effects.toList(effects) }
        return effects
    }

    private fun TestScope.send(
        viewModel: ChatMembersViewModel,
        vararg events: ChatMembersEvent,
    ) {
        events.forEach {
            viewModel.onEvent(it)
            runCurrent()
        }
    }

    private val ChatMembersViewModel.items
        get() = (uiState.value.list as MembersList.Content).items

    @Test
    fun `members load with the filters an admin who restricts may choose, and the actions on each`() =
        runTest {
            answerSupergroup()
            val viewModel = viewModel()

            with(viewModel.uiState.value) {
                assertEquals(
                    listOf(
                        MemberFilter.Recent,
                        MemberFilter.Administrators,
                        MemberFilter.Restricted,
                        MemberFilter.Banned,
                    ),
                    filters,
                )
                assertEquals(1, (list as MembersList.Content).totalCount)
            }
            val ada = viewModel.items.single()
            assertEquals(UiText.Plain("Ada Lovelace"), ada.name)
            // Restricting and removing, but no promoting without that right.
            assertEquals(listOf(MemberAction.Restrict, MemberAction.Remove), ada.actions)
        }

    @Test
    fun `a member sees no restricted or removed users, and no actions`() =
        runTest {
            answerSupergroup(status = TdApiFixtures.memberStatus())
            val viewModel = viewModel()

            assertEquals(listOf(MemberFilter.Recent, MemberFilter.Administrators), viewModel.uiState.value.filters)
            assertTrue(
                viewModel.items
                    .single()
                    .actions
                    .isEmpty(),
            )
        }

    @Test
    fun `a filter loads its members anew, and is kept across process death`() =
        runTest {
            answerSupergroup()
            val handle = SavedStateHandle()
            val viewModel = viewModel(handle)

            send(viewModel, ChatMembersEvent.SelectFilter(MemberFilter.Banned))
            assertInstanceOf(
                TdApi.SupergroupMembersFilterBanned::class.java,
                client.sentOf<TdApi.GetSupergroupMembers>().last().filter,
            )

            val restored = viewModel(handle)
            assertEquals(MemberFilter.Banned, restored.uiState.value.filter)
        }

    @Test
    fun `a search waits for typing to pause`() =
        runTest {
            answerSupergroup()
            val viewModel = viewModel()
            val before = client.sentOf<TdApi.GetSupergroupMembers>().size

            send(viewModel, ChatMembersEvent.Search("a"), ChatMembersEvent.Search("ad"))
            advanceTimeBy(SEARCH_DELAY + 1.milliseconds)
            runCurrent()

            val searches = client.sentOf<TdApi.GetSupergroupMembers>().drop(before)
            assertEquals(listOf("ad"), searches.map { (it.filter as TdApi.SupergroupMembersFilterSearch).query })
            assertEquals("ad", viewModel.uiState.value.query)
        }

    @Test
    fun `removing asks first, then bans the member and takes the row out`() =
        runTest {
            answerSupergroup()
            val viewModel = viewModel()
            val effects = effectsOf(viewModel)
            val ada = MessageSender.User(ADA)

            send(viewModel, ChatMembersEvent.Act(ada, MemberAction.Remove))
            assertEquals(
                ada,
                viewModel.uiState.value.removing
                    ?.sender,
            )
            assertTrue(client.sentOf<TdApi.SetChatMemberStatus>().isEmpty())
            send(viewModel, ChatMembersEvent.ConfirmRemove)

            assertInstanceOf(
                TdApi.ChatMemberStatusBanned::class.java,
                client.sentOf<TdApi.SetChatMemberStatus>().single().status,
            )
            assertNull(viewModel.uiState.value.removing)
            assertEquals(MembersList.Empty, viewModel.uiState.value.list)
            assertEquals(
                listOf(ChatMembersEffect.Done(R.string.chat_info_member_removed, UiText.Plain("Ada Lovelace"))),
                effects,
            )
        }

    @Test
    fun `promoting and restricting open the rights page, a refused change is a snackbar`() =
        runTest {
            answerSupergroup()
            client.on(TdApi.SetChatMemberStatus::class, TdResult.Failure(TdError(400, "CHAT_ADMIN_REQUIRED")))
            val viewModel = viewModel()
            val effects = effectsOf(viewModel)
            val ada = MessageSender.User(ADA)

            send(
                viewModel,
                ChatMembersEvent.Act(ada, MemberAction.Promote),
                ChatMembersEvent.Act(ada, MemberAction.Restrict),
                ChatMembersEvent.Act(ada, MemberAction.Unrestrict),
                ChatMembersEvent.Open(ada),
            )

            assertEquals(
                listOf(
                    ChatMembersEffect.OpenRights(ADA, restrict = false),
                    ChatMembersEffect.OpenRights(ADA, restrict = true),
                    ChatMembersEffect.ShowError(DomainError.AdminRightsRequired),
                    ChatMembersEffect.OpenProfile(ADA),
                ),
                effects,
            )
        }

    @Test
    fun `a first page that fails is an error, and retrying loads it`() =
        runTest {
            answerSupergroup()
            client.on(
                TdApi.GetSupergroupMembers::class,
                TdResult.Failure(TdError(429, "Too Many Requests: retry after 5")),
            )
            val viewModel = viewModel()
            assertEquals(MembersList.Error(DomainError.FloodWait(5)), viewModel.uiState.value.list)

            client.on(
                TdApi.GetSupergroupMembers::class,
                TdResult.Success(TdApi.ChatMembers(1, arrayOf(member(ADA, TdApiFixtures.memberStatus())))),
            )
            send(viewModel, ChatMembersEvent.Retry)

            assertEquals(1, viewModel.items.size)
        }

    @Test
    fun `the list loads again after the rights page saved`() =
        runTest {
            answerSupergroup()
            val handle = SavedStateHandle()
            viewModel(handle)
            val before = client.sentOf<TdApi.GetSupergroupMembers>().size

            handle[MEMBERS_CHANGED_RESULT] = true
            runCurrent()

            assertEquals(before + 1, client.sentOf<TdApi.GetSupergroupMembers>().size)
            assertEquals(false, handle[MEMBERS_CHANGED_RESULT])
        }

    private companion object {
        val SEARCH_DELAY = 300.milliseconds
    }
}
