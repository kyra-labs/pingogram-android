package dev.kyralabs.pingogram.feature.chat.ui.attach

import androidx.lifecycle.SavedStateHandle

// The checklist form in a SavedStateHandle for process death, as AttachSaving.kt keeps the attach preview and the
// poll form: its title, its tasks and its two switches.

/** Keeps the checklist form: whether it is open, and what it holds. */
internal fun SavedStateHandle.saveChecklist(
    open: Boolean,
    draft: ChecklistDraft,
) {
    this[KEY_CHECKLIST_OPEN] = open
    this[KEY_CHECKLIST_TITLE] = draft.title
    this[KEY_CHECKLIST_TASKS] = ArrayList(draft.tasks)
    this[KEY_CHECKLIST_FLAGS] = booleanArrayOf(draft.othersCanAddTasks, draft.othersCanMarkTasksAsDone)
}

/** Whether the checklist form was open. */
internal fun SavedStateHandle.restoredChecklistOpen(): Boolean = get<Boolean>(KEY_CHECKLIST_OPEN) == true

/** What the checklist form held, or an empty form. */
internal fun SavedStateHandle.restoredChecklist(): ChecklistDraft {
    val flags = get<BooleanArray>(KEY_CHECKLIST_FLAGS)?.takeIf { it.size == CHECKLIST_FLAGS } ?: return ChecklistDraft()
    return ChecklistDraft(
        title = get<String>(KEY_CHECKLIST_TITLE).orEmpty(),
        tasks = get<ArrayList<String>>(KEY_CHECKLIST_TASKS).orEmpty(),
        othersCanAddTasks = flags[0],
        othersCanMarkTasksAsDone = flags[1],
    )
}

private const val KEY_CHECKLIST_OPEN = "chat.checklistOpen"
private const val KEY_CHECKLIST_TITLE = "chat.checklistTitle"
private const val KEY_CHECKLIST_TASKS = "chat.checklistTasks"
private const val KEY_CHECKLIST_FLAGS = "chat.checklistFlags"
private const val CHECKLIST_FLAGS = 2
