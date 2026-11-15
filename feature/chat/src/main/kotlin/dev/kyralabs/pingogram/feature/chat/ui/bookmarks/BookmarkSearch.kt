package dev.kyralabs.pingogram.feature.chat.ui.bookmarks

import dev.kyralabs.pingogram.core.domain.model.Bookmark
import dev.kyralabs.pingogram.core.domain.model.BookmarkTag
import dev.kyralabs.pingogram.core.domain.model.Chat
import java.text.Normalizer
import java.util.Locale

/**
 * The Bookmarks page's search (the owner's board 34b), on the phone: a bookmark matches when each word typed is found
 * in its chat's name, who sent it, what it said, its note or its tags' names, in any letter case and with or without
 * accents.
 */
internal class BookmarkSearch private constructor(
    private val words: List<String>,
    private val locale: Locale,
) {
    /** Whether [bookmark], of [chat] (null while TDLib does not know it), with its tags from [tags], has every word. */
    fun matches(
        bookmark: Bookmark,
        chat: Chat?,
        tags: Map<Long, BookmarkTag>,
    ): Boolean {
        if (words.isEmpty()) return true
        val haystack =
            buildList {
                chat?.title?.let(::add)
                add(bookmark.preview.sender)
                add(bookmark.preview.text)
                add(bookmark.note)
                bookmark.tagIds.mapNotNullTo(this) { tags[it]?.name }
            }.joinToString(SEPARATOR) { fold(it, locale) }
        return words.all { it in haystack }
    }

    companion object {
        /** The search of [query]'s words, in [locale]'s letter case; every bookmark matches an empty one. */
        fun of(
            query: String,
            locale: Locale,
        ): BookmarkSearch =
            BookmarkSearch(
                words = fold(query, locale).split(WHITESPACE).filter { it.isNotEmpty() },
                locale = locale,
            )
    }
}

/** [text] without its accents (its combining marks), in lower case. */
private fun fold(
    text: String,
    locale: Locale,
): String =
    Normalizer
        .normalize(text, Normalizer.Form.NFD)
        .replace(COMBINING_MARKS, "")
        .lowercase(locale)

private val COMBINING_MARKS = Regex("\\p{Mn}+")
private val WHITESPACE = Regex("\\s+")

// Keeps a word from running across two fields.
private const val SEPARATOR = "\n"
