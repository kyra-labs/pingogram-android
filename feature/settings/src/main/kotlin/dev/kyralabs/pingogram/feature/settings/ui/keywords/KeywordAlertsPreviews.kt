package dev.kyralabs.pingogram.feature.settings.ui.keywords

import androidx.compose.runtime.Composable
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreview
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews
import kotlinx.collections.immutable.persistentSetOf

@PingogramPreviews
@Composable
private fun KeywordAlertsScreenPreview() {
    PingogramPreview { KeywordAlertsScreen(KeywordAlertsPreviewStates.words, onEvent = {}, onBack = {}) }
}

@PingogramPreviews
@Composable
private fun KeywordAlertsScreenEmptyPreview() {
    PingogramPreview { KeywordAlertsScreen(KeywordAlertsPreviewStates.empty, onEvent = {}, onBack = {}) }
}

@PingogramPreviews
@Composable
private fun KeywordAlertsScreenFullPreview() {
    PingogramPreview { KeywordAlertsScreen(KeywordAlertsPreviewStates.full, onEvent = {}, onBack = {}) }
}

@PingogramPreviews
@Composable
private fun KeywordAlertsScreenLoadingPreview() {
    PingogramPreview { KeywordAlertsScreen(KeywordAlertsUiState(), onEvent = {}, onBack = {}) }
}

@PingogramPreviews
@Composable
private fun KeywordFoldersPreview() {
    PingogramPreview {
        KeywordFoldersContent(KeywordAlertsPreviewStates.words, persistentSetOf(1), onEvent = {})
    }
}
