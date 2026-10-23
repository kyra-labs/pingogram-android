package dev.kyralabs.pingogram.core.data.repository

import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.FormattedText
import dev.kyralabs.pingogram.core.domain.model.LinkSubject
import dev.kyralabs.pingogram.core.domain.model.LinkTarget
import dev.kyralabs.pingogram.core.domain.model.NewChatKind
import dev.kyralabs.pingogram.core.domain.model.SettingsPage
import dev.kyralabs.pingogram.core.tdlib.TdError
import dev.kyralabs.pingogram.core.tdlib.TdResult
import dev.kyralabs.pingogram.core.testing.FakeTdlibClient
import dev.kyralabs.pingogram.core.testing.TdApiFixtures.chat
import dev.kyralabs.pingogram.core.testing.TdApiFixtures.message
import dev.kyralabs.pingogram.core.testing.TdApiFixtures.user
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.test.runTest
import org.drinkless.tdlib.TdApi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource

/** Telegram links: what `getInternalLinkType` says, and the lookups each kind takes. */
class TdlibLinkRepositoryTest {
    private val client = FakeTdlibClient()
    private val repository = TdlibLinkRepository(client)

    private fun linkType(type: TdApi.InternalLinkType) =
        client.on(TdApi.GetInternalLinkType::class, TdResult.Success(type))

    @Test
    fun `asks TDLib what the link is`() =
        runTest {
            linkType(TdApi.InternalLinkTypeSearch())

            repository.resolve("tg://chats/search")

            assertEquals("tg://chats/search", client.sentOf<TdApi.GetInternalLinkType>().single().link)
        }

    @Test
    fun `a person's username opens their profile`() =
        runTest {
            linkType(TdApi.InternalLinkTypePublicChat("ada", "", false))
            client.on(
                TdApi.SearchPublicChat::class,
                TdResult.Success(chat(id = CHAT, type = TdApi.ChatTypePrivate(USER))),
            )
            client.on(TdApi.GetUser::class, TdResult.Success(user(id = USER)))

            assertEquals(Outcome.Success(LinkTarget.UserProfile(USER)), repository.resolve("https://t.me/ada"))
            assertEquals("ada", client.sentOf<TdApi.SearchPublicChat>().single().username)
        }

    @Test
    fun `a bot's username opens its chat`() =
        runTest {
            linkType(TdApi.InternalLinkTypePublicChat("helper_bot", "", false))
            client.on(
                TdApi.SearchPublicChat::class,
                TdResult.Success(chat(id = CHAT, type = TdApi.ChatTypePrivate(USER))),
            )
            client.on(TdApi.GetUser::class, TdResult.Success(user(id = USER, type = TdApi.UserTypeBot())))

            assertEquals(Outcome.Success(LinkTarget.Chat(CHAT)), repository.resolve("https://t.me/helper_bot"))
        }

    @Test
    fun `a bot's start link opens its chat with the start parameter, never started by itself`() =
        runTest {
            linkType(TdApi.InternalLinkTypeBotStart("helper_bot", "ref42", true))
            // A private chat's id is its user's.
            client.on(
                TdApi.SearchPublicChat::class,
                TdResult.Success(chat(id = USER, type = TdApi.ChatTypePrivate(USER))),
            )
            client.on(TdApi.GetUser::class, TdResult.Success(user(id = USER, type = TdApi.UserTypeBot())))

            assertEquals(
                Outcome.Success(LinkTarget.BotStart(USER, USER, "ref42")),
                repository.resolve("https://t.me/helper_bot?start=ref42"),
            )
            assertEquals("helper_bot", client.sentOf<TdApi.SearchPublicChat>().single().username)
            assertTrue(client.sentOf<TdApi.SendBotStartMessage>().isEmpty())
        }

    @Test
    fun `a start link to someone who is no bot is not found`() =
        runTest {
            linkType(TdApi.InternalLinkTypeBotStart("ada", "ref42", false))
            client.on(
                TdApi.SearchPublicChat::class,
                TdResult.Success(chat(id = CHAT, type = TdApi.ChatTypePrivate(USER))),
            )
            client.on(TdApi.GetUser::class, TdResult.Success(user(id = USER)))

            assertEquals(
                Outcome.Success(LinkTarget.NotFound(LinkSubject.Username)),
                repository.resolve("https://t.me/ada?start=ref42"),
            )
        }

