package dev.kyralabs.pingogram.core.data.repository

import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.common.result.map
import dev.kyralabs.pingogram.core.data.di.AccountScoped
import dev.kyralabs.pingogram.core.data.mapper.toInputChecklistTasks
import dev.kyralabs.pingogram.core.domain.model.NewChecklistTask
import dev.kyralabs.pingogram.core.domain.repository.ChecklistRepository
import dev.kyralabs.pingogram.core.tdlib.TdlibClient
import org.drinkless.tdlib.TdApi
import javax.inject.Inject

/**
 * [ChecklistRepository] on `markChecklistTasksAsDone` and `addChecklistTasks`, without a cache: TDLib announces the
 * changed checklist with `updateMessageContent`, which [TdlibMessageRepository]'s windows apply to its message.
 */
@AccountScoped
class TdlibChecklistRepository
    @Inject
    constructor(
        private val client: TdlibClient,
    ) : ChecklistRepository {
        override suspend fun markTasks(
            chatId: Long,
            messageId: Long,
            doneTaskIds: List<Int>,
            notDoneTaskIds: List<Int>,
        ): Outcome<Unit> =
            client
                .request(
                    TdApi.MarkChecklistTasksAsDone(
                        chatId,
                        messageId,
                        doneTaskIds.toIntArray(),
                        notDoneTaskIds.toIntArray(),
                    ),
                ).map {}

        override suspend fun addTasks(
            chatId: Long,
            messageId: Long,
            tasks: List<NewChecklistTask>,
        ): Outcome<Unit> =
            client.request(TdApi.AddChecklistTasks(chatId, messageId, tasks.toInputChecklistTasks())).map {}
    }
