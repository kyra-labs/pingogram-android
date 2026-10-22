package dev.kyralabs.pingogram.core.data.repository

import app.cash.turbine.test
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.BotCommand
import dev.kyralabs.pingogram.core.domain.model.BotMenuButton
import dev.kyralabs.pingogram.core.domain.model.ChatBots
import dev.kyralabs.pingogram.core.tdlib.TdError
import dev.kyralabs.pingogram.core.tdlib.TdResult
import dev.kyralabs.pingogram.core.testing.FakeTdlibClient
import dev.kyralabs.pingogram.core.testing.TdApiFixtures.chat
import dev.kyralabs.pingogram.core.testing.TdApiFixtures.photoSize
import dev.kyralabs.pingogram.core.testing.TdApiFixtures.user
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.test.runTest
import org.drinkless.tdlib.TdApi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * [TdlibBotInfoRepository] on a [FakeTdlibClient]: a chat's bots from the full info of its bot or group, their changes,
 * the chats without bots, and starting a bot.
 */
class TdlibBotInfoRepositoryTest {
    private val client = FakeTdlibClient()
    private val repository = TdlibBotInfoRepository(client)

    private fun command(
        name: String,
        description: String = "Does $name",
    ) = TdApi.BotCommand(name, description, false)

    private fun botInfo(
        description: String = "I find stickers.",
        commands: Array<TdApi.BotCommand> = arrayOf(command("start"), command("help")),
        menuButton: TdApi.BotMenuButton? = null,
        photo: TdApi.Photo? = null,
    ) = TdApi.BotInfo().also {
        it.description = description
        it.commands = commands
        it.menuButton = menuButton
        it.photo = photo
    }

    private fun fullInfo(botInfo: TdApi.BotInfo?) = TdApi.UserFullInfo().also { it.botInfo = botInfo }

    private fun botChat() {
        client.on(TdApi.GetChat::class, TdResult.Success(chat(id = BOT, type = TdApi.ChatTypePrivate(BOT))))
        client.on(TdApi.GetUser::class, TdResult.Success(user(id = BOT, type = TdApi.UserTypeBot())))
    }

    private fun group(type: TdApi.ChatType) =
        client.on(TdApi.GetChat::class, TdResult.Success(chat(id = GROUP, type = type)))

    @Test
    fun `a bot's chat has its commands, description, photo and menu button`() =
        runTest {
            botChat()
            val photo = TdApi.Photo(false, null, arrayOf(photoSize("x", 640, 360)))
            val menu = TdApi.BotMenuButton("Open app", "https://example.org/app")
            client.on(
                TdApi.GetUserFullInfo::class,
                TdResult.Success(fullInfo(botInfo(menuButton = menu, photo = photo))),
            )

            repository.chatBots(BOT).test {
                val bots = (awaitItem() as Outcome.Success).value
                assertEquals(
                    persistentListOf(BotCommand("start", "Does start", BOT), BotCommand("help", "Does help", BOT)),
                    bots.commands,
                )
                val bot = bots.bot!!
                assertEquals(BOT, bot.botUserId)
                assertEquals("I find stickers.", bot.description)
                assertEquals(
                    640,
                    bot.photo
                        ?.sizes
                        ?.single()
                        ?.width,
                )
                assertNull(bot.animation)
                assertEquals(BotMenuButton("Open app", "https://example.org/app"), bot.menuButton)
                cancelAndIgnoreRemainingEvents()
            }
            assertEquals(BOT, client.sentOf<TdApi.GetUserFullInfo>().single().userId)
        }

