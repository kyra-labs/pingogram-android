package dev.kyralabs.pingogram.feature.chat.ui.inline

import androidx.compose.runtime.snapshotFlow
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.FormattedText
import dev.kyralabs.pingogram.core.domain.model.InlineBot
import dev.kyralabs.pingogram.core.domain.model.InlineQueryResults
import dev.kyralabs.pingogram.core.domain.model.InlineResult
import dev.kyralabs.pingogram.core.domain.model.InlineResultsButton
import dev.kyralabs.pingogram.core.domain.model.InlineResultsButtonType
import dev.kyralabs.pingogram.core.domain.model.Location
import dev.kyralabs.pingogram.core.domain.model.LocationAccess
import dev.kyralabs.pingogram.core.domain.repository.InlineBotRepository
import dev.kyralabs.pingogram.feature.chat.ui.ChatEffect
import dev.kyralabs.pingogram.feature.chat.ui.ChatLog
import dev.kyralabs.pingogram.feature.chat.ui.InlineBotEvent
import dev.kyralabs.pingogram.feature.chat.ui.composer.ChatComposer
import dev.kyralabs.pingogram.feature.chat.ui.composer.ComposerMode
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Inline bots in chat [chatId]'s composer for `ChatViewModel` (`REQUIREMENTS.md` FR-C5, "@gif cats"): while the field
 * starts with "@username " ([inlineQuery]) and does not edit, the bot's results above the composer ([state], kept out
 * of `ChatUiState` as only the composer shows them).
 *
 * - **The bot** is looked up once per username ([InlineBotRepository.inlineBot], kept for the conversation, also when
 *   it is no inline bot); a failed lookup shows nothing, as "@ada " may start a message to someone. Only usernames the
 *   user typed are looked up, never other bots ahead of time.
 * - **Queries** wait [debounce] after the last change, and the latest wins: a newer query cancels the one on its way,
 *   whose late answer TDLib drops (TDLib also keeps recent answers). The results shown stay while a newer query is on
 *   its way ([InlineResultsContent.Results.isSearching]). "@gif " alone asks too: the bot's suggestions.
 * - **Pages**: [InlineBotEvent.LoadMore], from the list nearing its end, loads the next page by the bot's offset; each
 *   page's results are sent with that page's query id. A failed page keeps the others, and loads again on the next
 *   [InlineBotEvent.LoadMore] ("Try again").
 * - **Errors** of the first page show in the area with "Try again" ([InlineBotEvent.Retry]); a flood wait counts its
 *   seconds down first, and nothing asks again by itself.
 * - **Location** (td_api.tl `userTypeBot.need_location`): before the first query to such a bot, [locationSheet] asks
 *   whether to share the location, and the query waits for the answer ([InlineBotEvent.AnswerLocation], kept for the
 *   process in [InlineLocationConsent]); "Not now" queries without it. With consent, the device's location is found
 *   once per conversation ([LOCATION_TIMEOUT] at most, through `DeviceLocationRepository`, never without Android's
 *   permission) and goes with that bot's queries. It is never sent without the user's "Share location".
 * - **Sending** ([InlineBotEvent.Send]): `sendInlineQueryResultMessage` into the chat (the topic's or thread's, through
 *   [bots]), replying where the composer replies, with the result's own send options; the field clears, and comes
 *   back with its reply if Telegram refuses. A game is not sent ([ChatEffect.GamesUnsupported]).
 * - **The bot's button** ([InlineBotEvent.OpenButton]): "Start" hands its parameter to the bot's chat
 *   ([InlineBotRepositories.starts]) and opens it, where "Start" waits for the user's tap; a Mini App's button says
 *   Pingogram cannot open Mini Apps yet ([ChatEffect.MiniAppsUnsupported]).
 *
 * Runs in [scope], the view model's, on the main thread, as the text field. Nothing typed or found is logged.
 */
