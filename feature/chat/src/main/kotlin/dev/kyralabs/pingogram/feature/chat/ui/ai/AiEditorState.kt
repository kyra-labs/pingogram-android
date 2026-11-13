package dev.kyralabs.pingogram.feature.chat.ui.ai

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.domain.model.DiffText
import dev.kyralabs.pingogram.core.domain.model.FormattedText
import kotlinx.collections.immutable.ImmutableList

/** What Telegram's AI does with the composer's text. */
enum class AiMode {
    /** Fixes spelling, grammar and punctuation. */
    Fix,

    /** Rewrites it in one of Telegram's styles. */
    Rewrite,

    /** Translates it, in its own style or another. */
    Translate,
}

/** A style the user can pick: its [name] for Telegram, its [title] to show, and whether a user made it. */
@Immutable
data class AiStyleUi(
    val name: String,
    val title: String,
    val isCustom: Boolean,
)

/** The editor's result. */
@Immutable
sealed interface AiResult {
    /** Nothing asked yet, or the choices changed since. */
    data object Idle : AiResult

    /** Telegram's AI is working. */
    data object Working : AiResult

    /** The [text] to use; for a fix, [diff] shows what changed. */
    data class Done(
        val text: FormattedText,
        val diff: DiffText? = null,
    ) : AiResult

    /** Why it failed. */
    data class Failed(
        val error: DomainError,
    ) : AiResult
}

/** The sheet of the AI editor (`docs/DESIGN_SYSTEM.md` §6 "AI text editor"). */
@Immutable
sealed interface AiEditorUiState {
    /** The one-time explanation before the first use. */
    data object Consent : AiEditorUiState

    /**
     * The editor: the [mode], the [styles] and the one picked ([styleName]; null keeps the text's own while
     * translating), the [language] to translate into among [languages], whether to [addEmoji], and the [result].
     */
    data class Editor(
        val mode: AiMode,
        val styles: ImmutableList<AiStyleUi>,
        val styleName: String?,
        val language: String,
        val languages: ImmutableList<String>,
        val addEmoji: Boolean,
        val result: AiResult,
    ) : AiEditorUiState {
        /** Whether the current choices can be sent: a rewrite needs a style. */
        val canRun: Boolean get() = result != AiResult.Working && (mode != AiMode.Rewrite || styleName != null)
    }
}

/** What the user does in the AI editor. */
sealed interface AiEditorEvent {
    /** The formatting menu's "Edit with AI", with the composer's [text]. */
    data class Open(
        val text: FormattedText,
    ) : AiEditorEvent

    /** The explanation's "Continue". */
    data object AcceptConsent : AiEditorEvent

    data class SetMode(
        val mode: AiMode,
    ) : AiEditorEvent

    data class SetStyle(
        val name: String?,
    ) : AiEditorEvent

    data class SetLanguage(
        val code: String,
    ) : AiEditorEvent

    data class SetAddEmoji(
        val addEmoji: Boolean,
    ) : AiEditorEvent

    /** "Fix", "Rewrite" or "Translate": the one tap that sends the text to Telegram's AI. */
    data object Run : AiEditorEvent

    /** "Use": the result replaces the composer's text. */
    data object Use : AiEditorEvent

    /** The sheet closes; nothing changes. */
    data object Dismiss : AiEditorEvent
}

/** What the route does after the editor. */
sealed interface AiEditorEffect {
    /** Puts [text] in the composer, with "Undo" back to [original] in a snackbar. */
    data class Use(
        val text: FormattedText,
        val original: FormattedText,
    ) : AiEditorEffect
}

/**
 * Opens the AI editor on the composer's text, which the conversation's route provides where Telegram's AI may be used
 * (never in a secret chat); null elsewhere, so the formatting menu offers no "Edit with AI".
 */
internal val LocalAiEditor = compositionLocalOf<((FormattedText) -> Unit)?> { null }
