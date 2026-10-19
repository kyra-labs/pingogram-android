package dev.kyralabs.pingogram.feature.chat.ui

import androidx.compose.runtime.Immutable
import dev.kyralabs.pingogram.core.ui.text.UiText
import kotlinx.collections.immutable.ImmutableList

/**
 * The actions sheet of message [messageId] (`REQUIREMENTS.md` FR-C6, FR-C7; `docs/DESIGN_SYSTEM.md` §6 "Message
 * actions"): the quick reactions TDLib offers for it, then what the user may do with it now, as TDLib allows.
 *
 * @property reactionsMessageId the message the quick reactions go to: an album's newest, whose reactions its bubble
 *   shows.
 * @property reactions the quick reactions, TDLib's top ones first; empty where reactions are off.
 * @property actions the actions, in the order the sheet lists them.
 */
@Immutable
data class MessageActionsSheet(
    val messageId: Long,
    val reactionsMessageId: Long,
    val reactions: ImmutableList<QuickReaction>,
    val actions: ImmutableList<MessageAction>,
)

/** A reaction of the sheet's quick row; [isChosen] if the user added it already, which choosing it takes back. */
@Immutable
data class QuickReaction(
    val label: ReactionLabel,
    val isChosen: Boolean,
)

/**
 * The actions every bubble offers screen readers directly (`docs/DESIGN_SYSTEM.md` §9), besides opening its actions
 * sheet: replying where the user can write ([canReply]), copying a text where the chat lets its content be saved
 * ([canCopy]). They are checked against TDLib when chosen, like the sheet's.
 */
@Immutable
data class MessageShortcuts(
    val canReply: Boolean = false,
    val canCopy: Boolean = false,
)

/** What the actions sheet offers for a message, each only where TDLib allows it. */
enum class MessageAction {
    /** Send a message that failed again. */
    Resend,

    /** Reply to it from the composer. */
    Reply,

    /** Copy its text or caption, as plain text. */
    Copy,

    /** Edit its text or caption in the composer. */
    Edit,

    /** Pin it for everyone in the chat. */
    Pin,

    /** Pin it for the user alone (private chats). */
    PinForSelf,

    Unpin,

    /** Delete it, after a confirmation ([DeleteMessagesDialog]). */
    Delete,
}

/**
 * The confirmation before [messageIds] are deleted (an album's every item), with what deleting them does.
 * `docs/DESIGN_SYSTEM.md` §6: an `AlertDialog`, for destructive confirmations only.
 */
@Immutable
data class DeleteMessagesDialog(
    val messageIds: ImmutableList<Long>,
    val scope: DeleteScope,
)

/** For whom messages are deleted. */
@Immutable
sealed interface DeleteScope {
    /** For the user only: the others keep them (TDLib allows no more). */
    data object ForMe : DeleteScope

    /** For everyone in the chat, as always in supergroups, channels and secret chats. */
    data object ForEveryone : DeleteScope

    /**
     * For the user, and for everyone too if they check [option]: "Also delete for Ada" in a private chat, "Delete for
     * everyone" in a group. Unchecked at first.
     */
    data class Choice(
        val option: UiText,
    ) : DeleteScope
}
