package dev.kyralabs.pingogram.core.domain.model

import kotlinx.collections.immutable.ImmutableList

/**
 * A checklist in a message (td_api.tl `checklist`, `REQUIREMENTS.md` FR-C4): a [title] over its [tasks], which people
 * in the chat tick off and add to as far as its creator allowed. TDLib announces every change of it with
 * `updateMessageContent`, so a snapshot is current as long as the message is.
 *
 * `toString()` leaves the texts out.
 */
data class Checklist(
    val title: FormattedText,
    /** In the order they show. */
    val tasks: ImmutableList<ChecklistTask>,
    /** The creator lets others add tasks (td_api.tl `others_can_add_tasks`). */
    val othersCanAddTasks: Boolean,
    /** The user may add tasks now (td_api.tl `can_add_tasks`: the creator, or anyone where they allowed it). */
    val canAddTasks: Boolean,
    /** The creator lets others mark tasks as done (td_api.tl `others_can_mark_tasks_as_done`). */
    val othersCanMarkTasksAsDone: Boolean,
    /** The user may mark tasks as done and not done now (td_api.tl `can_mark_tasks_as_done`). */
    val canMarkTasksAsDone: Boolean,
) {
    /** How many tasks are done. */
    val doneCount: Int get() = tasks.count { it.isDone }

    override fun toString() = "Checklist(tasks=${tasks.size}, done=$doneCount, canAddTasks=$canAddTasks, …)"
}

/**
 * One task of a [Checklist]: [id] identifies it when it is marked (td_api.tl `checklistTask.id`, positive).
 *
 * @property completedBy who marked it as done; null while it is not done, and when Telegram does not say who.
 * @property completionDate when it was marked as done (Unix time, seconds); 0 while it is not done.
 */
data class ChecklistTask(
    val id: Int,
    val text: FormattedText,
    val completedBy: MessageSender?,
    val completionDate: Long,
) {
    val isDone: Boolean get() = completionDate > 0 || completedBy != null

    override fun toString() = "ChecklistTask(id=$id, isDone=$isDone, completedBy=$completedBy)"
}

/**
 * A checklist the user creates (`REQUIREMENTS.md` FR-C5): a [title] and [tasks] within the limits of td_api.tl
 * `inputChecklist` ([MAX_TITLE_LENGTH], [MIN_TASKS]–[MAX_TASKS] tasks of up to [MAX_TASK_LENGTH] characters), and who
 * else may change it. Only Telegram Premium subscribers may send one (td_api.tl `premiumFeatureChecklists`), so it is
 * offered to them alone.
 *
 * `toString()` leaves the texts out.
 */
data class NewChecklist(
    val title: String,
    val tasks: ImmutableList<String>,
    val othersCanAddTasks: Boolean,
    val othersCanMarkTasksAsDone: Boolean,
) {
    override fun toString() =
        "NewChecklist(tasks=${tasks.size}, othersCanAddTasks=$othersCanAddTasks, " +
            "othersCanMarkTasksAsDone=$othersCanMarkTasksAsDone, …)"

    companion object {
        /** The longest title, in characters (td_api.tl `inputChecklist.title`: 1–255). */
        const val MAX_TITLE_LENGTH = 255

        /** The longest task, in characters (td_api.tl `inputChecklistTask.text`: 1–100). */
        const val MAX_TASK_LENGTH = 100

        const val MIN_TASKS = 1

        /** At most this many tasks in a checklist (td_api.tl `inputChecklist.tasks`: 1–30). */
        const val MAX_TASKS = 30
    }
}

/** A task added to an existing checklist: its [id], unique in the checklist and positive, and its [text]. */
data class NewChecklistTask(
    val id: Int,
    val text: String,
) {
    override fun toString() = "NewChecklistTask(id=$id, …)"
}
