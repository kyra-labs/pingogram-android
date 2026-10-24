package dev.kyralabs.pingogram.core.testing

import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.repository.LinkPreviewSettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow

/** [LinkPreviewSettingsRepository] in memory, off at first as in the app. */
class FakeLinkPreviewSettingsRepository(
    enabled: Boolean = false,
) : LinkPreviewSettingsRepository {
    override val secretChatPreviews = MutableStateFlow(enabled)

    override suspend fun setSecretChatPreviews(enabled: Boolean): Outcome<Unit> {
        secretChatPreviews.value = enabled
        return Outcome.Success(Unit)
    }
}
