package dev.kyralabs.pingogram.core.data.repository

import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.common.result.map
import dev.kyralabs.pingogram.core.data.di.AccountScoped
import dev.kyralabs.pingogram.core.domain.model.CallHistoryChange
import dev.kyralabs.pingogram.core.domain.model.CallHistoryEntry
import dev.kyralabs.pingogram.core.domain.model.CallHistoryPage
import dev.kyralabs.pingogram.core.domain.model.CallOutcome
import dev.kyralabs.pingogram.core.domain.repository.CallHistoryRepository
import dev.kyralabs.pingogram.core.tdlib.TdlibClient
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.mapNotNull
import org.drinkless.tdlib.TdApi
import javax.inject.Inject

/**
 * [CallHistoryRepository] on TDLib: `searchCallMessages` (every private chat's call messages, newest first, paged by
 * TDLib's own offset), `deleteMessages` without `revoke`, and the updates that change the history while it shows:
 * `updateNewMessage` with a `messageCall` or a `messageGroupCall` and `updateDeleteMessages` (permanent ones only:
 * TDLib also reports messages it merely drops from its memory, `from_cache`). Nothing is cached, so it is no
 * `TdlibUpdateCache`.
 *
 * Conference calls' invitations (`messageGroupCall`, `docs/adr/0025-conference-calls.md`) are entries as calls are;
 * TDLib's own updates of their content (the call ending) are not followed: the page is read again when it shows.
 * Nothing about a call is logged.
 */
@AccountScoped
class TdlibCallHistoryRepository
    @Inject
    constructor(
        private val client: TdlibClient,
    ) : CallHistoryRepository {
        override suspend fun page(
            offset: String,
            limit: Int,
            onlyMissed: Boolean,
        ): Outcome<CallHistoryPage> =
            client.request(TdApi.SearchCallMessages(offset, limit, onlyMissed)).map { found ->
                CallHistoryPage(
                    entries = found.messages.mapNotNull { it?.toCallHistoryEntry() }.toImmutableList(),
                    nextOffset = found.nextOffset.orEmpty(),
                )
            }

        override val changes: Flow<CallHistoryChange> =
            client.updates.mapNotNull { update ->
                when (update) {
                    is TdApi.UpdateNewMessage -> update.message?.toCallHistoryEntry()?.let(CallHistoryChange::Added)
                    is TdApi.UpdateDeleteMessages -> update.toRemoved()
                    else -> null
                }
            }

        override suspend fun delete(entry: CallHistoryEntry): Outcome<Unit> =
            client
                .request(TdApi.DeleteMessages(entry.chatId, longArrayOf(entry.messageId), false))
                .map { }
    }

private fun TdApi.UpdateDeleteMessages.toRemoved(): CallHistoryChange.Removed? =
    takeIf { isPermanent && !fromCache && messageIds?.isNotEmpty() == true }
        ?.let { CallHistoryChange.Removed(chatId, messageIds.toList().toImmutableList()) }

/** The entry of a call's service message, or null for any other message. */
internal fun TdApi.Message.toCallHistoryEntry(): CallHistoryEntry? =
    when (val content = content) {
        is TdApi.MessageCall -> toCallEntry(content)
        is TdApi.MessageGroupCall -> toGroupCallEntry(content)
        else -> null
    }

private fun TdApi.Message.toCallEntry(call: TdApi.MessageCall): CallHistoryEntry =
    CallHistoryEntry(
        chatId = chatId,
        messageId = id,
        userId = chatId,
        date = date.toLong(),
        isOutgoing = isOutgoing,
        isVideo = call.isVideo,
        outcome = call.discardReason.toCallOutcome(isOutgoing, call.duration),
        duration = call.duration.coerceAtLeast(0),
    )

/** A conference call's invitation: answered once it went on with someone or still does, else missed or not answered. */
private fun TdApi.Message.toGroupCallEntry(call: TdApi.MessageGroupCall) =
    CallHistoryEntry(
        chatId = chatId,
        messageId = id,
        userId = chatId,
        date = date.toLong(),
        isOutgoing = isOutgoing,
        isVideo = call.isVideo,
        outcome =
            when {
                call.isActive || (call.duration > 0 && !call.wasMissed) -> CallOutcome.Answered
                isOutgoing -> CallOutcome.Cancelled
                else -> CallOutcome.Missed
            },
        duration = call.duration.coerceAtLeast(0),
        isGroupCall = true,
        isActive = call.isActive,
    )

/**
 * How the call went for the user, as `core:ui`'s call previews word it: a missed outgoing call is one the user
 * cancelled before an answer; a call with a length was answered, whatever ended it.
 */
private fun TdApi.CallDiscardReason?.toCallOutcome(
    isOutgoing: Boolean,
    duration: Int,
): CallOutcome =
    when {
        this is TdApi.CallDiscardReasonMissed && isOutgoing -> CallOutcome.Cancelled
        this is TdApi.CallDiscardReasonMissed -> CallOutcome.Missed
        this is TdApi.CallDiscardReasonDeclined -> CallOutcome.Declined
        duration <= 0 && isOutgoing -> CallOutcome.Cancelled
        duration <= 0 -> CallOutcome.Missed
        else -> CallOutcome.Answered
    }
