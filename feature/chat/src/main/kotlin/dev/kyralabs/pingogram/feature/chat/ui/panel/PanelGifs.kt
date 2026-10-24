package dev.kyralabs.pingogram.feature.chat.ui.panel

import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.common.result.getOrNull
import dev.kyralabs.pingogram.core.domain.model.Animation
import dev.kyralabs.pingogram.core.domain.model.InlineBot
import dev.kyralabs.pingogram.core.domain.model.InlineResult
import dev.kyralabs.pingogram.core.domain.repository.InlineBotRepository
import dev.kyralabs.pingogram.core.domain.repository.SavedGifRepository
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The GIF tab of the composer's panel (`REQUIREMENTS.md` FR-C5), a part of [EmojiPanelViewModel] in its [scope]:
 *
 * - **Saved GIFs** from [saved], read again whenever Telegram reports that they changed (a GIF sent from any device
 *   joins them); "Remove" takes one out.
 * - **Search** through the bot Telegram names for it (the option `animation_search_bot_username`, usually @gif),
 *   asked as an inline query typed in chat [chatId] would be ([InlineBotRepository]): 400 ms after the last change of
 *   the query, the newest query cancelling the one still running, its GIF results only, more pages as the grid nears
 *   their end. The bot is looked up at the first search, never before.
 *
 * Sending a GIF goes through the conversation (`ChatEvent.SendAnimation`), which takes the composer's reply along.
 */
internal class PanelGifs(
    private val scope: CoroutineScope,
    private val chatId: Long,
    private val saved: SavedGifRepository,
    private val inlineBots: InlineBotRepository,
) {
    private val attempt = MutableStateFlow(0)
    private val search = MutableStateFlow<GifSearch?>(null)
    private var searchJob: Job? = null
    private var bot: InlineBot? = null
    private var nextOffset = ""

    /** The tab: loading until the saved GIFs and the search bot's name are read, then both, with the search. */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun tab(): Flow<GifTab> =
        attempt
            .flatMapLatest {
                combine(saved.savedGifs(), flow { emit(saved.searchBotUsername().getOrNull() != null) }, search) {
                    gifs,
                    canSearch,
                    search,
                    ->
                    when (gifs) {
                        is Outcome.Failure -> GifTab.Error(gifs.error)
                        is Outcome.Success -> GifTab.Content(gifs.value.toImmutableList(), canSearch, search)
                    }
                }.onStart { emit(GifTab.Loading) }
            }

    fun onQueryChanged(query: String) {
        searchJob?.cancel()
        if (query.isBlank()) {
            search.value = null
            return
        }
        val text = query.trim()
        search.update { GifSearch(text, it?.results ?: persistentListOf(), isLoading = true) }
        searchJob =
            scope.launch {
                delay(SEARCH_DELAY_MILLIS)
                loadPage(text, offset = "")
            }
    }

    fun loadMore() {
        val current = search.value ?: return
        if (current.isLoading || !current.hasMore || current.error != null) return
        search.value = current.copy(isLoading = true)
        searchJob = scope.launch { loadPage(current.query, nextOffset) }
    }

    fun retry() {
        val current = search.value
        if (current?.error == null) {
            attempt.update { it + 1 }
            return
        }
        search.value = current.copy(isLoading = true, error = null)
        searchJob = scope.launch { loadPage(current.query, if (current.results.isEmpty()) "" else nextOffset) }
    }

    fun remove(gif: Animation) {
        // A refusal changes nothing: the saved GIFs stay as Telegram has them.
        scope.launch { saved.removeSaved(gif) }
    }

    /** Asks the search bot for the page at [offset] of [query]; the first page replaces the results, others append. */
    private suspend fun loadPage(
        query: String,
        offset: String,
    ) {
        val found =
            when (val searcher = searchBot()) {
                is Outcome.Failure -> searcher
                is Outcome.Success -> inlineBots.results(searcher.value.userId, chatId, query, offset)
            }
        search.update { current ->
            if (current?.query != query) return@update current
            when (found) {
                is Outcome.Failure -> {
                    current.copy(isLoading = false, error = found.error)
                }

                is Outcome.Success -> {
                    nextOffset = found.value.nextOffset
                    val gifs = found.value.results.mapNotNull { (it as? InlineResult.Animation)?.animation }
                    current.copy(
                        results = (if (offset.isEmpty()) gifs else current.results + gifs).toImmutableList(),
                        isLoading = false,
                        error = null,
                        hasMore = found.value.nextOffset.isNotEmpty(),
                    )
                }
            }
        }
    }

    /** The bot that searches GIFs, looked up once; `DomainError.NotFound` where Telegram names none. */
    private suspend fun searchBot(): Outcome<InlineBot> {
        bot?.let { return Outcome.Success(it) }
        val found =
            when (val username = saved.searchBotUsername()) {
                is Outcome.Failure -> username
                is Outcome.Success -> username.value?.let { inlineBots.inlineBot(it) } ?: Outcome.Success(null)
            }
        return when (found) {
            is Outcome.Failure -> {
                found
            }

            is Outcome.Success -> {
                found.value?.also { bot = it }?.let { Outcome.Success(it) }
                    ?: Outcome.Failure(DomainError.NotFound)
            }
        }
    }

    private companion object {
        /** How long the query rests before it is searched. */
        const val SEARCH_DELAY_MILLIS = 400L
    }
}
