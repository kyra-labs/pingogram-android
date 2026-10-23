package dev.kyralabs.pingogram.feature.chat.ui.preview

import dev.kyralabs.pingogram.core.ui.text.UiText
import dev.kyralabs.pingogram.feature.chat.R
import dev.kyralabs.pingogram.feature.chat.ui.bots.BotButtonPosition
import dev.kyralabs.pingogram.feature.chat.ui.bots.BotButtonTone
import dev.kyralabs.pingogram.feature.chat.ui.bots.BotKeyboard
import dev.kyralabs.pingogram.feature.chat.ui.bots.BotKeyboardButton
import dev.kyralabs.pingogram.feature.chat.ui.bots.BotKeyboardDialog
import dev.kyralabs.pingogram.feature.chat.ui.bots.KeyboardShareSheet
import dev.kyralabs.pingogram.feature.chat.ui.bots.ReplyKeyboardUiState
import dev.kyralabs.pingogram.feature.chat.ui.bots.ShareKind
import dev.kyralabs.pingogram.feature.chat.ui.forward.ForwardTarget
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf

/**
 * A bot's keyboard for previews and screenshot tests: a shop bot's menu with text buttons in rows of one to three,
 * requests for the phone number, the location, a poll, people and a group, one waiting for Telegram; the phone number
 * question and the people and group pickers that follow a tap.
 */
internal object BotKeyboardPreviewData {
    private const val MESSAGE = 9L

    /** The menu: three sizes in a row, two actions, the requests, and a primary "Checkout". */
    val keyboard =
        ReplyKeyboardUiState(
            buttons =
                BotKeyboard(
                    messageId = MESSAGE,
                    rows =
                        persistentListOf(
                            persistentListOf(button("Small"), button("Medium"), button("Large")),
                            persistentListOf(button("🛒 Cart"), button("Order history")),
                            persistentListOf(button("📞 Share phone number"), button("📍 Send location")),
                            persistentListOf(button("📊 Create a poll"), button("👥 Invite friends")),
                            persistentListOf(button("Pick a group for updates")),
                            persistentListOf(button("Checkout", BotButtonTone.Primary)),
                        ),
                ),
            sendsText =
                persistentSetOf(
                    BotButtonPosition(0, 0),
                    BotButtonPosition(0, 1),
                    BotButtonPosition(0, 2),
                    BotButtonPosition(1, 0),
                    BotButtonPosition(1, 1),
                    BotButtonPosition(5, 0),
                ),
        )

    /** A short keyboard that is only as tall as its two rows, shown once. */
    val resizedKeyboard =
        ReplyKeyboardUiState(
            buttons =
                BotKeyboard(
                    messageId = MESSAGE,
                    rows =
                        persistentListOf(
                            persistentListOf(button("Yes"), button("No")),
                            persistentListOf(button("Ask me later")),
                        ),
                    pending = persistentSetOf(BotButtonPosition(0, 0)),
                ),
            resize = true,
            oneTime = true,
        )

    val phoneDialog =
        BotKeyboardDialog.SharePhone(botName = UiText.Plain("@shop_bot"), phoneNumber = "447911123456")

    /** People to pick, two of three picked. */
    val usersSheet =
        KeyboardShareSheet(
            kind = ShareKind.Users,
            botName = UiText.Plain("@shop_bot"),
            maxCount = 3,
            selected = persistentSetOf(11L, 13L),
            notes = persistentListOf(UiText.resource(R.string.bot_share_users_note, "@shop_bot")),
            targets =
                persistentListOf(
                    target(11, "Ada Lovelace"),
                    target(12, "Alan Turing"),
                    target(13, "Grace Hopper"),
                    target(14, "Katherine Johnson"),
                ),
        )

    /** A group to pick, with the notes on rights and the bot joining. */
    val groupSheet =
        KeyboardShareSheet(
            kind = ShareKind.Group,
            botName = UiText.Plain("@shop_bot"),
            selected = persistentSetOf(-21L),
            notes =
                persistentListOf(
                    UiText.resource(R.string.bot_share_chat_note, "@shop_bot"),
                    UiText.resource(R.string.bot_share_rights_note),
                    UiText.resource(R.string.bot_share_bot_member_note, "@shop_bot"),
                ),
            targets = persistentListOf(target(-21, "Book club"), target(-22, "Weekend hikes")),
        )

    /** The group picker with no group that the button allows. */
    val emptyGroupSheet = groupSheet.copy(selected = persistentSetOf(), targets = persistentListOf())

    private fun button(
        text: String,
        tone: BotButtonTone = BotButtonTone.Tonal,
    ) = BotKeyboardButton(text, tone = tone)

    private fun target(
        id: Long,
        title: String,
    ) = ForwardTarget(chatId = id, title = UiText.Plain(title), photo = null, placeholderIcon = null)
}
