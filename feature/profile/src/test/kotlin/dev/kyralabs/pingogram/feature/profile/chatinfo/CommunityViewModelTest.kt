package dev.kyralabs.pingogram.feature.profile.chatinfo

import androidx.lifecycle.SavedStateHandle
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.data.repository.OpenChats
import dev.kyralabs.pingogram.core.data.repository.TdlibChatJoinRepository
import dev.kyralabs.pingogram.core.data.repository.TdlibChatRepository
import dev.kyralabs.pingogram.core.data.repository.TdlibCommunityRepository
import dev.kyralabs.pingogram.core.data.repository.TdlibGroupRepository
import dev.kyralabs.pingogram.core.data.repository.TdlibUserRepository
import dev.kyralabs.pingogram.core.tdlib.TdError
import dev.kyralabs.pingogram.core.tdlib.TdResult
import dev.kyralabs.pingogram.core.testing.TdApiFixtures
import dev.kyralabs.pingogram.feature.profile.R
import dev.kyralabs.pingogram.feature.profile.chatinfo.community.CommunityChatKind
import dev.kyralabs.pingogram.feature.profile.chatinfo.community.CommunityDialog
import dev.kyralabs.pingogram.feature.profile.chatinfo.community.CommunityEffect
import dev.kyralabs.pingogram.feature.profile.chatinfo.community.CommunityEvent
import dev.kyralabs.pingogram.feature.profile.chatinfo.community.CommunityRoleLabel
import dev.kyralabs.pingogram.feature.profile.chatinfo.community.CommunityUiState
import dev.kyralabs.pingogram.feature.profile.chatinfo.community.CommunityViewModel
import dev.kyralabs.pingogram.feature.profile.chatinfo.community.Membership
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.drinkless.tdlib.TdApi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * [CommunityViewModel] on the real TDLib repositories: the community and its chats with the user's membership,
 * joining, renaming and deleting, errors, and the rename dialog kept across process death.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CommunityViewModelTest : GroupViewModelTest() {
    private suspend fun TestScope.viewModel(handle: SavedStateHandle = SavedStateHandle()): CommunityViewModel {
        val communities = TdlibCommunityRepository(client, backgroundScope)
        val groups = TdlibGroupRepository(client, backgroundScope)
        val users = TdlibUserRepository(client, backgroundScope)
        val chats = TdlibChatRepository(client, backgroundScope, StandardTestDispatcher(testScheduler), OpenChats())
        runCurrent()
        client.emit(TdApi.UpdateCommunity(community(TdApi.CommunityMemberStatusCreator())))
        client.emit(
            TdApiFixtures.updateSupergroup(
                TdApiFixtures.supergroup(id = SUPERGROUP, memberCount = 1_120, status = TdApiFixtures.memberStatus()),
            ),
        )
        client.emit(
            TdApiFixtures.updateSupergroup(
                TdApiFixtures.supergroup(id = OTHER_SUPERGROUP, memberCount = 640, status = TdApiFixtures.leftStatus()),
            ),
        )
        client.emit(TdApiFixtures.updateNewChat(chat()))
        client.emit(
            TdApiFixtures.updateNewChat(
                TdApiFixtures.chat(
                    id = OTHER_CHAT,
                    type = TdApi.ChatTypeSupergroup(OTHER_SUPERGROUP, false),
                    title = "Spenzia Waitlist",
                ),
            ),
        )
        runCurrent()
        val viewModel =
            CommunityViewModel(
                communityId = COMMUNITY,
                savedStateHandle = handle,
                communityRepository = communities,
                chatRepository = chats,
                groupRepository = groups,
                userRepository = users,
                joinRepository = TdlibChatJoinRepository(client),
                dispatcher = StandardTestDispatcher(testScheduler),
            )
        created += viewModel
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        runCurrent()
        return viewModel
    }

    private fun community(status: TdApi.CommunityMemberStatus) =
        TdApi.Community(COMMUNITY, true, "Kyra Labs", null, 0, status, TdApi.CommunityPermissions(false), false)

    private fun answerChats(
        vararg chats: TdApi.CommunityChat,
        requests: Int = 0,
    ) = client.on(
        TdApi.GetCommunityFullInfo::class,
        TdResult.Success(TdApi.CommunityFullInfo(null, arrayOf(*chats), 1, 0, requests, null)),
    )

    private fun TestScope.effectsOf(viewModel: CommunityViewModel): List<CommunityEffect> {
        val effects = mutableListOf<CommunityEffect>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.effects.toList(effects) }
        return effects
    }

    private fun TestScope.send(
        viewModel: CommunityViewModel,
        vararg events: CommunityEvent,
    ) {
        events.forEach {
            viewModel.onEvent(it)
            runCurrent()
        }
    }

    private val CommunityViewModel.content get() = uiState.value as CommunityUiState.Content

    @Test
    fun `shows the community and its chats, with whether the user is in each`() =
        runTest {
            answerChats(TdApi.CommunityChat(CHAT, true, false), TdApi.CommunityChat(OTHER_CHAT, true, true))
            val viewModel = viewModel()

            with(viewModel.content) {
                assertEquals("Kyra Labs", header.name)
                assertEquals(CommunityRoleLabel.Owner, header.role)
                assertTrue(header.canRename && header.canDelete)
                val (joined, other) = chats
                assertEquals(
                    Triple(CHAT, Membership.Joined, CommunityChatKind.Group),
                    Triple(joined.chatId, joined.membership, joined.kind),
                )
                assertEquals(1_120, joined.memberCount)
                assertEquals(Membership.CanJoin, other.membership)
                assertTrue(other.isHidden)
            }
        }

    @Test
    fun `joining is one request, and a request an administrator must accept is a snackbar`() =
        runTest {
            answerChats(TdApi.CommunityChat(OTHER_CHAT, true, false))
            client.on(TdApi.JoinChat::class, TdResult.Failure(TdError(400, "INVITE_REQUEST_SENT")))
            val viewModel = viewModel()
            val effects = effectsOf(viewModel)

            send(viewModel, CommunityEvent.Join(OTHER_CHAT))

            assertEquals(OTHER_CHAT, client.sentOf<TdApi.JoinChat>().single().chatId)
            assertEquals(
                Membership.CanJoin,
                viewModel.content.chats
                    .single()
                    .membership,
            )
            assertEquals(listOf(CommunityEffect.Notice(R.string.community_join_requested)), effects)
        }

    @Test
    fun `renaming keeps what was typed across process death, then saves it`() =
        runTest {
            answerChats()
            client.on(TdApi.SetCommunityName::class, TdResult.Success(TdApi.Ok()))
            val handle = SavedStateHandle()
            val viewModel = viewModel(handle)
            send(viewModel, CommunityEvent.Rename, CommunityEvent.NameChanged("Kyra Labs Hub"))

            val restored = viewModel(handle)
            assertEquals(CommunityDialog.Rename("Kyra Labs Hub"), restored.content.dialog)
            send(restored, CommunityEvent.Confirm)

            assertEquals("Kyra Labs Hub", client.sentOf<TdApi.SetCommunityName>().single().name)
            assertNull(restored.content.dialog)
        }

    @Test
    fun `deleting asks first, then closes the page`() =
        runTest {
            answerChats()
            client.on(TdApi.DeleteCommunity::class, TdResult.Success(TdApi.Ok()))
            val viewModel = viewModel()
            val effects = effectsOf(viewModel)

            send(viewModel, CommunityEvent.Delete)
            assertEquals(CommunityDialog.Delete, viewModel.content.dialog)
            assertTrue(client.sentOf<TdApi.DeleteCommunity>().isEmpty())
            send(viewModel, CommunityEvent.Confirm)

            assertEquals(COMMUNITY, client.sentOf<TdApi.DeleteCommunity>().single().communityId)
            assertEquals(listOf(CommunityEffect.Deleted), effects)
        }

    @Test
    fun `a member may neither rename nor delete, and a failure to read is an error that retrying reads again`() =
        runTest {
            client.on(TdApi.GetCommunityFullInfo::class, TdResult.Failure(TdError(400, "CHANNEL_PRIVATE")))
            val viewModel = viewModel()
            assertInstanceOf(CommunityUiState.Error::class.java, viewModel.uiState.value)

            answerChats()
            client.emit(TdApi.UpdateCommunity(community(TdApi.CommunityMemberStatusMember())))
            send(viewModel, CommunityEvent.Retry)

            with(viewModel.content.header) {
                assertNull(role)
                assertEquals(false, canRename || canDelete)
                // A member still shows the community as one entry in the chat list, or not, from the menu.
                assertTrue(isJoined && hasOptions)
                assertEquals(false, canEditChatList)
            }
        }

    @Test
    fun `mutes and shows the community as one entry, each one request`() =
        runTest {
            answerChats()
            client.on(TdApi.SetCommunityNotificationSettings::class, TdResult.Success(TdApi.Ok()))
            client.on(TdApi.ToggleCommunityAreChatsCollapsed::class, TdResult.Success(TdApi.Ok()))
            val viewModel = viewModel()
            val effects = effectsOf(viewModel)

            send(viewModel, CommunityEvent.ToggleMute, CommunityEvent.ToggleCollapsed)

            assertEquals(
                Int.MAX_VALUE,
                client
                    .sentOf<TdApi.SetCommunityNotificationSettings>()
                    .single()
                    .notificationSettings.muteFor,
            )
            assertEquals(true, client.sentOf<TdApi.ToggleCommunityAreChatsCollapsed>().single().areChatsCollapsed)
            assertEquals(listOf(CommunityEffect.Notice(R.string.community_collapsed_notice)), effects)
        }

    @Test
    fun `an administrator answers the requests to add a chat, read again once answered`() =
        runTest {
            answerChats(requests = 1)
            client.on(
                TdApi.GetCommunityAddChatRequests::class,
                TdResult.Success(
                    TdApi.CommunityAddChatRequests(
                        1,
                        arrayOf(TdApi.CommunityAddChatRequest(OTHER_CHAT, true, 42, 1_700_000_000)),
                        "",
                    ),
                ),
            )
            client.on(TdApi.ProcessCommunityAddChatRequest::class, TdResult.Success(TdApi.Ok()))
            val viewModel = viewModel()
            val effects = effectsOf(viewModel)

            with(viewModel.content.requests.single()) {
                assertEquals(
                    Triple(OTHER_CHAT, "Spenzia Waitlist", true),
                    Triple(chat.chatId, chat.title, chat.isHidden),
                )
            }
            send(viewModel, CommunityEvent.AnswerRequest(OTHER_CHAT, approve = true))

            with(client.sentOf<TdApi.ProcessCommunityAddChatRequest>().single()) {
                assertEquals(Triple(COMMUNITY, OTHER_CHAT, true), Triple(communityId, chatId, approve))
            }
            assertEquals(2, client.sentOf<TdApi.GetCommunityAddChatRequests>().size)
            assertEquals(listOf(CommunityEffect.Notice(R.string.community_request_approved)), effects)
        }

    @Test
    fun `the sheet adds the chats picked, kept across process death, leaving out those in the community`() =
        runTest {
            answerChats(TdApi.CommunityChat(CHAT, true, false))
            client.on(
                TdApi.GetSuitableCommunityChats::class,
                TdResult.Success(TdApi.Chats(2, longArrayOf(CHAT, OTHER_CHAT))),
            )
            client.on(TdApi.AddCommunityChat::class, TdResult.Success(TdApi.Ok()))
            val handle = SavedStateHandle()
            val first = viewModel(handle)
            send(
                first,
                CommunityEvent.AddChats,
                CommunityEvent.PickChat(OTHER_CHAT),
                CommunityEvent.HiddenChanged(true),
            )

            val viewModel = viewModel(handle)
            val effects = effectsOf(viewModel)
            with(viewModel.content.addChats!!) {
                assertEquals(listOf(OTHER_CHAT), chats!!.map { it.chatId })
                assertEquals(setOf(OTHER_CHAT), picked)
                assertTrue(isHidden)
            }
            send(viewModel, CommunityEvent.SendChats)

            with(client.sentOf<TdApi.AddCommunityChat>().single()) {
                assertEquals(Triple(COMMUNITY, OTHER_CHAT, true), Triple(communityId, chatId, isHidden))
            }
            assertNull(viewModel.content.addChats)
            assertEquals(listOf(CommunityEffect.CountNotice(R.plurals.community_chats_added, 1)), effects)
        }

    @Test
    fun `a member suggests a chat, which Telegram turns into a request`() =
        runTest {
            answerChats()
            client.on(TdApi.GetSuitableCommunityChats::class, TdResult.Success(TdApi.Chats(1, longArrayOf(OTHER_CHAT))))
            client.on(TdApi.AddCommunityChat::class, TdResult.Failure(TdError(400, "COMMUNITY_REQUEST_CREATED")))
            val viewModel = viewModel()
            client.emit(TdApi.UpdateCommunity(community(TdApi.CommunityMemberStatusMember())))
            val effects = effectsOf(viewModel)

            send(viewModel, CommunityEvent.AddChats, CommunityEvent.PickChat(OTHER_CHAT), CommunityEvent.SendChats)

            assertEquals(listOf(CommunityEffect.CountNotice(R.plurals.community_chats_suggested, 1)), effects)
        }

    @Test
    fun `removing a chat asks first, then takes it out`() =
        runTest {
            answerChats(TdApi.CommunityChat(CHAT, true, false))
            client.on(TdApi.RemoveCommunityChat::class, TdResult.Success(TdApi.Ok()))
            val viewModel = viewModel()
            val effects = effectsOf(viewModel)

            send(viewModel, CommunityEvent.RemoveChat(CHAT))
            assertInstanceOf(CommunityDialog.RemoveChat::class.java, viewModel.content.dialog)
            send(viewModel, CommunityEvent.Confirm)

            assertEquals(CHAT, client.sentOf<TdApi.RemoveCommunityChat>().single().chatId)
            assertNull(viewModel.content.dialog)
            assertEquals(listOf(CommunityEffect.Notice(R.string.community_chat_removed)), effects)
        }

    private companion object {
        const val COMMUNITY = 77L
        const val OTHER_CHAT = -200L
        const val OTHER_SUPERGROUP = 9L
    }
}
