package dev.kyralabs.pingogram.feature.chat.ui

import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.Chat
import dev.kyralabs.pingogram.core.domain.model.GroupMember
import dev.kyralabs.pingogram.core.domain.model.MemberFilter
import dev.kyralabs.pingogram.core.domain.model.MemberRole
import dev.kyralabs.pingogram.core.domain.repository.GroupInfoRepository
import dev.kyralabs.pingogram.core.ui.text.UiText
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onStart
import dev.kyralabs.pingogram.feature.chat.R as ChatR

/**
 * The role labels of a group's people (`docs/DESIGN_SYSTEM.md` §13.6, the labels after a sender's
 * name), for [ChatViewModel]: the owner and the administrators of [chat] while it is a group, asked for once per
 * conversation ([GroupInfoRepository.members], [MemberFilter.Administrators]); "Owner", "Admin" or the member's own
 * tag, by user or chat id. Nothing is asked per message, and none for other chats; a failure shows no labels.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal fun senderRoles(
    chat: Flow<Chat>,
    groups: GroupInfoRepository,
): Flow<Map<Long, UiText>> =
    chat
        .distinctUntilChangedBy { it.id to it.isGroup }
        .flatMapLatest { chat ->
            if (!chat.isGroup) {
                flowOf(emptyMap())
            } else {
                flow {
                    when (val page = groups.members(chat.id, MemberFilter.Administrators)) {
                        is Outcome.Success -> {
                            emit(
                                page.value.members
                                    .mapNotNull(::roleOf)
                                    .toMap(),
                            )
                        }

                        is Outcome.Failure -> {
                            emit(emptyMap())
                        }
                    }
                }
            }
        }.onStart { emit(emptyMap()) }

/** A member's id and label: their tag, else "Owner" or "Admin"; null for anyone else. */
private fun roleOf(member: GroupMember): Pair<Long, UiText>? {
    val tag = member.tag.takeIf(String::isNotBlank)?.let(UiText::Plain)
    val label =
        when (member.role) {
            is MemberRole.Owner -> tag ?: UiText.resource(ChatR.string.search_sender_owner)
            is MemberRole.Administrator -> tag ?: UiText.resource(ChatR.string.search_sender_admin)
            else -> return null
        }
    return member.sender.id to label
}