    @Test
    fun `a menu button without an address is the commands' menu`() =
        runTest {
            botChat()
            val menu = TdApi.BotMenuButton("Menu", "")
            client.on(TdApi.GetUserFullInfo::class, TdResult.Success(fullInfo(botInfo(menuButton = menu))))

            repository.chatBots(BOT).test {
                assertNull((awaitItem() as Outcome.Success).value.bot?.menuButton)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `commands edited with BotFather follow, other users' changes are left out`() =
        runTest {
            botChat()
            client.on(TdApi.GetUserFullInfo::class, TdResult.Success(fullInfo(botInfo())))

            repository.chatBots(BOT).test {
                awaitItem()
                client.emit(TdApi.UpdateUserFullInfo(OTHER_BOT, fullInfo(botInfo(commands = arrayOf()))))
                client.emit(TdApi.UpdateUserFullInfo(BOT, fullInfo(botInfo(commands = arrayOf(command("new"))))))

                val changed = (awaitItem() as Outcome.Success).value
                assertEquals(persistentListOf(BotCommand("new", "Does new", BOT)), changed.commands)
                expectNoEvents()
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `a person's chat has no bots, and asks for no full info`() =
        runTest {
            client.on(TdApi.GetChat::class, TdResult.Success(chat(id = PERSON, type = TdApi.ChatTypePrivate(PERSON))))
            client.on(TdApi.GetUser::class, TdResult.Success(user(id = PERSON)))

            repository.chatBots(PERSON).test {
                assertEquals(Outcome.Success(ChatBots.None), awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
            assertTrue(client.sentOf<TdApi.GetUserFullInfo>().isEmpty())
        }

    @Test
    fun `a basic group has the commands of each of its bots`() =
        runTest {
            group(TdApi.ChatTypeBasicGroup(BASIC_GROUP))
            val info =
                TdApi.BasicGroupFullInfo().also {
                    it.botCommands =
                        arrayOf(
                            TdApi.BotCommands(BOT, arrayOf(command("roll"))),
                            TdApi.BotCommands(OTHER_BOT, arrayOf(command("poll"), command(" "))),
                        )
                }
            client.on(TdApi.GetBasicGroupFullInfo::class, TdResult.Success(info))

            repository.chatBots(GROUP).test {
                assertEquals(
                    persistentListOf(BotCommand("roll", "Does roll", BOT), BotCommand("poll", "Does poll", OTHER_BOT)),
                    (awaitItem() as Outcome.Success).value.commands,
                )
                cancelAndIgnoreRemainingEvents()
            }
            assertEquals(BASIC_GROUP, client.sentOf<TdApi.GetBasicGroupFullInfo>().single().basicGroupId)
        }

    @Test
    fun `a supergroup follows its bots, as one is added`() =
        runTest {
            group(TdApi.ChatTypeSupergroup(SUPERGROUP, false))
            client.on(TdApi.GetSupergroupFullInfo::class, TdResult.Success(TdApi.SupergroupFullInfo()))

            repository.chatBots(GROUP).test {
                assertEquals(Outcome.Success(ChatBots.None), awaitItem())
                val added =
                    TdApi.SupergroupFullInfo().also {
                        it.botCommands =
                            arrayOf(TdApi.BotCommands(BOT, arrayOf(command("roll"))))
                    }
                client.emit(TdApi.UpdateSupergroupFullInfo(SUPERGROUP + 1, added))
                client.emit(TdApi.UpdateSupergroupFullInfo(SUPERGROUP, added))

                assertEquals(
                    persistentListOf(BotCommand("roll", "Does roll", BOT)),
                    (awaitItem() as Outcome.Success).value.commands,
                )
                expectNoEvents()
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `a channel asks for nothing more`() =
        runTest {
            group(TdApi.ChatTypeSupergroup(SUPERGROUP, true))

            repository.chatBots(GROUP).test {
                assertEquals(Outcome.Success(ChatBots.None), awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
            assertTrue(client.sentOf<TdApi.GetSupergroupFullInfo>().isEmpty())
        }

    @Test
    fun `a failed request is the first value`() =
        runTest {
            botChat()
            client.on(TdApi.GetUserFullInfo::class, TdResult.Failure(TdError(429, "Too Many Requests: retry after 8")))

            repository.chatBots(BOT).test {
                assertEquals(Outcome.Failure(DomainError.FloodWait(8)), awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `a chat TDLib does not know fails`() =
        runTest {
            client.on(TdApi.GetChat::class, TdResult.Failure(TdError(400, "Chat not found")))

            repository.chatBots(BOT).test {
                assertTrue((awaitItem() as Outcome.Failure).error is DomainError)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `starting a bot sends its start message with the parameter`() =
        runTest {
            client.on(TdApi.SendBotStartMessage::class, TdResult.Success(TdApi.Message()))

            assertEquals(Outcome.Success(Unit), repository.sendStartMessage(BOT, BOT, "ref42"))

            val sent = client.sentOf<TdApi.SendBotStartMessage>().single()
            assertEquals(BOT, sent.botUserId)
            assertEquals(BOT, sent.chatId)
            assertEquals("ref42", sent.parameter)
        }

    @Test
    fun `a refused start fails`() =
        runTest {
            client.on(TdApi.SendBotStartMessage::class, TdResult.Failure(TdError(400, "Bot is blocked")))

            assertTrue(repository.sendStartMessage(BOT, BOT) is Outcome.Failure)
        }

    private companion object {
        const val BOT = 42L
        const val OTHER_BOT = 43L
        const val PERSON = 7L
        const val GROUP = -100L
        const val BASIC_GROUP = 8L
        const val SUPERGROUP = 200L
    }
}
