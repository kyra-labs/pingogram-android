package dev.kyralabs.pingogram.core.testing

import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.repository.StickerSettings
import dev.kyralabs.pingogram.core.domain.repository.StickerSettingsRepository
import dev.kyralabs.pingogram.core.domain.repository.StickerSuggestions
import kotlinx.coroutines.flow.MutableStateFlow

/** [StickerSettingsRepository] in memory, with the app's defaults at first; saving fails with [failure] while set. */
class FakeStickerSettingsRepository(
    settings: StickerSettings = StickerSettings(),
) : StickerSettingsRepository {
    override val settings = MutableStateFlow(settings)

    var failure: DomainError? = null

    override suspend fun setSuggestions(suggestions: StickerSuggestions): Outcome<Unit> {
        failure?.let { return Outcome.Failure(it) }
        settings.value = settings.value.copy(suggestions = suggestions)
        return Outcome.Success(Unit)
    }

    override suspend fun setLoop(loop: Boolean): Outcome<Unit> {
        failure?.let { return Outcome.Failure(it) }
        settings.value = settings.value.copy(loop = loop)
        return Outcome.Success(Unit)
    }
}
