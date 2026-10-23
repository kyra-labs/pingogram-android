package dev.kyralabs.pingogram.core.data.mapper

import dev.kyralabs.pingogram.core.domain.model.AdministratorRights
import dev.kyralabs.pingogram.core.domain.model.BotButtonStyle
import dev.kyralabs.pingogram.core.domain.model.CallbackData
import dev.kyralabs.pingogram.core.domain.model.InlineKeyboardButton
import dev.kyralabs.pingogram.core.domain.model.InlineKeyboardButtonType
import dev.kyralabs.pingogram.core.domain.model.KeyboardButton
import dev.kyralabs.pingogram.core.domain.model.KeyboardButtonType
import dev.kyralabs.pingogram.core.domain.model.ReplyMarkup
import dev.kyralabs.pingogram.core.domain.model.SwitchInlineTarget
import dev.kyralabs.pingogram.core.testing.ReplyMarkupFixtures
import dev.kyralabs.pingogram.core.testing.TdApiFixtures
import kotlinx.collections.immutable.persistentListOf
import org.drinkless.tdlib.TdApi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/** Bots' buttons under messages and keyboards (td_api.tl `ReplyMarkup`) as domain models. */
class ReplyMarkupMappersTest {
    @Test
    fun `an inline keyboard maps every kind of button, row by row`() {
        val markup =
            TdApi.ReplyMarkupInlineKeyboard(
                arrayOf(
                    arrayOf(
                        button("Open", TdApi.InlineKeyboardButtonTypeUrl("https://example.org")),
                        button("Log in", TdApi.InlineKeyboardButtonTypeLoginUrl("https://example.org/login", 7, "")),
                    ),
                    arrayOf(
                        button("Vote", TdApi.InlineKeyboardButtonTypeCallback(byteArrayOf(1, 2))),
                        button("Confirm", TdApi.InlineKeyboardButtonTypeCallbackWithPassword(byteArrayOf(3))),
                    ),
                    arrayOf(
                        button("Share", TdApi.InlineKeyboardButtonTypeSwitchInline("cats", TdApi.TargetChatCurrent())),
                        button("Copy", TdApi.InlineKeyboardButtonTypeCopyText("PROMO")),
                        button("Author", TdApi.InlineKeyboardButtonTypeUser(42)),
                    ),
                    arrayOf(
                        button("App", TdApi.InlineKeyboardButtonTypeWebApp("https://example.org/app")),
                        button("Play", TdApi.InlineKeyboardButtonTypeCallbackGame()),
                        button("Pay", TdApi.InlineKeyboardButtonTypeBuy()),
                        button("Off", TdApi.InlineKeyboardButtonTypeDisabled()),
                    ),
                ),
                false,
            )

        val rows = (markup.toReplyMarkup() as ReplyMarkup.InlineKeyboard).rows

        assertEquals(
            listOf(
                listOf(
                    InlineKeyboardButtonType.Url("https://example.org"),
                    InlineKeyboardButtonType.LoginUrl("https://example.org/login", 7, ""),
                ),
                listOf(
                    InlineKeyboardButtonType.Callback(CallbackData(byteArrayOf(1, 2))),
                    InlineKeyboardButtonType.CallbackWithPassword(CallbackData(byteArrayOf(3))),
                ),
                listOf(
                    InlineKeyboardButtonType.SwitchInline("cats", SwitchInlineTarget.CurrentChat),
                    InlineKeyboardButtonType.CopyText("PROMO"),
                    InlineKeyboardButtonType.User(42),
                ),
                listOf(
                    InlineKeyboardButtonType.WebApp("https://example.org/app"),
                    InlineKeyboardButtonType.CallbackGame,
                    InlineKeyboardButtonType.Buy,
                    InlineKeyboardButtonType.Disabled,
                ),
            ),
            rows.map { row -> row.map { it.type } },
        )
        assertEquals("Open", rows[0][0].text)
    }

    @Test
    fun `a switch inline button names the chats the bot allows`() {
        val types = TdApi.TargetChatTypes(true, false, true, false)
        val markup =
            ReplyMarkupFixtures.inlineKeyboard(
                arrayOf(button("Send", TdApi.InlineKeyboardButtonTypeSwitchInline("", TdApi.TargetChatChosen(types)))),
            )

        assertEquals(
            InlineKeyboardButtonType.SwitchInline(
                query = "",
                target = SwitchInlineTarget.ChosenChat(users = true, bots = false, groups = true, channels = false),
            ),
            (markup.toReplyMarkup() as ReplyMarkup.InlineKeyboard)
                .rows
                .single()
                .single()
                .type,
        )
    }

    @Test
    fun `button styles map to the bot's style, and default without one`() {
        val markup =
            ReplyMarkupFixtures.inlineKeyboard(
                arrayOf(
                    TdApi.InlineKeyboardButton("A", 0, TdApi.ButtonStylePrimary(), TdApi.InlineKeyboardButtonTypeBuy()),
                    TdApi.InlineKeyboardButton("B", 0, TdApi.ButtonStyleDanger(), TdApi.InlineKeyboardButtonTypeBuy()),
                    TdApi.InlineKeyboardButton("C", 0, null, TdApi.InlineKeyboardButtonTypeBuy()),
                ),
            )

        assertEquals(
            listOf(BotButtonStyle.Primary, BotButtonStyle.Danger, BotButtonStyle.Default),
            (markup.toReplyMarkup() as ReplyMarkup.InlineKeyboard).rows.single().map { it.style },
        )
    }

