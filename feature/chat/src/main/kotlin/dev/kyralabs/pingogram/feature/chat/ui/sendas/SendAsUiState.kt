package dev.kyralabs.pingogram.feature.chat.ui.sendas

import androidx.compose.runtime.Immutable
import dev.kyralabs.pingogram.core.domain.model.MessageSender
import dev.kyralabs.pingogram.core.domain.model.ProfilePhoto
import dev.kyralabs.pingogram.core.ui.text.UiText
import kotlinx.collections.immutable.ImmutableList

/**
 * Who new messages go out as, for the composer's send-as button (`REQUIREMENTS.md` FR-C5): its avatar, by [id] (the
 * user's or chat's TDLib id, which picks the placeholder's colors), [name] and [photo].
 */
@Immutable
data class SendAsChoice(
    val id: Long,
    val name: UiText,
    val photo: ProfilePhoto?,
) {
    override fun toString() = "SendAsChoice(id=$id, …)"
}

/**
 * The send-as picker (`REQUIREMENTS.md` FR-C5): everyone the user can send messages as in the chat ([options], null
 * while Telegram is asked), the chosen one marked. [isChanging] while a new choice is on its way to TDLib, which
 * shows it chosen meanwhile.
 */
@Immutable
data class SendAsSheet(
    val options: ImmutableList<SendAsOption>? = null,
    val isChanging: Boolean = false,
)

/**
 * One sender in the picker: [sender] with its avatar ([id], [name], [photo]) and what it is ([kind]); [selected] the
 * one new messages go out as; [needsPremium] if choosing it needs Telegram Premium, which the user does not have: the
 * row is disabled and says so, with no offer.
 */
@Immutable
data class SendAsOption(
    val sender: MessageSender,
    val id: Long,
    val name: UiText,
    val photo: ProfilePhoto?,
    val kind: SenderKind,
    val selected: Boolean,
    val needsPremium: Boolean,
) {
    override fun toString() = "SendAsOption(id=$id, kind=$kind, selected=$selected, needsPremium=$needsPremium)"
}

/** What a sender in the picker is, which its second line says. */
enum class SenderKind {
    /** The user's own account. */
    Account,

    /** A channel the user owns. */
    Channel,

    /** The group the conversation is, for an anonymous admin. */
    Anonymous,

    /** Another group. */
    Group,
}
