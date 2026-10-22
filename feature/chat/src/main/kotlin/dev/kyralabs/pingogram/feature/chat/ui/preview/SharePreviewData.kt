package dev.kyralabs.pingogram.feature.chat.ui.preview

import dev.kyralabs.pingogram.core.domain.model.AttachmentKind
import dev.kyralabs.pingogram.feature.chat.ui.share.ShareSheet
import kotlinx.collections.immutable.persistentSetOf

/** Share sheets for previews and screenshot tests, with the forward sheet's chats ([ForwardPreviewData]). */
internal object SharePreviewData {
    private const val ADA = 2L
    private const val TEAM = 3L

    /** A shared link, nothing picked yet. */
    val link = ShareSheet(text = "https://developer.android.com/design", targets = ForwardPreviewData.targets)

    /** [link] with one chat picked: the button opens it. */
    val oneChat = link.copy(selectedIds = persistentSetOf(ADA))

    /** Three photos shared to two chats, with the comment field. */
    val photosToTwoChats =
        ShareSheet(
            fileCount = 3,
            fileKind = AttachmentKind.Media,
            targets = ForwardPreviewData.targets,
            selectedIds = persistentSetOf(ADA, TEAM),
        )

    /** A search that found nothing. */
    val nothingFound = ShareSheet(fileCount = 1, isSearching = true)
}
