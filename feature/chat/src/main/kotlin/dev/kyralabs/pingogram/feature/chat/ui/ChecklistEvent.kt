package dev.kyralabs.pingogram.feature.chat.ui

/**
 * What the user does with the checklists of the conversation's messages (`REQUIREMENTS.md` FR-C4), for
 * [ChatViewModel]. The form of a new checklist has the attach menu's events ([ChatEvent.OpenChecklist],
 * [ChatEvent.SendChecklist], [ChatEvent.DismissChecklist]).
 */
sealed interface ChecklistEvent : ChatEvent {
    /** Task [taskId] of the checklist of message [messageId] was ticked ([done]) or unticked: the change goes out. */
    data class MarkTask(
        val messageId: Long,
        val taskId: Int,
        val done: Boolean,
    ) : ChecklistEvent

    /** "Add task" under the checklist of message [messageId]: its inline field opens. */
    data class OpenAddTask(
        val messageId: Long,
    ) : ChecklistEvent

    /** The inline field of message [messageId] was closed without a task. */
    data class DismissAddTask(
        val messageId: Long,
    ) : ChecklistEvent

    /** The inline field's [text] goes out as a new task of the checklist of message [messageId]. */
    data class AddTask(
        val messageId: Long,
        val text: String,
    ) : ChecklistEvent {
        override fun toString() = "AddTask(messageId=$messageId, …)"
    }
}
