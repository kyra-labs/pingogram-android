package dev.kyralabs.pingogram.feature.chat.ui.ai

import androidx.compose.runtime.Composable
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.domain.model.DiffChange
import dev.kyralabs.pingogram.core.domain.model.DiffKind
import dev.kyralabs.pingogram.core.domain.model.DiffText
import dev.kyralabs.pingogram.core.domain.model.FormattedText
import dev.kyralabs.pingogram.core.domain.model.TextCompositionExample
import dev.kyralabs.pingogram.core.domain.model.TextCompositionStyle
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreview
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews
import kotlinx.collections.immutable.persistentListOf

/** The AI editor's and shared styles' sheets as the previews and goldens show them. */
internal object AiEditorPreviewData {
    private fun text(text: String) = FormattedText(text, persistentListOf())

    private val styles =
        persistentListOf(
            AiStyleUi("formal", "Formal", isCustom = false),
            AiStyleUi("friendly", "Friendly", isCustom = false),
            AiStyleUi("short", "Short", isCustom = false),
            AiStyleUi("pirate", "Pirate", isCustom = true),
        )

    private val editor =
        AiEditorUiState.Editor(
            mode = AiMode.Fix,
            styles = styles,
            styleName = null,
            language = "de",
            languages = persistentListOf("de", "en", "es", "fr"),
            addEmoji = false,
            result = AiResult.Idle,
        )

    private const val FIXED = "Hi Ada! I wanted to ask whether the meetup is still on for tonight."

    val fixed =
        editor.copy(
            result =
                AiResult.Done(
                    text(FIXED),
                    DiffText(
                        "Hi Ada! I wanted to ask wether whether the meetup is still on for tonight.",
                        persistentListOf(
                            DiffChange(6, 1, DiffKind.Insert),
                            DiffChange(25, 6, DiffKind.Delete),
                            DiffChange(32, 7, DiffKind.Insert),
                        ),
                    ),
                ),
        )

    val rewrite =
        editor.copy(
            mode = AiMode.Rewrite,
            styleName = "friendly",
            addEmoji = true,
            result = AiResult.Done(text("Hey Ada! 👋 Is the meetup still happening tonight? Can't wait! 🎉")),
        )

    val translating = editor.copy(mode = AiMode.Translate, result = AiResult.Working)

    val limit = editor.copy(result = AiResult.Failed(DomainError.AiEditLimitReached))

    val style =
        TextStyleUiState.Content(
            style =
                TextCompositionStyle(
                    name = "pirate",
                    title = "Pirate",
                    isCustom = true,
                    installCount = 1204,
                    example =
                        TextCompositionExample(
                            text("See you at the meetup tonight."),
                            text("Ahoy! Meet ye at the gatherin' when the sun goes down, matey."),
                        ),
                ),
            isAdded = false,
            limit = 3,
            busy = false,
            error = null,
        )
}

@PingogramPreviews
@Composable
private fun AiConsentPreview() {
    PingogramPreview { AiConsent(onEvent = {}) }
}

@PingogramPreviews
@Composable
private fun AiEditorFixedPreview() {
    PingogramPreview { AiEditor(AiEditorPreviewData.fixed, onEvent = {}) }
}

@PingogramPreviews
@Composable
private fun AiEditorRewritePreview() {
    PingogramPreview { AiEditor(AiEditorPreviewData.rewrite, onEvent = {}) }
}

@PingogramPreviews
@Composable
private fun AiEditorTranslatingPreview() {
    PingogramPreview { AiEditor(AiEditorPreviewData.translating, onEvent = {}) }
}

@PingogramPreviews
@Composable
private fun AiEditorLimitPreview() {
    PingogramPreview { AiEditor(AiEditorPreviewData.limit, onEvent = {}) }
}
