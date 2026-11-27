package dev.kyralabs.pingogram.feature.chat.ui

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import dev.kyralabs.pingogram.core.domain.model.Chat
import dev.kyralabs.pingogram.core.domain.model.ChatType
import dev.kyralabs.pingogram.core.domain.model.TextFindings
import dev.kyralabs.pingogram.core.ui.text.UiText
import dev.kyralabs.pingogram.feature.chat.R
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.ImmutableSet
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
 * @property link the address of the link of the message's text the long press landed on, which [MessageAction.OpenLink]
 *   and [MessageAction.CopyPressedLink] act on; null for none.
 * @property findings the date or time and the address the device found in the message's text, which
 *   [MessageAction.AddToCalendar] and [MessageAction.OpenInMaps] act on.
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
    val link: String? = null,
    val findings: TextFindings = TextFindings.None,
) {
    /** Leaves the link out, as the logs must. */
    override fun toString() = "MessageActionsSheet(messageId=$messageId, actions=$actions, showsPicker=$showsPicker, …)"
}

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
 * ([canCopy]), replying in another chat where the chat's content may leave it ([canReplyElsewhere]), copying a
 * message's link in supergroups and channels ([canCopyLink]; to it in this topic or thread where [linksInThread]),
 * forwarding a channel post from its share button where the chat lets its messages be forwarded ([canForward]: no
 * protected content, not a secret chat). They are checked against TDLib when chosen, like the sheet's.
 */
@Immutable
data class MessageShortcuts(
    val canReply: Boolean = false,
    val canCopy: Boolean = false,
    val canReplyElsewhere: Boolean = false,
    val canCopyLink: Boolean = false,
    val linksInThread: Boolean = false,
    val canForward: Boolean = false,
    val canRemind: Boolean = false,
)

/** What the actions sheet offers for a message, each only where TDLib allows it. */
enum class MessageAction {
    /** Open the link of its text the long press landed on ([MessageActionsSheet.link]). */
    OpenLink,

    /** Copy the address of that link. */
    CopyPressedLink,

    /** List the group's members who saw it, with when ("Seen by", `MessageInteractionsViewModel`). */
    SeenBy,

    /** List who added which reaction to it ("Reactions", `MessageInteractionsViewModel`). */
    Reactions,

    /** Send a message that failed again. */
    Resend,

    /** Reply to it from the composer. */
    Reply,

    /** Reply to it from another chat's composer, picked in a sheet ("Reply in another chat"). */
    ReplyElsewhere,

    /** Set a reminder on it, kept on this device (`reminders.ReminderSheetViewModel`, the owner's board 29). */
    RemindMe,

    /** Open its poll's statistics, where Telegram gives them to the chat's administrators. */
    PollStatistics,

    /** Copy its text or caption, as plain text. */
    Copy,

    /** Read its text or caption aloud on the device (Listen to posts, `listen.ListenViewModel`). */
    Listen,

    /** Copy its link (supergroups and channels; an album's link for an album's item). */
    CopyLink,

    /** Copy its link in the conversation's forum topic or message thread. */
    CopyThreadLink,

    /** Show Telegram's translation of its text or caption in its bubble (`ChatTranslation`). */
    Translate,

    /** Show its own text again in place of the translation its bubble shows. */
    ShowOriginal,

    /** Open the calendar's new event for the date or time found in its text ([MessageActionsSheet.findings]). */
    AddToCalendar,

    /** Open the address found in its text in a maps app. */
    OpenInMaps,

    /** Show Telegram's summary of a long message in its bubble (`ChatSummaries`). */
    Summarize,

    /** Show its own text again in place of the summary its bubble shows. */
    ShowFullText,

    /** Forward it to other chats, from the forward sheet. */
    Forward,

    /** Bookmark it on this device, with tags and a note (`bookmarks.BookmarkSheetViewModel`, the owner's board 34). */
    Bookmark,

    /** Edit its text or caption in the composer. */
    Edit,

    /** Pin it for everyone in the chat. */
    Pin,

    /** Pin it for the user alone (private chats). */
    PinForSelf,

    Unpin,

