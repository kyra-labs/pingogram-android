package dev.kyralabs.pingogram.feature.settings.ui.cleanup

import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.Chat
import dev.kyralabs.pingogram.core.domain.model.ChatType
import dev.kyralabs.pingogram.core.domain.model.Supergroup
import dev.kyralabs.pingogram.core.domain.repository.GroupInfoRepository
import dev.kyralabs.pingogram.core.domain.repository.GroupRepository
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

/**
 * When the user joined a group or channel, or created it (`REQUIREMENTS.md` FR-X24): Cleanup never suggests one joined
 * within the chosen time. A supergroup's time comes with it (td_api.tl `supergroup.date`); a basic group's is the
 * user's membership, asked once per group. A private chat has none: it is not joined.
 */
class ChatJoinDates internal constructor(
    internal val supergroups: Flow<Map<Long, Supergroup>>,
    private val memberSince: suspend (chatId: Long, userId: Long) -> Instant?,
) {
    @Inject
    constructor(groups: GroupRepository, groupInfo: GroupInfoRepository) : this(
        groups.supergroups,
        { chatId, userId ->
            (groupInfo.member(chatId, userId) as? Outcome.Success)?.value?.joinedDate?.let(::instantOf)
        },
    )

    /** Basic groups' join times found so far, by chat id; one not known is asked again next time. */
    private val basicGroups = ConcurrentHashMap<Long, Instant>()

    /** When [me] joined [chat], as of [supergroups]; null where it is not known or not a group. */
    suspend fun of(
        chat: Chat,
        supergroups: Map<Long, Supergroup>,
        me: Long?,
    ): Instant? =
        when (val type = chat.type) {
            is ChatType.Supergroup -> {
                supergroups[type.supergroupId]?.joinedDate?.let(::instantOf)
            }

            is ChatType.BasicGroup -> {
                basicGroups[chat.id] ?: me?.let { memberSince(chat.id, it) }?.also { basicGroups[chat.id] = it }
            }

            else -> {
                null
            }
        }

    private companion object {
        /** [seconds] since the epoch; null for 0, which TDLib gives where it does not know. */
        fun instantOf(seconds: Long): Instant? = seconds.takeIf { it > 0 }?.let(Instant::ofEpochSecond)
    }
}
