package dev.kyralabs.pingogram.feature.chats.ui

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.domain.model.MessageSearchFilter
import dev.kyralabs.pingogram.core.domain.model.ProfilePhoto
import dev.kyralabs.pingogram.core.ui.text.MatchHighlight
import dev.kyralabs.pingogram.core.ui.text.UiText
import dev.kyralabs.pingogram.feature.chats.R
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/**
 * Everything global search shows below its search field (`REQUIREMENTS.md` FR-L6): the [filter] chosen with the chips
 * under the field, then suggestions while the query is empty, the results of the query otherwise. The query itself
 * lives in the search field.
 */
@Immutable
data class SearchUiState(
    val content: SearchContent = SearchContent.Suggestions(),
    val filter: SearchFilter = SearchFilter.All,
)

/**
 * The filter chips under the search field (`docs/DESIGN_SYSTEM.md` §6, "Global search"), [label]led, each choosing the
 * sections search shows: every section, chats (the user's and public ones), contacts, or messages, all of them or only
 * photos and videos ([Media]) or links ([Links]). Media and links show even without a query: every one in the chats.
 */
enum class SearchFilter(
    @param:StringRes val label: Int,
) {
    All(R.string.search_filter_all),
    Chats(R.string.search_filter_chats),
    Contacts(R.string.search_filter_contacts),
    Messages(R.string.search_filter_messages),
    Media(R.string.search_filter_media),
    Links(R.string.search_filter_links),
    ;

    /** Searches the user's chats and public chats. */
    val searchesChats: Boolean get() = this == All || this == Chats

    /** Searches the contacts. */
    val searchesContacts: Boolean get() = this == All || this == Contacts

    /** Which messages it searches; null for none. */
    val messages: MessageSearchFilter?
        get() =
            when (this) {
                All, Messages -> MessageSearchFilter.All
                Media -> MessageSearchFilter.PhotoAndVideo
                Links -> MessageSearchFilter.Url
                Chats, Contacts -> null
            }

    /** Finds something without a query too: every photo and video, or every link. */
    val searchesWithoutQuery: Boolean get() = this == Media || this == Links

    companion object {
        /** The filter named [name], as saved; [All] for anything else. */
        fun of(name: String?): SearchFilter = entries.firstOrNull { it.name == name } ?: All
    }
}

/** What search shows. */
@Immutable
sealed interface SearchContent {
    /** Something is still being read or searched: the progress indicator under the field shows. */
    val isSearching: Boolean

    /**
     * Before anything is typed: the people the user writes to most ([topPeople]) and the chats opened from search
     * before ([recents], the last one first), each empty when there are none; [isLoading] until both were read.
     */
    data class Suggestions(
        val topPeople: ImmutableList<SearchChatItem> = persistentListOf(),
        val recents: ImmutableList<SearchChatItem> = persistentListOf(),
        val isLoading: Boolean = true,
    ) : SearchContent {
        /** Nothing to suggest: search shows a hint instead. */
        val isEmpty: Boolean get() = !isLoading && topPeople.isEmpty() && recents.isEmpty()

        override val isSearching: Boolean get() = isLoading
    }

    /**
     * The results of [query] (trimmed, as searched; empty for every photo and video or link) under [filter], section
     * by section, each loading, failing and loading again on its own: the user's chats ([chats]), contacts not among
     * them ([contacts]), public chats and channels found by username that are neither ([global]), and the messages
     * that match ([messages]). A section the filter leaves out found nothing. [highlight] marks the query's words in
     * the messages' text.
     */
    data class Results(
        val query: String,
        val highlight: MatchHighlight,
        val chats: SearchSection<SearchChatItem>,
        val contacts: SearchSection<SearchChatItem>,
        val global: SearchSection<SearchChatItem>,
        val messages: MessageSection,
        val filter: SearchFilter = SearchFilter.All,
    ) : SearchContent {
        private val sections get() = listOf(chats, contacts, global, messages.first)

        /** Every section searched and found nothing: search says so instead of listing sections. */
        val isEmpty: Boolean get() = sections.all { it is SearchSection.Items && it.items.isEmpty() }

        /** How many results the sections hold, for screen readers; null while a section still searches. */
        val resultCount: Int?
            get() =
                if (sections.any { it is SearchSection.Loading }) {
                    null
                } else {
                    sections.sumOf { (it as? SearchSection.Items)?.items?.size ?: 0 }
                }

        override val isSearching: Boolean
            get() = sections.any { it is SearchSection.Loading } || messages.isLoadingMore

        override fun toString() = "Results(chats=$chats, contacts=$contacts, global=$global, messages=$messages, …)"
    }
}

/** A section of the results. */
@Immutable
sealed interface SearchSection<out T> {
    /** Still searching, nothing found yet. */
    data object Loading : SearchSection<Nothing>

    /** The search failed with [error], shown with a retry ([SearchEvent.Retry]). */
    data class Failed(
        val error: DomainError,
    ) : SearchSection<Nothing>

    /** What was found, in the order to show; empty hides the section. */
    data class Items<T>(
        val items: ImmutableList<T>,
    ) : SearchSection<T> {
        override fun toString() = "Items(${items.size})"
    }
}

/**
 * The messages that match, a page at a time: [first] is the first page's state, then every page loaded so far. More
 * load as the list nears its end while [canLoadMore]; [isLoadingMore] while a page loads; [moreError] for a page that
 * failed, retried only by [SearchEvent.Retry].
 */
@Immutable
data class MessageSection(
    val first: SearchSection<SearchMessageItem> = SearchSection.Loading,
    val canLoadMore: Boolean = false,
    val isLoadingMore: Boolean = false,
    val moreError: DomainError? = null,
)

/**
 * A chat, a contact or a public chat in search, mapped as the chat list maps its rows (`ChatListItemMapper`), in one
 * or two lines: no last message, unread counts or time.
 *
 * @property id the chat's id; a private chat's is its user's.
 * @property title the chat's name; "Saved Messages" and "Deleted Account" come from resources.
 * @property photo the avatar's photo; null shows the placeholder.
 * @property placeholderIcon a Material Symbol on the avatar placeholder instead of initials.
 * @property isOnline a private chat whose user is online now (not bots).
 * @property isSecret an end-to-end encrypted chat, shown with a lock.
 * @property subtitle the second line: the username ("@ada"), else how many members or subscribers a group or channel
 *   has; null for none.
 */
@Immutable
data class SearchChatItem(
    val id: Long,
    val title: UiText,
    val photo: ProfilePhoto?,
    @param:DrawableRes val placeholderIcon: Int?,
    val isOnline: Boolean,
    val badge: TitleBadge?,
    val isSecret: Boolean,
    val isMuted: Boolean,
    val subtitle: UiText?,
) {
    override fun toString() = "SearchChatItem(id=$id, …)"
}

/**
 * A message that matches: its chat's avatar and name, who sent it in a group, its text around the first match, and
 * when it was sent.
 *
 * @property chatId the message's chat; with [messageId] the row's key.
 * @property sender "You" or the sender's first name in a group; null in private chats and channels.
 * @property preview the message as one line, starting shortly before the first match when that is far into the text.
 * @property date when it was sent, formatted as in the chat list.
 */
@Immutable
data class SearchMessageItem(
    val chatId: Long,
    val messageId: Long,
    val chatTitle: UiText,
    val photo: ProfilePhoto?,
    @param:DrawableRes val placeholderIcon: Int?,
    val badge: TitleBadge?,
    val sender: UiText?,
    val preview: UiText,
    val date: String,
) {
    override fun toString() = "SearchMessageItem(chatId=$chatId, messageId=$messageId, …)"
}
