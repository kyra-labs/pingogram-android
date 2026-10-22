package dev.kyralabs.pingogram.link

import app.cash.turbine.test
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.data.repository.TdlibLinkRepository
import dev.kyralabs.pingogram.core.domain.model.LinkSubject
import dev.kyralabs.pingogram.core.domain.model.LinkTarget
import dev.kyralabs.pingogram.core.domain.model.NewChatKind
import dev.kyralabs.pingogram.core.tdlib.TdError
import dev.kyralabs.pingogram.core.tdlib.TdResult
import dev.kyralabs.pingogram.core.testing.FakeTdlibClient
import dev.kyralabs.pingogram.core.testing.TdApiFixtures.chat
import dev.kyralabs.pingogram.core.testing.TdApiFixtures.user
import dev.kyralabs.pingogram.feature.chat.ui.bot.BotStartInbox
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.drinkless.tdlib.TdApi
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/** Opening Telegram links, through the real [TdlibLinkRepository] on a [FakeTdlibClient]. */
@OptIn(ExperimentalCoroutinesApi::class)
class LinkViewModelTest {
    private val client = FakeTdlibClient()
    private val botStarts = BotStartInbox()
    private val viewModel = LinkViewModel(TdlibLinkRepository(client), botStarts)

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun linkType(type: TdApi.InternalLinkType) =
        client.on(TdApi.GetInternalLinkType::class, TdResult.Success(type))

    private fun username(
        chat: TdApi.Chat,
        draftText: String = "",
    ) {
        linkType(TdApi.InternalLinkTypePublicChat("ada", draftText, false))
        client.on(TdApi.SearchPublicChat::class, TdResult.Success(chat))
        client.on(TdApi.GetUser::class, TdResult.Success(user(id = USER)))
    }

    @Test
    fun `resolves nothing at first`() {
        assertFalse(viewModel.isResolving.value)
    }

    @Test
    fun `a link tapped in the app opens its screen`() =
        runTest {
            username(chat(id = CHAT, type = TdApi.ChatTypePrivate(USER)))

            viewModel.effects.test {
                viewModel.open(LINK)

                assertEquals(LinkEffect.Open(LinkTarget.UserProfile(USER), fromOutside = false), awaitItem())
            }
        }

    @Test
    fun `a bot's start link opens its chat and hands the parameter to it, sending nothing`() =
        runTest {
            linkType(TdApi.InternalLinkTypeBotStart("helper_bot", "ref42", true))
            client.on(
                TdApi.SearchPublicChat::class,
                TdResult.Success(chat(id = USER, type = TdApi.ChatTypePrivate(USER))),
            )
            client.on(TdApi.GetUser::class, TdResult.Success(user(id = USER, type = TdApi.UserTypeBot())))

            viewModel.effects.test {
                viewModel.open(LINK)

                assertEquals(
                    LinkEffect.Open(LinkTarget.BotStart(USER, USER, "ref42"), fromOutside = false),
                    awaitItem(),
                )
            }
            assertEquals("ref42", botStarts.deliveredTo(USER).first())
            assertTrue(client.sentOf<TdApi.SendBotStartMessage>().isEmpty())
        }

    @Test
    fun `a link from another app says so`() =
        runTest {
            linkType(TdApi.InternalLinkTypeNewGroupChat())

            viewModel.effects.test {
                viewModel.open("tg://new_group", fromOutside = true)

                assertEquals(LinkEffect.Open(LinkTarget.NewChat(NewChatKind.Group), fromOutside = true), awaitItem())
            }
        }

    @Test
    fun `is resolving until the link is looked up`() =
        runTest {
            username(chat(id = CHAT, type = TdApi.ChatTypePrivate(USER)))

            viewModel.open(LINK)
            assertTrue(viewModel.isResolving.value)
            runCurrent()

            assertFalse(viewModel.isResolving.value)
        }

    @Test
    fun `cancelling opens nothing`() =
        runTest {
            linkType(TdApi.InternalLinkTypePublicChat("ada", "", false))
            client.on(TdApi.SearchPublicChat::class) { awaitCancellation() }

            viewModel.effects.test {
                viewModel.open(LINK)
                runCurrent()
                viewModel.cancel()
                runCurrent()

                assertFalse(viewModel.isResolving.value)
                expectNoEvents()
            }
        }

    @Test
    fun `a new link replaces one still being looked up`() =
        runTest {
            linkType(TdApi.InternalLinkTypePublicChat("ada", "", false))
            client.on(TdApi.SearchPublicChat::class) { awaitCancellation() }

            viewModel.effects.test {
                viewModel.open(LINK)
                runCurrent()
                linkType(TdApi.InternalLinkTypeSearch())
                viewModel.open("tg://chats/search")

                assertEquals(LinkEffect.Open(LinkTarget.Search, fromOutside = false), awaitItem())
                assertFalse(viewModel.isResolving.value)
                expectNoEvents()
            }
        }

    @Test
    fun `a chat with a text gets it as its draft before it opens`() =
        runTest {
            username(chat(id = CHANNEL, type = TdApi.ChatTypeSupergroup(5, true)), draftText = "Hello")
            client.on(TdApi.GetChat::class, TdResult.Success(chat(id = CHANNEL)))
            client.on(TdApi.SetChatDraftMessage::class, TdResult.Success(TdApi.Ok()))

            viewModel.effects.test {
                viewModel.open(LINK)

                assertEquals(
                    LinkEffect.Open(LinkTarget.Chat(CHANNEL, draftText = "Hello"), fromOutside = false),
                    awaitItem(),
                )
                assertEquals(CHANNEL, client.sentOf<TdApi.SetChatDraftMessage>().single().chatId)
            }
        }

