package dev.kyralabs.pingogram.feature.settings.ui.shownfirst

import kotlinx.collections.immutable.persistentListOf

/** The states of Chats shown first for previews and screenshot tests. */
internal object ChatsShownFirstPreviewStates {
    val content =
        ChatsShownFirstUiState.Content(
            chats =
                persistentListOf(
                    ShownFirstRow(ADA, "Ada Lovelace", null),
                    ShownFirstRow(TEAM, "Analytical Engine team", null),
                    ShownFirstRow(GRACE, "Grace Hopper", null),
                ),
            canAdd = true,
        )

    val empty = ChatsShownFirstUiState.Content(chats = persistentListOf(), canAdd = true)

    val picker =
        ShownFirstPickerUiState(
            query = "",
            candidates =
                persistentListOf(
                    ShownFirstCandidate(ALAN, "Alan Turing", null, selected = true),
                    ShownFirstCandidate(CHARLES, "Charles Babbage", null, selected = false),
                    ShownFirstCandidate(KATHERINE, "Katherine Johnson", null, selected = false),
                ),
            slots = 2,
        )

    private const val ADA = 1L
    private const val TEAM = -100L
    private const val GRACE = 3L
    private const val ALAN = 4L
    private const val CHARLES = 5L
    private const val KATHERINE = 6L
}