    @Test
    fun `a start link to a bot nobody has is not found`() =
        runTest {
            linkType(TdApi.InternalLinkTypeBotStart("gone_bot", "", false))
            client.on(TdApi.SearchPublicChat::class, TdResult.Failure(TdError(400, "USERNAME_NOT_OCCUPIED")))

            assertEquals(
                Outcome.Success(LinkTarget.NotFound(LinkSubject.Username)),
                repository.resolve("https://t.me/gone_bot?start"),
            )
        }

    @Test
    fun `a username with a text opens the person's chat with the text`() =
        runTest {
            linkType(TdApi.InternalLinkTypePublicChat("ada", "Hello", false))
            client.on(
                TdApi.SearchPublicChat::class,
                TdResult.Success(chat(id = CHAT, type = TdApi.ChatTypePrivate(USER))),
            )
            client.on(TdApi.GetUser::class, TdResult.Success(user(id = USER)))

            assertEquals(
                Outcome.Success(LinkTarget.Chat(CHAT, draftText = "Hello")),
                repository.resolve("https://t.me/ada?text=Hello"),
            )
        }

    @Test
    fun `a bot's username with profile opens its profile`() =
        runTest {
            linkType(TdApi.InternalLinkTypePublicChat("helper_bot", "", true))
            client.on(
                TdApi.SearchPublicChat::class,
                TdResult.Success(chat(id = CHAT, type = TdApi.ChatTypePrivate(USER))),
            )
            client.on(TdApi.GetUser::class, TdResult.Success(user(id = USER, type = TdApi.UserTypeBot())))

            assertEquals(
                Outcome.Success(LinkTarget.UserProfile(USER)),
                repository.resolve("https://t.me/helper_bot?profile"),
            )
        }

    @Test
    fun `a channel's username opens its chat, with the text`() =
        runTest {
            linkType(TdApi.InternalLinkTypePublicChat("news", "Hi", false))
            client.on(TdApi.SearchPublicChat::class, TdResult.Success(chat(id = CHANNEL, type = channelType())))

            assertEquals(
                Outcome.Success(LinkTarget.Chat(CHANNEL, draftText = "Hi")),
                repository.resolve("https://t.me/news"),
            )
        }

    @Test
    fun `a channel's username with profile opens its info`() =
        runTest {
            linkType(TdApi.InternalLinkTypePublicChat("news", "", true))
            client.on(TdApi.SearchPublicChat::class, TdResult.Success(chat(id = CHANNEL, type = channelType())))

            assertEquals(Outcome.Success(LinkTarget.ChatInfo(CHANNEL)), repository.resolve("https://t.me/news?profile"))
        }

    @Test
    fun `a username nobody has is not found`() =
        runTest {
            linkType(TdApi.InternalLinkTypePublicChat("nobody", "", false))
            client.on(TdApi.SearchPublicChat::class, TdResult.Failure(TdError(400, "USERNAME_NOT_OCCUPIED")))

            assertEquals(
                Outcome.Success(LinkTarget.NotFound(LinkSubject.Username)),
                repository.resolve("https://t.me/nobody"),
            )
        }

    @Test
    fun `a lookup without network fails`() =
        runTest {
            linkType(TdApi.InternalLinkTypePublicChat("ada", "", false))
            client.on(TdApi.SearchPublicChat::class, TdResult.Failure(TdError(500, "Network is unreachable")))

            assertEquals(Outcome.Failure(DomainError.Network), repository.resolve("https://t.me/ada"))
        }

    @Test
    fun `a flood wait fails with its time`() =
        runTest {
            linkType(TdApi.InternalLinkTypePublicChat("ada", "", false))
            client.on(
                TdApi.SearchPublicChat::class,
                TdResult.Failure(TdError(429, "Too Many Requests: retry after 30")),
            )

            assertEquals(Outcome.Failure(DomainError.FloodWait(30)), repository.resolve("https://t.me/ada"))
        }

    @Test
    fun `a phone number opens the person's profile`() =
        runTest {
            linkType(TdApi.InternalLinkTypeUserPhoneNumber("15551234567", "", false))
            client.on(TdApi.SearchUserByPhoneNumber::class, TdResult.Success(user(id = USER)))

            assertEquals(Outcome.Success(LinkTarget.UserProfile(USER)), repository.resolve("https://t.me/+15551234567"))
            val sent = client.sentOf<TdApi.SearchUserByPhoneNumber>().single()
            assertEquals("15551234567", sent.phoneNumber)
            assertEquals(false, sent.onlyLocal)
        }