    @Test
    fun `the chat opens even if its draft could not be set`() =
        runTest {
            username(chat(id = CHANNEL, type = TdApi.ChatTypeSupergroup(5, true)), draftText = "Hello")
            client.on(TdApi.GetChat::class, TdResult.Failure(TdError(500, "Network is unreachable")))

            viewModel.effects.test {
                viewModel.open(LINK)

                assertEquals(
                    LinkEffect.Open(LinkTarget.Chat(CHANNEL, draftText = "Hello"), fromOutside = false),
                    awaitItem(),
                )
            }
        }

    @Test
    fun `a username nobody has says so`() =
        runTest {
            linkType(TdApi.InternalLinkTypePublicChat("nobody", "", false))
            client.on(TdApi.SearchPublicChat::class, TdResult.Failure(TdError(400, "USERNAME_NOT_OCCUPIED")))

            viewModel.effects.test {
                viewModel.open(LINK)

                assertEquals(LinkEffect.Notice(LinkNotice.NotFound(LinkSubject.Username)), awaitItem())
            }
        }

    @Test
    fun `no network says so`() = failsWith(TdError(500, "Network is unreachable"), DomainError.Network)

    @Test
    fun `a flood wait says how long to wait`() =
        failsWith(TdError(429, "Too Many Requests: retry after 30"), DomainError.FloodWait(30))

    @Test
    fun `a session that is gone says so`() = failsWith(TdError(401, "Unauthorized"), DomainError.Unauthorized)

    private fun failsWith(
        tdError: TdError,
        error: DomainError,
    ) = runTest {
        linkType(TdApi.InternalLinkTypePublicChat("ada", "", false))
        client.on(TdApi.SearchPublicChat::class, TdResult.Failure(tdError))

        viewModel.effects.test {
            viewModel.open(LINK)

            assertEquals(LinkEffect.Notice(LinkNotice.Failed(error)), awaitItem())
        }
    }

    @Test
    fun `a login code is never used`() =
        runTest {
            linkType(TdApi.InternalLinkTypeAuthenticationCode("12345"))

            viewModel.effects.test {
                viewModel.open("https://t.me/login/12345")

                assertEquals(LinkEffect.Notice(LinkNotice.LoginCode), awaitItem())
            }
        }

    @Test
    fun `a web link Pingogram does not open offers the browser`() =
        runTest {
            linkType(TdApi.InternalLinkTypeStickerSet())

            viewModel.effects.test {
                viewModel.open("https://t.me/addstickers/pack")

                assertEquals(
                    LinkEffect.Notice(LinkNotice.Unsupported("https://t.me/addstickers/pack")),
                    awaitItem(),
                )
            }
        }

    @Test
    fun `an app link Pingogram does not open offers nothing`() =
        runTest {
            linkType(TdApi.InternalLinkTypeUnknownDeepLink("tg://unknown"))

            viewModel.effects.test {
                viewModel.open("tg://unknown")

                assertEquals(LinkEffect.Notice(LinkNotice.Unsupported(browserUrl = null)), awaitItem())
            }
        }

    @Test
    fun `an invitation opens the join sheet, resolving only the link`() =
        runTest {
            linkType(TdApi.InternalLinkTypeChatInvite("https://t.me/+abc"))

            viewModel.effects.test {
                viewModel.open("https://t.me/+abc")

                assertEquals(
                    LinkEffect.Open(LinkTarget.ChatInvite("https://t.me/+abc"), fromOutside = false),
                    awaitItem(),
                )
            }
            // Nothing joins before the user taps "Join" on the sheet.
            assertEquals(listOf(TdApi.GetInternalLinkType::class.java), client.sent.map { it.javaClass })
        }

    @Test
    fun `a QR code login from another app opens its confirmation, confirming nothing`() =
        runTest {
            linkType(TdApi.InternalLinkTypeQrCodeAuthentication())

            viewModel.effects.test {
                viewModel.open("tg://login?token=abc", fromOutside = true)

                assertEquals(
                    LinkEffect.Open(LinkTarget.QrCodeLogin("tg://login?token=abc"), fromOutside = true),
                    awaitItem(),
                )
            }
            assertEquals(emptyList<Any>(), client.sentOf<TdApi.ConfirmQrCodeAuthentication>())
        }

    @Test
    fun `a target the scanner resolved opens as a resolved link would`() =
        runTest {
            viewModel.effects.test {
                viewModel.show(LinkTarget.ChatBoost(CHANNEL))
                assertEquals(LinkEffect.Open(LinkTarget.ChatBoost(CHANNEL), fromOutside = false), awaitItem())

                viewModel.show(LinkTarget.LoginCode)
                assertEquals(LinkEffect.Notice(LinkNotice.LoginCode), awaitItem())
            }
            assertEquals(emptyList<Any>(), client.sent)
        }

    @Test
    fun `a message to share opens the share sheet, sending nothing`() =
        runTest {
            val text = TdApi.FormattedText("https://example.com\nLook", emptyArray())
            linkType(TdApi.InternalLinkTypeMessageDraft(text, true))

            viewModel.effects.test {
                viewModel.open("https://t.me/share?url=https://example.com&text=Look")

                val target = (awaitItem() as LinkEffect.Open).target as LinkTarget.MessageDraft
                assertEquals("https://example.com\nLook", target.text.text)
            }
            assertEquals(listOf(TdApi.GetInternalLinkType::class.java), client.sent.map { it.javaClass })
        }

    private companion object {
        const val LINK = "https://t.me/ada"
        const val USER = 42L
        const val CHAT = 100L
        const val CHANNEL = -1005L
    }
}
