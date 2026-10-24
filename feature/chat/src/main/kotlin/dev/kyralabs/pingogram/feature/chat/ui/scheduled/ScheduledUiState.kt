package dev.kyralabs.pingogram.feature.chat.ui.scheduled

import androidx.compose.runtime.Immutable
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.ui.text.UiText
import dev.kyralabs.pingogram.feature.chat.ui.ChatItem
import dev.kyralabs.pingogram.feature.chat.ui.composer.ComposerUiState
import kotlinx.collections.immutable.ImmutableList

/**
 * "3 scheduled messages" at the top of the conversation's sheet (`REQUIREMENTS.md` FR-C5): the chat holds [count]
 * messages the user scheduled, at least one, which a tap lists; [next] says when the first goes out ("Next: Today,
 * 9:41 AM", "When Ada is online").
 */
@Immutable
data class ScheduledBar(
    val count: Int,
    val next: UiText,
) {
    override fun toString() = "ScheduledBar(count=$count, …)"
}

/**
 * When a scheduled message goes out, as its bubble's meta shows it in the list of scheduled messages: at the bubble's
 * time ([At]), again every [repeatPeriod] seconds where that is more than 0; or once the other user comes online
 * ([WhenOnline], [user] their name, null while unknown).
 */
@Immutable
sealed interface BubbleSchedule {
    data class At(
        val repeatPeriod: Int = 0,
    ) : BubbleSchedule

    data class WhenOnline(
        val user: UiText?,
    ) : BubbleSchedule {
        override fun toString() = "WhenOnline(…)"
    }
}

/**
 * The scheduled messages of a chat (`REQUIREMENTS.md` FR-C5, `docs/DESIGN_SYSTEM.md` §6 "Scheduled messages").
 *
 * @property content the messages, or why there are none.
 * @property actions the actions sheet of a tapped or long-pressed message; null while none is open.
 * @property deleteDialog the message whose deletion waits for a confirmation; null while none does.
 * @property reschedule the message whose new time the date and time pickers ask for; null while they are closed.
 * @property composer the composer while a message is edited ([ComposerUiState.Available] with its edit bar); hidden
 *   otherwise, as the screen sends nothing new.
 * @property canCopy the chat lets its content be saved: screen readers get "Copy" on every bubble with a text.
 */
@Immutable
data class ScheduledMessagesUiState(
    val content: ScheduledContent = ScheduledContent.Loading,
    val actions: ScheduledActionsSheet? = null,
    val deleteDialog: ScheduledDeleteDialog? = null,
    val reschedule: RescheduleDialog? = null,
    val composer: ComposerUiState = ComposerUiState.Hidden,
    val canCopy: Boolean = false,
)

/** What the list of scheduled messages shows. */
@Immutable
sealed interface ScheduledContent {
    data object Loading : ScheduledContent

    /** None, or none left: every one was sent or deleted. */
    data object Empty : ScheduledContent

    /** The list could not be loaded because of [error]; "Try again" loads it again. */
    data class Error(
        val error: DomainError,
    ) : ScheduledContent

    /**
     * The messages as the conversation's entries, latest send time first for the list's reverse layout: bubbles under
     * date separators by their send day, those sent when the other user comes online last, under their own separator.
     */
    data class Content(
        val items: ImmutableList<ChatItem>,
    ) : ScheduledContent
}

/**
 * The actions of scheduled message [messageId] (`docs/DESIGN_SYSTEM.md` §6 "Scheduled messages"): when it goes out
 * ([sendsAt]) and how often it repeats ([repeatPeriod] seconds, 0 for never), then [actions] in their order.
 */
@Immutable
data class ScheduledActionsSheet(
    val messageId: Long,
    val sendsAt: UiText,
    val actions: ImmutableList<ScheduledAction>,
    val repeatPeriod: Int = 0,
) {
    override fun toString() = "ScheduledActionsSheet(messageId=$messageId, actions=$actions)"
}

/** What the actions sheet offers for a scheduled message, each only where it applies. */
enum class ScheduledAction {
    /** Send it now, without waiting for its time. */
    SendNow,

    /** Pick another time with the date and time pickers. */
    Reschedule,

    /** Edit its text or caption in the screen's composer, where TDLib allows it. */
    Edit,

    /** Copy its text or caption, as plain text, where the chat lets its content be saved. */
    Copy,

    /** Delete it, after a confirmation ([ScheduledDeleteDialog]). */
    Delete,
}

/** The confirmation before scheduled message [messageId] is deleted: an `AlertDialog`, as deleting cannot be undone. */
@Immutable
data class ScheduledDeleteDialog(
    val messageId: Long,
)

/**
 * The date and time pickers for scheduled message [messageId]'s new time, starting at its current one ([sendDate],
 * Unix time, seconds; null for a message sent when the other user comes online, which has none).
 */
@Immutable
data class RescheduleDialog(
    val messageId: Long,
    val sendDate: Long?,
)
