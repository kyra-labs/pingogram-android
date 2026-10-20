package dev.kyralabs.pingogram.feature.chats.ui.preview

import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.ui.text.UiText
import dev.kyralabs.pingogram.feature.chats.R
import dev.kyralabs.pingogram.feature.chats.ui.MatchHighlight
import dev.kyralabs.pingogram.feature.chats.ui.MessageSection
import dev.kyralabs.pingogram.feature.chats.ui.SearchChatItem
import dev.kyralabs.pingogram.feature.chats.ui.SearchContent
import dev.kyralabs.pingogram.feature.chats.ui.SearchFilter
import dev.kyralabs.pingogram.feature.chats.ui.SearchMessageItem
import dev.kyralabs.pingogram.feature.chats.ui.SearchSection
import dev.kyralabs.pingogram.feature.chats.ui.SearchUiState
import dev.kyralabs.pingogram.feature.chats.ui.TitleBadge
import kotlinx.collections.immutable.persistentListOf
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * Sample states of global search, shared by the previews and the screenshot tests: the suggestions, the hint, results
 * in every section with the query's words highlighted, a section still searching, a section that failed, a query
 * that found nothing, and the Media filter without a query, finding photos and videos or none.
 */
internal object SearchPreviewData {
    /** The query of the results. */
    const val QUERY = "ada"

    val highlight = MatchHighlight.of(QUERY)

    private fun chat(
        id: Long,
        title: String,
        subtitle: UiText? = null,
        isOnline: Boolean = false,
        badge: TitleBadge? = null,
        isMuted: Boolean = false,
    ) = SearchChatItem(
        id = id,
        title = UiText.Plain(title),
        photo = null,
        placeholderIcon = null,
        isOnline = isOnline,
        badge = badge,
        isSecret = false,
        isMuted = isMuted,
        subtitle = subtitle,
    )

    private fun username(name: String) = UiText.resource(R.string.search_username, name)

    /** [count] members, written out as [written] ("1,204"). */
    private fun members(
        count: Int,
        written: String,
    ) = UiText.plural(R.plurals.search_members, count, written)

    private fun message(
        chatId: Long,
        messageId: Long,
        chatTitle: String,
        preview: UiText,
        date: String,
        sender: String? = null,
        badge: TitleBadge? = null,
    ) = SearchMessageItem(
        chatId = chatId,
        messageId = messageId,
        chatTitle = UiText.Plain(chatTitle),
        photo = null,
        placeholderIcon = null,
        badge = badge,
        sender = sender?.let(UiText::Plain),
        preview = preview,
        date = date,
    )

    val chats =
        persistentListOf(
            chat(2, "Ada Lovelace", username("ada"), isOnline = true),
            chat(-100_3, "Ada's Book Club", members(1_204, "1,204"), isMuted = true),
        )

    private val contacts = persistentListOf(chat(4, "Adam Sedgwick", username("sedgwick")))

    private val global =
        persistentListOf(chat(-100_5, "Ada Developers", username("adadevs"), badge = TitleBadge.Verified))

    val messages =
        persistentListOf(
            message(
                6,
                61,
                "Charles Babbage",
                UiText.Plain("Lady Ada sent her notes on the Bernoulli numbers"),
                "9:41 AM",
            ),
            message(
                chatId = -100_3,
                messageId = 31,
                chatTitle = "Ada's Book Club",
                preview = UiText.Plain("…next month we read the letters Ada wrote to Babbage about the engine"),
                date = "Mon",
                sender = "Grace",
            ),
            message(
                chatId = -100_7,
                messageId = 71,
                chatTitle = "Analytical Society",
                preview =
                    UiText.resource(
                        UiR.string.preview_with_text,
                        UiText.resource(UiR.string.preview_photo),
                        UiText.Plain("Portrait of Ada, 1836"),
                    ),
                date = "Mar 17",
                badge = TitleBadge.Verified,
            ),
        )

    private val recents =
        persistentListOf(
            chats.first(),
            chat(6, "Charles Babbage"),
            chat(-100_7, "Analytical Society", username("analytical"), badge = TitleBadge.Verified),
        )

    private val topPeople =
        persistentListOf(
            chat(8, "Grace Hopper", isOnline = true),
            chat(6, "Charles Babbage"),
            chat(9, "Mary Somerville"),
            chat(10, "Alan Turing"),
            chat(11, "Hedy Lamarr"),
        )

    val suggestions = SearchUiState(SearchContent.Suggestions(topPeople, recents, isLoading = false))

    val hint = SearchUiState(SearchContent.Suggestions(isLoading = false))

    val results =
        SearchUiState(
            SearchContent.Results(
                query = QUERY,
                highlight = highlight,
                chats = SearchSection.Items(chats),
                contacts = SearchSection.Items(contacts),
                global = SearchSection.Items(global),
                messages = MessageSection(SearchSection.Items(messages), canLoadMore = true),
            ),
        )

    val loading =
        SearchUiState(
            SearchContent.Results(
                query = QUERY,
                highlight = highlight,
                chats = SearchSection.Items(chats),
                contacts = SearchSection.Items(contacts),
                global = SearchSection.Loading,
                messages = MessageSection(SearchSection.Loading),
            ),
        )

    val error =
        SearchUiState(
            SearchContent.Results(
                query = QUERY,
                highlight = highlight,
                chats = SearchSection.Items(chats),
                contacts = SearchSection.Items(persistentListOf()),
                global = SearchSection.Failed(DomainError.Network),
                // The next page of messages failed too: its error ends their group.
                messages =
                    MessageSection(
                        SearchSection.Items(persistentListOf(messages.first())),
                        moreError = DomainError.Network,
                    ),
            ),
        )

    val noResults =
        SearchUiState(
            SearchContent.Results(
                query = "qwxz",
                highlight = MatchHighlight.of("qwxz"),
                chats = SearchSection.Items(persistentListOf()),
                contacts = SearchSection.Items(persistentListOf()),
                global = SearchSection.Items(persistentListOf()),
                messages = MessageSection(SearchSection.Items(persistentListOf())),
            ),
        )

    private val photos =
        persistentListOf(
            messages.last(),
            message(
                chatId = 8,
                messageId = 81,
                chatTitle = "Grace Hopper",
                preview = UiText.resource(UiR.string.preview_video),
                date = "Feb 2",
            ),
        )

    private fun media(found: SearchSection<SearchMessageItem>) =
        SearchUiState(
            content =
                SearchContent.Results(
                    query = "",
                    highlight = MatchHighlight.of(""),
                    chats = SearchSection.Items(persistentListOf()),
                    contacts = SearchSection.Items(persistentListOf()),
                    global = SearchSection.Items(persistentListOf()),
                    messages = MessageSection(found),
                    filter = SearchFilter.Media,
                ),
            filter = SearchFilter.Media,
        )

    /** The Media filter without a query: every photo and video. */
    val media = media(SearchSection.Items(photos))

    /** The Media filter without a query, in chats without photos or videos. */
    val noMedia = media(SearchSection.Items(persistentListOf()))

    /** What the search field holds for [state]: its query, none for the suggestions. */
    fun queryOf(state: SearchUiState): String = (state.content as? SearchContent.Results)?.query.orEmpty()
}
