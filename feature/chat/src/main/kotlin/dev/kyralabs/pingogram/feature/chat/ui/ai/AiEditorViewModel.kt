package dev.kyralabs.pingogram.feature.chat.ui.ai

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.ChatType
import dev.kyralabs.pingogram.core.domain.model.FormattedText
import dev.kyralabs.pingogram.core.domain.repository.AiEditorConsentRepository
import dev.kyralabs.pingogram.core.domain.repository.AiTextRepository
import dev.kyralabs.pingogram.core.domain.repository.AppLanguageRepository
import dev.kyralabs.pingogram.core.domain.repository.ChatRepository
import dev.kyralabs.pingogram.core.domain.repository.TranslationRepository
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * The AI text editor of chat [chatId]'s composer (`docs/DESIGN_SYSTEM.md` §6 "AI text editor"): Telegram's AI fixes,
 * rewrites in a style or translates the text the composer holds. The text leaves the device only on "Fix", "Rewrite"
 * or "Translate" ([AiEditorEvent.Run]), after a one-time explanation ([AiEditorConsentRepository]), and never in a
 * secret chat ([isAvailable]); the result replaces the composer's text only on "Use" ([AiEditorEffect.Use]), which the
 * route can undo. The choices are kept in the saved state; the text and the result in memory only, so after process
 * death the route opens the editor again on the composer's text ([needsText]).
 */