    /** Take the user's vote in its poll back (`ChatPolls`), where the poll allows it. */
    RetractVote,

    /** Close its poll for everyone, after a question (`ChatPolls`): its author only. */
    StopPoll,

    /** Copy its file (an album's every file) into the public Downloads collection. */
    SaveToDownloads,

    /** Select it, which starts selection mode ([MessageSelection]). */
    Select,

    /** Report it to Telegram's moderators, with the reasons they ask for ([ReportSheet]). */
    Report,

    /** Delete it, after a confirmation ([DeleteMessagesDialog]). */
    Delete,
}

/** Opening and copying the link a long press landed on, which menus group under its address. */
internal val MessageAction.isLinkAction: Boolean
    get() = this == MessageAction.OpenLink || this == MessageAction.CopyPressedLink

/** The menu's groups: the pressed link's actions, the everyday ones, then reporting and deleting; none empty. */
internal fun actionGroups(actions: List<MessageAction>): List<List<MessageAction>> =
    listOf(
        actions.filter { it.isLinkAction },
        actions.filter { !it.isLinkAction && !it.isDestructive },
        actions.filter { it.isDestructive },
    ).filter { it.isNotEmpty() }

/** The label of this action, "Copy message link" for the message's link where a pressed [link] has "Copy link". */
@StringRes
internal fun MessageAction.labelWith(link: String?): Int =
    if (this == MessageAction.CopyLink && link != null) R.string.message_action_copy_message_link else label

/**
 * Selection mode (`REQUIREMENTS.md` FR-C6 "select"; `docs/DESIGN_SYSTEM.md` §6 "Selection mode"): the selected
 * [messageIds] among the loaded messages (an album's every item), at least one, and what the selection toolbar offers
 * for all of them ([actions], in its order: copying, forwarding, reporting, deleting), as TDLib allows for each.
 */
@Immutable
data class MessageSelection(
    val messageIds: ImmutableSet<Long>,
    val actions: ImmutableList<MessageAction>,
)

/**
 * Reporting messages to Telegram's moderators (`REQUIREMENTS.md` FR-C6 "report"), a sheet: the [step] TDLib asks for
 * (null until it answers), [isReporting] while a report is on its way.
 */
@Immutable
data class ReportSheet(
    val step: ReportStep? = null,
    val isReporting: Boolean = false,
)

/** What a report asks the user for next. */
@Immutable
sealed interface ReportStep {
    /** A reason: [title] asks, and the user picks one of [options]. */
    data class Options(
        val title: String,
        val options: ImmutableList<String>,
    ) : ReportStep {
        override fun toString() = "Options(${options.size})"
    }

    /** A comment that says more, which may be left empty if [isOptional]. */
    data class Comment(
        val isOptional: Boolean,
    ) : ReportStep
}

/**
 * The confirmation before [messageIds] are deleted (an album's every item, or the selected messages if
 * [fromSelection], whose selection mode deleting them ends), with what deleting them does. `docs/DESIGN_SYSTEM.md`
 * §6: an `AlertDialog`, for destructive confirmations only.
 */
@Immutable
data class DeleteMessagesDialog(
    val messageIds: ImmutableList<Long>,
    val scope: DeleteScope,
    val fromSelection: Boolean = false,
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

/**
 * What screen readers and the bubbles get directly in this chat: replying where the user can write ([canWrite]),
 * copying, replying in another chat and forwarding where its content may leave it (not secret chats for the latter
 * two), message links in supergroups and channels, to the message in the topic or thread where the conversation is
 * one ([inThread]), and reminders, which secret chats leave out as the notification would show their messages.
 */
internal fun Chat.shortcuts(
    canWrite: Boolean,
    inThread: Boolean,
) = MessageShortcuts(
    canReply = canWrite,
    canCopy = !hasProtectedContent,
    canReplyElsewhere = !hasProtectedContent && type !is ChatType.Secret,
    canForward = !hasProtectedContent && type !is ChatType.Secret,
    canCopyLink = type is ChatType.Supergroup,
    linksInThread = inThread,
    canRemind = type !is ChatType.Secret,
)
