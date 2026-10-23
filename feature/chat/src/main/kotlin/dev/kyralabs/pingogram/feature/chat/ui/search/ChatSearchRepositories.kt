package dev.kyralabs.pingogram.feature.chat.ui.search

import dev.kyralabs.pingogram.core.domain.repository.ChatSearchRepository
import dev.kyralabs.pingogram.core.domain.repository.GroupInfoRepository
import javax.inject.Inject

/**
 * The repositories of the search in the conversation (`REQUIREMENTS.md` §5.9): the search itself, and the members of a
 * group whose messages it can be limited to. One parameter of `ChatViewModel` for them, which already takes one
 * repository per kind of data it combines.
 */
class ChatSearchRepositories
    @Inject
    constructor(
        val search: ChatSearchRepository,
        val members: GroupInfoRepository,
    )
