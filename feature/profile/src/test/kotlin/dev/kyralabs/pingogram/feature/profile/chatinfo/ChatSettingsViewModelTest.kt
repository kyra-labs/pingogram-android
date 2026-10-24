package dev.kyralabs.pingogram.feature.profile.chatinfo

import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.data.repository.OpenChats
import dev.kyralabs.pingogram.core.data.repository.TdlibChatRepository
import dev.kyralabs.pingogram.core.data.repository.TdlibChatSettingsRepository
import dev.kyralabs.pingogram.core.domain.model.GroupKind
import dev.kyralabs.pingogram.core.tdlib.TdError
import dev.kyralabs.pingogram.core.tdlib.TdResult
import dev.kyralabs.pingogram.core.testing.TdApiFixtures
import dev.kyralabs.pingogram.feature.profile.chatinfo.settings.ChatSettingsDialog
import dev.kyralabs.pingogram.feature.profile.chatinfo.settings.ChatSettingsEffect
import dev.kyralabs.pingogram.feature.profile.chatinfo.settings.ChatSettingsEvent
import dev.kyralabs.pingogram.feature.profile.chatinfo.settings.ChatSettingsUiState
import dev.kyralabs.pingogram.feature.profile.chatinfo.settings.ChatSettingsViewModel
import dev.kyralabs.pingogram.feature.profile.chatinfo.settings.DiscussionPicker
import dev.kyralabs.pingogram.feature.profile.chatinfo.settings.ReactionsChoice
import dev.kyralabs.pingogram.feature.profile.chatinfo.settings.SignMessagesSetting
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

