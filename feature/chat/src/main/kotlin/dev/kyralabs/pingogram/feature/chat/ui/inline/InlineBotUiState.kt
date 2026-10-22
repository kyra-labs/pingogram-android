package dev.kyralabs.pingogram.feature.chat.ui.inline

import androidx.compose.runtime.Immutable
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.domain.model.InlineResult
import dev.kyralabs.pingogram.core.domain.model.InlineResultsButton
import kotlinx.collections.immutable.ImmutableList

/**
 * What the composer shows while its field holds an inline query to an inline bot ("@gif cats", `REQUIREMENTS.md`
 * FR-C5): the bot's [username], its [placeholder] after "@gif " while the [query] is empty ([showsPlaceholder]), and
 * its answer ([content]) above the composer.
 */
@Immutable
data class InlineBotUiState(
    val username: String,
    val placeholder: String,
    val query: String,
    val content: InlineResultsContent,
) {
    /** The placeholder shows in the field while nothing follows "@gif ", where the bot has one. */
    val showsPlaceholder: Boolean get() = query.isEmpty() && placeholder.isNotBlank()

    override fun toString() = "InlineBotUiState(username=$username, content=$content, …)"
}

/** What the area above the composer shows of an inline bot's answer. */
@Immutable
sealed interface InlineResultsContent {
    /**
     * The first answer is on its way (or the user is asked whether to share their location first): a loading
     * indicator, after a moment.
     */
    data object Loading : InlineResultsContent

    /**
     * The bot's [results], every page loaded so far, with its [button] above them (null without), in cells of
     * [cells]' size; [page] says whether more come. [isSearching] while the answer to a newer query is on its way: the
     * results shown stay until it comes.
     */
    data class Results(
        val results: ImmutableList<InlineResultEntry>,
        val button: InlineResultsButton?,
        val cells: InlineCellSize,
        val page: InlinePage,
        val isSearching: Boolean = false,
    ) : InlineResultsContent

    /** The bot found nothing for the query; its [button] (null without) still shows. */
    data class Empty(
        val button: InlineResultsButton?,
        val isSearching: Boolean = false,
    ) : InlineResultsContent

    /**
     * The answer failed with [error], with "Try again"; a [DomainError.FloodWait] counts its seconds down, and "Try
     * again" waits for it to end.
     */
    data class Failed(
        val error: DomainError,
    ) : InlineResultsContent {
        /** Whether "Try again" may ask now: not while Telegram says to wait. */
        val canRetry: Boolean get() = (error as? DomainError.FloodWait)?.let { it.seconds <= 0 } ?: true
    }
}

/**
 * A result as the area shows it: [result], sent with the [queryId] of the page it came on (each page has its own).
 * [key] is unique in the list.
 */
@Immutable
data class InlineResultEntry(
    val queryId: Long,
    val result: InlineResult,
) {
    val key: String get() = "$queryId/${result.id}"

    /** Photos, GIFs, stickers and videos show as cells of a grid; the others as rows across it. */
    val isCell: Boolean
        get() =
            result is InlineResult.Photo ||
                result is InlineResult.Animation ||
                result is InlineResult.Sticker ||
                result is InlineResult.Video
}

/** The size of the grid's cells: stickers alone take the sticker panel's cells, other media the shared media's. */
enum class InlineCellSize {
    Media,
    Stickers,
}

/** Whether more results come after those shown. */
@Immutable
sealed interface InlinePage {
    /** The bot has no more. */
    data object Done : InlinePage

    /** More come once the list nears its end. */
    data object More : InlinePage

    /** The next page is on its way. */
    data object Loading : InlinePage

    /** The next page failed with [error]; "Try again" asks again. */
    data class Failed(
        val error: DomainError,
    ) : InlinePage
}

/**
 * The question before the first query to a bot that asks for the user's location (td_api.tl
 * `userTypeBot.need_location`): [username] is the bot's; [needsPermission] while Android gives Pingogram no location,
 * which "Share location" then asks Android for first.
 */
@Immutable
data class InlineLocationSheet(
    val username: String,
    val needsPermission: Boolean,
)
