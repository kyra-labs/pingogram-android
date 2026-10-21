package dev.kyralabs.pingogram.feature.chat.ui

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.ChatType
import dev.kyralabs.pingogram.core.domain.model.EncryptionKeyHash
import dev.kyralabs.pingogram.core.domain.model.SecretChat
import dev.kyralabs.pingogram.core.domain.model.SecretChatState
import dev.kyralabs.pingogram.core.domain.model.SelfDestruct
import dev.kyralabs.pingogram.core.ui.text.UiText
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.ADA
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.PRIVATE_CHAT
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.chat
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.history
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.message
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.user
import dev.kyralabs.pingogram.feature.chat.ui.composer.ComposerUiState
import dev.kyralabs.pingogram.feature.chat.ui.secret.SELF_DESTRUCT_CHOICES
import dev.kyralabs.pingogram.feature.chat.ui.secret.SecretChatHeader
import dev.kyralabs.pingogram.feature.chat.ui.secret.SecretChatSheet
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import dev.kyralabs.pingogram.core.ui.R as UiR
import dev.kyralabs.pingogram.feature.chat.R as ChatR

/**
 * Secret chats in [ChatViewModel] (`REQUIREMENTS.md` FR-E1): the header's lock, state and timer, the composer while
 * the chat waits or ended, the self-destruct timer in bubbles, the timer sheet and the encryption key sheet.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatSecretChatTest : ChatViewModelHarness() {
    private val keyHash = EncryptionKeyHash(ByteArray(36) { it.toByte() })

    private fun secretChat(
        state: SecretChatState = SecretChatState.Ready,
        isOutbound: Boolean = true,
        keyHash: EncryptionKeyHash = this.keyHash,
    ) = SecretChat(SECRET_CHAT_ID, ADA, state, isOutbound, keyHash, layer = 144)

    private val secretChatOfAda =
        chat(id = SECRET_CHAT, type = ChatType.Secret(SECRET_CHAT_ID, userId = ADA), title = "Ada Lovelace")

    /** The secret chat with Ada in [state] and a timer of [timer] seconds, opened, its sheets collected. */
    private fun TestScope.secret(
        state: SecretChatState = SecretChatState.Ready,
        timer: Int = 0,
        handle: SavedStateHandle = savedState,
    ): ChatViewModel {
        users.setUsers(user(ADA, firstName = "Ada", lastName = "Lovelace"))
        secretChats.setSecretChats(secretChat(state))
        chats.setChats(secretChatOfAda.copy(messageAutoDeleteTime = timer))
        messages.setHistory(SECRET_CHAT, history(message(1, SECRET_CHAT)))
        val viewModel = viewModel(SECRET_CHAT, handle)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.secretChatSheet.collect {} }
        runCurrent()
        return viewModel
    }

    private val ChatViewModel.sheet get() = secretChatSheet.value

    @Nested
    inner class Header {
        @Test
        fun `a ready secret chat has its lock and timer, and the user's status`() =
            runViewModelTest {
                val viewModel = secret(timer = 30)

                assertEquals(
                    SecretChatHeader(SecretChatState.Ready, selfDestructSeconds = 30),
                    viewModel.state.header?.secret,
                )
                assertEquals(
                    ChatSubtitle.Status(res(ChatR.string.chat_subtitle_last_seen_long_ago)),
                    viewModel.subtitle,
                )
            }

        @Test
        fun `a pending secret chat says it waits for the other user, by name`() =
            runViewModelTest {
                val viewModel = secret(SecretChatState.Pending)

                assertEquals(
                    ChatSubtitle.Status(res(UiR.string.secret_chat_waiting, UiText.Plain("⁨Ada⁩"))),
                    viewModel.subtitle,
                )
                assertFalse(
                    viewModel.state.header
                        ?.secret
                        ?.canSetTimer == true,
                )
            }

        @Test
        fun `an ended secret chat says so`() =
            runViewModelTest {
                assertEquals(
                    ChatSubtitle.Status(res(UiR.string.secret_chat_ended)),
                    secret(SecretChatState.Closed).subtitle,
                )
            }

        @Test
        fun `follows the secret chat from pending to ready`() =
            runViewModelTest {
                val viewModel = secret(SecretChatState.Pending)

                secretChats.setSecretChats(secretChat(SecretChatState.Ready))
                runCurrent()

                assertEquals(
                    SecretChatState.Ready,
                    viewModel.state.header
                        ?.secret
                        ?.state,
                )
                assertInstanceOf(ComposerUiState.Available::class.java, viewModel.state.composer)
            }

        @Test
        fun `other chats have no secret header, so the window stays as it is`() =
            runViewModelTest {
                chats.setChats(chat())
                messages.setHistory(PRIVATE_CHAT, history(message(1)))

                assertNull(viewModel(PRIVATE_CHAT).state.header?.secret)
            }
    }

    @Nested
    inner class Composer {
        @Test
        fun `a ready secret chat can be written to, without scheduling`() =
            runViewModelTest {
                assertEquals(ComposerUiState.Available::class, secret().state.composer::class)
                assertFalse((secret().state.composer as ComposerUiState.Available).canSchedule)
            }

        @Test
        fun `a pending secret chat says why there is no composer`() =
            runViewModelTest {
                assertEquals(
                    ComposerUiState.Unavailable(res(ChatR.string.composer_secret_pending)),
                    secret(SecretChatState.Pending).state.composer,
                )
            }

        @Test
        fun `an ended secret chat says why there is no composer`() =
            runViewModelTest {
                assertEquals(
                    ComposerUiState.Unavailable(res(ChatR.string.composer_secret_closed)),
                    secret(SecretChatState.Closed).state.composer,
                )
            }

        @Test
        fun `a secret chat TDLib has not announced shows no composer`() =
            runViewModelTest {
                chats.setChats(secretChatOfAda)
                messages.setHistory(SECRET_CHAT, history(message(1, SECRET_CHAT)))

                assertEquals(ComposerUiState.Hidden, viewModel(SECRET_CHAT).state.composer)
            }

        @Test
        fun `a secret chat TDLib has not announced still has its lock, and no timer yet`() =
            runViewModelTest {
                chats.setChats(secretChatOfAda)
                messages.setHistory(SECRET_CHAT, history(message(1, SECRET_CHAT)))

                val secret = viewModel(SECRET_CHAT).state.header?.secret

                assertEquals(SecretChatHeader(SecretChatState.Pending, selfDestructSeconds = 0), secret)
                assertFalse(secret?.canSetTimer == true)
            }
    }

    @Test
    fun `a message with a self-destruct timer shows it`() =
        runViewModelTest {
            users.setUsers(user(ADA))
            secretChats.setSecretChats(secretChat())
            chats.setChats(secretChatOfAda)
            messages.setHistory(
                SECRET_CHAT,
                history(message(2, SECRET_CHAT).copy(selfDestruct = SelfDestruct.Timer(30)), message(1, SECRET_CHAT)),
            )

            val viewModel = viewModel(SECRET_CHAT)

            assertEquals(30, viewModel.bubble(2).selfDestructSeconds)
            assertNull(viewModel.bubble(1).selfDestructSeconds)
        }

    @Nested
    inner class Timer {
        @Test
        fun `opens with the chat's timer chosen`() =
            runViewModelTest {
                val viewModel = secret(timer = 30)

                send(viewModel, SecretChatEvent.OpenTimer)

                val sheet = viewModel.sheet as SecretChatSheet.Timer
                assertEquals(SELF_DESTRUCT_CHOICES, sheet.options.map { it.seconds })
                assertEquals(listOf(30), sheet.options.filter { it.selected }.map { it.seconds })
                assertFalse(sheet.isChanging)
            }

        @Test
        fun `a timer the other side set is among the choices`() =
            runViewModelTest {
                val viewModel = secret(timer = 7)

                send(viewModel, SecretChatEvent.OpenTimer)

                val options = (viewModel.sheet as SecretChatSheet.Timer).options
                assertEquals(listOf(5, 7, 10), options.map { it.seconds }.filter { it in 5..10 })
                assertTrue(options.single { it.seconds == 7 }.selected)
            }

        @Test
        fun `does not open before the chat is ready`() =
            runViewModelTest {
                val viewModel = secret(SecretChatState.Pending)

                send(viewModel, SecretChatEvent.OpenTimer)

                assertNull(viewModel.sheet)
            }

        @Test
        fun `choosing a timer shows it chosen, sets it and closes once TDLib set it`() =
            runViewModelTest {
                val answer = CompletableDeferred<Outcome<Unit>>()
                secretChats.onSetSelfDestructTime = { answer.await() }
                val viewModel = secret()
                send(viewModel, SecretChatEvent.OpenTimer)

                send(viewModel, SecretChatEvent.ChooseTimer(60), SecretChatEvent.ChooseTimer(5))

                val changing = viewModel.sheet as SecretChatSheet.Timer
                assertTrue(changing.isChanging)
                assertEquals(listOf(60), changing.options.filter { it.selected }.map { it.seconds })
                assertEquals(listOf(SECRET_CHAT to 60), secretChats.timersSet)

                answer.complete(Outcome.Success(Unit))
                runCurrent()

                assertNull(viewModel.sheet)
            }

        @Test
        fun `choosing the chat's own timer just closes the sheet`() =
            runViewModelTest {
                val viewModel = secret(timer = 30)
                send(viewModel, SecretChatEvent.OpenTimer)

                send(viewModel, SecretChatEvent.ChooseTimer(30))

                assertNull(viewModel.sheet)
                assertTrue(secretChats.timersSet.isEmpty())
            }

        @Test
        fun `a timer TDLib refuses comes back with a snackbar`() =
            runViewModelTest {
                secretChats.onSetSelfDestructTime = { Outcome.Failure(DomainError.FloodWait(8)) }
                val viewModel = secret(timer = 30)
                send(viewModel, SecretChatEvent.OpenTimer)

                viewModel.effects.test {
                    send(viewModel, SecretChatEvent.ChooseTimer(60))

                    assertEquals(ChatEffect.ShowError(DomainError.FloodWait(8)), awaitItem())
                }
                val sheet = viewModel.sheet as SecretChatSheet.Timer
                assertFalse(sheet.isChanging)
                assertEquals(listOf(30), sheet.options.filter { it.selected }.map { it.seconds })
            }

        @Test
        fun `closes when the chat ends while it is open`() =
            runViewModelTest {
                val viewModel = secret()
                send(viewModel, SecretChatEvent.OpenTimer)

                secretChats.setSecretChats(secretChat(SecretChatState.Closed))
                runCurrent()

                assertNull(viewModel.sheet)
            }

        @Test
        fun `dismissing closes it`() =
            runViewModelTest {
                val viewModel = secret()
                send(viewModel, SecretChatEvent.OpenTimer, SecretChatEvent.Dismiss)

                assertNull(viewModel.sheet)
            }
    }

    @Nested
    inner class Key {
        @Test
        fun `shows the key hash and the other user's name`() =
            runViewModelTest {
                val viewModel = secret()

                send(viewModel, SecretChatEvent.OpenKey)

                assertEquals(
                    SecretChatSheet.Key(name = UiText.Plain("Ada"), keyHash = keyHash, canEnd = true),
                    viewModel.sheet,
                )
            }

        @Test
        fun `shows the key once it is agreed`() =
            runViewModelTest {
                users.setUsers(user(ADA, firstName = "Ada"))
                secretChats.setSecretChats(secretChat(SecretChatState.Pending, keyHash = EncryptionKeyHash.Empty))
                chats.setChats(secretChatOfAda)
                messages.setHistory(SECRET_CHAT, history(message(1, SECRET_CHAT)))
                val viewModel = viewModel(SECRET_CHAT)
                backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.secretChatSheet.collect {} }
                send(viewModel, SecretChatEvent.OpenKey)
                assertTrue((viewModel.sheet as SecretChatSheet.Key).keyHash.isEmpty)

                secretChats.setSecretChats(secretChat(SecretChatState.Ready))
                runCurrent()

                assertEquals(keyHash, (viewModel.sheet as SecretChatSheet.Key).keyHash)
            }

        @Test
        fun `ending the chat asks first, then closes it for both sides`() =
            runViewModelTest {
                val viewModel = secret()
                send(viewModel, SecretChatEvent.OpenKey, SecretChatEvent.EndChat)

                assertTrue((viewModel.sheet as SecretChatSheet.Key).confirmingEnd)
                assertTrue(secretChats.closed.isEmpty())

                send(viewModel, SecretChatEvent.ConfirmEnd)

                assertEquals(listOf(SECRET_CHAT_ID), secretChats.closed)
                assertNull(viewModel.sheet)
            }

        @Test
        fun `dismissing the confirmation keeps the sheet and the chat`() =
            runViewModelTest {
                val viewModel = secret()
                send(viewModel, SecretChatEvent.OpenKey, SecretChatEvent.EndChat, SecretChatEvent.DismissEnd)

                assertFalse((viewModel.sheet as SecretChatSheet.Key).confirmingEnd)
                assertTrue(secretChats.closed.isEmpty())
            }

        @Test
        fun `a failure to end the chat is a snackbar`() =
            runViewModelTest {
                secretChats.onCloseSecretChat = { Outcome.Failure(DomainError.Network) }
                val viewModel = secret()
                send(viewModel, SecretChatEvent.OpenKey, SecretChatEvent.EndChat)

                viewModel.effects.test {
                    send(viewModel, SecretChatEvent.ConfirmEnd)

                    assertEquals(ChatEffect.ShowError(DomainError.Network), awaitItem())
                }
            }

        @Test
        fun `an ended chat cannot be ended again`() =
            runViewModelTest {
                val viewModel = secret(SecretChatState.Closed)

                send(viewModel, SecretChatEvent.OpenKey)

                assertFalse((viewModel.sheet as SecretChatSheet.Key).canEnd)
            }

        @Test
        fun `is not kept across process death, which reopens the conversation without it`() =
            runViewModelTest {
                val handle = SavedStateHandle()
                secret(handle = handle).onEvent(SecretChatEvent.OpenKey)
                runCurrent()

                assertNull(secret(handle = handle).sheet)
            }
    }

    private companion object {
        const val SECRET_CHAT = 900L
        const val SECRET_CHAT_ID = 7
    }
}
