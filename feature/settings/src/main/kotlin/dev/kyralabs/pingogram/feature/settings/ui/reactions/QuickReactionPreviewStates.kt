package dev.kyralabs.pingogram.feature.settings.ui.reactions

import dev.kyralabs.pingogram.core.domain.model.DoubleTapReactions
import kotlinx.collections.immutable.persistentListOf

/** The states of the double-tap page, shared by previews and screenshot tests. */
internal object QuickReactionPreviewStates {
    private val reactions =
        persistentListOf(
            "👍",
            "👎",
            "❤",
            "🔥",
            "🥰",
            "👏",
            "😁",
            "🤔",
            "🤯",
            "😱",
            "🤬",
            "😢",
            "🎉",
            "🤩",
            "🤮",
            "💩",
            "🙏",
            "👌",
        )

    val content =
        QuickReactionUiState.Content(
            reactions = reactions,
            chosen = "❤",
            isCustomEmoji = false,
            doubleTap = DoubleTapReactions(incoming = true, outgoing = false),
        )

    val customEmoji = content.copy(chosen = null, isCustomEmoji = true, doubleTap = DoubleTapReactions())

    val loading = QuickReactionUiState.Loading
}
