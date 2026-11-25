package dev.kyralabs.pingogram.core.testing

import dev.kyralabs.pingogram.core.domain.repository.LocalChatNamesRepository
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.coroutines.flow.MutableStateFlow

/** [LocalChatNamesRepository] in memory: the [names] by chat id, none at first. */
class FakeLocalChatNamesRepository(
    names: ImmutableMap<Long, String> = persistentMapOf(),
) : LocalChatNamesRepository {
    override val names = MutableStateFlow(names)
}
