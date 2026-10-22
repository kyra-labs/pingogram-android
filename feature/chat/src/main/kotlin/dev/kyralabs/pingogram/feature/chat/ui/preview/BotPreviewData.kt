package dev.kyralabs.pingogram.feature.chat.ui.preview

import dev.kyralabs.pingogram.core.domain.model.FileRef
import dev.kyralabs.pingogram.core.domain.model.Photo
import dev.kyralabs.pingogram.core.domain.model.PhotoSize
import dev.kyralabs.pingogram.core.ui.text.UiText
import dev.kyralabs.pingogram.feature.chat.R
import dev.kyralabs.pingogram.feature.chat.ui.ChatContent
import dev.kyralabs.pingogram.feature.chat.ui.ChatHeader
import dev.kyralabs.pingogram.feature.chat.ui.ChatSubtitle
import dev.kyralabs.pingogram.feature.chat.ui.ChatUiState
import dev.kyralabs.pingogram.feature.chat.ui.bot.BotCommandItem
import dev.kyralabs.pingogram.feature.chat.ui.bot.BotCommandsSheet
import dev.kyralabs.pingogram.feature.chat.ui.bot.BotIntro
import dev.kyralabs.pingogram.feature.chat.ui.bot.CommandBot
import dev.kyralabs.pingogram.feature.chat.ui.bot.CommandSuggestions
import dev.kyralabs.pingogram.feature.chat.ui.bot.ComposerBotButton
import dev.kyralabs.pingogram.feature.chat.ui.bot.startingWith
import dev.kyralabs.pingogram.feature.chat.ui.composer.ComposerUiState
import kotlinx.collections.immutable.persistentListOf

/**
 * Bots for previews and screenshot tests: a sticker bot's commands in its private chat, two bots' commands in a group,
 * the suggestions while "/st" is typed, the bot's intro in its empty chat, and that chat with "Start".
 */
internal object BotPreviewData {
    private const val STICKERS_BOT = 401L
    private const val DICE_BOT = 402L
    private const val INTRO_FILE_ID = 4_100
    private const val INTRO_WIDTH = 640
    private const val INTRO_HEIGHT = 360

    /** The attach options of a private chat. */
    val attachOptions = ChatPreviewData.canWrite.attachOptions

    /** The sticker bot's commands, in its private chat. */
    val commands =
        persistentListOf(
            command("start", "Start the bot"),
            command("stickers", "Find stickers for a word"),
            command("settings", "Change the language and safe search"),
            command("help", "What I can do"),
        )

    private val stickers = CommandBot(STICKERS_BOT, UiText.Plain("Sticker Finder"), photo = null)
    private val dice = CommandBot(DICE_BOT, UiText.Plain("Dice Roller"), photo = null)

    /** Two bots' commands in a group, each addressed to its bot. */
    val groupCommands =
        persistentListOf(
            command("start", "Start the bot", bot = stickers, username = "sticker_finder_bot"),
            command("stickers", "Find stickers for a word", bot = stickers, username = "sticker_finder_bot"),
            command("roll", "Roll a die", bot = dice, username = "dice_roller_bot"),
            command("stats", "Who rolled the most sixes", bot = dice, username = "dice_roller_bot"),
        )

    val sheet = BotCommandsSheet(commands)

    val groupSheet = BotCommandsSheet(groupCommands)

    /** While "/st" is typed. */
    val suggestions = CommandSuggestions("st", commands.startingWith("st"))

    /** While "/s" is typed in the group. */
    val groupSuggestions = CommandSuggestions("s", groupCommands.startingWith("s"))

    /** The bot's photo and description. */
    val intro =
        BotIntro(
            description =
                "I find stickers for any word. Send me a word or an emoji, or type @sticker_finder_bot " +
                    "in any chat to search from there.",
            photo =
                Photo(
                    sizes = persistentListOf(PhotoSize("x", INTRO_WIDTH, INTRO_HEIGHT, FileRef(INTRO_FILE_ID, 0))),
                    minithumbnail = null,
                ),
            animation = null,
        )

    /** A description without a picture. */
    val textIntro = intro.copy(photo = null)

    /** The empty chat with the sticker bot: its intro, and "Start" in the composer's place. */
    val emptyBotChat =
        ChatUiState(
            header =
                ChatHeader(
                    chatId = STICKERS_BOT,
                    title = UiText.Plain("Sticker Finder"),
                    photo = null,
                    placeholderIcon = null,
                    subtitle = ChatSubtitle.Status(UiText.resource(R.string.chat_subtitle_bot)),
                    profileUserId = STICKERS_BOT,
                ),
            content = ChatContent.Empty,
            composer = ComposerUiState.BotStart(),
            botIntro = intro,
        )

    /** The chat with the bot once started: the composer with the commands button. */
    val botChat =
        ChatPreviewData.privateChat.copy(
            header = emptyBotChat.header,
            composer = ChatPreviewData.canWrite.copy(bot = ComposerBotButton.Commands),
        )

    private fun command(
        name: String,
        description: String,
        bot: CommandBot? = null,
        username: String? = null,
    ) = BotCommandItem(
        command = name,
        text = if (username != null) "/$name@$username" else "/$name",
        description = description,
        bot = bot,
    )
}
