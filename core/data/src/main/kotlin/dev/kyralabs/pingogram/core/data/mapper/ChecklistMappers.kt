package dev.kyralabs.pingogram.core.data.mapper

import dev.kyralabs.pingogram.core.domain.model.Checklist
import dev.kyralabs.pingogram.core.domain.model.ChecklistTask
import dev.kyralabs.pingogram.core.domain.model.NewChecklist
import dev.kyralabs.pingogram.core.domain.model.NewChecklistTask
import kotlinx.collections.immutable.toImmutableList
import org.drinkless.tdlib.TdApi

/** The checklist with its tasks in TDLib's order; a task's completer of a kind a newer TDLib adds is left out. */
internal fun TdApi.Checklist.toChecklist(): Checklist =
    Checklist(
        title = title.toFormattedText(),
        tasks = tasks.map { it.toChecklistTask() }.toImmutableList(),
        othersCanAddTasks = othersCanAddTasks,
        canAddTasks = canAddTasks,
        othersCanMarkTasksAsDone = othersCanMarkTasksAsDone,
        canMarkTasksAsDone = canMarkTasksAsDone,
    )

private fun TdApi.ChecklistTask.toChecklistTask(): ChecklistTask =
    ChecklistTask(
        id = id,
        text = text.toFormattedText(),
        completedBy = completedBy?.toMessageSender(),
        completionDate = completionDate.toLong(),
    )

/**
 * `inputMessageChecklist` of this checklist: plain title and tasks (only Telegram Premium users may add custom emoji,
 * and the form takes plain text), the tasks numbered from 1 in their order, as td_api.tl asks for positive ids unique
 * in the checklist.
 */
internal fun NewChecklist.toInputMessageChecklist(): TdApi.InputMessageChecklist =
    TdApi.InputMessageChecklist(
        TdApi.InputChecklist(
            plainText(title),
            tasks.mapIndexed { index, text -> TdApi.InputChecklistTask(index + 1, plainText(text)) }.toTypedArray(),
            othersCanAddTasks,
            othersCanMarkTasksAsDone,
        ),
    )

/** `inputChecklistTask`s of [tasks], with the ids their caller chose. */
internal fun List<NewChecklistTask>.toInputChecklistTasks(): Array<TdApi.InputChecklistTask> =
    map { TdApi.InputChecklistTask(it.id, plainText(it.text)) }.toTypedArray()

private fun plainText(text: String) = TdApi.FormattedText(text, emptyArray())
