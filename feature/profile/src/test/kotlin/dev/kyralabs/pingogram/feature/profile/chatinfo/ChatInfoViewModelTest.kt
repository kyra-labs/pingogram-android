package dev.kyralabs.pingogram.feature.profile.chatinfo

import androidx.lifecycle.SavedStateHandle
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.data.repository.OpenChats
import dev.kyralabs.pingogram.core.data.repository.TdlibChatRepository
import dev.kyralabs.pingogram.core.data.repository.TdlibGroupAdministrationRepository
import dev.kyralabs.pingogram.core.data.repository.TdlibGroupInfoRepository
import dev.kyralabs.pingogram.core.data.repository.TdlibSearchRepository
import dev.kyralabs.pingogram.core.data.repository.TdlibSharedMediaRepository
import dev.kyralabs.pingogram.core.data.repository.TdlibUserRepository
import dev.kyralabs.pingogram.core.domain.model.GroupKind
import dev.kyralabs.pingogram.core.domain.model.MemberFilter
import dev.kyralabs.pingogram.core.domain.model.MessageSender
import dev.kyralabs.pingogram.core.domain.model.SharedMediaKind
import dev.kyralabs.pingogram.core.tdlib.TdError
import dev.kyralabs.pingogram.core.tdlib.TdResult
import dev.kyralabs.pingogram.core.testing.FakeIdVisibilityRepository
import dev.kyralabs.pingogram.core.testing.TdApiFixtures
import dev.kyralabs.pingogram.core.ui.text.UiText
import dev.kyralabs.pingogram.feature.profile.R
import dev.kyralabs.pingogram.feature.profile.ui.SharedMediaEvent
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
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.Locale

