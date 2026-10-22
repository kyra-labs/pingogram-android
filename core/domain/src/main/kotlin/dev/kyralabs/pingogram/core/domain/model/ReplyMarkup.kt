package dev.kyralabs.pingogram.core.domain.model

import kotlinx.collections.immutable.ImmutableList

/**
 * The buttons a bot attaches to a message (td_api.tl `message.reply_markup`), mapped by `core:data` with the message
 * and kept current through `updateMessageEdited`, which carries the new markup when a bot changes it.
 *
 * - [InlineKeyboard]: rows of buttons under the message itself, which act on it ([InlineKeyboardButton]).
 * - [ShowKeyboard]: a keyboard of the bot's that replaces the system keyboard; its buttons send text or share something
 *   with the bot ([KeyboardButton]).
 * - [RemoveKeyboard]: hides the bot's keyboard of an earlier message.
 * - [ForceReply]: asks the user to reply to this message.
 *
 * Only incoming messages of bots carry the last three; a message sent through an inline bot can carry an inline
 * keyboard too.
 */
sealed interface ReplyMarkup {
    /**
     * Buttons under the message, [rows] from top to bottom, each row's buttons from the start; no row is empty.
     */
    data class InlineKeyboard(
        val rows: ImmutableList<ImmutableList<InlineKeyboardButton>>,
    ) : ReplyMarkup

    /**
     * A keyboard of the bot's in place of the system keyboard while the chat is open: its [rows]; [resize] if it may be
     * as short as its buttons need (else as tall as the system keyboard); [oneTime] if it hides once a button was
     * used; [persistent] if it stays shown while the user types; [personal] if it is meant for the users the message
     * mentions or replies to only; [placeholder] the hint of the text field while it shows, empty for the usual one.
     */
    data class ShowKeyboard(
        val rows: ImmutableList<ImmutableList<KeyboardButton>>,
        val resize: Boolean,
        val oneTime: Boolean,
        val persistent: Boolean,
        val personal: Boolean,
        val placeholder: String,
    ) : ReplyMarkup

    /** Hides the bot's keyboard; [personal] if only for the users the message mentions or replies to. */
    data class RemoveKeyboard(
        val personal: Boolean,
    ) : ReplyMarkup

    /**
     * Asks the user to reply to this message, as if they had chosen "Reply": [personal] if only the users the message
     * mentions or replies to; [placeholder] the hint of the text field meanwhile, empty for the usual one.
     */
    data class ForceReply(
        val personal: Boolean,
        val placeholder: String,
    ) : ReplyMarkup
}

/** How a bot asks a button to look (td_api.tl `ButtonStyle`); [Default] for a style a newer TDLib adds. */
enum class BotButtonStyle {
    Default,
    Primary,
    Danger,
    Success,
    Link,
}

/** A button under a message ([ReplyMarkup.InlineKeyboard]): its [text], its [style] and what tapping it does. */
data class InlineKeyboardButton(
    val text: String,
    val style: BotButtonStyle,
    val type: InlineKeyboardButtonType,
) {
    override fun toString() = "InlineKeyboardButton(type=$type, …)"
}

/** What tapping an [InlineKeyboardButton] does (td_api.tl `InlineKeyboardButtonType`). */
sealed interface InlineKeyboardButtonType {
    /** Opens [url], a web page or a Telegram link. */
    data class Url(
        val url: String,
    ) : InlineKeyboardButtonType {
        override fun toString() = "Url(…)"
    }

    /**
     * Opens [url] with the user logged in to its site through Telegram, once the user agreed
     * (`BotButtonRepository.loginUrlInfo`); [id] names the button there. [forwardText] is its text in a forwarded copy.
     */
    data class LoginUrl(
        val url: String,
        val id: Long,
        val forwardText: String,
    ) : InlineKeyboardButtonType {
        override fun toString() = "LoginUrl(id=$id, …)"
    }

    /** Sends [data] to the bot, which may answer with a notice, an alert or a link (`BotButtonRepository.answer`). */
    data class Callback(
        val data: CallbackData,
    ) : InlineKeyboardButtonType

