package dev.kyralabs.pingogram.feature.chat.ui.forward

import androidx.annotation.DrawableRes
import androidx.compose.runtime.Immutable
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.domain.model.ProfilePhoto
import dev.kyralabs.pingogram.core.ui.text.UiText
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf

/**
 * The forward sheet of [messageCount] messages (`REQUIREMENTS.md` FR-C6; `docs/DESIGN_SYSTEM.md` §6 "Forward sheet"):
 * the chats they can go to, which the user picks, and how they go.
 *
 * @property targets the chats shown: without a query Saved Messages first, then the main chat list in its order, as
 *   far as TDLib loaded it; with one, the chats search found. Only chats where the user can send the messages.
 * @property selectedIds the chats picked, in the order they were picked; they stay picked while a search hides them.
 * @property isSearching the list shows what a query found rather than the chat list.
 * @property isLoading the list's first chats, or a query's results, are on their way.
 * @property searchError the search failed; shown in place of the results.
 * @property canHideSenderName every message can be sent as a copy, which "Hide sender's name" needs.
 * @property hasCaptions some message has a caption, which "Hide captions" leaves out.
 * @property hideSenderName the messages go as copies, without "Forwarded from".
 * @property hideCaptions the copies go without their captions; only with [hideSenderName].
 */
@Immutable
data class ForwardSheet(
    val messageCount: Int,
    val targets: ImmutableList<ForwardTarget> = persistentListOf(),
    val selectedIds: ImmutableSet<Long> = persistentSetOf(),
    val isSearching: Boolean = false,
    val isLoading: Boolean = false,
    val searchError: DomainError? = null,
    val canHideSenderName: Boolean = false,
    val hasCaptions: Boolean = false,
    val hideSenderName: Boolean = false,
    val hideCaptions: Boolean = false,
)

/**
 * A chat the messages can be forwarded to, as a row of the forward sheet shows it.
 *
 * @property chatId the chat, also the avatar's id, which picks its placeholder.
 * @property title the chat's name; "Saved Messages" and "Deleted Account" come from resources.
 * @property photo the avatar's photo; null shows the placeholder.
 * @property placeholderIcon a Material Symbol on the avatar placeholder instead of initials: the bookmark of Saved
 *   Messages, the person of a deleted account.
 * @property isSecret a secret chat, marked with a lock.
 */
@Immutable
data class ForwardTarget(
    val chatId: Long,
    val title: UiText,
    val photo: ProfilePhoto?,
    @param:DrawableRes val placeholderIcon: Int?,
    val isSecret: Boolean = false,
) {
    override fun toString() = "ForwardTarget(chatId=$chatId, …)"
}
