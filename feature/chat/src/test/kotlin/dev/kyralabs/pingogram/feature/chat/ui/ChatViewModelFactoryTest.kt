package dev.kyralabs.pingogram.feature.chat.ui

import androidx.lifecycle.SavedStateHandle
import dev.kyralabs.pingogram.core.testing.FakeAppLanguageRepository
import dev.kyralabs.pingogram.core.testing.FakeComposerSettingsRepository
import dev.kyralabs.pingogram.core.testing.FakeCustomEmojiRepository
import dev.kyralabs.pingogram.core.testing.FakeDeviceLanguageDetector
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.GRACE
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.PRIVATE_CHAT
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.chat
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.history
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.message
import dev.kyralabs.pingogram.feature.chat.ui.bot.BotStartInbox
import dev.kyralabs.pingogram.feature.chat.ui.inline.InlineBotRepositories
import dev.kyralabs.pingogram.feature.chat.ui.inline.InlineLocationConsent
import dev.kyralabs.pingogram.feature.chat.ui.location.FakeDeviceLocationRepository
import dev.kyralabs.pingogram.feature.chat.ui.search.ChatSearchRepositories
import dev.kyralabs.pingogram.feature.chat.ui.search.FakeChatSearchRepository
import dev.kyralabs.pingogram.feature.chat.ui.search.FakeGroupInfoRepository
import dev.kyralabs.pingogram.feature.chat.ui.share.ShareInbox
import dev.kyralabs.pingogram.feature.chat.ui.translation.TranslationRepositories
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Where [ChatViewModel] gets its chat: from whoever creates it through [ChatViewModel.Factory], the conversation
 * destination with its route's chat id or the detail pane of the Chats tab with the chat it shows. The constructor
 * that Hilt's factory calls takes nothing else: its saved state holds no route.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelFactoryTest : ChatViewModelHarness() {
    @Test
    fun `the factory's chat is the one shown, opened and closed, without a route in the saved state`() =
        runTest {
            chats.setChats(chat(id = PRIVATE_CHAT), chat(id = GRACE))
            messages.setHistory(GRACE, history(message(1, chatId = GRACE)))

            val viewModel = created(GRACE)
            send(viewModel, ChatEvent.Started, ChatEvent.Stopped)

            assertEquals(GRACE, viewModel.state.header?.chatId)
            assertEquals(listOf(1L), viewModel.bubbles.map { it.id })
            assertEquals(listOf("open:$GRACE", "close:$GRACE"), chats.openCalls)
        }

    @Test
    fun `view models of two chats each show their own chat`() =
        runTest {
            chats.setChats(chat(id = PRIVATE_CHAT), chat(id = GRACE))
            messages.setHistory(PRIVATE_CHAT, history(message(1)))
            messages.setHistory(GRACE, history(message(2, chatId = GRACE)))

            val first = created(PRIVATE_CHAT)
            val second = created(GRACE)

            assertEquals(PRIVATE_CHAT, first.state.header?.chatId)
            assertEquals(listOf(1L), first.bubbles.map { it.id })
            assertEquals(GRACE, second.state.header?.chatId)
            assertEquals(listOf(2L), second.bubbles.map { it.id })
        }

    /** A view model made as Hilt makes it for [chatId], whose state is collected, as by the screen. */
    private fun TestScope.created(chatId: Long): ChatViewModel {
        val viewModel =
            ChatViewModel(
                chatId = chatId,
                savedStateHandle = SavedStateHandle(),
                chatRepository = chats,
                messages = messages,
                messageActionRepository = messageActions,
                sponsoredMessageRepository = sponsored,
                attachmentRepository = attachments,
                composerSettings = FakeComposerSettingsRepository(),
                userRepository = users,
                groupRepository = groups,
                chatActionRepository = actions,
                fileRepository = files,
                searchRepository = search,
                sharedStorageRepository = sharedStorage,
                clock = clock,
                timePatterns = FakeTimePatterns(),
                dispatcher = StandardTestDispatcher(testScheduler),
                audioPlayer = player,
                voiceRecorder = FakeVoiceRecorder(),
                voiceNoteRepository = FakeVoiceNoteRepository(),
                sendAsRepository = FakeSendAsRepository(),
                secretChatRepository = FakeSecretChatRepository(),
                autoDownloadPolicy = autoDownloads,
                chatJoinRepository = FakeChatJoinRepository(),
                threadRepositories =
                    ThreadRepositories(
                        FakePinnedMessageRepository(),
                        FakeHiddenPinsRepository(),
                        FakeForumTopicRepository(),
                        FakeMessageThreadRepository(),
                    ),
                shareInbox = ShareInbox(),
                botButtonRepository = FakeBotButtonRepository(),
                botInfoRepository = FakeBotInfoRepository(),
                botStartInbox = BotStartInbox(),
                inlineBotRepositories =
                    InlineBotRepositories(
                        FakeInlineBotRepository(),
                        FakeDeviceLocationRepository(),
                        InlineLocationConsent(),
                        BotStartInbox(),
                    ),
                translationRepositories =
                    TranslationRepositories(
                        FakeTranslationRepository(),
                        FakeDeviceLanguageDetector(),
                        FakeAppLanguageRepository(),
                        FakeHiddenTranslateBarsRepository(),
                    ),
                searchRepositories = ChatSearchRepositories(FakeChatSearchRepository(), FakeGroupInfoRepository()),
                customEmojiRepository = FakeCustomEmojiRepository(),
            )
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        runCurrent()
        return viewModel
    }
}
