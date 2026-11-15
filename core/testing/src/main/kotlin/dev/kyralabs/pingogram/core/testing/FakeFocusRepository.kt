package dev.kyralabs.pingogram.core.testing

import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.FocusProfile
import dev.kyralabs.pingogram.core.domain.model.ManualFocus
import dev.kyralabs.pingogram.core.domain.repository.FocusRepository
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableStateFlow

/** [FocusRepository] in memory, starting with [profiles]; [failure] makes the changes fail. */
class FakeFocusRepository(
    profiles: List<FocusProfile> = persistentListOf(),
) : FocusRepository {
    override val profiles = MutableStateFlow<ImmutableList<FocusProfile>>(profiles.toImmutableList())
    override val manual = MutableStateFlow<ManualFocus?>(null)
    override val lastUsedId = MutableStateFlow<Long?>(null)

    /** What the changes fail with; null saves. */
    var failure: DomainError? = null

    override suspend fun saveProfile(profile: FocusProfile): Outcome<Long> {
        failure?.let { return Outcome.Failure(it) }
        val current = profiles.value
        val id = if (profile.id == 0L) (current.maxOfOrNull { it.id } ?: 0L) + 1 else profile.id
        val saved = profile.copy(id = id)
        profiles.value =
            if (current.any { it.id == id }) {
                current.map { if (it.id == id) saved else it }.toImmutableList()
            } else {
                (current + saved).toImmutableList()
            }
        return Outcome.Success(id)
    }

    override suspend fun deleteProfile(id: Long): Outcome<Unit> {
        failure?.let { return Outcome.Failure(it) }
        profiles.value = profiles.value.filterNot { it.id == id }.toImmutableList()
        if (manual.value?.profileId == id) manual.value = null
        return Outcome.Success(Unit)
    }

    override suspend fun setManual(manual: ManualFocus?): Outcome<Unit> {
        failure?.let { return Outcome.Failure(it) }
        this.manual.value = manual
        manual?.profileId?.let { lastUsedId.value = it }
        return Outcome.Success(Unit)
    }
}