    @Test
    fun `a phone number with a text opens the chat with the person`() =
        runTest {
            linkType(TdApi.InternalLinkTypeUserPhoneNumber("15551234567", "Hello", false))
            client.on(TdApi.SearchUserByPhoneNumber::class, TdResult.Success(user(id = USER)))
            client.on(TdApi.CreatePrivateChat::class, TdResult.Success(chat(id = CHAT)))

            assertEquals(
                Outcome.Success(LinkTarget.Chat(CHAT, draftText = "Hello")),
                repository.resolve("https://t.me/+15551234567?text=Hello"),
            )
            assertEquals(USER, client.sentOf<TdApi.CreatePrivateChat>().single().userId)
        }

    @Test
    fun `a phone number nobody has is not found`() =
        runTest {
            linkType(TdApi.InternalLinkTypeUserPhoneNumber("15551234567", "", false))
            client.on(TdApi.SearchUserByPhoneNumber::class, TdResult.Failure(TdError(404, "Not Found")))

            assertEquals(
                Outcome.Success(LinkTarget.NotFound(LinkSubject.PhoneNumber)),
                repository.resolve("https://t.me/+15551234567"),
            )
        }

    @Test
    fun `a contact token opens the person's profile`() =
        runTest {
            linkType(TdApi.InternalLinkTypeUserToken("token"))
            client.on(TdApi.SearchUserByToken::class, TdResult.Success(user(id = USER, type = TdApi.UserTypeBot())))

            assertEquals(
                Outcome.Success(LinkTarget.UserProfile(USER)),
                repository.resolve("https://t.me/contact/token"),
            )
            assertEquals("token", client.sentOf<TdApi.SearchUserByToken>().single().token)
        }

    @Test
    fun `an expired contact token is not found`() =
        runTest {
            linkType(TdApi.InternalLinkTypeUserToken("token"))
            client.on(TdApi.SearchUserByToken::class, TdResult.Failure(TdError(400, "CONTACT_TOKEN_INVALID")))

            assertEquals(
                Outcome.Success(LinkTarget.NotFound(LinkSubject.UserToken)),
                repository.resolve("https://t.me/contact/token"),
            )
        }

    @Test
    fun `a message link opens the chat at the message`() =
        runTest {
            linkType(TdApi.InternalLinkTypeMessage("https://t.me/news/7"))
            client.on(TdApi.GetMessageLinkInfo::class, TdResult.Success(linkInfo(CHANNEL, message(id = MESSAGE))))

            assertEquals(Outcome.Success(LinkTarget.Chat(CHANNEL, MESSAGE)), repository.resolve("https://t.me/news/7"))
            assertEquals("https://t.me/news/7", client.sentOf<TdApi.GetMessageLinkInfo>().single().url)
        }

    @Test
    fun `a link to a message that is gone opens the chat`() =
        runTest {
            linkType(TdApi.InternalLinkTypeMessage("https://t.me/news/7"))
            client.on(TdApi.GetMessageLinkInfo::class, TdResult.Success(linkInfo(CHANNEL, message = null)))

            assertEquals(Outcome.Success(LinkTarget.Chat(CHANNEL)), repository.resolve("https://t.me/news/7"))
        }

    @Test
    fun `a link to a message the user cannot see is not found`() =
        runTest {
            linkType(TdApi.InternalLinkTypeMessage("https://t.me/c/123/7"))
            client.on(TdApi.GetMessageLinkInfo::class, TdResult.Success(linkInfo(chatId = 0, message = null)))

            assertEquals(
                Outcome.Success(LinkTarget.NotFound(LinkSubject.Message)),
                repository.resolve("https://t.me/c/123/7"),
            )
        }

    @Test
    fun `Saved Messages open the user's own chat`() =
        runTest {
            linkType(TdApi.InternalLinkTypeSavedMessages())
            client.on(TdApi.GetMe::class, TdResult.Success(user(id = ME)))
            client.on(
                TdApi.CreatePrivateChat::class,
                TdResult.Success(chat(id = SAVED, type = TdApi.ChatTypePrivate(ME))),
            )

            assertEquals(Outcome.Success(LinkTarget.Chat(SAVED)), repository.resolve("tg://settings/saved-messages"))
            assertEquals(ME, client.sentOf<TdApi.CreatePrivateChat>().single().userId)
        }