@Suppress("LongParameterList") // The chat, where its bots, location and composer come from, and what it tells.
@OptIn(ExperimentalCoroutinesApi::class)
internal class ChatInlineBots(
    private val chatId: Long,
    private val repositories: InlineBotRepositories,
    private val bots: InlineBotRepository,
    private val composer: ChatComposer,
    private val scope: CoroutineScope,
    private val onEffect: (ChatEffect) -> Unit,
    private val log: ChatLog,
    private val debounce: Duration = QUERY_DEBOUNCE,
) {
    /** The bots looked up, by lower-case username; null for a username that names no inline bot. */
    private val found = mutableMapOf<String, InlineBot?>()

    /** Counts "Try again", which asks the bot again. */
    private val attempts = MutableStateFlow(0)

    /** The list nearing its end, or "Try again" after a failed page. */
    private val loadMore = Channel<Unit>(Channel.CONFLATED)

    /** The bot whose question about the location shows; null while none does. */
    private val asking = MutableStateFlow<InlineBot?>(null)

    /** The device's location for bots the user lets know it, once found; [located] once it was looked for. */
    private var deviceLocation: Location? = null
    private var located = false

    /** What [state] showed last, so results stay while a newer query of the same bot is on its way. */
    private var shown: InlineBotUiState? = null

    private val typed: Flow<TypedInlineQuery?> =
        combine(
            snapshotFlow { inlineQuery(composer.text.state.text) }.distinctUntilChanged(),
            composer.mode,
        ) { query, mode -> query.takeIf { mode !is ComposerMode.Editing } }
            .distinctUntilChanged()

    /** The inline bot and its results for the query in the field; null while the field holds none. */
    val state: StateFlow<InlineBotUiState?> =
        combine(typed, attempts) { query, _ -> query }
            .flatMapLatest { query -> if (query == null) flowOf(null) else session(query) }
            .onEach { shown = it }
            .stateIn(scope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), null)

    /** The question whether to share the location with a bot that asks for it; null while none is asked. */
    val locationSheet: StateFlow<InlineLocationSheet?> =
        asking
            .map { bot ->
                bot?.let {
                    InlineLocationSheet(it.username, repositories.location.access() == LocationAccess.None)
                }
            }.stateIn(scope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), null)

    fun onEvent(event: InlineBotEvent) {
        when (event) {
            is InlineBotEvent.Send -> {
                send(event)
            }

            InlineBotEvent.LoadMore -> {
                loadMore.trySend(Unit)
            }

            InlineBotEvent.Retry -> {
                attempts.value++
            }

            InlineBotEvent.OpenButton -> {
                openButton()
            }

            is InlineBotEvent.AnswerLocation -> {
                asking.value?.let { bot -> repositories.consent.set(bot.userId, event.share) }
                asking.value = null
            }
        }
    }

    /**
     * The query [typed] to its bot: nothing where it names no inline bot; otherwise the bot at once (with what showed
     * for it before, else loading), then the answer once the location question is answered and [debounce] passed.
     */
    private fun session(typed: TypedInlineQuery): Flow<InlineBotUiState?> =
        channelFlow {
            val bot = bot(typed.username)
            if (bot == null) {
                send(null)
                return@channelFlow
            }
            val before = shown?.takeIf { it.username == bot.username }?.content?.searching()
            val content = before ?: InlineResultsContent.Loading
            val base = InlineBotUiState(bot.username, bot.placeholder, typed.query, content)
            send(base)
            // A page asked for by the list of an older query is no page of this one.
            loadMore.tryReceive()
            val location = if (bot.needsLocation) locationFor(bot) else null
            delay(debounce)
            answer(bot, typed.query, location) { send(base.copy(content = it)) }
        }

    /** The inline bot with [username], looked up once; null where there is none, or the lookup failed. */
    private suspend fun bot(username: String): InlineBot? {
        val key = username.lowercase()
        if (key in found) return found[key]
        return when (val lookup = bots.inlineBot(username)) {
            is Outcome.Success -> {
                lookup.value.also { found[key] = it }
            }

            is Outcome.Failure -> {
                log.warn("Inline bot not looked up: ${lookup.error::class.simpleName}")
                null
            }
        }
    }

    /** The pages of [bot]'s answer to [query], shown through [show] as they come and as the list asks for more. */
    private suspend fun answer(
        bot: InlineBot,
        query: String,
        location: Location?,
        show: suspend (InlineResultsContent) -> Unit,
    ) {
        val first =
            when (val page = bots.results(bot.userId, chatId, query, location = location)) {
                is Outcome.Success -> page.value
                is Outcome.Failure -> return failed(page.error, show)
            }
        val button = first.button
        var entries = persistentListOf<InlineResultEntry>().adding(first)
        var offset = first.nextOffset
        while (offset.isNotEmpty()) {
            show(resultsOf(entries, button, InlinePage.More))
            loadMore.receive()
            // Each failure waits for "Try again", which asks for the same page.
            while (true) {
                show(resultsOf(entries, button, InlinePage.Loading))
                when (val page = bots.results(bot.userId, chatId, query, offset, location)) {
                    is Outcome.Success -> {
                        entries = entries.adding(page.value)
                        offset = page.value.nextOffset
                        break
                    }

                    is Outcome.Failure -> {
                        show(resultsOf(entries, button, InlinePage.Failed(page.error)))
                        loadMore.receive()
                    }
                }
            }
        }
        show(if (entries.isEmpty()) InlineResultsContent.Empty(button) else resultsOf(entries, button, InlinePage.Done))
    }

    /** The first page failed with [error]: shown, a flood wait counting its seconds down until "Try again" may ask. */
    private suspend fun failed(
        error: DomainError,
        show: suspend (InlineResultsContent) -> Unit,
    ) {
        if (error !is DomainError.FloodWait) return show(InlineResultsContent.Failed(error))
        for (left in error.seconds downTo 1) {
            show(InlineResultsContent.Failed(DomainError.FloodWait(left)))
            delay(1.seconds)
        }
        show(InlineResultsContent.Failed(DomainError.FloodWait(0)))
    }

    /**
     * Whether [bot]'s queries carry the location: asked once per process ([locationSheet]), waiting for the answer;
     * with consent, the device's location, where Android gives it.
     */
    private suspend fun locationFor(bot: InlineBot): Location? {
        val consent = repositories.consent
        val share =
            consent.answer(bot.userId) ?: try {
                asking.value = bot
                consent.answers(bot.userId).filterNotNull().first()
            } finally {
                if (asking.value == bot) asking.value = null
            }
        return if (share) currentLocation() else null
    }

    /** The device's location, looked for once per conversation: none without Android's permission or the location. */
    private suspend fun currentLocation(): Location? {
        if (located) return deviceLocation
        val device = repositories.location
        val location =
            if (device.access() == LocationAccess.None || !device.isLocationEnabled()) {
                null
            } else {
                withTimeoutOrNull(LOCATION_TIMEOUT) { device.locate().firstOrNull() }?.location
            }
        deviceLocation = location
        located = true
        return location
    }

    private fun send(event: InlineBotEvent.Send) {
        val results = (state.value?.content as? InlineResultsContent.Results)?.results.orEmpty()
        when (results.firstOrNull { it.queryId == event.queryId && it.result.id == event.resultId }?.result) {
            null -> Unit
            is InlineResult.Game -> onEffect(ChatEffect.GamesUnsupported)
            else -> sendResult(event)
        }
    }

    /** Sends the result of [event], clearing the field; a refusal gives the text and the reply back. */
    private fun sendResult(event: InlineBotEvent.Send) {
        val typed =
            composer.text.state.text
                .toString()
        composer.text.setContent(FormattedText.Empty)
        val replyTo = composer.takeReply()
        scope.launch {
            when (val sent = bots.send(chatId, event.queryId, event.resultId, event.options, replyTo)) {
                is Outcome.Success -> {
                    event.options.scheduledAt?.let { onEffect(ChatEffect.MessageScheduled(it)) }
                }

                is Outcome.Failure -> {
                    replyTo?.let(composer::restoreReply)
                    if (composer.text.isBlank) composer.text.setContent(FormattedText(typed, persistentListOf()))
                    onEffect(ChatEffect.ShowError(sent.error))
                }
            }
        }
    }

    private fun openButton() {
        val state = state.value
        val button = state?.content?.button
        val bot = state?.let { found[it.username.lowercase()] }
        if (button == null || bot == null) return
        when (val type = button.type) {
            is InlineResultsButtonType.StartBot -> {
                repositories.starts.deliver(bot.chatId, type.parameter)
                if (bot.chatId != chatId) onEffect(ChatEffect.OpenChat(bot.chatId))
            }

            InlineResultsButtonType.WebApp -> {
                onEffect(ChatEffect.MiniAppsUnsupported)
            }
        }
    }

    private companion object {
        /** How long the field rests before its query goes to the bot, as Telegram's apps wait. */
        val QUERY_DEBOUNCE = 400.milliseconds

        /** How long a bot that asks for the location waits for one before its query goes without. */
        val LOCATION_TIMEOUT = 10.seconds

        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}

