package dev.kyralabs.pingogram.core.testing

import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.TranscriptionSettings
import dev.kyralabs.pingogram.core.domain.repository.TranscriptionSettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/** [TranscriptionSettingsRepository] in memory: [initial] at first, [failure] making the setters fail. */
class FakeTranscriptionSettingsRepository(
    initial: TranscriptionSettings = TranscriptionSettings(),
) : TranscriptionSettingsRepository {
    override val settings = MutableStateFlow(initial)

    /** What the setters fail with; null saves. */
    var failure: DomainError? = null

    override suspend fun setAutomatic(enabled: Boolean): Outcome<Unit> = save { it.copy(automatic = enabled) }

    override suspend fun markNoticeShown(): Outcome<Unit> = save { it.copy(noticeShown = true) }

    private fun save(change: (TranscriptionSettings) -> TranscriptionSettings): Outcome<Unit> {
        failure?.let { return Outcome.Failure(it) }
        settings.update(change)
        return Outcome.Success(Unit)
    }
}