@Suppress("LongParameterList") // The chat, its saved state, the AI and its consent, and the reader's language.
@HiltViewModel(assistedFactory = AiEditorViewModel.Factory::class)
class AiEditorViewModel
    @AssistedInject
    constructor(
        @Assisted chatId: Long,
        private val savedStateHandle: SavedStateHandle,
        chats: ChatRepository,
        private val ai: AiTextRepository,
        private val consent: AiEditorConsentRepository,
        private val appLanguage: AppLanguageRepository,
        private val translations: TranslationRepository,
    ) : ViewModel() {
        @AssistedFactory
        interface Factory {
            fun create(chatId: Long): AiEditorViewModel
        }

        /** Whether Telegram's AI may be used here: never in a secret chat, as TDLib requires. */
        val isAvailable: StateFlow<Boolean> =
            chats
                .chat(chatId)
                .map { it != null && it.type !is ChatType.Secret }
                .distinctUntilChanged()
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), false)

        private val original = MutableStateFlow<FormattedText?>(null)
        private val result = MutableStateFlow<AiResult>(AiResult.Idle)
        private val acknowledged = MutableStateFlow(false)
        private var running: Job? = null

        /** True while the saved state says the editor was open but the composer's text is not here yet. */
        val needsText: Boolean get() = savedStateHandle.get<Boolean>(KEY_OPEN) == true && original.value == null

        /** The sheet; null while closed. */
        val sheet: StateFlow<AiEditorUiState?> =
            combine(
                original,
                acknowledged,
                choices(),
                ai.styles,
                result,
            ) { text, read, choices, styles, result ->
                when {
                    text == null -> null
                    !read -> AiEditorUiState.Consent
                    else -> editorState(choices, styles.map { AiStyleUi(it.name, it.title, it.isCustom) }, result)
                }
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), null)

        private val effectChannel = Channel<AiEditorEffect>(Channel.BUFFERED)

        /** What the route does: put the result in the composer. */
        val effects: Flow<AiEditorEffect> = effectChannel.receiveAsFlow()

        fun onEvent(event: AiEditorEvent) {
            when (event) {
                is AiEditorEvent.Open -> {
                    open(event.text)
                }

                AiEditorEvent.AcceptConsent -> {
                    viewModelScope.launch {
                        if (consent.acknowledge() is Outcome.Success) {
                            acknowledged.value =
                                true
                        }
                    }
                }

                is AiEditorEvent.SetMode -> {
                    change(KEY_MODE, event.mode.name)
                }

                is AiEditorEvent.SetStyle -> {
                    change(KEY_STYLE, event.name)
                }

                is AiEditorEvent.SetLanguage -> {
                    change(KEY_LANGUAGE, event.code)
                }

                is AiEditorEvent.SetAddEmoji -> {
                    change(KEY_EMOJI, event.addEmoji)
                }

                AiEditorEvent.Run -> {
                    run()
                }

                AiEditorEvent.Use -> {
                    use()
                }

                AiEditorEvent.Dismiss -> {
                    close()
                }
            }
        }

        private fun open(text: FormattedText) {
            if (!isAvailable.value) return
            savedStateHandle[KEY_OPEN] = true
            result.value = AiResult.Idle
            viewModelScope.launch {
                acknowledged.value = consent.acknowledged.first()
                if (savedStateHandle.get<String>(KEY_LANGUAGE) ==
                    null
                ) {
                    savedStateHandle[KEY_LANGUAGE] = readerLanguage()
                }
                original.value = text
            }
        }

        /** Changes a choice; a result made with the old ones no longer applies. */
        private fun change(
            key: String,
            value: Any?,
        ) {
            running?.cancel()
            savedStateHandle[key] = value
            result.value = AiResult.Idle
        }

        private fun run() {
            // The choices as saved now, not as the sheet last showed them, so a quick tap never sends stale ones.
            val text = original.value
            val state =
                editorState(
                    currentChoices(),
                    ai.styles.value.map { AiStyleUi(it.name, it.title, it.isCustom) },
                    result.value,
                )
            if (text == null || !state.canRun) return
            result.value = AiResult.Working
            running = viewModelScope.launch { result.value = ai.request(text, state) }
        }

        private fun currentChoices(): Choices =
            choicesOf(
                savedStateHandle[KEY_MODE],
                savedStateHandle[KEY_STYLE],
                savedStateHandle[KEY_LANGUAGE],
                savedStateHandle[KEY_EMOJI] ?: false,
            )

        private fun use() {
            val text = original.value ?: return
            val done = result.value as? AiResult.Done ?: return
            effectChannel.trySend(AiEditorEffect.Use(done.text, text))
            close()
        }

        private fun close() {
            running?.cancel()
            savedStateHandle[KEY_OPEN] = false
            original.value = null
            result.value = AiResult.Idle
        }

        private fun choices(): Flow<Choices> =
            combine(
                savedStateHandle.getStateFlow<String?>(KEY_MODE, null),
                savedStateHandle.getStateFlow<String?>(KEY_STYLE, null),
                savedStateHandle.getStateFlow<String?>(KEY_LANGUAGE, null),
                savedStateHandle.getStateFlow(KEY_EMOJI, false),
                ::choicesOf,
            )

        private fun editorState(
            choices: Choices,
            styles: List<AiStyleUi>,
            result: AiResult,
        ): AiEditorUiState.Editor {
            // A rewrite needs a style: the first one until the user picks another; a style that went keeps none.
            val known = choices.style?.takeIf { name -> styles.any { it.name == name } }
            val style = if (choices.mode == AiMode.Rewrite) known ?: styles.firstOrNull()?.name else known
            return AiEditorUiState.Editor(
                mode = choices.mode,
                styles = styles.toImmutableList(),
                styleName = style,
                language = choices.language ?: DEFAULT_LANGUAGE,
                languages = ai.languages,
                addEmoji = choices.emoji,
                result = result,
            )
        }

        /** The language the user reads: Pingogram's, or the system's, as Telegram translates it. */
        private suspend fun readerLanguage(): String {
            val tag = appLanguage.language.first() ?: Locale.getDefault().toLanguageTag()
            return translations.targetLanguageOf(tag) ?: DEFAULT_LANGUAGE
        }

        private companion object {
            const val KEY_OPEN = "ai.open"
            const val KEY_MODE = "ai.mode"
            const val KEY_STYLE = "ai.style"
            const val KEY_LANGUAGE = "ai.language"
            const val KEY_EMOJI = "ai.emoji"
            const val DEFAULT_LANGUAGE = "en"
            const val STOP_TIMEOUT_MILLIS = 5_000L
        }
    }

/** Asks Telegram's AI for what [state] chose, on [text]. */
private suspend fun AiTextRepository.request(
    text: FormattedText,
    state: AiEditorUiState.Editor,
): AiResult =
    when (state.mode) {
        AiMode.Fix -> {
            when (val fixed = fix(text)) {
                is Outcome.Success -> AiResult.Done(fixed.value.text, fixed.value.diff)
                is Outcome.Failure -> AiResult.Failed(fixed.error)
            }
        }

        AiMode.Rewrite, AiMode.Translate -> {
            val translateTo = state.language.takeIf { state.mode == AiMode.Translate }
            when (val composed = compose(text, state.styleName, translateTo, state.addEmoji)) {
                is Outcome.Success -> AiResult.Done(composed.value)
                is Outcome.Failure -> AiResult.Failed(composed.error)
            }
        }
    }

/** The editor's choices as saved. */
private data class Choices(
    val mode: AiMode,
    val style: String?,
    val language: String?,
    val emoji: Boolean,
)

private fun choicesOf(
    mode: String?,
    style: String?,
    language: String?,
    emoji: Boolean,
) = Choices(AiMode.entries.firstOrNull { it.name == mode } ?: AiMode.Fix, style, language, emoji)
