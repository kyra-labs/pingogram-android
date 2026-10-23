package dev.kyralabs.pingogram.feature.chat.ui.translation

import androidx.lifecycle.SavedStateHandle
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.Chat
import dev.kyralabs.pingogram.core.domain.model.ChatHistory
import dev.kyralabs.pingogram.core.domain.model.FormattedText
import dev.kyralabs.pingogram.core.domain.model.LanguageDetection
import dev.kyralabs.pingogram.core.domain.model.Message
import dev.kyralabs.pingogram.core.domain.model.isLanguageAmong
import dev.kyralabs.pingogram.core.domain.model.primaryLanguageOf
import dev.kyralabs.pingogram.feature.chat.ui.ChatEffect
import dev.kyralabs.pingogram.feature.chat.ui.MessageAction
import dev.kyralabs.pingogram.feature.chat.ui.TranslationEffect
import dev.kyralabs.pingogram.feature.chat.ui.TranslationEvent
import dev.kyralabs.pingogram.feature.chat.ui.composer.textOrCaption
import dev.kyralabs.pingogram.feature.chat.ui.findById
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.util.concurrent.ConcurrentHashMap

/**
 * Message translation in the conversation of chat [chatId], for `ChatViewModel` (`REQUIREMENTS.md` FR-C3, FR-C6): the
 * translations the bubbles show ([bubbles], copied only into the bubbles they change), the translate bar ([bar]), and
 * the "Translate" or "Show original" a message's actions offer ([actionFor]). Telegram translates (TDLib's
 * `translateMessageText`, through [TranslationRepositories.translations]); nothing of a message goes anywhere else, and
 * nothing is translated that the user did not ask for: a message they chose, or the messages on screen once they
 * turned the bar on.
 *
 * - **Languages**: translations go into Pingogram's language, or the system's first one while Pingogram follows the
 *   system ([TranslationRepositories.appLanguage]); the user reads that language and every language of the system's
 *   settings. Where Telegram does not translate into it, nothing is offered.
 * - **Detection** (`DeviceLanguageDetector`): on the device only, kept per message for its text. "Translate" is offered
 *   for a text with letters unless the device found it in a language the user reads; where the device cannot tell
 *   (before Android 10, or undetermined) it is offered.
 * - **A message** ([TranslationEvent.Translate]): its bubble shows that it translates, then the translation, with its
 *   language in the meta; "Show original" ([TranslationEvent.ShowOriginal]) shows its text again. A failure is a
 *   snackbar ([TranslationEffect.Failed]: the time a flood wait asks for, no network, Telegram Premium required, or
 *   that it failed) and the bubble shows its text.
 * - **The translate bar**, where Telegram suggests translating the chat (`Chat.isTranslatable`, Telegram Premium only),
 *   the device can detect languages, the user did not close it here ([TranslationRepositories.hiddenBars]), and most
 *   of the chat's recent incoming messages are in one language the user does not read. A tap
 *   ([TranslationEvent.ToggleChat]) translates the messages on screen as they show ([setVisible]): what the list
 *   reports is batched for [BATCH_MILLIS], only the messages the device found in a language the user does not read are
 *   asked for, at most [MAX_REQUESTS] at once, and a message that leaves the screen before its answer stops being
 *   waited for (TDLib cannot cancel a request; its late answer is dropped). The history around is never asked for. In
 *   that mode "Show original" keeps a message's own text. Closing the bar ([TranslationEvent.CloseBar]) turns it off
 *   and hides it for the chat. One snackbar at most tells of failures until the bar is turned on again.
 * - **Kept**: the translations, in memory per message and language, for the view model's life; one whose message was
 *   edited meanwhile no longer shows, and a message the user chose is no longer translated once it is edited.
 *   Whether the bar is on, and the messages the user chose and those they keep in the original, survive process death
 *   in [savedState]; their translations are asked for again as they show.
 *
 * [messageOf] gives a loaded message as it is now. Runs in [scope], the view model's, on the main thread; [bubbles] and
 * [bar] may be collected on another dispatcher.
 */
