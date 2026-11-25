package dev.kyralabs.pingogram.core.testing

import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.repository.NotesAndNamesRepository
import kotlinx.coroutines.flow.MutableStateFlow

/** [NotesAndNamesRepository] in memory, on at first as in the app. */
class FakeNotesAndNamesRepository(
    enabled: Boolean = true,
) : NotesAndNamesRepository {
    override val enabled = MutableStateFlow(enabled)

    override suspend fun setEnabled(enabled: Boolean): Outcome<Unit> {
        this.enabled.value = enabled
        return Outcome.Success(Unit)
    }
}
