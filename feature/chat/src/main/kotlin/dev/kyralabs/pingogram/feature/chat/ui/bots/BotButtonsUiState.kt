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
 * The buttons a bot attached under message [messageId] (td_api.tl `replyMarkupInlineKeyboard`), as the conversation
 * shows them below its bubble: [rows] from top to bottom, each from the start. [pending] are the buttons whose
 * request is on its way (a callback, a login link), each showing its progress.
 */
@Immutable
data class BotKeyboard(
    val messageId: Long,
    val rows: ImmutableList<ImmutableList<BotKeyboardButton>>,
    val pending: ImmutableSet<BotButtonPosition> = persistentSetOf(),
)

/**
 * One button of a [BotKeyboard]: its [text] (the bot's, user content), the small trailing [mark] that says it leaves
 * the conversation, its [tone], and whether it does anything ([isEnabled]).
 */
@Immutable
data class BotKeyboardButton(
    val text: String,
    val mark: BotButtonMark = BotButtonMark.None,
    val tone: BotButtonTone = BotButtonTone.Tonal,
    val isEnabled: Boolean = true,
) {
    override fun toString() = "BotKeyboardButton(mark=$mark, tone=$tone, isEnabled=$isEnabled)"
}

/** Where a button is in its keyboard: its [row] and its [column] in that row, from 0. */
@Immutable
data class BotButtonPosition(
    val row: Int,
    val column: Int,
)

/** The trailing icon of a button that leaves the conversation. */
enum class BotButtonMark {
    None,

    /** Opens a link: a web page or a login. */
    Link,

    /** Starts an inline query of the bot in a composer ("@bot query"). */
    SwitchInline,
}

/**
 * The colors of a button, from the bot's style: [Tonal] (`secondaryContainer`) for most, [Primary] for a bot's primary
 * action, [Danger] (`errorContainer`) for a destructive one.
 */
enum class BotButtonTone {
    Tonal,
    Primary,
    Danger,
}

/** What shows over the conversation after a bot's button was tapped, one at a time. */
@Immutable
sealed interface BotButtonDialog {
    /** The bot answered a callback with an alert: its [text], which the user dismisses. */
    data class Alert(
        val text: String,
    ) : BotButtonDialog {
        override fun toString() = "Alert(…)"
    }

    /** A link button's [url], a web page, whose button shows other text: opened only once the user confirmed it. */
    data class OpenLink(
        val url: String,
    ) : BotButtonDialog {
        override fun toString() = "OpenLink(…)"
    }

    /**
     * A login button asks whether to log in to [domain] as the user ([myName]) for bot [botName], and, if
     * [requestWriteAccess], whether the bot may send them messages; [isLoggingIn] while Telegram makes the link.
     */
    data class Login(
        val domain: String,
        val botName: UiText,
        val myName: UiText,
        val requestWriteAccess: Boolean,
        val isLoggingIn: Boolean = false,
    ) : BotButtonDialog {
        override fun toString() = "Login(requestWriteAccess=$requestWriteAccess, isLoggingIn=$isLoggingIn, …)"
    }

    /**
     * A button that needs the user's 2-step verification password, for bot [botName]: [isChecking] while Telegram
     * checks it, [error] why the last try failed (null before one).
     */
    data class Password(
        val botName: UiText,
        val isChecking: Boolean = false,
        val error: DomainError? = null,
    ) : BotButtonDialog {
        override fun toString() = "Password(isChecking=$isChecking, error=$error)"
    }
}

/**
 * The chat picker of a switch-inline button (`docs/DESIGN_SYSTEM.md` §6 "Bot buttons"): the inline [query] it puts
 * into the composer of the chat the user picks ("@bot cats"), and the chats the bot allows, as the forward sheet lists
 * them.
 */
@Immutable
data class SwitchInlineSheet(
    val query: String,
    val targets: ImmutableList<ForwardTarget> = persistentListOf(),
    val isSearching: Boolean = false,
    val isLoading: Boolean = false,
    val searchError: DomainError? = null,
) {
    override fun toString() = "SwitchInlineSheet(targets=${targets.size}, isLoading=$isLoading)"
}