    @ParameterizedTest(name = "{1}")
    @MethodSource("pages")
    fun `links to pages of the app need no lookup`(
        type: TdApi.InternalLinkType,
        expected: LinkTarget,
    ) = runTest {
        linkType(type)

        assertEquals(Outcome.Success(expected), repository.resolve(LINK))
        assertEquals(1, client.sent.size)
    }

    @Test
    fun `a QR code login keeps the link, which confirming it takes`() =
        runTest {
            linkType(TdApi.InternalLinkTypeQrCodeAuthentication())

            assertEquals(
                Outcome.Success(LinkTarget.QrCodeLogin("tg://login?token=abc")),
                repository.resolve("tg://login?token=abc"),
            )
        }

    @Test
    fun `a boost link opens the boosts of the chat it names`() =
        runTest {
            linkType(TdApi.InternalLinkTypeChatBoost("https://t.me/news?boost"))
            client.on(TdApi.GetChatBoostLinkInfo::class, TdResult.Success(TdApi.ChatBoostLinkInfo(true, CHAT)))

            assertEquals(Outcome.Success(LinkTarget.ChatBoost(CHAT)), repository.resolve("https://t.me/news?boost"))
            assertEquals("https://t.me/news?boost", client.sentOf<TdApi.GetChatBoostLinkInfo>().single().url)
        }

    @Test
    fun `a boost link to a chat the user cannot see is not found`() =
        runTest {
            linkType(TdApi.InternalLinkTypeChatBoost("https://t.me/boost?c=1"))
            client.on(TdApi.GetChatBoostLinkInfo::class, TdResult.Success(TdApi.ChatBoostLinkInfo(false, 0)))

            assertEquals(
                Outcome.Success(LinkTarget.NotFound(LinkSubject.Chat)),
                repository.resolve("https://t.me/boost?c=1"),
            )
        }

    @Test
    fun `a message to share keeps its text`() =
        runTest {
            linkType(
                TdApi.InternalLinkTypeMessageDraft(
                    TdApi.FormattedText("https://example.com\nLook", emptyArray()),
                    true,
                ),
            )

            assertEquals(
                Outcome.Success(
                    LinkTarget.MessageDraft(FormattedText("https://example.com\nLook", persistentListOf()), true),
                ),
                repository.resolve("https://t.me/share?url=https://example.com&text=Look"),
            )
        }

    @Test
    fun `a login code is never acted upon`() =
        runTest {
            linkType(TdApi.InternalLinkTypeAuthenticationCode("12345"))

            assertEquals(Outcome.Success(LinkTarget.LoginCode), repository.resolve("https://t.me/login/12345"))
            assertEquals(1, client.sent.size)
        }

    @ParameterizedTest(name = "{0}")
    @MethodSource("unsupported")
    fun `kinds Pingogram does not open are unsupported`(type: TdApi.InternalLinkType) =
        runTest {
            linkType(type)

            assertEquals(Outcome.Success(LinkTarget.Unsupported(LINK)), repository.resolve(LINK))
            assertEquals(1, client.sent.size)
        }

    @Test
    fun `a link TDLib does not know is unsupported`() =
        runTest {
            client.on(TdApi.GetInternalLinkType::class, TdResult.Failure(TdError(404, "Not Found")))

            assertEquals(
                Outcome.Success(LinkTarget.Unsupported("https://t.me/")),
                repository.resolve("https://t.me/"),
            )
        }

    @Test
    fun `a session that is not ready fails`() =
        runTest {
            client.on(TdApi.GetInternalLinkType::class, TdResult.Failure(TdError(401, "Unauthorized")))

            assertEquals(Outcome.Failure(DomainError.Unauthorized), repository.resolve(LINK))
        }

    @Test
    fun `prefilling sets the draft of a chat without one`() =
        runTest {
            client.on(TdApi.GetChat::class, TdResult.Success(chat(id = CHAT)))
            client.on(TdApi.SetChatDraftMessage::class, TdResult.Success(TdApi.Ok()))

            assertEquals(Outcome.Success(Unit), repository.prefillDraft(CHAT, "Hello"))

            val sent = client.sentOf<TdApi.SetChatDraftMessage>().single()
            assertEquals(CHAT, sent.chatId)
            assertEquals("Hello", (sent.draftMessage.content as TdApi.DraftMessageContentText).text.text)
        }

