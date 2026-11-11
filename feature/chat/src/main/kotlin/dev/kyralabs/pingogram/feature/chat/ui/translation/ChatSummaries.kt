package dev.kyralabs.pingogram.feature.chat.ui.translation

import androidx.lifecycle.SavedStateHandle
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.ChatHistory
import dev.kyralabs.pingogram.core.domain.model.FormattedText
import dev.kyralabs.pingogram.core.domain.model.Message
import dev.kyralabs.pingogram.core.domain.model.MessageContent
import dev.kyralabs.pingogram.core.domain.model.isLanguageAmong
import dev.kyralabs.pingogram.feature.chat.ui.ChatEffect
import dev.kyralabs.pingogram.feature.chat.ui.MessageAction
import dev.kyralabs.pingogram.feature.chat.ui.SummaryEvent
import dev.kyralabs.pingogram.feature.chat.ui.TranslationEffect
import dev.kyralabs.pingogram.feature.chat.ui.composer.textOrCaption
import dev.kyralabs.pingogram.feature.chat.ui.findById
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.job
import kotlinx.coroutines.launch

/**
 * Telegram's summaries of long messages in the conversation of chat [chatId], for `ChatViewModel` (td_api.tl
 * `summarizeMessage`, made on Telegram's servers): the summaries the bubbles show ([bubbles], as [BubbleTranslation]s,
 * copied into their bubbles as translations are) and the "Summarize" or "Show full text" a message's actions offer
 * ([actionFor]).
 *
 * - **Which messages**: those Telegram says it can summarize (`Message.summaryLanguage`, td_api.tl
 *   `message.summary_language_code`), by their text or caption; rich messages, whose bubble draws blocks rather than a
 *   text, are left out, as translation leaves them out.
 * - **Asked for** only when the user chooses "Summarize" ([SummaryEvent.Summarize]): one request per message, in the
 *   reader's language where the message is in a language the user does not read (Pingogram's language, or the
 *   system's first one, as translations go) and Telegram translates into it, else in the message's own language. Its
 *   bubble shows that Telegram summarizes, then the summary with "Summary by Telegram" in its meta; "Show full text"
 *   ([SummaryEvent.ShowFullText]) shows its text again, and stops waiting for a summary on its way. A failure is a
 *   snackbar ([TranslationEffect.SummaryFailed]: a flood wait's time, no network, Telegram Premium required, or that it
 *   failed), and the bubble shows its text.
 * - **Kept**: the summaries in memory per message for the view model's life; a summary of a text the message no longer
 *   has (it was edited) is asked for again. The messages the user chose survive process death in [savedState], and
 *   their summaries are asked for again once their messages are loaded ([start]).
 *
 * Runs in [scope], the view model's, on the main thread; [bubbles] may
 * be collected on another dispatcher.
 */
