package dev.kyralabs.pingogram.core.domain.repository

import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.OpenSourceLibrary
import kotlinx.collections.immutable.ImmutableList

/**
 * The libraries, fonts and icons Pingogram is built from, with their licenses (`REQUIREMENTS.md` FR-S1, About). `:app`
 * implements it on the list its build generates (`docs/adr/0002-open-source-licenses.md`); nothing goes to the
 * network.
 */
interface OpenSourceLicensesRepository {
    /** Every library, sorted by name. */
    suspend fun libraries(): Outcome<ImmutableList<OpenSourceLibrary>>

    /** The library [id] ([OpenSourceLibrary.id]); `DomainError.NotFound` if this build has none of that id. */
    suspend fun library(id: String): Outcome<OpenSourceLibrary>
}
