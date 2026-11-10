package dev.kyralabs.pingogram.feature.chat.ui.checklist

import androidx.compose.runtime.Immutable
import dev.kyralabs.pingogram.core.domain.model.MessageContent
import dev.kyralabs.pingogram.core.domain.model.MessageSender
import dev.kyralabs.pingogram.core.ui.R
import dev.kyralabs.pingogram.core.ui.text.fullName
import dev.kyralabs.pingogram.feature.chat.ui.ItemInputs
import dev.kyralabs.pingogram.feature.chat.ui.SenderAvatar
import dev.kyralabs.pingogram.feature.chat.ui.memberIdsOf
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableMap

/**
 * How a bubble's checklist shows beyond its message (`ChatItem.Bubble.checklist`, `docs/DESIGN_SYSTEM.md` §6
 * "Checklists").
 *
 * @property completers who ticked each done task, by task id, for the avatar at its end; a task whose completer the
 *   device does not know (or a chat) is missing.
 * @property pending the tasks whose change is on its way to Telegram ([ChecklistEvent.MarkTask]), which show their
 *   progress and take no tap meanwhile.
 * @property isAddingTask the inline field for a new task is open ([ChecklistEvent.OpenAddTask]).
 * @property isSendingTask a new task is on its way to Telegram: the field waits.
 */
@Immutable
data class ChecklistUi(
    val completers: ImmutableMap<Int, SenderAvatar> = persistentMapOf(),
    val pending: ImmutableSet<Int> = persistentSetOf(),
    val isAddingTask: Boolean = false,
    val isSendingTask: Boolean = false,
) {
    override fun toString() =
        "ChecklistUi(completers=${completers.size}, pending=$pending, isAddingTask=$isAddingTask, " +
            "isSendingTask=$isSendingTask)"
}

/**
 * The form of a new checklist, while it is open (`REQUIREMENTS.md` FR-C5); what it holds is the view model's
 * `ChecklistEditor`. [canSchedule] whether the send menu offers scheduling.
 */
@Immutable
data class ChecklistSheet(
    val canSchedule: Boolean = true,
)

/**
 * A checklist's [ChecklistUi] for its bubble: the completers' avatars by task id, from the users looked up for the item
 * ([ItemInputs.members]); null for other content.
 */
internal fun ItemInputs.checklistUi(content: MessageContent): ChecklistUi? {
    if (content !is MessageContent.Checklist) return null
    val users = memberIdsOf(content).zip(members).toMap()
    val completers =
        content.list.tasks
            .mapNotNull { task ->
                val userId = (task.completedBy as? MessageSender.User)?.userId ?: return@mapNotNull null
                val user = users[userId] ?: return@mapNotNull null
                task.id to
                    SenderAvatar(
                        id = user.id,
                        name = user.fullName(),
                        photo = user.profilePhoto,
                        placeholderIcon = if (user.isDeleted) R.drawable.ic_person_filled else null,
                    )
            }.toMap()
    return ChecklistUi(completers = completers.toImmutableMap())
}