/** The settings page of a group or channel on the real TDLib repositories. */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatSettingsViewModelTest : GroupViewModelTest() {
    private fun TestScope.viewModel(chatId: Long = CHAT): ChatSettingsViewModel {
        val settings = TdlibChatSettingsRepository(client, backgroundScope)
        val chats = TdlibChatRepository(client, backgroundScope, StandardTestDispatcher(testScheduler), OpenChats())
        runCurrent()
        return ChatSettingsViewModel(settings, chats, chatId).also {
            created += it
            runCurrent()
        }
    }

    private val ChatSettingsViewModel.content get() = uiState.value as ChatSettingsUiState.Content

    private fun answerChannelOwner(reactions: TdApi.ChatAvailableReactions = TdApi.ChatAvailableReactionsAll(11)) {
        answerSupergroup(status = TdApiFixtures.creatorStatus(), isChannel = true)
        client.on(
            TdApi.GetChat::class,
            TdResult.Success(chat(isChannel = true).also { it.availableReactions = reactions }),
        )
    }

    @Test
    fun `an owner of a channel sees every setting a channel has`() =
        runTest {
            answerChannelOwner()

            val content = viewModel().content

            assertEquals(GroupKind.Channel, content.kind)
            assertEquals(ReactionsChoice.All, content.reactions?.choice)
            assertEquals(false, content.protectedContent)
            assertEquals(SignMessagesSetting(sign = false, showProfiles = false), content.signMessages)
            assertNull(content.topics)
            assertTrue(content.discussion?.canChange == true)
            assertFalse(content.canUpgrade)
        }

    @Test
    fun `an administrator who may change the info gets no owner's settings`() =
        runTest {
            answerSupergroup()

            val content = viewModel().content

            assertTrue(content.reactions != null)
            assertNull(content.protectedContent)
            assertNull(content.topics)
            assertNull(content.signMessages)
        }

    @Test
    fun `some reactions start with every offered emoji, and a tap takes one away`() =
        runTest {
            answerChannelOwner()
            client.on(TdApi.SetChatAvailableReactions::class, TdResult.Success(TdApi.Ok()))
            val viewModel = viewModel()
            client.emit(TdApi.UpdateActiveEmojiReactions(arrayOf("👍", "❤")))
            runCurrent()

            viewModel.onEvent(ChatSettingsEvent.ChooseReactions(ReactionsChoice.Some))
            runCurrent()
            val some = client.sentOf<TdApi.SetChatAvailableReactions>().last().availableReactions
            assertEquals(
                listOf("👍", "❤"),
                (some as TdApi.ChatAvailableReactionsSome).reactions.map {
                    (it as TdApi.ReactionTypeEmoji).emoji
                },
            )

            client.emit(TdApi.UpdateChatAvailableReactions(CHAT, some))
            runCurrent()
            viewModel.onEvent(ChatSettingsEvent.ToggleReaction("❤"))
            runCurrent()
            val left = client.sentOf<TdApi.SetChatAvailableReactions>().last().availableReactions
            assertEquals(
                listOf("👍"),
                (left as TdApi.ChatAvailableReactionsSome).reactions.map {
                    (it as TdApi.ReactionTypeEmoji).emoji
                },
            )
        }

    @Test
    fun `a refused change shows why and the page waits no longer`() =
        runTest {
            answerChannelOwner()
            client.on(TdApi.ToggleChatHasProtectedContent::class, TdResult.Failure(TdError(400, "CHAT_ADMIN_REQUIRED")))
            val viewModel = viewModel()

            viewModel.onEvent(ChatSettingsEvent.SetProtectedContent(true))
            runCurrent()

            assertTrue(viewModel.effects.first() is ChatSettingsEffect.ShowError)
            assertFalse(viewModel.content.isSaving)
        }

    @Test
    fun `the discussion picker lists the groups and links the one picked`() =
        runTest {
            answerChannelOwner()
            client.on(TdApi.GetSuitableDiscussionChats::class, TdResult.Success(TdApi.Chats(1, longArrayOf(77))))
            client.on(TdApi.SetChatDiscussionGroup::class, TdResult.Success(TdApi.Ok()))
            val viewModel = viewModel()
            client.emit(
                TdApi.UpdateNewChat(
                    TdApiFixtures.chat(id = 77, type = TdApi.ChatTypeSupergroup(7, false), title = "Rust talk"),
                ),
            )
            runCurrent()

            viewModel.onEvent(ChatSettingsEvent.OpenDiscussionPicker)
            runCurrent()
            val groups = (viewModel.content.discussionPicker as DiscussionPicker.Groups).groups
            assertEquals(listOf("Rust talk"), groups.map { it.title })

            viewModel.onEvent(ChatSettingsEvent.LinkDiscussion(77))
            runCurrent()
            assertNull(viewModel.content.discussionPicker)
            assertEquals(77, client.sentOf<TdApi.SetChatDiscussionGroup>().single().discussionChatId)
        }

    @Test
    fun `upgrading a basic group asks first, then opens the supergroup`() =
        runTest {
            client.on(
                TdApi.GetChat::class,
                TdResult.Success(TdApiFixtures.chat(id = BASIC, type = TdApi.ChatTypeBasicGroup(5), title = "Friends")),
            )
            client.on(
                TdApi.GetBasicGroup::class,
                TdResult.Success(TdApiFixtures.basicGroup(status = TdApiFixtures.creatorStatus())),
            )
            client.on(
                TdApi.UpgradeBasicGroupChatToSupergroupChat::class,
                TdResult.Success(TdApiFixtures.chat(id = 900, type = TdApi.ChatTypeSupergroup(9, false))),
            )
            val viewModel = viewModel(BASIC)
            assertTrue(viewModel.content.canUpgrade)

            viewModel.onEvent(ChatSettingsEvent.Upgrade)
            assertEquals(ChatSettingsDialog.Upgrade, viewModel.content.dialog)
            assertTrue(client.sentOf<TdApi.UpgradeBasicGroupChatToSupergroupChat>().isEmpty())

            viewModel.onEvent(ChatSettingsEvent.ConfirmDialog)
            runCurrent()

            assertEquals(ChatSettingsEffect.Upgraded(900), viewModel.effects.first())
        }

    @Test
    fun `settings that cannot be read show the error`() =
        runTest {
            client.on(TdApi.GetChat::class, TdResult.Failure(TdError(400, "Chat not found")))

            assertEquals(ChatSettingsUiState.Error(DomainError.NotFound), viewModel().uiState.value)
        }

    private companion object {
        const val BASIC = -5L
    }
}