    @Test
    fun `empty rows are left out, and a keyboard without buttons is none`() {
        val markup = TdApi.ReplyMarkupInlineKeyboard(arrayOf(emptyArray(), arrayOf(button("A")), emptyArray()), false)

        assertEquals(
            ReplyMarkup.InlineKeyboard(
                persistentListOf(persistentListOf(InlineKeyboardButton("A", BotButtonStyle.Default, CALLBACK))),
            ),
            markup.toReplyMarkup(),
        )
        assertNull(TdApi.ReplyMarkupInlineKeyboard(arrayOf(emptyArray()), false).toReplyMarkup())
    }

    @Test
    fun `a reply keyboard maps its options and its text, phone and location buttons`() {
        val keyboard = replyKeyboard().toReplyMarkup() as ReplyMarkup.ShowKeyboard

        assertEquals(
            listOf(
                KeyboardButtonType.Text,
                KeyboardButtonType.RequestPhoneNumber,
                KeyboardButtonType.RequestLocation,
            ),
            keyboard.rows[0].map(KeyboardButton::type),
        )
        assertEquals(
            listOf(true, true, false, true, "Choose", true),
            listOf(
                keyboard.persistent,
                keyboard.resize,
                keyboard.oneTime,
                keyboard.personal,
                keyboard.placeholder,
                keyboard.forceReply,
            ),
        )
    }

    @Test
    fun `a reply keyboard maps its poll, users and chat requests`() {
        val keyboard = replyKeyboard().toReplyMarkup() as ReplyMarkup.ShowKeyboard

        assertEquals(
            listOf(
                KeyboardButtonType.RequestPoll(quiz = true),
                KeyboardButtonType.RequestPoll(quiz = null),
                KeyboardButtonType.RequestUsers(
                    id = 3,
                    isBot = false,
                    isPremium = null,
                    maxQuantity = 2,
                    requestName = true,
                    requestUsername = false,
                    requestPhoto = true,
                ),
                KeyboardButtonType.RequestChat(
                    id = 4,
                    isChannel = true,
                    isForum = null,
                    hasUsername = true,
                    isCreated = false,
                    userRights = AdministratorRights.None.copy(canInviteUsers = true),
                    botRights = null,
                    botIsMember = true,
                    requestTitle = true,
                    requestUsername = false,
                    requestPhoto = false,
                ),
            ),
            keyboard.rows[1].map(KeyboardButton::type),
        )
    }

    @Test
    fun `removing the keyboard and forcing a reply keep whom they are for`() {
        assertEquals(ReplyMarkup.RemoveKeyboard(personal = true), TdApi.ReplyMarkupRemoveKeyboard(true).toReplyMarkup())
        assertEquals(
            ReplyMarkup.ForceReply(personal = false, placeholder = "Your name"),
            TdApi.ReplyMarkupForceReply(false, "Your name").toReplyMarkup(),
        )
    }

    @Test
    fun `a message carries its buttons`() {
        val message =
            ReplyMarkupFixtures.withMarkup(
                TdApiFixtures.message(),
                ReplyMarkupFixtures.inlineKeyboard(arrayOf(button("A"))),
            )

        assertEquals(
            ReplyMarkup.InlineKeyboard(
                persistentListOf(persistentListOf(InlineKeyboardButton("A", BotButtonStyle.Default, CALLBACK))),
            ),
            message.toMessage().replyMarkup,
        )
        assertNull(TdApiFixtures.message().toMessage().replyMarkup)
    }

    @Test
    fun `callback data compares by content`() {
        assertEquals(CallbackData(byteArrayOf(1, 2)), CallbackData(byteArrayOf(1, 2)))
        assertEquals(CallbackData(byteArrayOf(1, 2)).hashCode(), CallbackData(byteArrayOf(1, 2)).hashCode())
        assertNotEquals(CallbackData(byteArrayOf(1, 2)), CallbackData(byteArrayOf(2, 1)))
    }

    /** A reply keyboard with a row of simple buttons and a row of requests. */
    private fun replyKeyboard(): TdApi.ReplyMarkupShowKeyboard {
        val rights = TdApi.ChatAdministratorRights().also { it.canInviteUsers = true }
        return TdApi.ReplyMarkupShowKeyboard(
            arrayOf(
                arrayOf(
                    keyboardButton("Hi", TdApi.KeyboardButtonTypeText()),
                    keyboardButton("Phone", TdApi.KeyboardButtonTypeRequestPhoneNumber()),
                    keyboardButton("Here", TdApi.KeyboardButtonTypeRequestLocation()),
                ),
                arrayOf(
                    keyboardButton("Quiz", TdApi.KeyboardButtonTypeRequestPoll(false, true)),
                    keyboardButton("Poll", TdApi.KeyboardButtonTypeRequestPoll(false, false)),
                    keyboardButton(
                        "Friends",
                        TdApi.KeyboardButtonTypeRequestUsers(3, true, false, false, true, 2, true, false, true),
                    ),
                    keyboardButton(
                        "Channel",
                        TdApi.KeyboardButtonTypeRequestChat(
                            4,
                            true,
                            false,
                            false,
                            true,
                            true,
                            false,
                            rights,
                            null,
                            true,
                            true,
                            false,
                            false,
                        ),
                    ),
                ),
            ),
            true,
            true,
            false,
            true,
            true,
            "Choose",
        )
    }

    private fun button(
        text: String,
        type: TdApi.InlineKeyboardButtonType = TdApi.InlineKeyboardButtonTypeCallback(byteArrayOf(1)),
    ) = ReplyMarkupFixtures.inlineButton(text, type)

    private fun keyboardButton(
        text: String,
        type: TdApi.KeyboardButtonType,
    ) = TdApi.KeyboardButton(text, 0, TdApi.ButtonStyleDefault(), type)

    private companion object {
        val CALLBACK = InlineKeyboardButtonType.Callback(CallbackData(byteArrayOf(1)))
    }
}
