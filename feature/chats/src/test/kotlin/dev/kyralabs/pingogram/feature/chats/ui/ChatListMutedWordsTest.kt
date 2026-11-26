package dev.kyralabs.pingogram.feature.chats.ui

import dev.kyralabs.pingogram.core.domain.model.ChatListType
import dev.kyralabs.pingogram.core.domain.model.ChatType
import dev.kyralabs.pingogram.core.domain.model.MessageContent
import dev.kyralabs.pingogram.core.domain.model.MessageSender
import dev.kyralabs.pingogram.core.domain.model.MutedWordSettings
import dev.kyralabs.pingogram.core.ui.R
import dev.kyralabs.pingogram.core.ui.text.UiText
import dev.kyralabs.pingogram.feature.chats.ui.ChatListFixtures.chat
import dev.kyralabs.pingogram.feature.chats.ui.ChatListFixtures.message
import dev.kyralabs.pingogram.feature.chats.ui.ChatListFixtures.text
import dev.kyralabs.pingogram.feature.chats.ui.ChatListFixtures.user
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Muted words in the list (`REQUIREMENTS.md` FR-X21): a group's last message with one says so in place of its text,
 * while the setting is on; a channel's never does.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatListMutedWordsTest : ChatListViewModelHarness() {
    private val spoiler = MessageContent.Text(text("Huge spoiler for the finale"), linkPreview = null)

    private fun lists() {
        users.setUsers(user(ADA, firstName = "Ada"))
        chats.setChats(
            ChatListType.Main,
            chat(
                id = GROUP,
                type = ChatType.BasicGroup(1),
                title = "Readers",
                lastMessage = message(GROUP, sender = MessageSender.User(ADA), content = spoiler),
            ),
            chat(
                id = CHANNEL,
                type = ChatType.Supergroup(2, isChannel = true),
                title = "News",
                lastMessage = message(CHANNEL, sender = MessageSender.Chat(CHANNEL), content = spoiler),
            ),
        )
    }

    @Test
    fun `a group's last message with a muted word says so, keeping its sender`() =
        runTest {
            mutedWords.settings.value = MutedWordSettings(enabled = true, words = persistentListOf("spoiler"))
            lists()

            val preview = viewModel().item(GROUP).preview as ChatPreview.Message

            assertEquals(UiText.resource(R.string.preview_muted_word), preview.text)
            assertEquals(null, preview.contentIcon)
        }

    @Test
    fun `a channel's post and everything while off show their text`() =
        runTest {
            mutedWords.settings.value = MutedWordSettings(enabled = true, words = persistentListOf("spoiler"))
            lists()
            val viewModel = viewModel()
            assertEquals(
                UiText.Plain("Huge spoiler for the finale"),
                (viewModel.item(CHANNEL).preview as ChatPreview.Message).text,
            )

            mutedWords.settings.value = MutedWordSettings(enabled = false, words = persistentListOf("spoiler"))
            runCurrent()

            assertEquals(
                UiText.Plain("Huge spoiler for the finale"),
                (viewModel.item(GROUP).preview as ChatPreview.Message).text,
            )
        }

    private companion object {
        const val GROUP = -100L
        const val CHANNEL = -1_000_000_000_200L
        const val ADA = 5L
    }
}