/** The results of [this] page after these, each with its page's query id, without those already listed. */
private fun PersistentList<InlineResultEntry>.adding(page: InlineQueryResults): PersistentList<InlineResultEntry> {
    val ids = mapTo(HashSet()) { it.result.id }
    return addingAll(page.results.filter { ids.add(it.id) }.map { InlineResultEntry(page.queryId, it) })
}

/** [entries] with [button], in cells of the stickers' size where every cell is a sticker, and [page]. */
private fun resultsOf(
    entries: PersistentList<InlineResultEntry>,
    button: InlineResultsButton?,
    page: InlinePage,
): InlineResultsContent.Results {
    val cells = entries.filter { it.isCell }
    val stickers = cells.isNotEmpty() && cells.all { it.result is InlineResult.Sticker }
    return InlineResultsContent.Results(
        results = entries,
        button = button,
        cells = if (stickers) InlineCellSize.Stickers else InlineCellSize.Media,
        page = page,
    )
}

/** What shows while a newer query is on its way: these results or "nothing found", marked as searching. */
private fun InlineResultsContent.searching(): InlineResultsContent? =
    when (this) {
        is InlineResultsContent.Results -> copy(isSearching = true)
        is InlineResultsContent.Empty -> copy(isSearching = true)
        else -> null
    }

/** The bot's button of these results; null without, and while none are shown. */
internal val InlineResultsContent.button: InlineResultsButton?
    get() =
        when (this) {
            is InlineResultsContent.Results -> button
            is InlineResultsContent.Empty -> button
            else -> null
        }
