package dev.kyralabs.pingogram.feature.settings.ui.mutedwords

import dev.kyralabs.pingogram.core.domain.model.MutedWordSettings
import kotlinx.collections.immutable.persistentListOf

/** The page's states for previews and goldens: on with words, off without any, and the most words. */
internal object MutedWordsPreviewStates {
    val words =
        MutedWordsUiState(
            MutedWordSettings(enabled = true, words = persistentListOf("spoiler", "giveaway", "crypto", "剧透")),
        )

    val empty = MutedWordsUiState(MutedWordSettings())

    val full =
        MutedWordsUiState(
            MutedWordSettings(
                enabled = true,
                words = persistentListOf(*Array(MutedWordSettings.MAX_WORDS) { "word ${it + 1}" }),
            ),
        )
}
