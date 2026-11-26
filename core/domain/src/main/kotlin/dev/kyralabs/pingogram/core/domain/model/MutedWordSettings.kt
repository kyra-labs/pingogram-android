package dev.kyralabs.pingogram.core.domain.model

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/**
 * Muted words (`REQUIREMENTS.md` FR-X21), kept on this phone and never sent to Telegram: while [enabled], a group's
 * messages with one of the [words] (whole words or phrases, any letter case, with or without accents; within the text
 * in scripts written without spaces) collapse into a line the user taps to show. At most [MAX_WORDS] of at most
 * [MAX_WORD_LENGTH] characters; words can be added while it is off.
 */
data class MutedWordSettings(
    val enabled: Boolean = false,
    val words: ImmutableList<String> = persistentListOf(),
) {
    override fun toString() = "MutedWordSettings(enabled=$enabled, words=${words.size})"

    companion object {
        /** The most words or phrases the user keeps. */
        const val MAX_WORDS = 100

        /** The longest word or phrase. */
        const val MAX_WORD_LENGTH = 64
    }
}
