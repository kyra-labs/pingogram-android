package dev.kyralabs.pingogram.feature.chat.ui

import androidx.compose.runtime.snapshots.Snapshot
import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.BotCommand
import dev.kyralabs.pingogram.core.domain.model.BotInfo
import dev.kyralabs.pingogram.core.domain.model.BotMenuButton
import dev.kyralabs.pingogram.core.domain.model.ChatBots
import dev.kyralabs.pingogram.core.domain.model.ChatHistory
import dev.kyralabs.pingogram.core.domain.model.SendOptions
import dev.kyralabs.pingogram.core.domain.model.UserType
import dev.kyralabs.pingogram.core.ui.text.UiText
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.BASIC_GROUP_ID
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.GROUP_CHAT
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.ME
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.basicGroup
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.chat
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.groupChat
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.history
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.message
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.text
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.user
import dev.kyralabs.pingogram.feature.chat.ui.bot.BotCommandItem
import dev.kyralabs.pingogram.feature.chat.ui.bot.BotIntro
import dev.kyralabs.pingogram.feature.chat.ui.bot.ChatBotCommands
import dev.kyralabs.pingogram.feature.chat.ui.bot.CommandBot
import dev.kyralabs.pingogram.feature.chat.ui.bot.ComposerBotButton
import dev.kyralabs.pingogram.feature.chat.ui.composer.ComposerBar
import dev.kyralabs.pingogram.feature.chat.ui.composer.ComposerUiState
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * The chat's bots in [ChatViewModel] (`REQUIREMENTS.md` FR-C5): the composer's bot button, the commands sheet, the
 * suggestions while a command is typed, sending and inserting commands, the bot's intro and "Start", also after a
 * start link.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatBotCommandsTest : ChatViewModelHarness() {
    private val botChat = chat(id = BOT, title = "Sticker Finder")

    private fun botInfo(
        menuButton: BotMenuButton? = null,
        description: String = "I find stickers.",
    ) = BotInfo(BOT, description, photo = null, animation = null, menuButton = menuButton)

    private val botCommands =
        persistentListOf(
            BotCommand("start", "Start the bot", BOT),
            BotCommand("stickers", "Find stickers", BOT),
            BotCommand("help", "What I can do", BOT),
        )

    /** The chat with the bot, its [history], the bot offering [chatBots]. */
    private fun TestScope.bot(
        history: ChatHistory = history(message(1, chatId = BOT)),
        chatBots: ChatBots = ChatBots(botCommands, botInfo()),
        handle: SavedStateHandle = savedState,
    ): ChatViewModel {
        users.setUsers(user(BOT, firstName = "Sticker Finder", type = UserType.Bot), user(ME))
        val hasMessages = history.messages.isNotEmpty()
        chats.setChats(if (hasMessages) botChat.copy(lastMessage = history.messages.first()) else botChat)
        messages.setHistory(BOT, history)
        bots.setBots(BOT, chatBots)
        return subscribed(viewModel(BOT, handle))
    }

    /** The view model with its commands sheet and suggestions collected, as the screen collects them. */
    private fun TestScope.subscribed(viewModel: ChatViewModel): ChatViewModel {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.botCommandsSheet.collect {} }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.commandSuggestions.collect {} }
        runCurrent()
        return viewModel
    }

    /** Replaces the composer's text, as typing does, and lets the view model see it. */
    private fun TestScope.type(
        viewModel: ChatViewModel,
        text: String,
    ) {
        viewModel.composerText.state.edit { replace(0, length, text) }
        Snapshot.sendApplyNotifications()
        runCurrent()
    }

    private val ChatViewModel.available get() = state.composer as ComposerUiState.Available

    private fun item(
        name: String,
        description: String,
    ) = BotCommandItem(name, "/$name", description)

    @Nested
    inner class Button {
        @Test
        fun `a bot with commands puts the commands button at the start of the composer`() =
            runViewModelTest {
                assertEquals(ComposerBotButton.Commands, bot().available.bot)
            }

        @Test
        fun `a bot's menu button takes the commands' place`() =
            runViewModelTest {
                val menu = BotMenuButton("Open app", "https://example.org/app")
                val viewModel = bot(chatBots = ChatBots(botCommands, botInfo(menuButton = menu)))

                assertEquals(ComposerBotButton.MiniApp("Open app"), viewModel.available.bot)
            }

        @Test
        fun `a chat whose bots offer nothing has none`() =
            runViewModelTest {
                assertNull(bot(chatBots = ChatBots(persistentListOf(), botInfo())).available.bot)
            }

        @Test
        fun `bots that cannot be loaded leave the composer as in any chat`() =
            runViewModelTest {
                users.setUsers(user(BOT, type = UserType.Bot))
                chats.setChats(botChat.copy(lastMessage = message(1, chatId = BOT)))
                messages.setHistory(BOT, history(message(1, chatId = BOT)))
                bots.setBots(BOT, Outcome.Failure(DomainError.Network))
                val viewModel = viewModel(BOT)

                assertNull(viewModel.available.bot)
                assertTrue(logged.any { it.startsWith("Bots not loaded") })
            }

        @Test
        fun `is not there while editing`() =
            runViewModelTest {
                val mine = message(1, chatId = BOT, isOutgoing = true)
                val viewModel = bot(history = history(mine))

                send(viewModel, ChatEvent.Edit(1))

                assertTrue(viewModel.available.bar is ComposerBar.Edit)
                assertNull(viewModel.available.bot)
            }

        @Test
        fun `follows the bot's commands as they change`() =
            runViewModelTest {
                val viewModel = bot(chatBots = ChatBots(persistentListOf(), botInfo()))

                bots.setBots(BOT, ChatBots(botCommands, botInfo()))
                runCurrent()

                assertEquals(ComposerBotButton.Commands, viewModel.available.bot)
            }
    }

    @Nested
    inner class Sheet {
        @Test
        fun `opens with the bot's commands`() =
            runViewModelTest {
                val viewModel = bot()

                send(viewModel, BotEvent.OpenMenu)

                assertEquals(
                    persistentListOf(
                        item("start", "Start the bot"),
                        item("stickers", "Find stickers"),
                        item("help", "What I can do"),
                    ),
                    viewModel.botCommandsSheet.value?.commands,
                )
            }

        @Test
        fun `a group's commands are addressed to their bots, with each bot's avatar`() =
            runViewModelTest {
                groups.basicGroupMap.value = mapOf(BASIC_GROUP_ID to basicGroup(3))
                users.setUsers(
                    user(ME),
                    user(BOT, firstName = "Sticker Finder", type = UserType.Bot)
                        .copy(usernames = persistentListOf("sticker_finder_bot")),
                    user(DICE_BOT, firstName = "Dice", type = UserType.Bot),
                )
                chats.setChats(groupChat)
                messages.setHistory(GROUP_CHAT, history(message(1, GROUP_CHAT)))
                bots.setBots(
                    GROUP_CHAT,
                    ChatBots(
                        persistentListOf(BotCommand("start", "Start", BOT), BotCommand("roll", "", DICE_BOT)),
                        null,
                    ),
                )
                val viewModel = subscribed(viewModel(GROUP_CHAT))

                send(viewModel, BotEvent.OpenMenu)

                assertEquals(
                    persistentListOf(
                        BotCommandItem(
                            "start",
                            "/start@sticker_finder_bot",
                            "Start",
                            CommandBot(BOT, UiText.Plain("Sticker Finder"), photo = null),
                        ),
                        // A bot without a username takes the command unaddressed.
                        BotCommandItem("roll", "/roll", "", CommandBot(DICE_BOT, UiText.Plain("Dice"), photo = null)),
                    ),
                    viewModel.botCommandsSheet.value?.commands,
                )
                assertEquals(ComposerBotButton.Commands, viewModel.available.bot)
            }

        @Test
        fun `a menu button says Mini Apps are not supported yet, and opens no sheet`() =
            runViewModelTest {
                val menu = BotMenuButton("Open app", "https://example.org/app")
                val viewModel = bot(chatBots = ChatBots(botCommands, botInfo(menuButton = menu)))

                viewModel.effects.test {
                    send(viewModel, BotEvent.OpenMenu)

                    assertEquals(ChatEffect.MiniAppsUnsupported, awaitItem())
                }
                assertNull(viewModel.botCommandsSheet.value)
            }

        @Test
        fun `dismissing it closes it`() =
            runViewModelTest {
                val viewModel = bot()

                send(viewModel, BotEvent.OpenMenu, BotEvent.DismissCommands)

                assertNull(viewModel.botCommandsSheet.value)
            }
    }

    @Nested
    inner class Sending {
        @Test
        fun `a command goes out as a message and closes the sheet`() =
            runViewModelTest {
                val viewModel = bot()

                send(viewModel, BotEvent.OpenMenu, BotEvent.SendCommand("/help"))

                assertEquals(listOf(Triple(BOT, text("/help"), SendOptions())), messages.sent)
                assertNull(viewModel.botCommandsSheet.value)
            }

        @Test
        fun `a command replies where the composer replies`() =
            runViewModelTest {
                val viewModel = bot()

                send(viewModel, ChatEvent.Reply(1), BotEvent.SendCommand("/help"))

                assertEquals(listOf<Long?>(1), messages.sentReplies)
                assertNull(viewModel.available.bar)
            }

        @Test
        fun `the command being typed goes with it, other text stays`() =
            runViewModelTest {
                val viewModel = bot()

                type(viewModel, "/he")
                send(viewModel, BotEvent.SendCommand("/help"))
                assertEquals("", viewModel.composerText.content.text)

                type(viewModel, "Thanks!")
                send(viewModel, BotEvent.SendCommand("/help"))
                assertEquals("Thanks!", viewModel.composerText.content.text)
            }

        @Test
        fun `a refused command gives the reply back, with a snackbar`() =
            runViewModelTest {
                messages.onSendText = { Outcome.Failure(DomainError.FloodWait(30)) }
                val viewModel = bot()

                viewModel.effects.test {
                    send(viewModel, ChatEvent.Reply(1))
                    send(viewModel, BotEvent.SendCommand("/help"))

                    assertEquals(ChatEffect.ShowError(DomainError.FloodWait(30)), expectMostRecentItem())
                }
                assertEquals(1L, viewModel.available.bar?.messageId)
            }

        @Test
        fun `inserting puts the command into the field with a space, and focuses it`() =
            runViewModelTest {
                val viewModel = bot()

                viewModel.effects.test {
                    send(viewModel, BotEvent.InsertCommand("/stickers"))

                    assertEquals(ChatEffect.FocusComposer, awaitItem())
                }
                assertEquals("/stickers ", viewModel.composerText.content.text)
                assertTrue(messages.sent.isEmpty())
            }

        @Test
        fun `inserting puts the command before what the field holds`() =
            runViewModelTest {
                val viewModel = bot()
                type(viewModel, "cats")

                send(viewModel, BotEvent.InsertCommand("/stickers"))

                assertEquals("/stickers cats", viewModel.composerText.content.text)
            }
    }

    @Nested
    inner class Suggestions {
        @Test
        fun `a command being typed suggests the commands it starts`() =
            runViewModelTest {
                val viewModel = bot()

                type(viewModel, "/st")

                val suggestions = viewModel.commandSuggestions.value
                assertEquals("st", suggestions?.query)
                assertEquals(listOf("/start", "/stickers"), suggestions?.commands?.map { it.text })
            }

        @Test
        fun `a slash alone suggests every command`() =
            runViewModelTest {
                val viewModel = bot()

                type(viewModel, "/")

                assertEquals(
                    3,
                    viewModel.commandSuggestions.value
                        ?.commands
                        ?.size,
                )
            }

        @Test
        fun `go once the arguments start, or nothing matches`() =
            runViewModelTest {
                val viewModel = bot()

                type(viewModel, "/stickers cats")
                assertNull(viewModel.commandSuggestions.value)

                type(viewModel, "/xyz")
                assertNull(viewModel.commandSuggestions.value)

                type(viewModel, "hello /st")
                assertNull(viewModel.commandSuggestions.value)
            }

        @Test
        fun `none while editing`() =
            runViewModelTest {
                val viewModel = bot(history = history(message(1, chatId = BOT, isOutgoing = true)))

                send(viewModel, ChatEvent.Edit(1))
                type(viewModel, "/st")

                assertNull(viewModel.commandSuggestions.value)
            }

        @Test
        fun `none in a chat without bots`() =
            runViewModelTest {
                val viewModel = bot(chatBots = ChatBots.None)

                type(viewModel, "/st")

                assertNull(viewModel.commandSuggestions.value)
            }
    }

    @Nested
    inner class Start {
        @Test
        fun `an empty bot chat shows Start and what the bot does`() =
            runViewModelTest {
                val viewModel = bot(history = ChatHistory(persistentListOf(), reachedStart = true))

                assertEquals(ComposerUiState.BotStart(), viewModel.state.composer)
                assertEquals(BotIntro("I find stickers.", photo = null, animation = null), viewModel.state.botIntro)
            }

        @Test
        fun `Start shows while the history loads in a chat without a last message`() =
            runViewModelTest {
                messages.onLoadNewest = loadsForever
                messages.onLoadOlder = loadsForever
                val viewModel = bot(history = ChatHistory(persistentListOf(), reachedStart = false))

                assertEquals(ChatContent.Loading, viewModel.state.content)
                assertEquals(ComposerUiState.BotStart(), viewModel.state.composer)
            }

        @Test
        fun `a bot chat with messages has the composer`() =
            runViewModelTest {
                assertTrue(bot().state.composer is ComposerUiState.Available)
            }

        @Test
        fun `a chat with a person has no Start`() =
            runViewModelTest {
                users.setUsers(user(ChatFixtures.ADA), user(ME))
                chats.setChats(chat())
                messages.setHistory(ChatFixtures.PRIVATE_CHAT, ChatHistory(persistentListOf(), reachedStart = true))
                val viewModel = viewModel()

                assertTrue(viewModel.state.composer is ComposerUiState.Available)
                assertNull(viewModel.state.botIntro)
            }

        @Test
        fun `Start starts the bot, busy while Telegram is asked`() =
            runViewModelTest {
                val answer = CompletableDeferred<Outcome<Unit>>()
                bots.onStart = { answer.await() }
                val viewModel = bot(history = ChatHistory(persistentListOf(), reachedStart = true))

                send(viewModel, BotEvent.Start, BotEvent.Start)
                assertEquals(ComposerUiState.BotStart(isStarting = true), viewModel.state.composer)

                answer.complete(Outcome.Success(Unit))
                runCurrent()

                assertEquals(listOf(Triple(BOT, BOT, "")), bots.started)
                assertEquals(ComposerUiState.BotStart(isStarting = false), viewModel.state.composer)
            }

        @Test
        fun `a refused start says why`() =
            runViewModelTest {
                bots.onStart = { Outcome.Failure(DomainError.Unauthorized) }
                val viewModel = bot(history = ChatHistory(persistentListOf(), reachedStart = true))

                viewModel.effects.test {
                    send(viewModel, BotEvent.Start)

                    assertEquals(ChatEffect.ShowError(DomainError.Unauthorized), awaitItem())
                }
                assertEquals(ComposerUiState.BotStart(), viewModel.state.composer)
            }

        @Test
        fun `a start link shows Start in a chat with messages and sends its parameter, once`() =
            runViewModelTest {
                botStarts.deliver(BOT, "ref42")
                val viewModel = bot()

                assertEquals(ComposerUiState.BotStart(), viewModel.state.composer)
                send(viewModel, BotEvent.Start)

                assertEquals(listOf(Triple(BOT, BOT, "ref42")), bots.started)
                assertTrue(viewModel.state.composer is ComposerUiState.Available)
            }

        @Test
        fun `a start link reaches the conversation already open`() =
            runViewModelTest {
                val viewModel = bot()
                assertTrue(viewModel.state.composer is ComposerUiState.Available)

                botStarts.deliver(BOT, "ref42")
                runCurrent()

                assertEquals(ComposerUiState.BotStart(), viewModel.state.composer)
            }

        @Test
        fun `a start link's parameter survives process death, and is taken only once`() =
            runViewModelTest {
                botStarts.deliver(BOT, "ref42")
                val before = bot()
                clear(before)

                val restored = bot(handle = SavedStateHandle(savedState.keys().associateWith { savedState[it] }))
                assertEquals(ComposerUiState.BotStart(), restored.state.composer)
                assertEquals("ref42", savedState[ChatBotCommands.KEY_START_PARAMETER])

                val other = bot(handle = SavedStateHandle())
                assertFalse(other.state.composer is ComposerUiState.BotStart)
            }
    }

    private companion object {
        const val BOT = 500L
        const val DICE_BOT = 501L
    }
}
