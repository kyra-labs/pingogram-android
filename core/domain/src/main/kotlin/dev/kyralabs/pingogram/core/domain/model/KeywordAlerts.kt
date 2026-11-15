package dev.kyralabs.pingogram.core.domain.model

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentListOf

/**
 * Keyword alerts (board 30), kept on this phone only and never sent to Telegram: the [words] that alert when a message
 * has one (whole words, any letter case, with or without accents; at most [MAX_WORDS], owner decision 2026-11-15),
 * [where] to look for them, whether they alert also from muted chats ([alertWhenMuted]), and whether the chat list has
 * a "Keyword alerts" tab of the chats with a recent match ([showInChatList]).
 */
data class KeywordAlertSettings(
    val words: ImmutableList<String> = persistentListOf(),
    val where: KeywordScope = KeywordScope.AllChats,
    val alertWhenMuted: Boolean = true,
    val showInChatList: Boolean = false,
) {
    override fun toString() = "KeywordAlertSettings(words=${words.size}, where=$where, …)"

    companion object {
        /** The most words the user keeps. */
        const val MAX_WORDS = 20

        /** The longest word or phrase. */
        const val MAX_WORD_LENGTH = 64
    }
}

/** Where keyword alerts look: every chat, the chats of some folders, or only muted chats. */
sealed interface KeywordScope {
    data object AllChats : KeywordScope

    /** The chats of the chat folders [folderIds] (TDLib's `chat_folder_id`). */
    data class Folders(
        val folderIds: ImmutableSet<Int>,
    ) : KeywordScope

    data object MutedChats : KeywordScope
}
