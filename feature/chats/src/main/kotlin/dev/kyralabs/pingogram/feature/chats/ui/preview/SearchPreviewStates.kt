package dev.kyralabs.pingogram.feature.chats.ui.preview

import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import dev.kyralabs.pingogram.feature.chats.ui.SearchUiState

/** Every state of global search for its previews, named in the preview pane. */
internal class SearchPreviewStates : PreviewParameterProvider<SearchUiState> {
    private val states =
        listOf(
            "Suggestions" to SearchPreviewData.suggestions,
            "Hint" to SearchPreviewData.hint,
            "Results" to SearchPreviewData.results,
            "Searching" to SearchPreviewData.loading,
            "Section error" to SearchPreviewData.error,
            "No results" to SearchPreviewData.noResults,
        )

    override val values: Sequence<SearchUiState> = states.asSequence().map { it.second }

    override fun getDisplayName(index: Int): String = states[index].first
}
