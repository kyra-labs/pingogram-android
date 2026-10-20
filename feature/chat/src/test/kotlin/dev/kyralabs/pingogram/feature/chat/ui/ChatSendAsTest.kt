package dev.kyralabs.pingogram.feature.chat.ui

import app.cash.turbine.test
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.AvailableSender
import dev.kyralabs.pingogram.core.domain.model.ChatType
import dev.kyralabs.pingogram.core.domain.model.MessageSender
import dev.kyralabs.pingogram.core.ui.text.UiText
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.BASIC_GROUP_ID
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.GROUP_CHAT
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.ME
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.basicGroup
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.chat
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.groupChat
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.history
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.message
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.user
import dev.kyralabs.pingogram.feature.chat.ui.composer.ComposerBar
import dev.kyralabs.pingogram.feature.chat.ui.composer.ComposerUiState
import dev.kyralabs.pingogram.feature.chat.ui.sendas.SendAsChoice
import dev.kyralabs.pingogram.feature.chat.ui.sendas.SendAsOption
import dev.kyralabs.pingogram.feature.chat.ui.sendas.SendAsSheet
import dev.kyralabs.pingogram.feature.chat.ui.sendas.SenderKind
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * Send-as in [ChatViewModel] (`REQUIREMENTS.md` FR-C5): the composer's avatar of who new messages go out as, and the
 * picker that lists who the user can send as and changes it.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatSendAsTest : ChatViewModelHarness() {
    private val channel =
        chat(id = CHANNEL, type = ChatType.Supergroup(CHANNEL_SUPERGROUP, isChannel = true), title = "Engine news")
    private val premiumChannel =
        chat(id = PREMIUM_CHANNEL, type = ChatType.Supergroup(PREMIUM_SUPERGROUP, isChannel = true), title = "Lab")

    /** A group the user may write in, sending as [sender], with the user and their channels known. */
    private fun TestScope.group(sender: MessageSender? = MessageSender.User(ME)): ChatViewModel {
        groups.basicGroupMap.value = mapOf(BASIC_GROUP_ID to basicGroup(3))
        users.setUsers(user(ME, firstName = "Ada", lastName = "Lovelace"))
        val group = groupChat.copy(messageSender = sender)
        chats.setChats(group, channel, premiumChannel)
        messages.setHistory(GROUP_CHAT, history(message(1, GROUP_CHAT)))
        return viewModel(GROUP_CHAT)
    }

    private val ChatViewModel.composer get() = state.composer as ComposerUiState.Available

    private val ChatViewModel.sheet get() = sendAsSheet.value

    private val available: ImmutableList<AvailableSender> =
        persistentListOf(
            AvailableSender(MessageSender.User(ME), needsPremium = false),
            AvailableSender(MessageSender.Chat(CHANNEL), needsPremium = false),
            AvailableSender(MessageSender.Chat(PREMIUM_CHANNEL), needsPremium = true),
            AvailableSender(MessageSender.Chat(GROUP_CHAT), needsPremium = false),
        )

    @Nested
    inner class Button {
        @Test
        fun `shows who new messages go out as`() =
            runViewModelTest {
                val viewModel = group(MessageSender.Chat(CHANNEL))

                assertEquals(
                    SendAsChoice(CHANNEL, UiText.Plain("Engine news"), photo = null),
                    viewModel.composer.sendAs,
                )
            }

        @Test
        fun `shows the user by their full name`() =
            runViewModelTest {
                assertEquals(SendAsChoice(ME, UiText.Plain("Ada Lovelace"), photo = null), group().composer.sendAs)
            }

        @Test
        fun `is not there where the user cannot choose`() =
            runViewModelTest {
                assertNull(group(sender = null).composer.sendAs)
            }

        @Test
        fun `follows the chat's sender`() =
            runViewModelTest {
                val viewModel = group()

                chats.setChats(groupChat.copy(messageSender = MessageSender.Chat(CHANNEL)), channel, premiumChannel)
                runCurrent()

                assertEquals(CHANNEL, viewModel.composer.sendAs?.id)
            }

        @Test
        fun `is not there while editing, as an edit keeps its sender`() =
            runViewModelTest {
                groups.basicGroupMap.value = mapOf(BASIC_GROUP_ID to basicGroup(3))
                users.setUsers(user(ME))
                chats.setChats(groupChat.copy(messageSender = MessageSender.User(ME)))
                val mine = message(1, GROUP_CHAT, sender = MessageSender.User(ME), isOutgoing = true)
                messages.setHistory(GROUP_CHAT, history(mine))
                val viewModel = viewModel(GROUP_CHAT)

                send(viewModel, ChatEvent.Edit(1))

                assertTrue(viewModel.composer.bar is ComposerBar.Edit)
                assertNull(viewModel.composer.sendAs)
            }
    }

    @Nested
    inner class Picker {
        @Test
        fun `opens at once and lists every sender, the current one chosen`() =
            runViewModelTest {
                val answer = CompletableDeferred<Outcome<ImmutableList<AvailableSender>>>()
                sendAs.onAvailableSenders = { answer.await() }
                val viewModel = group(MessageSender.Chat(CHANNEL))

                send(viewModel, SendAsEvent.Open)
                assertEquals(SendAsSheet(), viewModel.sheet)

                answer.complete(Outcome.Success(available))
                runCurrent()

                assertEquals(
                    persistentListOf(
                        SendAsOption(
                            MessageSender.User(ME),
                            ME,
                            UiText.Plain("Ada Lovelace"),
                            null,
                            SenderKind.Account,
                            selected = false,
                            needsPremium = false,
                        ),
                        SendAsOption(
                            MessageSender.Chat(CHANNEL),
                            CHANNEL,
                            UiText.Plain("Engine news"),
                            null,
                            SenderKind.Channel,
                            selected = true,
                            needsPremium = false,
                        ),
                        SendAsOption(
                            MessageSender.Chat(PREMIUM_CHANNEL),
                            PREMIUM_CHANNEL,
                            UiText.Plain("Lab"),
                            null,
                            SenderKind.Channel,
                            selected = false,
                            needsPremium = true,
                        ),
                        SendAsOption(
                            MessageSender.Chat(GROUP_CHAT),
                            GROUP_CHAT,
                            UiText.Plain("Group"),
                            null,
                            SenderKind.Anonymous,
                            selected = false,
                            needsPremium = false,
                        ),
                    ),
                    viewModel.sheet?.options,
                )
            }

        @Test
        fun `a failure to list the senders closes it with a snackbar`() =
            runViewModelTest {
                sendAs.onAvailableSenders = { Outcome.Failure(DomainError.Network) }
                val viewModel = group()

                viewModel.effects.test {
                    send(viewModel, SendAsEvent.Open)

                    assertEquals(ChatEffect.ShowError(DomainError.Network), awaitItem())
                }
                assertNull(viewModel.sheet)
            }

        @Test
        fun `choosing a sender sets it and closes the picker once TDLib took it`() =
            runViewModelTest {
                sendAs.onAvailableSenders = { Outcome.Success(available) }
                val answer = CompletableDeferred<Outcome<Unit>>()
                sendAs.onSetSender = { answer.await() }
                val viewModel = group()
                send(viewModel, SendAsEvent.Open)

                send(viewModel, SendAsEvent.Choose(MessageSender.Chat(CHANNEL)))
                assertEquals(true, viewModel.sheet?.isChanging)
                assertEquals(
                    CHANNEL,
                    viewModel.sheet
                        ?.options
                        ?.single { it.selected }
                        ?.id,
                )

                answer.complete(Outcome.Success(Unit))
                runCurrent()

                assertEquals(listOf(GROUP_CHAT to MessageSender.Chat(CHANNEL)), sendAs.chosen)
                assertNull(viewModel.sheet)
            }

        @Test
        fun `a failure to set the sender keeps the picker with a snackbar`() =
            runViewModelTest {
                sendAs.onAvailableSenders = { Outcome.Success(available) }
                sendAs.onSetSender = { Outcome.Failure(DomainError.FloodWait(30)) }
                val viewModel = group()
                send(viewModel, SendAsEvent.Open)

                viewModel.effects.test {
                    send(viewModel, SendAsEvent.Choose(MessageSender.Chat(GROUP_CHAT)))

                    assertEquals(ChatEffect.ShowError(DomainError.FloodWait(30)), awaitItem())
                }
                assertEquals(false, viewModel.sheet?.isChanging)
                assertEquals(
                    ME,
                    viewModel.sheet
                        ?.options
                        ?.single { it.selected }
                        ?.id,
                )
            }

        @Test
        fun `a sender that needs Premium cannot be chosen`() =
            runViewModelTest {
                sendAs.onAvailableSenders = { Outcome.Success(available) }
                val viewModel = group()
                send(viewModel, SendAsEvent.Open)

                send(viewModel, SendAsEvent.Choose(MessageSender.Chat(PREMIUM_CHANNEL)))

                assertTrue(sendAs.chosen.isEmpty())
                assertEquals(false, viewModel.sheet?.isChanging)
            }

        @Test
        fun `choosing the current sender just closes the picker`() =
            runViewModelTest {
                sendAs.onAvailableSenders = { Outcome.Success(available) }
                val viewModel = group()
                send(viewModel, SendAsEvent.Open)

                send(viewModel, SendAsEvent.Choose(MessageSender.User(ME)))

                assertTrue(sendAs.chosen.isEmpty())
                assertNull(viewModel.sheet)
            }

        @Test
        fun `dismissing closes it without a change`() =
            runViewModelTest {
                sendAs.onAvailableSenders = { CompletableDeferred<Outcome<ImmutableList<AvailableSender>>>().await() }
                val viewModel = group()
                send(viewModel, SendAsEvent.Open)

                send(viewModel, SendAsEvent.Dismiss)

                assertNull(viewModel.sheet)
                assertTrue(sendAs.chosen.isEmpty())
            }
    }

    private companion object {
        const val CHANNEL = -1_000_000_000_300L
        const val CHANNEL_SUPERGROUP = 300L
        const val PREMIUM_CHANNEL = -1_000_000_000_400L
        const val PREMIUM_SUPERGROUP = 400L
    }
}
