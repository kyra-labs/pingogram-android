package dev.kyralabs.pingogram.core.testing

import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.repository.PlaybackSettingsRepository
import dev.kyralabs.pingogram.core.domain.repository.RaiseToListenSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/** [PlaybackSettingsRepository] in memory: the defaults at first ([initial]), [failure] making the setters fail. */
class FakePlaybackSettingsRepository(
    initial: RaiseToListenSettings = RaiseToListenSettings(),
) : PlaybackSettingsRepository {
    override val raiseToListen = MutableStateFlow(initial)

    /** What the setters fail with; null saves. */
    var failure: DomainError? = null

    override suspend fun setRaiseToListen(enabled: Boolean): Outcome<Unit> = save { it.copy(enabled = enabled) }

    override suspend fun setPauseWhenLowered(enabled: Boolean): Outcome<Unit> =
        save { it.copy(pauseWhenLowered = enabled) }

    private fun save(change: (RaiseToListenSettings) -> RaiseToListenSettings): Outcome<Unit> {
        failure?.let { return Outcome.Failure(it) }
        raiseToListen.update(change)
        return Outcome.Success(Unit)
    }
}