    @Test
    fun `prefilling keeps a draft the chat has`() =
        runTest {
            val draft =
                TdApi.DraftMessage(
                    null,
                    0,
                    TdApi.DraftMessageContentText(TdApi.FormattedText("Mine", emptyArray()), null),
                    0,
                    null,
                )
            client.on(TdApi.GetChat::class, TdResult.Success(chat(id = CHAT, draftMessage = draft)))

            assertEquals(Outcome.Success(Unit), repository.prefillDraft(CHAT, "Hello"))

            assertTrue(client.sentOf<TdApi.SetChatDraftMessage>().isEmpty())
        }

    private fun channelType() = TdApi.ChatTypeSupergroup(5, true)

    private fun linkInfo(
        chatId: Long,
        message: TdApi.Message?,
    ) = TdApi.MessageLinkInfo(true, chatId, null, message, 0, 0, "", false)

    companion object {
        private const val LINK = "tg://some_link"
        private const val USER = 42L
        private const val ME = 7L
        private const val CHAT = 100L
        private const val SAVED = 700L
        private const val CHANNEL = -1005L
        private const val MESSAGE = 7L shl 20

        @JvmStatic
        fun pages(): List<Arguments> =
            listOf(
                Arguments.of(TdApi.InternalLinkTypeMyProfilePage(""), LinkTarget.EditProfile),
                Arguments.of(TdApi.InternalLinkTypeContactsPage(""), LinkTarget.Contacts),
                Arguments.of(TdApi.InternalLinkTypeSettings(null), LinkTarget.Settings()),
                Arguments.of(TdApi.InternalLinkTypeSettings(TdApi.SettingsSectionFaq()), LinkTarget.Settings()),
                Arguments.of(
                    TdApi.InternalLinkTypeSettings(TdApi.SettingsSectionEditProfile()),
                    LinkTarget.EditProfile,
                ),
                Arguments.of(
                    TdApi.InternalLinkTypeSettings(TdApi.SettingsSectionPrivacyAndSecurity("")),
                    LinkTarget.Settings(SettingsPage.PrivacyAndSecurity),
                ),
                Arguments.of(
                    TdApi.InternalLinkTypeSettings(TdApi.SettingsSectionAppearance("")),
                    LinkTarget.Settings(SettingsPage.Appearance),
                ),
                Arguments.of(
                    TdApi.InternalLinkTypeSettings(TdApi.SettingsSectionChatFolders("")),
                    LinkTarget.Settings(SettingsPage.ChatFolders),
                ),
                Arguments.of(
                    TdApi.InternalLinkTypeSettings(TdApi.SettingsSectionDevices()),
                    LinkTarget.Settings(SettingsPage.ActiveSessions),
                ),
                Arguments.of(TdApi.InternalLinkTypeNewPrivateChat(), LinkTarget.NewChat(NewChatKind.Private)),
                Arguments.of(TdApi.InternalLinkTypeNewGroupChat(), LinkTarget.NewChat(NewChatKind.Group)),
                Arguments.of(TdApi.InternalLinkTypeNewChannelChat(), LinkTarget.NewChat(NewChatKind.Channel)),
                Arguments.of(TdApi.InternalLinkTypeSearch(), LinkTarget.Search),
                Arguments.of(
                    TdApi.InternalLinkTypeChatInvite("https://t.me/+abc"),
                    LinkTarget.ChatInvite("https://t.me/+abc"),
                ),
                Arguments.of(
                    TdApi.InternalLinkTypeChatFolderInvite("https://t.me/addlist/abc"),
                    LinkTarget.ChatFolderInvite("https://t.me/addlist/abc"),
                ),
                Arguments.of(
                    TdApi.InternalLinkTypeSettings(TdApi.SettingsSectionQrCode("")),
                    LinkTarget.MyQrCode,
                ),
            )

        @JvmStatic
        fun unsupported(): List<TdApi.InternalLinkType> =
            listOf(
                TdApi.InternalLinkTypeProxy(),
                TdApi.InternalLinkTypeLanguagePack(),
                TdApi.InternalLinkTypeTheme(),
                TdApi.InternalLinkTypeBackground(),
                TdApi.InternalLinkTypeStickerSet(),
                TdApi.InternalLinkTypeWebApp(),
                TdApi.InternalLinkTypeInvoice(),
                TdApi.InternalLinkTypePassportDataRequest(),
                TdApi.InternalLinkTypeStory(),
                TdApi.InternalLinkTypeVideoChat(),
                TdApi.InternalLinkTypeInstantView(),
                TdApi.InternalLinkTypePhoneNumberConfirmation(),
                TdApi.InternalLinkTypeUnknownDeepLink(LINK),
            )
    }
}
