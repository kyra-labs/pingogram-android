package dev.kyralabs.pingogram.core.testing

import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.repository.ChatListSettings
import dev.kyralabs.pingogram.core.domain.repository.ChatListSettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow

/** [ChatListSettingsRepository] in memory, with the app's defaults at first; saving fails with [failure] while set. */
class FakeChatListSettingsRepository(
    settings: ChatListSettings = ChatListSettings(),
) : ChatListSettingsRepository {
    override val settings = MutableStateFlow(settings)

    var failure: DomainError? = null

    override suspend fun setShowStories(show: Boolean): Outcome<Unit> {
        failure?.let { return Outcome.Failure(it) }
        settings.value = settings.value.copy(showStories = show)
        return Outcome.Success(Unit)
    }
}
