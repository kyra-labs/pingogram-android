package dev.kyralabs.pingogram.feature.chat.ui.checklist

import androidx.lifecycle.SavedStateHandle
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.Checklist
import dev.kyralabs.pingogram.core.domain.model.Message
import dev.kyralabs.pingogram.core.domain.model.MessageContent
import dev.kyralabs.pingogram.core.domain.model.NewChecklist
import dev.kyralabs.pingogram.core.domain.model.NewChecklistTask
import dev.kyralabs.pingogram.core.domain.repository.ChecklistRepository
import dev.kyralabs.pingogram.feature.chat.ui.ChatContent
import dev.kyralabs.pingogram.feature.chat.ui.ChatEffect
import dev.kyralabs.pingogram.feature.chat.ui.ChatItem
import dev.kyralabs.pingogram.feature.chat.ui.ChatUiState
import dev.kyralabs.pingogram.feature.chat.ui.ChecklistEvent
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toPersistentSet
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The checklists of chat [chatId]'s messages (`REQUIREMENTS.md` FR-C4), for `ChatViewModel`: ticking tasks off and
 * adding tasks. Every request is one tap of the user's (Telegram's API terms). Each looks the checklist up in its
 * message as it is now ([messageOf]), which `updateMessageContent` keeps current, so a task ticked elsewhere counts at
 * once; a change the checklist no longer allows says so ([ChatEffect.ActionUnavailable]).
 *
 * - **Marking** ([ChecklistEvent.MarkTask]): where the checklist lets the user ([Checklist.canMarkTasksAsDone]), the
 *   task is [pending] until Telegram answers, which TDLib follows with the changed checklist; a second tap meanwhile
 *   does nothing.
 * - **Adding** ([ChecklistEvent.OpenAddTask], [ChecklistEvent.AddTask]): the inline field is open per message
 *   ([adding], kept across process death); its text goes out as one task with the next free id, the field waiting
 *   ([sending]) and closing once Telegram took it, staying open with a snackbar when it refused.
 * - Failures are [ChatEffect.ShowError] snackbars.
 *
 * Runs in [scope], the view model's, on the main thread.
 */
