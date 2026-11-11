package dev.kyralabs.pingogram.core.domain.repository

import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.NewChecklistTask

/**
 * Changing the checklists of messages (`REQUIREMENTS.md` FR-C4): marking tasks as done or not done, and adding tasks.
 * Each call is one tap of the user's, never made on its own (Telegram's API terms). Nothing is kept here: TDLib
 * announces every change of a checklist with `updateMessageContent`, which `MessageRepository.history` applies to the
 * message that shows it. Whether the user may change a checklist is the checklist's own say (`Checklist.canAddTasks`,
 * `Checklist.canMarkTasksAsDone`), which callers check first.
 *
 * Every call fails with a `DomainError`: `FloodWait`, `Network` for a request that gets no answer in time, `NotFound`
 * for a message TDLib does not know, or another one when TDLib refuses it.
 */
interface ChecklistRepository {
    /**
     * Marks the tasks [doneTaskIds] of the checklist of message [messageId] of chat [chatId] as done, and
     * [notDoneTaskIds] as not done (`ChecklistTask.id`). Succeeds once Telegram took the change.
     */
    suspend fun markTasks(
        chatId: Long,
        messageId: Long,
        doneTaskIds: List<Int>,
        notDoneTaskIds: List<Int>,
    ): Outcome<Unit>

    /** Adds [tasks] to the end of the checklist of message [messageId] of chat [chatId]. */
    suspend fun addTasks(
        chatId: Long,
        messageId: Long,
        tasks: List<NewChecklistTask>,
    ): Outcome<Unit>
}
