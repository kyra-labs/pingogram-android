package dev.kyralabs.pingogram.feature.profile.chatinfo

import androidx.lifecycle.SavedStateHandle
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.data.repository.OpenChats
import dev.kyralabs.pingogram.core.data.repository.TdlibChatRepository
import dev.kyralabs.pingogram.core.data.repository.TdlibGroupAdministrationRepository
import dev.kyralabs.pingogram.core.data.repository.TdlibGroupInfoRepository
import dev.kyralabs.pingogram.core.data.repository.TdlibUserRepository
import dev.kyralabs.pingogram.core.tdlib.TdError
import dev.kyralabs.pingogram.core.tdlib.TdResult
import dev.kyralabs.pingogram.core.testing.TdApiFixtures
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
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
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The administrators' pages on the real TDLib repositories: [MemberRightsViewModel] (promoting within the user's own
 * rights, restricting within the group's defaults, revoking, process death), [ChatPermissionsViewModel] (a switch and
 * the slow mode, shown at once, undone on a refusal) and [EditChatInfoViewModel] (what changed is sent, a refused title
 * shows under its field).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AdministrationViewModelsTest : GroupViewModelTest() {
    private fun <T> TestScope.collected(flow: Flow<T>): List<T> {
        val items = mutableListOf<T>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { flow.toList(items) }
        return items
    }

    // Rights

    private suspend fun TestScope.rights(
        restrict: Boolean,
        handle: SavedStateHandle = SavedStateHandle(),
    ): MemberRightsViewModel {
        val users = TdlibUserRepository(client, backgroundScope)
        runCurrent()
        client.emit(TdApiFixtures.updateUser(TdApiFixtures.user(id = ADA, firstName = "Ada")))
        client.on(TdApi.GetUser::class, TdResult.Success(TdApiFixtures.user(id = ADA, firstName = "Ada")))
        runCurrent()
        val viewModel =
            MemberRightsViewModel(
                chatId = CHAT,
                userId = ADA,
                restrict = restrict,
                savedStateHandle = handle,
                groupInfoRepository = TdlibGroupInfoRepository(client),
                administrationRepository = TdlibGroupAdministrationRepository(client),
                userRepository = users,
            )
        created += viewModel
        runCurrent()
        return viewModel
    }

    private val MemberRightsViewModel.content get() = uiState.value as MemberRightsUiState.Content

    private fun MemberRightsUiState.Content.switch(key: String) = switches.single { it.key == key }

    @Test
    fun `promoting starts from the usual rights the admin has, and grants only those`() =
        runTest {
            answerSupergroup()
            client.on(TdApi.GetChatMember::class, TdResult.Success(member(ADA, TdApiFixtures.memberStatus())))
            val viewModel = rights(restrict = false)

            with(viewModel.content) {
                assertTrue(isNew)
                assertTrue(switch(AdminRight.DeleteMessages.key).checked)
                // Pinning is a usual right, but this admin lacks it: off, and not to be switched on.
                assertFalse(switch(AdminRight.PinMessages.key).checked)
                assertFalse(switch(AdminRight.PinMessages.key).enabled)
            }
            viewModel.onEvent(MemberRightsEvent.Toggle(AdminRight.PinMessages.key, on = true))
            viewModel.onEvent(MemberRightsEvent.Toggle(AdminRight.DeleteMessages.key, on = false))
            assertFalse(viewModel.content.switch(AdminRight.PinMessages.key).checked)

            val effects = collected(viewModel.effects)
            viewModel.onEvent(MemberRightsEvent.Save)
            runCurrent()

            val sent = client.sentOf<TdApi.SetChatMemberStatus>().single().status as TdApi.ChatMemberStatusAdministrator
            assertFalse(sent.rights.canDeleteMessages)
            assertFalse(sent.rights.canPinMessages)
            assertTrue(sent.rights.canInviteUsers)
            assertEquals(listOf(MemberRightsEffect.Saved), effects)
        }

    @Test
    fun `the switches changed survive process death`() =
        runTest {
            answerSupergroup()
            client.on(TdApi.GetChatMember::class, TdResult.Success(member(ADA, TdApiFixtures.memberStatus())))
            val handle = SavedStateHandle()
            rights(restrict = false, handle = handle).onEvent(
                MemberRightsEvent.Toggle(AdminRight.ChangeInfo.key, on = false),
            )

            val restored = rights(restrict = false, handle = handle)

            assertFalse(restored.content.switch(AdminRight.ChangeInfo.key).checked)
        }

    @Test
    fun `restricting allows nothing the group's defaults do not, and lifting makes a plain member`() =
        runTest {
            answerSupergroup()
            val restricted =
                TdApi.ChatMemberStatusRestricted(
                    true,
                    0,
                    TdApi.ChatPermissions().also { it.canSendBasicMessages = true },
                )
            client.on(TdApi.GetChatMember::class, TdResult.Success(member(ADA, restricted)))
            val viewModel = rights(restrict = true)

            with(viewModel.content) {
                assertTrue(canRevoke)
                assertTrue(switch(Permission.SendMessages.key).checked)
                // The chat's defaults allow only text messages.
                assertFalse(switch(Permission.SendPhotos.key).enabled)
            }
            viewModel.onEvent(MemberRightsEvent.Revoke)
            runCurrent()

            assertTrue(client.sentOf<TdApi.SetChatMemberStatus>().single().status is TdApi.ChatMemberStatusMember)
        }

    @Test
    fun `a member that cannot be read is an error, and retrying reads it`() =
        runTest {
            answerSupergroup()
            client.on(TdApi.GetChatMember::class, TdResult.Failure(TdError(500, "Network is unreachable")))
            val viewModel = rights(restrict = false)
            assertEquals(MemberRightsUiState.Error(DomainError.Network), viewModel.uiState.value)

            client.on(TdApi.GetChatMember::class, TdResult.Success(member(ADA, TdApiFixtures.memberStatus())))
            viewModel.onEvent(MemberRightsEvent.Retry)
            runCurrent()

            assertTrue(viewModel.uiState.value is MemberRightsUiState.Content)
        }

    // Permissions

    private fun TestScope.permissions(): ChatPermissionsViewModel {
        val viewModel =
            ChatPermissionsViewModel(
                chatId = CHAT,
                groupInfoRepository = TdlibGroupInfoRepository(client),
                administrationRepository = TdlibGroupAdministrationRepository(client),
            )
        created += viewModel
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        runCurrent()
        return viewModel
    }

    @Test
    fun `a permission switched sends every permission, and a refusal takes it back`() =
        runTest {
            answerSupergroup()
            client.on(TdApi.SetChatPermissions::class, TdResult.Failure(TdError(400, "CHAT_ADMIN_REQUIRED")))
            val viewModel = permissions()
            val effects = collected(viewModel.effects)
            val content = viewModel.uiState.value as ChatPermissionsUiState.Content
            assertEquals(30, content.slowModeDelay)

            viewModel.onEvent(ChatPermissionsEvent.Toggle(Permission.SendPolls.key, on = true))
            runCurrent()

            val sent = client.sentOf<TdApi.SetChatPermissions>().single().permissions
            assertTrue(sent.canSendPolls && sent.canSendBasicMessages)
            val after = viewModel.uiState.value as ChatPermissionsUiState.Content
            assertFalse(after.switches.single { it.key == Permission.SendPolls.key }.checked)
            assertEquals(listOf(ChatPermissionsEffect.ShowError(DomainError.AdminRightsRequired)), effects)
        }

    @Test
    fun `the slow mode is sent when chosen, and shows until the group says the same`() =
        runTest {
            answerSupergroup()
            client.on(TdApi.SetChatSlowModeDelay::class, TdResult.Success(TdApi.Ok()))
            val viewModel = permissions()

            viewModel.onEvent(ChatPermissionsEvent.SetSlowMode(300))
            runCurrent()

            assertEquals(300, client.sentOf<TdApi.SetChatSlowModeDelay>().single().slowModeDelay)
            assertEquals(300, (viewModel.uiState.value as ChatPermissionsUiState.Content).slowModeDelay)
        }

    // Name and description

    private suspend fun TestScope.editInfo(handle: SavedStateHandle = SavedStateHandle()): EditChatInfoViewModel {
        val chats = TdlibChatRepository(client, backgroundScope, StandardTestDispatcher(testScheduler), OpenChats())
        runCurrent()
        client.emit(TdApiFixtures.updateNewChat(chat()))
        runCurrent()
        val viewModel =
            EditChatInfoViewModel(
                chatId = CHAT,
                savedStateHandle = handle,
                chatRepository = chats,
                groupInfoRepository = TdlibGroupInfoRepository(client),
                administrationRepository = TdlibGroupAdministrationRepository(client),
            )
        created += viewModel
        runCurrent()
        return viewModel
    }

    @Test
    fun `saving sends only what changed and closes the page`() =
        runTest {
            answerSupergroup()
            client.on(TdApi.SetChatDescription::class, TdResult.Success(TdApi.Ok()))
            val viewModel = editInfo()
            val effects = collected(viewModel.effects)
            assertEquals("Rust Berlin", (viewModel.uiState.value as EditChatInfoUiState.Content).title)

            viewModel.onEvent(EditChatInfoEvent.DescriptionChanged("Meetups and talks"))
            viewModel.onEvent(EditChatInfoEvent.Save)
            runCurrent()

            assertTrue(client.sentOf<TdApi.SetChatTitle>().isEmpty())
            assertEquals("Meetups and talks", client.sentOf<TdApi.SetChatDescription>().single().description)
            assertEquals(listOf(EditChatInfoEffect.Saved), effects)
        }

    @Test
    fun `a refused title shows under its field, and what was typed survives process death`() =
        runTest {
            answerSupergroup()
            client.on(TdApi.SetChatTitle::class, TdResult.Failure(TdError(400, "CHAT_TITLE_EMPTY")))
            val handle = SavedStateHandle()
            val viewModel = editInfo(handle)

            viewModel.onEvent(EditChatInfoEvent.TitleChanged("Rust"))
            viewModel.onEvent(EditChatInfoEvent.Save)
            runCurrent()

            val content = viewModel.uiState.value as EditChatInfoUiState.Content
            assertEquals(DomainError.TitleInvalid, content.titleError)
            assertFalse(content.isSaving)
            assertEquals("Rust", (editInfo(handle).uiState.value as EditChatInfoUiState.Content).title)
        }
}