    /** As [Callback], once the user confirmed it with their 2-step verification password. */
    data class CallbackWithPassword(
        val data: CallbackData,
    ) : InlineKeyboardButtonType

    /**
     * Starts an inline query of the bot that sent the message, "@bot [query]", in the composer of a chat [target]
     * says.
     */
    data class SwitchInline(
        val query: String,
        val target: SwitchInlineTarget,
    ) : InlineKeyboardButtonType {
        override fun toString() = "SwitchInline(target=$target, …)"
    }

    /** Copies [text] to the clipboard. */
    data class CopyText(
        val text: String,
    ) : InlineKeyboardButtonType {
        override fun toString() = "CopyText(…)"
    }

    /** Opens the profile of user [userId]. */
    data class User(
        val userId: Long,
    ) : InlineKeyboardButtonType

    /** Opens the bot's Mini App at [url], which Pingogram does not show yet. */
    data class WebApp(
        val url: String,
    ) : InlineKeyboardButtonType {
        override fun toString() = "WebApp(…)"
    }

    /** Opens the bot's game of the message, which Pingogram does not show yet. */
    data object CallbackGame : InlineKeyboardButtonType

    /** Pays the invoice of the message, which Pingogram does not offer. */
    data object Buy : InlineKeyboardButtonType

    /** Does nothing: a button the bot turned off, or one of a kind a newer TDLib adds. */
    data object Disabled : InlineKeyboardButtonType
}

/**
 * The data a callback button sends to its bot, as TDLib gave it. It compares by content, so equal messages stay equal;
 * nothing writes to [bytes] after mapping.
 */
class CallbackData(
    val bytes: ByteArray,
) {
    override fun equals(other: Any?): Boolean = other is CallbackData && bytes.contentEquals(other.bytes)

    override fun hashCode(): Int = bytes.contentHashCode()

    override fun toString() = "CallbackData(size=${bytes.size})"
}

/** Where a [InlineKeyboardButtonType.SwitchInline] button starts its inline query. */
sealed interface SwitchInlineTarget {
    /** In the chat of the message. */
    data object CurrentChat : SwitchInlineTarget

    /**
     * In a chat the user picks, among those the bot allows: private chats with users ([users]) or bots ([bots]),
     * groups ([groups]) and channels ([channels]).
     */
    data class ChosenChat(
        val users: Boolean,
        val bots: Boolean,
        val groups: Boolean,
        val channels: Boolean,
    ) : SwitchInlineTarget

    /** In a chat a Telegram link names (td_api.tl `targetChatInternalLink`), or a target a newer TDLib adds. */
    data object Link : SwitchInlineTarget
}

/** A button of a bot's keyboard ([ReplyMarkup.ShowKeyboard]): its [text], its [style] and what tapping it does. */
data class KeyboardButton(
    val text: String,
    val style: BotButtonStyle,
    val type: KeyboardButtonType,
) {
    override fun toString() = "KeyboardButton(type=$type, …)"
}

/**
 * What tapping a [KeyboardButton] does (td_api.tl `KeyboardButtonType`). The requests share something with the bot
 * only once the user chose it and agreed (`BotButtonRepository.shareUsers`, `shareChat`).
 */
sealed interface KeyboardButtonType {
    /** Sends the button's text as a message. */
    data object Text : KeyboardButtonType

    /** Shares the user's phone number with the bot, once they agreed. */
    data object RequestPhoneNumber : KeyboardButtonType

    /** Shares the user's location with the bot, once they agreed. */
    data object RequestLocation : KeyboardButtonType

    /** Lets the user create a poll for the bot: a quiz if [quiz], a regular poll if false, either if null. */
    data class RequestPoll(
        val quiz: Boolean?,
    ) : KeyboardButtonType