/**
 * [ChatInfoViewModel] through the real TDLib repositories on a [FakeTdlibClient]: the header, the link, the members
 * and the administration rows by the user's rights, the actions and their confirmations, errors, and the shared media
 * tab kept across process death.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatInfoViewModelTest : GroupViewModelTest() {
    private val idVisibility = FakeIdVisibilityRepository()

    private suspend fun TestScope.viewModel(handle: SavedStateHandle = SavedStateHandle()): ChatInfoViewModel {
        val users = TdlibUserRepository(client, backgroundScope)
        val chats = TdlibChatRepository(client, backgroundScope, StandardTestDispatcher(testScheduler), OpenChats())
        runCurrent()
        client.emit(TdApiFixtures.myIdOption(ME))
        client.emit(TdApiFixtures.updateUser(TdApiFixtures.user(id = ADA, firstName = "Ada", lastName = "Lovelace")))
        client.emit(TdApiFixtures.updateNewChat(chat()))
        client.on(
            TdApi.SearchChatMessages::class,
            TdResult.Success(TdApi.FoundChatMessages(0, emptyArray(), 0)),
        )
        runCurrent()
        val viewModel =
            ChatInfoViewModel(
                chatId = CHAT,
                savedStateHandle = handle,
                chatRepository = chats,
                groupInfoRepository = TdlibGroupInfoRepository(client),
                administrationRepository = TdlibGroupAdministrationRepository(client),
                userRepository = users,
                sharedMediaRepository = TdlibSharedMediaRepository(client),
                searchRepository = TdlibSearchRepository(client, chats),
                idVisibility = idVisibility,
                clock = clock,
                timePatterns = patterns,
                dispatcher = StandardTestDispatcher(testScheduler),
                locale = { Locale.US },
            )
        created += viewModel
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.media.collect {} }
        runCurrent()
        return viewModel
    }

    private fun TestScope.effectsOf(viewModel: ChatInfoViewModel): List<ChatInfoEffect> {
        val effects = mutableListOf<ChatInfoEffect>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.effects.toList(effects) }
        return effects
    }

    private fun TestScope.send(
        viewModel: ChatInfoViewModel,
        vararg events: ChatInfoEvent,
    ) {
        events.forEach {
            viewModel.onEvent(it)
            runCurrent()
        }
    }

    private val ChatInfoViewModel.content get() = uiState.value as ChatInfoUiState.Content

    @Test
    fun `a public group shows its title, kind and count, its link, the first members and the admin's rows`() =
        runTest {
            answerSupergroup()
            val viewModel = viewModel()

            with(viewModel.content) {
                assertEquals(UiText.Plain("Rust Berlin"), header.name)
                val status = header.status as UiText.Resource
                assertEquals(R.string.chat_info_status, status.id)
                assertEquals(UiText.resource(R.string.chat_info_public_group), status.args[0])
                assertEquals(GroupKind.Supergroup, kind)
                assertEquals("https://t.me/rustberlin", link?.url)
                assertEquals("Meetups", description?.text)
                assertEquals(1_024, members?.totalCount)
                assertEquals(UiText.Plain("Ada Lovelace"), members?.items?.single()?.name)
                with(checkNotNull(administration)) {
                    assertTrue(canEditInfo && canEditPermissions && canManageInviteLinks)
                    assertEquals(3, administratorCount)
                    assertEquals(2, bannedCount)
                    assertEquals(1, restrictedCount)
                }
                assertTrue(actions.canLeave && actions.canClearHistory)
                assertFalse(actions.canDelete)
                assertTrue(actions.canExport)
            }
            val preview = client.sentOf<TdApi.GetSupergroupMembers>().single()
            assertInstanceOf(TdApi.SupergroupMembersFilterRecent::class.java, preview.filter)
            assertEquals(ChatInfoViewModel.MEMBERS_PREVIEW, preview.limit)
        }

    @Test
    fun `a private channel's reader sees no link, no members and no administration`() =
        runTest {
            answerSupergroup(
                status = TdApiFixtures.memberStatus(),
                isChannel = true,
                usernames = emptyList(),
                full = TdResult.Success(fullInfo(canGetMembers = false)),
            )
            val viewModel = viewModel()

            with(viewModel.content) {
                assertEquals(GroupKind.Channel, kind)
                assertNull(link)
                assertNull(members)
                assertNull(administration)
                assertFalse(actions.canClearHistory)
            }
            assertTrue(client.sentOf<TdApi.GetSupergroupMembers>().isEmpty())
        }

    @Test
    fun `the owner of a private group gets the invite link and can delete the group`() =
        runTest {
            answerSupergroup(status = TdApiFixtures.creatorStatus(), usernames = emptyList())
            val viewModel = viewModel()

            with(viewModel.content) {
                assertEquals("https://t.me/+primary", link?.url)
                assertFalse(checkNotNull(link).isPublic)
                assertTrue(actions.canDelete)
            }
        }

    @Test
    fun `leaving asks first, then leaves the chat and closes the page`() =
        runTest {
            answerSupergroup()
            client.on(TdApi.LeaveChat::class, TdResult.Success(TdApi.Ok()))
            val viewModel = viewModel()
            val effects = effectsOf(viewModel)

            send(viewModel, ChatInfoEvent.Leave)
            assertEquals(ChatInfoDialog.Leave, viewModel.content.dialog)
            assertTrue(client.sentOf<TdApi.LeaveChat>().isEmpty())
            send(viewModel, ChatInfoEvent.Confirm)

            assertNull(viewModel.content.dialog)
            assertEquals(CHAT, client.sentOf<TdApi.LeaveChat>().single().chatId)
            assertEquals(listOf(ChatInfoEffect.Left), effects)
        }

    @Test
    fun `clearing the history deletes it for the user only, and a failure is a snackbar`() =
        runTest {
            answerSupergroup()
            client.on(
                TdApi.DeleteChatHistory::class,
                TdResult.Failure(TdError(429, "Too Many Requests: retry after 4")),
            )
            val viewModel = viewModel()
            val effects = effectsOf(viewModel)

            send(viewModel, ChatInfoEvent.ClearHistory, ChatInfoEvent.Confirm)
            client.on(TdApi.DeleteChatHistory::class, TdResult.Success(TdApi.Ok()))
            send(viewModel, ChatInfoEvent.ClearHistory, ChatInfoEvent.Confirm)

            assertFalse(client.sentOf<TdApi.DeleteChatHistory>().first().revoke)
            assertEquals(
                listOf(ChatInfoEffect.ShowError(DomainError.FloodWait(4)), ChatInfoEffect.HistoryCleared),
                effects,
            )
        }

    @Test
    fun `dismissing a dialog does nothing`() =
        runTest {
            answerSupergroup()
            val viewModel = viewModel()

            send(viewModel, ChatInfoEvent.Leave, ChatInfoEvent.DismissDialog)

            assertNull(viewModel.content.dialog)
            assertTrue(client.sentOf<TdApi.LeaveChat>().isEmpty())
        }

    @Test
    fun `the link is copied, shared and shown as a QR code`() =
        runTest {
            answerSupergroup()
            val viewModel = viewModel()
            val effects = effectsOf(viewModel)

            send(viewModel, ChatInfoEvent.CopyLink, ChatInfoEvent.ShareLink, ChatInfoEvent.ShowQrCode)

            assertEquals(
                listOf(
                    ChatInfoEffect.CopyText("https://t.me/rustberlin", CopiedChatText.Link),
                    ChatInfoEffect.Share("https://t.me/rustberlin"),
                ),
                effects,
            )
            assertEquals(ChatInfoDialog.QrCode("https://t.me/rustberlin"), viewModel.content.dialog)
        }

    @Test
    fun `the chat's ID shows while Show IDs is on, and is copied`() =
        runTest {
            answerSupergroup()
            val viewModel = viewModel()
            val effects = effectsOf(viewModel)
            assertNull(viewModel.content.id)

            idVisibility.shown.value = true
            runCurrent()
            assertEquals(CHAT, viewModel.content.id)

            send(viewModel, ChatInfoEvent.CopyId)
            assertEquals(listOf(ChatInfoEffect.CopyText(CHAT.toString(), CopiedChatText.Id)), effects)
        }

    @Test
    fun `muting mutes the chat`() =
        runTest {
            answerSupergroup()
            client.on(TdApi.SetChatNotificationSettings::class, TdResult.Success(TdApi.Ok()))
            val viewModel = viewModel()

            send(viewModel, ChatInfoEvent.ToggleMute)

            assertTrue(
                client
                    .sentOf<TdApi.SetChatNotificationSettings>()
                    .single()
                    .notificationSettings.muteFor > 0,
            )
        }

    @Test
    fun `members, pages and profiles open as effects`() =
        runTest {
            answerSupergroup()
            val viewModel = viewModel()
            val effects = effectsOf(viewModel)

            send(
                viewModel,
                ChatInfoEvent.OpenMember(MessageSender.User(ADA)),
                ChatInfoEvent.OpenMembers(MemberFilter.Banned),
                ChatInfoEvent.Open(ChatInfoPage.Permissions),
            )

            assertEquals(
                listOf(
                    ChatInfoEffect.OpenProfile(ADA),
                    ChatInfoEffect.OpenMembers(MemberFilter.Banned),
                    ChatInfoEffect.Open(ChatInfoPage.Permissions),
                ),
                effects,
            )
        }

    @Test
    fun `a chat TDLib does not know is an error, and retrying looks again`() =
        runTest {
            answerSupergroup()
            client.on(TdApi.GetChat::class, TdResult.Failure(TdError(404, "Not Found")))
            val viewModel = viewModel()
            assertEquals(ChatInfoUiState.Error(DomainError.NotFound), viewModel.uiState.value)

            client.on(TdApi.GetChat::class, TdResult.Success(chat()))
            send(viewModel, ChatInfoEvent.Retry)

            assertInstanceOf(ChatInfoUiState.Content::class.java, viewModel.uiState.value)
        }

    @Test
    fun `a full info that fails for the network is a snackbar, and what it fills is left out`() =
        runTest {
            answerSupergroup(full = TdResult.Failure(TdError(500, "Network is unreachable")))
            val viewModel = viewModel()
            val effects = effectsOf(viewModel)
            runCurrent()

            assertNull(viewModel.content.description)
            assertNull(viewModel.content.members)
            assertTrue(ChatInfoEffect.ShowError(DomainError.Network) in effects, effects.toString())
        }

    @Test
    fun `the shared media tab survives process death`() =
        runTest {
            answerSupergroup()
            val handle = SavedStateHandle()
            val first = viewModel(handle)
            first.onMediaEvent(SharedMediaEvent.Select(SharedMediaKind.Links))
            runCurrent()

            val restored = viewModel(handle)

            assertEquals(SharedMediaKind.Links, restored.media.value.selected)
        }
}
