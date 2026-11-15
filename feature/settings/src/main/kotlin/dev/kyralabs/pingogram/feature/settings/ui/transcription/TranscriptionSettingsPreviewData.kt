package dev.kyralabs.pingogram.feature.settings.ui.transcription

import kotlinx.collections.immutable.persistentListOf

/** The page's states for previews and screenshot tests. */
internal object TranscriptionSettingsPreviewData {
    val content =
        TranscriptionSettingsUiState.Content(
            automatic = true,
            languages =
                persistentListOf(
                    TranscriptionLanguage("en-IN", LanguageState.Ready),
                    TranscriptionLanguage("hi-IN", LanguageState.Ready),
                    TranscriptionLanguage("de-DE", LanguageState.Downloading),
                    TranscriptionLanguage("ta-IN", LanguageState.NotOnDevice),
                ),
        )

    val languagesLoading = TranscriptionSettingsUiState.Content(automatic = false, languages = null)
}