@Suppress("LongParameterList", "TooManyFunctions") // The chat and what it reads; one function per event and step.
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
internal class ChatTranslation(
    private val chatId: Long,
    repositories: TranslationRepositories,
    chat: Flow<Chat>,
    history: Flow<ChatHistory>,
    private val messageOf: (messageId: Long) -> Message?,
    private val savedState: SavedStateHandle,
    private val scope: CoroutineScope,
    private val onEffect: (ChatEffect) -> Unit,
) {
    private val repository = repositories.translations
    private val detector = repositories.detector
    private val hiddenBars = repositories.hiddenBars

    /** The language translations go into and those the user reads; null where Telegram does not translate into it. */
    private val target: Flow<Target?> =
        repositories.appLanguage.language
            .map(::targetOf)
            .distinctUntilChanged()
            .shareIn(scope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), replay = 1)

    private val chatOn: StateFlow<Boolean> = savedState.getStateFlow(KEY_CHAT, false)
    private val singles: StateFlow<LongArray> = savedState.getStateFlow(KEY_SINGLES, LongArray(0))
    private val originals: StateFlow<LongArray> = savedState.getStateFlow(KEY_ORIGINALS, LongArray(0))

    private val choices: Flow<Choices> =
        combine(
            chatOn,
            singles,
            originals,
        ) { on, singles, originals -> Choices(on, singles.toSet(), originals.toSet()) }

    /** Telegram's translations, by message and language. */
    private val translated = MutableStateFlow<PersistentMap<TranslationKey, Translated>>(persistentMapOf())

    /** The messages whose translation is on its way. */
    private val pending = MutableStateFlow<PersistentSet<Long>>(persistentSetOf())

    /** The request of each message in [pending], by message id; only on the main thread. */
    private val jobs = mutableMapOf<Long, Job>()

    /** What Telegram did not translate, not asked for again until the user asks; only on the main thread. */
    private val failed = mutableSetOf<TranslationKey>()

    /** A failure of the chat's translation was told since the bar was turned on; only on the main thread. */
    private var toldChatFailure = false

    /** The language detected for each message, with the text it was detected in. */
    private val detections = ConcurrentHashMap<Long, Detection>()

    private val requests = Semaphore(MAX_REQUESTS)

    /** The messages on screen, by id. */
    private val visible = MutableStateFlow<Set<Long>>(emptySet())

    /** What the bubbles show of the translations, by message id: only the bubbles that show one, or wait for one. */
    val bubbles: Flow<ImmutableMap<Long, BubbleTranslation>> =
        combine(history, target, choices, translated, pending) { history, target, choices, translated, pending ->
            if (target == null) persistentMapOf() else bubbleTranslations(history, target, choices, translated, pending)
        }.onStart { emit(persistentMapOf()) }
            .distinctUntilChanged()

    /** The translate bar; null where it does not show. */
    val bar: Flow<TranslateBar?> =
        combine(
            chat.map { it.isTranslatable }.distinctUntilChanged(),
            hiddenBars.isHidden(chatId),
            target,
        ) { isTranslatable, isHidden, target ->
            target.takeIf { isTranslatable && !isHidden && detector.canDetect }
        }.distinctUntilChanged()
            .flatMapLatest { target ->
                if (target == null) {
                    flowOf(null)
                } else {
                    combine(foreignLanguageOf(history, target), chatOn) { from, isOn ->
                        if (isOn || from != null) TranslateBar(target.languageTag, from, isOn) else null
                    }
                }
            }.onStart { emit(null) }
            .distinctUntilChanged()

    /** Starts translating the messages on screen that the user's choices want translated. */
    fun start() {
        scope.launch {
            combine(visible.debounce(BATCH_MILLIS), choices, target, translated) { visible, choices, target, _ ->
                Batch(visible, choices, target)
            }.collectLatest(::request)
        }
    }

    /** The list shows [messageIds] now. */
    fun setVisible(messageIds: Set<Long>) {
        visible.value = messageIds
    }

    fun onEvent(event: TranslationEvent) {
        when (event) {
            is TranslationEvent.Translate -> translateOne(event.messageId)
            is TranslationEvent.ShowOriginal -> showOriginal(event.messageId)
            TranslationEvent.ToggleChat -> setChatOn(!chatOn.value)
            TranslationEvent.CloseBar -> closeBar()
        }
    }

    /**
     * What [message]'s actions offer: "Show original" while its bubble shows a translation or waits for one,
     * "Translate" for a text with letters the device did not find in a language the user reads; null otherwise, and
     * where Telegram does not translate into the user's language.
     */
    suspend fun actionFor(message: Message): MessageAction? {
        val text = message.textOrCaption
        val target = target.first()
        if (!text.hasLetters || target == null) return null
        val id = message.id
        val shown = translated.value[TranslationKey(id, target.code)]?.source == text && currentChoices().wants(id)
        return when {
            shown || id in pending.value -> MessageAction.ShowOriginal
            detect(id, text).isRead(target) -> null
            else -> MessageAction.Translate
        }
    }

    private fun translateOne(messageId: Long) {
        setOriginals(originals.value.toSet() - messageId)
        setSingles(singles.value.toSet() + messageId)
        scope.launch {
            val target = target.first() ?: return@launch
            val text = messageOf(messageId)?.textOrCaption?.takeIf { it.hasLetters } ?: return@launch
            val key = TranslationKey(messageId, target.code)
            failed -= key
            if (translated.value[key]?.source != text && messageId !in jobs) translate(messageId, text, target, true)
        }
    }

    private fun showOriginal(messageId: Long) {
        setSingles(singles.value.toSet() - messageId)
        if (chatOn.value) setOriginals(originals.value.toSet() + messageId)
        stop(messageId)
    }

    private fun setChatOn(on: Boolean) {
        if (on) {
            setOriginals(emptySet())
            failed.clear()
            toldChatFailure = false
        }
        savedState[KEY_CHAT] = on
    }

    private fun closeBar() {
        savedState[KEY_CHAT] = false
        scope.launch {
            when (val hid = hiddenBars.hide(chatId)) {
                is Outcome.Success -> onEffect(TranslationEffect.BarHidden)
                is Outcome.Failure -> onEffect(ChatEffect.ShowError(hid.error))
            }
        }
    }

    /**
     * Stops waiting for the messages no longer wanted, and for those of the chat's translation that left the screen (a
     * message the user chose is waited for wherever the list is), then asks Telegram for those on
     * screen that the user's choices want translated and are not yet: every message the user chose; in the chat's
     * translation, those the device found in a language the user does not read. A chosen message whose translation is
     * of a text it no longer has (it was edited) is no longer chosen.
     */
    private suspend fun request(batch: Batch) {
        for (id in jobs.keys.toList()) {
            val chosen = id in batch.choices.singles
            if (!batch.choices.wants(id) || (!chosen && id !in batch.visible)) stop(id)
        }
        val target = batch.target ?: return
        for (id in batch.visible.sortedDescending()) {
            val text = messageOf(id)?.textOrCaption?.takeIf { it.hasLetters }
            if (id !in jobs && text != null && batch.choices.wants(id)) request(id, text, target, batch.choices)
        }
    }

    /** Asks for message [messageId] on screen, whose text is [text], as [request] says. */
    private suspend fun request(
        messageId: Long,
        text: FormattedText,
        target: Target,
        choices: Choices,
    ) {
        val key = TranslationKey(messageId, target.code)
        val done = translated.value[key]
        val chosen = messageId in choices.singles
        when {
            done?.source == text || key in failed -> Unit
            done != null && chosen -> setSingles(singles.value.toSet() - messageId)
            chosen -> translate(messageId, text, target, single = true)
            detect(messageId, text).isForeign(target) -> translate(messageId, text, target, single = false)
        }
    }

    /** Asks Telegram for the translation of message [messageId], whose text is [text], into [target]. */
    private fun translate(
        messageId: Long,
        text: FormattedText,
        target: Target,
        single: Boolean,
    ) {
        val key = TranslationKey(messageId, target.code)
        pending.update { it.adding(messageId) }
        lateinit var job: Job
        job =
            scope.launch(start = CoroutineStart.LAZY) {
                try {
                    val result = requests.withPermit { repository.translateMessage(chatId, messageId, target.code) }
                    when (result) {
                        is Outcome.Success -> {
                            // An answer for a text the message no longer has is dropped.
                            if (messageOf(messageId)?.textOrCaption == text) {
                                val from = (detect(messageId, text) as? LanguageDetection.Detected)?.languageTag
                                translated.update { it.putting(key, Translated(text, result.value, from)) }
                            }
                        }

                        is Outcome.Failure -> {
                            failed += key
                            if (single) setSingles(singles.value.toSet() - messageId)
                            tell(result.error, single)
                        }
                    }
                } finally {
                    if (jobs[messageId] === job) {
                        jobs.remove(messageId)
                        pending.update { it.removing(messageId) }
                    }
                }
            }
        jobs[messageId] = job
        job.start()
    }

    /** Stops waiting for the translation of message [messageId]: its late answer is dropped. */
    private fun stop(messageId: Long) {
        jobs.remove(messageId)?.cancel()
        pending.update { it.removing(messageId) }
    }

    /** A snackbar for [error]: every one of a message the user chose, the first of the chat's translation. */
    private fun tell(
        error: DomainError,
        single: Boolean,
    ) {
        if (error == DomainError.Silent || (!single && toldChatFailure)) return
        if (!single) toldChatFailure = true
        onEffect(TranslationEffect.Failed(error))
    }

    /** The language of message [messageId], whose text is [text], detected on the device once per text. */
    private suspend fun detect(
        messageId: Long,
        text: FormattedText,
    ): LanguageDetection {
        detections[messageId]?.takeIf { it.text == text.text }?.let { return it.result }
        val result = detector.detect(text.text)
        detections[messageId] = Detection(text.text, result)
        return result
    }

    /**
     * The language most of the chat's recent incoming texts are in (at most [RECENT_MESSAGES] of them), as the device
     * detects them, while the user does not read it; null otherwise.
     */
    private fun foreignLanguageOf(
        history: Flow<ChatHistory>,
        target: Target,
    ): Flow<String?> =
        history
            .map { recentTexts(it) }
            .distinctUntilChanged()
            .mapLatest { texts ->
                val detected =
                    texts.mapNotNull { (id, text) -> (detect(id, text) as? LanguageDetection.Detected)?.languageTag }
                val likeliest = detected.groupBy(::primaryLanguageOf).maxByOrNull { it.value.size }?.value
                likeliest
                    ?.takeIf { it.size * 2 > detected.size }
                    ?.first()
                    ?.takeUnless { isLanguageAmong(it, target.reads) }
            }

    /** The target for Pingogram's language [appLanguage], or the system's first one with null. */
    private fun targetOf(appLanguage: String?): Target? {
        val preferred = detector.preferredLanguages()
        val language = appLanguage ?: preferred.first()
        val code = repository.targetLanguageOf(language) ?: return null
        return Target(language, code, (listOf(language) + preferred).distinct().toImmutableList())
    }

    private fun currentChoices() = Choices(chatOn.value, singles.value.toSet(), originals.value.toSet())

    private fun setSingles(ids: Set<Long>) {
        savedState[KEY_SINGLES] = ids.toLongArray()
    }

    private fun setOriginals(ids: Set<Long>) {
        savedState[KEY_ORIGINALS] = ids.toLongArray()
    }

    /** Whether the user reads the language detected; false where the device could not tell. */
    private fun LanguageDetection.isRead(target: Target): Boolean =
        this is LanguageDetection.Detected && isLanguageAmong(languageTag, target.reads)

    /** Whether the device found the text in a language the user does not read. */
    private fun LanguageDetection.isForeign(target: Target): Boolean =
        this is LanguageDetection.Detected && !isLanguageAmong(languageTag, target.reads)

    internal companion object {
        /** Whether the chat's translation is on. */
        const val KEY_CHAT = "translation.chat"

        /** The messages the user chose to translate. */
        const val KEY_SINGLES = "translation.messages"

        /** The messages the user keeps in their own text while the chat's translation is on. */
        const val KEY_ORIGINALS = "translation.originals"

        /** How long what the list shows settles before it is asked for, as viewing messages is batched. */
        const val BATCH_MILLIS = 300L

        /** Requests to Telegram at once, so a fast scroll does not send a page of them. */
        const val MAX_REQUESTS = 4

        /** The recent incoming texts that tell the chat's language. */
        const val RECENT_MESSAGES = 10

        private const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}

