package dev.kyralabs.pingogram.feature.chats.ui

import dev.kyralabs.pingogram.core.domain.repository.GroupRepository
import dev.kyralabs.pingogram.core.domain.repository.LocalChatNamesRepository
import dev.kyralabs.pingogram.core.domain.repository.UserRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.Instant
import javax.inject.Inject

/**
 * What search's rows name besides their chats, for the pages that show them as search does (Drafts, Mentions &
 * replies): the users and groups, the current user, and the user's own names for chats (`REQUIREMENTS.md` FR-X20).
 */
class SearchRowInputs
    @Inject
    constructor(
        private val userRepository: UserRepository,
        private val groupRepository: GroupRepository,
        private val localChatNames: LocalChatNamesRepository,
    ) {
        /** The rows' sources at each of [minutes], times formatted by [timeFormatter], then every change. */
        internal fun sources(
            minutes: Flow<Instant>,
            timeFormatter: () -> ChatListTimeFormatter,
        ): Flow<SearchRowSources> =
            combine(
                combine(userRepository.users, groupRepository.supergroups, groupRepository.basicGroups, ::Triple),
                userRepository.myUserId,
                minutes,
                localChatNames.names,
            ) { (users, supergroups, basicGroups), myUserId, now, localNames ->
                SearchRowSources(users, supergroups, basicGroups, myUserId, now, timeFormatter(), localNames)
            }
    }
