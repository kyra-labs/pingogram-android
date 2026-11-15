package dev.kyralabs.pingogram.core.testing

import dev.kyralabs.pingogram.core.domain.repository.ViewerHintsRepository
import kotlinx.coroutines.flow.MutableStateFlow

/** A [ViewerHintsRepository] in memory. */
class FakeViewerHintsRepository(
    shown: Boolean = false,
) : ViewerHintsRepository {
    override val controlsHintShown = MutableStateFlow(shown)

    override suspend fun setControlsHintShown() {
        controlsHintShown.value = true
    }
}
