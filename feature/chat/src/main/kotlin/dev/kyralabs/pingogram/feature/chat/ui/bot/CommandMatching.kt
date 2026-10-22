package dev.kyralabs.pingogram.feature.chat.ui.bot

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import java.util.Locale

/**
 * The command being typed in [text], for the suggestions above the composer: what follows its "/", where the text is a
 * "/" and then only what a command can hold (Latin letters, digits and "_", up to [MAX_COMMAND_LENGTH]), with an
 * "@bot" after it in a group ("/start@hel"); null for any other text, so the suggestions go once a space or a second
 * line starts the command's arguments.
 */
internal fun commandQuery(text: CharSequence): String? = COMMAND_BEING_TYPED.matchEntire(text)?.groupValues?.get(1)

/**
 * The commands that [query] (from [commandQuery]) starts, in their order, ignoring case: "sta" starts "/start", and
 * "start@hel" starts "/start@helper_bot".
 */
internal fun List<BotCommandItem>.startingWith(query: String): ImmutableList<BotCommandItem> {
    val prefix = query.lowercase(Locale.ROOT)
    return filter {
        it.text
            .drop(1)
            .lowercase(Locale.ROOT)
            .startsWith(prefix)
    }.toImmutableList()
}

/**
 * The commands of the sheet whose name or description holds [query], ignoring case and a leading "/"; all of them for
 * a blank query.
 */
internal fun List<BotCommandItem>.searching(query: String): ImmutableList<BotCommandItem> {
    val words = query.trim().removePrefix("/")
    if (words.isEmpty()) return toImmutableList()
    return filter { it.command.contains(words, ignoreCase = true) || it.description.contains(words, ignoreCase = true) }
        .toImmutableList()
}

/** Telegram's limit on a command's name (td_api.tl `botCommand`: 1–32 characters). */
private const val MAX_COMMAND_LENGTH = 32

/** "/", a command's characters, and optionally "@" and a bot's username being typed. */
private val COMMAND_BEING_TYPED = Regex("/([A-Za-z0-9_]{0,$MAX_COMMAND_LENGTH}(?:@[A-Za-z0-9_]*)?)")