    /**
     * Lets the user pick up to [maxQuantity] users and share them with the bot, as button [id]: only bots, or no bots,
     * as [isBot] says (either if null), and only users with or without Telegram Premium as [isPremium] says; their
     * name, username and photo too where [requestName], [requestUsername], [requestPhoto].
     */
    data class RequestUsers(
        val id: Int,
        val isBot: Boolean?,
        val isPremium: Boolean?,
        val maxQuantity: Int,
        val requestName: Boolean,
        val requestUsername: Boolean,
        val requestPhoto: Boolean,
    ) : KeyboardButtonType

    /**
     * Lets the user pick a chat and share it with the bot, as button [id]: a channel if [isChannel], else a group; a
     * forum or not ([isForum]) and with a username or not ([hasUsername]), either if null; only one the user created
     * if [isCreated]. The user (and the bot, if [botIsMember]) must have [userRights] ([botRights]) there, if not null.
     * Its title, username and photo are shared too where [requestTitle], [requestUsername], [requestPhoto].
     */
    data class RequestChat(
        val id: Int,
        val isChannel: Boolean,
        val isForum: Boolean?,
        val hasUsername: Boolean?,
        val isCreated: Boolean,
        val userRights: AdministratorRights?,
        val botRights: AdministratorRights?,
        val botIsMember: Boolean,
        val requestTitle: Boolean,
        val requestUsername: Boolean,
        val requestPhoto: Boolean,
    ) : KeyboardButtonType

    /** Lets the user create a bot managed by this bot, as button [id], suggesting [suggestedName] and its username. */
    data class RequestManagedBot(
        val id: Int,
        val suggestedName: String,
        val suggestedUsername: String,
    ) : KeyboardButtonType

    /** Opens the bot's Mini App at [url], which Pingogram does not show yet. */
    data class WebApp(
        val url: String,
    ) : KeyboardButtonType {
        override fun toString() = "WebApp(…)"
    }

    /** A kind a newer TDLib adds: does nothing. */
    data object Unsupported : KeyboardButtonType
}

/** What a callback button sends its bot (td_api.tl `CallbackQueryPayload`). */
sealed interface CallbackPayload {
    /** A callback button's [data]. */
    data class Data(
        val data: CallbackData,
    ) : CallbackPayload

    /** A callback button's [data] with the user's 2-step verification [password], which `toString()` leaves out. */
    data class DataWithPassword(
        val password: String,
        val data: CallbackData,
    ) : CallbackPayload {
        override fun toString() = "DataWithPassword(…)"
    }

    /** A game button's game, by its [shortName]. */
    data class Game(
        val shortName: String,
    ) : CallbackPayload
}

/** How a bot answered a callback button (td_api.tl `callbackQueryAnswer`). */
sealed interface CallbackAnswer {
    /**
     * The bot's [text] (empty for none) as a notice, or as an alert the user dismisses if [showAlert]; [url] (empty
     * for none) to open.
     */
    data class Answered(
        val text: String,
        val showAlert: Boolean,
        val url: String,
    ) : CallbackAnswer {
        override fun toString() = "Answered(showAlert=$showAlert, …)"
    }

    /** The bot did not answer in time, which bots may do: nothing to show. */
    data object NoAnswer : CallbackAnswer
}

/** What opening a login button needs (td_api.tl `LoginUrlInfo`). */
sealed interface LoginUrlInfo {
    /** Open [url] as it is, after asking the user to confirm the link unless [skipConfirmation]. */
    data class Open(
        val url: String,
        val skipConfirmation: Boolean,
    ) : LoginUrlInfo {
        override fun toString() = "Open(skipConfirmation=$skipConfirmation, …)"
    }

    /**
     * Ask the user whether to log in to [domain] as their Telegram account for bot [botUserId], opening [url] either
     * way; if [requestWriteAccess], whether the bot may then send them messages too.
     */
    data class RequestConfirmation(
        val url: String,
        val domain: String,
        val botUserId: Long,
        val requestWriteAccess: Boolean,
    ) : LoginUrlInfo {
        override fun toString() = "RequestConfirmation(botUserId=$botUserId, requestWriteAccess=$requestWriteAccess, …)"
    }
}
