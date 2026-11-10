package dev.kyralabs.pingogram.feature.chat.ui.checklist

import android.content.res.Resources
import dev.kyralabs.pingogram.core.domain.model.MessageContent
import dev.kyralabs.pingogram.core.domain.model.NewChecklist
import dev.kyralabs.pingogram.core.ui.text.resolve
import dev.kyralabs.pingogram.feature.chat.R
import dev.kyralabs.pingogram.feature.chat.ui.ChatEvent
import dev.kyralabs.pingogram.feature.chat.ui.ChatItem
import dev.kyralabs.pingogram.feature.chat.ui.ChecklistEvent

/**
 * What screen readers hear of [bubble]'s checklist within its bubble's description (`docs/DESIGN_SYSTEM.md` §9):
 * "Checklist", the title, "2 of 5 done", then each task as one entry ("Book the room, done by Ada"; "saving" while its
 * change is on its way). Empty for other content.
 */
internal fun checklistDescription(
    resources: Resources,
    bubble: ChatItem.Bubble,
): String {
    val list = (bubble.content as? MessageContent.Checklist)?.list ?: return ""
    val ui = bubble.checklist ?: ChecklistUi()
    val tasks =
        list.tasks.map { task ->
            val state =
                when {
                    task.id in ui.pending -> resources.getString(R.string.checklist_task_sending)
                    !task.isDone -> resources.getString(R.string.checklist_task_not_done)
                    else -> {
                        ui.completers[task.id]?.let { completer ->
                            resources.getString(R.string.checklist_task_done_by, completer.name.resolve(resources))
                        } ?: resources.getString(R.string.checklist_task_done)
                    }
                }
            "${task.text.text}, $state"
        }
    return (
        listOf(
            resources.getString(R.string.checklist_kicker),
            list.title.text,
            resources.getString(R.string.checklist_progress, list.doneCount, list.tasks.size),
        ) + tasks
    ).joinToString(", ")
}

/**
 * The custom actions of [bubble]'s checklist, labels and events: "Mark … as done" or "… as not done" for each task
 * where the user may mark tasks (none for a task whose change is on its way), and "Add task" where they may add one.
 * None for other content.
 */
internal fun checklistActions(
    resources: Resources,
    bubble: ChatItem.Bubble,
): List<Pair<String, ChatEvent>> {
    val list = (bubble.content as? MessageContent.Checklist)?.list ?: return emptyList()
    val ui = bubble.checklist ?: ChecklistUi()
    val marks =
        if (list.canMarkTasksAsDone) {
            list.tasks.filter { it.id !in ui.pending }.map { task ->
                val label = if (task.isDone) R.string.checklist_mark_not_done else R.string.checklist_mark_done
                resources.getString(label, task.text.text) to
                    ChecklistEvent.MarkTask(bubble.id, task.id, done = !task.isDone)
            }
        } else {
            emptyList()
        }
    val add =
        if (list.canAddTasks && list.tasks.size < NewChecklist.MAX_TASKS && !ui.isAddingTask) {
            listOf(resources.getString(R.string.checklist_add_task) to ChecklistEvent.OpenAddTask(bubble.id))
        } else {
            emptyList()
        }
    return marks + add
}
