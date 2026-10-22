package dev.kyralabs.pingogram.feature.chat.ui.preview

import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.ui.text.UiText
import dev.kyralabs.pingogram.feature.chat.R
import dev.kyralabs.pingogram.feature.chat.ui.ChatHeader
import dev.kyralabs.pingogram.feature.chat.ui.ChatItem
import dev.kyralabs.pingogram.feature.chat.ui.ChatSubtitle
import dev.kyralabs.pingogram.feature.chat.ui.ChatUiState
import dev.kyralabs.pingogram.feature.chat.ui.GroupPosition
import dev.kyralabs.pingogram.feature.chat.ui.OutgoingStatus
import dev.kyralabs.pingogram.feature.chat.ui.bots.BotButtonDialog
import dev.kyralabs.pingogram.feature.chat.ui.bots.BotButtonMark
import dev.kyralabs.pingogram.feature.chat.ui.bots.BotButtonPosition
import dev.kyralabs.pingogram.feature.chat.ui.bots.BotButtonTone
import dev.kyralabs.pingogram.feature.chat.ui.bots.BotKeyboard
import dev.kyralabs.pingogram.feature.chat.ui.bots.BotKeyboardButton
import dev.kyralabs.pingogram.feature.chat.ui.bots.SwitchInlineSheet
import dev.kyralabs.pingogram.feature.chat.ui.preview.ChatPreviewData.bubble
import dev.kyralabs.pingogram.feature.chat.ui.preview.ChatPreviewData.content
import dev.kyralabs.pingogram.feature.chat.ui.preview.ChatPreviewData.today
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableList

/**
 * Bots' buttons for previews and screenshot tests: a chat with a bot whose messages carry inline keyboards of every
 * look (callbacks in connected rows, a link, an inline query, a disabled button, a primary and a destructive one), one
 * waiting for its bot, and the questions and chat picker that follow a tap.
 */
internal object BotButtonsPreviewData {
    private const val BOT = 500L

    /** A poll's keyboard: three answers in a row, then a link and an inline query side by side, then one alone. */
    val keyboard =
        BotKeyboard(
            messageId = 3,
            rows =
                persistentListOf(
                    persistentListOf(button("👍 12"), button("🤔 3"), button("👎 1")),
                    persistentListOf(
                        button("Results", BotButtonMark.Link),
                        button("Share poll", BotButtonMark.SwitchInline),
                    ),
                    persistentListOf(button("Close poll", tone = BotButtonTone.Danger)),
                ),
        )

    /** [keyboard] with its first answer waiting for the bot. */
    val pendingKeyboard = keyboard.copy(pending = persistentSetOf(BotButtonPosition(0, 0)))

    /** A settings message's keyboard: a primary action, a page turn and a button the bot turned off. */
    val settingsKeyboard =
        BotKeyboard(
            messageId = 2,
            rows =
                persistentListOf(
                    persistentListOf(button("Turn on reminders", tone = BotButtonTone.Primary)),
                    persistentListOf(button("‹ Back", isEnabled = false), button("Next ›")),
                ),
        )

    /** A chat with a poll bot: its messages with their buttons, one waiting, below the user's command. */
    val conversation: ChatUiState
        get() =
            ChatUiState(
                composer = ChatPreviewData.canWrite,
                header =
                    ChatHeader(
                        chatId = BOT,
                        title = UiText.Plain("Poll Bot"),
                        photo = null,
                        placeholderIcon = null,
                        subtitle = ChatSubtitle.Status(UiText.resource(R.string.chat_subtitle_bot)),
                        profileUserId = BOT,
                    ),
                content =
                    content(
                        bubble(
                            3,
                            "Which shape should the send button morph into?",
                            time = "9:42 AM",
                            sender = UiText.Plain("Poll Bot"),
                            senderId = BOT,
                        ).copy(keyboard = pendingKeyboard),
                        bubble(
                            2,
                            "Reminders are off.",
                            time = "9:41 AM",
                            sender = UiText.Plain("Poll Bot"),
                            senderId = BOT,
                            position = GroupPosition.Single,
                        ).copy(keyboard = settingsKeyboard),
                        bubble(1, "/poll", time = "9:41 AM", isOutgoing = true, status = OutgoingStatus.Read),
                        ChatItem.DateSeparator(epochDay = 1, date = today),
                    ),
            )

    /** The password question after a wrong password. */
    val passwordWrong = BotButtonDialog.Password(UiText.Plain("@pollbot"), error = DomainError.PasswordInvalid)

    /** The login question of a site that also asks to send messages. */
    val login =
        BotButtonDialog.Login(
            domain = "polls.example.org",
            botName = UiText.Plain("@pollbot"),
            myName = UiText.Plain("Ada Lovelace"),
            requestWriteAccess = true,
        )

    /** The chat picker of "Share poll": the forward sheet's chats but the secret chat, where bots are not offered. */
    val chatPicker =
        SwitchInlineSheet(
            query = "@pollbot shapes",
            targets = ForwardPreviewData.targets.filterNot { it.isSecret }.toImmutableList(),
        )

    private fun button(
        text: String,
        mark: BotButtonMark = BotButtonMark.None,
        tone: BotButtonTone = BotButtonTone.Tonal,
        isEnabled: Boolean = true,
    ) = BotKeyboardButton(text, mark, tone, isEnabled)
}