internal class ChatChecklists(
    private val chatId: Long,
    private val repository: ChecklistRepository,
    private val messageOf: (messageId: Long) -> Message?,
    private val savedState: SavedStateHandle,
    private val scope: CoroutineScope,
    private val onEffect: (ChatEffect) -> Unit,
) {
    private val pending = MutableStateFlow<PersistentMap<Long, PersistentSet<Int>>>(persistentMapOf())

    private val adding =
        MutableStateFlow(
            savedState.get<LongArray>(KEY_ADDING)?.toList()?.toPersistentSet() ?: persistentSetOf<Long>(),
        )

    private val sending = MutableStateFlow<PersistentSet<Long>>(persistentSetOf())

    /** What the bubbles show beyond their messages, for [withChecklists]. */
    val changes: StateFlow<ChecklistChanges> =
        combine(pending, adding, sending, ::ChecklistChanges)
            .stateIn(scope, SharingStarted.Eagerly, ChecklistChanges(pending.value, adding.value, sending.value))

    fun onEvent(event: ChecklistEvent) {
        when (event) {
            is ChecklistEvent.MarkTask -> mark(event.messageId, event.taskId, event.done)
            is ChecklistEvent.OpenAddTask -> setAdding(event.messageId, open = true)
            is ChecklistEvent.DismissAddTask -> setAdding(event.messageId, open = false)
            is ChecklistEvent.AddTask -> add(event.messageId, event.text)
        }
    }

    private fun mark(
        messageId: Long,
        taskId: Int,
        done: Boolean,
    ) {
        val list = checklistOf(messageId)
        if (list == null || !list.canMarkTasksAsDone || list.tasks.none { it.id == taskId }) {
            onEffect(ChatEffect.ActionUnavailable)
            return
        }
        if (taskId in pending.value[messageId].orEmpty()) return
        setPending(messageId, taskId, waiting = true)
        scope.launch {
            try {
                val doneIds = if (done) listOf(taskId) else emptyList()
                val notDoneIds = if (done) emptyList() else listOf(taskId)
                val result = repository.markTasks(chatId, messageId, doneIds, notDoneIds)
                if (result is Outcome.Failure) onEffect(ChatEffect.ShowError(result.error))
            } finally {
                setPending(messageId, taskId, waiting = false)
            }
        }
    }

    private fun add(
        messageId: Long,
        text: String,
    ) {
        val list = checklistOf(messageId)
        val task = text.trim().take(NewChecklist.MAX_TASK_LENGTH)
        if (list == null || !list.canAddTasks || list.tasks.size >= NewChecklist.MAX_TASKS || task.isEmpty()) {
            onEffect(ChatEffect.ActionUnavailable)
            return
        }
        if (messageId in sending.value) return
        sending.update { it.add(messageId) }
        scope.launch {
            try {
                val id = (list.tasks.maxOfOrNull { it.id } ?: 0) + 1
                when (val result = repository.addTasks(chatId, messageId, listOf(NewChecklistTask(id, task)))) {
                    is Outcome.Failure -> onEffect(ChatEffect.ShowError(result.error))
                    is Outcome.Success -> setAdding(messageId, open = false)
                }
            } finally {
                sending.update { it.remove(messageId) }
            }
        }
    }

    private fun setAdding(
        messageId: Long,
        open: Boolean,
    ) {
        if (open && checklistOf(messageId)?.canAddTasks != true) {
            onEffect(ChatEffect.ActionUnavailable)
            return
        }
        val changed = if (open) adding.value.add(messageId) else adding.value.remove(messageId)
        adding.value = changed
        savedState[KEY_ADDING] = changed.toLongArray()
    }

    private fun setPending(
        messageId: Long,
        taskId: Int,
        waiting: Boolean,
    ) {
        pending.update { all ->
            val tasks = all[messageId].orEmpty()
            val changed = if (waiting) tasks + taskId else tasks - taskId
            if (changed.isEmpty()) all.remove(messageId) else all.put(messageId, changed.toPersistentSet())
        }
    }

    private fun checklistOf(messageId: Long): Checklist? =
        (messageOf(messageId)?.content as? MessageContent.Checklist)?.list

    private companion object {
        const val KEY_ADDING = "checklist_adding"
    }
}

/** The tasks whose change is on its way, by message; the messages whose inline field is open; those sending one. */
internal data class ChecklistChanges(
    val pending: PersistentMap<Long, PersistentSet<Int>>,
    val adding: PersistentSet<Long>,
    val sending: PersistentSet<Long>,
) {
    val isEmpty: Boolean get() = pending.isEmpty() && adding.isEmpty() && sending.isEmpty()
}

/**
 * The state with [changes] copied into the bubbles of the checklists they concern only, so no other recomposes; the
 * state as it is while there are none.
 */
internal fun ChatUiState.withChecklists(changes: ChecklistChanges): ChatUiState {
    val content = content as? ChatContent.Content
    if (changes.isEmpty || content == null) return this
    var changed = false
    val items =
        content.items.map { item ->
            val bubble = item as? ChatItem.Bubble
            val ui = bubble?.checklist
            if (bubble == null || ui == null) {
                item
            } else {
                val pending = changes.pending[bubble.id]
                val isAdding = bubble.id in changes.adding
                val isSending = bubble.id in changes.sending
                if (pending == null && !isAdding && !isSending) {
                    item
                } else {
                    changed = true
                    bubble.copy(
                        checklist =
                            ui.copy(
                                pending = pending ?: persistentSetOf(),
                                isAddingTask = isAdding,
                                isSendingTask = isSending,
                            ),
                    )
                }
            }
        }
    return if (changed) copy(content = content.copy(items = items.toImmutableList())) else this
}
