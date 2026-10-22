package dev.kyralabs.pingogram.feature.chat.ui.share

import androidx.compose.runtime.Immutable
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.domain.model.AttachmentKind
import dev.kyralabs.pingogram.feature.chat.ui.forward.ForwardTarget
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf

/**
 * The share sheet (`REQUIREMENTS.md` FR-N2, direct share; `docs/DESIGN_SYSTEM.md` §6 "Share sheet"): what is shared,
 * the chats it can go to, which the user picks.
 *
 * @property text the start of the shared text, at most [ShareSummary.MAX_TEXT] characters on one line; empty without.
 * @property fileCount how many files are shared, [fileKind] saying whether they go as photos and videos or as files.
 * @property targets the chats shown: without a query Saved Messages first, then the main chat list in its order, as far
 *   as TDLib loaded it; with one, the chats search found. Only chats where the user can send messages.
 * @property selectedIds the chats picked, in the order they were picked; they stay picked while a search hides them.
 *   One opens that chat with the share in its composer; several get it at once, after the comment.
 * @property isSearching the list shows what a query found rather than the chat list.
 * @property isLoading the list's first chats, or a query's results, are on their way.
 * @property searchError the search failed; shown in place of the results.
 */
@Immutable
data class ShareSheet(
    val text: String = "",
    val fileCount: Int = 0,
    val fileKind: AttachmentKind = AttachmentKind.Document,
    val targets: ImmutableList<ForwardTarget> = persistentListOf(),
    val selectedIds: ImmutableSet<Long> = persistentSetOf(),
    val isSearching: Boolean = false,
    val isLoading: Boolean = false,
    val searchError: DomainError? = null,
) {
    override fun toString() = "ShareSheet(textLength=${text.length}, files=$fileCount, targets=${targets.size}, …)"
}

/** How a shared text is summed up in the sheet. */
internal object ShareSummary {
    /** The most characters of the shared text the sheet shows. */
    const val MAX_TEXT = 200

    /** The start of [text] on one line, its line breaks as spaces. */
    fun of(text: String): String = text.trim().take(MAX_TEXT).replace(Regex("\\s+"), " ")
}

/** How the share sheet closed: dismissed, or sent to several chats ([Done]), or [OpenChat] for the one chat picked. */
sealed interface ShareClose {
    /** Dismissed, sent to several chats, or there was nothing to share: the sheet leaves. */
    data object Done : ShareClose

    /** The one chat picked, [chatId], opens with the share in its composer. */
    data class OpenChat(
        val chatId: Long,
    ) : ShareClose
}

/** What the user does in the share sheet, sent to [ShareViewModel.onEvent]. */
sealed interface ShareEvent {
    /** The search field holds [query] now: the chats it finds replace the list once it stays so. */
    data class QueryChanged(
        val query: String,
    ) : ShareEvent {
        override fun toString() = "QueryChanged(…)"
    }

    /** A chat was tapped: it is picked, or unpicked if it was. */
    data class ToggleTarget(
        val chatId: Long,
    ) : ShareEvent

    /** The list comes near its end: the next page of the chat list loads. */
    data object LoadMore : ShareEvent

    /** The button: the one chat picked opens with the share, or the share goes to the chats picked. */
    data object Send : ShareEvent

    /** The sheet was dismissed: nothing is sent. */
    data object Dismiss : ShareEvent
}
