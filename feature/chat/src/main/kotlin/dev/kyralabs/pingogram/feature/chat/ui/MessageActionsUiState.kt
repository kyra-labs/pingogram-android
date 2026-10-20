package dev.kyralabs.pingogram.feature.chat.ui

import androidx.compose.runtime.Immutable
import dev.kyralabs.pingogram.core.ui.text.UiText
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentMapOf

/**
 * The actions sheet of message [messageId] (`REQUIREMENTS.md` FR-C6, FR-C7; `docs/DESIGN_SYSTEM.md` §6 "Message
 * actions", "Reactions picker"): the quick reactions TDLib offers for it, then what the user may do with it now, as
 * TDLib allows; or, while [showsPicker], the reactions picker with every reaction the user can add.
 *
 * @property reactionsMessageId the message the reactions go to: an album's newest, whose reactions its bubble shows.
 * @property reactions the quick reactions, TDLib's top ones first; empty where reactions are off.
 * @property actions the actions, in the order the sheet lists them.
 * @property picker the reactions picker; null where it would show nothing the quick reactions and the bubble do not.
 * @property showsPicker the sheet shows the [picker] instead of the quick reactions and the actions.
 * @property customEmojiPictures the TDLib file of a picture of each custom emoji the sheet shows, by custom emoji id:
 *   the sticker itself if it is a still, else its thumbnail if that is a picture. Comes after the sheet opens; a custom
 *   emoji without one shows a placeholder.
 */
@Immutable
data class MessageActionsSheet(
    val messageId: Long,
    val reactionsMessageId: Long,
    val reactions: ImmutableList<ReactionOption>,
    val actions: ImmutableList<MessageAction>,
    val picker: ReactionPicker? = null,
    val showsPicker: Boolean = false,
    val customEmojiPictures: ImmutableMap<Long, Int> = persistentMapOf(),
)

/**
 * A reaction the sheet offers, in its quick row or its picker; [isChosen] if the user added it already, which choosing
 * it takes back.
 */
@Immutable
data class ReactionOption(
    val label: ReactionLabel,
    val isChosen: Boolean,
)

/**
 * The reactions picker (`REQUIREMENTS.md` FR-C7): the reactions already on the message, all of them, beyond the badges
 * its bubble has room for, and then every reaction the user can add, by [sections].
 *
 * @property onMessage the message's reactions as its bubble shows them; empty if it has none.
 * @property sections the reactions the user can add, by kind, each section with at least one reaction and each
 *   reaction in one section only.
 */
@Immutable
data class ReactionPicker(
    val onMessage: ImmutableList<ReactionChip>,
    val sections: ImmutableList<ReactionSection>,
)

/** The reactions of one [kind] in the reactions picker, in TDLib's order. */
@Immutable
data class ReactionSection(
    val kind: ReactionSectionKind,
    val reactions: ImmutableList<ReactionOption>,
)

/** Where the reactions of a [ReactionSection] come from (TDLib's `availableReactions`). */
enum class ReactionSectionKind {
    /** The most used in the chat, which the quick row shows too. */
    Top,

    /** The ones the user used recently. */
    Recent,

    /** The ones most used on Telegram. */
    Popular,
}

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

    /** Forward it to other chats, from the forward sheet. */
    Forward,

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
