package dev.kyralabs.pingogram.tile

import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.repository.PrivacyScreenRepository
import kotlinx.coroutines.flow.first

/** Turns the privacy screen (`REQUIREMENTS.md` FR-X25) the other way from how it is now, as its tile's tap does. */
internal class PrivacyScreenToggle(
    private val repository: PrivacyScreenRepository,
) {
    /** Saves the opposite of the current setting; fails as the setting's save does. */
    suspend fun toggle(): Outcome<Unit> = repository.setEnabled(!repository.enabled.first())

    /** Whether the phone must be unlocked first: to turn it off while [isLocked]; turning it on never needs it. */
    suspend fun needsUnlock(isLocked: Boolean): Boolean = isLocked && repository.enabled.first()
}
