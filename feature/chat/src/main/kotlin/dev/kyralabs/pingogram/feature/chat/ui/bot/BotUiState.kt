package dev.kyralabs.pingogram.feature.chat.ui.bot

import androidx.compose.runtime.Immutable
import dev.kyralabs.pingogram.core.domain.model.Animation
import dev.kyralabs.pingogram.core.domain.model.Photo
import dev.kyralabs.pingogram.core.domain.model.ProfilePhoto
import dev.kyralabs.pingogram.core.ui.text.UiText
import kotlinx.collections.immutable.ImmutableList

/**
 * The button at the start of the composer's pill in a chat whose bots offer something (`docs/DESIGN_SYSTEM.md` §6
 * "Composer"), in the attach button's place.
 */
@Immutable
sealed interface ComposerBotButton {
    /** "/": the commands of the chat's bots, which it opens in their sheet. */
    data object Commands : ComposerBotButton

    /**
     * The bot's menu button, named [text] by the bot ("Open app"), which opens a Mini App: Pingogram says it cannot
     * open Mini Apps yet.
     */
    data class MiniApp(
        val text: String,
    ) : ComposerBotButton {
        override fun toString() = "MiniApp(…)"
    }
}

/**
 * A command in the commands sheet and the suggestions above the composer: [command] without its "/", what sending it
 * sends ([text]: "/start", "/start@helper_bot" in a group, so only that bot takes it), what it does ([description],
 * from the bot), and in a group the [bot] that takes it.
 */
@Immutable
data class BotCommandItem(
    val command: String,
    val text: String,
    val description: String,
    val bot: CommandBot? = null,
)

/** The bot of a group's command: its avatar, by user [id], [name] and [photo]. */
@Immutable
data class CommandBot(
    val id: Long,
    val name: UiText,
    val photo: ProfilePhoto?,
) {
    override fun toString() = "CommandBot(id=$id, …)"
}

/** The commands sheet: every command of the chat's bots, which a search field filters where there are many. */
@Immutable
data class BotCommandsSheet(
    val commands: ImmutableList<BotCommandItem>,
)

/**
 * The commands suggested above the composer while the field holds a command being typed ("/sta"): [query] is what
 * follows the "/", [commands] those it starts, in the bots' order.
 */
@Immutable
data class CommandSuggestions(
    val query: String,
    val commands: ImmutableList<BotCommandItem>,
)

/**
 * What a bot shows in its empty chat (td_api.tl `botInfo`): [description], "What can this bot do?", with its [photo] or
 * [animation] above it, either may be missing.
 */
@Immutable
data class BotIntro(
    val description: String,
    val photo: Photo?,
    val animation: Animation?,
) {
    override fun toString() = "BotIntro(hasPhoto=${photo != null}, hasAnimation=${animation != null}, …)"
}
