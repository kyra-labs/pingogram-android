package dev.kyralabs.pingogram.feature.chat.ui.preview

import dev.kyralabs.pingogram.core.ui.text.UiText
import dev.kyralabs.pingogram.core.ui.text.bidiIsolated
import dev.kyralabs.pingogram.feature.chat.ui.ChatUiState
import dev.kyralabs.pingogram.feature.chat.ui.pinned.PinnedBar
import dev.kyralabs.pingogram.feature.chat.ui.pinned.PinnedRow
import dev.kyralabs.pingogram.feature.chat.ui.pinned.PinnedSheet
import kotlinx.collections.immutable.persistentListOf

/**
 * Pinned messages for previews and screenshot tests: one in a private chat, which the user may unpin, and five in a
 * group, the second of them shown, which the user may only hide.
 */
internal object PinnedPreviewData {
    val one =
        PinnedBar(
            messageId = 30,
            index = 0,
            count = 1,
            sender = UiText.Plain("Ada Lovelace").bidiIsolated(),
            preview = UiText.Plain("The meetup moved to Thursday, 7 PM, same place."),
            canUnpin = true,
        )

    val many =
        PinnedBar(
            messageId = 21,
            index = 1,
            count = 5,
            sender = UiText.Plain("Grace Hopper").bidiIsolated(),
            preview = UiText.Plain("Read the rules before posting, and keep it friendly"),
            canUnpin = false,
        )

    val sheet =
        PinnedSheet(
            rows =
                persistentListOf(
                    row(30, "Ada Lovelace", "The meetup moved to Thursday, 7 PM, same place."),
                    row(21, "Grace Hopper", "Read the rules before posting, and keep it friendly"),
                    row(14, null, "Welcome to Compose Developers!"),
                    row(9, "Alan Turing", "Slides from the last talk"),
                    row(3, "Grace Hopper", "Ask your questions in the threads, not here"),
                ),
            shownId = 21,
        )

    /** The group conversation with [many] pinned messages. */
    val groupWithPins: ChatUiState get() = ChatPreviewData.groupChat.copy(pinnedBar = many)

    /** The private conversation with [one] pinned message. */
    val privateWithPin: ChatUiState get() = ChatPreviewData.privateChat.copy(pinnedBar = one)

    private fun row(
        id: Long,
        sender: String?,
        text: String,
    ) = PinnedRow(id, sender?.let { UiText.Plain(it).bidiIsolated() }, UiText.Plain(text))
}
