package dev.kyralabs.pingogram.core.domain.model

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/**
 * The bots of a chat as its conversation offers them (`BotInfoRepository.chatBots`): the commands they take, and for a
 * private chat with a bot what the bot shows before the first message.
 *
 * @property commands every command of the chat's bots, in TDLib's order: a private chat's bot's (td_api.tl
 *   `userFullInfo.bot_info.commands`), a group's bot by bot (`basicGroupFullInfo.bot_commands`,
 *   `supergroupFullInfo.bot_commands`); empty where no bot has commands.
 * @property bot the bot of a private chat with a bot; null in groups, and with people.
 */
data class ChatBots(
    val commands: ImmutableList<BotCommand>,
    val bot: BotInfo?,
) {
    companion object {
        /** A chat without bots, or whose bots offer nothing. */
        val None = ChatBots(persistentListOf(), bot = null)
    }
}

/**
 * What bot [botUserId] shows in its private chat (td_api.tl `botInfo`): [description], "What can this bot do?", shown
 * in the empty chat with its [photo] or [animation] (null without); [menuButton], the button the bot puts in place of
 * its commands' menu, null where the menu is its commands.
 *
 * `toString()` leaves the description out.
 */
data class BotInfo(
    val botUserId: Long,
    val description: String,
    val photo: Photo?,
    val animation: Animation?,
    val menuButton: BotMenuButton?,
) {
    override fun toString() = "BotInfo(botUserId=$botUserId, hasMenuButton=${menuButton != null}, …)"
}

/**
 * A command a bot takes (td_api.tl `botCommand`): [command] without its "/", what it does ([description]), and the bot
 * that takes it ([botUserId]), which a group's commands name after the command ("/start@bot").
 */
data class BotCommand(
    val command: String,
    val description: String,
    val botUserId: Long,
)

/**
 * The button a bot shows in place of its commands' menu (td_api.tl `botMenuButton`): its [text], and the [url] of the
 * Mini App (Web App) it opens, which Pingogram does not open yet.
 *
 * `toString()` leaves the address out.
 */
data class BotMenuButton(
    val text: String,
    val url: String,
) {
    override fun toString() = "BotMenuButton(…)"
}
