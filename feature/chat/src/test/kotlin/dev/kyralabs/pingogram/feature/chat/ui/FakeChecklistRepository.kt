package dev.kyralabs.pingogram.feature.chat.ui

import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.NewChecklistTask
import dev.kyralabs.pingogram.core.domain.repository.ChecklistRepository

/** Checklists: answers marks and added tasks as tests set, and records every request. */
class FakeChecklistRepository : ChecklistRepository {
    var onMark: suspend (done: List<Int>, notDone: List<Int>) -> Outcome<Unit> = { _, _ -> Outcome.Success(Unit) }
    var onAdd: suspend (tasks: List<NewChecklistTask>) -> Outcome<Unit> = { Outcome.Success(Unit) }

    /** The message, done tasks and not done tasks of every [markTasks]. */
    val marks = mutableListOf<Triple<Long, List<Int>, List<Int>>>()

    /** The message and tasks of every [addTasks]. */
    val added = mutableListOf<Pair<Long, List<NewChecklistTask>>>()

    override suspend fun markTasks(
        chatId: Long,
        messageId: Long,
        doneTaskIds: List<Int>,
        notDoneTaskIds: List<Int>,
    ): Outcome<Unit> {
        marks += Triple(messageId, doneTaskIds, notDoneTaskIds)
        return onMark(doneTaskIds, notDoneTaskIds)
    }

    override suspend fun addTasks(
        chatId: Long,
        messageId: Long,
        tasks: List<NewChecklistTask>,
    ): Outcome<Unit> {
        added += messageId to tasks
        return onAdd(tasks)
    }
}
