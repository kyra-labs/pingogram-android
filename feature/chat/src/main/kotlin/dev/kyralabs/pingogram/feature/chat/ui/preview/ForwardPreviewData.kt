package dev.kyralabs.pingogram.feature.chat.ui.preview

import dev.kyralabs.pingogram.core.ui.R
import dev.kyralabs.pingogram.core.ui.text.UiText
import dev.kyralabs.pingogram.feature.chat.ui.forward.ForwardSheet
import dev.kyralabs.pingogram.feature.chat.ui.forward.ForwardTarget
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf

/** Forward sheets for previews and screenshot tests: Saved Messages first, then chats of every kind. */
internal object ForwardPreviewData {
    private const val SAVED_MESSAGES = 1L
    private const val ADA = 2L
    private const val TEAM = 3L
    private const val GRACE_SECRET = 4L
    private const val NEWS = 5L
    private const val ALAN = 6L

    val targets =
        persistentListOf(
            ForwardTarget(
                SAVED_MESSAGES,
                UiText.resource(R.string.chat_saved_messages),
                null,
                R.drawable.ic_bookmark_filled,
            ),
            ForwardTarget(ADA, UiText.Plain("Ada Lovelace"), null, null),
            ForwardTarget(TEAM, UiText.Plain("Analytical Engine team"), null, null),
            ForwardTarget(GRACE_SECRET, UiText.Plain("Grace Hopper"), null, null, isSecret = true),
            ForwardTarget(NEWS, UiText.Plain("Engine news"), null, null),
            ForwardTarget(ALAN, UiText.Plain("Alan Turing"), null, null),
        )

    /** A photo with a caption, nothing picked yet. */
    val sheet = ForwardSheet(messageCount = 1, targets = targets, canHideSenderName = true, hasCaptions = true)

    /** [sheet] with two chats picked and the captions hidden, which hides the sender's name too. */
    val withSelection =
        sheet.copy(selectedIds = persistentSetOf(ADA, TEAM), hideSenderName = true, hideCaptions = true)

    /** Three messages without captions, Saved Messages picked: no "Hide captions". */
    val album = sheet.copy(messageCount = 3, hasCaptions = false, selectedIds = persistentSetOf(SAVED_MESSAGES))

    /** A search that found nothing. */
    val nothingFound = ForwardSheet(messageCount = 1, isSearching = true)
}
