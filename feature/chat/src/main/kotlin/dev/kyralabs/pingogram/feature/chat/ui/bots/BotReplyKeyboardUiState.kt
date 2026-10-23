package dev.kyralabs.pingogram.feature.chat.ui.bots

import androidx.compose.runtime.Immutable
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.ui.text.UiText
import dev.kyralabs.pingogram.feature.chat.ui.forward.ForwardTarget
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf

/**
 * What the composer shows of the chat's bots' keyboards (td_api.tl `chat.reply_markup_message_id`): the [keyboard] in
 * effect, null while none is, and the hint of the text field, [placeholder], empty for the usual "Message": the
 * keyboard's, or that of a bot's message that asks for a reply while the composer replies to it.
 */
@Immutable
data class BotReplyUiState(
    val keyboard: ReplyKeyboardUiState? = null,
    val placeholder: String = "",
) {
    override fun toString() = "BotReplyUiState(keyboard=$keyboard, …)"
}

/**
 * A bot's keyboard (td_api.tl `replyMarkupShowKeyboard`), shown in the system keyboard's place: its [buttons], as the
 * inline keyboards' rows show them (their message, rows and progress); [resize] if it is only as tall as its rows
 * need; [oneTime] if it hides once a button was used; [persistent] if it shows whenever the system keyboard does not;
 * [sendsText] the buttons whose tap sends their text, so the conversation scrolls to it.
 */
@Immutable
data class ReplyKeyboardUiState(
    val buttons: BotKeyboard,
    val resize: Boolean = false,
    val oneTime: Boolean = false,
    val persistent: Boolean = false,
    val sendsText: ImmutableSet<BotButtonPosition> = persistentSetOf(),
) {
    /** The message the keyboard came with. */
    val messageId: Long get() = buttons.messageId

    override fun toString() =
        "ReplyKeyboardUiState(messageId=$messageId, resize=$resize, oneTime=$oneTime, persistent=$persistent)"
}

/** The question before a keyboard button shares something with its bot. */
@Immutable
sealed interface BotKeyboardDialog {
    /**
     * A button asks for the user's phone number: "Share your phone number with [botName]?", the number ([phoneNumber],
     * as Telegram has it: digits) and their name going as a contact; [isSharing] while it is being sent.
     */
    data class SharePhone(
        val botName: UiText,
        val phoneNumber: String,
        val isSharing: Boolean = false,
    ) : BotKeyboardDialog {
        override fun toString() = "SharePhone(isSharing=$isSharing, …)"
    }
}

/** What a keyboard button's picker shares with the bot. */
enum class ShareKind {
    /** Users: people, or bots. */
    Users,

    /** A group: a basic group or a supergroup. */
    Group,

    /** A channel. */
    Channel,
}

/**
 * The picker of a keyboard button that asks for users or a chat (`docs/DESIGN_SYSTEM.md` §6 "Bot keyboard"), as the
 * forward sheet lists chats: what it asks for ([kind]), for bot [botName]; up to [maxCount] picks ([selected], by chat
 * id: a private chat's id is its user's); the [notes] that say what the bot gets and what Telegram checks or does on
 * sharing; the candidates the button's restrictions let through ([targets]); [isSharing] while Telegram is asked, and
 * [error] why the last try failed.
 */
@Immutable
data class KeyboardShareSheet(
    val kind: ShareKind,
    val botName: UiText,
    val maxCount: Int = 1,
    val selected: ImmutableSet<Long> = persistentSetOf(),
    val notes: ImmutableList<UiText> = persistentListOf(),
    val targets: ImmutableList<ForwardTarget> = persistentListOf(),
    val isSearching: Boolean = false,
    val isLoading: Boolean = false,
    val searchError: DomainError? = null,
    val isSharing: Boolean = false,
    val error: DomainError? = null,
) {
    override fun toString() =
        "KeyboardShareSheet(kind=$kind, selected=${selected.size}, targets=${targets.size}, isSharing=$isSharing)"
}
