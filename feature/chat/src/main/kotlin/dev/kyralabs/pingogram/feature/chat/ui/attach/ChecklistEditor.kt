package dev.kyralabs.pingogram.feature.chat.ui.attach

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.kyralabs.pingogram.core.domain.model.NewChecklist
import kotlinx.collections.immutable.toImmutableList
import java.util.Locale

/**
 * What the form of a new checklist holds (`REQUIREMENTS.md` FR-C5): the title, 1–30 tasks, and who else may change
 * it. Like the composer's text, its owner, the conversation's view model, keeps it across rotations, and the text
 * fields edit its [TextFieldState]s directly; everything is snapshot state, so the form and a `snapshotFlow` that saves
 * it ([draft]) follow every change. Main thread only.
 *
 * Others may mark tasks as done by default and may not add tasks, as in Telegram's apps.
 */
@Stable
class ChecklistEditor(
    initial: ChecklistDraft = ChecklistDraft(),
) {
    val title = TextFieldState(initial.title)

    private var nextKey = 0L

    private val fields = mutableStateListOf<ChecklistTaskField>()

    /** The tasks, in their order; at least [NewChecklist.MIN_TASKS] fields, at most [NewChecklist.MAX_TASKS]. */
    val tasks: List<ChecklistTaskField> get() = fields

    var othersCanAddTasks by mutableStateOf(initial.othersCanAddTasks)

    var othersCanMarkTasksAsDone by mutableStateOf(initial.othersCanMarkTasksAsDone)

    init {
        fill(initial)
    }

    val canAddTask: Boolean get() = fields.size < NewChecklist.MAX_TASKS

    val canRemoveTask: Boolean get() = fields.size > NewChecklist.MIN_TASKS

    /** Adds an empty task at the end, if there is room, and returns its key; null otherwise. */
    fun addTask(): Long? {
        if (!canAddTask) return null
        return newField("").also { fields += it }.key
    }

    /** Removes task [key], while more than the minimum are left. */
    fun removeTask(key: Long) {
        if (!canRemoveTask) return
        fields.removeAll { it.key == key }
    }

    /** Whether task [key] repeats a task above it, which Telegram refuses. */
    fun isDuplicate(key: Long): Boolean {
        val index = fields.indexOfFirst { it.key == key }
        val text = fields.getOrNull(index)?.normalized ?: return false
        return text.isNotEmpty() && fields.take(index).any { it.normalized == text }
    }

    /**
     * The checklist to send; null while it is not complete: a title and at least one filled-in task, none repeated.
     * Empty tasks are left out.
     */
    fun checklist(): NewChecklist? {
        val title =
            title.text
                .toString()
                .trim()
                .take(NewChecklist.MAX_TITLE_LENGTH)
        val texts =
            fields
                .filter { it.normalized.isNotEmpty() }
                .map {
                    it.text.text
                        .toString()
                        .trim()
                        .take(NewChecklist.MAX_TASK_LENGTH)
                }
        val complete =
            title.isNotEmpty() && texts.size >= NewChecklist.MIN_TASKS && fields.none { isDuplicate(it.key) }
        if (!complete) return null
        return NewChecklist(
            title = title,
            tasks = texts.take(NewChecklist.MAX_TASKS).toImmutableList(),
            othersCanAddTasks = othersCanAddTasks,
            othersCanMarkTasksAsDone = othersCanMarkTasksAsDone,
        )
    }

    /** What the form holds now, for process death. */
    val draft: ChecklistDraft
        get() =
            ChecklistDraft(
                title = title.text.toString(),
                tasks = fields.map { it.text.text.toString() },
                othersCanAddTasks = othersCanAddTasks,
                othersCanMarkTasksAsDone = othersCanMarkTasksAsDone,
            )

    /** Empties the form for the next checklist. */
    fun clear() {
        title.clearText()
        fill(ChecklistDraft())
    }

    private fun fill(draft: ChecklistDraft) {
        fields.clear()
        fields += draft.tasks.take(NewChecklist.MAX_TASKS).map(::newField)
        while (fields.size < NewChecklist.MIN_TASKS) fields += newField("")
        othersCanAddTasks = draft.othersCanAddTasks
        othersCanMarkTasksAsDone = draft.othersCanMarkTasksAsDone
    }

    private fun newField(text: String) = ChecklistTaskField(nextKey++, TextFieldState(text))
}

/** One task of the [ChecklistEditor]: its text field, by a [key] that stays while the form lives. */
@Stable
class ChecklistTaskField(
    val key: Long,
    val text: TextFieldState,
) {
    /** The text as tasks are compared: trimmed, case ignored. */
    internal val normalized: String get() =
        text.text
            .toString()
            .trim()
            .lowercase(Locale.ROOT)
}

/** What a [ChecklistEditor] holds, as plain values: for process death, and for previews and tests. */
data class ChecklistDraft(
    val title: String = "",
    val tasks: List<String> = emptyList(),
    val othersCanAddTasks: Boolean = false,
    val othersCanMarkTasksAsDone: Boolean = true,
) {
    override fun toString() = "ChecklistDraft(tasks=${tasks.size}, …)"
}
