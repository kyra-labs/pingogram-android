package dev.kyralabs.pingogram.core.testing

import org.drinkless.tdlib.TdApi

/** TDLib's reply markup for tests: the buttons bots attach to messages, and messages and edits that carry them. */
object ReplyMarkupFixtures {
    /** A bot's inline keyboard: [rows] of buttons under its message. */
    fun inlineKeyboard(vararg rows: Array<TdApi.InlineKeyboardButton>) =
        TdApi.ReplyMarkupInlineKeyboard(arrayOf(*rows), false)

    /** A button of an inline keyboard: [text], doing what [type] says, in the default style. */
    fun inlineButton(
        text: String,
        type: TdApi.InlineKeyboardButtonType = TdApi.InlineKeyboardButtonTypeCallback(byteArrayOf(1)),
    ) = TdApi.InlineKeyboardButton(text, 0, TdApi.ButtonStyleDefault(), type)

    /** [message] with [replyMarkup] attached. */
    fun withMarkup(
        message: TdApi.Message,
        replyMarkup: TdApi.ReplyMarkup?,
    ): TdApi.Message = message.also { it.replyMarkup = replyMarkup }

    /** Message [messageId] of chat [chatId] edited at [editDate], its buttons now [replyMarkup]. */
    fun updateMessageEdited(
        chatId: Long,
        messageId: Long,
        editDate: Int,
        replyMarkup: TdApi.ReplyMarkup?,
    ) = TdApi.UpdateMessageEdited(chatId, messageId, editDate, replyMarkup)
}
