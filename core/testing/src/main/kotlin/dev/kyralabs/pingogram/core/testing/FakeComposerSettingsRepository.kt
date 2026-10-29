package dev.kyralabs.pingogram.core.testing

import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.SendKeys
import dev.kyralabs.pingogram.core.domain.repository.ComposerSettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * [ComposerSettingsRepository] in memory, the defaults at first ([initial], [originalQuality], [videoMessages]);
 * [failure] fails it.
 */
class FakeComposerSettingsRepository(
    initial: SendKeys = SendKeys(),
    originalQuality: Boolean = false,
    videoMessages: Boolean = false,
) : ComposerSettingsRepository {
    private val saved = MutableStateFlow(initial)

    /** What [sendPhotosInOriginalQuality] holds. */
    val original = MutableStateFlow(originalQuality)

    /** What [recordsVideoMessages] holds. */
    val video = MutableStateFlow(videoMessages)

    /** What the setter fails with; null saves. */
    var failure: DomainError? = null

    override val sendKeys: Flow<SendKeys> = saved

    override suspend fun setSendKeys(keys: SendKeys): Outcome<Unit> {
        failure?.let { return Outcome.Failure(it) }
        saved.value = keys
        return Outcome.Success(Unit)
    }

    override val sendPhotosInOriginalQuality: Flow<Boolean> = original

    override suspend fun setSendPhotosInOriginalQuality(enabled: Boolean): Outcome<Unit> {
        failure?.let { return Outcome.Failure(it) }
        original.value = enabled
        return Outcome.Success(Unit)
    }

    override val recordsVideoMessages: Flow<Boolean> = video

    override suspend fun setRecordsVideoMessages(enabled: Boolean): Outcome<Unit> {
        failure?.let { return Outcome.Failure(it) }
        video.value = enabled
        return Outcome.Success(Unit)
    }
}