/** What the bubbles show of [translated] and [pending], for the messages in [history]. */
private fun bubbleTranslations(
    history: ChatHistory,
    target: Target,
    choices: Choices,
    translated: Map<TranslationKey, Translated>,
    pending: Set<Long>,
): ImmutableMap<Long, BubbleTranslation> {
    if (translated.isEmpty() && pending.isEmpty()) return persistentMapOf()
    val shown = mutableMapOf<Long, BubbleTranslation>()
    for (id in pending) {
        if (choices.wants(id)) shown[id] = BubbleTranslation.Translating
    }
    for ((key, done) in translated) {
        val id = key.messageId
        if (key.code != target.code || !choices.wants(id)) continue
        // A message edited since keeps its own text.
        if (history.messages.findById(id)?.textOrCaption == done.source) {
            shown[id] = BubbleTranslation.Shown(done.text, done.fromLanguage)
        }
    }
    return shown.toImmutableMap()
}

/** The recent incoming texts with letters, newest first, by message id. */
private fun recentTexts(history: ChatHistory): List<Pair<Long, FormattedText>> =
    history.messages
        .asSequence()
        .filter { !it.isOutgoing }
        .map { it.id to it.textOrCaption }
        .filter { (_, text) -> text.hasLetters }
        .take(ChatTranslation.RECENT_MESSAGES)
        .toList()

/** A text worth translating: one with letters, not only emoji, digits or punctuation. */
private val FormattedText.hasLetters: Boolean get() = text.any(Char::isLetter)

/**
 * Where translations go: into [languageTag] (IETF BCP 47), Telegram's [code] for it; the user reads [reads], that
 * language first.
 */
private data class Target(
    val languageTag: String,
    val code: String,
    val reads: ImmutableList<String>,
)

/** Which messages show a translation: those the user chose, and while [chatOn] every other but [originals]. */
private class Choices(
    val chatOn: Boolean,
    val singles: Set<Long>,
    val originals: Set<Long>,
) {
    fun wants(messageId: Long) = messageId in singles || (chatOn && messageId !in originals)
}

/** What the list showed, with the choices and the target then. */
private class Batch(
    val visible: Set<Long>,
    val choices: Choices,
    val target: Target?,
)

private data class TranslationKey(
    val messageId: Long,
    val code: String,
)

/** Telegram's translation [text] of [source], the message's text then, from [fromLanguage] as detected. */
private class Translated(
    val source: FormattedText,
    val text: FormattedText,
    val fromLanguage: String?,
)

/** The language [result] detected in [text]. */
private class Detection(
    val text: String,
    val result: LanguageDetection,
)
