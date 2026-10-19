package dev.kyralabs.pingogram.feature.settings.ui

import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.OpenSourceLibrary
import dev.kyralabs.pingogram.core.domain.model.OpenSourceLicense
import dev.kyralabs.pingogram.core.domain.repository.OpenSourceLicensesRepository
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CompletableDeferred

/**
 * An [OpenSourceLicensesRepository] that answers with [libraries], or fails with [error] while it is set. While [gate]
 * is set, answers wait for it, so tests see what shows before.
 */
class FakeOpenSourceLicensesRepository(
    var libraries: List<OpenSourceLibrary> = listOf(TDLIB, MATERIAL3),
    var error: DomainError? = null,
) : OpenSourceLicensesRepository {
    var gate: CompletableDeferred<Unit>? = null

    /** How often the list was asked for. */
    var requests = 0
        private set

    override suspend fun libraries(): Outcome<ImmutableList<OpenSourceLibrary>> {
        requests++
        gate?.await()
        return error?.let { Outcome.Failure(it) } ?: Outcome.Success(libraries.toImmutableList())
    }

    override suspend fun library(id: String): Outcome<OpenSourceLibrary> =
        when (val all = libraries()) {
            is Outcome.Success -> {
                all.value.firstOrNull { it.id == id }?.let { Outcome.Success(it) }
                    ?: Outcome.Failure(DomainError.NotFound)
            }

            is Outcome.Failure -> {
                all
            }
        }

    companion object {
        val BOOST =
            OpenSourceLicense(
                id = "BSL-1.0",
                name = "Boost Software License 1.0",
                url = "https://www.boost.org/LICENSE_1_0.txt",
                text = "Boost Software License - Version 1.0 - August 17th, 2003\n\nPermission is hereby\ngranted.\n",
            )

        val APACHE =
            OpenSourceLicense(
                id = "Apache-2.0",
                name = "Apache License 2.0",
                url = "https://www.apache.org/licenses/LICENSE-2.0",
                text = null,
            )

        val TDLIB =
            OpenSourceLibrary(
                id = "org.drinkless:tdlib",
                name = "TDLib",
                version = "a94b83d67a",
                description = "Telegram Database Library.",
                developers = persistentListOf("Aliaksei Levin", "Arseny Smirnov"),
                website = "https://github.com/tdlib/td",
                licenses = persistentListOf(BOOST),
            )

        val MATERIAL3 =
            OpenSourceLibrary(
                id = "androidx.compose.material3:material3",
                name = "Compose Material3 Components",
                version = "1.5.0-beta01",
                description = null,
                developers = persistentListOf(),
                website = null,
                licenses = persistentListOf(APACHE, BOOST),
            )
    }
}