internal class ChatSummaries(
    private val chatId: Long,
    repositories: TranslationRepositories,
    private val history: Flow<ChatHistory>,
    private val savedState: SavedStateHandle,
    private val scope: CoroutineScope,
    private val onEffect: (ChatEffect) -> Unit,
) {
    private val repository = repositories.translations
    private val detector = repositories.detector
    private val appLanguage = repositories.appLanguage

    /** The messages whose bubble the user wants to show a summary. */
    private val chosen: StateFlow<LongArray> = savedState.getStateFlow(KEY_CHOSEN, LongArray(0))

    /** Telegram's summaries, by message, with the text they summarize. */
    private val summaries = MutableStateFlow<PersistentMap<Long, Summarized>>(persistentMapOf())

    /** The messages whose summary is on its way. */
    private val pending = MutableStateFlow<PersistentSet<Long>>(persistentSetOf())

    /** The request of each message in [pending], by message id; only on the main thread. */
    private val jobs = mutableMapOf<Long, Job>()

    /** What the bubbles show of the summaries, by message id: only the bubbles that show one, or wait for one. */
    val bubbles: Flow<ImmutableMap<Long, BubbleTranslation>> =
        combine(history, chosen, summaries, pending) { history, chosen, summaries, pending ->
            buildMap {
                for (id in chosen) {
                    val text = history.messages.findById(id)?.summarizableText ?: continue
                    when {
                        id in pending -> put(id, BubbleTranslation.Summarizing)
                        summaries[id]?.source == text -> put(id, BubbleTranslation.Summary(summaries.getValue(id).text))
                    }
                }
            }.toImmutableMap()
        }.onStart { emit(persistentMapOf()) }
            .distinctUntilChanged()

    /** Asks for the summaries of the chosen messages that have none of their current text, as their messages load. */
    fun start() {
        scope.launch {
            combine(history, chosen) { history, chosen -> chosen.toList().mapNotNull(history.messages::findById) }
                .distinctUntilChanged()
                .collect { messages -> messages.forEach(::request) }
        }
    }

    fun onEvent(event: SummaryEvent) {
        when (event) {
            is SummaryEvent.Summarize -> summarize(event.messageId)
            is SummaryEvent.ShowFullText -> showFullText(event.messageId)
        }
    }

    /**
     * What [message]'s actions offer: "Show full text" while its bubble shows a summary or waits for one, "Summarize"
     * where Telegram can summarize it; null otherwise.
     */
    fun actionFor(message: Message): MessageAction? =
        when {
            message.summaryLanguage == null || message.summarizableText.text.isBlank() -> null
            message.id in chosen.value -> MessageAction.ShowFullText
            else -> MessageAction.Summarize
        }

    /** Chooses the message; [start]'s collection asks for its summary, once, as for every chosen message. */
    private fun summarize(messageId: Long) {
        setChosen(chosen.value.toSet() + messageId)
    }

    private fun showFullText(messageId: Long) {
        setChosen(chosen.value.toSet() - messageId)
        jobs.remove(messageId)?.cancel()
        pending.update { it.remove(messageId) }
    }

    /** Asks Telegram for [message]'s summary, unless it is on its way or there is one of its current text. */
    private fun request(message: Message) {
        val id = message.id
        val language = message.summaryLanguage?.takeIf { message.summarizableText.text.isNotBlank() }
        val text = message.summarizableText
        if (language == null || id in jobs || summaries.value[id]?.source == text) return
        pending.update { it.add(id) }
        // Registered before it starts, so a request that ends at once (an immediate dispatcher) is not asked again.
        val job =
            scope.launch(start = CoroutineStart.LAZY) {
                val job = coroutineContext.job
                try {
                    when (val result = repository.summarizeMessage(chatId, id, targetFor(language))) {
                        is Outcome.Success -> {
                            summaries.update { it.put(id, Summarized(text, result.value)) }
                        }

                        is Outcome.Failure -> {
                            setChosen(chosen.value.toSet() - id)
                            onEffect(TranslationEffect.SummaryFailed(result.error))
                        }
                    }
                } finally {
                    // A newer request for the message may have replaced this one meanwhile.
                    if (jobs.remove(id, job)) pending.update { it.remove(id) }
                }
            }
        jobs[id] = job
        job.start()
    }

    /**
     * The language Telegram is asked to summarize into: the reader's (Pingogram's language, or the system's first one)
     * where they do not read [summaryLanguage] and Telegram translates into theirs; null for the message's own.
     */
    private suspend fun targetFor(summaryLanguage: String): String? {
        val preferred = detector.preferredLanguages()
        val reader = appLanguage.language.first() ?: preferred.firstOrNull()
        val reads = reader == null || isLanguageAmong(summaryLanguage, listOf(reader) + preferred)
        return if (reads) null else reader?.let(repository::targetLanguageOf)
    }

    private fun setChosen(ids: Set<Long>) {
        savedState[KEY_CHOSEN] = ids.toLongArray()
    }

    private companion object {
        /** The messages the user chose to see summarized. */
        const val KEY_CHOSEN = "summary.messages"
    }
}

/** A summary of [source], the text it was asked for. */
private data class Summarized(
    val source: FormattedText,
    val text: FormattedText,
)

/** The text Telegram summarizes, as the bubble shows it: none for a rich message, whose bubble draws blocks. */
private val Message.summarizableText: FormattedText
    get() = if (content is MessageContent.RichMessage) FormattedText.Empty else textOrCaption
