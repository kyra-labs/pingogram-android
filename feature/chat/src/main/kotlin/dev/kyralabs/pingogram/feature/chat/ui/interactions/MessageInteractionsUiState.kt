package dev.kyralabs.pingogram.feature.chat.ui.interactions

import androidx.annotation.DrawableRes
import androidx.compose.runtime.Immutable
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.domain.model.MessageSender
import dev.kyralabs.pingogram.core.domain.model.ProfilePhoto
import dev.kyralabs.pingogram.core.domain.model.Reaction
import dev.kyralabs.pingogram.core.ui.text.UiText
import dev.kyralabs.pingogram.feature.chat.ui.ReactionLabel
import kotlinx.collections.immutable.ImmutableList

/**
 * What a group message's "Seen by" or "Reactions" opens (`docs/DESIGN_SYSTEM.md` §6 "Seen by and reactions"), as the
 * message's actions hand it over once TDLib allows it: message [messageId] of chat [chatId], whose viewers are listed
 * if [canGetViewers], and [reactionsMessageId] (an album's newest item, whose reactions its bubble shows), whose added
 * [reactions] (each kind with its count, as the bubble has them; the paid reaction left out) are listed where not
 * empty. The sheet opens at the reactions if [opensReactions], else at the viewers.
 */
@Immutable
data class MessageInteractionsRequest(
    val chatId: Long,
    val messageId: Long,
    val reactionsMessageId: Long,
    val canGetViewers: Boolean,
    val reactions: ImmutableList<Reaction>,
    val opensReactions: Boolean,
)

/** A tab of the sheet: who saw the message, every reaction, or the reactions of one kind ([label]). */
@Immutable
sealed interface InteractionsTab {
    data object Seen : InteractionsTab

    data object AllReactions : InteractionsTab

    data class OneReaction(
        val label: ReactionLabel,
    ) : InteractionsTab
}

/**
 * A tab as its button shows it: [tab] with [count] where it is known (the viewers once loaded, the reactions as the
 * bubble counts them).
 */
@Immutable
data class InteractionsTabItem(
    val tab: InteractionsTab,
    val count: Int?,
)

/**
 * The open sheet: its [tabs] (the viewers first, then every reaction and each kind; only those TDLib allows), the
 * [selected] one and what it shows ([content]).
 */
@Immutable
data class MessageInteractionsSheet(
    val tabs: ImmutableList<InteractionsTabItem>,
    val selected: InteractionsTab,
    val content: InteractionsContent,
)

/** What the selected tab shows. */
@Immutable
sealed interface InteractionsContent {
    /** Its first page is on its way. */
    data object Loading : InteractionsContent

    /** Its first page could not be loaded, because of [error]; "Try again" asks again. */
    data class Error(
        val error: DomainError,
    ) : InteractionsContent

    /** Nobody saw the message yet, or nobody is left with the reactions of the tab. */
    data object Empty : InteractionsContent

    /** The [rows] loaded, and whether more follow ([more]). */
    data class Rows(
        val rows: ImmutableList<InteractionRow>,
        val more: MoreInteractions,
    ) : InteractionsContent
}

/** Whether a tab has more rows than it shows. */
@Immutable
sealed interface MoreInteractions {
    /** Every row is loaded. */
    data object None : MoreInteractions

    /** More follow, loaded as the end of the list comes near. */
    data object Available : MoreInteractions

    data object Loading : MoreInteractions

    /** The next page failed with [error]; "Try again" asks again. */
    data class Failed(
        val error: DomainError,
    ) : MoreInteractions
}

/**
 * One person who saw the message or reacted to it: [sender] (a user, or a chat such as a channel the sender reacted
 * as) with its [name], [photo] and [avatarId] for the placeholder's colors ([placeholderIcon] for a deleted account),
 * [time] ("Seen at 9:41 AM", "Reacted yesterday at 9:41 AM"; null where Telegram gives none), and on the tab of every
 * reaction the [reaction] they added. [key] tells rows apart: a sender may add several reactions.
 */
@Immutable
data class InteractionRow(
    val key: String,
    val sender: MessageSender,
    val name: UiText,
    val photo: ProfilePhoto?,
    val avatarId: Long,
    val time: UiText?,
    val reaction: ReactionLabel? = null,
    @param:DrawableRes val placeholderIcon: Int? = null,
) {
    override fun toString() = "InteractionRow(key=$key, sender=$sender, …)"
}
