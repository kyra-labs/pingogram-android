package dev.kyralabs.pingogram.feature.chat.ui.preview

import dev.kyralabs.pingogram.core.ui.text.UiText
import dev.kyralabs.pingogram.feature.chat.R
import dev.kyralabs.pingogram.feature.chat.ui.ChatHeader
import dev.kyralabs.pingogram.feature.chat.ui.ChatUiState
import dev.kyralabs.pingogram.feature.chat.ui.GroupPosition
import dev.kyralabs.pingogram.feature.chat.ui.composer.ComposerUiState
import dev.kyralabs.pingogram.feature.chat.ui.preview.ChatPreviewData.bubble
import dev.kyralabs.pingogram.feature.chat.ui.preview.ChatPreviewData.content
import dev.kyralabs.pingogram.feature.chat.ui.saved.SavedTab
import dev.kyralabs.pingogram.feature.chat.ui.saved.SavedTabsUiState
import kotlinx.collections.immutable.persistentListOf
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * Saved Messages with its tabs for previews and screenshot tests: "All", "My Notes" and three chats, the tab of the
 * messages saved from a channel selected, whose composer says where new messages go.
 */
internal object SavedPreviewData {
    val tabs =
        SavedTabsUiState(
            tabs =
                persistentListOf(
                    SavedTab(null, UiText.resource(R.string.saved_tab_all)),
                    SavedTab(MY_NOTES, UiText.resource(R.string.saved_tab_my_notes)),
                    SavedTab(CHANNEL, UiText.Plain("Android Developers")),
                    SavedTab(ADA, UiText.Plain("Ada Lovelace")),
                    SavedTab(HIDDEN, UiText.resource(R.string.saved_tab_author_hidden)),
                ),
            selectedTopicId = CHANNEL,
        )

    /** The tab of the messages saved from Android Developers. */
    val savedFromChannel: ChatUiState
        get() =
            ChatUiState(
                composer = ComposerUiState.Unavailable(UiText.resource(R.string.composer_saved_topic)),
                header =
                    ChatHeader(
                        chatId = ME,
                        title = UiText.resource(UiR.string.chat_saved_messages),
                        photo = null,
                        placeholderIcon = UiR.drawable.ic_bookmark_filled,
                        subtitle = null,
                    ),
                content =
                    content(
                        bubble(
                            3,
                            "Jetpack Compose 1.12 is stable, with faster lists and new shared element APIs.",
                            time = "9:41 AM",
                            isOutgoing = true,
                            forwardedFrom = UiText.Plain("Android Developers"),
                            position = GroupPosition.Last,
                        ),
                        bubble(
                            2,
                            "Material 3 Expressive: what's new in components and motion.",
                            time = "Yesterday",
                            isOutgoing = true,
                            forwardedFrom = UiText.Plain("Android Developers"),
                            position = GroupPosition.First,
                        ),
                    ),
            )

    private const val ME = 1L
    private const val MY_NOTES = 1L
    private const val CHANNEL = 2L
    private const val ADA = 3L
    private const val HIDDEN = 4L
}
